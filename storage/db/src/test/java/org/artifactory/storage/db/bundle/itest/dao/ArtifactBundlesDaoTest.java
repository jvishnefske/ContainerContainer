/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.bundle.itest.dao;

import lombok.NonNull;
import org.artifactory.api.release.bundle.ReleaseBundleSearchFilter;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.artifactory.repo.RepoPath;
import org.artifactory.sh.ArtifactoryTestBinariesDao;
import org.artifactory.sh.TestBinaryEntity;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.artifactory.storage.db.bundle.model.DBBundleResult;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;

/**
 * @author Rotem Kfir
 */
@Test
public class ArtifactBundlesDaoTest extends DbBaseTest {

    private static final String RELEASE_BUNDLE_DEFAULT_REPO = "release-bundles";
    private static final String IN_TRANSIT_REPO = "_intransit";

    @Autowired
    protected ArtifactBundlesDao artifactBundlesDao;

    @Autowired
    protected NodesDao nodesDao;

    @Autowired
    protected ArtifactoryTestBinariesDao binariesDao;

    private int nextId = 1;

    private List<Long> nodesToDeleteAfterMethod = new ArrayList<>();

    @AfterMethod
    public void cleanup() throws SQLException {
        artifactBundlesDao.deleteAllBundleNodes();
        artifactBundlesDao.deleteAllArtifactsBundles();
        nodesDao.deleteBulk(nodesToDeleteAfterMethod);
        nodesToDeleteAfterMethod = new ArrayList<>();
    }

    @Test
    public void getLatestVersionOfCompletedBundles() throws SQLException {
        List<DBArtifactsBundle> bundles = artifactBundlesDao.getCompletedBundlesLastVersion(BundleType.TARGET);
        assertThat(bundles).isEmpty();

        // Same name, with different versions
        DBArtifactsBundle bundle = new DBArtifactsBundle();
        for (int i = 0; i < 3; i++) {
            bundle.setId(++nextId);
            bundle.setName("RB");
            bundle.setStatus(BundleTransactionStatus.COMPLETE);
            bundle.setDateCreated(DateTime.now().plusMillis(nextId));
            bundle.setDateDistributed(DateTime.now().plusMillis(nextId));
            bundle.setVersion(String.valueOf(nextId));
            bundle.setType(BundleType.TARGET);
            bundle.setSignature("Signature" + nextId);
            artifactBundlesDao.create(bundle);
        }
        bundles = artifactBundlesDao.getCompletedBundlesLastVersion(BundleType.TARGET);
        assertThat(bundles).hasSize(1);

        // Same creation date, even higher version, but INPROGRESS
        // Should be completed bundle with the latest date_created and highest version name
        bundle.setId(++nextId);
        bundle.setVersion(String.valueOf(nextId));
        bundle.setStatus(BundleTransactionStatus.INPROGRESS);
        artifactBundlesDao.create(bundle);
        bundles = artifactBundlesDao.getCompletedBundlesLastVersion(BundleType.TARGET);
        assertThat(bundles).hasSize(1);
        assertThat(bundles.get(0).getVersion()).isEqualTo(String.valueOf(nextId - 1));

        // Different name
        bundle.setId(++nextId);
        bundle.setName("RB2");
        bundle.setStatus(BundleTransactionStatus.COMPLETE);
        artifactBundlesDao.create(bundle);
        bundles = artifactBundlesDao.getCompletedBundlesLastVersion(BundleType.TARGET);
        assertThat(bundles).hasSize(2);
    }

    @Test
    public void testCreateBundleWithDateDistributed() throws SQLException {
        List<DBArtifactsBundle> bundles = artifactBundlesDao.getCompletedBundlesLastVersion(BundleType.TARGET);
        assertThat(bundles).isEmpty();

        DateTime dateDistributed = DateTime.now().plusMillis(100);

        createBundle("MyRB1", "version" + nextId, BundleTransactionStatus.COMPLETE, BundleType.TARGET,
                "release_bundle", dateDistributed);

        bundles = artifactBundlesDao.getAllArtifactsBundles(BundleType.TARGET);
        assertThat(bundles).hasSize(1);
        assertThat(bundles.get(0).getDateDistributed().getMillis()).isEqualTo(dateDistributed.getMillis());
        assertThat(bundles.get(0).getKeep()).isFalse();
    }

