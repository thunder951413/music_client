package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentHomeTabPodcastBinding;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.interfaces.PodcastCallback;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.PodcastChannelHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.PodcastEpisodeAdapter;
import com.eddyizm.tempus.ui.dialog.PodcastChannelEditorDialog;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.PodcastViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class HomeTabPodcastFragment extends Fragment implements ClickCallback, PodcastCallback {
    private static final String TAG = "HomeTabPodcastFragment";

    private FragmentHomeTabPodcastBinding bind;
    private MainActivity activity;
    private PodcastViewModel podcastViewModel;

    private PodcastEpisodeAdapter podcastEpisodeAdapter;
    private PodcastChannelHorizontalAdapter podcastChannelHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentHomeTabPodcastBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        podcastViewModel = new ViewModelProvider(requireActivity()).get(PodcastViewModel.class);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        init();
        initPodcastView();
        initNewestPodcastsView();
        initPodcastChannelsView();
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (podcastEpisodeAdapter != null) {
            podcastEpisodeAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        bind.podcastChannelsPreTextView.setOnClickListener(v -> {
            PodcastChannelEditorDialog dialog = new PodcastChannelEditorDialog(this);
            dialog.show(activity.getSupportFragmentManager(), null);
        });

        bind.podcastChannelsTextViewClickable.setOnClickListener(v -> activity.navController.navigate(R.id.podcastChannelCatalogueFragment));
        bind.podcastRefresh.setOnClickListener(v -> triggerRefresh());
        bind.hideSectionButton.setOnClickListener(v -> Preferences.setPodcastSectionHidden());
    }

    public void triggerRefresh() {
        com.eddyizm.tempus.podcast.LocalPodcastStore.getInstance().refreshAll(true);
        if (!isAdded() || getView() == null || podcastViewModel == null) return;
        podcastViewModel.refreshPodcastChannels(getViewLifecycleOwner());
        podcastViewModel.refreshNewestPodcastEpisodes(getViewLifecycleOwner());
    }

    private void initPodcastView() {
        podcastViewModel.getPodcastChannels(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), podcastChannels -> {
            if (podcastChannels == null) {
                if (bind != null) bind.homePodcastChannelsSector.setVisibility(View.GONE);
                if (bind != null) bind.emptyPodcastLayout.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homePodcastChannelsSector.setVisibility(!podcastChannels.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.emptyPodcastLayout.setVisibility(podcastChannels.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void initPodcastChannelsView() {
        bind.podcastChannelsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        podcastChannelHorizontalAdapter = new PodcastChannelHorizontalAdapter(this);
        bind.podcastChannelsRecyclerView.setAdapter(podcastChannelHorizontalAdapter);
        podcastViewModel.getPodcastChannels(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), podcastChannels -> {
            if (podcastChannels == null) {
                if (bind != null) bind.homePodcastChannelsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homePodcastChannelsSector.setVisibility(!podcastChannels.isEmpty() ? View.VISIBLE : View.GONE);

                podcastChannelHorizontalAdapter.setItems(new java.util.ArrayList<>(podcastChannels.subList(0, Math.min(6, podcastChannels.size()))));
                if (bind != null) bind.podcastChannelsTextViewClickable.setText(getString(R.string.audio_subscriptions_count, podcastChannels.size()));
            }
        });
    }

    private void initNewestPodcastsView() {
        bind.newestPodcastsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        podcastEpisodeAdapter = new PodcastEpisodeAdapter(this);
        bind.newestPodcastsRecyclerView.setAdapter(podcastEpisodeAdapter);
        podcastViewModel.getNewestPodcastEpisodes(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), podcastEpisodes -> {
            if (podcastEpisodes == null) {
                if (bind != null) bind.homeNewestPodcastsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeNewestPodcastsSector.setVisibility(!podcastEpisodes.isEmpty() ? View.VISIBLE : View.GONE);

                podcastEpisodeAdapter.setItems(podcastEpisodes.stream().filter(podcastEpisode -> Objects.equals(podcastEpisode.getStatus(), "completed")).collect(Collectors.toList()));
            }
        });
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onPodcastEpisodeClick(Bundle bundle) {
        MediaManager.startPodcast(mediaBrowserListenableFuture, bundle.getParcelable(Constants.PODCAST_OBJECT));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onPodcastEpisodeLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.podcastEpisodeBottomSheetDialog, bundle);
    }

    @Override
    public void onPodcastChannelClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.podcastChannelPageFragment, bundle);
    }

    @Override
    public void onPodcastChannelLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.podcastChannelBottomSheetDialog, bundle);
    }

    @Override
    public void onDismiss() {
        new Handler().postDelayed(() -> {
            if (!isAdded() || getView() == null) return;
            if (podcastViewModel != null) podcastViewModel.refreshPodcastChannels(getViewLifecycleOwner());
            if (podcastViewModel != null) podcastViewModel.refreshNewestPodcastEpisodes(getViewLifecycleOwner());
        }, 1000);
    }
}
