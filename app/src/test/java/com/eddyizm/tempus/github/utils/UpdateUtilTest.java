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
}
