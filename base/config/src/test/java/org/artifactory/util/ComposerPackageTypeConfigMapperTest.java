package org.artifactory.util;

import org.artifactory.repo.packagetype.configuration.ComposerConfiguration;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Mor Merhav
 */
class ComposerPackageTypeConfigMapperTest {

    @Test
    void testStandardMapping() {
        Map<String, String> packageTypeConfig =
                Map.of(ComposerPackageTypeConfigMapper.MODEL_VERSION, "1",
                        ComposerPackageTypeConfigMapper.COMPOSER_REGISTRY_URL, "https://composer-registry-url/",
                        ComposerPackageTypeConfigMapper.ENABLE_COMPOSER_V1_INDEXING, "true",
                        VcsPackageTypeConfigMapper.VCS_GIT_PROVIDER, "BITBUCKET",
                        VcsPackageTypeConfigMapper.VCS_GIT_DOWNLOAD_URL, "https://vcs-git-download-url/"
                );

        ComposerConfiguration composerConfiguration = ComposerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);

        Assertions.assertTrue(composerConfiguration.isEnableComposerV1Indexing());
        Assertions.assertEquals(1, composerConfiguration.getModelVersion());
        Assertions.assertEquals("https://composer-registry-url/", composerConfiguration.getComposerRegistryUrl());
        Assertions.assertEquals(VcsGitProvider.BITBUCKET, composerConfiguration.getVcs().getGit().getProvider());
        Assertions.assertEquals("https://vcs-git-download-url/",
                composerConfiguration.getVcs().getGit().getDownloadUrl());
    }

    @Test
    void testAllNullsMapping() {
        ComposerConfiguration composerConfiguration = ComposerPackageTypeConfigMapper.INSTANCE.map(Map.of());

        Assertions.assertFalse(composerConfiguration.isEnableComposerV1Indexing());
        Assertions.assertEquals(0L, composerConfiguration.getModelVersion());
        Assertions.assertEquals("https://packagist.org", composerConfiguration.getComposerRegistryUrl());
        Assertions.assertEquals(VcsGitProvider.GITHUB, composerConfiguration.getVcs().getGit().getProvider());
        Assertions.assertNull(composerConfiguration.getVcs().getGit().getDownloadUrl());
    }
}