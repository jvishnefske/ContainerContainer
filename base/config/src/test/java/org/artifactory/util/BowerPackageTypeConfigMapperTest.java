package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.BowerConfiguration;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Mor Merhav
 */
class BowerPackageTypeConfigMapperTest {

    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(BowerPackageTypeConfigMapper.MODEL_VERSION, "1",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, "true",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF,
                        "remote-repo",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS,
                        "[\"1\",\"2\",\"3\"]",
                        BowerPackageTypeConfigMapper.BOWER_REGISTRY_URL, "bower-url.com",
                        "vcsGitProvider", "GITHUB",
                        "vcsGitDownloadUrl", "git-download-url.com",
                        "vcsType", "GIT"
                );

        BowerConfiguration bowerConfiguration = BowerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertTrue(bowerConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(1, bowerConfiguration.getModelVersion());
        Assertions.assertEquals("remote-repo", bowerConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertEquals("1", bowerConfiguration.getExternalDependencies().getPatterns().get(0));
        Assertions.assertEquals("2", bowerConfiguration.getExternalDependencies().getPatterns().get(1));
        Assertions.assertEquals("3", bowerConfiguration.getExternalDependencies().getPatterns().get(2));
        Assertions.assertEquals("bower-url.com", bowerConfiguration.getBowerRegistryUrl());
        Assertions.assertEquals(VcsGitProvider.GITHUB, bowerConfiguration.getVcs().getGit().getProvider());
        Assertions.assertEquals("git-download-url.com", bowerConfiguration.getVcs().getGit().getDownloadUrl());
        Assertions.assertEquals(VcsType.GIT, bowerConfiguration.getVcs().getType());

    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(BowerPackageTypeConfigMapper.MODEL_VERSION, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, null);
        packageTypeConfig.put(BowerPackageTypeConfigMapper.BOWER_REGISTRY_URL, null);

        BowerConfiguration bowerConfiguration = BowerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertFalse(bowerConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(0L, bowerConfiguration.getModelVersion());
        Assertions.assertNull(bowerConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertNull(bowerConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertEquals("https://registry.bower.io", bowerConfiguration.getBowerRegistryUrl());
        Assertions.assertEquals(VcsGitProvider.GITHUB, bowerConfiguration.getVcs().getGit().getProvider());
        Assertions.assertNull(bowerConfiguration.getVcs().getGit().getDownloadUrl());
        Assertions.assertEquals(VcsType.GIT, bowerConfiguration.getVcs().getType());
    }

    @Test
    void testEmptyListMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, "[]");

        BowerConfiguration bowerConfiguration = BowerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertNotNull(bowerConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertTrue(bowerConfiguration.getExternalDependencies().getPatterns().isEmpty());
    }
}