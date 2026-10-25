package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.ui.player.PlayerBackdrop;
import com.eddyizm.tempus.databinding.InnerFragmentPlayerLyricsBinding;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.Line;
import com.eddyizm.tempus.subsonic.models.LyricsList;
import com.eddyizm.tempus.subsonic.models.StructuredLyrics;
import com.eddyizm.tempus.ui.player.lyrics.LyricsComposeBridge;
import com.eddyizm.tempus.ui.player.lyrics.LyricsState;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.PlayerBottomSheetViewModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;


@OptIn(markerClass = UnstableApi.class)
public class PlayerLyricsFragment extends Fragment {
    private static final String TAG = "PlayerLyricsFragment";

    private InnerFragmentPlayerLyricsBinding bind;
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private MediaBrowser mediaBrowser;
    private final LyricsState lyricsState = new LyricsState();
    private String currentLyrics;
    private LyricsList currentLyricsList;
    private String currentDescription;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerLyricsBinding.inflate(PlayerBackdrop.themedInflater(inflater), container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        initOverlay();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initPanelContent();
        observeDownloadState();
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();

    }

    @Override
    public void onResume() {
        super.onResume();
        bindMediaController();
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!Preferences.isDisplayAlwaysOn()) {
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onStop() {
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
        currentLyrics = null;
        currentLyricsList = null;
        currentDescription = null;
    }

    private void initOverlay() {
        LyricsComposeBridge.bind(bind.nowPlayingLyricsComposeView, lyricsState, this::currentPosition, this::seekTo);
        lyricsState.setFollow(playerBottomSheetViewModel.getSyncLyricsState());
        bind.syncLyricsTapButton.setOnClickListener(view -> {
            playerBottomSheetViewModel.changeSyncLyricsState();
            lyricsState.setFollow(playerBottomSheetViewModel.getSyncLyricsState());
        });

        bind.downloadLyricsButton.setOnClickListener(view -> {
            boolean saved = playerBottomSheetViewModel.downloadCurrentLyrics();
            if (getContext() != null) {
                Toast.makeText(
                        requireContext(),
                        saved ? R.string.player_lyrics_download_success : R.string.player_lyrics_download_failure,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                mediaBrowser = mediaBrowserListenableFuture.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void initPanelContent() {
        playerBottomSheetViewModel.getLiveLyrics().observe(getViewLifecycleOwner(), lyrics -> {
            currentLyrics = lyrics;
            updatePanelContent();
        });

        playerBottomSheetViewModel.getLiveLyricsList().observe(getViewLifecycleOwner(), lyricsList -> {
            currentLyricsList = lyricsList;
            updatePanelContent();
        });

        playerBottomSheetViewModel.getLiveDescription().observe(getViewLifecycleOwner(), description -> {
            currentDescription = description;
            updatePanelContent();
        });
    }

    private void observeDownloadState() {
        playerBottomSheetViewModel.getLyricsCachedState().observe(getViewLifecycleOwner(), cached -> {
            if (bind != null) {
                MaterialButton downloadButton = (MaterialButton) bind.downloadLyricsButton;
                if (cached != null && cached) {
                    downloadButton.setIconResource(R.drawable.ic_done);
                    downloadButton.setContentDescription(getString(R.string.player_lyrics_downloaded_content_description));
                } else {
                    downloadButton.setIconResource(R.drawable.ic_download);
                    downloadButton.setContentDescription(getString(R.string.player_lyrics_download_content_description));
                }
            }
        });
    }

    private void updatePanelContent() {
        if (bind == null) {
            return;
        }

        boolean showText = true;
        if (hasStructuredLyrics(currentLyricsList)) {
            StructuredLyrics structured = currentLyricsList.getStructuredLyrics().get(0);
            if (structured.getSynced()) {
                List<Long> starts = new ArrayList<>();
                List<String> texts = new ArrayList<>();
                for (Line line : structured.getLine()) {
                    starts.add(line.getStart() == null ? 0L : line.getStart().longValue());
                    texts.add(line.getValue());
                }
                lyricsState.showSynced(LyricsState.buildLines(starts, texts));
            } else {
                StringBuilder lyricsBuilder = new StringBuilder();
                for (Line line : structured.getLine()) {
                    lyricsBuilder.append(line.getValue().trim()).append("\n\n");
                }
                lyricsState.showPlain(lyricsBuilder.toString().trim());
            }
            bind.syncLyricsTapButton.setVisibility(structured.getSynced() ? View.VISIBLE : View.GONE);
            bind.downloadLyricsButton.setVisibility(View.VISIBLE);
            bind.downloadLyricsButton.setEnabled(true);
        } else if (hasText(currentLyrics)) {
            lyricsState.showPlain(MusicUtil.getReadableLyrics(currentLyrics));
            bind.syncLyricsTapButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.VISIBLE);
            bind.downloadLyricsButton.setEnabled(true);
        } else if (hasText(currentDescription)) {
            lyricsState.showPlain(MusicUtil.getReadableLyrics(currentDescription));
            bind.syncLyricsTapButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setEnabled(false);
        } else {
            lyricsState.showPlain(null);
            showText = false;
            bind.syncLyricsTapButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setEnabled(false);
        }
        bind.nowPlayingLyricsComposeView.setVisibility(showText ? View.VISIBLE : View.GONE);
        bind.emptyDescriptionImageView.setVisibility(showText ? View.GONE : View.VISIBLE);
        bind.titleEmptyDescriptionLabel.setVisibility(showText ? View.GONE : View.VISIBLE);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean hasStructuredLyrics(LyricsList lyricsList) {
        return lyricsList != null
                && lyricsList.getStructuredLyrics() != null
                && !lyricsList.getStructuredLyrics().isEmpty()
                && lyricsList.getStructuredLyrics().get(0) != null
                && lyricsList.getStructuredLyrics().get(0).getLine() != null
                && !lyricsList.getStructuredLyrics().get(0).getLine().isEmpty();
    }

    private long currentPosition() {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
            return com.eddyizm.tempus.lan.LanRemoteSession.current().positionAt(android.os.SystemClock.elapsedRealtime());
        }
        return mediaBrowser != null ? mediaBrowser.getCurrentPosition() : 0L;
    }

    private void seekTo(long positionMs) {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
            com.eddyizm.tempus.lan.LanRemoteSession.command("seek", positionMs);
        } else if (mediaBrowser != null) {
            mediaBrowser.seekTo(positionMs);
        }
    }
}