    @Test
    public void testGetExpiredBundlesIds() throws SQLException {
        DateTime dateDistributed = DateTime.now().minusHours(2);

        long bundleIdInProgressAndExpired = createBundle("ExpiredBundle", "V" + nextId,
                BundleTransactionStatus.INPROGRESS, BundleType.TARGET, "release_bundle", dateDistributed).getId();

        createBundle("CompleteBundle", "V" + nextId,
                BundleTransactionStatus.COMPLETE, BundleType.TARGET, "release_bundle", dateDistributed);

        createBundle("InProgressSourceBundle", "V" + nextId,
                BundleTransactionStatus.INPROGRESS, BundleType.SOURCE, "release_bundle", null);

        createBundle("InProgressTargetBundle", "V" + nextId,
                BundleTransactionStatus.INPROGRESS, BundleType.TARGET, "release_bundle", new DateTime());

        createBundle("InProgressSourceBundle", "V" + nextId,
                BundleTransactionStatus.INPROGRESS, BundleType.SOURCE, "release_bundle", dateDistributed);

        createBundle("InProgressTargetFresh", "V" + nextId,
                BundleTransactionStatus.INPROGRESS, BundleType.TARGET, "release_bundle", DateTime.now());

        long bundleIdFailedAndExpired = createBundle("FailedExpiredBundle", "V" + nextId,
                BundleTransactionStatus.FAILED, BundleType.TARGET, "release_bundle", dateDistributed).getId();

        assertThat(artifactBundlesDao.getExpiredBundlesIds(1L))
                .hasSize(2)
                .containsExactlyInAnyOrder(bundleIdFailedAndExpired, bundleIdInProgressAndExpired);
    }

    @Test
    public void testCreateBundleFile() throws SQLException {

        DBArtifactsBundle dbArtifactsBundle = createBundle("yoazRB", "1.x.0", BundleTransactionStatus.INPROGRESS,
                BundleType.SOURCE, RELEASE_BUNDLE_DEFAULT_REPO);
        TestBinaryEntity binaryEntity = createBinary(88);
        Node node = createNode(true, "repo2", "path/to", "file", 2, binaryEntity);
        String componentDetails =
                "{\"name\":\"yoaz.jar\",\"version\":\"1.0.x\",\"componentType\":\"Maven\",\"extension\":\"jar\"," +
                        "\"mimeType\":\"application/java-archive\"}";
        BundleNode bundleNode =
                buildBundleNode(node.getNodeId(), dbArtifactsBundle.getId(), componentDetails, "my/repo/path", 1231);
        artifactBundlesDao.create(bundleNode);
        bundleNode = buildBundleNode(node.getNodeId(), dbArtifactsBundle.getId(), null, "my/repo/path2", 1232);
        artifactBundlesDao.create(bundleNode);
        String details = artifactBundlesDao.getComponentDetails("my/repo/path");
        assertEquals(componentDetails, details);
        details = artifactBundlesDao.getComponentDetails("my/repo/path2");
        assertNull(details);
    }

    @Test
    public void testDeleteBundleNodes() throws SQLException {
        DBArtifactsBundle dbArtifactsBundle = createBundle("yoazRB", "1.x.0", BundleTransactionStatus.INPROGRESS,
                BundleType.SOURCE, RELEASE_BUNDLE_DEFAULT_REPO);

        TestBinaryEntity binaryEntity = createBinary(100);
        Node node = createNode(true, "repo2", "path/to", "file" + ++nextId, 2, binaryEntity);
        BundleNode bundleNode = buildBundleNode(node.getNodeId(), dbArtifactsBundle.getId(), null, "my/repo/path",
                1000);
        deleteAndAssertDeleted(bundleNode, 2000);

        deleteAndAssertDeleted(bundleNode, 2189);

        deleteAndAssertDeleted(bundleNode, 3);
    }

    private void deleteAndAssertDeleted(BundleNode bundleNode, int numberOfRecords) throws SQLException {
        for (int i = 0; i < numberOfRecords; i++) {
            artifactBundlesDao.create(bundleNode);
            bundleNode.setId(bundleNode.getId() + 1);
        }
        artifactBundlesDao.deleteBundleNodes(bundleNode.getBundleId());
        assertThat(artifactBundlesDao.getBundleNodes(bundleNode.getBundleId())).isEmpty();
    }

