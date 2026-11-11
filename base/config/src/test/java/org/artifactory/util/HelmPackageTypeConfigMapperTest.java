package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.HelmConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.util.BasePackageTypeConfigMapper.MODEL_VERSION;
import static org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED;
import static org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF;
import static org.artifactory.util.HelmPackageTypeConfigMapper.CHARTS_BASE_URL;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.artifactory.util.HelmPackageTypeConfigMapper.USE_NAMESPACES;

/**
 * @author Tamar Rosen
 */
class HelmPackageTypeConfigMapperTest {

    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(MODEL_VERSION, "1",
                        EXTERNAL_DEPENDENCIES_ENABLED, "true",
                        EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF, "remote-repo",
                        EXTERNAL_DEPENDENCIES_PATTERNS, "[\"1\",\"2\",\"3\"]",
                        CHARTS_BASE_URL, "base-url",
                        USE_NAMESPACES, "true",
                        FORCE_NON_DUPLICATE_CHART,"true",
                        FORCE_METADATA_NAME_VERSION,"true");

        HelmConfiguration helmConfiguration = HelmPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertTrue(helmConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(1, helmConfiguration.getModelVersion());
        Assertions.assertEquals("remote-repo", helmConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertEquals("1", helmConfiguration.getExternalDependencies().getPatterns().get(0));
        Assertions.assertEquals("2", helmConfiguration.getExternalDependencies().getPatterns().get(1));
        Assertions.assertEquals("base-url", helmConfiguration.getChartsBaseUrl());
        Assertions.assertTrue(helmConfiguration.isUseNamespaces());
        Assertions.assertTrue(helmConfiguration.isForceMetadataNameVersion());
        Assertions.assertTrue(helmConfiguration.isForceNonDuplicateChart());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(MODEL_VERSION, null);
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_ENABLED, null);
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF, null);
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_PATTERNS, null);
        packageTypeConfig.put(CHARTS_BASE_URL, null);
        packageTypeConfig.put(USE_NAMESPACES, null);
        packageTypeConfig.put(FORCE_METADATA_NAME_VERSION, null);
        packageTypeConfig.put(FORCE_NON_DUPLICATE_CHART, null);
        HelmConfiguration helmConfiguration = HelmPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertFalse(helmConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(0L, helmConfiguration.getModelVersion());
        Assertions.assertNull(helmConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertNull(helmConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertNull(helmConfiguration.getChartsBaseUrl());
        Assertions.assertFalse(helmConfiguration.isUseNamespaces());
        Assertions.assertFalse(helmConfiguration.isForceMetadataNameVersion());
        Assertions.assertFalse(helmConfiguration.isForceNonDuplicateChart());
    }

    @Test
    void testEmptyListMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, "[]");

        HelmConfiguration helmConfiguration = HelmPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertNotNull(helmConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertTrue(helmConfiguration.getExternalDependencies().getPatterns().isEmpty());
    }
}
