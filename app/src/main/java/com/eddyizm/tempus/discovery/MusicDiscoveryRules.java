package com.eddyizm.tempus.discovery;

import com.eddyizm.tempus.subsonic.models.Child;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared selection rules; an external chart is a reference, never an external audio source. */
public final class MusicDiscoveryRules {
    private static final Pattern ARTIST_SPLIT = Pattern.compile("(?i)\\s+(?:feat\\.?|ft\\.?)\\s+|\\s+[•·]\\s+|[/,;&、，]");
    private static final Pattern ARTIST_ALIAS = Pattern.compile("^\\s*(.+?)\\s*\\(([^()]+)\\)\\s*$");
    private static final Pattern TITLE_PAREN = Pattern.compile("\\([^()]+\\)");
    private static final Pattern VERSION_WORDS = Pattern.compile(
            "(?i)live|remix|mix|version|explicit|clean|acoustic|instrumental|\\binst\\b|remaster|edit|\\bdj\\b|版|伴奏|现场|翻唱|合唱|剪辑|加速|钢琴"
    );

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
        Set<String> titles=titleNames(track.title, track.originalTitle);
        Set<String> artists=new HashSet<>();
        if (!track.artists.isEmpty()) for (String artist:track.artists) artists.addAll(artistAliases(artist));
        else artists.addAll(artistAliases(track.artist));
        artists.remove("");
        if (titles.isEmpty() || artists.isEmpty()) return result;
        for (Child song:candidates) {
            if (song==null || song.isVideo() || song.isDir() || !titles.contains(normalize(song.getTitle()))) continue;
            Set<String> common=artistNames(song.getArtist());
            common.retainAll(artists);
            if (common.isEmpty() || !ids.add(song.getId())) continue;
            // Keep versions (Live, Remix, etc.) in the title comparison; never silently map a cover.
            if (track.duration!=null && song.getDuration()!=null && Math.abs(track.duration-song.getDuration())>12) continue;
            result.add(song);
        }
        return result;
    }
    public static Set<String> titleNames(String title, String originalTitle) {
        Set<String> names = new LinkedHashSet<>();
        if (title == null) return names;
        String full = Normalizer.normalize(title, Normalizer.Form.NFKC);
        String normalized = normalize(full);
        if (!normalized.isEmpty()) names.add(normalized);
        if (originalTitle != null) {
            List<int[]> groups = new ArrayList<>();
            Matcher matcher = TITLE_PAREN.matcher(full);
            while (matcher.find() && groups.size() < 4) {
                groups.add(new int[]{matcher.start(), matcher.end()});
            }
            List<String> variants = new ArrayList<>();
            variants.add(full);
            for (int i = groups.size() - 1; i >= 0; i--) {
                int[] span = groups.get(i);
                String group = full.substring(span[0], span[1]);
                if (!VERSION_WORDS.matcher(group).find()) {
                    List<String> additions = new ArrayList<>(variants.size());
                    for (String v : variants) {
                        additions.add(v.substring(0, span[0]) + v.substring(span[1]));
                    }
                    variants.addAll(additions);
                }
            }
            String orig = normalize(originalTitle);
            if (!orig.isEmpty()) {
                for (String v : variants) {
                    if (normalize(v).equals(orig)) {
                        names.add(orig);
                        break;
                    }
                }
            }
        }
        return names;
    }
    public static List<String> searchTitles(ChartTrack track) {
        List<String> titles = new ArrayList<>();
        if (track == null || track.title == null || track.title.isBlank()) return titles;
        titles.add(track.title);
        if (track.originalTitle != null && !track.originalTitle.isBlank()) {
            String origNorm = normalize(track.originalTitle);
            if (!origNorm.equals(normalize(track.title))
                    && titleNames(track.title, track.originalTitle).contains(origNorm)) {
                titles.add(track.originalTitle);
            }
        }
        return titles;
    }
    public static Set<String> artistAliases(String value) {
        Set<String> names = new HashSet<>();
        if (value == null) return names;
        String full = Normalizer.normalize(value, Normalizer.Form.NFKC);
        String base = normalize(full);
        if (!base.isEmpty()) names.add(base);
        Matcher alias = ARTIST_ALIAS.matcher(full);
        if (alias.matches()) {
            String left = normalize(alias.group(1));
            String right = normalize(alias.group(2));
            if (!left.isEmpty()) names.add(left);
            if (!right.isEmpty()) names.add(right);
        }
        return names;
    }
    public static Set<String> artistNames(String value) {
        Set<String> result = new HashSet<>();
        if (value == null) return result;
        result.addAll(artistAliases(value));
        for (String part : ARTIST_SPLIT.split(value)) {
            result.addAll(artistAliases(part));
        }
        return result;
    }
}
