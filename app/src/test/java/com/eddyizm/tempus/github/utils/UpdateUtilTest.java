package com.eddyizm.tempus.github.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateUtilTest {

    @Test
    public void newerTagIsGreater() {
        assertTrue(UpdateUtil.compareVersions("v1.0.1", "1.0.0") > 0);
        assertTrue(UpdateUtil.compareVersions("v1.10.0", "1.9.9") > 0);
        assertTrue(UpdateUtil.compareVersions("v2.0", "1.9.9") > 0);
    }

    @Test
    public void sameOrOlderTagIsNotGreater() {
        assertEquals(0, UpdateUtil.compareVersions("v1.0.0", "1.0.0"));
        assertEquals(0, UpdateUtil.compareVersions("v1.0", "1.0.0"));
        assertTrue(UpdateUtil.compareVersions("v1.0.0", "1.2.0") < 0);
    }

    @Test
    public void suffixesAreIgnoredAndGarbageIsNotAnUpdate() {
        assertEquals(0, UpdateUtil.compareVersions("v1.0.0-beta", "1.0.0"));
        assertEquals(0, UpdateUtil.compareVersions("latest", "1.0.0"));
    }

    @Test
    public void flavorAwareAssetSelectionPicksMatchingApkOrSingleFallback() {
        com.eddyizm.tempus.github.models.Assets degoogled = new com.eddyizm.tempus.github.models.Assets();
        degoogled.setName("app-degoogled-release.apk");
        degoogled.setBrowserDownloadUrl("https://example.invalid/degoogled.apk");
        com.eddyizm.tempus.github.models.Assets tempus = new com.eddyizm.tempus.github.models.Assets();
        tempus.setName("app-tempus-release.apk");
        tempus.setBrowserDownloadUrl("https://example.invalid/tempus.apk");
        com.eddyizm.tempus.github.models.Assets mapping = new com.eddyizm.tempus.github.models.Assets();
        mapping.setName("mapping.txt.gz");
        mapping.setBrowserDownloadUrl("https://example.invalid/mapping.txt.gz");

        com.eddyizm.tempus.github.models.LatestRelease dual = new com.eddyizm.tempus.github.models.LatestRelease();
        dual.getAssets().add(mapping);
        dual.getAssets().add(degoogled);
        dual.getAssets().add(tempus);

        assertEquals("app-tempus-release.apk", UpdateUtil.findApkAsset(dual, "tempus").getName());
        assertEquals("app-degoogled-release.apk", UpdateUtil.findApkAsset(dual, "degoogled").getName());

        com.eddyizm.tempus.github.models.Assets singleApk = new com.eddyizm.tempus.github.models.Assets();
        singleApk.setName("music_client-v4.30.0.apk");
        singleApk.setBrowserDownloadUrl("https://example.invalid/music_client.apk");
        com.eddyizm.tempus.github.models.LatestRelease single = new com.eddyizm.tempus.github.models.LatestRelease();
        single.getAssets().add(singleApk);
        assertEquals("music_client-v4.30.0.apk", UpdateUtil.findApkAsset(single, "tempus").getName());
        assertEquals("music_client-v4.30.0.apk", UpdateUtil.findApkAsset(single, "degoogled").getName());
    }
}
