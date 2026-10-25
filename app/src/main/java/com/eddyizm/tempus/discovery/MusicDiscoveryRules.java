package com.eddyizm.tempus.discovery;

import com.eddyizm.tempus.subsonic.models.Child;
import java.text.Normalizer;
import java.util.*;

/** Shared selection rules; an external chart is a reference, never an external audio source. */
public final class MusicDiscoveryRules {
    private MusicDiscoveryRules() {}
    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value,Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]", "");
    }
    public static List<Child> unheard(List<Child> candidates, Set<String> heard, Set<String> shown, int limit) {
        List<Child> result=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for (Child song:candidates) {
            if (song == null || song.isDir() || song.isVideo() || song.getId()==null || !ids.add(song.getId())) continue;
            if (heard.contains(song.getId()) || shown.contains(song.getId())) continue;
            if (song.getPlayCount()!=null && song.getPlayCount()>0) continue;
            result.add(song); if (result.size()>=limit) break;
        }
        return result;
    }
    public static List<Child> matches(ChartTrack track,List<Child> candidates) {
        List<Child> result=new ArrayList<>(); Set<String> ids=new HashSet<>();
        String title=normalize(track.title);
        Set<String> artists=new HashSet<>();
        if (!track.artists.isEmpty()) for (String artist:track.artists) artists.add(normalize(artist));
        else artists.add(normalize(track.artist));
        artists.remove("");
        if (title.isEmpty() || artists.isEmpty()) return result;
        for (Child song:candidates) {
            if (song==null || song.isVideo() || song.isDir() || !title.equals(normalize(song.getTitle()))) continue;
            Set<String> common=artistNames(song.getArtist());common.add(normalize(song.getArtist()));common.retainAll(artists);
            if (common.isEmpty() || !ids.add(song.getId())) continue;
            // Keep versions (Live, Remix, etc.) in the title comparison; never silently map a cover.
            if (track.duration!=null && song.getDuration()!=null && Math.abs(track.duration-song.getDuration())>12) continue;
            result.add(song);
        }
        return result;
    }
    private static Set<String> artistNames(String value) {
        Set<String> result=new HashSet<>();
        if (value!=null) for (String part:value.split("(?i)\\s+(?:feat\\.?|ft\\.?)\\s+|[/,;&、，]")) {
            String name=normalize(part); if (!name.isEmpty()) result.add(name);
        }
        return result;
    }
}