    @Test
    public void testIsPathRelatedToBundle() throws SQLException {
        DBArtifactsBundle dbArtifactsBundle = createBundle("yoazRB2", "1.x.1", BundleTransactionStatus.INPROGRESS,
                BundleType.SOURCE, RELEASE_BUNDLE_DEFAULT_REPO);

        TestBinaryEntity binaryEntity = createBinary(33);
        // out of the bundle
        createNode(true, "repo1", "path", "file" + ++nextId, 1, binaryEntity);
        // in the bundle
        Node node = createNode(true, "repo2", "path/to", "file" + ++nextId, 2, binaryEntity);
        BundleNode bundleNode = buildBundleNode(node.getNodeId(), dbArtifactsBundle.getId(), null,
                "repo2/path/to/file", 1000);
        artifactBundlesDao.create(bundleNode);

        assertThat(artifactBundlesDao.getRelatedArtifactsBundles("repo2", false, 1L)).isNotEmpty();
        assertThat(artifactBundlesDao.getRelatedArtifactsBundles("repo2/path/", false, 1L)).isNotEmpty();
        assertThat(artifactBundlesDao.getRelatedArtifactsBundles("repo2/path/", true, 1L)).isEmpty();
        assertThat(artifactBundlesDao.getRelatedArtifactsBundles("repo2/path/to", false, 1L)).isNotEmpty();
        assertThat(artifactBundlesDao.getRelatedArtifactsBundles("repo2/path/to/file", true, 1L)).isNotEmpty();

        assertThat(artifactBundlesDao.getRelatedArtifactsBundles("repo1/path/", false, 1L)).isEmpty();
        assertThat(artifactBundlesDao.getRelatedArtifactsBundles("repo1/path/file", true, 1L)).isEmpty();
    }

    @Test
    public void getCompletedBundles() throws SQLException {
        createBundle("bundle1", "1.0.0", BundleTransactionStatus.COMPLETE, BundleType.SOURCE,
                RELEASE_BUNDLE_DEFAULT_REPO);
        createBundle("bundle1", "2.0.0", BundleTransactionStatus.COMPLETE, BundleType.SOURCE, "release-bundles-1");
        createBundle("bundle1", "3.0.0", BundleTransactionStatus.INPROGRESS, BundleType.SOURCE, "release-bundles-2");

        createBundle("bundle2", "3.0.0", BundleTransactionStatus.COMPLETE, BundleType.TARGET, null);
        createBundle("bundle2", "4.0.0", BundleTransactionStatus.COMPLETE, BundleType.SOURCE,
                RELEASE_BUNDLE_DEFAULT_REPO);

        createBundle("bundle3", "1.0.0", BundleTransactionStatus.COMPLETE, BundleType.SOURCE, "release-bundles-2");
        createBundle("bundle3", "5.0.0", BundleTransactionStatus.COMPLETE, BundleType.TARGET,
                RELEASE_BUNDLE_DEFAULT_REPO);

        List<DBBundleResult> completedBundles = artifactBundlesDao.getAllCompletedBundles(null);
        assertThat(completedBundles).hasSize(5);
        List<DBBundleResult> expectedBundles = Lists.newArrayList(
                new DBBundleResult("bundle1", RELEASE_BUNDLE_DEFAULT_REPO),
                new DBBundleResult("bundle1", "release-bundles-1"),
                new DBBundleResult("bundle2", RELEASE_BUNDLE_DEFAULT_REPO),
                new DBBundleResult("bundle3", RELEASE_BUNDLE_DEFAULT_REPO),
                new DBBundleResult("bundle3", "release-bundles-2")
        );
        assertEquals(expectedBundles, completedBundles);

        completedBundles = artifactBundlesDao.getAllCompletedBundles(BundleType.SOURCE);
        assertThat(completedBundles).hasSize(4);
        expectedBundles = Lists.newArrayList(
                new DBBundleResult("bundle1", RELEASE_BUNDLE_DEFAULT_REPO),
                new DBBundleResult("bundle1", "release-bundles-1"),
                new DBBundleResult("bundle2", RELEASE_BUNDLE_DEFAULT_REPO),
                new DBBundleResult("bundle3", "release-bundles-2"));
        assertEquals(expectedBundles, completedBundles);

        completedBundles = artifactBundlesDao.getAllCompletedBundles(BundleType.TARGET);
        assertThat(completedBundles).hasSize(2);
        expectedBundles = Lists.newArrayList(
                new DBBundleResult("bundle2", RELEASE_BUNDLE_DEFAULT_REPO),
                new DBBundleResult("bundle3", RELEASE_BUNDLE_DEFAULT_REPO)
        );
        assertEquals(expectedBundles, completedBundles);
    }

    @Test
    public void getCompletedBundlesNotFound() throws SQLException {
        createBundle("bundle2", "3.0.0", BundleTransactionStatus.INPROGRESS, BundleType.TARGET, null);
        createBundle("bundle2", "4.0.0", BundleTransactionStatus.FAILED, BundleType.SOURCE,
                RELEASE_BUNDLE_DEFAULT_REPO);
        createBundle("bundle3", "1.0.0", BundleTransactionStatus.FAILED, BundleType.SOURCE, "release-bundles-2");

        List<DBBundleResult> completedBundles = artifactBundlesDao.getAllCompletedBundles(null);
        assertThat(completedBundles).isEmpty();
    }

