package com.eddyizm.tempus.github.utils;

import androidx.annotation.Nullable;

import com.eddyizm.tempus.BuildConfig;
import com.eddyizm.tempus.github.models.Assets;
import com.eddyizm.tempus.github.models.LatestRelease;

public class UpdateUtil {

    public static boolean showUpdateDialog(LatestRelease release) {
        if (release.getTagName() == null) return false;
        if (Boolean.TRUE.equals(release.getDraft()) || Boolean.TRUE.equals(release.getPrerelease())) return false;
        return compareVersions(release.getTagName(), BuildConfig.VERSION_NAME) > 0;
    }

    /** Whether a release tag such as "v1.2.0" is newer than the installed version. */
    public static boolean isNewerThanInstalled(String tag) {
        return tag != null && compareVersions(tag, BuildConfig.VERSION_NAME) > 0;
    }

    /** The release's APK matching the installed flavor, or null when the release has none attached. */
    @Nullable
    public static Assets findApkAsset(LatestRelease release) {
        return findApkAsset(release, BuildConfig.FLAVOR);
    }

    @Nullable
    static Assets findApkAsset(LatestRelease release, String flavor) {
        if (release == null || release.getAssets() == null) return null;
        java.util.List<Assets> apks = new java.util.ArrayList<>();
        for (Assets asset : release.getAssets()) {
            if (asset != null && asset.getName() != null
                    && asset.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")
                    && asset.getBrowserDownloadUrl() != null) {
                apks.add(asset);
            }
        }
        if (apks.isEmpty()) return null;
        if (apks.size() == 1) return apks.get(0);
        boolean degoogled = "degoogled".equalsIgnoreCase(flavor);
        for (Assets asset : apks) {
            String lower = asset.getName().toLowerCase(java.util.Locale.ROOT);
            if (degoogled && lower.contains("degoogled")) return asset;
            if (!degoogled && lower.contains("tempus") && !lower.contains("degoogled")) return asset;
        }
        for (Assets asset : apks) {
            String lower = asset.getName().toLowerCase(java.util.Locale.ROOT);
            if (degoogled && !lower.contains("tempus")) return asset;
            if (!degoogled && !lower.contains("degoogled")) return asset;
        }
        return apks.get(0);
    }

    /**
     * Compares dotted numeric versions such as "v1.2.0" and "1.10.3"; a leading "v" and any
     * "-suffix" are ignored, missing parts count as 0. Returns 0 when either side can't be parsed.
     */
    static int compareVersions(String a, String b) {
        try {
            String[] left = clean(a).split("\\.");
            String[] right = clean(b).split("\\.");

            for (int i = 0; i < Math.max(left.length, right.length); i++) {
                int leftPart = i < left.length ? Integer.parseInt(left[i]) : 0;
                int rightPart = i < right.length ? Integer.parseInt(right[i]) : 0;
                if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
            }
        } catch (NumberFormatException exception) {
            return 0;
        }

        return 0;
    }

    private static String clean(String version) {
        return version.replaceAll("^\\D+", "").replaceAll("-.*$", "");
    }
}
