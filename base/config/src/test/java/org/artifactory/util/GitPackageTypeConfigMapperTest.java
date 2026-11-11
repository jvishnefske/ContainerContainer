package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.GitConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Tamar Rosen
 */
class GitPackageTypeConfigMapperTest {
    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(GitPackageTypeConfigMapper.MODEL_VERSION, "1",
                        GitPackageTypeConfigMapper.GIT_REGISTRY_URL, "git-registry-url.com");

        GitConfiguration gitConfiguration = GitPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(1L, gitConfiguration.getModelVersion());
        Assertions.assertEquals("git-registry-url.com", gitConfiguration.getRegistryUrl());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(GitPackageTypeConfigMapper.MODEL_VERSION, null);
        packageTypeConfig.put(GitPackageTypeConfigMapper.GIT_REGISTRY_URL, null);

        GitConfiguration gitConfiguration = GitPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertEquals(0L, gitConfiguration.getModelVersion());
        Assertions.assertEquals("https://index.crates.io", gitConfiguration.getRegistryUrl());
    }
}