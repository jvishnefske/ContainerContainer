package org.artifactory.util;

import com.google.common.collect.Maps;
import org.artifactory.repo.packagetype.configuration.GoConfiguration;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Tamar Rosen
 */
class GoPackageTypeConfigMapperTest {
    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(GoPackageTypeConfigMapper.MODEL_VERSION, "1",
                        GoPackageTypeConfigMapper.GITLAB_RESOLVE_SUBGROUPS, "true",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, "true",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF,
                        "remote-repo",
                        ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS,
                        "[\"1\",\"2\",\"3\"]",
                        VcsPackageTypeConfigMapper.VCS_GIT_PROVIDER, "BITBUCKET",
                        VcsPackageTypeConfigMapper.VCS_GIT_DOWNLOAD_URL, "git-download-url.com",
                        VcsPackageTypeConfigMapper.VCS_TYPE, "GIT"
                );

        GoConfiguration goConfiguration = GoPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        Assertions.assertTrue(goConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(1, goConfiguration.getModelVersion());
        Assertions.assertTrue(goConfiguration.isGitLabResolveSubgroups());
        Assertions.assertEquals("remote-repo", goConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertEquals("1", goConfiguration.getExternalDependencies().getPatterns().get(0));
        Assertions.assertEquals("2", goConfiguration.getExternalDependencies().getPatterns().get(1));
        Assertions.assertEquals("3", goConfiguration.getExternalDependencies().getPatterns().get(2));
        Assertions.assertEquals(VcsGitProvider.BITBUCKET, goConfiguration.getVcs().getGit().getProvider());
        Assertions.assertEquals("git-download-url.com", goConfiguration.getVcs().getGit().getDownloadUrl());
        Assertions.assertEquals(VcsType.GIT, goConfiguration.getVcs().getType());
    }

    @Test
    void testAllNullsMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(GoPackageTypeConfigMapper.MODEL_VERSION, null);
        packageTypeConfig.put(GoPackageTypeConfigMapper.GITLAB_RESOLVE_SUBGROUPS, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF, null);
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, null);
        packageTypeConfig.put(VcsPackageTypeConfigMapper.VCS_GIT_PROVIDER, null);
        packageTypeConfig.put(VcsPackageTypeConfigMapper.VCS_GIT_DOWNLOAD_URL, null);
        packageTypeConfig.put(VcsPackageTypeConfigMapper.VCS_TYPE, null);

        GoConfiguration goConfiguration = GoPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertFalse(goConfiguration.getExternalDependencies().isEnabled());
        Assertions.assertEquals(0L, goConfiguration.getModelVersion());
        Assertions.assertFalse(goConfiguration.isGitLabResolveSubgroups());
        Assertions.assertNull(goConfiguration.getExternalDependencies().getRemoteRepoRef());
        Assertions.assertNull(goConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertEquals(VcsGitProvider.GITHUB, goConfiguration.getVcs().getGit().getProvider());
        Assertions.assertNull(goConfiguration.getVcs().getGit().getDownloadUrl());
        Assertions.assertEquals(VcsType.GIT, goConfiguration.getVcs().getType());
    }

    @Test
    void testEmptyListMapping() {
        Map<String, String> packageTypeConfig = Maps.newHashMap();
        packageTypeConfig.put(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS, "[]");

        GoConfiguration goConfiguration = GoPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertNotNull(goConfiguration.getExternalDependencies().getPatterns());
        Assertions.assertTrue(goConfiguration.getExternalDependencies().getPatterns().isEmpty());
    }
}