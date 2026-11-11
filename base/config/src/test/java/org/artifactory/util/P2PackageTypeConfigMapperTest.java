package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.P2Configuration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.artifactory.util.BasePackageTypeConfigMapper.MODEL_VERSION;
import static org.artifactory.util.P2PackageTypeConfigMapper.FORCE_P2_AUTHENTICATION;
import static org.artifactory.util.P2PackageTypeConfigMapper.P2_ORIGINAL_URL;
import static org.artifactory.util.P2PackageTypeConfigMapper.URLS;

/**
 * @author Tamar Rosen
 */
class P2PackageTypeConfigMapperTest {
    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(MODEL_VERSION, "1",
                        URLS, "[\"https://url-1.com\",\"https://url-2.com\"]",
                        P2_ORIGINAL_URL, "https://p2-original-url.com",
                        FORCE_P2_AUTHENTICATION, "true");

        P2Configuration p2Configuration = P2PackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(1L, p2Configuration.getModelVersion());
        Assertions.assertEquals(List.of("https://url-1.com", "https://url-2.com"), p2Configuration.getUrls());
        Assertions.assertEquals("https://p2-original-url.com", p2Configuration.getP2OriginalUrl());
        Assertions.assertTrue(p2Configuration.isForceP2Authentication());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(MODEL_VERSION, null);
        packageTypeConfig.put(URLS, null);
        packageTypeConfig.put(P2_ORIGINAL_URL, null);

        P2Configuration p2Configuration = P2PackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(0, p2Configuration.getModelVersion());
        Assertions.assertTrue(p2Configuration.getUrls().isEmpty());
        Assertions.assertNull(p2Configuration.getP2OriginalUrl());
        Assertions.assertFalse(p2Configuration.isForceP2Authentication());
    }
}