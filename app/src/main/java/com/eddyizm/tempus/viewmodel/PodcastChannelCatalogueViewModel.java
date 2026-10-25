package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.eddyizm.tempus.repository.PodcastRepository;
import com.eddyizm.tempus.subsonic.models.PodcastChannel;
import java.util.List;

public class PodcastChannelCatalogueViewModel extends AndroidViewModel {
    private final LiveData<List<PodcastChannel>> channels;
    public PodcastChannelCatalogueViewModel(@NonNull Application application) {
        super(application);
        channels=new PodcastRepository().getPodcastChannels(false,null);
    }
    public LiveData<List<PodcastChannel>> getPodcastChannels(LifecycleOwner owner) { return channels; }
}
