package org.artifactory.util;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.NuGetConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Mor Merhav
 */
class NuGetPackageTypeConfigMapperTest {

    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(NuGetPackageTypeConfigMapper.MODEL_VERSION, "1",
                        NuGetPackageTypeConfigMapper.FORCE_NUGET_AUTHENTICATION, "true",
                        NuGetPackageTypeConfigMapper.FEED_CONTEXT_PATH, "somePath",
                        NuGetPackageTypeConfigMapper.DOWNLOAD_CONTEXT_PATH, "downloadContextPath",
                        NuGetPackageTypeConfigMapper.SYMBOL_SERVER_URL, "someServerUrl",
                        NuGetPackageTypeConfigMapper.V3_FEED_URL, "someFeedUrl",
                        "UnexpectedKey", StringUtils.EMPTY,
                        "enableNormalizedVersion", "true",
                        "UnexpectedKey2", "UnexpectedValue");

        NuGetConfiguration nuGetConfiguration = NuGetPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(1L, nuGetConfiguration.getModelVersion());
        Assertions.assertTrue(nuGetConfiguration.isEnableNormalizedVersion());
        Assertions.assertTrue(nuGetConfiguration.isForceNugetAuthentication());
        Assertions.assertEquals("somePath", nuGetConfiguration.getFeedContextPath());
        Assertions.assertEquals("downloadContextPath", nuGetConfiguration.getDownloadContextPath());
        Assertions.assertEquals("someServerUrl", nuGetConfiguration.getSymbolServerUrl());
        Assertions.assertEquals("someFeedUrl", nuGetConfiguration.getV3FeedUrl());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(NuGetPackageTypeConfigMapper.MODEL_VERSION, null);
        packageTypeConfig.put(NuGetPackageTypeConfigMapper.FORCE_NUGET_AUTHENTICATION, null);
        packageTypeConfig.put(NuGetPackageTypeConfigMapper.FEED_CONTEXT_PATH, null);
        packageTypeConfig.put(NuGetPackageTypeConfigMapper.DOWNLOAD_CONTEXT_PATH, null);
        packageTypeConfig.put(NuGetPackageTypeConfigMapper.SYMBOL_SERVER_URL, null);
        packageTypeConfig.put(NuGetPackageTypeConfigMapper.V3_FEED_URL, null);

        NuGetConfiguration nuGetConfiguration = NuGetPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(0L, nuGetConfiguration.getModelVersion());
        Assertions.assertFalse(nuGetConfiguration.isEnableNormalizedVersion());
        Assertions.assertFalse(nuGetConfiguration.isForceNugetAuthentication());
        Assertions.assertNull(nuGetConfiguration.getFeedContextPath());
        Assertions.assertNull(nuGetConfiguration.getDownloadContextPath());
        Assertions.assertNull(nuGetConfiguration.getSymbolServerUrl());
        Assertions.assertNull(nuGetConfiguration.getV3FeedUrl());
    }

    @Test
    void testSomeFieldsMissing() {
        Map<String, String> packageTypeConfig =
                Map.of(NuGetPackageTypeConfigMapper.MODEL_VERSION, "1",
                        NuGetPackageTypeConfigMapper.DOWNLOAD_CONTEXT_PATH, "downloadContextPath",
                        NuGetPackageTypeConfigMapper.SYMBOL_SERVER_URL, "someServerUrl",
                        NuGetPackageTypeConfigMapper.V3_FEED_URL, "someFeedUrl");

        NuGetConfiguration nuGetConfiguration = NuGetPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(1L, nuGetConfiguration.getModelVersion());
        Assertions.assertFalse(nuGetConfiguration.isForceNugetAuthentication());
        Assertions.assertNull(nuGetConfiguration.getFeedContextPath());
        Assertions.assertEquals("downloadContextPath", nuGetConfiguration.getDownloadContextPath());
        Assertions.assertEquals("someServerUrl", nuGetConfiguration.getSymbolServerUrl());
        Assertions.assertEquals("someFeedUrl", nuGetConfiguration.getV3FeedUrl());
    }
}