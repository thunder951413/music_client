package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentLibraryBinding;
import com.eddyizm.tempus.helper.recyclerview.CustomLinearSnapHelper;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.AlbumAdapter;
import com.eddyizm.tempus.ui.adapter.ArtistAdapter;
import com.eddyizm.tempus.ui.adapter.GenreAdapter;
import com.eddyizm.tempus.ui.adapter.MusicFolderAdapter;
import com.eddyizm.tempus.ui.adapter.PlaylistHorizontalAdapter;
import com.eddyizm.tempus.ui.browse.LibraryHeaderBinder;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.LibraryViewModel;
import com.eddyizm.tempus.viewmodel.MainViewModel;

@UnstableApi
public class LibraryFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LibraryFragment";

    private FragmentLibraryBinding bind;
    private MainActivity activity;
    private LibraryViewModel libraryViewModel;
    private MainViewModel mainViewModel;

    private MusicFolderAdapter musicFolderAdapter;
    private AlbumAdapter albumAdapter;
    private ArtistAdapter artistAdapter;
    private GenreAdapter genreAdapter;
    private PlaylistHorizontalAdapter playlistHorizontalAdapter;

    private boolean playlistsLoaded = false;
    private boolean albumsLoaded = false;
    private boolean artistsLoaded = false;
    private boolean genresLoaded = false;

    private boolean playlistsEmpty = true;
    private boolean albumsEmpty = true;
    private boolean artistsEmpty = true;
    private boolean genresEmpty = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentLibraryBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        libraryViewModel.clearCacheIfMusicFolderChanged();

        init();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playlistsLoaded = false;
        albumsLoaded = false;
        artistsLoaded = false;
        genresLoaded = false;

        LibraryHeaderBinder.bind(bind.libraryHeaderCompose, this, libraryViewModel, mainViewModel);
        initPlaylistView();
        initAlbumView();
        initArtistView();
        initGenreView();
        initMusicFolderView();
        initSwipeToRefresh();
        initMusicFolderChangeListener();
    }

    private void initMusicFolderChangeListener() {
        mainViewModel.getActiveMusicFolderId().observe(getViewLifecycleOwner(), musicFolderId ->
                libraryViewModel.reloadIfMusicFolderChanged(getViewLifecycleOwner()));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (activity != null) {
            activity.toggleBottomNavigationBarVisibilityOnOrientationChange();
            activity.setBottomSheetVisibility(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPlaylistView();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void updateEmptyState() {
        if (bind == null) return;
        boolean allLoaded = playlistsLoaded && albumsLoaded && artistsLoaded && genresLoaded;
        boolean allEmpty = playlistsEmpty && albumsEmpty && artistsEmpty && genresEmpty;
        bind.libraryEmptyTextView.setVisibility(allLoaded && allEmpty ? View.VISIBLE : View.GONE);
    }

    private void init() {
        bind.albumCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_albumCatalogueFragment));
        bind.artistCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_artistCatalogueFragment));
        bind.genreCatalogueTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.action_libraryFragment_to_genreCatalogueFragment));
        bind.playlistCatalogueTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.PLAYLIST_ALL, Constants.PLAYLIST_ALL);
            activity.navController.navigate(R.id.action_libraryFragment_to_playlistCatalogueFragment, bundle);
        });

        bind.albumCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshAlbumSample(getViewLifecycleOwner());
            return true;
        });
        bind.albumCatalogueSampleTextViewRefreshable.setOnClickListener(v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );

        bind.artistCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshArtistSample(getViewLifecycleOwner());
            return true;
        });
        bind.artistCatalogueSampleTextViewRefreshable.setOnClickListener(v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );

        bind.genreCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshGenreSample(getViewLifecycleOwner());
            return true;
        });
        bind.genreCatalogueSampleTextViewRefreshable.setOnClickListener(v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );

        bind.playlistCatalogueSampleTextViewRefreshable.setOnLongClickListener(view -> {
            libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
            return true;
        });
        bind.playlistCatalogueSampleTextViewRefreshable.setOnClickListener(v ->
            Toast.makeText(requireContext(), R.string.library_toast_long_press_to_refresh, Toast.LENGTH_SHORT).show()
        );
    }

    private void initMusicFolderView() {
        if (!Preferences.isMusicDirectorySectionVisible()) {
            bind.libraryMusicFolderSector.setVisibility(View.GONE);
            return;
        }

        bind.musicFolderRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.musicFolderRecyclerView.setHasFixedSize(true);

        musicFolderAdapter = new MusicFolderAdapter(this);
        bind.musicFolderRecyclerView.setAdapter(musicFolderAdapter);
        libraryViewModel.getMusicFolders(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), musicFolders -> {
            if (musicFolders == null) {
                if (bind != null) bind.libraryMusicFolderSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryMusicFolderSector.setVisibility(!musicFolders.isEmpty() ? View.VISIBLE : View.GONE);

                musicFolderAdapter.setItems(musicFolders);
            }
        });
    }

    private void initAlbumView() {
        bind.albumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.albumRecyclerView.setHasFixedSize(true);

        albumAdapter = new AlbumAdapter(this, true);
        bind.albumRecyclerView.setAdapter(albumAdapter);
        libraryViewModel.getAlbumSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            albumsLoaded = true;
            albumsEmpty = albums == null || albums.isEmpty();
            if (albums == null) {
                if (bind != null) bind.libraryAlbumSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryAlbumSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                albumAdapter.setItems(albums);
            }
            updateEmptyState();
        });

        CustomLinearSnapHelper albumSnapHelper = new CustomLinearSnapHelper();
        albumSnapHelper.attachToRecyclerView(bind.albumRecyclerView);
    }

    private void initArtistView() {
        bind.artistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.artistRecyclerView.setHasFixedSize(true);

        artistAdapter = new ArtistAdapter(this, false, false, true);
        bind.artistRecyclerView.setAdapter(artistAdapter);
        libraryViewModel.getArtistSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            artistsLoaded = true;
            artistsEmpty = artists == null || artists.isEmpty();
            if (artists == null) {
                if (bind != null) bind.libraryArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                artistAdapter.setItems(artists);
            }
            updateEmptyState();
        });

        CustomLinearSnapHelper artistSnapHelper = new CustomLinearSnapHelper();
        artistSnapHelper.attachToRecyclerView(bind.artistRecyclerView);
    }

    private void initGenreView() {
        bind.genreRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false));
        bind.genreRecyclerView.setHasFixedSize(true);

        genreAdapter = new GenreAdapter(this);
        bind.genreRecyclerView.setAdapter(genreAdapter);

        libraryViewModel.getGenreSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), genres -> {
            genresLoaded = true;
            genresEmpty = genres == null || genres.isEmpty();
            if (genres == null) {
                if (bind != null) bind.libraryGenresSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.libraryGenresSector.setVisibility(!genres.isEmpty() ? View.VISIBLE : View.GONE);

                genreAdapter.setItems(genres);
            }
            updateEmptyState();
        });

        CustomLinearSnapHelper genreSnapHelper = new CustomLinearSnapHelper();
        genreSnapHelper.attachToRecyclerView(bind.genreRecyclerView);
    }

    private void initPlaylistView() {
        bind.playlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistRecyclerView.setHasFixedSize(true);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.playlistRecyclerView.setAdapter(playlistHorizontalAdapter);
        libraryViewModel.getPlaylistSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), playlists -> {
            playlistsLoaded = true;
            playlistsEmpty = playlists == null || playlists.isEmpty();
            if (playlists == null) {
                if (bind != null) bind.libraryPlaylistSector.setVisibility(View.GONE);
            } else {
                if (bind != null) {
                    bind.libraryPlaylistSector.setVisibility(!playlists.isEmpty() ? View.VISIBLE : View.GONE);
                    if (playlists.size() > 5) {
                        bind.playlistCatalogueTextViewClickable.setText(
                                getString(R.string.editorial_library_see_all_count, playlists.size())
                        );
                    } else {
                        bind.playlistCatalogueTextViewClickable.setText(
                                R.string.library_title_playlist_see_all_button
                        );
                    }
                }

                playlistHorizontalAdapter.setItems(playlists.subList(0, Math.min(5, playlists.size())));
            }
            updateEmptyState();
        });
    }

    private void refreshPlaylistView() {
        final Handler handler = new Handler(Looper.getMainLooper());

        final Runnable runnable = () -> {
            if (getView() != null && bind != null && libraryViewModel != null)
                libraryViewModel.refreshPlaylistSample(getViewLifecycleOwner());
        };

        handler.postDelayed(runnable, 100);
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }

    @Override
    public void onGenreClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistRowBottomSheetDialog, bundle);
    }

    @Override
    public void onMusicFolderClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.indexFragment, bundle);
    }

    public void initSwipeToRefresh() {
        bind.swipeLibraryToRefresh.setOnRefreshListener(() -> {
            pullToRefresh();
            bind.swipeLibraryToRefresh.setRefreshing(false);
        });
    }

    private void pullToRefresh() {
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        libraryViewModel.refreshAlbumSample(lifecycleOwner);
        libraryViewModel.refreshGenreSample(lifecycleOwner);
        libraryViewModel.refreshArtistSample(lifecycleOwner);
        libraryViewModel.refreshPlaylistSample(lifecycleOwner);
    }
}
