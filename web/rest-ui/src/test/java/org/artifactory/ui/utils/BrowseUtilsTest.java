package org.artifactory.ui.utils;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.lifecycleManifestVisible;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class BrowseUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    private static final Pattern MACHINELEARNING_TMP_REGEX_PATTERN = Pattern.compile("^([^/]+/){2,3}tmp(/.*)?$");

    @Test
    void testFilterItemsIfNeeded() {
        List<ItemInfo> items = List.of(
                new FileInfoImpl(RepoPathFactory.create("repo", "")),
                new FileInfoImpl(RepoPathFactory.create("repo", ".git")),
                new FileInfoImpl(RepoPathFactory.create("repo", ".git/yo")),
                new FileInfoImpl(RepoPathFactory.create("repo", ".got/yo")),
                new FileInfoImpl(RepoPathFactory.create("repo", "git/yo")),
                new FileInfoImpl(RepoPathFactory.create("repo", "git")),
                new FileInfoImpl(RepoPathFactory.create("repo", "a.sha1")),
                new FileInfoImpl(RepoPathFactory.create("repo", "a.sha256")),
                new FileInfoImpl(RepoPathFactory.create("repo", "a.md5")),
                new FileInfoImpl(RepoPathFactory.create("repo", "models/namespace/mymodel/2024-10-28-10-11-41-007")),
                new FileInfoImpl(RepoPathFactory.create("repo", "models/namespace/mymodel/tmp")),
                new FileInfoImpl(RepoPathFactory.create("repo", "models/namespace/mymodel/tmpkind")),
                new FileInfoImpl(RepoPathFactory.create("repo", "models/mymodel/tmp")),
                new FileInfoImpl(RepoPathFactory.create("repo", "models/tmp")),
                new FileInfoImpl(RepoPathFactory.create("repo",
                        "models/namespace/mymodel/someversion/model/tmp")),
                new FileInfoImpl(RepoPathFactory.create(DEFAULT_RBV2_REPO,
                        "some-RB-name/some-RB-version/release-bundle.json.evd")),
                new FileInfoImpl(RepoPathFactory.create(DEFAULT_RBV2_REPO,
                        "some-RB-name/some-RB-version/promotion-1234567890.json.evd"))
        );
        setStringSystemProperty(lifecycleManifestVisible, String.valueOf(false));
        //test list function
        List<ItemInfo> noSpecialPackageResults = BrowseUtils.filterItemsIfNeeded(items, null, false);
        assertEquals(14, noSpecialPackageResults.size());

        List<ItemInfo> cargoResults = BrowseUtils.filterItemsIfNeeded(items, RepoType.Cargo.getType(), false);
        assertEquals(12, cargoResults.size());
        cargoResults.forEach(item -> Assertions.assertFalse(item.getRelPath().startsWith(".git")));

        List<ItemInfo> mlResults =
                BrowseUtils.filterItemsIfNeeded(items, RepoType.MachineLearning.getDisplayName(), false);
        assertEquals(12, mlResults.size());
        mlResults.forEach(
                item -> Assertions.assertFalse(MACHINELEARNING_TMP_REGEX_PATTERN.matcher(item.getRelPath()).matches()));
        mlResults = BrowseUtils.filterItemsIfNeeded(items, RepoType.MachineLearning.getDisplayName(), true);
        assertEquals(14, mlResults.size());

        //test stream function
        Stream<ItemInfo> noSpecialPackageResultsStream =
                BrowseUtils.filterStreamItemsIfNeeded(items.stream(), null, false);
        assertEquals(12, noSpecialPackageResultsStream.toList().size());

        Stream<ItemInfo> cargoResultsStream =
                BrowseUtils.filterStreamItemsIfNeeded(items.stream(), RepoType.Cargo.getType(), false);
        cargoResults = cargoResultsStream.toList();
        assertEquals(10, cargoResults.size());
        cargoResults.forEach(item -> Assertions.assertFalse(item.getRelPath().startsWith(".git")));

        Stream<ItemInfo> mlResultsStream =
                BrowseUtils.filterStreamItemsIfNeeded(items.stream(), RepoType.MachineLearning.getDisplayName(), false);
        mlResults = mlResultsStream.toList();
        assertEquals(10, mlResults.size());
        mlResults.forEach(
                item -> Assertions.assertFalse(MACHINELEARNING_TMP_REGEX_PATTERN.matcher(item.getRelPath()).matches()));
        mlResultsStream =
                BrowseUtils.filterStreamItemsIfNeeded(items.stream(), RepoType.MachineLearning.getDisplayName(), true);
        mlResults = mlResultsStream.toList();
        assertEquals(12, mlResults.size());
    }

    @Test
    void testFilterInternalMetadata() {
        List<ItemInfo> items = List.of(
                new FileInfoImpl(RepoPathFactory.create("repo", "")),
                new FileInfoImpl(RepoPathFactory.create("repo", ".jfrog/repository.catalog")),
                new FileInfoImpl(RepoPathFactory.create("repo",
                        "sha256:6e2b7e3d21e385681a38a8d66a40213462762e7e14c6e97b7701cee8b21e32d6")),
                new FileInfoImpl(RepoPathFactory.create("repo",
                        "sha256:93361007a78c4b38d0c868599685aa8a08fb3c27067933243c719cb71b514184")),
                new FileInfoImpl(RepoPathFactory.create("repo", "manifest.json")),
                new FileInfoImpl(RepoPathFactory.create("repo", "a.sha1")),
                new FileInfoImpl(RepoPathFactory.create("repo", "a.sha256")),
                new FileInfoImpl(RepoPathFactory.create("repo", "a.md5"))
        );
        setStringSystemProperty(lifecycleManifestVisible, String.valueOf(false));
        //test list function
        List<ItemInfo> dockerResultsWithoutManagePermission = BrowseUtils.filterItemsIfNeeded(items, null, false);
        assertEquals(4, dockerResultsWithoutManagePermission.size());

        List<ItemInfo> dockerResultsWithManagePermission = BrowseUtils.filterItemsIfNeeded(items, null, true);
        assertEquals(5, dockerResultsWithManagePermission.size());

        //test stream function
        Stream<ItemInfo> dockerResultsStreamWithoutManagePermission =
                BrowseUtils.filterStreamItemsIfNeeded(items.stream(), null, false);
        assertEquals(4, dockerResultsStreamWithoutManagePermission.toList().size());

        Stream<ItemInfo> dockerResultsStreamWithManagePermission =
                BrowseUtils.filterStreamItemsIfNeeded(items.stream(), null, true);
        assertEquals(5, dockerResultsStreamWithManagePermission.toList().size());
    }

    @Test
    void passesCommonFilters_shouldNotThrowException_whenRelativePathIsNull() {
        boolean cargoResult = BrowseUtils.passesCommonFilters("file.txt", null, RepoType.Cargo.getType(), false);
        Assertions.assertTrue(cargoResult);

        boolean mlResult = BrowseUtils.passesCommonFilters("file.txt", null,
                RepoType.MachineLearning.getDisplayName(), false);
        Assertions.assertTrue(mlResult);

        boolean genericResult = BrowseUtils.passesCommonFilters("file.txt", null, null, false);
        Assertions.assertTrue(genericResult);
    }

    @Test
    void filterStreamItemsIfNeeded_filtersEvidenceItemsWhenNavigationDisabled() {
        List<ItemInfo> items = List.of(
                new FileInfoImpl(RepoPathFactory.create("repo", "regular.txt")),
                new FileInfoImpl(RepoPathFactory.create("repo", ".evidence/artifact.evd")),
                new FileInfoImpl(RepoPathFactory.create("repo", ".evidence/sub/nested.evd"))
        );
        try (MockedStatic<ArtifactoryHome> mocked = mockStatic(ArtifactoryHome.class, Answers.CALLS_REAL_METHODS)) {
            mocked.when(() -> ArtifactoryHome.getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false))
                    .thenReturn(false);

            List<ItemInfo> result = BrowseUtils.filterStreamItemsIfNeeded(items.stream(), null, false).toList();

            assertEquals(1, result.size());
            assertEquals("regular.txt", result.get(0).getName());
        }
    }

    @Test
    void filterStreamItemsIfNeeded_keepsEvidenceItemsWhenNavigationEnabled() {
        List<ItemInfo> items = List.of(
                new FileInfoImpl(RepoPathFactory.create("repo", "regular.txt")),
                new FileInfoImpl(RepoPathFactory.create("repo", ".evidence/artifact.evd")),
                new FileInfoImpl(RepoPathFactory.create("repo", ".evidence/sub/nested.evd"))
        );
        try (MockedStatic<ArtifactoryHome> mocked = mockStatic(ArtifactoryHome.class, Answers.CALLS_REAL_METHODS)) {
            mocked.when(() -> ArtifactoryHome.getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false))
                    .thenReturn(true);

            List<ItemInfo> result = BrowseUtils.filterStreamItemsIfNeeded(items.stream(), null, false).toList();

            assertEquals(3, result.size());
        }
    }

}