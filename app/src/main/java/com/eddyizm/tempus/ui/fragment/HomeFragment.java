package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.databinding.FragmentHomeBinding;
import com.eddyizm.tempus.ui.activity.MainActivity;

@UnstableApi
public class HomeFragment extends Fragment {
    private FragmentHomeBinding bind;
    private MainActivity activity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        bind = FragmentHomeBinding.inflate(inflater, container, false);
        return bind.getRoot();
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
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    /**
     * Home hosts the Music destination directly; Podcasts and Radio live on ListenFragment.
     */
    public boolean isLibraryScopedTab() {
        return true;
    }
}
