package org.artifactory.addon;

import org.artifactory.api.config.VersionInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;


public class VersionResultCachedTest {

    private final String ART_VER = "7.73";
    private final String ART_REVISION = "70";

    private final VersionResultCached cached = new VersionResultCached();

    @Test
    void testUpdateVersionResult() {
        VersionResult versionResult = prepareVersionResult(ART_VER, ART_REVISION);
        Integer key = prepareKey(ART_VER, ART_REVISION);

        cached.updateVersionResult(key, versionResult);
        Assertions.assertNotNull(cached.getVersionResult(key));
    }

    private Integer prepareKey(String ver, String rev) {
        return (new VersionInfo(ver, rev)).hashCode();
    }

    private VersionResult prepareVersionResult(String ver, String rev) {
        return new VersionResult(ver, rev, List.of(), null, Map.of());
    }


    @Test
    void testClearBeforeReload() {
        cached.updateVersionResult(prepareKey(ART_VER, ART_REVISION), prepareVersionResult(ART_VER, ART_REVISION));
        Assertions.assertTrue(cached.size() != 0);
        cached.clearBeforeReload();
        Assertions.assertEquals(0, cached.size());
    }

    @Test
    void testSize() {
        cached.clearBeforeReload();
        cached.updateVersionResult(prepareKey(ART_VER, ART_REVISION), prepareVersionResult(ART_VER, ART_REVISION));
        Assertions.assertEquals(1, cached.size());

        String ART_REVISION_2 = "71";
        cached.updateVersionResult(prepareKey(ART_VER, ART_REVISION_2), prepareVersionResult(ART_VER, ART_REVISION_2));
        Assertions.assertEquals(2, cached.size());
    }

    @Test
    void testGetVersionResult() {
        VersionResult versionResult = prepareVersionResult(ART_VER, ART_REVISION);
        Integer key = prepareKey(ART_VER, ART_REVISION);
        cached.updateVersionResult(key, versionResult);
        Assertions.assertEquals(cached.getVersionResult(key), versionResult);
    }
}
