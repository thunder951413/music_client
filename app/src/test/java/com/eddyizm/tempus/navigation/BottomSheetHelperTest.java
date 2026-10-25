package com.eddyizm.tempus.navigation;
import android.os.Handler;
import android.view.View;
import androidx.fragment.app.FragmentManager;
import com.eddyizm.tempus.viewmodel.MainViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import org.junit.Test;
import org.mockito.MockedConstruction;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
public class BottomSheetHelperTest {
    @Test public void passivePeekDoesNotOverrideExplicitExpansion() {
        try (MockedConstruction<Handler> handlers=mockConstruction(Handler.class)) {
            BottomSheetBehavior<View> behavior=mock(BottomSheetBehavior.class);
            BottomSheetHelper helper=new BottomSheetHelper(behavior,mock(View.class),mock(FragmentManager.class));
            helper.expand();clearInvocations(behavior);helper.setStateInPeek(true);
            verify(behavior,never()).setState(BottomSheetBehavior.STATE_COLLAPSED);assertTrue(helper.isExpanded());
        }
    }
    @Test public void queuedInitializationCannotCollapseAnExpansionThatHappenedLater() {
        List<Runnable> tasks=new ArrayList<>();
        try (MockedConstruction<Handler> handlers=mockConstruction(Handler.class,(handler,context)->{
            when(handler.postDelayed(any(Runnable.class),anyLong())).thenAnswer(call->{tasks.add(call.getArgument(0));return true;});
        })) {
            BottomSheetBehavior<View> behavior=mock(BottomSheetBehavior.class);when(behavior.getState()).thenReturn(BottomSheetBehavior.STATE_HIDDEN);
            BottomSheetHelper helper=new BottomSheetHelper(behavior,mock(View.class),mock(FragmentManager.class));
            MainViewModel model=mock(MainViewModel.class);when(model.isQueueLoaded()).thenReturn(true);
            helper.checkAfterStateChanged(model);assertEquals(1,tasks.size());helper.expand();clearInvocations(behavior);tasks.get(0).run();
            verify(behavior,never()).setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }
    @Test public void settlingTowardsCollapsedIsNotTreatedAsExpanded() {
        try (MockedConstruction<Handler> handlers=mockConstruction(Handler.class)) {
            BottomSheetBehavior<View> behavior=mock(BottomSheetBehavior.class);when(behavior.getState()).thenReturn(BottomSheetBehavior.STATE_SETTLING);
            BottomSheetHelper helper=new BottomSheetHelper(behavior,mock(View.class),mock(FragmentManager.class));assertFalse(helper.isExpanded());helper.setStateInPeek(true);
            verify(behavior).setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @Test public void navigationInsetIsAddedOnceToTheConfiguredPeekHeight() {
        try (MockedConstruction<Handler> handlers = mockConstruction(Handler.class)) {
            BottomSheetBehavior<View> behavior = mock(BottomSheetBehavior.class);
            when(behavior.getPeekHeight()).thenReturn(456);
            BottomSheetHelper helper = new BottomSheetHelper(behavior, mock(View.class), mock(FragmentManager.class));
            helper.setBottomInset(42);
            helper.setBottomInset(42);
            verify(behavior, times(1)).setPeekHeight(498);
        }
    }

    @Test public void compactPeekAndHiddenSystemBarsUseTheCurrentInset() {
        try (MockedConstruction<Handler> handlers = mockConstruction(Handler.class)) {
            BottomSheetBehavior<View> behavior = mock(BottomSheetBehavior.class);
            BottomSheetHelper helper = new BottomSheetHelper(behavior, mock(View.class), mock(FragmentManager.class));
            helper.setPeekHeight(152, 3f);
            helper.setBottomInset(42);
            clearInvocations(behavior);
            helper.setPeekHeight(72, 3f);
            verify(behavior).setPeekHeight(258);
            helper.setBottomInset(0);
            verify(behavior).setPeekHeight(216);
        }
    }
}
