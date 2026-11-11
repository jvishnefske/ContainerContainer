package org.artifactory.util.bundle;

import jakarta.annotation.Nonnull;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.artifactory.common.ConstantValues.lifecycleManifestVisible;
import static org.artifactory.util.bundles.BundleConfigurationUtil.APP_VERSIONS_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_DISTRIBUTION_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.extractProjectKey;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isAppVersionsDefaultRepoKey;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isHiddenReleaseBundlePath;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundleNavigationDisabled;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2DefaultRepoKey;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2DistributionRepoKey;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2RepoKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BundleConfigurationUtilTest {

    @BeforeEach
    void setup() {
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(properties.getBooleanProperty(lifecycleManifestVisible)).thenReturn(false);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(properties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    void isReleaseBundlesV2DefaultRepoKey_Success() {
        assertTrue(isReleaseBundlesV2DefaultRepoKey(DEFAULT_RBV2_REPO));
        assertFalse(isReleaseBundlesV2DefaultRepoKey("project-" + DEFAULT_RBV2_REPO));
    }

    @Test
    void isReleaseBundlesV2DistributionRepoKey_Success() {
        assertFalse(isReleaseBundlesV2DistributionRepoKey(null));
        // JFDS distribution repo (-jfds suffix)
        assertTrue(isReleaseBundlesV2DistributionRepoKey(DEFAULT_RBV2_DISTRIBUTION_REPO));
        // Standard release-bundles-v2 repo is NOT a distribution repo
        assertFalse(isReleaseBundlesV2DistributionRepoKey(DEFAULT_RBV2_REPO));
        // Unrelated repo
        assertFalse(isReleaseBundlesV2DistributionRepoKey("generic-local"));
    }

    @Test
    void isReleaseBundlesV2RepoKey_Success() {
        assertFalse(isReleaseBundlesV2RepoKey(null));
        assertTrue(isReleaseBundlesV2RepoKey(DEFAULT_RBV2_REPO));
        assertTrue(isReleaseBundlesV2RepoKey("project-" + DEFAULT_RBV2_REPO));
        assertTrue(isReleaseBundlesV2RepoKey("project-" + APP_VERSIONS_REPO));
        // JFDS distribution repo matches via isReleaseBundlesV2DistributionRepoKey
        assertTrue(isReleaseBundlesV2RepoKey(DEFAULT_RBV2_DISTRIBUTION_REPO));
    }

    @Test
    void isAppVersionsDefaultRepoKey_Success() {
        assertFalse(isAppVersionsDefaultRepoKey(null));
        assertTrue(isAppVersionsDefaultRepoKey(APP_VERSIONS_REPO));
        assertFalse(isAppVersionsDefaultRepoKey("project-" + APP_VERSIONS_REPO));
        assertFalse(isAppVersionsDefaultRepoKey(DEFAULT_RBV2_REPO));
    }

    @Test
    void extractProjectKey_Success() {
        assertEquals("default", extractProjectKey(DEFAULT_RBV2_REPO));
        assertEquals("default", extractProjectKey(APP_VERSIONS_REPO));
        assertEquals("project", extractProjectKey("project-" + DEFAULT_RBV2_REPO));
        assertEquals("project", extractProjectKey("project-" + APP_VERSIONS_REPO));
        assertThrows(IllegalArgumentException.class, () -> extractProjectKey("invalid-repo-key"));
    }

    @Test
    void isHiddenReleaseBundlePath_Success() {
        String someFileNoExtension = "some-repo/some-dir/some-file";
        String someFileWithExtension = "some-repo/some-dir/some-file.json.evd";
        String bundleArtifactPath = "release-bundles-v2/some-RB-name/some-RB-version/artifacts/generic/file.zip";
        String bundleManifestPath = "release-bundles-v2/some-RB-name/some-RB-version/release-bundle.json.evd";
        String draftBundleManifestPath = "release-bundles-v2/some-RB-name/some-RB-version/release-bundle.json.draft";
        String promotionManifestPath = "release-bundles-v2/some-RB-name/some-RB-version/promotion-1234567890.json.evd";
        assertFalse(isHiddenReleaseBundlePath(null));
        assertFalse(isHiddenReleaseBundlePath(mockRepoPath(someFileNoExtension)));
        assertFalse(isHiddenReleaseBundlePath(mockRepoPath(someFileWithExtension)));
        assertFalse(isHiddenReleaseBundlePath(mockRepoPath(bundleArtifactPath)));
        assertTrue(isHiddenReleaseBundlePath(mockRepoPath(promotionManifestPath)));
        assertTrue(isHiddenReleaseBundlePath(mockRepoPath(bundleManifestPath)));
        assertTrue(isHiddenReleaseBundlePath(mockRepoPath(draftBundleManifestPath)));
    }

    @Test
    void isReleaseBundleNavigationDisabled_Success() {
        assertTrue(isReleaseBundleNavigationDisabled());
    }

    @Nonnull
    private RepoPath mockRepoPath(@Nonnull String repoPathRaw) {
        String[] parts = repoPathRaw.split("/", 2);
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn(parts[0]);
        when(repoPath.getPath()).thenReturn(parts[1]);
        return repoPath;
    }
}