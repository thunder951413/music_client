package com.eddyizm.tempus.podcast;
import com.eddyizm.tempus.util.Preferences.ResumePoint;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;
public class RssResumePointTest {
    @Test public void oldResumeRecordsRemainReadable() {
        var point=new Gson().fromJson("{\"id\":\"music\",\"position\":12000,\"timestamp\":100}",ResumePoint.class);
        assertEquals(12000,point.getPosition()); assertNull(point.getMediaUrl());
    }
    @Test public void rssPlaybackUrlSurvivesWithoutTheFeedCache() {
        var point=new ResumePoint("rss_ep_a",12000,"Episode","Channel","Channel",null,null,"podcast",100,"https://example.org/a.mp3");
        var restored=new Gson().fromJson(new Gson().toJson(point),ResumePoint.class);
        assertEquals("https://example.org/a.mp3",restored.getMediaUrl()); assertEquals(12000,restored.getPosition());
    }
}
