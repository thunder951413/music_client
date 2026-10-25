package com.eddyizm.tempus.catalog;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.model.InternetRadioStationCache;
import java.util.HashSet;
import java.util.Set;

/** Stable IDs and persistent seed history make upgrades additive and deletions durable. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class BuiltinRadioSeeder {
    private BuiltinRadioSeeder() {}
    public static synchronized void seed() {
        var preferences = App.getContext().getSharedPreferences("audio_catalog",0);
        Set<String> seeded = new HashSet<>(preferences.getStringSet("radio_seeded",new HashSet<>()));
        var dao = AppDatabase.getInstance().internetRadioStationDao();
        Set<String> urls = new HashSet<>();
        for (InternetRadioStationCache c : dao.getAll()) urls.add(AudioCatalog.canonicalUrl(c.getStreamUrl()));
        for (AudioCatalog.Entry e : AudioCatalog.load(App.getContext()).radio) {
            if (!AudioCatalog.shouldSeed(e,seeded,urls)) continue;
            InternetRadioStationCache c = new InternetRadioStationCache();
            c.setId("local_builtin_"+e.id); c.setName(e.name); c.setStreamUrl(e.url);
            c.setHomePageUrl(e.homepage); c.setCoverArtUrl(e.artwork);
            c.setSource(InternetRadioStationCache.SOURCE_LOCAL);
            dao.insert(c);
        }
        preferences.edit().putStringSet("radio_seeded",seeded).commit();
    }
}
