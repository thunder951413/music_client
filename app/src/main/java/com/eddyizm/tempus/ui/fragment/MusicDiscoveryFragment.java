package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentMusicDiscoveryBinding;
import com.eddyizm.tempus.discovery.*;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.service.*;
import com.eddyizm.tempus.subsonic.models.*;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.*;
import com.eddyizm.tempus.util.*;
import com.eddyizm.tempus.viewmodel.*;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@UnstableApi
public final class MusicDiscoveryFragment extends Fragment implements ClickCallback {
    public static final String ARG_MODE="discovery_mode", ARG_SOURCE="chart_source", ARG_TITLE="discovery_title";
    private FragmentMusicDiscoveryBinding bind;
    private MusicDiscoveryViewModel model;
    private MusicDiscoveryViewModel.State current;
    private SongHorizontalAdapter songs;
    private AlbumHorizontalAdapter albums;
    private DiscoveryChartAdapter charts;
    private ListenableFuture<MediaBrowser> browser;
    private boolean playableOnly=true;
    private MenuItem refreshItem;
    @Override public void onCreate(Bundle saved) { super.onCreate(saved);setHasOptionsMenu(true); }
    @Override public void onCreateOptionsMenu(@NonNull Menu menu,@NonNull MenuInflater inflater) {
        refreshItem=menu.add(current==null || current.mode==MusicDiscoveryViewModel.Mode.UNHEARD || current.mode==MusicDiscoveryViewModel.Mode.RANDOM ? R.string.discovery_shuffle:R.string.audio_refresh);
        refreshItem.setIcon(R.drawable.ic_refresh).setOnMenuItemClickListener(item -> { model.refresh();return true; })
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        bind=FragmentMusicDiscoveryBinding.inflate(inflater,parent,false);
        model=new ViewModelProvider(this).get(MusicDiscoveryViewModel.class);
        var activity=(MainActivity)requireActivity();activity.setSupportActionBar(bind.discoveryToolbar);
        bind.discoveryToolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());
        bind.discoveryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        songs=new SongHorizontalAdapter(getViewLifecycleOwner(),this,true,false,null);
        albums=new AlbumHorizontalAdapter(this,false);charts=new DiscoveryChartAdapter(this::chartClicked);
        PlaybackViewModel playback=new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        playback.getCurrentSongId().observe(getViewLifecycleOwner(), id -> songs.setPlaybackState(id,Boolean.TRUE.equals(playback.getIsPlaying().getValue())));
        playback.getIsPlaying().observe(getViewLifecycleOwner(), playing -> songs.setPlaybackState(playback.getCurrentSongId().getValue(),Boolean.TRUE.equals(playing)));
        int[] labels={R.string.discovery_unheard,R.string.discovery_random,R.string.discovery_newest,R.string.discovery_frequent,R.string.discovery_genres,R.string.discovery_charts};
        for (int i=0;i<labels.length;i++) {
            MusicDiscoveryViewModel.Mode mode=MusicDiscoveryViewModel.Mode.values()[i];Chip chip=new Chip(requireContext());
            chip.setId(View.generateViewId());chip.setTag(mode);chip.setText(labels[i]);chip.setCheckable(true);chip.setChecked(i==0);
            chip.setOnCheckedChangeListener((button,checked) -> { if (checked) { bind.discoveryList.scrollToPosition(0);model.mode(mode); } });
            bind.discoveryModes.addView(chip);
        }
        ArrayAdapter<ChartSource> sources=new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item,ChartSource.values());
        sources.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bind.discoveryChartSource.setAdapter(sources);
        bind.discoveryChartSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p,View view,int index,long id) {
                if (current!=null && current.mode==MusicDiscoveryViewModel.Mode.CHART) model.source(ChartSource.values()[index]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        bind.discoveryStatus.setOnClickListener(v -> {
            if (current!=null && current.mode==MusicDiscoveryViewModel.Mode.UNHEARD) new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.discovery_unheard).setMessage(R.string.discovery_unheard_details).setPositiveButton(android.R.string.ok,null).show();
        });
        bind.discoveryGenre.setOnClickListener(v -> chooseGenre());
        bind.discoveryMore.setOnClickListener(v -> model.more());
        bind.discoveryMatch.setOnClickListener(v -> model.matchLibrary());
        bind.discoveryPlay.setOnClickListener(v -> playAll());
        bind.discoveryExport.setOnClickListener(v -> exportMissing());
        bind.discoveryPlayable.setOnCheckedChangeListener((button,checked) -> {
            playableOnly=checked;if (current!=null) render(current);
        });
        applyPreset();
        model.state().observe(getViewLifecycleOwner(),this::render);model.enter();return bind.getRoot();
    }
    /** A link from the Discover page fixes the mode, so the mode chips and intro are hidden. */
    private void applyPreset() {
        Bundle args=getArguments();
        if (args==null || args.getString(ARG_MODE)==null) return;
        MusicDiscoveryViewModel.Mode mode=MusicDiscoveryViewModel.Mode.valueOf(args.getString(ARG_MODE));
        String source=args.getString(ARG_SOURCE);
        ChartSource chart=source==null?null:ChartSource.valueOf(source);
        model.preset(mode,chart);
        bind.discoveryModes.setVisibility(View.GONE);
        bind.discoveryIntro.setVisibility(View.GONE);
        bind.discoveryToolbar.setTitle(args.getString(ARG_TITLE,getString(R.string.discovery_title)));
    }
    @Override public void onStart() {
        super.onStart();browser=new MediaBrowser.Builder(requireContext(),new SessionToken(requireContext(),new ComponentName(requireContext(),MediaService.class))).buildAsync();
        songs.setMediaBrowserListenableFuture(browser);model.enter();
    }
    @Override public void onStop() { MediaBrowser.releaseFuture(browser);browser=null;super.onStop(); }
    @Override public void onDestroyView() { super.onDestroyView();bind=null; }
    private void render(MusicDiscoveryViewModel.State state) {
        if (bind==null) return;current=state;
        if (refreshItem!=null) refreshItem.setTitle(state.mode==MusicDiscoveryViewModel.Mode.UNHEARD || state.mode==MusicDiscoveryViewModel.Mode.RANDOM ? R.string.discovery_shuffle:R.string.audio_refresh);
        boolean chart=state.mode==MusicDiscoveryViewModel.Mode.CHART;
        boolean album=state.mode==MusicDiscoveryViewModel.Mode.NEWEST || state.mode==MusicDiscoveryViewModel.Mode.FREQUENT;
        for (int i=0;i<bind.discoveryModes.getChildCount();i++) {
            Chip chip=(Chip)bind.discoveryModes.getChildAt(i);if (chip.getTag()==state.mode && !chip.isChecked()) chip.setChecked(true);
        }
        bind.discoveryChartOptions.setVisibility(chart?View.VISIBLE:View.GONE);
        if (chart && bind.discoveryChartSource.getSelectedItemPosition()!=state.source.ordinal()) bind.discoveryChartSource.setSelection(state.source.ordinal());
        bind.discoveryGenre.setVisibility(state.mode==MusicDiscoveryViewModel.Mode.GENRE?View.VISIBLE:View.GONE);
        bind.discoveryGenre.setText(state.genre==null?getString(R.string.discovery_choose_genre):state.genre+" ▾");
        bind.discoveryProgress.setVisibility(state.loading || state.matching?View.VISIBLE:View.GONE);
        bind.discoveryMatch.setVisibility(chart?View.VISIBLE:View.GONE);bind.discoveryMatch.setEnabled(!state.loading && !state.matching && !state.chart.isEmpty());
        bind.discoveryPlayable.setVisibility(chart?View.VISIBLE:View.GONE);
        int missing=chart && !state.matching && state.checked==state.chart.size() ? ChartMatches.missing(state.chart).size() : 0;
        bind.discoveryExport.setVisibility(missing>0?View.VISIBLE:View.GONE);
        bind.discoveryExport.setText(getString(R.string.discovery_export_missing,missing));
        bind.discoveryMore.setVisibility(state.more?View.VISIBLE:View.GONE);bind.discoveryMore.setEnabled(!state.loading);
        bind.discoveryPlay.setVisibility(album?View.GONE:View.VISIBLE);
        int available=(int)state.chart.stream().filter(t->t.matches.size()==1).count();
        bind.discoveryPlay.setEnabled(chart?available>0:!state.songs.isEmpty());
        String status;
        if (chart) status=getString(R.string.discovery_chart_info,state.chart.size(),getString(state.cached?R.string.discovery_cached:R.string.discovery_current),updated(state.updated))
                +"\n"+getString(R.string.discovery_match_progress,state.checked,state.chart.size(),available,state.failed)
                +"\n"+getString(R.string.discovery_chart_hint);
        else if (state.mode==MusicDiscoveryViewModel.Mode.UNHEARD) status=getString(R.string.discovery_unheard_hint)+"\n"+getString(R.string.discovery_song_count,state.songs.size());
        else if (album) status=getString(state.mode==MusicDiscoveryViewModel.Mode.NEWEST?R.string.discovery_newest_hint:R.string.discovery_frequent_hint);
        else status=getString(R.string.discovery_song_count,state.songs.size());
        bind.discoveryStatus.setText(status);
        boolean empty=chart?(state.chart.isEmpty() || playableOnly && state.chart.stream().noneMatch(t->!t.matches.isEmpty())):album?state.albums.isEmpty():state.songs.isEmpty();
        bind.discoveryEmpty.setVisibility(!state.loading && (empty || state.error!=null)?View.VISIBLE:View.GONE);
        bind.discoveryEmpty.setText(state.error!=null?state.error:getString(R.string.discovery_no_results));
        if (chart) { if (bind.discoveryList.getAdapter()!=charts) bind.discoveryList.setAdapter(charts);charts.setItems(state.chart,playableOnly); }
        else if (album) { if (bind.discoveryList.getAdapter()!=albums) bind.discoveryList.setAdapter(albums);albums.setItems(state.albums); }
        else { if (bind.discoveryList.getAdapter()!=songs) bind.discoveryList.setAdapter(songs);songs.setItems(state.songs); }
    }
    private String updated(String value) {
        if (value==null) return "—";
        try { return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(Long.parseLong(value))); }
        catch (Exception ignored) {
            for (String pattern:new String[]{"EEE, dd MMM yyyy HH:mm:ss Z","yyyy-MM-dd'T'HH:mm:ssXXX"}) {
                try { return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new SimpleDateFormat(pattern,Locale.US).parse(value)); }
                catch (Exception invalid) {}
            }
            return value;
        }
    }
    private void exportMissing() {
        if (current==null || current.mode!=MusicDiscoveryViewModel.Mode.CHART) return;
        List<ChartTrack> missing=ChartMatches.missing(current.chart);
        com.eddyizm.tempus.ui.browse.MissingExport.show(this,getString(R.string.discovery_export_title,missing.size()),current.source.label,missing);
    }

    private void chooseGenre() {
        if (current==null || current.genres.isEmpty()) return;
        String[] names=current.genres.stream().map(g->g.getGenre()+" · "+g.getSongCount()).toArray(String[]::new);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.discovery_choose_genre).setItems(names,(d,index)->{
            bind.discoveryList.scrollToPosition(0);model.genre(current.genres.get(index).getGenre());
        }).show();
    }
    private void playAll() {
        if (current==null) return;
        List<Child> media=current.mode==MusicDiscoveryViewModel.Mode.CHART?matchedSongs():current.songs;
        if (!media.isEmpty()) { MediaManager.startQueue(browser,media,0);((MainActivity)requireActivity()).setBottomSheetInPeek(true); }
    }
    private List<Child> matchedSongs() {
        List<Child> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        for (ChartTrack row:current.chart) if (row.matches.size()==1) {
            Child selected=row.matches.get(0);if (ids.add(selected.getId())) result.add(selected);
        }
        return result;
    }
    private void chartClicked(ChartTrack track) {
        if (!track.checked || track.matchFailed) { model.matchLibrary();return; }
        if (track.matches.size()==1) { playChartSong(track.matches.get(0));return; }
        List<Child> choices=track.matches.isEmpty()?track.alternatives:track.matches;
        if (choices.isEmpty()) { Toast.makeText(requireContext(),R.string.discovery_no_candidates,Toast.LENGTH_LONG).show();return; }
        String[] names=choices.stream().map(s->s.getTitle()+" · "+s.getArtist()+"\n"+s.getAlbum()+" · "+MusicUtil.getReadableDurationString(s.getDuration(),false)).toArray(String[]::new);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.discovery_choose_version).setItems(names,(d,index)->{
            Child chosen=choices.get(index);model.chooseVersion(track,chosen);playChartSong(chosen);
        }).show();
    }
    private void playChartSong(Child song) {
        List<Child> media=matchedSongs();int index=0;
        for (int i=0;i<media.size();i++) if (media.get(i).getId().equals(song.getId())) index=i;
        if (media.stream().noneMatch(s->s.getId().equals(song.getId()))) { media=new ArrayList<>();media.add(song);index=0; }
        MediaManager.startQueue(browser,media,index);((MainActivity)requireActivity()).setBottomSheetInPeek(true);
    }
    @Override public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(browser,bundle.getParcelableArrayList(Constants.TRACKS_OBJECT),bundle.getInt(Constants.ITEM_POSITION));
        ((MainActivity)requireActivity()).setBottomSheetInPeek(true);
    }
    @Override public void onMediaLongClick(Bundle bundle) { ((MainActivity)requireActivity()).navController.navigate(R.id.songBottomSheetDialog,bundle); }
    @Override public void onAlbumClick(Bundle bundle) { ((MainActivity)requireActivity()).navController.navigate(R.id.albumPageFragment,bundle); }
    @Override public void onAlbumLongClick(Bundle bundle) { ((MainActivity)requireActivity()).navController.navigate(R.id.albumBottomSheetDialog,bundle); }
}
