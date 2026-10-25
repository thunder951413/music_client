package com.eddyizm.tempus.catalog;

import android.content.Context;
import androidx.annotation.Keep;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Public publisher feeds and broadcasts bundled with every flavor of the app. */
@Keep
public class AudioCatalog {
    public int version;
    public String verifiedAt;
    public List<Entry> podcasts = new ArrayList<>();
    public List<Entry> radio = new ArrayList<>();

    @Keep
    public static class Entry {
        public String id, name, category, region, description, url, homepage, artwork, sourceUrl;
        public String label() {
            return category + (region == null || region.isEmpty() ? "" : " · " + region);
        }
    }

    public static AudioCatalog load(Context context) {
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open("chinese_audio_catalog.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, AudioCatalog.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read bundled audio catalog", e);
        }
    }

    /** Treat HTTP/HTTPS variants and trailing slashes as the same subscription. */
    public static String canonicalUrl(String value) {
        if (value == null) return "";
        try {
            URI uri = new URI(value.trim()).normalize();
            String host = uri.getHost();
            if (host == null) return value.trim();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
            return host.toLowerCase(Locale.ROOT) + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                    + path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (Exception e) { return value.trim(); }
    }

    public static boolean isWebUrl(String value) {
        try {
            URI uri = new URI(value);
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (Exception e) { return false; }
    }

    public static boolean shouldSeed(Entry entry, java.util.Set<String> history, java.util.Set<String> urls) {
        return history.add(entry.id) && urls.add(canonicalUrl(entry.url));
    }

    private static AudioCatalog cached;
    public static synchronized Entry radioEntry(String id) {
        if (id == null || !id.startsWith("local_builtin_")) return null;
        if (cached == null) cached = load(com.eddyizm.tempus.App.getContext());
        for (Entry entry : cached.radio) if (id.equals("local_builtin_"+entry.id)) return entry;
        return null;
    }
}
