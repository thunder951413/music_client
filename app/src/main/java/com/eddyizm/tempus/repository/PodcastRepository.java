package com.eddyizm.tempus.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.PodcastChannel;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PodcastRepository {
    private static final String TAG = "PodcastRepository";

    public MutableLiveData<List<PodcastChannel>> getPodcastChannels(boolean includeEpisodes, String channelId) {
        androidx.lifecycle.MediatorLiveData<List<PodcastChannel>> result = new androidx.lifecycle.MediatorLiveData<>();
        var local = com.eddyizm.tempus.podcast.LocalPodcastStore.getInstance();
        List<PodcastChannel> server = new ArrayList<>();
        List<PodcastChannel> device = new ArrayList<>();
        Runnable publish = () -> {
            List<PodcastChannel> merged = new ArrayList<>(server);
            java.util.Set<String> urls = new java.util.HashSet<>();
            for (PodcastChannel channel : server) urls.add(com.eddyizm.tempus.catalog.AudioCatalog.canonicalUrl(channel.getUrl()));
            for (PodcastChannel channel : device) {
                if (channelId != null && !channelId.equals(channel.getId())) continue;
                if (urls.add(com.eddyizm.tempus.catalog.AudioCatalog.canonicalUrl(channel.getUrl())))
                    merged.add(includeEpisodes ? channel : com.eddyizm.tempus.podcast.LocalPodcastStore.summary(channel));
            }
            result.setValue(merged);
        };
        result.addSource(local.observe(), channels -> { device.clear(); device.addAll(channels); publish.run(); });
        if (com.eddyizm.tempus.podcast.LocalPodcastStore.isLocal(channelId)) {
            local.refresh(channelId, false);
            return result;
        }
        local.refreshAll(false);
        App.getSubsonicClientInstance(false).getPodcastClient().getPodcasts(includeEpisodes, channelId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPodcasts() != null) {
                            List<PodcastChannel> channels = response.body().getSubsonicResponse().getPodcasts().getChannels();
                            server.clear(); if (channels != null) server.addAll(channels);
                        }
                        publish.run();
                    }
                    @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) { publish.run(); }
                });
        return result;
    }

    public MutableLiveData<List<PodcastEpisode>> getNewestPodcastEpisodes(int count) {
        androidx.lifecycle.MediatorLiveData<List<PodcastEpisode>> result = new androidx.lifecycle.MediatorLiveData<>();
        List<PodcastEpisode> server = new ArrayList<>();
        List<PodcastEpisode> device = new ArrayList<>();
        Runnable publish = () -> {
            List<PodcastEpisode> merged = new ArrayList<>(server);
            merged.addAll(device);
            merged.sort(java.util.Comparator.comparing(PodcastEpisode::getPublishDate,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
            result.setValue(new ArrayList<>(merged.subList(0, Math.min(count, merged.size()))));
        };
        var local = com.eddyizm.tempus.podcast.LocalPodcastStore.getInstance();
        result.addSource(local.observe(), channels -> {
            device.clear();
            for (PodcastChannel channel : channels) if (channel.getEpisodes() != null) device.addAll(channel.getEpisodes());
            publish.run();
        });
        local.refreshAll(false);
        App.getSubsonicClientInstance(false).getPodcastClient().getNewestPodcasts(count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getNewestPodcasts() != null) {
                            List<PodcastEpisode> episodes = response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes();
                            server.clear(); if (episodes != null) server.addAll(episodes);
                        }
                        publish.run();
                    }
                    @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) { publish.run(); }
                });
        return result;
    }

    public Call<ApiResponse> refreshPodcasts() {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .refreshPodcasts();
    }

    public Call<ApiResponse> createPodcastChannel(String url) {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .createPodcastChannel(url);
    }

    public Call<ApiResponse> deletePodcastChannel(String channelId) {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .deletePodcastChannel(channelId);
    }

    public Call<ApiResponse> deletePodcastEpisode(String episodeId) {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .deletePodcastEpisode(episodeId);
    }

    public Call<ApiResponse> downloadPodcastEpisode(String episodeId) {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .downloadPodcastEpisode(episodeId);
    }
}