    @Test
    public void testGetAllBundlesRelatedToNode() throws SQLException {
        long rb1Id = createBundle("rb1", "1.0.0", BundleTransactionStatus.COMPLETE, BundleType.TARGET,
                RELEASE_BUNDLE_DEFAULT_REPO, new DateTime()).getId();
        TestBinaryEntity binaryEntity = createBinary(11);

        Node node = createNode(false, "generic-target-1", ".", "my_path_1", 1, binaryEntity);
        createBundleNode(node.getNodeId(), rb1Id, "generic-target-1/my_path_1/org/artifact/1.0/artifact-1.0.jar");
        long rb2Id = createBundle("rb2", "1.0.0", BundleTransactionStatus.COMPLETE, BundleType.TARGET,
                RELEASE_BUNDLE_DEFAULT_REPO, new DateTime()).getId();
        createBundleNode(node.getNodeId(), rb2Id, "generic-target-1/my_path_1/org/artifact/1.0/artifact-1.0.jar");

        assertThat(artifactBundlesDao.getAllBundlesRelatedToNode(node.getNodeId()))
                .hasSize(2)
                .containsExactlyInAnyOrder(rb1Id, rb2Id);
    }

    @Test
    public void testIsReleaseBundleExist() throws SQLException {
        createBundle("rb1", "1.0.0", BundleTransactionStatus.COMPLETE, BundleType.TARGET,
                RELEASE_BUNDLE_DEFAULT_REPO, new DateTime());

        Assert.assertTrue(artifactBundlesDao.isReleaseBundleExist("rb1"));
        Assert.assertFalse(artifactBundlesDao.isReleaseBundleExist("no-bundle"));
    }

    private BundleNode buildBundleNode(long nodeID, long bundleID, String componentDetails, String bundleNodeRepoPath,
            int bundleNodeID) {
        BundleNode bundleNode = new BundleNode();
        bundleNode.setBundleId(bundleID);
        bundleNode.setId(bundleNodeID);
        bundleNode.setOriginalFileDetails(componentDetails);
        bundleNode.setRepoPath(bundleNodeRepoPath);
        bundleNode.setNodeId(nodeID);
        return bundleNode;
    }

    private Node createNode(boolean isFile, String repoKey, String path, String name, int depth,
            TestBinaryEntity binary)
            throws SQLException {
        DateTime now = DateTime.now();
        Node node = new Node(++nextId, isFile, repoKey, path, name, (short) depth, now.minus(nextId).getMillis(),
                "creator", now.getMillis(), "modifier", now.plus(nextId).getMillis(), binary.getLength(),
                binary.getSha1(), binary.getSha1(), binary.getMd5(), binary.getMd5(), binary.getSha2());
        nodesDao.create(node);
        nodesToDeleteAfterMethod.add(node.getNodeId());
        return node;
    }

    private void createBundleNode(long nodeId, long bundleId, String path) throws SQLException {
        BundleNode bundleNode = new BundleNode();
        bundleNode.setNodeId(nodeId);
        bundleNode.setBundleId(bundleId);
        bundleNode.setRepoPath(path);
        artifactBundlesDao.create(bundleNode);
    }

    private DBArtifactsBundle createBundle(String name, String version, BundleTransactionStatus status,
            BundleType type, String storingRepo, DateTime dateDistributed, String serviceId) throws SQLException {
        DBArtifactsBundle bundle = new DBArtifactsBundle();
        bundle.setId(++nextId);
        bundle.setName(name);
        bundle.setStatus(status);
        bundle.setDateCreated(DateTime.now());
        bundle.setDateDistributed(dateDistributed);
        bundle.setVersion(version);
        bundle.setType(type);
        bundle.setStoringRepo(storingRepo);
        bundle.setSignature("Signature" + nextId);
        bundle.setSourceServiceId(serviceId);
        artifactBundlesDao.create(bundle);
        return bundle;
    }

    private DBArtifactsBundle createBundle(String name, String version, BundleTransactionStatus status,
            BundleType type, String storingRepo) throws SQLException {
        return createBundle(name, version, status, type, storingRepo,
                type.equals(BundleType.TARGET) ? new DateTime() : null);
    }

    private DBArtifactsBundle createBundle(String name, String version, BundleTransactionStatus status,
            BundleType type, String storingRepo, DateTime dateDistributed) throws SQLException {
        return createBundle(name, version, status, type, storingRepo, dateDistributed, null);
    }

