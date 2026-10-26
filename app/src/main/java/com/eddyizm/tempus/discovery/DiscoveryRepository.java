package com.eddyizm.tempus.discovery;

import android.util.AtomicFile;
import com.eddyizm.tempus.App;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.*;
import com.eddyizm.tempus.util.Preferences;
import okhttp3.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import retrofit2.Call;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class DiscoveryRepository {
    private final Set<Call<ApiResponse>> calls=ConcurrentHashMap.newKeySet();
    private final Set<okhttp3.Call> publicCalls=ConcurrentHashMap.newKeySet();
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
            .readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build();
    public void cancel() { for (Call<?> call:calls) call.cancel(); for (okhttp3.Call call:publicCalls) call.cancel(); }
    private ApiResponse execute(Call<ApiResponse> call) throws IOException {
        calls.add(call);
        try {
            var response=call.execute();
            if (!response.isSuccessful() || response.body()==null || response.body().getSubsonicResponse()==null
                    || !"ok".equals(response.body().getSubsonicResponse().getStatus())) throw new IOException("Music server request failed");
            return response.body();
        } finally { calls.remove(call); }
    }
    public List<Child> random() throws IOException {
        var body=execute(App.getSubsonicClientInstance(false).getAlbumSongListClient().getRandomSongs(300,null,null));
        return body.getSubsonicResponse().getRandomSongs()!=null && body.getSubsonicResponse().getRandomSongs().getSongs()!=null
                ?body.getSubsonicResponse().getRandomSongs().getSongs():Collections.emptyList();
    }
    public Set<String> heardIds() {
        return new HashSet<>(AppDatabase.getInstance().chronologyDao().getHeardIds(Preferences.getServerId()));
    }
    public List<AlbumID3> albums(String type) throws IOException {
        var body=execute(App.getSubsonicClientInstance(false).getAlbumSongListClient().getAlbumList2(type,50,0,null,null));
        return body.getSubsonicResponse().getAlbumList2()!=null && body.getSubsonicResponse().getAlbumList2().getAlbums()!=null
                ?body.getSubsonicResponse().getAlbumList2().getAlbums():Collections.emptyList();
    }
    public List<Genre> genres() throws IOException {
        var body=execute(App.getSubsonicClientInstance(false).getBrowsingClient().getGenres());
        List<Genre> genres=body.getSubsonicResponse().getGenres()!=null?body.getSubsonicResponse().getGenres().getGenres():null;
        if (genres==null) return Collections.emptyList();
        genres=new ArrayList<>(genres);
        genres.sort(Comparator.comparing(g->g.getGenre()==null?"":g.getGenre()));
        return genres;
    }
    public List<Child> genreSongs(String genre,int offset) throws IOException {
        var body=execute(App.getSubsonicClientInstance(false).getAlbumSongListClient().getSongsByGenre(genre,100,offset));
        return body.getSubsonicResponse().getSongsByGenre()!=null && body.getSubsonicResponse().getSongsByGenre().getSongs()!=null
                ?body.getSubsonicResponse().getSongsByGenre().getSongs():Collections.emptyList();
    }
    public List<Child> search(String title) throws IOException {
        var body=execute(App.getSubsonicClientInstance(false).getSearchingClient().search3(title,40,0,0,0,0,0));
        return body.getSubsonicResponse().getSearchResult3()!=null && body.getSubsonicResponse().getSearchResult3().getSongs()!=null
                ?body.getSubsonicResponse().getSearchResult3().getSongs():Collections.emptyList();
    }
    public ChartParser.Result chart(ChartSource source,boolean refresh) throws Exception {
        File dir=new File(App.getContext().getFilesDir(),"music_charts");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create chart cache");
        AtomicFile file=new AtomicFile(new File(dir,source.name()+".json"));
        if (!refresh && file.getBaseFile().exists() && System.currentTimeMillis()-file.getBaseFile().lastModified()<TimeUnit.HOURS.toMillis(6)) {
            try { return cached(source,file); } catch (Exception ignored) {}
        }
        okhttp3.Call call=http.newCall(new Request.Builder().url(source.endpoint())
                .header("User-Agent","Mozilla/5.0 (Linux; Android) Tempus/1.0")
                .header("Referer",source.referer()).build());
        publicCalls.add(call);
        try (Response response=call.execute()) {
            if (!response.isSuccessful() || response.body()==null) throw new IOException("Chart unavailable");
            String json=readBounded(response.body().byteStream());
            ChartParser.Result result=ChartParser.parse(source,json);
            FileOutputStream output=file.startWrite();
            try { output.write(json.getBytes(StandardCharsets.UTF_8)); file.finishWrite(output); }
            catch (IOException e) { file.failWrite(output); throw e; }
            return result;
        } catch (Exception e) {
            if (call.isCanceled()) throw e;
            try { return cached(source,file); } catch (Exception ignored) { throw e; }
        } finally { publicCalls.remove(call); }
    }
    private ChartParser.Result cached(ChartSource source,AtomicFile file) throws Exception {
        try (InputStream in=file.openRead()) {
            ChartParser.Result result=ChartParser.parse(source,readBounded(in)); result.cached=true; return result;
        }
    }
    private String readBounded(InputStream in) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
        while ((count=in.read(buffer))!=-1) {
            if (bytes.size()+count>4*1024*1024) throw new IOException("Chart too large");
            bytes.write(buffer,0,count);
        }
        return bytes.toString(StandardCharsets.UTF_8.name());
    }
}
