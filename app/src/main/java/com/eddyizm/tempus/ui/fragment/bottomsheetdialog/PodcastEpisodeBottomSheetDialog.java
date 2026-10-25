package com.eddyizm.tempus.ui.fragment.bottomsheetdialog;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.viewmodel.PodcastEpisodeBottomSheetViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.common.util.concurrent.ListenableFuture;

@UnstableApi
public class PodcastEpisodeBottomSheetDialog extends BottomSheetDialogFragment implements View.OnClickListener {
    private PodcastEpisodeBottomSheetViewModel podcastEpisodeBottomSheetViewModel;
    private PodcastEpisode podcastEpisode;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_podcast_episode_dialog, container, false);

        Bundle args = getArguments();
        if (args == null || (podcastEpisode = args.getParcelable(Constants.PODCAST_OBJECT)) == null) {
            dismissAllowingStateLoss();
            return view;
        }

        podcastEpisodeBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PodcastEpisodeBottomSheetViewModel.class);
        podcastEpisodeBottomSheetViewModel.setPodcastEpisode(podcastEpisode);

        init(view);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (podcastEpisode != null) {
            initializeMediaBrowser();
        }
    }

    @Override
    public void onStop() {
        if (mediaBrowserListenableFuture != null) {
            releaseMediaBrowser();
        }
        super.onStop();
    }

    private void init(View view) {
        ImageView coverPodcast = view.findViewById(R.id.podcast_cover_image_view);

        CustomGlideRequest.Builder
                .from(requireContext(), podcastEpisodeBottomSheetViewModel.getPodcastEpisode().getCoverArtId(), CustomGlideRequest.ResourceType.Podcast)
                .build()
                .into(coverPodcast);

        TextView titlePodcast = view.findViewById(R.id.podcast_title_text_view);
        titlePodcast.setText(podcastEpisodeBottomSheetViewModel.getPodcastEpisode().getTitle());

        titlePodcast.setSelected(true);

        TextView playNext = view.findViewById(R.id.play_next_text_view);
        playNext.setOnClickListener(v -> {
            enqueueEpisode(true);
            ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
            dismissBottomSheet();
        });

        TextView addToQueue = view.findViewById(R.id.add_to_queue_text_view);
        addToQueue.setOnClickListener(v -> {
            enqueueEpisode(false);
            ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
            dismissBottomSheet();
        });

        TextView download = view.findViewById(R.id.download_text_view);
        download.setOnClickListener(v -> {
            // TODO
            /* DownloadUtil.getDownloadTracker(requireContext()).download(
                    MappingUtil.mapMediaItem(podcast, false),
                    MappingUtil.mapDownload(podcast, null, null)
            ); */
            dismissBottomSheet();
        });

        TextView remove = view.findViewById(R.id.remove_text_view);
        remove.setOnClickListener(v -> {
            // TODO
            /* DownloadUtil.getDownloadTracker(requireContext()).remove(
                    MappingUtil.mapMediaItem(podcast, false),
                    MappingUtil.mapDownload(podcast, null, null)
            ); */
            dismissBottomSheet();
        });

        initDownloadUI(download, remove);

        TextView delete = view.findViewById(R.id.delete_text_view);
        delete.setOnClickListener(v -> {
            podcastEpisodeBottomSheetViewModel.deletePodcastEpisode();
            dismissBottomSheet();
        });

        TextView goToChannel = view.findViewById(R.id.go_to_channel_text_view);
        if (com.eddyizm.tempus.podcast.LocalPodcastStore.isLocal(podcastEpisode.getId())) {
            download.setVisibility(View.GONE); remove.setVisibility(View.GONE); delete.setVisibility(View.GONE);
            goToChannel.setVisibility(View.GONE);
        }
        goToChannel.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Open the channel", Toast.LENGTH_SHORT).show();
            dismissBottomSheet();
        });
    }

    private void enqueueEpisode(boolean next) {
        if (mediaBrowserListenableFuture == null) return;
        if (com.eddyizm.tempus.podcast.LocalPodcastStore.isLocal(podcastEpisode.getId())) {
            com.eddyizm.tempus.service.MediaManager.enqueue(mediaBrowserListenableFuture,
                    com.eddyizm.tempus.util.MappingUtil.mapToChild(com.eddyizm.tempus.util.MappingUtil.mapMediaItem(podcastEpisode)),next);
            return;
        }
        var future = mediaBrowserListenableFuture;
        future.addListener(() -> {
            try {
                MediaBrowser browser = future.get();
                var item = com.eddyizm.tempus.util.MappingUtil.mapMediaItem(podcastEpisode);
                if (browser.getMediaItemCount() == 0) { browser.setMediaItem(item); browser.prepare(); }
                else browser.addMediaItem(next ? browser.getCurrentMediaItemIndex()+1 : browser.getMediaItemCount(),item);
            } catch (Exception e) { android.util.Log.w("PodcastActions","Cannot enqueue episode",e); }
        }, androidx.core.content.ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onClick(View v) {
        dismissBottomSheet();
    }

    private void dismissBottomSheet() {
        dismiss();
    }

    private void initDownloadUI(TextView download, TextView remove) {
        // TODO
        /* if (DownloadUtil.getDownloadTracker(requireContext()).isDownloaded(MappingUtil.mapMediaItem(podcast, false))) {
            download.setVisibility(View.GONE);
            remove.setVisibility(View.VISIBLE);
        } else {
            download.setVisibility(View.VISIBLE);
            remove.setVisibility(View.GONE);
        } */
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }
}