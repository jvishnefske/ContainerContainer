package org.artifactory.repo.service;

import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.Test;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_REPO_LAYOUT;
import static org.artifactory.repo.config.RepoConfigDefaultValues.EXAMPLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.BILLING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_DISTRIBUTION_REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryConfigServiceHelperTest {

    @Test
    void testGenerateExampleRepoLocalConfig() {
        LocalRepoConfig localRepoConfig = RepositoryConfigServiceHelper.generateExampleRepoLocalConfig();
        assertEquals(EXAMPLE_REPO_KEY, localRepoConfig.getKey());
        assertEquals(RepoDetailsType.LOCAL, localRepoConfig.getRepoType());
        assertEquals(DEFAULT_REPO_LAYOUT, localRepoConfig.getBaseConfig().getRepoLayoutRef());
        assertTrue(localRepoConfig.getRepoTypeConfig().isHandleSnapshots());
        assertTrue(localRepoConfig.getRepoTypeConfig().isHandleReleases());
    }

    @Test
    void testIsHiddenRepo() {
        assertTrue(RepositoryConfigServiceHelper.isHiddenRepo(BILLING_REPO_KEY));
        assertTrue(RepositoryConfigServiceHelper.isHiddenRepo(IN_TRANSIT_REPO_KEY));
        assertTrue(RepositoryConfigServiceHelper.isHiddenRepo(DEFAULT_RBV2_DISTRIBUTION_REPO));
        assertTrue(RepositoryConfigServiceHelper.isHiddenRepo(
                "cool-" + DEFAULT_RBV2_DISTRIBUTION_REPO));
        assertTrue(RepositoryConfigServiceHelper.isHiddenRepo(FULL_SYNC_REPO_KEY));
        assertFalse(RepositoryConfigServiceHelper.isHiddenRepo("cool-repo"));
    }
}