    @Test
    public void testBundleFilters() throws SQLException {
        DBArtifactsBundle bundle = new DBArtifactsBundle();
        int counter = 0;
        for (int i = 1; i <= 4; i++) {
            for (int j = 0; j < 2; j++) {
                bundle.setId(++nextId);
                bundle.setName("myRBS" + i);
                bundle.setStatus(BundleTransactionStatus.COMPLETE);
                DateTime now = DateTime.now();
                bundle.setDateCreated(now.plus(counter++));
                bundle.setVersion("1." + j);
                bundle.setType(BundleType.TARGET);
                bundle.setSignature("Signature");
                artifactBundlesDao.create(bundle);
            }
        }
        testFilterName();
        testFilterNameCaseInsensitive();
        testFilterLimit();
        testFilterNameAndOrderBy();
        testFilterStarNameAndOrderBy();
        testFilterNameDefaultOrderBy();
        testFilterDates();
        testFilterUnderScoreNameAndOrderBy();
        testFilterNameAndOrderBySeveralColumns();
        testFilterNameAndOrderByIncorrectOrderQuery();
        testFilterNameAndOrderBySqlInjectionInNames();
    }

    @Test
    public void testGetOrphanRepoPathsFromIntransit() throws SQLException {
        List<RepoPath> nodesToVerify = prepareDBForDeletionOfOrphanAndUnidentifiedItems(false);
        Set<RepoPath> orphanRepoPaths = artifactBundlesDao.getOrphanRBVRepoPathsFromIntransit();
        assertEquals(orphanRepoPaths.size(), nodesToVerify.size());
        for (RepoPath repoPath : nodesToVerify) {
            assertTrue(orphanRepoPaths.contains(repoPath));
        }
    }

    @Test
    public void testGetUnidentifiedRepoPathsFromIntransit() throws SQLException {
        List<RepoPath> nodesToVerify = prepareDBForDeletionOfOrphanAndUnidentifiedItems(true);
        Set<RepoPath> unidentifiedRepoPaths = artifactBundlesDao.getUnidentifiedRepoPathsOnInTransit();
        assertEquals(unidentifiedRepoPaths.size(), nodesToVerify.size());
        for (RepoPath repoPath : nodesToVerify) {
            assertTrue(unidentifiedRepoPaths.contains(repoPath));
        }
    }

    @Test
    public void testKeepBundle() throws SQLException {
        // setup
        String name = "rb1";
        String version = "version" + nextId;

        createBundle(name, version, BundleTransactionStatus.COMPLETE, BundleType.TARGET, "release_bundle");
        boolean isKeep = artifactBundlesDao.isKeepBundle(name, version);
        assertFalse(isKeep);

        // test
        int i = artifactBundlesDao.setKeepBundle(name, version, true);

        // verify
        assertEquals(1, i);

        isKeep = artifactBundlesDao.isKeepBundle(name, version);
        assertTrue(isKeep);
    }

    @Test
    public void testKeepBundle_bundleNotExist() throws SQLException {
        // setup
        String name = "rb1";
        String version = "version" + nextId;

        createBundle(name, version, BundleTransactionStatus.COMPLETE, BundleType.TARGET, "release_bundle");

        // test
        int i = artifactBundlesDao.setKeepBundle("wrongBundle", version, true);

        // verify
        assertEquals(0, i);
    }

    @Test(expectedExceptions = SQLException.class, expectedExceptionsMessageRegExp = "Bundle .* does not exists")
    public void testIsKeepBundle_bundleNotExist() throws SQLException {
        // test
        artifactBundlesDao.isKeepBundle("wrongBundle", "version");
    }

    @Test
    public void testKeepBundle_sourceBundle() throws SQLException {
        // setup
        String name = "rb1";
        String version = "version" + nextId;

        createBundle(name, version, BundleTransactionStatus.COMPLETE, BundleType.SOURCE, "release_bundle");

        // test
        int i = artifactBundlesDao.setKeepBundle(name, version, true);

        // verify
        assertEquals(0, i);

        boolean caughtExpectedException = false;
        try {
            artifactBundlesDao.isKeepBundle(name, version);
        } catch (SQLException e) {
            caughtExpectedException = e.getMessage().equals("Bundle " + name + "/" + version + " does not exists");
        }

        assertTrue(caughtExpectedException);
    }

