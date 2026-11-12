package com.eddyizm.tempus.navigation;

import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;

import com.eddyizm.tempus.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class NavigationController {

    NavigationHelper helper;

    public NavigationController(@NonNull NavigationHelper helper) {
        this.helper = helper;
    }

    public void syncWithBottomSheetBehavior(BottomSheetBehavior<View> bottomSheetBehavior,
                                            NavController navController) {
        helper.syncWithBottomSheetBehavior(bottomSheetBehavior, navController);
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.listenFragment
                    && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
    }

    public void setListenTabVisible(boolean visible) {
        MenuItem bottomItem = helper.getBottomNavigationView().getMenu().findItem(R.id.listenFragment);
        if (bottomItem != null && bottomItem.isVisible() != visible) {
            bottomItem.setVisible(visible);
        }
    }

    public void setNavbarVisibility(boolean visibility) {
        helper.setBottomNavigationBarVisibility(visibility);
    }

    public void setDrawerLock(boolean visibility) {
        helper.setNavigationDrawerLock(visibility);
    }

    public boolean isNavigationDrawerLocked() {
        return helper.isNavigationDrawerLocked();
    }

    public void toggleDrawerLockOnOrientation(AppCompatActivity activity) {
        helper.toggleNavigationDrawerLockOnOrientationChange(activity);
    }

    public void setSystemBarsVisibility(AppCompatActivity activity, boolean visibility) {
        helper.setSystemBarsVisibility(activity, visibility);
    }

    public void setHamburgerMenuForLandscape(AppCompatActivity activity, MaterialToolbar toolbar) {
        helper.sethamburgerMenuIconOnToolbar(activity, toolbar);
    }

    @NonNull
    public NavController getNavController() {
        return helper.getNavController();
    }
}
