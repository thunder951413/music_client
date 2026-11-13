package com.eddyizm.tempus.navigation;

import android.content.res.Configuration;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.util.Preferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigationrail.NavigationRailView;

import org.jetbrains.annotations.Contract;

public class NavigationHelper {
    /* UI components */
    private final BottomNavigationView bottomNavigationView;
    private final FrameLayout bottomNavigationViewFrame;
    @Nullable
    private final NavigationRailView navigationRailView;
    private final DrawerLayout drawerLayout;

    /* Navigation components */
    private final NavigationView navigationView;
    private final NavHostFragment navHostFragment;
    private final NavController navController;

    public NavigationHelper(@NonNull BottomNavigationView bottomNavigationView,
                            @NonNull FrameLayout bottomNavigationViewFrame,
                            @NonNull DrawerLayout drawerLayout,
                            @NonNull NavigationView navigationView,
                            @NonNull NavHostFragment navHostFragment,
                            @NonNull NavController navController) {
        this(
                bottomNavigationView,
                bottomNavigationViewFrame,
                null,
                drawerLayout,
                navigationView,
                navHostFragment,
                navController
        );
    }

    public NavigationHelper(@NonNull BottomNavigationView bottomNavigationView,
                            @NonNull FrameLayout bottomNavigationViewFrame,
                            @Nullable NavigationRailView navigationRailView,
                            @NonNull DrawerLayout drawerLayout,
                            @NonNull NavigationView navigationView,
                            @NonNull NavHostFragment navHostFragment,
                            @NonNull NavController navController) {
        this.bottomNavigationView = bottomNavigationView;
        this.bottomNavigationViewFrame = bottomNavigationViewFrame;
        this.navigationRailView = navigationRailView;
        this.drawerLayout = drawerLayout;
        this.navigationView = navigationView;
        this.navHostFragment = navHostFragment;
        this.navController = navController;
    }

    public void syncWithBottomSheetBehavior(@NonNull BottomSheetBehavior<View> bottomSheetBehavior,
                                            @NonNull NavController navController) {
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {
                    // React to the user clicking one of these on bottom-navbar/rail/drawer
                    boolean isTarget = isTargetDestination(destination);
                    int currentState = bottomSheetBehavior.getState();

                    if (isTarget && currentState == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                });

        bindNavigationBar(bottomNavigationView, navController);
        if (navigationRailView != null) {
            bindNavigationBar(navigationRailView, navController);
        }
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    private void bindNavigationBar(@NonNull NavigationBarView barView,
                                   @NonNull NavController navController) {
        NavigationUI.setupWithNavController(barView, navController);
        // Tabs swap at the Home level instead of stacking: NavigationUI pops to the graph's start
        // (the landing page), which is no longer on the back stack, so every tab switch added a
        // layer that Back then walked through. Back from a tab now returns Home, then exits.
        barView.setOnItemSelectedListener(item -> {
            NavOptions options = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(R.id.homeFragment, false, true)
                    .build();
            try {
                navController.navigate(item.getItemId(), null, options);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
        // Tapping the current tab again returns to its first page.
        barView.setOnItemReselectedListener(
                item -> navController.popBackStack(item.getItemId(), false));
    }

    @Contract(pure = true)
    public static boolean isTargetDestination(@NonNull NavDestination destination) {
        int destId = destination.getId();
        return destId == R.id.homeFragment ||
                destId == R.id.externalPlaylistFragment ||
                destId == R.id.listenFragment ||
                destId == R.id.searchFragment ||
                destId == R.id.libraryFragment ||
                destId == R.id.downloadFragment ||
                destId == R.id.albumCatalogueFragment ||
                destId == R.id.artistCatalogueFragment ||
                destId == R.id.genreCatalogueFragment ||
                destId == R.id.playlistCatalogueFragment;
    }

    public void setListenTabVisible(boolean visible) {
        MenuItem bottomItem = bottomNavigationView.getMenu().findItem(R.id.listenFragment);
        if (bottomItem != null && bottomItem.isVisible() != visible) {
            bottomItem.setVisible(visible);
        }
        if (navigationRailView != null) {
            MenuItem railItem = navigationRailView.getMenu().findItem(R.id.listenFragment);
            if (railItem != null && railItem.isVisible() != visible) {
                railItem.setVisible(visible);
            }
        }
        MenuItem drawerItem = navigationView.getMenu().findItem(R.id.listenFragment);
        if (drawerItem != null && drawerItem.isVisible() != visible) {
            drawerItem.setVisible(visible);
        }
    }

    /*
    Clean public methods
    Removes the need to invoke the activity on the fragment
     */

    public void setBottomNavigationBarVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (navigationRailView != null) {
            navigationRailView.setVisibility(visibility);
            bottomNavigationViewFrame.setVisibility(visibility);
            bottomNavigationView.setVisibility(View.GONE);
        } else {
            bottomNavigationView.setVisibility(visibility);
            bottomNavigationViewFrame.setVisibility(View.GONE);
        }
    }

    public void setNavigationDrawerLock(boolean locked) {
        int mode = locked
                ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                : DrawerLayout.LOCK_MODE_UNLOCKED;
        drawerLayout.setDrawerLockMode(mode);
    }

    public boolean isNavigationDrawerLocked() {
        return drawerLayout.getDrawerLockMode(navigationView) != DrawerLayout.LOCK_MODE_UNLOCKED;
    }

    @OptIn(markerClass = UnstableApi.class)
    public void toggleNavigationDrawerLockOnOrientationChange(
            AppCompatActivity activity) {

        int orientation = activity.getResources().getConfiguration().orientation;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (Preferences.getEnableDrawerOnPortrait()) {
            setNavigationDrawerLock(false);
            return;
        }
        setNavigationDrawerLock(!isLandscape);
    }

    public void sethamburgerMenuIconOnToolbar(AppCompatActivity activity, MaterialToolbar toolbar) {

        int orientation = activity.getResources().getConfiguration().orientation;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                    activity,
                    drawerLayout,
                    toolbar,
                    R.string.toolbar_navigation_drawer_open,
                    R.string.toolbar_navigation_drawer_closed
            );
            drawerLayout.addDrawerListener(drawerToggle);
            drawerToggle.syncState();
        }
    }

    /*
    All of these are the "backward compatible" changes that don't break the assumption
    that everything was defined on the activity and is gobally available
     */

    @NonNull
    public NavController getNavController() {
        return navController;
    }

    @NonNull
    public BottomNavigationView getBottomNavigationView() {
        return bottomNavigationView;
    }

    @Nullable
    public NavigationRailView getNavigationRailView() {
        return navigationRailView;
    }

    @NonNull
    public FrameLayout getBottomNavigationViewFrame() {
        return bottomNavigationViewFrame;
    }

    @NonNull
    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    /*
    Auxiliar functions, could be moved somewhere else
     */

    @OptIn(markerClass = UnstableApi.class)
    public void setSystemBarsVisibility(AppCompatActivity activity, boolean visibility) {
        WindowInsetsControllerCompat insetsController;
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        insetsController = new WindowInsetsControllerCompat(window, decorView);

        if (visibility) {
            insetsController.show(WindowInsetsCompat.Type.navigationBars());
            insetsController.show(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        } else {
            insetsController.hide(WindowInsetsCompat.Type.navigationBars());
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
