package org.artifactory.storage.db.policy.cleanup.dao;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.artifactory.policy.NodeMetadata;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.ArtifactoryTestBinariesDao;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.db.fs.entity.QuartzTaskHistoryRecord;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.cleanup.CleanedNode;
import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;
import org.artifactory.storage.db.policy.cleanup.DeleteArtifactStatus;
import org.artifactory.storage.db.policy.dao.RetentionRunSummaryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author vovam
 */
public class NodeCleanedDaoTest extends DbBaseTest {
    private static final String REPO_KEY = "cleaned-nodes-test";
    private static final String POLICY_KEY = "cleaned-nodes-test-policy-key";
    private static final String EMPTY_STR = "";
    private static final String EMPTY_WITH_DOT = ".";

    @Autowired
    private NodeCleanedDao nodeCleanedDao;

    @Autowired
    private NodesDao nodesDao;

    @Autowired
    private QuartzTaskHistoryDao quartzTaskHistoryDao;

    @Autowired
    private CleanupRunSummaryDao cleanupRunSummaryDao;

    @Autowired
    private RetentionRunSummaryDao retentionRunSummaryDao;

    @Autowired
    private ArtifactoryTestBinariesDao binariesDao;

    private int policyRunId;

    private static Node createNode(String sha1, long binLength) {
        return createNode(sha1, binLength, REPO_KEY);
    }

    private static Node createNodeInTrashcan(String sha1, long binLength) {
        return createNode(sha1, binLength, TrashService.TRASH_KEY);
    }

    private static Node createNode(String sha1, long binLength, String repoKey) {
        int id = RandomUtils.nextInt(0, Integer.MAX_VALUE);
        String random = RandomStringUtils.randomAlphabetic(5);
        String path = ".";
        String name = "name_" + random;
        String actualSha1 = "actualSha1_" + sha1;
        String md5 = "md5_" + random;
        String sha2 = "sha2_" + random;
        return new Node(id, true, repoKey, path, name, (short) 1, 1L, "user",
                3L, "user", 4L, binLength, sha1, actualSha1, md5, md5, sha2);
    }

    @BeforeMethod
    public void setUp() throws SQLException {
        policyRunId = quartzTaskHistoryDao.insert(createRecord(POLICY_KEY));
        cleanupRunSummaryDao.insert(new CleanupRunSummary(POLICY_KEY, policyRunId, false, 0, 0,
                CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false, 0, 0, 0, 0, 0, 0,
                "username", 0, 0, null));
        RetentionRunSummary retentionRunSummary = RetentionRunSummary.builder()
                .policyKey(POLICY_KEY)
                .runId(policyRunId)
                .dryRun(false)
                .startedDate(0)
                .finishedDate(0)
                .retentionType(RetentionRunSummary.RetentionType.ARCHIVE)
                .status(RetentionRunSummary.Status.COMPLETED)
                .itemType(RetentionRunSummary.ItemType.PACKAGE)
                .processedItemsCount(0)
                .failedItemsCount(0)
                .totalItemsCount(0)
                .rejectedItemsCount(0)
                .processedPhysicalBinarySize(0)
                .processedVirtualBinarySize(0)
                .totalBinarySize(0)
                .triggeredBy("username")
                .tempNodesCleaned(false)
                .build();
        retentionRunSummaryDao.insert(retentionRunSummary);
    }

    @AfterMethod
    public void tearDown() throws SQLException {
        nodeCleanedDao.truncateAll();

        deleteBinariesAndNodes(REPO_KEY);
        deleteBinariesAndNodes(TrashService.TRASH_KEY);
        quartzTaskHistoryDao.deleteAll();
    }

    @Test
    public void testInsertRecord() throws SQLException {
        // given
        String packageName = "package-name";
        String packageVersion = "package-version";
        String repo = "repo";
        String nodePath = "node-path";
        String nodeName = "node-name";
        long binLength = 2001;
        String sha1 = "sha1";
        int status = DeleteArtifactStatus.DELETED.getCode();
        String message = "message";
        boolean lead = true;
        CleanedNode cleanedNode = new CleanedNode(POLICY_KEY, policyRunId, packageName, packageVersion, lead, repo,
                nodePath, nodeName, binLength, sha1, status, message, null);
        // when
        nodeCleanedDao.insertCleanedNode(cleanedNode);

        // then
        List<CleanedNode> cleanedNodes = nodeCleanedDao.getAll();
        assertEquals(cleanedNodes.size(), 1);

        CleanedNode cleanedNodeFromDb = cleanedNodes.get(0);
        assertEquals(cleanedNodeFromDb.policyKey(), POLICY_KEY);
        assertEquals(cleanedNodeFromDb.policyRunId(), policyRunId);
        assertEquals(cleanedNodeFromDb.packageName(), packageName);
        assertEquals(cleanedNodeFromDb.packageVersion(), packageVersion);
        assertEquals(cleanedNodeFromDb.lead(), lead);
        assertEquals(cleanedNodeFromDb.repo(), repo);
        assertEquals(cleanedNodeFromDb.nodePath(), nodePath);
        assertEquals(cleanedNodeFromDb.nodeName(), nodeName);
        assertEquals(cleanedNodeFromDb.binLength(), binLength);
        assertTrue(cleanedNodeFromDb.sha1().startsWith(sha1));
        assertEquals(cleanedNodeFromDb.status(), status);
        assertEquals(cleanedNodeFromDb.message(), message);
    }

