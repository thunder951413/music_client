package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.eddyizm.tempus.repository.PodcastRepository;
import com.eddyizm.tempus.subsonic.models.PodcastChannel;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;
import java.util.List;

public class PodcastViewModel extends AndroidViewModel {
    private final PodcastRepository repository = new PodcastRepository();
    private final MediatorLiveData<List<PodcastEpisode>> newest = new MediatorLiveData<>();
    private final MediatorLiveData<List<PodcastChannel>> channels = new MediatorLiveData<>();
    private LiveData<List<PodcastEpisode>> newestSource;
    private LiveData<List<PodcastChannel>> channelSource;
    public PodcastViewModel(@NonNull Application application) { super(application); }
    public LiveData<List<PodcastEpisode>> getNewestPodcastEpisodes(LifecycleOwner owner) {
        if (newestSource == null) refreshNewestPodcastEpisodes(owner);
        return newest;
    }
    public LiveData<List<PodcastChannel>> getPodcastChannels(LifecycleOwner owner) {
        if (channelSource == null) refreshPodcastChannels(owner);
        return channels;
    }
    public void refreshNewestPodcastEpisodes(LifecycleOwner owner) {
        if (newestSource != null) newest.removeSource(newestSource);
        newestSource=repository.getNewestPodcastEpisodes(20);
        newest.addSource(newestSource,newest::setValue);
    }
    public void refreshPodcastChannels(LifecycleOwner owner) {
        if (channelSource != null) channels.removeSource(channelSource);
        channelSource=repository.getPodcastChannels(false,null);
        channels.addSource(channelSource,channels::setValue);
    }
}
