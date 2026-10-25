package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.R;



public class PodcastChannelEditorViewModel extends AndroidViewModel {
    private static final String TAG = "PodcastChannelEditorViewModel";


    private final MutableLiveData<Boolean> isSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public PodcastChannelEditorViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Boolean> getIsSuccess() {
        return isSuccess;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void reset() { isSuccess.setValue(false); errorMessage.setValue(null); }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public void createChannel(String url) {
        errorMessage.setValue(null);
        isSuccess.setValue(false);

        com.eddyizm.tempus.podcast.LocalPodcastStore.getInstance().subscribe(url, error -> {
            if (error == null) isSuccess.setValue(true);
            else showError(error);
        });
    }

    private void showError(String message) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show();
        errorMessage.setValue(message);
        Log.e(TAG, "Error shown: " + message);
    }
}