package com.eddyizm.tempus.discovery;

import com.eddyizm.tempus.util.Preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Library matches of charts, kept for the running app so a chart is searched against the server
 * once per chart update, plus the "missing songs" list built from them.
 */
public final class ChartMatches {
    /** How many chart songs the library has, for the Discover cards. */
    public static final class Summary {
        public final int available, total;
        Summary(int available,int total) { this.available=available;this.total=total; }
    }

    private static final Map<String,List<ChartTrack>> TRACKS=new ConcurrentHashMap<>();
    private static final Map<String,Summary> SUMMARIES=new ConcurrentHashMap<>();

    private ChartMatches() {}

    private static String scope() { return Preferences.getServerId()+":"+Preferences.getActiveMusicFolderId(); }

    private static String key(ChartSource source,String updated) { return scope()+"|"+source.name()+"|"+updated; }

    /** Matched rows for this chart version, or null when it has not been matched yet. */
    public static List<ChartTrack> get(ChartSource source,String updated) {
        return TRACKS.get(key(source,updated));
    }

    public static void put(ChartSource source,String updated,List<ChartTrack> rows) {
        TRACKS.put(key(source,updated),Collections.unmodifiableList(new ArrayList<>(rows)));
        SUMMARIES.put(scope()+"|"+source.name(),new Summary(available(rows),rows.size()));
    }

    public static Summary summary(ChartSource source) { return SUMMARIES.get(scope()+"|"+source.name()); }

    public static int available(List<ChartTrack> rows) {
        int count=0;
        for (ChartTrack row:rows) if (!row.matches.isEmpty()) count++;
        return count;
    }

    /** Checked chart songs with no library match, in chart order. Failed lookups are left out. */
    public static List<ChartTrack> missing(List<ChartTrack> rows) {
        List<ChartTrack> result=new ArrayList<>();
        for (ChartTrack row:rows) if (row.checked && !row.matchFailed && row.matches.isEmpty()) result.add(row);
        return result;
    }

    /**
     * One "title - artist" line per song, the shape QQ Music's text import and playlist migration
     * tools read. Several artists are joined with "/".
     */
    public static String exportText(List<ChartTrack> rows) {
        StringBuilder text=new StringBuilder();
        for (ChartTrack row:rows) {
            String artist=row.artists!=null && !row.artists.isEmpty() ? String.join("/",row.artists) : row.artist;
            text.append(clean(row.title));
            if (artist!=null && !artist.isEmpty()) text.append(" - ").append(clean(artist));
            text.append('\n');
        }
        return text.toString();
    }

    private static String clean(String value) { return value==null ? "" : value.replace('\n',' ').replace('\r',' ').trim(); }
}
