package com.eddyizm.tempus.podcast;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class RssFeedParserTest {
    private com.eddyizm.tempus.subsonic.models.PodcastChannel parse(String xml) throws Exception {
        return RssFeedParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),"https://example.org/feed.xml","rss_test");
    }
    @Test public void parsesRssAudioImageDurationAndStableGuid() throws Exception {
        String xml="<rss xmlns:itunes='http://www.itunes.com/dtds/podcast-1.0.dtd'><channel><title>中文节目</title><itunes:image href='/cover.jpg'/><item><title>第一期</title><guid>unchanging</guid><itunes:duration>1:02:03</itunes:duration><pubDate>Fri, 23 Oct 2026 08:00:00 +0800</pubDate><description><![CDATA[<p>你好</p>]]></description><enclosure url='/episode.mp3' type='audio/mpeg'/></item></channel></rss>";
        var channel=parse(xml); var e=channel.getEpisodes().get(0);
        assertEquals("中文节目",channel.getTitle()); assertEquals("https://example.org/cover.jpg",e.getCoverArtId());
        assertEquals("https://example.org/episode.mp3",e.getMediaUrl()); assertEquals(Integer.valueOf(3723),e.getDuration());
        assertNotNull(e.getPublishDate()); assertEquals("<p>你好</p>",e.getDescription()); assertEquals("completed",e.getStatus());
        assertEquals(e.getId(),parse(xml.replace("episode.mp3","new-host.mp3")).getEpisodes().get(0).getId());
    }
    @Test public void parsesAtomAndSkipsVideo() throws Exception {
        var c=parse("<feed xmlns='http://www.w3.org/2005/Atom'><title>Atom</title><logo>/art.png</logo><entry><id>abc</id><title>Episode</title><published>2026-10-23T12:00:00Z</published><link rel='enclosure' type='video/mp4' href='/video.mp4'/><link rel='enclosure' type='audio/aac' href='/audio.aac'/></entry></feed>");
        assertEquals(1,c.getEpisodes().size()); assertEquals("https://example.org/audio.aac",c.getEpisodes().get(0).getMediaUrl());
        assertNotNull(c.getEpisodes().get(0).getPublishDate());
    }
    @Test public void rejectsExternalAndInternalEntities() throws Exception {
        for (String dtd:new String[]{"<!DOCTYPE rss SYSTEM 'file:///etc/passwd'>","<!DOCTYPE rss [<!ENTITY x 'entity'>]>"}) {
            try { parse(dtd+"<rss><channel><title>bad</title><item><title>&x;</title><enclosure url='https://example.org/a.mp3'/></item></channel></rss>"); fail("DTD accepted"); }
            catch (org.xml.sax.SAXException expected) { }
        }
    }
    @Test public void boundsLargeFeedsAndStopsReadingOldItems() throws Exception {
        StringBuilder xml=new StringBuilder("<rss><channel><title>Daily</title>");
        for(int i=0;i<80;i++) xml.append("<item><title>ep</title><enclosure url='https://example.org/").append(i).append(".mp3'/></item>");
        xml.append("<malformed old archive");
        assertEquals(60,parse(xml.toString()).getEpisodes().size());
    }
    @Test public void deduplicatesEnclosuresAndIgnoresNonWebUrls() throws Exception {
        var c=parse("<rss><channel><title>A</title><item><enclosure url='file:///secret'/></item><item><enclosure url='/a.mp3'/></item><item><enclosure url='/a.mp3'/></item></channel></rss>");
        assertEquals(1,c.getEpisodes().size());
    }
    @Test public void rejectsNonFeedsAndFeedsWithoutAudio() throws Exception {
        for (String xml:new String[]{"<html><title>not RSS</title></html>","<rss><channel><title>Text feed</title><item><title>Text</title></item></channel></rss>"}) {
            try { parse(xml); fail("Empty feed accepted"); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void handlesMissingOptionalMetadataAndInvalidDuration() throws Exception {
        var e=parse("<rss><channel><title>A</title><item><duration>invalid</duration><enclosure url='/a.mp3'/></item></channel></rss>").getEpisodes().get(0);
        assertNull(e.getDuration()); assertNull(e.getPublishDate()); assertEquals("A",e.getTitle());
    }
}
