package com.eddyizm.tempus.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TooltipCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Format;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.RepeatModeUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.transition.ChangeBounds;
import androidx.transition.Slide;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import androidx.viewpager2.widget.ViewPager2;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.InnerFragmentPlayerControllerBinding;
import com.eddyizm.tempus.service.BaseMediaService;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.ReplayGainInfo;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.dialog.PlaybackSpeedDialog;
import com.eddyizm.tempus.ui.dialog.PlaylistChooserDialog;
import com.eddyizm.tempus.ui.dialog.RatingDialog;
import com.eddyizm.tempus.ui.dialog.SleepTimerDialog;
import com.eddyizm.tempus.ui.dialog.TrackInfoDialog;
import com.eddyizm.tempus.ui.fragment.pager.PlayerControllerHorizontalPager;
import com.eddyizm.tempus.ui.player.PlayerBackdrop;
import com.eddyizm.tempus.ui.player.PlayerMotion;
import com.eddyizm.tempus.ui.player.PlayerSheets;
import com.eddyizm.tempus.ui.player.PressFeedback;
import com.eddyizm.tempus.upnp.UpnpPlayer;
import com.eddyizm.tempus.upnp.UpnpRouteProvider;
import com.eddyizm.tempus.util.AssetLinkUtil;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.FavoriteRegistry;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.ReplayGainBundleUtil;
import com.eddyizm.tempus.util.SleepTimerManager;
import com.eddyizm.tempus.viewmodel.PlayerBottomSheetViewModel;
import com.eddyizm.tempus.viewmodel.RatingViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@UnstableApi
public class PlayerControllerFragment extends Fragment {
    private static final String TAG = "PlayerCoverFragment";

    // Editorial Warm Dark ("唱片店夜色") tokens
    private static final int COLOR_SURFACE_3 = 0xFF28241F;
    private static final int COLOR_FROSTED_CAPSULE = 0xCC1E1B18;
    private static final int COLOR_HAIRLINE = 0x14FFF0DC;
    private static final int COLOR_IVORY = 0xFFF2ECE3;
    private static final int COLOR_IVORY_SECONDARY = 0xA3F2ECE3;
    private static final int COLOR_IVORY_MUTED = 0x80F2ECE3;
    private static final int COLOR_BRASS = 0xFFCFA46A;
    private static final int COLOR_BRASS_SUBTLE = 0x22CFA46A;
    private static final int COLOR_BRASS_BORDER = 0x88CFA46A;
    private static final int COLOR_MOSS = 0xFF8DB596;
    private static final int COLOR_MOSS_SUBTLE = 0x228DB596;
    private static final int COLOR_MOSS_BORDER = 0x888DB596;
    private static final int COLOR_ON_BRASS = 0xFF0E0D0C;

    private InnerFragmentPlayerControllerBinding bind;
    private com.eddyizm.tempus.lan.LanPlayerAdapter remotePlayer;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsChangeListener = (prefs, key) -> {
        if (bind == null || !isAdded()) return;
        updateRemoteToggleVisibility();
        refreshSignalBadges();
        updateCapsuleState();
    };
    private boolean renderingRemote;
    private ViewPager2 playerMediaCoverViewPager;
    private ToggleButton buttonFavorite;
    @Nullable private ImageButton playerOverflowButton;
    private RatingViewModel ratingViewModel;
    @Nullable private RatingBar songRatingBar;
    private TextView playerMediaTitleLabel;
    private TextView playerArtistNameLabel;
    @Nullable private TextView playerOutputLabel;
    @Nullable private Button playbackSpeedButton;
    @Nullable private ToggleButton skipSilenceToggleButton;
    @Nullable private Chip playerMediaExtension;
    @Nullable private TextView playerMediaBitrate;
    @Nullable private ConstraintLayout playerQuickActionView;
    @Nullable private View playerOpenQueueButton;
    @Nullable private View playerOpenLyricsButton;
    @Nullable private TextView playerContextLabel;
    @Nullable private ImageButton playModeButton;
    @Nullable private View playerFavoriteSurface;
    private boolean immersiveLayout;
    @Nullable private ImageButton playerTrackInfo;
    @Nullable private LinearLayout ratingContainer;
    @Nullable private LinearLayout sleepTimerContainer;
    @Nullable private ImageButton sleepTimerButton;
    @Nullable private TextView sleepTimerLabel;
    @Nullable private ChipGroup assetLinkChipGroup;
    @Nullable private Chip playerSongLinkChip;
    @Nullable private Chip playerAlbumLinkChip;
    @Nullable private Chip playerArtistLinkChip;

    // Editorial Warm Dark surfaced views (null-guarded across all 4 layout variants)
    @Nullable private View playerTopOutputPill;
    @Nullable private ImageView playerTopOutputIcon;
    @Nullable private TextView playerTopOutputText;
    @Nullable private View playerSignalBadgesRow;
    @Nullable private TextView playerBadgeCodec;
    @Nullable private TextView playerBadgeRate;
    @Nullable private TextView playerBadgeRg;
    @Nullable private TextView playerBadgeEq;
    @Nullable private View playerCapsuleBar;
    @Nullable private ImageView playerCapsuleLyricsIcon;
    @Nullable private TextView playerCapsuleLyricsText;
    @Nullable private View playerCapsuleOutputButton;
    @Nullable private ImageView playerCapsuleOutputIcon;
    @Nullable private TextView playerCapsuleOutputText;
    @Nullable private View playerCapsuleOutputDot;
    @Nullable private View playerCapsuleSoundButton;
    @Nullable private ImageView playerCapsuleSoundIcon;
    @Nullable private TextView playerCapsuleSoundText;
    @Nullable private View playerCapsuleSoundDot;
    @Nullable private ImageView playerCapsuleQueueIcon;
    @Nullable private TextView playerCapsuleQueueText;
    private boolean queueSheetOpen = false;