    @Test
    public void testSourceServiceId() throws SQLException {
        // setup
        String name = "rb_source";
        String version = "version" + nextId;
        String expectedServiceId = "serviceId" + nextId;

        createBundle(name, version, BundleTransactionStatus.COMPLETE, BundleType.TARGET, RELEASE_BUNDLE_DEFAULT_REPO,
                new DateTime(), expectedServiceId);

        // test
        String serviceId = artifactBundlesDao.getSourceServiceId(name, version);

        // verify
        assertEquals(serviceId, expectedServiceId);

        // test not_found
        serviceId = artifactBundlesDao.getSourceServiceId("not_found" + nextId, version);

        // verify
        assertNull(serviceId);
    }

    @Test
    public void testSourceServiceId_nonNull() {
        assertThrows(NullPointerException.class, () -> artifactBundlesDao.getSourceServiceId(null, ""));
        assertThrows(NullPointerException.class, () -> artifactBundlesDao.getSourceServiceId("", null));
        assertThrows(NullPointerException.class, () -> artifactBundlesDao.getSourceServiceId(null, null));
    }

    @Test
    public void testGetArtifactsBundleById() throws SQLException {
        DBArtifactsBundle bundle = new DBArtifactsBundle();
        int bundleId = ++nextId;
        DateTime creationDate = DateTime.now();
        DateTime distributionDate = DateTime.now().plusMinutes(10);

        bundle.setId(bundleId);
        bundle.setName("RBS");
        bundle.setVersion("1.0");
        bundle.setStatus(BundleTransactionStatus.COMPLETE);
        bundle.setDateCreated(creationDate);
        bundle.setDateDistributed(distributionDate);
        bundle.setSignature("Signature");
        bundle.setType(BundleType.SOURCE);
        bundle.setStoringRepo("storing_repo");
        bundle.setKeep(false);
        bundle.setSourceServiceId("123SourceServiceId@345");
        artifactBundlesDao.create(bundle);

        DBArtifactsBundle rb = artifactBundlesDao.getArtifactsBundle(bundleId);
        assertThat(rb).isNotNull();
        assertEquals(bundleId, rb.getId());
        assertEquals("RBS", rb.getName());
        assertEquals("1.0", rb.getVersion());
        assertEquals(BundleTransactionStatus.COMPLETE, rb.getStatus());
        assertEquals(creationDate, rb.getDateCreated());
        assertEquals(distributionDate, rb.getDateDistributed());
        assertEquals("Signature", rb.getSignature());
        assertEquals(BundleType.SOURCE, rb.getType());
        assertEquals("storing_repo", rb.getStoringRepo());
        assertFalse(rb.getKeep());
        assertEquals("123SourceServiceId@345", rb.getSourceServiceId());
    }

    @Test
    public void testGetArtifactsBundles() throws SQLException {
        DBArtifactsBundle bundle = new DBArtifactsBundle();
        int bundleId = ++nextId;
        DateTime creationDate = DateTime.now();
        DateTime distributionDate = DateTime.now().plusMinutes(10);

        bundle.setId(bundleId);
        bundle.setName("RBS");
        bundle.setVersion("1");
        bundle.setStatus(BundleTransactionStatus.COMPLETE);
        bundle.setDateCreated(creationDate);
        bundle.setDateDistributed(distributionDate);
        bundle.setSignature("Signature");
        bundle.setType(BundleType.SOURCE);
        bundle.setStoringRepo("storing_repo");
        bundle.setKeep(false);
        bundle.setSourceServiceId("123SourceServiceId@345");
        artifactBundlesDao.create(bundle);

        List<DBArtifactsBundle> rbs = artifactBundlesDao.getArtifactsBundles("RBS", BundleType.SOURCE);
        assertThat(rbs).isNotNull();
        assertEquals(1,  rbs.size());
        DBArtifactsBundle dbArtifactsBundle = rbs.getFirst();
        assertEquals(bundleId, dbArtifactsBundle.getId());
        assertEquals("RBS", dbArtifactsBundle.getName());
        assertEquals("1", dbArtifactsBundle.getVersion());
        assertEquals(BundleTransactionStatus.COMPLETE, dbArtifactsBundle.getStatus());
        assertEquals(creationDate, dbArtifactsBundle.getDateCreated());
        assertEquals("storing_repo" ,dbArtifactsBundle.getStoringRepo());
        assertFalse(dbArtifactsBundle.getKeep());
        assertNull(dbArtifactsBundle.getDateDistributed());
        assertNull(dbArtifactsBundle.getSignature());
    }

