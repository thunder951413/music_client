package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.discovery.*;
import com.eddyizm.tempus.subsonic.models.*;
import com.eddyizm.tempus.util.Preferences;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class MusicDiscoveryViewModel extends AndroidViewModel {
    public enum Mode { UNHEARD, RANDOM, NEWEST, FREQUENT, GENRE, CHART }
    public static final class State {
        public Mode mode=Mode.UNHEARD;
        public List<Child> songs=new ArrayList<>();
        public List<AlbumID3> albums=new ArrayList<>();
        public List<Genre> genres=new ArrayList<>();
        public List<ChartTrack> chart=new ArrayList<>();
        public String genre,updated,error;
        public ChartSource source=ChartSource.NETEASE_HOT;
        public boolean loading,matching,cached,more;
        public int checked,failed;
        State copy() {
            State n=new State(); n.mode=mode; n.songs=songs; n.albums=albums; n.genres=genres; n.chart=chart;
            n.genre=genre;n.updated=updated;n.error=error;n.source=source;n.loading=loading;n.matching=matching;
            n.cached=cached;n.more=more;n.checked=checked;n.failed=failed;return n;
        }
    }
    private final DiscoveryRepository repository=new DiscoveryRepository();
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicInteger epoch=new AtomicInteger();
    private final MutableLiveData<State> live=new MutableLiveData<>();
    private final Set<String> shown=new HashSet<>();
    private String scope;
    private State state=new State();
    public MusicDiscoveryViewModel(@NonNull Application app) { super(app); }
    public LiveData<State> state() { return live; }
    public void enter() {
        String key=Preferences.getServerId()+":"+Preferences.getActiveMusicFolderId();
        if (!key.equals(scope)) { scope=key; state=new State(); shown.clear(); load(false); }
        else if (live.getValue()==null) load(false);
    }
    public void mode(Mode mode) {
        if (state.mode==mode && live.getValue()!=null) return;
        State next=new State(); next.mode=mode; next.source=state.source; next.genre=state.genre;
        state=next; load(false);
    }
    public void source(ChartSource source) { if (state.source!=source) { state.source=source;load(false); } }
    public void genre(String genre) { state.genre=genre;load(false); }
    public void refresh() { load(true); }
    public void more() { if (state.more && !state.loading) loadGenre(true); }
    private boolean current(int token) { return epoch.get()==token && Objects.equals(scope,Preferences.getServerId()+":"+Preferences.getActiveMusicFolderId()); }
    private void publish() { live.setValue(state.copy()); }
    private void load(boolean refresh) {
        int token=epoch.incrementAndGet(); repository.cancel();
        state=state.copy();state.loading=true;state.matching=false;state.error=null;state.checked=0;state.failed=0;state.more=false;
        state.updated=null;state.cached=false;
        state.songs=new ArrayList<>(); state.albums=new ArrayList<>(); state.chart=new ArrayList<>();publish();
        Mode mode=state.mode; String genre=state.genre; ChartSource source=state.source;
        Set<String> excluded=new HashSet<>(shown);
        workers.execute(() -> {
            try {
                State result=new State();result.mode=mode;result.source=source;result.genre=genre;
                switch (mode) {
                    case UNHEARD:
                        Set<String> heard=repository.heardIds(); List<Child> pool=repository.random();
                        result.songs=MusicDiscoveryRules.unheard(pool,heard,excluded,40);
                        if (result.songs.size()<20 && current(token)) {
                            pool=new ArrayList<>(pool);pool.addAll(repository.random());
                            result.songs=MusicDiscoveryRules.unheard(pool,heard,excluded,40);
                        }
                        break;
                    case RANDOM:
                        List<Child> random=repository.random();Collections.shuffle(random);
                        result.songs=new ArrayList<>(random.subList(0,Math.min(40,random.size())));break;
                    case NEWEST: result.albums=repository.albums("newest");break;
                    case FREQUENT: result.albums=repository.albums("frequent");break;
                    case GENRE:
                        result.genres=repository.genres();
                        if (genre==null && !result.genres.isEmpty()) result.genre=result.genres.get(0).getGenre();
                        if (result.genre!=null) { result.songs=repository.genreSongs(result.genre,0);result.more=result.songs.size()==100; }
                        break;
                    case CHART:
                        ChartParser.Result chart=repository.chart(source,refresh);result.chart=chart.tracks;
                        result.cached=chart.cached;result.updated=chart.updated;break;
                }
                main.post(() -> {
                    if (!current(token)) return;
                    state=result; if (mode==Mode.UNHEARD) for (Child song:result.songs) shown.add(song.getId());
                    if (shown.size()>1000) shown.clear(); publish();
                });
            } catch (Exception e) { main.post(() -> {
                if (!current(token)) return;
                state.loading=false;state.error=getApplication().getString(mode==Mode.CHART?R.string.discovery_chart_failed:R.string.discovery_load_failed);publish();
            }); }
        });
    }
    private void loadGenre(boolean append) {
        int token=epoch.get(); String genre=state.genre; int offset=state.songs.size();
        state=state.copy();state.loading=true;state.error=null;publish();
        workers.execute(() -> {
            try { List<Child> page=repository.genreSongs(genre,offset);main.post(() -> {
                if (!current(token)) return;state=state.copy();state.loading=false;
                List<Child> all=new ArrayList<>(state.songs); Set<String> ids=new HashSet<>();for (Child c:all) ids.add(c.getId());
                for (Child c:page) if (ids.add(c.getId())) all.add(c);
                state.songs=all;state.more=page.size()==100 && all.size()>offset;publish();
            }); } catch (Exception e) { main.post(() -> { if (current(token)) { state.loading=false;state.error=getApplication().getString(R.string.discovery_load_failed);publish(); } }); }
        });
    }
    public void matchLibrary() {
        if (state.matching || state.loading || state.chart.isEmpty()) return;
        int token=epoch.incrementAndGet();repository.cancel();
        state=state.copy();state.matching=true;state.checked=0;state.failed=0;state.error=null;publish();
        List<ChartTrack> rows=new ArrayList<>(state.chart);
        for (int index=0;index<rows.size();index++) {
            int position=index;ChartTrack track=rows.get(index);
            workers.execute(() -> {
                if (!current(token)) return;
                List<Child> candidates=Collections.emptyList();boolean failed=false;
                try { candidates=repository.search(track.title); } catch (Exception e) { failed=true; }
                List<Child> choices=candidates;boolean error=failed;
                List<Child> matched=MusicDiscoveryRules.matches(track,candidates);
                main.post(() -> {
                    if (!current(token)) return;
                    state=state.copy();List<ChartTrack> chart=new ArrayList<>(state.chart);
                    ChartTrack result=track.matched(matched,choices);result.matchFailed=error;
                    chart.set(position,result);state.chart=chart;state.checked++;
                    if (error) state.failed++;
                    state.matching=state.checked<chart.size();publish();
                });
            });
        }
    }
    public void chooseVersion(ChartTrack track,Child song) {
        int index=state.chart.indexOf(track); if (index<0) return;
        state=state.copy();List<ChartTrack> chart=new ArrayList<>(state.chart);
        chart.set(index,track.matched(Collections.singletonList(song),track.alternatives));state.chart=chart;publish();
    }
    @Override protected void onCleared() { epoch.incrementAndGet();repository.cancel();workers.shutdownNow(); }
}
