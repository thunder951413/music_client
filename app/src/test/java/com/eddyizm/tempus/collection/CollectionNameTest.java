package com.eddyizm.tempus.collection;

import org.junit.Test;

import static org.junit.Assert.*;

public class CollectionNameTest {
    @Test
    public void allIconsTravelInPlainServerPlaylistNames() {
        for (String icon : CollectionName.ICONS) {
            String name = CollectionName.encode("  通勤的歌  ", icon);
            assertEquals("通勤的歌", CollectionName.title(name));
            assertEquals(icon, CollectionName.icon(name));
        }
    }

    @Test
    public void regularAndUnknownEmojiNamesArePreserved() {
        assertEquals("My old playlist", CollectionName.title("My old playlist"));
        assertEquals("🦄 Collection", CollectionName.title("🦄 Collection"));
        assertEquals("", CollectionName.icon("🦄 Collection"));
    }

    @Test
    public void renameAndIconChangeLeaveTheActualTitleIntact() {
        String old = CollectionName.encode("夜晚", "🌙");
        String next = CollectionName.encode(CollectionName.title(old), "🎧");
        assertEquals("🎧 夜晚", next);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyNamesAreRejected() {
        CollectionName.encode("  ", "🎧");
    }

    @Test
    public void dailyMixInternalNamesAreDetectedAndSanitized() {
        assertTrue(CollectionName.isDailyMixPlaylist("☀️ · 11-09 · PMX110 · 35df31c3", null));
        assertTrue(CollectionName.isDailyMixPlaylist(" · 11-09 · PMX110 · 35df31c3", null));
        assertTrue(CollectionName.isDailyMixPlaylist("每日推荐 · 11-09 · PMX110", null));
        assertTrue(CollectionName.isDailyMixPlaylist("Daily mix · 11-09 · Pixel 9", null));
        assertTrue(CollectionName.isDailyMixPlaylist("Custom Name", "Tempus daily mix v1\n{\"date\":\"2026-11-09\"}"));
        assertFalse(CollectionName.isDailyMixPlaylist("🎧 通勤的歌", null));

        assertEquals("每日推荐 · 11-09", CollectionName.displayTitle("☀️ · 11-09 · PMX110 · 35df31c3", "每日推荐"));
        assertEquals("每日推荐 · 11-09", CollectionName.displayTitle("每日推荐 · 11-09 · PMX110", "每日推荐"));
        assertEquals("Daily Mix · 11-09", CollectionName.displayTitle("Daily mix · 11-09 · Pixel 9", "Daily Mix"));
        assertEquals("通勤的歌", CollectionName.displayTitle("🎧 通勤的歌", "每日推荐"));
    }
}
