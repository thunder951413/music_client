package com.eddyizm.tempus.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaManagerResumePolicyTest {

    @Test
    public void isResumable_onlyForPodcastAudiobookOrLongTracks() {
        assertTrue(MediaManager.isResumable("podcast", 0));
        assertTrue(MediaManager.isResumable("audiobook", 0));
        assertTrue(MediaManager.isResumable("music", 11L * 60L * 1000L));
        assertFalse(MediaManager.isResumable("music", 3L * 60L * 1000L));
        assertFalse(MediaManager.isResumable("radio", 60L * 60L * 1000L));
    }

    @Test
    public void podcastResumesMidwayButRestartsWhenNearlyDone() {
        long hour = 60L * 60L * 1000L;
        assertEquals(0L, MediaManager.resumeOrRestart(0L, hour));
        assertEquals(20L * 60L * 1000L, MediaManager.resumeOrRestart(20L * 60L * 1000L, hour));
        assertEquals(0L, MediaManager.resumeOrRestart(hour - 10_000L, hour));
        // Without a known duration the saved spot is always used.
        assertEquals(hour - 10_000L, MediaManager.resumeOrRestart(hour - 10_000L, 0L));
    }
}
