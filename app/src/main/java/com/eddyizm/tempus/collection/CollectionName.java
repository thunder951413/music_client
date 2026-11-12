package com.eddyizm.tempus.collection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A visible Unicode prefix keeps the chosen icon portable through standard Subsonic playlists. */
public final class CollectionName {
    public static final String[] ICONS = {"", "🎧", "⭐", "❤️", "🌙", "☀️", "🚗", "🏃", "📚", "☕", "🎮", "🎹", "🌊"};
    private static final String DAILY_COMMENT_PREFIX = "Tempus daily mix v1\n";
    private static final Pattern AUTO_DAILY_PATTERN = Pattern.compile(
            "^(?:(?:每日推荐|Daily\\s+[Mm]ix)\\s*)?·\\s*(?:\\d{4}-)?(\\d{2}-\\d{2})(?:\\s*·\\s*.+)?$"
    );

    private CollectionName() {}

    public static String icon(String name) {
        if (name != null) {
            for (String icon : ICONS) {
                if (!icon.isEmpty() && name.startsWith(icon + " ")) return icon;
            }
        }
        return "";
    }

    public static String title(String name) {
        if (name == null) return "";
        String icon = icon(name);
        return icon.isEmpty() ? name : name.substring(icon.length() + 1);
    }

    public static boolean isDailyMixPlaylist(String name, String comment) {
        if (comment != null && comment.startsWith(DAILY_COMMENT_PREFIX)) {
            return true;
        }
        String clean = title(name).trim();
        return !clean.isEmpty() && AUTO_DAILY_PATTERN.matcher(clean).matches();
    }

    public static String displayTitle(String name, String dailyPrefix) {
        String raw = title(name);
        String trimmed = raw.trim();
        Matcher matcher = AUTO_DAILY_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            String prefix = (dailyPrefix == null || dailyPrefix.trim().isEmpty())
                    ? "每日推荐"
                    : dailyPrefix.trim();
            return prefix + " · " + matcher.group(1);
        }
        return raw;
    }

    public static String encode(String title, String icon) {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("Collection name required");
        boolean valid = false;
        for (String allowed : ICONS) {
            if (allowed.equals(icon)) valid = true;
        }
        return valid && !icon.isEmpty() ? icon + " " + clean : clean;
    }
}
