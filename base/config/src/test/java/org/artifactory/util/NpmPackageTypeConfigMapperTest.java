package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.NpmConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Mor Merhav
 */
class NpmPackageTypeConfigMapperTest {

    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(NpmPackageTypeConfigMapper.MODEL_VERSION, "1",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, "true",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF,
                        "remote-repo",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS,
                        "[\"1\",\"2\",\"3\"]");

        NpmConfiguration npmConfiguration = NpmPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertTrue(npmConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(1, npmConfiguration.getModelVersion());
        Assertions.assertEquals("remote-repo", npmConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertEquals("1", npmConfiguration.getExternalDependencies().getPatterns().get(0));
        Assertions.assertEquals("2", npmConfiguration.getExternalDependencies().getPatterns().get(1));
        Assertions.assertEquals("3", npmConfiguration.getExternalDependencies().getPatterns().get(2));
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(NpmPackageTypeConfigMapper.MODEL_VERSION, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, null);

        NpmConfiguration npmConfiguration = NpmPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertFalse(npmConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(0L, npmConfiguration.getModelVersion());
        Assertions.assertNull(npmConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertNull(npmConfiguration.getExternalDependencies().getPatterns());
    }

    @Test
    void testEmptyListMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, "[]");

        NpmConfiguration npmConfiguration = NpmPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertNotNull(npmConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertTrue(npmConfiguration.getExternalDependencies().getPatterns().isEmpty());
    }
}
