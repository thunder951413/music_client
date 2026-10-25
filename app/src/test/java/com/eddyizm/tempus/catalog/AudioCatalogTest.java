package com.eddyizm.tempus.catalog;
import org.junit.Test;
import java.util.*;
import java.io.*;
import com.google.gson.Gson;
import static org.junit.Assert.*;
public class AudioCatalogTest {
    private AudioCatalog.Entry entry(String id,String url) { var e=new AudioCatalog.Entry();e.id=id;e.url=url;return e; }
    @Test public void seededDeletionStaysDeletedAndNewEditionsAreAdditive() {
        Set<String> history=new HashSet<>(), urls=new HashSet<>();
        var first=entry("one","https://example.org/one");
        assertTrue(AudioCatalog.shouldSeed(first,history,urls));
        urls.clear(); // User unsubscribes; seed history remains.
        assertFalse(AudioCatalog.shouldSeed(first,history,urls));
        assertTrue(AudioCatalog.shouldSeed(entry("two","https://example.org/two"),history,urls));
    }
    @Test public void equivalentExistingCustomFeedIsPreservedAndRemembered() {
        Set<String> history=new HashSet<>(), urls=new HashSet<>(Collections.singleton(AudioCatalog.canonicalUrl("http://EXAMPLE.org/a/")));
        var e=entry("builtin","https://example.org/a");
        assertFalse(AudioCatalog.shouldSeed(e,history,urls));
        urls.clear(); assertFalse(AudioCatalog.shouldSeed(e,history,urls));
    }
    @Test public void bundledCatalogHasUniqueStableIdsValidUrlsAndRequiredCoverage() throws Exception {
        try (Reader reader=new java.io.InputStreamReader(new FileInputStream("src/main/assets/chinese_audio_catalog.json"),java.nio.charset.StandardCharsets.UTF_8)) {
            AudioCatalog c=new Gson().fromJson(reader,AudioCatalog.class);
            assertTrue(c.radio.size()>=40); assertTrue(c.podcasts.size()>=30);
            Set<String> ids=new HashSet<>(),urls=new HashSet<>(),categories=new HashSet<>();
            List<AudioCatalog.Entry> all=new ArrayList<>(c.radio);all.addAll(c.podcasts);
            for(var e:all) {
                assertTrue(e.id,ids.add(e.id)); assertTrue(e.name,AudioCatalog.isWebUrl(e.url));
                assertTrue(e.name,urls.add(AudioCatalog.canonicalUrl(e.url))); assertNotNull(e.category); categories.add(e.category);
                assertTrue(e.name,AudioCatalog.isWebUrl(e.sourceUrl));
            }
            assertTrue(c.podcasts.stream().anyMatch(e->e.name.equals("日谈公园")));
            assertTrue(c.podcasts.stream().anyMatch(e->e.region.equals("海外中文")));
            assertTrue(categories.containsAll(Arrays.asList("新闻资讯","音乐","故事文艺","科技互联网","商业财经")));
        }
    }
}
