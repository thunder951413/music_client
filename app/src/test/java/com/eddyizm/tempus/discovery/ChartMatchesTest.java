package com.eddyizm.tempus.discovery;

import com.eddyizm.tempus.subsonic.models.Child;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ChartMatchesTest {
    private static ChartTrack track(String title, List<String> artists, boolean checked, boolean failed, boolean matched) {
        ChartTrack t = new ChartTrack();
        t.title = title;
        t.artists = artists;
        t.artist = String.join(" / ", artists);
        t.checked = checked;
        t.matchFailed = failed;
        if (matched) t.matches = Collections.singletonList(new Child("id-" + title));
        return t;
    }

    @Test
    public void missingKeepsOnlyCheckedUnmatchedSongsInOrder() {
        List<ChartTrack> rows = Arrays.asList(
                track("有", List.of("A"), true, false, true),
                track("缺一", List.of("B"), true, false, false),
                track("失败", List.of("C"), true, true, false),
                track("未查", List.of("D"), false, false, false),
                track("缺二", List.of("E"), true, false, false));
        List<ChartTrack> missing = ChartMatches.missing(rows);
        assertEquals(2, missing.size());
        assertEquals("缺一", missing.get(0).title);
        assertEquals("缺二", missing.get(1).title);
        assertEquals(1, ChartMatches.available(rows));
    }

    @Test
    public void exportWritesOneTitleArtistLinePerSong() {
        String text = ChartMatches.exportText(Arrays.asList(
                track("晴天", List.of("周杰伦"), true, false, false),
                track("Song\nTwo ", Arrays.asList("A", "B"), true, false, false)));
        assertEquals("晴天 - 周杰伦\nSong Two - A/B\n", text);
    }

    @Test
    public void sameSongOnTwoChartsSharesOneKey() {
        ChartTrack netease = track("晴天", Arrays.asList("周杰伦"), true, false, false);
        ChartTrack qq = track("晴天（Live）".replace("（Live）", ""), Arrays.asList("周杰伦", "合唱"), true, false, false);
        ChartTrack other = track("晴天", Arrays.asList("别人"), true, false, false);
        assertEquals(AllChartsMatcher.songKey(netease), AllChartsMatcher.songKey(qq));
        org.junit.Assert.assertNotEquals(AllChartsMatcher.songKey(netease), AllChartsMatcher.songKey(other));
    }
}