    @Test
    public void testTryToOverrideStatusWhenFinal() throws SQLException {
        String bundleName = "status_not_to_be_overridden";
        String bundleVersion = "1.0.0";

        createBundle(bundleName, bundleVersion, BundleTransactionStatus.COMPLETE, BundleType.TARGET, null);
        DBArtifactsBundle dbArtifactsBundle =
                artifactBundlesDao.getArtifactsBundleStatus(bundleName, bundleVersion, BundleType.TARGET);
        assertEquals(BundleTransactionStatus.COMPLETE, dbArtifactsBundle.getStatus());

        // try to set not final status, although it's final in the db
        artifactBundlesDao.tryToSetBundleStatus(bundleName, bundleVersion, BundleType.TARGET,
                BundleTransactionStatus.CLOSE_INPROGRESS);
        dbArtifactsBundle =
                artifactBundlesDao.getArtifactsBundleStatus(bundleName, bundleVersion, BundleType.TARGET);
        // status not changed, as it's final
        assertEquals(BundleTransactionStatus.COMPLETE, dbArtifactsBundle.getStatus());
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Failed updating Release Bundle not_found_bundle/1.0.0 with type" +
                    " TARGET. Release Bundle does not exist")
    public void testTryToUpdateStatusWhenNotFound() throws SQLException {
        String bundleName = "not_found_bundle";
        String bundleVersion = "1.0.0";

        artifactBundlesDao.tryToSetBundleStatus(bundleName, bundleVersion, BundleType.TARGET,
                BundleTransactionStatus.CLOSE_INPROGRESS);
    }


    @Test
    public void testTryToUpdateStatus() throws SQLException {
        String bundleName = "status_to_be_overridden";
        String bundleVersion = "1.0.0";

        createBundle(bundleName, bundleVersion, BundleTransactionStatus.CLOSE_INPROGRESS, BundleType.TARGET, null);
        DBArtifactsBundle dbArtifactsBundle =
                artifactBundlesDao.getArtifactsBundleStatus(bundleName, bundleVersion, BundleType.TARGET);
        assertEquals(BundleTransactionStatus.CLOSE_INPROGRESS, dbArtifactsBundle.getStatus());

        artifactBundlesDao.tryToSetBundleStatus(bundleName, bundleVersion, BundleType.TARGET,
                BundleTransactionStatus.COMPLETE);
        dbArtifactsBundle =
                artifactBundlesDao.getArtifactsBundleStatus(bundleName, bundleVersion, BundleType.TARGET);
        assertEquals(BundleTransactionStatus.COMPLETE, dbArtifactsBundle.getStatus());
    }


    @NonNull
    private List<RepoPath> prepareDBForDeletionOfOrphanAndUnidentifiedItems(boolean returnUnidentified)
            throws SQLException {
        //create rbv artifacts
        String rbName = "heziRB" + ++nextId;
        Node rbBaseFolderNode = createSimpleFolder("", rbName, 1);
        Node inProgressNode = createOrphanBundleNode(rbName, BundleTransactionStatus.INPROGRESS);
        Node closeInProgressNode = createOrphanBundleNode(rbName, BundleTransactionStatus.CLOSE_INPROGRESS);
        Node failedNode = createOrphanBundleNode(rbName, BundleTransactionStatus.FAILED);
        Node completedNode = createOrphanBundleNode(rbName, BundleTransactionStatus.COMPLETE);

        //create file1 that's not related to any rbv
        Node unidentifiedItem1 = createSimpleNode("", "unidentified" + ++nextId, 1);

        //create file2 that's not related to any rbv
        Node unidentifiedItem2 = createSimpleFolder("", "folder_1", 1);
        Node unidentifiedItem3 = createSimpleNode("folder_1", "unidentified" + ++nextId, 2);

        //create file2 that's not related to any rbv
        Node unidentifiedItem4 = createSimpleFolder("", "folder_2", 1);
        Node unidentifiedItem5 = createSimpleFolder("folder_2", "folder_3", 2);
        Node unidentifiedItem6 = createSimpleNode("folder_2/folder_3", "unidentified" + ++nextId, 3);

        assertNodesExist(List.of(inProgressNode, closeInProgressNode, failedNode, completedNode, unidentifiedItem1,
                unidentifiedItem2, unidentifiedItem3, unidentifiedItem4, unidentifiedItem5, unidentifiedItem6));

        List<RepoPath> unidentifiedRepoPaths = List.of(
                rbBaseFolderNode.getNodePath().toRepoPath(),
                unidentifiedItem1.getNodePath().toRepoPath(),
                unidentifiedItem2.getNodePath().toRepoPath(),
                unidentifiedItem4.getNodePath().toRepoPath());

        List<RepoPath> orphanRepoPaths = List.of(
                unidentifiedItem5.getNodePath().toRepoPath(),
                failedNode.getNodePath().toRepoPath(),
                completedNode.getNodePath().toRepoPath(),
                inProgressNode.getNodePath().toRepoPath(),
                closeInProgressNode.getNodePath().toRepoPath());

        return returnUnidentified ? unidentifiedRepoPaths : orphanRepoPaths;
    }

