package org.artifactory.release.bundle;

import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.joda.time.DateTime;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * @author Eli Skoran 14/09/2020
 */

@Test
public class ReleaseBundleUtilsTest {
    private static final String BUNDLE_NAME = "bundle1";
    private static final String BUNDLE_VERSION = "version1";
    private static final String REPO_NAME = "release_bundles";
    private static final String RBV2_REPO_NAME = "release_bundles_v2";
    private static final Set<String> FOLDER_PATHS_DEPTH_2_SET = Set.of("repo1/path/", "repo2/another/");
    private static final Set<String> FOLDER_PATHS_DEPTH_3_SET = Set.of("repo1/path/to/", "repo2/another/path/");
    private static final Set<String> FOLDER_PATHS_DEPTH_4_SET = Set.of("repo2/another/path/to/");
    private static final Set<String> FOLDER_PATHS_DEPTH_2_3_4_SET =
            Stream.of(FOLDER_PATHS_DEPTH_2_SET, FOLDER_PATHS_DEPTH_3_SET, FOLDER_PATHS_DEPTH_4_SET)
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());

    @DataProvider
    public static Object[][] extractTargetRepoPathTestData() {
        // input <-> expected
        return new Object[][] {
                {"_intransit/rb1/v1/example-repo-local", "example-repo-local"},
                {"_intransit/rb1/v1/example-repo-local/folderA", "example-repo-local/folderA"},
                {"_intransit/rb1/v1/example-repo-local/folderA/zip.zip", "example-repo-local/folderA/zip.zip"},
        };
    }

    @DataProvider
    public static Object[][] nameVersionFromInTransitRepoPathTestData() {
        // input <-> expected
        return new Object[][] {
                {"_intransit/rb1/v1/example-repo-local", "rb1", "v1"},
                {"_intransit/rb1/v1/example-repo-local/folderA", "rb1", "v1"},
                {"_intransit/rb1/v1/example-repo-local/folderA/zip.zip", "rb1", "v1"},
                {"_intransit/differentName/v1/example-repo-local/folderA/zip.zip", "differentName", "v1"},
                {"_intransit/differentName/differentVersion/example-repo-local/folderA/zip.zip", "differentName",
                        "differentVersion"},
        };
    }

    @DataProvider
    public static Object[][] inTransitRepoPathTestData_invalid() {
        // input <-> expected
        return new Object[][] {
                {"docker-local/folderA/file.zip"},
                {"docker-local/folderA/folderB/file.zip"},
                {"docker-local"},
                {"_intransit"},
                {"_intransit/a"},
                {"_intransit/rb1/v1"},
        };
    }

    @Test
    public void testBuildExportFileRepoPath() {
        String expectedRepoPath =
                REPO_NAME + "/" + BUNDLE_NAME + "/" + BUNDLE_VERSION + "/" + BUNDLE_NAME + "-" + BUNDLE_VERSION +
                        ".zip";

        RepoPath repoPath = ReleaseBundleUtils.buildExportFileRepoPath(BUNDLE_NAME, BUNDLE_VERSION, REPO_NAME);

        assertEquals(repoPath.toPath(), expectedRepoPath);
    }

    @Test
    public void testConvertToArtifactsBundleModel() {
        DBArtifactsBundle artifactsBundle = new DBArtifactsBundle();
        artifactsBundle.setName(BUNDLE_NAME);
        artifactsBundle.setVersion(BUNDLE_VERSION);
        artifactsBundle.setDateCreated(DateTime.now());
        artifactsBundle.setId(1111);
        artifactsBundle.setSignature("1q2w3e4r5t6y7u8i");
        artifactsBundle.setStatus(BundleTransactionStatus.COMPLETE);
        artifactsBundle.setStoringRepo(REPO_NAME);
        artifactsBundle.setType(BundleType.SOURCE);

        ArtifactsBundleModel artifactsBundleModel = ReleaseBundleUtils.convertToArtifactsBundleModel(artifactsBundle);

        assertEquals(artifactsBundleModel.getStatus(), artifactsBundle.getStatus());
        assertEquals(artifactsBundleModel.getStoringRepo(), artifactsBundle.getStoringRepo());
        assertEquals(artifactsBundleModel.getId(), artifactsBundle.getId());
        assertEquals(artifactsBundleModel.getName(), artifactsBundle.getName());
        assertEquals(artifactsBundleModel.getVersion(), artifactsBundle.getVersion());
        assertEquals(artifactsBundleModel.getType(), artifactsBundle.getType());
    }

    @Test
    public void testGenerateTransactionPath() {
        String transactionPath = ReleaseBundleUtils.generateTransactionPath(BUNDLE_NAME, BUNDLE_VERSION);

        assertEquals(transactionPath, "_intransit/" + BUNDLE_NAME + "/" + BUNDLE_VERSION);
    }

    @Test
    public void testExtractReleaseBundleNameAndVersion() {
        // wrong format
        expectThrows(IllegalArgumentException.class,
                () -> ReleaseBundleUtils.extractReleaseBundleNameAndVersion("badFormat")
        );

        // wrong format
        expectThrows(IllegalArgumentException.class,
                () -> ReleaseBundleUtils.extractReleaseBundleNameAndVersion("a/b#c")
        );

        Pair<String, String> nameVersion = ReleaseBundleUtils.extractReleaseBundleNameAndVersion("_intransit/b/c");
        assertEquals(nameVersion.getLeft(), "b");
        assertEquals(nameVersion.getRight(), "c");
    }

    @Test
    public void testGetPotentialFolderPathsToDelete() {
        RepoPath firstRepoPath = RepoPathFactory.create("repo1/path/to/file1");
        RepoPath secondRepoPath = RepoPathFactory.create("repo2/another/path/to/file2");
        List<BundleNode> bundleNodes = List.of(
                new BundleNode(1234, 234, firstRepoPath.toPath(), 111, "originalDetails"),
                new BundleNode(1235, 235, secondRepoPath.toPath(), 111, "originalDetails")
        );
        Set<String> potentialFolderPathsToDelete = ReleaseBundleUtils.getPotentialFolderPathsToDelete(bundleNodes);
        assertEquals(potentialFolderPathsToDelete.size(), FOLDER_PATHS_DEPTH_2_3_4_SET.size());
        assertTrue(potentialFolderPathsToDelete.containsAll(FOLDER_PATHS_DEPTH_2_3_4_SET));
    }

    @Test
    public void testGetDepthFolderPathsMap() {
        Map<Integer, Set<String>> depthFolderPathsMap =
                ReleaseBundleUtils.getDepthFolderPathsMap(FOLDER_PATHS_DEPTH_2_3_4_SET);
        assertEquals(depthFolderPathsMap.keySet().size(), 3);
        assertNull(depthFolderPathsMap.get(1));

        Set<String> depth2 = depthFolderPathsMap.get(2);
        assertNotNull(depth2);
        assertEquals(depth2.size(), 2);
        assertTrue(depth2.containsAll(FOLDER_PATHS_DEPTH_2_SET));

        Set<String> depth3 = depthFolderPathsMap.get(3);
        assertNotNull(depth3);
        assertEquals(depth3.size(), 2);
        assertTrue(depth3.containsAll(FOLDER_PATHS_DEPTH_3_SET));

        Set<String> depth4 = depthFolderPathsMap.get(4);
        assertNotNull(depth4);
        assertEquals(depth4.size(), 1);
        assertEquals(depth4, FOLDER_PATHS_DEPTH_4_SET);
    }

    @Test(dataProvider = "extractTargetRepoPathTestData")
    public void testExtractTargetRepoPath(String inputPath, String expected) {
        // setup
        RepoPath inputRepoPath = RepoPathFactory.create(inputPath);
        RepoPath expectedRepoPath = RepoPathFactory.create(expected);

        // test
        RepoPath result = ReleaseBundleUtils.extractTargetPathFromIntransitPath(inputRepoPath);
        // verify
        assertEquals(result, expectedRepoPath);
    }

    @Test(dataProvider = "inTransitRepoPathTestData_invalid", expectedExceptions = IllegalArgumentException.class)
    public void testExtractTargetRepoPath_invalidInput(String inputPath) {
        // setup
        RepoPath inputRepoPath = RepoPathFactory.create(inputPath);

        // test
        ReleaseBundleUtils.extractTargetPathFromIntransitPath(inputRepoPath);
    }

    @Test(dataProvider = "nameVersionFromInTransitRepoPathTestData")
    public void testExtractNameVersion(String inputPath, String expectedName, String expectedVersion) {
        // setup
        RepoPath inputRepoPath = RepoPathFactory.create(inputPath);

        // test
        Pair<String, String> result =
                ReleaseBundleUtils.extractReleaseBundleNameAndVersionFromIntransitPath(inputRepoPath);
        // verify
        assertEquals(result.getKey(), expectedName);
        assertEquals(result.getValue(), expectedVersion);
    }

    @Test(dataProvider = "inTransitRepoPathTestData_invalid", expectedExceptions = IllegalArgumentException.class)
    public void testExtractNameVersion_invalidInput(String inputPath) {
        // setup
        RepoPath inputRepoPath = RepoPathFactory.create(inputPath);

        // test
        ReleaseBundleUtils.extractReleaseBundleNameAndVersionFromIntransitPath(inputRepoPath);
    }
}
