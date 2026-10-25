package com.eddyizm.tempus.podcast;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import androidx.annotation.Keep;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.catalog.AudioCatalog;
import com.eddyizm.tempus.subsonic.models.PodcastChannel;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;
import com.google.gson.Gson;
import okhttp3.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Device subscriptions and a bounded per-feed cache; deliberately independent of server accounts. */
public final class LocalPodcastStore {
    private static LocalPodcastStore instance;
    private static final long REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);
    private static final int MAX_FEED_BYTES = 8 * 1024 * 1024;
    private final Context context;
    private final Gson gson = new Gson();
    private final File directory;
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
            .readTimeout(25,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build();
    private final MutableLiveData<List<PodcastChannel>> live = new MutableLiveData<>(Collections.emptyList());
    private final Map<String,PodcastChannel> channels = new LinkedHashMap<>();
    private final Set<String> refreshing = new HashSet<>();
    private final Map<String,Long> attempted = new HashMap<>();
    private State state;

    @Keep
    private static class State {
        Set<String> seeded = new HashSet<>();
        List<PodcastChannel> subscriptions = new ArrayList<>();
    }
    public interface Completion { void complete(String error); }

    public static synchronized LocalPodcastStore getInstance() {
        if (instance == null) instance = new LocalPodcastStore(App.getContext());
        return instance;
    }
    public static boolean isLocal(String id) { return id != null && id.startsWith("rss_"); }

    private LocalPodcastStore(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(context.getFilesDir(),"podcasts");
        disk.execute(this::initialize);
    }

    private void initialize() {
        try {
            if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create podcast cache");
            File file = new File(directory,"subscriptions.json");
            state = file.exists() || new File(file+".bak").exists() ? read(file,State.class) : new State();
            for (PodcastChannel summary : state.subscriptions) {
                PodcastChannel channel = summary;
                try {
                    File cache = cache(summary.getId());
                    if (cache.exists()) channel = read(cache,PodcastChannel.class);
                } catch (Exception e) { Log.w("LocalPodcasts","Ignoring damaged feed cache"); }
                channels.put(summary.getId(),channel);
            }
            Set<String> urls = new HashSet<>();
            for (PodcastChannel channel : channels.values()) urls.add(AudioCatalog.canonicalUrl(channel.getUrl()));
            for (AudioCatalog.Entry entry : AudioCatalog.load(context).podcasts) {
                if (!AudioCatalog.shouldSeed(entry,state.seeded,urls)) continue;
                PodcastChannel channel = new PodcastChannel();
                channel.setId("rss_" + entry.id);
                channel.setUrl(entry.url);
                channel.setTitle(entry.name);
                channel.setCategory(entry.category);
                channel.setRegion(entry.region);
                channel.setDescription(entry.description);
                channel.setCoverArtId(entry.artwork);
                channel.setOriginalImageUrl(entry.artwork);
                channel.setStatus("pending");
                channels.put(channel.getId(),channel);
            }
            persistSubscriptions();
            publish();
            refreshAll(false);
        } catch (Exception e) {
            // Never overwrite an unreadable subscription file with an empty list.
            Log.e("LocalPodcasts","Cannot load subscriptions",e);
        }
    }

    public LiveData<List<PodcastChannel>> observe() { return live; }

    public void refreshAll(boolean force) {
        disk.execute(() -> {
            for (PodcastChannel channel : new ArrayList<>(channels.values())) scheduleRefresh(channel,force);
        });
    }
    public void refresh(String id, boolean force) {
        disk.execute(() -> { PodcastChannel channel = channels.get(id); if (channel != null) scheduleRefresh(channel,force); });
    }
    private void scheduleRefresh(PodcastChannel channel, boolean force) {
        String id = channel.getId();
        long now = System.currentTimeMillis();
        if (refreshing.contains(id)) return;
        if (!force && (now - cache(id).lastModified() < REFRESH_INTERVAL_MS
                || now - attempted.getOrDefault(id,0L) < TimeUnit.MINUTES.toMillis(5))) return;
        refreshing.add(id);
        attempted.put(id,now);
        network.execute(() -> {
            PodcastChannel result = null;
            try { result = fetch(channel.getUrl(),id); }
            catch (Exception e) { Log.w("LocalPodcasts","Feed refresh failed: " + id + " (" + e.getClass().getSimpleName() + ")"); }
            PodcastChannel fetched = result;
            disk.execute(() -> {
                refreshing.remove(id);
                PodcastChannel current = channels.get(id);
                if (current == null) return; // A feed deleted during its request stays deleted.
                if (fetched != null) {
                    fetched.setCategory(current.getCategory());
                    fetched.setRegion(current.getRegion());
                    if (fetched.getCoverArtId() == null) fetched.setCoverArtId(current.getCoverArtId());
                    channels.put(id,fetched);
                    try { write(cache(id),fetched); } catch (Exception e) { Log.w("LocalPodcasts","Cannot save feed cache",e); }
                } else {
                    PodcastChannel failed = copy(current);
                    failed.setErrorMessage(context.getString(R.string.podcast_rss_refresh_failed));
                    failed.setStatus("error");
                    channels.put(id,failed);
                }
                publish();
            });
        });
    }

    public void subscribe(String url, Completion completion) {
        if (!AudioCatalog.isWebUrl(url)) { main.post(() -> completion.complete(context.getString(R.string.podcast_rss_invalid_url))); return; }
        network.execute(() -> {
            try {
                AudioCatalog.Entry builtin = null;
                for (AudioCatalog.Entry entry : AudioCatalog.load(context).podcasts)
                    if (AudioCatalog.canonicalUrl(entry.url).equals(AudioCatalog.canonicalUrl(url))) builtin=entry;
                String id = builtin == null ? "rss_custom_"+RssFeedParser.digest(AudioCatalog.canonicalUrl(url)) : "rss_"+builtin.id;
                PodcastChannel fetched = fetch(url,id);
                if (builtin != null) { fetched.setCategory(builtin.category); fetched.setRegion(builtin.region); }
                disk.execute(() -> {
                    if (state == null) { main.post(() -> completion.complete(context.getString(R.string.podcast_rss_save_failed))); return; }
                    for (PodcastChannel c : channels.values()) {
                        if (AudioCatalog.canonicalUrl(c.getUrl()).equals(AudioCatalog.canonicalUrl(url))) {
                            main.post(() -> completion.complete(null)); return;
                        }
                    }
                    channels.put(id,fetched);
                    try {
                        write(cache(id),fetched);
                        persistSubscriptions();
                        publish(); main.post(() -> completion.complete(null));
                    } catch (Exception e) {
                        channels.remove(id);
                        main.post(() -> completion.complete(context.getString(R.string.podcast_rss_save_failed)));
                    }
                });
            } catch (Exception e) { main.post(() -> completion.complete(context.getString(R.string.podcast_rss_add_failed))); }
        });
    }

    public void unsubscribe(String id, Completion completion) {
        disk.execute(() -> {
            PodcastChannel previous = channels.remove(id);
            try {
                persistSubscriptions(); // Seed history and deletion are in the same atomic file.
                new AtomicFile(cache(id)).delete();
                publish(); main.post(() -> completion.complete(null));
            } catch (Exception e) {
                if (previous != null) channels.put(id,previous);
                main.post(() -> completion.complete(context.getString(R.string.podcast_rss_save_failed)));
            }
        });
    }

    /** For Continue listening; cached episodes remain streamable without a feed refresh. */
    public void findEpisode(String id, java.util.function.Consumer<PodcastEpisode> callback) {
        disk.execute(() -> {
            PodcastEpisode found = null;
            for (PodcastChannel c : channels.values()) if (c.getEpisodes() != null)
                for (PodcastEpisode e : c.getEpisodes()) if (id.equals(e.getId())) found = e;
            PodcastEpisode result = found;
            main.post(() -> callback.accept(result));
        });
    }

    private PodcastChannel fetch(String url, String id) throws Exception {
        Request request = new Request.Builder().url(url).header("User-Agent","Tempus/1.0 PodcastReader")
                .header("Accept","application/rss+xml, application/atom+xml, application/xml, text/xml").build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            InputStream bounded = new java.io.FilterInputStream(response.body().byteStream()) {
                int remaining = MAX_FEED_BYTES;
                @Override public int read() throws IOException {
                    if (--remaining < 0) throw new IOException("Feed too large");
                    return super.read();
                }
                @Override public int read(byte[] b,int off,int len) throws IOException {
                    if (remaining <= 0) throw new IOException("Feed too large");
                    int count=in.read(b,off,Math.min(len,remaining));
                    if (count>0) remaining-=count;
                    return count;
                }
            };
            PodcastChannel channel = RssFeedParser.parse(bounded,response.request().url().toString(),id);
            channel.setUrl(url);
            return channel;
        }
    }
    private void persistSubscriptions() throws IOException {
        state.subscriptions = new ArrayList<>();
        for (PodcastChannel channel : channels.values()) state.subscriptions.add(summary(channel));
        write(new File(directory,"subscriptions.json"),state);
    }
    public static PodcastChannel summary(PodcastChannel channel) {
        PodcastChannel out = new PodcastChannel();
        out.setId(channel.getId()); out.setUrl(channel.getUrl()); out.setTitle(channel.getTitle());
        out.setDescription(channel.getDescription()); out.setCoverArtId(channel.getCoverArtId());
        out.setOriginalImageUrl(channel.getOriginalImageUrl()); out.setStatus(channel.getStatus());
        out.setErrorMessage(channel.getErrorMessage()); out.setCategory(channel.getCategory()); out.setRegion(channel.getRegion());
        return out;
    }
    private static PodcastChannel copy(PodcastChannel channel) {
        PodcastChannel out=summary(channel); out.setEpisodes(channel.getEpisodes()); return out;
    }
    private File cache(String id) { return new File(directory,RssFeedParser.digest(id)+".json"); }
    private void publish() { live.postValue(new ArrayList<>(channels.values())); }
    private <T> T read(File path, Class<T> type) throws IOException {
        try (InputStream in = new AtomicFile(path).openRead(); InputStreamReader reader = new InputStreamReader(in,StandardCharsets.UTF_8)) {
            return gson.fromJson(reader,type);
        }
    }
    private void write(File path, Object value) throws IOException {
        AtomicFile file = new AtomicFile(path);
        FileOutputStream output = file.startWrite();
        try {
            output.write(gson.toJson(value).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception e) { file.failWrite(output); throw new IOException(e); }
    }
}