    private void assertNodesExist(List<Node> nodes) throws SQLException {
        for (Node node : nodes) {
            assertNotNull(nodesDao.get(node.getNodeId()));
        }
    }

    @NonNull
    private Node createOrphanBundleNode(@NonNull String name, BundleTransactionStatus status) throws SQLException {
        String version = String.valueOf(++nextId);
        String fileName = status + "_" + version;
        Node rbArtifact = createSimpleFolder(name, version, 2);
        createSimpleNode(name + "/" + version, fileName, 3);
        return rbArtifact;
    }

    private Node createSimpleNode(String path, String name, int depth) throws SQLException {
        TestBinaryEntity binaryEntity = createBinary(50);
        return createNode(true, IN_TRANSIT_REPO, path, name, depth, binaryEntity);
    }

    private Node createSimpleFolder(String path, String name, int depth) throws SQLException {
        Node node = new Node(++nextId, false, IN_TRANSIT_REPO, path, name, (short) depth,
                543L, "creator", 123L, "modifier", 213L, 0, null, null, null, null, null);
        nodesDao.create(node);
        nodesToDeleteAfterMethod.add(node.getNodeId());
        return node;
    }

    private void testFilterNameAndOrderBy() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("myRBS*")
                .orderBy("name")
                .direction("asc")
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 4);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getName(), "myRBS2");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getVersion(), "1.1");
    }

    private void testFilterName() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("myRBS1")
                .limit(3) //should not affect, limit implemented outside of the dao
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 1);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS1");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
    }

    private void testFilterNameCaseInsensitive() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("myrbs1")
                .limit(3) //should not affect, limit implemented outside of the dao
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 1);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS1");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
    }

    private void testFilterLimit() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("?")
                .daoLimit(3) //should not affect, limit implemented outside of the dao
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 3);
    }

    private void testFilterNameDefaultOrderBy() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("myRBS")
                .limit(3) //should not affect, limit implemented outside of the dao
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 4);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS4");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
    }

    private void testFilterStarNameAndOrderBy() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("my*")
                .orderBy("name")
                .direction("desc")
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 4);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS4");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getName(), "myRBS3");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getVersion(), "1.1");
    }

    private void testFilterDates() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("my*")
                .after(1013572468190L)
                .before(1913572468190L)
                .orderBy("name")
                .direction("desc")
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 4);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS4");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getName(), "myRBS3");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getVersion(), "1.1");
    }

    private void testFilterUnderScoreNameAndOrderBy() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("m_")
                .orderBy("name")
                .direction("desc")
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 4);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS4");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getName(), "myRBS3");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getVersion(), "1.1");
    }

    private void testFilterNameAndOrderBySeveralColumns() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("myRBS*")
                .orderBy("date_created, name")
                .direction("asc")
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 4);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getName(), "myRBS2");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getVersion(), "1.1");
    }

    private void testFilterNameAndOrderByIncorrectOrderQuery() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("myRBS*")
                .orderBy("name")
                .direction("asc,  (select*from(select(sleep(8)))a)")
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 4);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS4");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getName(), "myRBS3");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getVersion(), "1.1");
    }

    private void testFilterNameAndOrderBySqlInjectionInNames() throws SQLException {
        ReleaseBundleSearchFilter filter = ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.TARGET)
                .name("myRBS*")
                .orderBy("name, (select*from(select(sleep(8)))a)")
                .direction("asc")
                .build();
        List<DBArtifactsBundle> filteredArtifactsBundles = artifactBundlesDao.getFilteredBundlesLastVersion(filter);

        Assert.assertEquals(filteredArtifactsBundles.size(), 4);
        Assert.assertEquals(filteredArtifactsBundles.get(0).getName(), "myRBS1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getName(), "myRBS2");
        Assert.assertEquals(filteredArtifactsBundles.get(0).getVersion(), "1.1");
        Assert.assertEquals(filteredArtifactsBundles.get(1).getVersion(), "1.1");
    }

    private TestBinaryEntity createBinary(long length) {
        TestBinaryEntity binaryEntity =
                new TestBinaryEntity(randomSha1(), randomSha2(), randomSha2().substring(32), length);
        binariesDao.create(binaryEntity);
        return binaryEntity;
    }
}