    @Nullable private MediaRouter mediaRouter;
    private final MediaRouter.Callback mediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route, int reason) {
            onRoutesUpdated();
        }

        @Override
        public void onRouteUnselected(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route, int reason) {
            onRoutesUpdated();
        }

        @Override
        public void onRouteChanged(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route) {
            onRoutesUpdated();
        }

        private void onRoutesUpdated() {
            if (bind == null || !isAdded()) return;
            updatePlaybackDestinationLabel();
            refreshSignalBadges();
            updateCapsuleState();
        }
    };

    private MainActivity activity;
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private BaseMediaService.LocalBinder mediaServiceBinder;
    private boolean isServiceBound = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = InnerFragmentPlayerControllerBinding.inflate(PlayerBackdrop.themedInflater(inflater), container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);
        ratingViewModel = new ViewModelProvider(requireActivity()).get(RatingViewModel.class);

        init();
        remotePlayer = new com.eddyizm.tempus.lan.LanPlayerAdapter();
        App.getInstance().getPreferences().registerOnSharedPreferenceChangeListener(prefsChangeListener);
        com.eddyizm.tempus.lan.LanRemoteSession.state().observe(getViewLifecycleOwner(), state -> refreshPlaybackDestination());
        com.eddyizm.tempus.lan.LanRemoteSession.queueState().observe(getViewLifecycleOwner(), queue -> refreshPlaybackDestination());
        bind.playerRemoteToggle.setVisibility(View.GONE);
        bind.playerRemoteToggle.setOnClickListener(v -> openOutputRouteSheet());
        initOverflowButton();
        initQuickActionView();
        initCoverLyricsSlideView();
        initMediaListenable();
        initMediaLabelButton();
        initArtistLabelButton();
        View collapse = view.findViewById(R.id.player_collapse_button);
        if (collapse != null) collapse.setOnClickListener(v -> activity.collapseBottomSheetDelayed());
        playerBottomSheetViewModel.getLiveLyrics().observe(getViewLifecycleOwner(), ignored -> updateLyricsAvailability());
        playerBottomSheetViewModel.getLiveLyricsList().observe(getViewLifecycleOwner(), ignored -> updateLyricsAvailability());

        updatePlaybackDestinationLabel();
        refreshSignalBadges();
        updateSleepTimerUI();
        updateLyricsAvailability();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        registerRouteObserver();
        initializeBrowser();
        bindMediaController();
    }

    @Override
    public void onStop() {
        unregisterRouteObserver();
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        PlayerMotion.setLyricsShowing(false);
        App.getInstance().getPreferences().unregisterOnSharedPreferenceChangeListener(prefsChangeListener);
        if (bind != null) bind.nowPlayingMediaControllerView.setPlayer(null);
        if (remotePlayer != null) remotePlayer.release();
        remotePlayer = null;
        bind = null;
    }

    private void registerRouteObserver() {
        try {
            mediaRouter = MediaRouter.getInstance(requireContext());
            MediaRouteSelector selector = new MediaRouteSelector.Builder()
                    .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                    .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                    .addControlCategory(UpnpRouteProvider.CATEGORY_UPNP)
                    .build();
            mediaRouter.addCallback(selector, mediaRouterCallback, 0);
        } catch (Exception ignored) {
            mediaRouter = null;
        }
    }

    private void unregisterRouteObserver() {
        if (mediaRouter != null) {
            try {
                mediaRouter.removeCallback(mediaRouterCallback);
            } catch (Exception ignored) {
            }
            mediaRouter = null;
        }
    }

    private void openSignalAndSoundSheet() {
        if (!isAdded()) return;
        PlayerSheets.showSignalAndSound(requireActivity(), this::navigateToEqualizerFragment);
    }

    private void openOutputRouteSheet() {
        if (!isAdded()) return;
        PlayerSheets.showOutputRoute(requireActivity());
    }

    private void refreshPlaybackDestination() {
        if (bind == null || remotePlayer == null) return;
        updateRemoteToggleVisibility();
        updatePlayModeButton();
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
            remotePlayer.refresh();
            bind.nowPlayingMediaControllerView.setPlayer(remotePlayer);
            com.eddyizm.tempus.lan.LanRemoteState state = com.eddyizm.tempus.lan.LanRemoteSession.current();
            bind.playerRemoteToggle.setText(getString(R.string.lan_operating_on, state.getName()));
            bind.playerRemoteToggle.setContentDescription(getString(R.string.lan_controlling, state.getName()));
            bind.playerRemoteToggle.setSelected(true);
            bind.nowPlayingMediaControllerView.setShowShuffleButton(true);
            bind.nowPlayingMediaControllerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL | RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE);
            bind.nowPlayingMediaControllerView.setShowRewindButton(false);
            bind.nowPlayingMediaControllerView.setShowFastForwardButton(false);
            bind.nowPlayingMediaControllerView.setShowPreviousButton(true);
            bind.nowPlayingMediaControllerView.setShowNextButton(true);
            if (playbackSpeedButton != null) playbackSpeedButton.setVisibility(View.VISIBLE);
            if (skipSilenceToggleButton != null) skipSilenceToggleButton.setVisibility(View.GONE);
            if (buttonFavorite != null) buttonFavorite.setVisibility(View.VISIBLE);
            syncFavoriteSurface();
            var displayedSong = playerBottomSheetViewModel.getLiveMedia().getValue();
            if (buttonFavorite != null) {
                buttonFavorite.setEnabled(displayedSong != null && Objects.equals(displayedSong.getId(), state.getMediaId()));
            }
            renderingRemote = true;
            try {
                MediaMetadata metadata = com.eddyizm.tempus.lan.LanPlayerAdapter.metadata(com.eddyizm.tempus.lan.LanRemoteSession.currentSong());
                setMetadata(metadata);
                setMediaInfo(metadata);
                if (playbackSpeedButton != null) {
                    playbackSpeedButton.setText(getString(R.string.player_playback_speed, state.getSpeed()));
                }
                updateSleepTimerUI();
                refreshSignalBadges();
                updateCapsuleState();
            } finally {
                renderingRemote = false;
            }
        } else {
            bind.playerRemoteToggle.setText(R.string.lan_play_on);
            bind.playerRemoteToggle.setContentDescription(getString(R.string.lan_play_on));
            bind.playerRemoteToggle.setSelected(false);
            if (buttonFavorite != null) buttonFavorite.setEnabled(true);
            MediaBrowser browser = getBrowser();
            if (browser != null) {
                bind.nowPlayingMediaControllerView.setPlayer(browser);
                setMediaControllerUI(browser);
                setMetadata(browser.getMediaMetadata());
                setMediaInfo(browser.getMediaMetadata());
            }
            refreshSignalBadges();
            updateCapsuleState();
        }
    }

    private void init() {
        View root = bind.getRoot();
        playerMediaCoverViewPager = root.findViewById(R.id.player_media_cover_view_pager);
        buttonFavorite = root.findViewById(R.id.button_favorite);
        playerOverflowButton = root.findViewById(R.id.player_overflow_button);
        playerMediaTitleLabel = root.findViewById(R.id.player_media_title_label);
        playerArtistNameLabel = root.findViewById(R.id.player_artist_name_label);
        playerOutputLabel = root.findViewById(R.id.player_output_label);
        playbackSpeedButton = root.findViewById(R.id.player_playback_speed_button);
        skipSilenceToggleButton = root.findViewById(R.id.player_skip_silence_toggle_button);
        playerMediaExtension = root.findViewById(R.id.player_media_extension);
        playerMediaBitrate = root.findViewById(R.id.player_media_bitrate);
        playerQuickActionView = root.findViewById(R.id.player_quick_action_view);
        playerOpenQueueButton = root.findViewById(R.id.player_open_queue_button);
        playerOpenLyricsButton = root.findViewById(R.id.player_open_lyrics_button);
        if (playerOpenQueueButton != null) playerOpenQueueButton.setSaveFromParentEnabled(false);
        if (playerOpenLyricsButton != null) playerOpenLyricsButton.setSaveFromParentEnabled(false);
        playerTrackInfo = root.findViewById(R.id.player_info_track);
        songRatingBar = root.findViewById(R.id.song_rating_bar);
        ratingContainer = root.findViewById(R.id.rating_container);
        assetLinkChipGroup = root.findViewById(R.id.asset_link_chip_group);
        playerSongLinkChip = root.findViewById(R.id.asset_link_song_chip);
        playerAlbumLinkChip = root.findViewById(R.id.asset_link_album_chip);
        playerArtistLinkChip = root.findViewById(R.id.asset_link_artist_chip);
        sleepTimerContainer = root.findViewById(R.id.player_sleep_timer_container);
        sleepTimerButton = root.findViewById(R.id.player_sleep_timer_button);
        sleepTimerLabel = root.findViewById(R.id.player_sleep_timer_label);
        playerContextLabel = root.findViewById(R.id.player_context_label);
        playModeButton = root.findViewById(R.id.player_play_mode_button);
        if (playModeButton != null) playModeButton.setOnClickListener(v -> cyclePlayMode());
        playerFavoriteSurface = root.findViewById(R.id.player_favorite_surface);

        // Bind Editorial Warm Dark surfaced controls
        playerTopOutputPill = root.findViewById(R.id.player_top_output_pill);
        playerTopOutputIcon = root.findViewById(R.id.player_top_output_icon);
        playerTopOutputText = root.findViewById(R.id.player_top_output_text);

        playerSignalBadgesRow = root.findViewById(R.id.player_signal_badges_row);
        playerBadgeCodec = root.findViewById(R.id.player_badge_codec);
        playerBadgeRate = root.findViewById(R.id.player_badge_rate);
        playerBadgeRg = root.findViewById(R.id.player_badge_rg);
        playerBadgeEq = root.findViewById(R.id.player_badge_eq);

        playerCapsuleBar = root.findViewById(R.id.player_capsule_bar);
        playerCapsuleLyricsIcon = root.findViewById(R.id.player_capsule_lyrics_icon);
        playerCapsuleLyricsText = root.findViewById(R.id.player_capsule_lyrics_text);
        playerCapsuleOutputButton = root.findViewById(R.id.player_capsule_output_button);
        playerCapsuleOutputIcon = root.findViewById(R.id.player_capsule_output_icon);
        playerCapsuleOutputText = root.findViewById(R.id.player_capsule_output_text);
        playerCapsuleOutputDot = root.findViewById(R.id.player_capsule_output_dot);
        playerCapsuleSoundButton = root.findViewById(R.id.player_capsule_sound_button);
        playerCapsuleSoundIcon = root.findViewById(R.id.player_capsule_sound_icon);
        playerCapsuleSoundText = root.findViewById(R.id.player_capsule_sound_text);
        playerCapsuleSoundDot = root.findViewById(R.id.player_capsule_sound_dot);
        playerCapsuleQueueIcon = root.findViewById(R.id.player_capsule_queue_icon);
        playerCapsuleQueueText = root.findViewById(R.id.player_capsule_queue_text);

        immersiveLayout = playerContextLabel != null;

        // Style hero play/pause button in Stylus Brass (#CFA46A) with deep charcoal glyph
        View playPauseButton = root.findViewById(R.id.exo_play_pause);
        if (playPauseButton != null) {
            ViewCompat.setBackgroundTintList(playPauseButton, ColorStateList.valueOf(COLOR_BRASS));
            if (playPauseButton instanceof ImageView) {
                ImageViewCompat.setImageTintList((ImageView) playPauseButton, ColorStateList.valueOf(COLOR_ON_BRASS));
            }
        }

        for (int id : new int[]{R.id.exo_prev, R.id.exo_play_pause, R.id.exo_next, R.id.exo_rew, R.id.exo_ffwd}) {
            View button = root.findViewById(id);
            if (button != null) PressFeedback.attach(button);
        }

        if (playerOutputLabel != null) {
            playerOutputLabel.setOnClickListener(v -> openOutputRouteSheet());
        }

        if (playerTopOutputPill != null) {
            PressFeedback.attach(playerTopOutputPill);
            playerTopOutputPill.setOnClickListener(v -> openOutputRouteSheet());
        }

        View.OnClickListener signalSheetClick = v -> openSignalAndSoundSheet();
        if (playerSignalBadgesRow != null) {
            PressFeedback.attach(playerSignalBadgesRow);
            playerSignalBadgesRow.setOnClickListener(signalSheetClick);
        }
        for (View badge : new View[]{playerBadgeCodec, playerBadgeRate, playerBadgeRg, playerBadgeEq}) {
            if (badge != null) {
                badge.setOnClickListener(signalSheetClick);
            }
        }

        if (playerCapsuleBar != null) {
            playerCapsuleBar.setBackground(makeRoundedRect(999f, COLOR_FROSTED_CAPSULE, 1f, COLOR_HAIRLINE));
        }
        if (playerCapsuleOutputDot != null) {
            playerCapsuleOutputDot.setBackground(makeOval(COLOR_BRASS));
        }
        if (playerCapsuleSoundDot != null) {
            playerCapsuleSoundDot.setBackground(makeOval(COLOR_BRASS));
        }

        checkAndSetRatingContainerVisibility();
    }

    private GradientDrawable makeRoundedRect(float radiusDp, int fillColor, float strokeDp, int strokeColor) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusDp * density);
        d.setColor(fillColor);
        if (strokeDp > 0f) {
            d.setStroke(Math.max(1, Math.round(strokeDp * density)), strokeColor);
        }
        return d;
    }

    private GradientDrawable makeOval(int fillColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fillColor);
        return d;
    }

    private com.eddyizm.tempus.ui.player.PlayMode currentPlayMode() {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
            com.eddyizm.tempus.lan.LanRemoteState state = com.eddyizm.tempus.lan.LanRemoteSession.current();
            return com.eddyizm.tempus.ui.player.PlayMode.of(state.getShuffle(), state.getRepeat());
        }
        MediaBrowser browser = getBrowser();
        return browser != null
                ? com.eddyizm.tempus.ui.player.PlayMode.of(browser.getShuffleModeEnabled(), browser.getRepeatMode())
                : com.eddyizm.tempus.ui.player.PlayMode.of(Preferences.isShuffleModeEnabled(), Preferences.getRepeatMode());
    }

    /** Steps the play order: repeat queue, shuffle, repeat one, then in order without repeat. */
    private void cyclePlayMode() {
        com.eddyizm.tempus.ui.player.PlayMode next = currentPlayMode().next();
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
            com.eddyizm.tempus.lan.LanRemoteSession.command("shuffle", next.getShuffle() ? 1 : 0);
            com.eddyizm.tempus.lan.LanRemoteSession.command("repeat", next.getRepeat());
        } else {
            MediaBrowser browser = getBrowser();
            if (browser == null) return;
            browser.setShuffleModeEnabled(next.getShuffle());
            browser.setRepeatMode(next.getRepeat());
        }
        showPlayMode(next);
        Toast.makeText(requireContext(), next.getLabel(), Toast.LENGTH_SHORT).show();
    }

    private void updatePlayModeButton() {
        if (playModeButton != null) showPlayMode(currentPlayMode());
    }

    private void showPlayMode(com.eddyizm.tempus.ui.player.PlayMode mode) {
        if (playModeButton == null) return;
        playModeButton.setImageResource(mode.getIcon());
        playModeButton.setContentDescription(getString(mode.getLabel()));
        TooltipCompat.setTooltipText(playModeButton, getString(mode.getLabel()));
    }

    private void syncFavoriteSurface() {
        if (playerFavoriteSurface != null && buttonFavorite != null) {
            playerFavoriteSurface.setVisibility(buttonFavorite.getVisibility());
        }
    }

    private void initOverflowButton() {
        if (playerOverflowButton != null) {
            playerOverflowButton.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(requireContext(), v);
                popup.inflate(R.menu.player_overflow_menu);
                if (popup.getMenu().findItem(R.id.action_show_lyrics) != null) {
                    popup.getMenu().findItem(R.id.action_show_lyrics).setVisible(playerBottomSheetViewModel.hasLyrics());
                }
                View audioDetails = bind.getRoot().findViewById(R.id.player_media_quality_sector);
                if (popup.getMenu().findItem(R.id.action_audio_details) != null && audioDetails != null) {
                    popup.getMenu().findItem(R.id.action_audio_details)
                            .setVisible(playerOutputLabel != null)
                            .setChecked(audioDetails.getVisibility() == View.VISIBLE);
                }

                if (!com.eddyizm.tempus.lan.LanDevicePicker.isOffered()
                        && popup.getMenu().findItem(R.id.action_lan_playback) != null) {
                    popup.getMenu().removeItem(R.id.action_lan_playback);
                }

                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_signal_and_sound) {
                        openSignalAndSoundSheet();
                        return true;
                    } else if (itemId == R.id.action_output_route) {
                        openOutputRouteSheet();
                        return true;
                    } else if (itemId == R.id.action_track_info) {
                        if (playerTrackInfo != null) playerTrackInfo.performClick();
                        return true;
                    } else if (itemId == R.id.action_playback_speed) {
                        if (playbackSpeedButton != null) playbackSpeedButton.performClick();
                        else openSignalAndSoundSheet();
                        return true;
                    } else if (itemId == R.id.action_sleep_timer) {
                        if (sleepTimerButton != null) sleepTimerButton.performClick();
                        else openSignalAndSoundSheet();
                        return true;
                    } else if (itemId == R.id.action_show_lyrics) {
                        goToLyricsPage();
                        return true;
                    } else if (itemId == R.id.action_audio_details) {
                        if (audioDetails != null) {
                            boolean show = audioDetails.getVisibility() != View.VISIBLE;
                            audioDetails.setVisibility(show ? View.VISIBLE : View.GONE);
                            View assetRow = bind.getRoot().findViewById(R.id.player_asset_link_row);
                            if (assetRow != null) assetRow.setVisibility(show ? View.VISIBLE : View.GONE);
                        }
                        return true;
                    } else if (itemId == R.id.action_lan_playback) {
                        openOutputRouteSheet();
                        return true;
                    } else if (itemId == R.id.action_open_equalizer) {
                        navigateToEqualizerFragment();
                        return true;
                    } else if (itemId == R.id.action_add_to_playlist) {
                        openAddToPlaylistDialog();
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }

    private void initQuickActionView() {
        if (playerQuickActionView != null) {
            playerQuickActionView.setVisibility(Preferences.getQuickActionVisible() ? View.VISIBLE : View.GONE);
        }

        if (playerOpenLyricsButton != null) {
            PressFeedback.attach(playerOpenLyricsButton);
            playerOpenLyricsButton.setOnClickListener(view -> {
                if (!playerBottomSheetViewModel.hasLyrics()) {
                    if (isLyricsPage()) goToControllerPage();
                    Toast.makeText(requireContext(), R.string.editorial_player_no_lyrics_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                int currentItem = playerMediaCoverViewPager.getCurrentItem();
                if (currentItem == 0) {
                    playerMediaCoverViewPager.setCurrentItem(1, true);
                } else if (currentItem == 1) {
                    playerMediaCoverViewPager.setCurrentItem(0, true);
                }
            });
        }

        if (playerCapsuleOutputButton != null) {
            PressFeedback.attach(playerCapsuleOutputButton);
            playerCapsuleOutputButton.setOnClickListener(view -> openOutputRouteSheet());
        }

        if (playerCapsuleSoundButton != null) {
            PressFeedback.attach(playerCapsuleSoundButton);
            playerCapsuleSoundButton.setOnClickListener(view -> openSignalAndSoundSheet());
        }

        if (playerOpenQueueButton != null) {
            PressFeedback.attach(playerOpenQueueButton);
            playerOpenQueueButton.setOnClickListener(view -> {
                PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) requireActivity()
                        .getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
                if (playerBottomSheetFragment != null) {
                    playerBottomSheetFragment.goToQueuePage();
                }
            });
        }

        updateQueueButtonTint(false);
        updateCapsuleState();
    }

    private void updateLyricsAvailability() {
        if (bind == null) return;
        boolean hasLyrics = playerBottomSheetViewModel.hasLyrics();
        if (playerOpenLyricsButton != null) {
            // Keep all 4 capsule slots permanently balanced; dim Lyrics slot when unavailable.
            playerOpenLyricsButton.setVisibility(View.VISIBLE);
            playerOpenLyricsButton.setAlpha(hasLyrics ? 1.0f : 0.38f);
        }
        if (!hasLyrics && isLyricsPage()) {
            goToControllerPage();
        }
        updateCapsuleState();
    }

    private void updatePlaybackDestinationLabel() {
        if (bind == null || !isAdded()) return;
        Context context = requireContext();
        PlayerSheets.OutputSummary summary = PlayerSheets.currentOutputSummary(context);

        if (playerOutputLabel != null) {
            playerOutputLabel.setText(summary.getLabel());
        }

        if (playerTopOutputPill != null) {
            boolean remote = summary.isRemote();
            playerTopOutputPill.setBackground(makeRoundedRect(
                    999f,
                    remote ? COLOR_BRASS_SUBTLE : COLOR_SURFACE_3,
                    1f,
                    remote ? COLOR_BRASS_BORDER : COLOR_HAIRLINE
            ));
            int contentColor = remote ? COLOR_BRASS : COLOR_IVORY;
            if (playerTopOutputIcon != null) {
                playerTopOutputIcon.setImageResource(summary.getIconRes());
                ImageViewCompat.setImageTintList(playerTopOutputIcon, ColorStateList.valueOf(contentColor));
            }
            if (playerTopOutputText != null) {
                setTextIfChanged(playerTopOutputText, summary.getLabel());
                playerTopOutputText.setTextColor(contentColor);
            }
        }
    }

    private void refreshSignalBadges() {
        if (bind == null || !isAdded()) return;
        Context context = requireContext();

        boolean lanActive = com.eddyizm.tempus.lan.LanRemoteSession.isActive();
        MediaBrowser browser = getBrowser();
        MediaMetadata metadata = lanActive
                ? com.eddyizm.tempus.lan.LanPlayerAdapter.metadata(com.eddyizm.tempus.lan.LanRemoteSession.currentSong())
                : (browser != null ? browser.getMediaMetadata() : null);
        Bundle extras = metadata != null ? metadata.extras : null;

        // 1 & 2. Codec + Sample Rate / Bitrate Badges
        String codecUpper = "";
        boolean transcoded = false;
        int bitDepth = 0;
        int sampleRateHz = 0;
        int bitrateKbps = 0;

        if (lanActive) {
            Child remoteSong = com.eddyizm.tempus.lan.LanRemoteSession.currentSong();
            String ext = remoteSong != null ? remoteSong.getSuffix() : null;
            codecUpper = !TextUtils.isEmpty(ext) ? ext.toUpperCase(Locale.ROOT) : "STREAM";
            if (remoteSong != null && remoteSong.getBitrate() != null && remoteSong.getBitrate() > 0) {
                bitrateKbps = remoteSong.getBitrate();
            }
        } else {
            boolean isLocalTrack = MusicUtil.isCurrentTrackLocal(browser);
            Format format = MusicUtil.getCurrentAudioFormat(browser);
            boolean renderer = format != null && UpnpPlayer.FORMAT_ID.equals(format.id);
            boolean unknownRendererFormat = renderer && MimeTypes.AUDIO_UNKNOWN.equals(format.sampleMimeType);

            String actual = null;
            if (!isLocalTrack && format != null && !unknownRendererFormat) {
                actual = MusicUtil.audioFormatLabel(format.sampleMimeType);
                String original = renderer
                        ? (extras != null ? MusicUtil.sourceSuffix(extras) : null)
                        : MusicUtil.getCurrentOriginalSuffix(browser);
                transcoded = MusicUtil.isTranscodedFormat(actual, original);
            }

            String suffix = extras != null ? extras.getString("suffix") : null;
            String rawCodec = !TextUtils.isEmpty(actual) ? actual
                    : (!TextUtils.isEmpty(suffix) ? suffix : context.getString(R.string.player_unknown_format));
            codecUpper = rawCodec.toUpperCase(Locale.ROOT);

            if (extras != null) {
                bitDepth = extras.getInt("bitDepth", 0);
                sampleRateHz = extras.getInt("samplingRate", 0);
                bitrateKbps = extras.getInt("bitrate", 0);
            }
            if (sampleRateHz <= 0 && format != null && format.sampleRate != Format.NO_VALUE && format.sampleRate > 0) {
                sampleRateHz = format.sampleRate;
            }
            if (transcoded) {
                if (format != null && format.bitrate != Format.NO_VALUE && format.bitrate > 0) {
                    bitrateKbps = format.bitrate / 1000;
                } else {
                    String targetBr = MusicUtil.getBitratePreference();
                    try {
                        int parsed = Integer.parseInt(targetBr);
                        if (parsed > 0) bitrateKbps = parsed;
                    } catch (Exception ignored) {
                    }
                }
            } else if (bitrateKbps <= 0 && format != null && format.bitrate != Format.NO_VALUE && format.bitrate > 0) {
                bitrateKbps = format.bitrate / 1000;
            }
        }

        boolean losslessCodec = !transcoded && isLosslessCodec(codecUpper);
        boolean hiResAudio = !transcoded && (bitDepth >= 24 || sampleRateHz > 48000);
        boolean brassCodecBadge = losslessCodec || hiResAudio;

        if (playerBadgeCodec != null) {
            String codecDisplay = transcoded
                    ? context.getString(R.string.editorial_badge_transcoded, codecUpper)
                    : codecUpper;
            setTextIfChanged(playerBadgeCodec, codecDisplay);
            playerBadgeCodec.setBackground(makeRoundedRect(
                    6f,
                    brassCodecBadge ? COLOR_BRASS_SUBTLE : COLOR_SURFACE_3,
                    1f,
                    brassCodecBadge ? COLOR_BRASS : COLOR_HAIRLINE
            ));
            playerBadgeCodec.setTextColor(brassCodecBadge ? COLOR_BRASS : COLOR_IVORY_SECONDARY);
            playerBadgeCodec.setVisibility(View.VISIBLE);
        }

        if (playerBadgeRate != null) {
            String rateLabel = "";
            DecimalFormat khzFormat = new DecimalFormat("0.#");
            if (!transcoded && bitDepth > 0 && sampleRateHz > 0) {
                rateLabel = bitDepth + "/" + khzFormat.format(sampleRateHz / 1000.0) + " kHz";
            } else if (!transcoded && losslessCodec && sampleRateHz > 0) {
                rateLabel = khzFormat.format(sampleRateHz / 1000.0) + " kHz";
            } else if (bitrateKbps > 0) {
                rateLabel = bitrateKbps + " kbps";
            } else if (!transcoded && sampleRateHz > 0) {
                rateLabel = khzFormat.format(sampleRateHz / 1000.0) + " kHz";
            }

            if (!rateLabel.isEmpty()) {
                setTextIfChanged(playerBadgeRate, rateLabel);
                playerBadgeRate.setBackground(makeRoundedRect(
                        6f,
                        hiResAudio ? COLOR_BRASS_SUBTLE : COLOR_SURFACE_3,
                        1f,
                        hiResAudio ? COLOR_BRASS_BORDER : COLOR_HAIRLINE
                ));
                playerBadgeRate.setTextColor(hiResAudio ? COLOR_BRASS : COLOR_IVORY);
                playerBadgeRate.setVisibility(View.VISIBLE);
            } else {
                playerBadgeRate.setVisibility(View.GONE);
            }
        }

        // 3. ReplayGain Badge
        if (playerBadgeRg != null) {
            String rgMode = Preferences.getReplayGainMode();
            if (rgMode == null || "disabled".equals(rgMode)) {
                playerBadgeRg.setVisibility(View.GONE);
            } else {
                ReplayGainInfo rgInfo = ReplayGainBundleUtil.fromBundle(extras);
                Float gainDb = null;
                if (rgInfo != null) {
                    if ("album".equals(rgMode)) {
                        gainDb = rgInfo.getAlbumGain() != null ? rgInfo.getAlbumGain()
                                : (rgInfo.getTrackGain() != null ? rgInfo.getTrackGain() : rgInfo.getFallbackGain());
                    } else {
                        gainDb = rgInfo.getTrackGain() != null ? rgInfo.getTrackGain()
                                : (rgInfo.getAlbumGain() != null ? rgInfo.getAlbumGain() : rgInfo.getFallbackGain());
                    }
                }
                String rgText;
                if (gainDb != null && gainDb != 0f) {
                    rgText = context.getString(R.string.editorial_badge_rg_db, String.format(Locale.US, "%+.1f", gainDb));
                } else if ("track".equals(rgMode)) {
                    rgText = context.getString(R.string.editorial_badge_rg_track);
                } else if ("album".equals(rgMode)) {
                    rgText = context.getString(R.string.editorial_badge_rg_album);
                } else if ("auto".equals(rgMode)) {
                    rgText = context.getString(R.string.editorial_badge_rg_auto);
                } else {
                    rgText = context.getString(R.string.editorial_badge_rg_on);
                }
                setTextIfChanged(playerBadgeRg, rgText);
                playerBadgeRg.setBackground(makeRoundedRect(6f, COLOR_BRASS_SUBTLE, 1f, COLOR_BRASS_BORDER));
                playerBadgeRg.setTextColor(COLOR_BRASS);
                playerBadgeRg.setVisibility(View.VISIBLE);
            }
        }

        // 4. EQ Badge
        if (playerBadgeEq != null) {
            PlayerSheets.EqSummary eqSummary = PlayerSheets.currentEqSummary(context);
            setTextIfChanged(playerBadgeEq, eqSummary.getBadgeText());
            if (eqSummary.isActive()) {
                boolean peq = eqSummary.isParametric();
                playerBadgeEq.setBackground(makeRoundedRect(
                        6f,
                        peq ? COLOR_MOSS_SUBTLE : COLOR_BRASS_SUBTLE,
                        1f,
                        peq ? COLOR_MOSS_BORDER : COLOR_BRASS
                ));
                playerBadgeEq.setTextColor(peq ? COLOR_MOSS : COLOR_BRASS);
            } else {
                playerBadgeEq.setBackground(makeRoundedRect(6f, COLOR_SURFACE_3, 1f, COLOR_HAIRLINE));
                playerBadgeEq.setTextColor(COLOR_IVORY_MUTED);
            }
            playerBadgeEq.setVisibility(View.VISIBLE);
        }
    }

    private void updateCapsuleState() {
        if (bind == null || !isAdded()) return;
        Context context = requireContext();

        // 1. Lyrics slot
        boolean lyricsActive = isLyricsPage();
        styleCapsuleSlot(playerOpenLyricsButton, playerCapsuleLyricsIcon, playerCapsuleLyricsText, lyricsActive);

        // 2. Output slot
        PlayerSheets.OutputSummary outputSummary = PlayerSheets.currentOutputSummary(context);
        boolean remoteActive = outputSummary.isRemote();
        if (playerCapsuleOutputIcon != null) {
            playerCapsuleOutputIcon.setImageResource(outputSummary.getIconRes());
        }
        if (playerCapsuleOutputDot != null) {
            playerCapsuleOutputDot.setVisibility(remoteActive ? View.VISIBLE : View.GONE);
        }
        styleCapsuleSlot(playerCapsuleOutputButton, playerCapsuleOutputIcon, playerCapsuleOutputText, remoteActive);

        // 3. Sound slot (sleep timer countdown text or brass dot when EQ / DSP / timer active)
        boolean lanRemote = com.eddyizm.tempus.lan.LanRemoteSession.isActive();
        com.eddyizm.tempus.lan.LanRemoteState lanState = com.eddyizm.tempus.lan.LanRemoteSession.current();
        boolean timerActive = lanRemote ? lanState.getTimerActive() : SleepTimerManager.getInstance().isActive();

        String soundLabel = context.getString(R.string.editorial_player_capsule_sound);
        if (timerActive) {
            boolean eot = lanRemote ? lanState.getTimerEndOfTrack() : SleepTimerManager.getInstance().isEndOfTrack();
            if (eot) {
                soundLabel = "EOT";
            } else {
                String raw = lanRemote ? lanState.getTimerRemaining() : SleepTimerManager.getInstance().getRemainingFormatted();
                String compact = formatCompactSleepCountdown(raw);
                if (!compact.isEmpty()) {
                    soundLabel = compact;
                }
            }
        }
        if (playerCapsuleSoundText != null) {
            setTextIfChanged(playerCapsuleSoundText, soundLabel);
        }

        PlayerSheets.EqSummary eqSummary = PlayerSheets.currentEqSummary(context);
        String rgMode = Preferences.getReplayGainMode();
        boolean rgActive = rgMode != null && !"disabled".equals(rgMode);
        float speed = lanRemote ? lanState.getSpeed() : Preferences.getPlaybackSpeed();
        boolean speedActive = Math.abs(speed - 1.0f) > 0.01f;
        boolean skipSilence = !lanRemote && Preferences.isSkipSilenceMode();
        boolean anySoundActive = timerActive || eqSummary.isActive() || rgActive || speedActive || skipSilence;

        if (playerCapsuleSoundDot != null) {
            playerCapsuleSoundDot.setVisibility(anySoundActive ? View.VISIBLE : View.GONE);
        }
        styleCapsuleSlot(playerCapsuleSoundButton, playerCapsuleSoundIcon, playerCapsuleSoundText, timerActive || eqSummary.isActive());

        // 4. Queue slot
        styleCapsuleSlot(playerOpenQueueButton, playerCapsuleQueueIcon, playerCapsuleQueueText, queueSheetOpen);
    }

    private void styleCapsuleSlot(@Nullable View container, @Nullable ImageView icon, @Nullable TextView label, boolean active) {
        int fg = active ? COLOR_BRASS : COLOR_IVORY;
        if (icon != null) {
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(fg));
        }
        if (label != null) {
            label.setTextColor(fg);
        }
        if (container != null) {
            if (active) {
                container.setBackground(makeRoundedRect(999f, COLOR_BRASS_SUBTLE, 0f, 0));
            } else {
                container.setBackgroundResource(android.R.color.transparent);
            }
        }
    }

    private String formatCompactSleepCountdown(@Nullable String formattedMmSs) {
        if (formattedMmSs == null || formattedMmSs.trim().isEmpty()) return "";
        String trimmed = formattedMmSs.trim();
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            try {
                int mins = Integer.parseInt(trimmed.substring(0, colon));
                int secs = Integer.parseInt(trimmed.substring(colon + 1));
                int totalSec = mins * 60 + secs;
                if (totalSec >= 60) {
                    int ceilMins = (totalSec + 59) / 60;
                    return ceilMins + "m";
                } else if (totalSec > 0) {
                    return totalSec + "s";
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return trimmed;
    }

    private boolean isLosslessCodec(@Nullable String codecUpper) {
        if (codecUpper == null) return false;
        String clean = codecUpper.toUpperCase(Locale.ROOT);
        return clean.contains("FLAC")
                || clean.contains("ALAC")
                || clean.contains("WAV")
                || clean.contains("PCM")
                || clean.contains("AIFF")
                || clean.contains("APE")
                || clean.contains("WV")
                || clean.contains("DSD")
                || clean.contains("DFF")
                || clean.contains("DSF");
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(),
                new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class)))
                .buildAsync();
    }

    private void releaseBrowser() {
        SleepTimerManager.getInstance().setTickListener(null);
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                if (bind == null) return;

                bind.nowPlayingMediaControllerView.setPlayer(com.eddyizm.tempus.lan.LanRemoteSession.isActive() ? remotePlayer : mediaBrowser);
                mediaBrowser.setShuffleModeEnabled(Preferences.isShuffleModeEnabled());
                mediaBrowser.setRepeatMode(Preferences.getRepeatMode());
                setMediaControllerListener(mediaBrowser);
                initSleepTimerButton(mediaBrowser);
                initPlaybackSpeedButton(mediaBrowser);
                refreshPlaybackDestination();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaControllerListener(MediaBrowser mediaBrowser) {
        setMediaControllerUI(mediaBrowser);
        setMetadata(mediaBrowser.getMediaMetadata());
        setMediaInfo(mediaBrowser.getMediaMetadata());

        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setMediaControllerUI(mediaBrowser);
                setMetadata(mediaMetadata);
                setMediaInfo(mediaMetadata);
            }

            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                setMediaFormatFromFileReturnedByServer();
                refreshSignalBadges();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    setMediaFormatFromFileReturnedByServer();
                    refreshSignalBadges();
                }
            }

            @Override
            public void onDeviceInfoChanged(@NonNull androidx.media3.common.DeviceInfo deviceInfo) {
                updatePlaybackDestinationLabel();
                refreshSignalBadges();
                updateCapsuleState();
            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                Preferences.setShuffleModeEnabled(shuffleModeEnabled);
                updatePlayModeButton();
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
                Preferences.setRepeatMode(repeatMode);
                updatePlayModeButton();
            }
        });
        updatePlayModeButton();
    }

    private void setMetadata(MediaMetadata mediaMetadata) {
        if (bind == null || (com.eddyizm.tempus.lan.LanRemoteSession.isActive() && !renderingRemote)) return;
        updatePlaybackDestinationLabel();
        String type = mediaMetadata.extras != null ? mediaMetadata.extras.getString("type") : null;

        if (Objects.equals(type, Constants.MEDIA_TYPE_RADIO)) {
            String stationName = mediaMetadata.extras != null
                    ? mediaMetadata.extras.getString("stationName",
                            mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : "")
                    : mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : "";

            String artist = mediaMetadata.extras != null
                    ? mediaMetadata.extras.getString("radioArtist", "")
                    : "";

            String title = mediaMetadata.extras != null
                    ? mediaMetadata.extras.getString("radioTitle", "")
                    : "";

            String mainTitle;
            if (!TextUtils.isEmpty(artist) && !TextUtils.isEmpty(title)) {
                mainTitle = artist + " - " + title;
            } else if (!TextUtils.isEmpty(title)) {
                mainTitle = title;
            } else if (!TextUtils.isEmpty(artist)) {
                mainTitle = artist;
            } else {
                mainTitle = stationName;
            }

            setTextIfChanged(playerMediaTitleLabel, mainTitle);
            setTextIfChanged(playerArtistNameLabel, stationName);
            setContextLabel(null);

            playerMediaTitleLabel.setSelected(true);
            playerArtistNameLabel.setSelected(true);

            playerMediaTitleLabel.setVisibility(!TextUtils.isEmpty(mainTitle) ? View.VISIBLE : View.GONE);
            playerArtistNameLabel.setVisibility(!TextUtils.isEmpty(stationName) ? View.VISIBLE : View.GONE);

            updateAssetLinkChips(mediaMetadata);
            refreshSignalBadges();
            return;
        }

        setTextIfChanged(playerMediaTitleLabel,
                Preferences.getTrackNumberVisible()
                        && mediaMetadata.trackNumber != null
                        && !String.valueOf(mediaMetadata.trackNumber).isEmpty()
                                ? String.format("%02d", mediaMetadata.trackNumber) + ". "
                                        + String.valueOf(mediaMetadata.title)
                                : String.valueOf(mediaMetadata.title));
        setTextIfChanged(playerArtistNameLabel,
                mediaMetadata.artist != null
                        ? String.valueOf(mediaMetadata.artist)
                        : "");

        playerMediaTitleLabel.setSelected(true);
        playerArtistNameLabel.setSelected(true);

        playerMediaTitleLabel.setVisibility(
                mediaMetadata.title != null && !Objects.equals(mediaMetadata.title, "") ? View.VISIBLE : View.GONE);
        playerArtistNameLabel.setVisibility(
                (mediaMetadata.artist != null && !Objects.equals(mediaMetadata.artist, ""))
                        || mediaMetadata.extras != null
                                && Objects.equals(mediaMetadata.extras.getString("type"), Constants.MEDIA_TYPE_RADIO)
                                && mediaMetadata.extras.getString("uri") != null
                                        ? View.VISIBLE
                                        : View.GONE);

        setContextLabel(mediaMetadata.albumTitle);
        updateAssetLinkChips(mediaMetadata);
        refreshSignalBadges();
    }

    private void setContextLabel(@Nullable CharSequence context) {
        if (playerContextLabel == null) return;
        setTextIfChanged(playerContextLabel, context == null ? "" : context);
        playerContextLabel.setVisibility(TextUtils.isEmpty(context) ? View.GONE : View.VISIBLE);
    }

    private void setTextIfChanged(@Nullable TextView label, CharSequence text) {
        if (label == null) return;
        if (!TextUtils.equals(label.getText(), text)) label.setText(text);
    }

    private void setMediaInfo(MediaMetadata mediaMetadata) {
        if (bind == null || (com.eddyizm.tempus.lan.LanRemoteSession.isActive() && !renderingRemote)) return;
        boolean isLocal = !com.eddyizm.tempus.lan.LanRemoteSession.isActive() && MusicUtil.isCurrentTrackLocal(getBrowser());

        if (mediaMetadata.extras != null && playerMediaExtension != null && playerMediaBitrate != null) {
            String extension = mediaMetadata.extras.getString("suffix", getString(R.string.player_unknown_format));
            int rawBitrate = mediaMetadata.extras.getInt("bitrate", 0);
            String bitrate = rawBitrate != 0 ? rawBitrate + "kbps" : "Original";
            String samplingRate = mediaMetadata.extras.getInt("samplingRate", 0) != 0
                    ? new DecimalFormat("0.#").format(mediaMetadata.extras.getInt("samplingRate", 0) / 1000.0)
                            + "kHz"
                    : "";
            String bitDepth = mediaMetadata.extras.getInt("bitDepth", 0) != 0
                    ? mediaMetadata.extras.getInt("bitDepth", 0) + "b"
                    : "";

            playerMediaExtension.setText(extension);

            if (bitrate.equals("Original") && !isLocal) {
                playerMediaBitrate.setVisibility(View.GONE);
            } else {
                List<String> items = new ArrayList<>();
                if (!bitrate.trim().isEmpty()) items.add(bitrate);
                if (!bitDepth.trim().isEmpty()) items.add(bitDepth);
                if (!samplingRate.trim().isEmpty()) items.add(samplingRate);
                String mediaQuality = TextUtils.join(" • ", items);

                playerMediaBitrate.setVisibility(Preferences.getBitrateVisible() ? View.VISIBLE : View.GONE);
                playerMediaBitrate.setText(mediaQuality);
            }
        }

        if (!isLocal) {
            setMediaFormatFromFileReturnedByServer();
        }

        if (playerTrackInfo != null) {
            playerTrackInfo.setOnClickListener(view -> {
                TrackInfoDialog dialog = new TrackInfoDialog(mediaMetadata);
                dialog.show(activity.getSupportFragmentManager(), null);
            });
        }

        if (playerMediaExtension != null) {
            playerMediaExtension.setOnClickListener(v -> toggleBitrateVisibility());
            playerMediaExtension.setOnLongClickListener(v -> toggleQuickActionVisiblity());
        }

        if (playerMediaBitrate != null) {
            playerMediaBitrate.setOnClickListener(v -> toggleBitrateVisibility());
            playerMediaBitrate.setOnLongClickListener(v -> toggleQuickActionVisiblity());
        }

        refreshSignalBadges();
    }

    private MediaBrowser getBrowser() {
        if (mediaBrowserListenableFuture == null || !mediaBrowserListenableFuture.isDone()) return null;
        try {
            return mediaBrowserListenableFuture.get();
        } catch (Exception e) {
            Log.e(TAG, "Unable to resolve media browser", e);
            return null;
        }
    }

    private void setMediaFormatFromFileReturnedByServer() {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) return;
        if (playerMediaExtension == null || playerMediaBitrate == null) return;

        MediaBrowser browser = getBrowser();
        if (MusicUtil.isCurrentTrackLocal(browser)) return;

        Format format = MusicUtil.getCurrentAudioFormat(browser);
        if (format == null) return;

        boolean renderer = UpnpPlayer.FORMAT_ID.equals(format.id);
        if (renderer && MimeTypes.AUDIO_UNKNOWN.equals(format.sampleMimeType)) return;

        String actual = MusicUtil.audioFormatLabel(format.sampleMimeType);
        String original = renderer
                ? (browser.getMediaMetadata().extras != null ? MusicUtil.sourceSuffix(browser.getMediaMetadata().extras) : null)
                : MusicUtil.getCurrentOriginalSuffix(browser);
        boolean transcoded = MusicUtil.isTranscodedFormat(actual, original);

        if (actual != null && !actual.isEmpty()) {
            playerMediaExtension.setText(transcoded
                    ? actual + " (" + getString(R.string.player_transcoding) + ")"
                    : actual);
        }

        if (browser.getMediaMetadata().extras != null
                && Objects.equals(browser.getMediaMetadata().extras.getString("type"), Constants.MEDIA_TYPE_RADIO)) {
            playerMediaBitrate.setText((format.bitrate / 1000) + "kbps");
            playerMediaBitrate.setVisibility(Preferences.getBitrateVisible() ? View.VISIBLE : View.GONE);
        } else if (format.bitrate != Format.NO_VALUE && format.bitrate > 0) {
            if (transcoded) {
                playerMediaBitrate.setText((format.bitrate / 1000) + "kbps");
                playerMediaBitrate.setVisibility(Preferences.getBitrateVisible() ? View.VISIBLE : View.GONE);
            }
        } else if (transcoded) {
            String targetBitrate = MusicUtil.getBitratePreference();
            if (!targetBitrate.equals("0")) {
                playerMediaBitrate.setText(targetBitrate + "kbps");
                playerMediaBitrate.setVisibility(Preferences.getBitrateVisible() ? View.VISIBLE : View.GONE);
            } else {
                playerMediaBitrate.setVisibility(View.GONE);
            }
        }
    }

    private void toggleBitrateVisibility() {
        if (playerMediaBitrate == null || !(playerMediaBitrate.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) playerMediaBitrate.getParent();

        TransitionSet transition = new TransitionSet()
                .addTransition(new Slide(Gravity.START))
                .addTransition(new ChangeBounds())
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator());
        TransitionManager.beginDelayedTransition(parent, transition);

        playerMediaBitrate.setVisibility(Preferences.getBitrateVisible() ? View.GONE : View.VISIBLE);
        Preferences.setBitrateVisible(!Preferences.getBitrateVisible());
    }

    private boolean toggleQuickActionVisiblity() {
        if (playerQuickActionView == null || !(playerQuickActionView.getParent() instanceof ViewGroup)) return true;
        ViewGroup parent = (ViewGroup) playerQuickActionView.getParent();

        TransitionSet transition = new TransitionSet()
                .addTransition(new Slide(Gravity.BOTTOM))
                .addTransition(new ChangeBounds())
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator());
        TransitionManager.beginDelayedTransition(parent, transition);
        playerQuickActionView.setVisibility(Preferences.getQuickActionVisible() ? View.GONE : View.VISIBLE);
        Preferences.setQuickActionVisible(!Preferences.getQuickActionVisible());
        return true;
    }

    private void updateAssetLinkChips(MediaMetadata mediaMetadata) {
        if (assetLinkChipGroup == null) return;
        String mediaType = mediaMetadata.extras != null
                ? mediaMetadata.extras.getString("type", Constants.MEDIA_TYPE_MUSIC)
                : Constants.MEDIA_TYPE_MUSIC;
        if (!Constants.MEDIA_TYPE_MUSIC.equals(mediaType)) {
            clearAssetLinkChip(playerSongLinkChip);
            clearAssetLinkChip(playerAlbumLinkChip);
            clearAssetLinkChip(playerArtistLinkChip);
            syncAssetLinkGroupVisibility();
            return;
        }

        String songId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("id") : null;
        String albumId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("albumId") : null;
        String artistId = mediaMetadata.extras != null ? mediaMetadata.extras.getString("artistId") : null;

        AssetLinkUtil.AssetLink songLink = bindAssetLinkChip(playerSongLinkChip, AssetLinkUtil.TYPE_SONG, songId);
        AssetLinkUtil.AssetLink albumLink = bindAssetLinkChip(playerAlbumLinkChip, AssetLinkUtil.TYPE_ALBUM, albumId);
        AssetLinkUtil.AssetLink artistLink = bindAssetLinkChip(playerArtistLinkChip, AssetLinkUtil.TYPE_ARTIST, artistId);
        bindAssetLinkView(playerMediaTitleLabel, songLink);
        bindAssetLinkView(playerArtistNameLabel, artistLink != null ? artistLink : songLink);
        bindAssetLinkView(playerMediaCoverViewPager, songLink);
        syncAssetLinkGroupVisibility();
    }

    private AssetLinkUtil.AssetLink bindAssetLinkChip(Chip chip, String type, String id) {
        if (chip == null) return null;
        if (TextUtils.isEmpty(id)) {
            clearAssetLinkChip(chip);
            return null;
        }

        String label = getString(AssetLinkUtil.getLabelRes(type));
        AssetLinkUtil.AssetLink assetLink = AssetLinkUtil.buildAssetLink(type, id);
        if (assetLink == null) {
            clearAssetLinkChip(chip);
            return null;
        }

        chip.setText(getString(R.string.asset_link_chip_text, label, assetLink.id));
        chip.setVisibility(View.VISIBLE);

        chip.setOnClickListener(v -> activity.openAssetLink(assetLink));

        chip.setOnLongClickListener(v -> {
            AssetLinkUtil.copyToClipboard(requireContext(), assetLink);
            Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, id), Toast.LENGTH_SHORT).show();
            return true;
        });

        return assetLink;
    }

    private void clearAssetLinkChip(Chip chip) {
        if (chip == null) return;
        chip.setVisibility(View.GONE);
        chip.setText("");
        chip.setOnClickListener(null);
        chip.setOnLongClickListener(null);
    }

    private void bindAssetLinkView(View view, AssetLinkUtil.AssetLink assetLink) {
        if (view == null) return;
        if (assetLink == null) {
            AssetLinkUtil.clearLinkAppearance(view);
            view.setOnClickListener(null);
            view.setOnLongClickListener(null);
            view.setClickable(false);
            view.setLongClickable(false);
            return;
        }

        view.setClickable(true);
        view.setLongClickable(true);
        AssetLinkUtil.applyLinkAppearance(view);
        view.setOnClickListener(v -> {
            boolean collapse = !AssetLinkUtil.TYPE_SONG.equals(assetLink.type);
            activity.openAssetLink(assetLink, collapse);
        });
        view.setOnLongClickListener(v -> {
            AssetLinkUtil.copyToClipboard(requireContext(), assetLink);
            Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, assetLink.id), Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void syncAssetLinkGroupVisibility() {
        if (assetLinkChipGroup == null) return;
        boolean hasVisible = false;
        for (int i = 0; i < assetLinkChipGroup.getChildCount(); i++) {
            View child = assetLinkChipGroup.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                hasVisible = true;
                break;
            }
        }
        assetLinkChipGroup.setVisibility(hasVisible ? View.VISIBLE : View.GONE);
    }

    private void setMediaControllerUI(MediaBrowser mediaBrowser) {
        if (bind == null || com.eddyizm.tempus.lan.LanRemoteSession.isActive()) return;
        initPlaybackSpeedButton(mediaBrowser);

        if (mediaBrowser.getMediaMetadata().extras != null) {
            switch (mediaBrowser.getMediaMetadata().extras.getString("type", Constants.MEDIA_TYPE_MUSIC)) {
                case Constants.MEDIA_TYPE_PODCAST:
                    bind.nowPlayingMediaControllerView.setShowShuffleButton(false);
                    bind.nowPlayingMediaControllerView.setShowRewindButton(true);
                    bind.nowPlayingMediaControllerView.setShowPreviousButton(false);
                    bind.nowPlayingMediaControllerView.setShowNextButton(false);
                    bind.nowPlayingMediaControllerView.setShowFastForwardButton(true);
                    bind.nowPlayingMediaControllerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
                    if (playbackSpeedButton != null) playbackSpeedButton.setVisibility(View.VISIBLE);
                    if (skipSilenceToggleButton != null) skipSilenceToggleButton.setVisibility(View.GONE);
                    if (buttonFavorite != null) buttonFavorite.setVisibility(View.GONE);
                    setPlaybackParameters(mediaBrowser);
                    break;
                case Constants.MEDIA_TYPE_RADIO:
                    bind.nowPlayingMediaControllerView.setShowShuffleButton(false);
                    bind.nowPlayingMediaControllerView.setShowRewindButton(false);
                    bind.nowPlayingMediaControllerView.setShowPreviousButton(false);
                    bind.nowPlayingMediaControllerView.setShowNextButton(false);
                    bind.nowPlayingMediaControllerView.setShowFastForwardButton(false);
                    bind.nowPlayingMediaControllerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
                    if (playbackSpeedButton != null) playbackSpeedButton.setVisibility(View.GONE);
                    if (skipSilenceToggleButton != null) skipSilenceToggleButton.setVisibility(View.GONE);
                    if (buttonFavorite != null) buttonFavorite.setVisibility(View.GONE);
                    setPlaybackParameters(mediaBrowser);
                    break;
                case Constants.MEDIA_TYPE_MUSIC:
                default:
                    bind.nowPlayingMediaControllerView.setShowShuffleButton(true);
                    bind.nowPlayingMediaControllerView.setShowRewindButton(false);
                    bind.nowPlayingMediaControllerView.setShowPreviousButton(true);
                    bind.nowPlayingMediaControllerView.setShowNextButton(true);
                    bind.nowPlayingMediaControllerView.setShowFastForwardButton(false);
                    bind.nowPlayingMediaControllerView.setRepeatToggleModes(
                            RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL | RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE);
                    if (playbackSpeedButton != null) playbackSpeedButton.setVisibility(View.VISIBLE);
                    if (skipSilenceToggleButton != null) skipSilenceToggleButton.setVisibility(View.GONE);
                    if (buttonFavorite != null) buttonFavorite.setVisibility(View.VISIBLE);
                    setPlaybackParameters(mediaBrowser);
                    break;
            }
        }
        syncFavoriteSurface();
        if (playModeButton != null && buttonFavorite != null) {
            playModeButton.setVisibility(buttonFavorite.getVisibility());
        }
    }

    // -------------------------------------------------------------------------
    // Sleep timer
    // -------------------------------------------------------------------------

    private void initSleepTimerButton(MediaBrowser mediaBrowser) {
        if (sleepTimerButton != null) {
            sleepTimerButton.setOnClickListener(v -> {
                SleepTimerDialog dialog = new SleepTimerDialog();
                dialog.setSleepTimerListener(new SleepTimerDialog.SleepTimerListener() {
                    @Override
                    public void onTimerSet(int minutes) {
                        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
                            com.eddyizm.tempus.lan.LanRemoteSession.sleepTimer("minutes", minutes);
                            return;
                        }
                        SleepTimerManager.getInstance().startTimer(minutes);
                        connectSleepTimerTick(mediaBrowser);
                    }

                    @Override
                    public void onTimerCancelled() {
                        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
                            com.eddyizm.tempus.lan.LanRemoteSession.sleepTimer("cancel", 0);
                            return;
                        }
                        SleepTimerManager.getInstance().cancelTimer();
                        updateSleepTimerUI();
                    }

                    @Override
                    public void onEndOfTrackSet() {
                        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
                            com.eddyizm.tempus.lan.LanRemoteSession.sleepTimer("end", 0);
                            return;
                        }
                        SleepTimerManager.getInstance().startEndOfTrack();
                        connectSleepTimerTick(mediaBrowser);
                    }
                });
                dialog.show(requireActivity().getSupportFragmentManager(), null);
            });
        }

        connectSleepTimerTick(mediaBrowser);
    }

    private void connectSleepTimerTick(MediaBrowser mediaBrowser) {
        SleepTimerManager.getInstance().setTickListener(expired -> updateSleepTimerUI());
    }

    private void updateSleepTimerUI() {
        if (bind == null || !isAdded()) return;
        updateCapsuleState();

        if (sleepTimerButton == null || sleepTimerLabel == null) return;

        boolean remote = com.eddyizm.tempus.lan.LanRemoteSession.isActive();
        com.eddyizm.tempus.lan.LanRemoteState remoteState = com.eddyizm.tempus.lan.LanRemoteSession.current();
        boolean active = remote ? remoteState.getTimerActive() : SleepTimerManager.getInstance().isActive();

        if (active) {
            boolean isEndOfTrack = remote ? remoteState.getTimerEndOfTrack() : SleepTimerManager.getInstance().isEndOfTrack();
            String label = isEndOfTrack
                    ? getString(R.string.sleep_timer_end_of_track_label)
                    : remote ? remoteState.getTimerRemaining() : SleepTimerManager.getInstance().getRemainingFormatted();
            sleepTimerLabel.setText(label);
            sleepTimerLabel.setVisibility(View.VISIBLE);
            if (sleepTimerContainer != null) sleepTimerContainer.setVisibility(View.GONE);
            ImageViewCompat.setImageTintList(sleepTimerButton, ColorStateList.valueOf(COLOR_BRASS));
        } else {
            sleepTimerLabel.setVisibility(View.GONE);
            sleepTimerLabel.setText("");
            if (sleepTimerContainer != null) sleepTimerContainer.setVisibility(View.GONE);
            ImageViewCompat.setImageTintList(sleepTimerButton, ColorStateList.valueOf(COLOR_IVORY));
        }
    }

    private void initCoverLyricsSlideView() {
        playerMediaCoverViewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        playerMediaCoverViewPager.setAdapter(new PlayerControllerHorizontalPager(this));
        playerMediaCoverViewPager.setUserInputEnabled(false);
        playerMediaCoverViewPager.setNestedScrollingEnabled(false);
        if (playerMediaCoverViewPager.getChildCount() > 0) {
            playerMediaCoverViewPager.getChildAt(0).setNestedScrollingEnabled(false);
        }

        playerMediaCoverViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                PlayerMotion.setLyricsShowing(position == 1 || queueSheetOpen);

                if (getParentFragment() instanceof PlayerBottomSheetFragment) {
                    ((PlayerBottomSheetFragment) getParentFragment()).updateBottomSheetDraggableState();
                } else if (position == 0) {
                    activity.setBottomSheetDraggableState(true);
                } else if (position == 1) {
                    activity.setBottomSheetDraggableState(false);
                }
                if (position == 0) {
                    playerMediaCoverViewPager.setUserInputEnabled(false);
                    updateLyricsButtonTint(false);
                } else if (position == 1) {
                    playerMediaCoverViewPager.setUserInputEnabled(true);
                    updateLyricsButtonTint(true);
                }
            }
        });
    }

    private void updateLyricsButtonTint(boolean isLyricsActive) {
        updateCapsuleState();
    }

    public void updateQueueButtonTint(boolean isQueueActive) {
        this.queueSheetOpen = isQueueActive;
        PlayerMotion.setLyricsShowing(isLyricsPage() || isQueueActive);
        updateCapsuleState();
    }

    private void initMediaListenable() {
        playerBottomSheetViewModel.getLiveMedia().observe(getViewLifecycleOwner(), media -> {
            if (media != null) {
                if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()
                        && !Objects.equals(media.getId(), com.eddyizm.tempus.lan.LanRemoteSession.current().getMediaId())) return;
                if (buttonFavorite != null) {
                    buttonFavorite.setEnabled(true);
                    buttonFavorite.setChecked(FavoriteRegistry.resolve(FavoriteRegistry.Kind.SONG, media.getId(), media.getStarred() != null));
                    buttonFavorite.setOnClickListener(v -> playerBottomSheetViewModel.setFavorite(requireContext(), media));
                    buttonFavorite.setOnLongClickListener(v -> {
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(Constants.TRACK_OBJECT, media);

                        RatingDialog dialog = new RatingDialog();
                        dialog.setArguments(bundle);
                        dialog.show(requireActivity().getSupportFragmentManager(), null);

                        return true;
                    });
                }
                ratingViewModel.setSong(media);

                Integer currentRating = media.getUserRating();
                if (songRatingBar != null) {
                    songRatingBar.setRating(currentRating != null ? currentRating : 0);
                    songRatingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
                        if (fromUser) {
                            ratingViewModel.rate((int) rating);
                            media.setUserRating((int) rating);
                        }
                    });
                }

                if (getActivity() != null) {
                    playerBottomSheetViewModel.refreshMediaInfo(requireActivity(), media);
                }
            }
        });
    }

    private void initMediaLabelButton() {
        playerBottomSheetViewModel.getLiveAlbum().observe(getViewLifecycleOwner(), album -> {
            if (album != null && playerMediaTitleLabel != null) {
                playerMediaTitleLabel.setOnClickListener(view -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.ALBUM_OBJECT, album);
                    NavHostFragment.findNavController(this).navigate(R.id.albumPageFragment, bundle);
                    activity.collapseBottomSheetDelayed();
                });
                setupCopyTextOnLongTap(playerMediaTitleLabel);
            }
        });
    }

    private void initArtistLabelButton() {
        playerBottomSheetViewModel.getLiveArtist().observe(getViewLifecycleOwner(), artist -> {
            if (artist != null && playerArtistNameLabel != null) {
                playerArtistNameLabel.setOnClickListener(view -> {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.ARTIST_OBJECT, artist);
                    NavHostFragment.findNavController(this).navigate(R.id.artistPageFragment, bundle);
                    activity.collapseBottomSheetDelayed();
                });
                setupCopyTextOnLongTap(playerArtistNameLabel);
            }
        });
    }

    private void setupCopyTextOnLongTap(TextView textView) {
        if (textView == null) return;
        textView.setOnLongClickListener(v -> {
            CharSequence text = textView.getText();
            if (!TextUtils.isEmpty(text)) {
                ClipboardManager clipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText(getString(R.string.app_name), text);
                clipboardManager.setPrimaryClip(clipData);
                Toast.makeText(requireContext(), getString(R.string.asset_link_copied_toast, text), Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void initPlaybackSpeedButton(MediaBrowser mediaBrowser) {
        if (playbackSpeedButton != null) {
            playbackSpeedButton.setOnClickListener(view -> {
                PlaybackSpeedDialog dialog = new PlaybackSpeedDialog();
                dialog.setPlaybackSpeedListener(this::applyPlaybackSpeed);
                dialog.show(requireActivity().getSupportFragmentManager(), null);
            });
        }

        if (skipSilenceToggleButton != null) {
            skipSilenceToggleButton.setOnClickListener(view -> Preferences.setSkipSilenceMode(!skipSilenceToggleButton.isChecked()));
        }
    }

    private void openAddToPlaylistDialog() {
        var song = playerBottomSheetViewModel.getLiveMedia().getValue();
        if (song != null) {
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(Collections.singletonList(song)));
            PlaylistChooserDialog dialog = new PlaylistChooserDialog();
            dialog.setArguments(bundle);
            dialog.show(requireActivity().getSupportFragmentManager(), null);
        }
    }

    private void navigateToEqualizerFragment() {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
            Toast.makeText(requireContext(), R.string.lan_receiver_audio_settings, Toast.LENGTH_LONG).show();
            return;
        }
        if (Preferences.getSelectedEqualizer() == 0) {
            Preferences.setSelectedEqualizer("1");
            if (mediaServiceBinder != null) {
                mediaServiceBinder.reloadEqualizer();
            }
        }
        NavController navController = NavHostFragment.findNavController(this);
        NavOptions navOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.equalizerFragment, true)
                .build();
        navController.navigate(R.id.equalizerFragment, null, navOptions);
        if (activity != null) {
            activity.collapseBottomSheetDelayed();
        }
    }

    public void goToControllerPage() {
        if (playerMediaCoverViewPager != null) {
            playerMediaCoverViewPager.setCurrentItem(0, false);
        }
    }

    public void goToLyricsPage() {
        if (playerMediaCoverViewPager != null) {
            playerMediaCoverViewPager.setCurrentItem(1, true);
        }
    }

    public boolean isLyricsPage() {
        return playerMediaCoverViewPager != null && playerMediaCoverViewPager.getCurrentItem() == 1;
    }

    private void checkAndSetRatingContainerVisibility() {
        if (ratingContainer == null) return;
        ratingContainer.setVisibility(Preferences.showItemStarRating() ? View.VISIBLE : View.GONE);
    }

    private void setPlaybackParameters(MediaBrowser mediaBrowser) {
        float currentSpeed = Preferences.getPlaybackSpeed();
        boolean skipSilence = Preferences.isSkipSilenceMode();

        mediaBrowser.setPlaybackParameters(getPlaybackParameters(currentSpeed));
        if (playbackSpeedButton != null) {
            playbackSpeedButton.setText(getString(R.string.player_playback_speed, currentSpeed));
        }
        if (skipSilenceToggleButton != null) {
            skipSilenceToggleButton.setChecked(skipSilence);
        }
    }

    private void applyPlaybackSpeed(float speed) {
        if (playbackSpeedButton != null) {
            playbackSpeedButton.setText(getString(R.string.player_playback_speed, speed));
        }
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
            com.eddyizm.tempus.lan.LanRemoteSession.playbackParameters(speed, getPlaybackParameters(speed).pitch);
            return;
        }

        if (mediaBrowserListenableFuture == null) return;

        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                mediaBrowser.setPlaybackParameters(getPlaybackParameters(speed));
            } catch (Exception e) {
                Log.e(TAG, "Error applying playback speed", e);
            }
        }, MoreExecutors.directExecutor());
    }

    private PlaybackParameters getPlaybackParameters(float speed) {
        float pitch = Preferences.isPlaybackSpeedPitchEnabled()
                ? getAdjustedPitch(speed)
                : 1.0f;
        return new PlaybackParameters(speed, pitch);
    }

    private float getAdjustedPitch(float speed) {
        return Preferences.isPlaybackSpeedManualPitchEnabled()
                ? Preferences.getPlaybackSpeedManualPitch()
                : speed;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaServiceBinder = (BaseMediaService.LocalBinder) service;
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaServiceBinder = null;
            isServiceBound = false;
        }
    };

    private void bindMediaService() {
        Intent intent = new Intent(requireActivity(), MediaService.class);
        intent.setAction(MediaService.ACTION_BIND_EQUALIZER);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isServiceBound = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        bindMediaService();
        updatePlaybackDestinationLabel();
        refreshSignalBadges();
        updateCapsuleState();
    }

    private void updateRemoteToggleVisibility() {
        if (bind == null) return;
        bind.playerRemoteToggle.setVisibility(View.GONE);
        updatePlaybackDestinationLabel();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
