package com.eddyizm.tempus.podcast;

import com.eddyizm.tempus.catalog.AudioCatalog;
import com.eddyizm.tempus.subsonic.models.PodcastChannel;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;
import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/** Streaming RSS/Atom reader: stops after 60 audio entries, including on very large daily feeds. */
public final class RssFeedParser {
    public static final int MAX_EPISODES = 60;
    private RssFeedParser() {}
    private static final class EnoughEpisodes extends SAXException {}

    public static PodcastChannel parse(InputStream input, String feedUrl, String channelId) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        var reader = factory.newSAXParser().getXMLReader();
        Handler handler = new Handler(feedUrl,channelId);
        reader.setContentHandler(handler);
        reader.setEntityResolver(handler);
        reader.setErrorHandler(handler);
        // Both Android Expat and the JVM expose the SAX lexical handler. Reject DTDs before
        // any entities can be expanded; no external resources or publisher credentials.
        reader.setProperty("http://xml.org/sax/properties/lexical-handler",handler);
        try { reader.parse(new InputSource(input)); } catch (EnoughEpisodes expected) { }
        if (handler.channel.getTitle() == null || handler.episodes.isEmpty())
            throw new IllegalArgumentException("Feed has no playable audio episodes");
        handler.episodes.sort(Comparator.comparing(PodcastEpisode::getPublishDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        handler.channel.setEpisodes(handler.episodes);
        return handler.channel;
    }

    private static final class Handler extends DefaultHandler2 {
        final String base, channelId;
        final PodcastChannel channel = new PodcastChannel();
        final List<PodcastEpisode> episodes = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final Deque<Field> fields = new ArrayDeque<>();
        int depth, channelDepth = -1, itemDepth = -1, imageDepth = -1;
        PodcastEpisode episode;
        String guid;
        Handler(String base, String id) {
            this.base=base; channelId=id;
            channel.setId(id); channel.setUrl(base); channel.setStatus("ready");
        }
        @Override public void startDTD(String name,String publicId,String systemId) throws SAXException {
            throw new SAXException("DTDs are not allowed in podcast feeds");
        }
        @Override public InputSource resolveEntity(String publicId,String systemId) throws SAXException {
            throw new SAXException("External entities are not allowed");
        }
        @Override public void startElement(String uri,String local,String qName,Attributes a) {
            depth++;
            if (depth == 1 && "feed".equals(local) || depth == 2 && "channel".equals(local)) channelDepth=depth;
            if (channelDepth == -1) return;
            if (depth == channelDepth+1 && ("item".equals(local) || "entry".equals(local))) {
                itemDepth=depth; episode=new PodcastEpisode(); guid=null;
            }
            int ownerDepth = episode == null ? channelDepth : itemDepth;
            if (depth == ownerDepth+1) {
                if ("image".equals(local)) {
                    imageDepth=depth; setImage(resolve(base,a.getValue("href")));
                }
                if (episode != null && ("enclosure".equals(local) || "content".equals(local) && a.getValue("url") != null
                        || "link".equals(local) && "enclosure".equals(a.getValue("rel")))) {
                    String type = a.getValue("type");
                    if (type == null || type.isEmpty() || type.startsWith("audio/") || type.equals("application/octet-stream")) {
                        String audio = resolve(base,first(a.getValue("url"),a.getValue("href")));
                        if (episode.getMediaUrl() == null && audio != null) {
                            episode.setMediaUrl(audio); episode.setStreamId(audio);
                            episode.setContentType(type == null || type.isEmpty() ? "audio/mpeg" : type);
                            try { episode.setSize(Long.parseLong(a.getValue("length"))); } catch (Exception ignored) {}
                        }
                    }
                }
                if (Arrays.asList("title","description","subtitle","summary","encoded","content","guid","id","duration","pubDate","published","updated","logo","icon").contains(local))
                    fields.push(new Field(local,depth));
            } else if (imageDepth > 0 && depth == imageDepth+1 && "url".equals(local)) fields.push(new Field("imageUrl",depth));
        }
        private void setImage(String url) {
            if (url == null) return;
            if (episode == null) { channel.setCoverArtId(url); channel.setOriginalImageUrl(url); }
            else episode.setCoverArtId(url);
        }
        @Override public void characters(char[] chars,int start,int length) {
            for (Field field:fields) if (field.value.length()<65536) field.value.append(chars,start,Math.min(length,65536-field.value.length()));
        }
        @Override public void endElement(String uri,String local,String qName) throws SAXException {
            if (!fields.isEmpty() && fields.peek().depth == depth) {
                Field field=fields.pop(); String value=first(field.value.toString());
                if (episode == null) {
                    if ("title".equals(field.name)) channel.setTitle(value);
                    if (Arrays.asList("description","subtitle","summary").contains(field.name) && channel.getDescription()==null) channel.setDescription(value);
                } else {
                    switch (field.name) {
                        case "title": episode.setTitle(value); break;
                        case "guid": case "id": guid=first(guid,value); break;
                        case "description": case "summary": case "encoded": case "content":
                            if (episode.getDescription()==null) episode.setDescription(value); break;
                        case "duration": episode.setDuration(duration(value)); break;
                        case "pubDate": case "published": case "updated":
                            if (episode.getPublishDate()==null) episode.setPublishDate(date(value)); break;
                    }
                }
                if (Arrays.asList("imageUrl","logo","icon").contains(field.name)) setImage(resolve(base,value));
            }
            if (depth==imageDepth) imageDepth=-1;
            if (depth==itemDepth && episode!=null) {
                if (episode.getMediaUrl()!=null && seen.add(episode.getMediaUrl())) {
                    episode.setId("rss_ep_"+digest(channelId+":"+first(guid,episode.getMediaUrl())));
                    episode.setChannelId(channelId); episode.setParentId(channelId);
                    episode.setTitle(first(episode.getTitle(),channel.getTitle()));
                    episode.setArtist(channel.getTitle()); episode.setAlbum(channel.getTitle());
                    episode.setCoverArtId(first(episode.getCoverArtId(),channel.getCoverArtId()));
                    episode.setStatus("completed"); episode.setType("podcast");
                    episodes.add(episode);
                }
                episode=null; itemDepth=-1;
                if (episodes.size()>=MAX_EPISODES) throw new EnoughEpisodes();
            }
            depth--;
        }
    }
    private static final class Field {
        final String name; final int depth; final StringBuilder value=new StringBuilder();
        Field(String name,int depth) { this.name=name; this.depth=depth; }
    }
    public static String digest(String value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for (int i=0;i<16;i++) out.append(String.format(Locale.ROOT,"%02x",bytes[i]));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String first(String... values) {
        for (String v:values) if (v!=null && !v.trim().isEmpty()) return v.trim();
        return null;
    }
    private static String resolve(String base,String value) {
        try { String url=new URI(base).resolve(value).toString(); return AudioCatalog.isWebUrl(url)?url:null; }
        catch (Exception ignored) { return null; }
    }
    static Integer duration(String value) {
        if (value==null) return null;
        try {
            long seconds=0;
            for (String part:value.split(":")) seconds=seconds*60+(long)Double.parseDouble(part);
            return seconds>=0 && seconds<=Integer.MAX_VALUE?(int)seconds:null;
        } catch (Exception ignored) { return null; }
    }
    private static Date date(String value) {
        if (value==null) return null;
        for (String pattern:new String[]{"EEE, dd MMM yyyy HH:mm:ss Z","EEE, dd MMM yyyy HH:mm Z","dd MMM yyyy HH:mm:ss Z","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss.SSSXXX"}) {
            try { return new SimpleDateFormat(pattern,Locale.US).parse(value); } catch (Exception ignored) {}
        }
        return null;
    }
}