    @Test
    public void testInsertRecordWhenPathIsEmpty() throws SQLException {
        String packageName = "package-name";
        String packageVersion = "package-version";
        String repo = "repo";
        String nodePath = EMPTY_STR;
        String nodeName = "node-name";
        long binLength = 2001;
        String sha1 = "sha1";
        int status = DeleteArtifactStatus.DELETED.getCode();
        String message = "message";
        boolean lead = true;
        CleanedNode cleanedNode = new CleanedNode(POLICY_KEY, policyRunId, packageName, packageVersion, lead, repo,
                nodePath, nodeName, binLength, sha1, status, message, null);
        // when
        nodeCleanedDao.insertCleanedNode(cleanedNode);

        List<CleanedNode> cleanedNodes = nodeCleanedDao.getAll();
        assertEquals(cleanedNodes.size(), 1);

        CleanedNode cleanedNodeFromDb = cleanedNodes.get(0);
        assertEquals(cleanedNodeFromDb.policyKey(), POLICY_KEY);
        assertEquals(cleanedNodeFromDb.policyRunId(), policyRunId);
        assertEquals(cleanedNodeFromDb.packageName(), packageName);
        assertEquals(cleanedNodeFromDb.packageVersion(), packageVersion);
        assertEquals(cleanedNodeFromDb.lead(), lead);
        assertEquals(cleanedNodeFromDb.repo(), repo);
        assertEquals(cleanedNodeFromDb.nodePath(), EMPTY_WITH_DOT);
        assertEquals(cleanedNodeFromDb.nodeName(), nodeName);
        assertEquals(cleanedNodeFromDb.binLength(), binLength);
        assertTrue(cleanedNodeFromDb.sha1().startsWith(sha1));
        assertEquals(cleanedNodeFromDb.status(), status);
        assertEquals(cleanedNodeFromDb.message(), message);
    }

    @Test
    public void testDeleteRecordsByPolicyKeyAndRunWhenPathIsEmpty() throws SQLException {
        String policy1 = "policy-key1";
        int policy1RunId1 = quartzTaskHistoryDao.insert(createRecord(policy1));

        cleanupRunSummaryDao.insert(new CleanupRunSummary(policy1, policy1RunId1, false, 0,
                0, CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false,
                0, 0, 0, 0, 0,
                0, "username", 0, 0, null));

        // given
        CleanedNode policy1Run1CleanedNode1 = new CleanedNode(policy1, policy1RunId1, "pname",
                "pversion", true, "repo", EMPTY_STR, "nodename", 1,
                "sha11", DeleteArtifactStatus.DELETED.getCode(),
                null, null);
        nodeCleanedDao.insertCleanedNode(policy1Run1CleanedNode1);

        CleanedNode policy1Run1CleanedNode2 = new CleanedNode(policy1, policy1RunId1, "pname-a",
                "pversion", true, "repo", EMPTY_STR, "nodename", 1,
                "sha11", DeleteArtifactStatus.DELETED.getCode(),
                null, null);
        nodeCleanedDao.insertCleanedNode(policy1Run1CleanedNode2);

        assertEquals(nodeCleanedDao.getAll().size(), 2);

        nodeCleanedDao.deleteByPolicyKeyAndRun(policy1, policy1RunId1);
        assertEquals(nodeCleanedDao.getAll().size(), 0);
    }

    @Test
    public void testDeleteRecordsByPolicyKeyAndRun() throws SQLException {
        String policy1 = "policy-key1";
        String policy2 = "policy-key2";
        int policy1RunId1 = quartzTaskHistoryDao.insert(createRecord(policy1));
        int policy1RunId2 = quartzTaskHistoryDao.insert(createRecord(policy1));
        int policy2RunId1 = quartzTaskHistoryDao.insert(createRecord(policy2));
        int policy2RunId2 = quartzTaskHistoryDao.insert(createRecord(policy2));

        cleanupRunSummaryDao.insert(new CleanupRunSummary(policy1, policy1RunId1, false, 0, 0,
                CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false, 0, 0, 0, 0, 0, 0,
                "username", 0, 0, null));
        cleanupRunSummaryDao.insert(new CleanupRunSummary(policy1, policy1RunId2, false, 0, 0,
                CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false, 0, 0, 0, 0, 0, 0,
                "username", 0, 0, null));
        cleanupRunSummaryDao.insert(new CleanupRunSummary(policy2, policy2RunId1, false, 0, 0,
                CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false, 0, 0, 0, 0, 0, 0,
                "username", 0, 0, null));
        cleanupRunSummaryDao.insert(new CleanupRunSummary(policy2, policy2RunId2, false, 0, 0,
                CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false, 0, 0, 0, 0, 0, 0,
                "username", 0, 0, null));

        // given
        CleanedNode policy1Run1CleanedNode = new CleanedNode(policy1, policy1RunId1, "pname",
                "pversion", true, "repo", "nodepath", "nodename", 1, "sha11", DeleteArtifactStatus.DELETED.getCode(),
                null, null);
        nodeCleanedDao.insertCleanedNode(policy1Run1CleanedNode);

        CleanedNode policy1Run2CleanedNode = new CleanedNode(policy1, policy1RunId2, "pname",
                "pversion", false, "repo", "nodepath", "nodename", 1, "sha12", DeleteArtifactStatus.DELETED.getCode(),
                null, null);
        nodeCleanedDao.insertCleanedNode(policy1Run2CleanedNode);

        CleanedNode policy2Run1CleanedNode = new CleanedNode(policy2, policy2RunId1, "pname",
                "pversion", false, "repo", "nodepath", "nodename", 1, "sha21", DeleteArtifactStatus.DELETED.getCode(),
                null, null);
        nodeCleanedDao.insertCleanedNode(policy2Run1CleanedNode);

        CleanedNode policy2Run2CleanedNode = new CleanedNode(policy2, policy2RunId2, "pname",
                "pversion", false, "repo", "nodepath", "nodename", 1, "sha22", DeleteArtifactStatus.DELETED.getCode(),
                null, null);
        nodeCleanedDao.insertCleanedNode(policy2Run2CleanedNode);

        // when
        nodeCleanedDao.deleteByPolicyKeyAndRun(policy1, policy1RunId1);

        // then
        assertEquals(nodeCleanedDao.getAll().size(), 3);
    }

