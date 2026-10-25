package com.eddyizm.tempus.discovery;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChartParserTest {
    @Test public void readsNeteaseOldAndNewPayloadsInRankOrder() {
        for (String json:new String[]{"{\"code\":200,\"result\":{\"updateTime\":123,\"tracks\":[{\"id\":1,\"name\":\"歌曲\",\"duration\":180000,\"artists\":[{\"name\":\"歌手\"}],\"album\":{\"picUrl\":\"https://example.org/a.jpg\"}}]}}","{\"code\":200,\"playlist\":{\"tracks\":[{\"id\":2,\"name\":\"歌曲\",\"dt\":181000,\"ar\":[{\"name\":\"歌手\"}],\"al\":{\"picUrl\":\"https://example.org/b.jpg\"}}]}}"}) {
            var result=ChartParser.parse(ChartSource.NETEASE_HOT,json);assertEquals(1,result.tracks.size());
            var t=result.tracks.get(0);assertEquals(1,t.rank);assertEquals("歌曲",t.title);assertEquals("歌手",t.artist);assertTrue(t.duration>=180);assertFalse(t.checked);
        }
    }
    @Test public void readsAppleWithoutInventingDurationOrLibraryIds() {
        var r=ChartParser.parse(ChartSource.APPLE_JP,"{\"feed\":{\"updated\":\"2026-10-25T00:00:00Z\",\"results\":[{\"id\":\"apple-1\",\"name\":\"Song\",\"artistName\":\"Artist\",\"artworkUrl100\":\"https://example.org/a.jpg\",\"url\":\"https://music.apple.com/jp/1\"}]}}");
        assertEquals("apple-1",r.tracks.get(0).id);assertNull(r.tracks.get(0).duration);assertTrue(r.tracks.get(0).matches.isEmpty());assertEquals("2026-10-25T00:00:00Z",r.updated);
    }
    @Test public void failedAndEmptyChartsStayFailures() {
        for (String json:new String[]{"{\"code\":403}","{\"code\":200,\"result\":{\"tracks\":[]}}"}) {
            try { ChartParser.parse(ChartSource.NETEASE_HOT,json);fail("Expected failure"); } catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void boundsDisplayToFirstHundredTracks() {
        StringBuilder entries=new StringBuilder();for (int i=0;i<120;i++) { if(i>0)entries.append(',');entries.append("{\"id\":\"").append(i).append("\",\"name\":\"Song\",\"artistName\":\"Artist\"}"); }
        var r=ChartParser.parse(ChartSource.APPLE_CN,"{\"feed\":{\"results\":["+entries+"]}}");assertEquals(100,r.tracks.size());assertEquals("99",r.tracks.get(99).id);assertEquals(100,r.tracks.get(99).rank);
    }
}
