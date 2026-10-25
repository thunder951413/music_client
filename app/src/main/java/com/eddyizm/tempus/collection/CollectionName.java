package com.eddyizm.tempus.collection;

/** A visible Unicode prefix keeps the chosen icon portable through standard Subsonic playlists. */
public final class CollectionName {
    public static final String[] ICONS={"", "🎧", "⭐", "❤️", "🌙", "☀️", "🚗", "🏃", "📚", "☕", "🎮", "🎹", "🌊"};
    private CollectionName() {}
    public static String icon(String name) {
        if (name!=null) for (String icon:ICONS) if (!icon.isEmpty() && name.startsWith(icon+" ")) return icon;
        return "";
    }
    public static String title(String name) {
        if (name==null) return "";
        String icon=icon(name);return icon.isEmpty()?name:name.substring(icon.length()+1);
    }
    public static String encode(String title,String icon) {
        String clean=title==null?"":title.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("Collection name required");
        boolean valid=false;for (String allowed:ICONS) if (allowed.equals(icon)) valid=true;
        return valid && !icon.isEmpty()?icon+" "+clean:clean;
    }
}