    /**
     * Estimate cleaned binary length when there are no records in nodes_cleaned table
     */
    @Test
    public void testEstimateCleanedBinLengthWithoutNodesAndCleanedNodes() throws SQLException {
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);
        assertEquals(estimatedLength, 0);
    }

    /**
     * Estimate processed binary length when there are no records in nodes_cleaned table
     */
    @Test
    public void testEstimateProcessedBinLengthWithoutNodesAndCleanedNodes() throws SQLException {
        long estimatedLength =
                nodeCleanedDao.estimateActualProcessedBinLength(POLICY_KEY, policyRunId, DeleteArtifactStatus.ARCHIVED);
        assertEquals(estimatedLength, 0);
    }

    /**
     * Estimate processed binary length when there are no records in nodes_cleaned table
     */
    @Test
    public void testEstimateProcessedRestoredBinLengthWithoutNodesAndCleanedNodes() throws SQLException {
        long estimatedLength =
                nodeCleanedDao.estimateActualProcessedBinLengthByStatus(POLICY_KEY, policyRunId,
                        DeleteArtifactStatus.RESTORED);
        assertEquals(estimatedLength, 0);
    }

    /**
     * There are nodes in trashcan, but no cleaned nodes
     */
    @Test
    public void testEstimateCleanedBinLengthNoCleanedNodes() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNodeInTrashcan(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, 0);
    }

    /**
     * There are nodes in trashcan, but no processed nodes
     */
    @Test
    public void testEstimateProcessedBinLengthNoCleanedNodes() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNodeInTrashcan(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);

        // when
        long estimatedLength =
                nodeCleanedDao.estimateActualProcessedBinLength(POLICY_KEY, policyRunId, DeleteArtifactStatus.ARCHIVED);

        // then
        assertEquals(estimatedLength, 0);
    }

    /**
     * Cleanup policy cleans artifact partially, means artifacts with the same SHA are still present in the system
     */
    @Test
    public void testEstimateCleanedBinLengthShaPartiallyCleaned() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNode(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);
        nodesDao.create(createNodeInTrashcan(sha1, binLength));
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, 0);
    }

    /**
     * Deleted policy cleans artifact partially, means artifacts with the same SHA are still present in the system,
     * no cleaned records
     */
    @Test
    public void testEstimateCleanedBinLengthShaPartiallyDeletedNoCleanedRecords() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNode(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);
        nodesDao.create(createNodeInTrashcan(sha1, binLength));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, 0);
    }

    /**
     * Cleanup policy cleans an artifact with SHA completely
     */
    @Test
    public void testEstimateCleanedBinLengthShaCompletelyCleaned() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNodeInTrashcan(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, binLength);
    }

    /**
     * policy processed an artifact with SHA completely
     */
    @Test
    public void testEstimateProcessedBinLengthShaCompletelyCleaned() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNodeInTrashcan(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);
        nodeCleanedDao.insertCleanedNode(createSuccessfullyProcessedNode(sha1, binLength));

        // when
        long estimatedLength =
                nodeCleanedDao.estimateActualProcessedBinLength(POLICY_KEY, policyRunId, DeleteArtifactStatus.ARCHIVED);

        // then
        assertEquals(estimatedLength, binLength);
    }

    @Test
    public void testEstimateProcessedRestoredBinLengthShaCompletelyCleaned() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNodeInTrashcan(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);
        nodeCleanedDao.insertCleanedNode(createSuccessfullyProcessedRestoredNode(sha1, binLength));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualProcessedBinLengthByStatus(POLICY_KEY, policyRunId,
                DeleteArtifactStatus.RESTORED);

        // then
        assertEquals(estimatedLength, binLength);
    }

    /**
     * Cleanup policy cleans an artifact with SHA completely with skipped trashcan
     */
    @Test
    public void testEstimateCleanedBinLengthShaCompletelyCleanedWithSkippedTrashcan() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, binLength);
    }

    /**
     * Cleanup policy cleans artifacts completely with the same SHA
     */
    @Test
    public void testEstimateCleanedBinLengthShaCompletelyCleanedTwice() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node1 = createNodeInTrashcan(sha1, binLength);
        Node node2 = createNodeInTrashcan(sha1, binLength);
        binariesDao.create(node1.getSha1Actual(), node1.getSha2(), node1.getMd5Actual(), binLength);
        nodesDao.create(node1);
        nodesDao.create(node2);

        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength));
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, binLength);
    }

    @Test
    public void testEstimateCleanedBinLengthShaSuccessfullyCleanedAndWithError() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node1 = createNodeInTrashcan(sha1, binLength);
        Node node2 = createNodeInTrashcan(sha1, binLength);
        Node node3 = createNode(sha1, binLength);
        binariesDao.create(node1.getSha1Actual(), node1.getSha2(), node1.getMd5Actual(), binLength);
        nodesDao.create(node1);
        nodesDao.create(node2);
        nodesDao.create(node3);

        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength));
        nodeCleanedDao.insertCleanedNode(createCleanedNodeWithError(sha1, binLength));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, 0);
    }

    @Test
    public void testEstimateCleanedBinLengthShaCleanedWithError() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNode(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);

        nodeCleanedDao.insertCleanedNode(createCleanedNodeWithError(sha1, binLength));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, 0);
    }

    @Test
    public void testCalculateCleanupRunTotalBinSize() throws SQLException {
        createNodeCleanedDbRecords("sha1_1", 1000, true, DeleteArtifactStatus.DELETED.getCode());
        createNodeCleanedDbRecords("sha1_2", 2000, false, DeleteArtifactStatus.DELETED.getCode());
        createNodeCleanedDbRecords("sha1_3", 3000, true, DeleteArtifactStatus.DELETED.getCode());

        long totalBinSize = nodeCleanedDao.calculateCleanupRunTotalBinSize(POLICY_KEY, policyRunId);
        assertEquals(totalBinSize, 4000);
    }

    @Test
    public void testCalculateVirtualBinarySize() throws SQLException {
        createNodeCleanedDbRecords("sha2_1", 1001, true, DeleteArtifactStatus.ARCHIVED.getCode());
        createNodeCleanedDbRecords("sha2_2", 2001, false, DeleteArtifactStatus.ARCHIVED.getCode());
        createNodeCleanedDbRecords("sha2_3", 3001, true, DeleteArtifactStatus.ARCHIVED.getCode());

        long totalBinSizeForArchive =
                nodeCleanedDao.calculateVirtualBinarySize(POLICY_KEY, policyRunId, DeleteArtifactStatus.ARCHIVED);

        assertEquals(totalBinSizeForArchive, 4002);
    }
    @Test
    public void testMixOfCleanedNodes() throws SQLException {
        // given
        // only in trash can, cleaned totally
        String sha1 = "cleanup-sha-1";
        int binLength1 = 1;
        // exists in trashcan and in a repo
        String sha2 = "cleanup-sha-2";
        int binLength2 = 12;
        // does not exist in system at all, only in the cleaned table
        String sha3 = "cleanup-sha-3";
        int binLength3 = 103;
        // exists only in repo, does not exist in cleaned node table
        String sha4 = "cleanup-sha-4";
        int binLength4 = 1004;
        // exist in a repo and in trashcan, deleted successfully and with an error
        String sha5 = "cleanup-sha-5";
        int binLength5 = 10005;
        // cleaned successfully, 2 times
        String sha6 = "cleanup-sha-6";
        int binLength6 = 100006;
        // cleaned only with error, still present in a repo
        String sha7 = "cleanup-sha-7";
        int binLength7 = 1000007;

        Node node1 = createNodeInTrashcan(sha1, binLength1);
        Node node2 = createNode(sha2, binLength2);
        Node node4 = createNode(sha4, binLength4);
        Node node5 = createNode(sha5, binLength5);
        Node node6 = createNode(sha6, binLength6);
        Node node7 = createNode(sha7, binLength7);

        binariesDao.create(node1.getSha1Actual(), node1.getSha2(), node1.getMd5Actual(), binLength1);
        binariesDao.create(node2.getSha1Actual(), node2.getSha2(), node2.getMd5Actual(), binLength2);
        binariesDao.create(node4.getSha1Actual(), node4.getSha2(), node4.getMd5Actual(), binLength4);
        binariesDao.create(node5.getSha1Actual(), node5.getSha2(), node5.getMd5Actual(), binLength5);
        binariesDao.create(node6.getSha1Actual(), node6.getSha2(), node6.getMd5Actual(), binLength6);
        binariesDao.create(node7.getSha1Actual(), node7.getSha2(), node7.getMd5Actual(), binLength7);

        nodesDao.create(node1);
        nodesDao.create(node2);
        nodesDao.create(createNodeInTrashcan(sha2, binLength2));
        nodesDao.create(node4);
        nodesDao.create(node5);
        nodesDao.create(createNodeInTrashcan(sha5, binLength5));
        nodesDao.create(createNodeInTrashcan(sha6, binLength6));
        nodesDao.create(createNodeInTrashcan(sha6, binLength6));
        nodesDao.create(node7);

        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength1));
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha2, binLength2));
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha3, binLength3));
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha5, binLength5));
        nodeCleanedDao.insertCleanedNode(createCleanedNodeWithError(sha5, binLength5));
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha6, binLength6));
        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha6, binLength6));
        nodeCleanedDao.insertCleanedNode(createCleanedNodeWithError(sha7, binLength7));

        // when
        long estimatedLength = nodeCleanedDao.estimateActualCleanedBinLength(POLICY_KEY, policyRunId);

        // then
        assertEquals(estimatedLength, binLength1 + binLength3 + binLength6);
    }

    @Test
    public void testTruncateAll() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNode(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);

        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength));
        nodeCleanedDao.insertCleanedNode(createCleanedNodeWithError(sha1, binLength));

        assertEquals(nodeCleanedDao.countAll(), 2);

        // when
        nodeCleanedDao.truncateAll();

        // then
        assertEquals(nodeCleanedDao.countAll(), 0);
    }

    @Test
    public void testCountAll() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        int binLength = 100;
        Node node = createNode(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);

        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength));
        nodeCleanedDao.insertCleanedNode(createCleanedNodeWithError(sha1, binLength));

        // when
        int count = nodeCleanedDao.countAll();

        // then
        assertEquals(count, 2);
    }

    @Test
    public void testGetAll() throws SQLException {
        // given
        String sha1 = "cleanup-sha-1";
        String sha2 = "cleanup-sha-2";
        int binLength1 = 100;
        int binLength2 = 100;
        Node node1 = createNode(sha1, binLength1);
        Node node2 = createNode(sha2, binLength2);
        binariesDao.create(node1.getSha1Actual(), node1.getSha2(), node1.getMd5Actual(), binLength1);
        binariesDao.create(node2.getSha1Actual(), node2.getSha2(), node2.getMd5Actual(), binLength2);
        nodesDao.create(node1);
        nodesDao.create(node2);

        nodeCleanedDao.insertCleanedNode(createSuccessfullyCleanedNode(sha1, binLength1));
        nodeCleanedDao.insertCleanedNode(createCleanedNodeWithError(sha2, binLength2));

        // when
        List<CleanedNode> allCleanedNodes = nodeCleanedDao.getAll();

        // then
        assertEquals(allCleanedNodes.size(), 2);

        CleanedNode cleanedNode1 = allCleanedNodes.stream()
                .filter(cn -> cn.sha1().startsWith(sha1))
                .findFirst()
                .orElseThrow();

        assertTrue(cleanedNode1.sha1().startsWith(sha1));
        assertEquals(cleanedNode1.repo(), REPO_KEY);
        assertEquals(cleanedNode1.nodeName(), "nname");
        assertEquals(cleanedNode1.nodePath(), "npath");
        assertEquals(cleanedNode1.packageVersion(), "pversion");
        assertEquals(cleanedNode1.packageName(), "pname");
        assertEquals(cleanedNode1.binLength(), binLength1);
        assertEquals(cleanedNode1.status(), DeleteArtifactStatus.DELETED.getCode());

        CleanedNode cleanedNode2 = allCleanedNodes.stream()
                .filter(cn -> cn.sha1().startsWith(sha2))
                .findFirst()
                .orElseThrow();

        assertTrue(cleanedNode2.sha1().startsWith(sha2));
        assertEquals(cleanedNode2.repo(), REPO_KEY);
        assertEquals(cleanedNode2.nodeName(), "nname");
        assertEquals(cleanedNode2.nodePath(), "npath");
        assertEquals(cleanedNode2.packageVersion(), "pversion");
        assertEquals(cleanedNode2.packageName(), "pname");
        assertEquals(cleanedNode2.binLength(), binLength2);
        assertEquals(cleanedNode2.status(), DeleteArtifactStatus.ERROR.getCode());
    }

    @Test
    public void testGetDeletedNodesRecords() throws SQLException {
        String policy1 = "policy-key1";
        int policy1RunId1 = quartzTaskHistoryDao.insert(createRecord(policy1));
        cleanupRunSummaryDao.insert(new CleanupRunSummary(policy1, policy1RunId1, false, 0, 0,
                CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false, 0, 0, 0, 0, 0, 0,
                "username", 0, 0, null));

        CleanedNode policy1Run1CleanedNode1 = new CleanedNode(policy1, policy1RunId1, "pname",
                "pversion", true, "repo", "nodepath", "nodename", 1, "sha11", DeleteArtifactStatus.DELETED.getCode(),
                null, null);
        CleanedNode policy1Run1CleanedNode2 = new CleanedNode(policy1, policy1RunId1, "pname",
                "pversion", false, "repo", "nodepath", "nodename", 1, "sha12", DeleteArtifactStatus.DELETED.getCode(),
                null, null);

        nodeCleanedDao.insertCleanedNode(policy1Run1CleanedNode1);
        nodeCleanedDao.insertCleanedNode(policy1Run1CleanedNode2);

        assertEquals(nodeCleanedDao.getDeletedNodesRecords(policy1, policy1RunId1).size(), 1);
    }

    @Test
    public void testGetCleanedNodesByRun() throws SQLException {
        String policy1 = "policy-key1";
        int policy1RunId1 = quartzTaskHistoryDao.insert(createRecord(policy1));
        cleanupRunSummaryDao.insert(new CleanupRunSummary(policy1, policy1RunId1, false, 0, 0,
                CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false, 0, 0, 0, 0, 0, 0,
                "username", 0, 0, null));

        CleanedNode policy1Run1CleanedNode1 = new CleanedNode(policy1, policy1RunId1, "pname",
                "pversion", true, "repo", "nodepath", "nodename", 1, "sha11", DeleteArtifactStatus.DELETED.getCode(),
                null, null);
        CleanedNode policy1Run1CleanedNode2 = new CleanedNode(policy1, policy1RunId1, "pname",
                "pversion", false, "repo", "nodepath", "nodename", 1, "sha12", DeleteArtifactStatus.DELETED.getCode(),
                null, null);

        nodeCleanedDao.insertCleanedNode(policy1Run1CleanedNode1);
        nodeCleanedDao.insertCleanedNode(policy1Run1CleanedNode2);

        assertEquals(nodeCleanedDao.getCleanedNodesByRun(policy1, policy1RunId1).size(), 2);
    }

    @Test
    public void testGetCleanedNodesByStatus() throws SQLException {
        String policy1 = "policy-key1";
        int policy1RunId1 = quartzTaskHistoryDao.insert(createRecord(policy1));
        CleanedNode node1 = CleanedNode.builder()
                .policyKey(policy1)
                .policyRunId(policy1RunId1)
                .packageName("pname")
                .packageVersion("pversion")
                .lead(true)
                .repo("repo")
                .nodePath(EMPTY_STR)
                .nodeName("nodename")
                .binLength(1)
                .sha1("sha11")
                .status(DeleteArtifactStatus.RESTORE_NOT_INITIALIZED.getCode())
                .build();

        CleanedNode node2 = node1.toBuilder().status(DeleteArtifactStatus.RESTORED.getCode()).build();


        nodeCleanedDao.insertCleanedNode(node1);
        nodeCleanedDao.insertCleanedNode(node2);

        List<CleanedNode> nodes =
                nodeCleanedDao.getCleanedNodesByStatus(List.of(DeleteArtifactStatus.RESTORE_NOT_INITIALIZED.getCode()),
                        1000, 0);
        assertEquals(nodes.size(), 1);
        assertEquals(nodes.getFirst().status(), DeleteArtifactStatus.RESTORE_NOT_INITIALIZED.getCode());
    }

    @Test
    public void testUpdateCleanedNode() throws SQLException {
        String policy1 = "policy-key-1";
        int policy1RunId1 = quartzTaskHistoryDao.insert(createRecord(policy1));

        CleanedNode initialNode = CleanedNode.builder()
                .policyKey(policy1)
                .policyRunId(policy1RunId1)
                .packageName("pname")
                .packageVersion("pversion")
                .lead(true)
                .repo("repo")
                .nodePath(EMPTY_STR)
                .nodeName("nodename")
                .binLength(1)
                .sha1("sha1")
                .status(DeleteArtifactStatus.PENDING_WARM.getCode())
                .build();
        nodeCleanedDao.insertCleanedNode(initialNode);

        assertEquals(nodeCleanedDao.getAll().size(), 1);

        nodeCleanedDao.updateCleanedNodeBySha(policy1, policy1RunId1, DeleteArtifactStatus.ERROR.getCode(),
                List.of("sha1"));

        List<CleanedNode> cleanedNodes = nodeCleanedDao.getCleanedNodesByRun(policy1, policy1RunId1);
        assertEquals(cleanedNodes.size(), 1);
    }

    private void createNodeCleanedDbRecords(String sha1, int binLength, boolean success, int status)
            throws SQLException {
        Node node = createNode(sha1, binLength);
        binariesDao.create(node.getSha1Actual(), node.getSha2(), node.getMd5Actual(), binLength);
        nodesDao.create(node);

        CleanedNode cleanedNode;
        if (status == DeleteArtifactStatus.DELETED.getCode()) {
            cleanedNode = success ? createSuccessfullyCleanedNode(sha1, binLength) :
                    createCleanedNodeWithError(sha1, binLength);
        } else { // for archived nodes cleaned entry
            cleanedNode = success ? createSuccessfullyArchivedCleanedNode(sha1, binLength) :
                    createArchivedCleanedNodeWithError(sha1, binLength);
        }
        nodeCleanedDao.insertCleanedNode(cleanedNode);
    }

    private QuartzTaskHistoryRecord createRecord(String externalId) {
        return QuartzTaskHistoryRecord.builder()
                .externalId(externalId)
                .taskType("cleaned")
                .status(IN_PROGRESS)
                .statusMessage("statusMsg")
                .taskId("taskId")
                .serverId("serverId")
                .username("username")
                .build();
    }

    private CleanedNode createSuccessfullyCleanedNode(String sha1, long binLength) {
        return createCleanedNode(sha1, binLength, true);
    }

    private CleanedNode createSuccessfullyArchivedCleanedNode(String sha1, long binLength) {
        return createArchivedCleanedNode(sha1, binLength, true);
    }

    private CleanedNode createArchivedCleanedNodeWithError(String sha1, long binLength) {
        return createArchivedCleanedNode(sha1, binLength, false);
    }

    private CleanedNode createSuccessfullyProcessedNode(String sha1, long binLength) {
        return createProcessedNode(sha1, binLength, DeleteArtifactStatus.ARCHIVED);
    }

    private CleanedNode createSuccessfullyProcessedRestoredNode(String sha1, long binLength) {
        return createProcessedNode(sha1, binLength, DeleteArtifactStatus.RESTORED);
    }

    private CleanedNode createCleanedNodeWithError(String sha1, long binLength) {
        return createCleanedNode(sha1, binLength, false);
    }

    private CleanedNode createCleanedNode(String sha1, long binLength, boolean deleted) {
        return new CleanedNode(POLICY_KEY, policyRunId, "pname", "pversion", true, REPO_KEY,
                "npath", "nname", binLength, sha1,
                deleted ? DeleteArtifactStatus.DELETED.getCode() : DeleteArtifactStatus.ERROR.getCode(),
                null, null);
    }

    private CleanedNode createArchivedCleanedNode(String sha1, long binLength, boolean archived) {
        return CleanedNode.builder()
                .policyKey(POLICY_KEY)
                .policyRunId(policyRunId)
                .packageName("pname")
                .packageVersion("pversion")
                .lead(true)
                .repo(REPO_KEY)
                .nodePath("npath")
                .nodeName("nname")
                .binLength(binLength)
                .sha1(sha1)
                .status(archived ? DeleteArtifactStatus.ARCHIVED.getCode() : DeleteArtifactStatus.ERROR.getCode())
                .build();
    }

    private CleanedNode createProcessedNode(String sha1, long binLength, DeleteArtifactStatus status) {
        return new CleanedNode(POLICY_KEY, policyRunId, "pname", "pversion", true, REPO_KEY,
                "npath", "nname", binLength, sha1, status.getCode(),
                null, null);
    }

    private void deleteBinariesAndNodes(String repoKey) throws SQLException {
        NodePath nodePath = new NodePath(repoKey, "", "", false);
        List<Node> nodes = nodesDao.getChildren(nodePath);
        List<Long> nodeIds = nodes.stream().map(Node::getNodeId).toList();
        nodesDao.deleteBulk(nodeIds);
        nodes.forEach(node -> binariesDao.deleteEntry(node.getSha1Actual()));
    }

    @Test
    public void testInsertAndRetrieveNodeWithMetadata() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode cleanedNode = new CleanedNode(POLICY_KEY, policyRunId, "pname", "pversion", 
                true, REPO_KEY, "npath", "nname", 100L, "sha1", 
                DeleteArtifactStatus.DELETED.getCode(), "message", metadata);

        nodeCleanedDao.insertCleanedNode(cleanedNode);

        List<CleanedNode> allNodes = nodeCleanedDao.getAll();
        assertEquals(allNodes.size(), 1);

        CleanedNode retrievedNode = allNodes.get(0);
        assertEquals(retrievedNode.metadata().created(), 1000L);
        assertEquals(retrievedNode.metadata().modified(), 2000L);
        assertEquals(retrievedNode.metadata().lastDownloaded(), 3000L);
    }

    @Test
    public void testInsertAndRetrieveNodeWithNullMetadata() throws SQLException {
        CleanedNode cleanedNode = new CleanedNode(POLICY_KEY, policyRunId, "pname", "pversion", 
                true, REPO_KEY, "npath", "nname", 100L, "sha1", 
                DeleteArtifactStatus.DELETED.getCode(), "message", null);

        nodeCleanedDao.insertCleanedNode(cleanedNode);

        List<CleanedNode> allNodes = nodeCleanedDao.getAll();
        assertEquals(allNodes.size(), 1);

        CleanedNode retrievedNode = allNodes.get(0);
        assertEquals(retrievedNode.metadata(), null);
    }

    @Test
    public void testInsertAndRetrieveNodeWithPartialMetadata() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, null);
        CleanedNode cleanedNode = new CleanedNode(POLICY_KEY, policyRunId, "pname", "pversion", 
                true, REPO_KEY, "npath", "nname", 100L, "sha1", 
                DeleteArtifactStatus.DELETED.getCode(), "message", metadata);

        nodeCleanedDao.insertCleanedNode(cleanedNode);

        List<CleanedNode> allNodes = nodeCleanedDao.getAll();
        assertEquals(allNodes.size(), 1);

        CleanedNode retrievedNode = allNodes.get(0);
        assertEquals(retrievedNode.metadata().created(), 1000L);
        assertEquals(retrievedNode.metadata().modified(), 2000L);
        assertEquals(retrievedNode.metadata().lastDownloaded(), null);
    }

    @Test
    public void testGetLeadMetadataReturnsOnlyLeadNodes() throws SQLException {
        NodeMetadata metadata1 = new NodeMetadata(1000L, 2000L, 3000L);
        NodeMetadata metadata2 = new NodeMetadata(4000L, 5000L, 6000L);

        CleanedNode leadNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg1", "1.0", 
                true, REPO_KEY, "path1", "artifact.jar", 100L, "sha1", 
                DeleteArtifactStatus.DELETED.getCode(), null, metadata1);

        CleanedNode nonLeadNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg1", "1.0", 
                false, REPO_KEY, "path1", "other.jar", 50L, "sha2", 
                DeleteArtifactStatus.DELETED.getCode(), null, metadata2);

        nodeCleanedDao.insertCleanedNode(leadNode);
        nodeCleanedDao.insertCleanedNode(nonLeadNode);

        List<CleanedNode> leadMetadata = nodeCleanedDao.getLeadMetadata(POLICY_KEY, policyRunId);

        assertEquals(leadMetadata.size(), 1);
        assertEquals(leadMetadata.get(0).lead(), true);
        assertEquals(leadMetadata.get(0).packageName(), "pkg1");
        assertEquals(leadMetadata.get(0).metadata().created(), 1000L);
    }

    @Test
    public void testGetLeadMetadataReturnsEmptyListWhenNoLeadNodes() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode nonLeadNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg1", "1.0", 
                false, REPO_KEY, "path1", "artifact.jar", 100L, "sha1", 
                DeleteArtifactStatus.DELETED.getCode(), null, metadata);

        nodeCleanedDao.insertCleanedNode(nonLeadNode);

        List<CleanedNode> leadMetadata = nodeCleanedDao.getLeadMetadata(POLICY_KEY, policyRunId);

        assertEquals(leadMetadata.size(), 0);
    }

    @Test
    public void testGetLeadMetadataWithMultipleLeadNodes() throws SQLException {
        NodeMetadata metadata1 = new NodeMetadata(1000L, 2000L, 3000L);
        NodeMetadata metadata2 = new NodeMetadata(4000L, 5000L, 6000L);

        CleanedNode leadNode1 = new CleanedNode(POLICY_KEY, policyRunId, "pkg1", "1.0", 
                true, REPO_KEY, "path1", "artifact1.jar", 100L, "sha1", 
                DeleteArtifactStatus.DELETED.getCode(), null, metadata1);

        CleanedNode leadNode2 = new CleanedNode(POLICY_KEY, policyRunId, "pkg2", "2.0", 
                true, REPO_KEY, "path2", "artifact2.jar", 200L, "sha2", 
                DeleteArtifactStatus.ARCHIVED.getCode(), null, metadata2);

        nodeCleanedDao.insertCleanedNode(leadNode1);
        nodeCleanedDao.insertCleanedNode(leadNode2);

        List<CleanedNode> leadMetadata = nodeCleanedDao.getLeadMetadata(POLICY_KEY, policyRunId);

        assertEquals(leadMetadata.size(), 2);
        assertTrue(leadMetadata.stream().allMatch(CleanedNode::lead));
    }

    @Test
    public void testGetLeadMetadataFiltersByPolicyKeyAndRunId() throws SQLException {
        String policy2 = "policy-key-2";
        int policy2RunId = quartzTaskHistoryDao.insert(createRecord(policy2));
        cleanupRunSummaryDao.insert(new CleanupRunSummary(policy2, policy2RunId, false, 0, 0,
                CleanupRunSummary.Status.COMPLETED, CleanupRunSummary.Type.PACKAGE, false, 0, 0, 0, 0, 0, 0,
                "username", 0, 0, null));

        NodeMetadata metadata1 = new NodeMetadata(1000L, 2000L, 3000L);
        NodeMetadata metadata2 = new NodeMetadata(4000L, 5000L, 6000L);

        CleanedNode leadNode1 = new CleanedNode(POLICY_KEY, policyRunId, "pkg1", "1.0", 
                true, REPO_KEY, "path1", "artifact1.jar", 100L, "sha1", 
                DeleteArtifactStatus.DELETED.getCode(), null, metadata1);

        CleanedNode leadNode2 = new CleanedNode(policy2, policy2RunId, "pkg2", "2.0", 
                true, REPO_KEY, "path2", "artifact2.jar", 200L, "sha2", 
                DeleteArtifactStatus.DELETED.getCode(), null, metadata2);

        nodeCleanedDao.insertCleanedNode(leadNode1);
        nodeCleanedDao.insertCleanedNode(leadNode2);

        List<CleanedNode> leadMetadataPolicy1 = nodeCleanedDao.getLeadMetadata(POLICY_KEY, policyRunId);
        List<CleanedNode> leadMetadataPolicy2 = nodeCleanedDao.getLeadMetadata(policy2, policy2RunId);

        assertEquals(leadMetadataPolicy1.size(), 1);
        assertEquals(leadMetadataPolicy1.get(0).packageName(), "pkg1");

        assertEquals(leadMetadataPolicy2.size(), 1);
        assertEquals(leadMetadataPolicy2.get(0).packageName(), "pkg2");
    }

    @Test
    public void testMetadataWithZeroTimestamps() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(0L, 0L, 0L);
        CleanedNode cleanedNode = new CleanedNode(POLICY_KEY, policyRunId, "pname", "pversion",
                true, REPO_KEY, "npath", "nname", 100L, "sha1",
                DeleteArtifactStatus.DELETED.getCode(), "message", metadata);

        nodeCleanedDao.insertCleanedNode(cleanedNode);

        List<CleanedNode> allNodes = nodeCleanedDao.getAll();
        assertEquals(allNodes.size(), 1);

        CleanedNode retrievedNode = allNodes.get(0);
        assertEquals(retrievedNode.metadata().created(), 0L);
        assertEquals(retrievedNode.metadata().modified(), 0L);
        assertEquals(retrievedNode.metadata().lastDownloaded(), 0L);
    }

    @Test
    public void testMetadataWithLargeTimestamps() throws SQLException {
        long largeTimestamp = 9999999999999L;
        NodeMetadata metadata = new NodeMetadata(largeTimestamp, largeTimestamp, largeTimestamp);
        CleanedNode cleanedNode = new CleanedNode(POLICY_KEY, policyRunId, "pname", "pversion",
                true, REPO_KEY, "npath", "nname", 100L, "sha1",
                DeleteArtifactStatus.ARCHIVED.getCode(), "message", metadata);

        nodeCleanedDao.insertCleanedNode(cleanedNode);

        List<CleanedNode> allNodes = nodeCleanedDao.getAll();
        assertEquals(allNodes.size(), 1);

        CleanedNode retrievedNode = allNodes.get(0);
        assertEquals(retrievedNode.metadata().created(), largeTimestamp);
        assertEquals(retrievedNode.metadata().modified(), largeTimestamp);
        assertEquals(retrievedNode.metadata().lastDownloaded(), largeTimestamp);
    }

    @Test
    public void testGetLeadMetadataWithSamePackageKey() throws SQLException {
        NodeMetadata metadata1 = new NodeMetadata(1000L, 2000L, 3000L);
        NodeMetadata metadata2 = new NodeMetadata(4000L, 5000L, 6000L);

        CleanedNode leadNode1 = new CleanedNode(POLICY_KEY, policyRunId, "pkg1", "1.0",
                true, "repo1", "path1", "artifact1.jar", 100L, "sha1",
                DeleteArtifactStatus.DELETED.getCode(), null, metadata1);

        CleanedNode leadNode2 = new CleanedNode(POLICY_KEY, policyRunId, "pkg1", "1.0",
                true, "repo2", "path2", "artifact2.jar", 200L, "sha2",
                DeleteArtifactStatus.DELETED.getCode(), null, metadata2);

        nodeCleanedDao.insertCleanedNode(leadNode1);
        nodeCleanedDao.insertCleanedNode(leadNode2);

        List<CleanedNode> leadMetadata = nodeCleanedDao.getLeadMetadata(POLICY_KEY, policyRunId);

        assertEquals(leadMetadata.size(), 2);
        assertTrue(leadMetadata.stream().allMatch(n -> n.packageName().equals("pkg1")));
        assertTrue(leadMetadata.stream().allMatch(n -> n.packageVersion().equals("1.0")));
    }

    @Test
    public void testGetFirstErrorMessageForRun_errorRowExists_returnsMessage() throws SQLException {
        CleanedNode errorNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg", "1.0",
                false, REPO_KEY, "path", "artifact.jar", 100L, "sha1error",
                DeleteArtifactStatus.ERROR.getCode(), "S3 connection timeout", null);
        nodeCleanedDao.insertCleanedNode(errorNode);

        String message = nodeCleanedDao.getFirstErrorMessageForRun(POLICY_KEY, policyRunId);

        assertEquals("S3 connection timeout", message);
    }

    @Test
    public void testGetFirstErrorMessageForRun_skippedRowExists_returnsMessage() throws SQLException {
        CleanedNode skippedNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg", "1.0",
                true, REPO_KEY, "path", "artifact.jar", 100L, "sha1skip",
                DeleteArtifactStatus.SKIPPED.getCode(), "Plugin blocked deletion", null);
        nodeCleanedDao.insertCleanedNode(skippedNode);

        String message = nodeCleanedDao.getFirstErrorMessageForRun(POLICY_KEY, policyRunId);

        assertEquals("Plugin blocked deletion", message);
    }

    @Test
    public void testGetFirstErrorMessageForRun_errorPreferredOverSkipped() throws SQLException {
        CleanedNode skippedNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg", "1.0",
                true, REPO_KEY, "path", "lead.jar", 100L, "sha1skip",
                DeleteArtifactStatus.SKIPPED.getCode(), "Lead skipped", null);
        CleanedNode errorNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg", "1.0",
                false, REPO_KEY, "path", "artifact.jar", 50L, "sha1err",
                DeleteArtifactStatus.ERROR.getCode(), "Delete failed: permission denied", null);
        nodeCleanedDao.insertCleanedNode(skippedNode);
        nodeCleanedDao.insertCleanedNode(errorNode);

        String message = nodeCleanedDao.getFirstErrorMessageForRun(POLICY_KEY, policyRunId);

        assertEquals("Delete failed: permission denied", message);
    }

    @Test
    public void testGetFirstErrorMessageForRun_onlyDeletedRows_returnsNull() throws SQLException {
        CleanedNode deletedNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg", "1.0",
                true, REPO_KEY, "path", "artifact.jar", 100L, "sha1del",
                DeleteArtifactStatus.DELETED.getCode(), "Deleted permanently", null);
        nodeCleanedDao.insertCleanedNode(deletedNode);

        String message = nodeCleanedDao.getFirstErrorMessageForRun(POLICY_KEY, policyRunId);

        assertNull(message);
    }

    @Test
    public void testGetFirstErrorMessageForRun_noRows_returnsNull() throws SQLException {
        String message = nodeCleanedDao.getFirstErrorMessageForRun(POLICY_KEY, policyRunId);

        assertNull(message);
    }

    @Test
    public void testGetFirstErrorMessageForRun_differentRunId_returnsNull() throws SQLException {
        CleanedNode errorNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg", "1.0",
                false, REPO_KEY, "path", "artifact.jar", 100L, "sha1err",
                DeleteArtifactStatus.ERROR.getCode(), "error message", null);
        nodeCleanedDao.insertCleanedNode(errorNode);

        String message = nodeCleanedDao.getFirstErrorMessageForRun(POLICY_KEY, policyRunId + 999);

        assertNull(message);
    }

    @Test
    public void testGetFirstErrorMessageForRun_nullMessageOnErrorRow_returnsNull() throws SQLException {
        CleanedNode errorNode = new CleanedNode(POLICY_KEY, policyRunId, "pkg", "1.0",
                false, REPO_KEY, "path", "artifact.jar", 100L, "sha1null",
                DeleteArtifactStatus.ERROR.getCode(), null, null);
        nodeCleanedDao.insertCleanedNode(errorNode);

        String message = nodeCleanedDao.getFirstErrorMessageForRun(POLICY_KEY, policyRunId);

        assertNull(message);
    }

    @Test
    public void testGetLeadMetadataWithMixedStatusTypes() throws SQLException {
        NodeMetadata metadata1 = new NodeMetadata(1000L, 2000L, 3000L);
        NodeMetadata metadata2 = new NodeMetadata(4000L, 5000L, null);
        NodeMetadata metadata3 = new NodeMetadata(7000L, 8000L, 9000L);

        CleanedNode leadNode1 = new CleanedNode(POLICY_KEY, policyRunId, "pkg1", "1.0",
                true, REPO_KEY, "path1", "artifact1.jar", 100L, "sha1",
                DeleteArtifactStatus.DELETED.getCode(), null, metadata1);

        CleanedNode leadNode2 = new CleanedNode(POLICY_KEY, policyRunId, "pkg2", "2.0",
                true, REPO_KEY, "path2", "artifact2.jar", 200L, "sha2",
                DeleteArtifactStatus.ARCHIVED.getCode(), null, metadata2);

        CleanedNode leadNode3 = new CleanedNode(POLICY_KEY, policyRunId, "pkg3", "3.0",
                true, REPO_KEY, "path3", "artifact3.jar", 300L, "sha3",
                DeleteArtifactStatus.RESTORED.getCode(), null, metadata3);

        nodeCleanedDao.insertCleanedNode(leadNode1);
        nodeCleanedDao.insertCleanedNode(leadNode2);
        nodeCleanedDao.insertCleanedNode(leadNode3);

        List<CleanedNode> leadMetadata = nodeCleanedDao.getLeadMetadata(POLICY_KEY, policyRunId);

        assertEquals(leadMetadata.size(), 3);
        assertTrue(leadMetadata.stream().allMatch(CleanedNode::lead));
        assertTrue(leadMetadata.stream().anyMatch(n -> n.status() == DeleteArtifactStatus.DELETED.getCode()));
        assertTrue(leadMetadata.stream().anyMatch(n -> n.status() == DeleteArtifactStatus.ARCHIVED.getCode()));
        assertTrue(leadMetadata.stream().anyMatch(n -> n.status() == DeleteArtifactStatus.RESTORED.getCode()));
    }
}