package com.eddyizm.tempus.collection;
import org.junit.Test;
import static org.junit.Assert.*;
public class CollectionNameTest {
    @Test public void allIconsTravelInPlainServerPlaylistNames() {
        for (String icon:CollectionName.ICONS) {
            String name=CollectionName.encode("  通勤的歌  ",icon);
            assertEquals("通勤的歌",CollectionName.title(name));assertEquals(icon,CollectionName.icon(name));
        }
    }
    @Test public void regularAndUnknownEmojiNamesArePreserved() {
        assertEquals("My old playlist",CollectionName.title("My old playlist"));assertEquals("🦄 Collection",CollectionName.title("🦄 Collection"));assertEquals("",CollectionName.icon("🦄 Collection"));
    }
    @Test public void renameAndIconChangeLeaveTheActualTitleIntact() {
        String old=CollectionName.encode("夜晚","🌙");String next=CollectionName.encode(CollectionName.title(old),"🎧");assertEquals("🎧 夜晚",next);
    }
    @Test(expected=IllegalArgumentException.class) public void emptyNamesAreRejected() { CollectionName.encode("  ","🎧"); }
}
