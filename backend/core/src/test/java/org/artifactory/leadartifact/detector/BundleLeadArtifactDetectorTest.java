package org.artifactory.leadartifact.detector;

import lombok.NonNull;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.repo.RepoPathFactory.create;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BundleLeadArtifactDetectorTest {

    private final BundleLeadArtifactDetector detector = new BundleLeadArtifactDetector();

    @ParameterizedTest
    @MethodSource("isLeadArtifact_Provider")
    void isLeadArtifact(RepoPath repoPath, boolean expectedResult) {
        assertEquals(expectedResult, detector.isLeadArtifact(repoPath));
    }

    @Test
    void getLeadArtifactExtensions() {
        assertEquals(Set.of("evd"), detector.getLeadArtifactExtensions());
    }

    @Test
    void getRepoType() {
        assertEquals(ReleaseBundles, detector.getRepoType());
    }

    @NonNull
    private static Stream<Arguments> isLeadArtifact_Provider() {
        RepoPath validManifestPath = create(
                "release-bundles-v2/RB-A/1.1/release-bundle.json.evd");
        RepoPath validManifestPathProject = create(
                "phoenix-release-bundles-v2/RB-A/1.1/release-bundle.json.evd");
        RepoPath validPromotionManifestPath = create(
                "release-bundles-v2/RB-A/1.1/extensions/promotion-1699962846623.json.evd");
        RepoPath validPromotionManifestPathProject = create(
                "phoenix-release-bundles-v2/RB-A/1.1/extensions/promotion-1699962846623.json.evd");
        RepoPath validDistributionManifestPath = create(
                "release-bundles-v2/RB-A/1.1/extensions/distribution-1699962846623.json.evd");
        RepoPath validDistributionManifestPathProject = create(
                "phoenix-release-bundles-v2/RB-A/1.1/extensions/distribution-1699962846623.json.evd");
        RepoPath validArtifactPath = create(
                "release-bundles-v2/RB-A/1.1/artifacts/docker/commons/1.0.0/manifest.json");
        RepoPath validExtensionsPath = create(
                "release-bundles-v2/RB-A/1.1/extensions/dir/file.txt");
        RepoPath invalidManifestPath = create(
                "releaseBundles-v2/RB-A/1.1/release-bundle.json.evd");
        return Stream.of(
                Arguments.of(validManifestPath, true),
                Arguments.of(validManifestPathProject, true),
                Arguments.of(validPromotionManifestPath, true),
                Arguments.of(validPromotionManifestPathProject, true),
                Arguments.of(validDistributionManifestPath, true),
                Arguments.of(validDistributionManifestPathProject, true),
                Arguments.of(validArtifactPath, false),
                Arguments.of(validExtensionsPath, false),
                Arguments.of(invalidManifestPath, false)
        );
    }
}