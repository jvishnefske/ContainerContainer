package org.artifactory.storage.db.policy.cleanup.service;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.policy.NodeMetadata;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.cleanup.CleanedNode;
import org.artifactory.storage.db.policy.cleanup.CleanedPackage;
import org.artifactory.storage.db.policy.cleanup.DeleteArtifactStatus;
import org.artifactory.storage.db.policy.cleanup.dao.NodeCleanedDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class NodeCleanedStorageServiceImplTest {
    @Mock
    private NodeCleanedDao nodeCleanedDao;
    @Mock
    private ArtifactoryContext context;
    @InjectMocks
    private NodeCleanedStorageServiceImpl nodesCleanedStorageService;
    private MockitoSession mockitoSession;

    @BeforeEach
    void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        mockitoSession.finishMocking();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void insertCleanedNodeSuccessfully() throws SQLException {
        when(nodeCleanedDao.insertCleanedNode(any())).thenReturn(1);
        CleanedNode node = new CleanedNode("pkey", 1, "pname", "pversion", false, "repo", "npath", "nname", 2L, "sha1",
                DeleteArtifactStatus.DELETED.getCode(), "message", null);
        assertDoesNotThrow(() -> nodesCleanedStorageService.insertCleanedNode(node));
    }

    @Test
    void updateCleanedNodeSuccessfully() throws SQLException {
        doNothing().when(nodeCleanedDao).updateCleanedNodeBySha(any(), anyInt(), anyInt(), any());
        assertDoesNotThrow(() -> nodesCleanedStorageService.updateCleanedNodeBySha("pkey", 1,
                DeleteArtifactStatus.DELETED.getCode(), List.of("sha1")));
    }

    @Test
    void updateCleanedNodeFailed() throws SQLException {
        doThrow(SQLException.class).when(nodeCleanedDao).updateCleanedNodeBySha(any(), anyInt(), anyInt(), any());
        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> nodesCleanedStorageService.updateCleanedNodeBySha("pkey", 1,
                                DeleteArtifactStatus.DELETED.getCode(), List.of("sha1")));
        assertEquals("Can't update cleaned node. Policy key pkey, policy run id 1", storageException.getMessage());
    }

    @Test
    void getCleanedNodeByStatusSuccessfully() throws SQLException {
        CleanedNode node = CleanedNode.builder()
                .policyKey("pkey")
                .policyRunId(1)
                .packageName("pname")
                .status(DeleteArtifactStatus.DELETED.getCode())
                .repo("repo")
                .build();
        when(nodeCleanedDao.getCleanedNodesByStatus(List.of(DeleteArtifactStatus.DELETED.getCode()), 1000,
                0)).thenReturn(List.of(node));
        assertEquals(
                nodesCleanedStorageService.getCleanedNodeByStatus(List.of(DeleteArtifactStatus.DELETED.getCode()), 1000,
                        0),
                List.of(node));
    }

    @Test
    void getCleanedNodeByStatusFailed() throws SQLException {
        when(nodeCleanedDao.getCleanedNodesByStatus(List.of(DeleteArtifactStatus.DELETED.getCode()), 1000, 0))
                .thenThrow(new SQLException("Expected exception"));
        StorageException storageException = assertThrows(StorageException.class,
                () -> nodesCleanedStorageService.getCleanedNodeByStatus(List.of(DeleteArtifactStatus.DELETED.getCode()),
                        1000, 0));
        assertEquals("Can't get processed retention nodes by status [0]",
                storageException.getMessage());
    }


    @Test
    void insertStorageFailure() throws SQLException {
        when(nodeCleanedDao.insertCleanedNode(any())).thenThrow(new SQLException("Expected exception"));
        CleanedNode node = new CleanedNode("pkey", 1, "pname", "pversion", false, "repo", "npath", "nname", 2L, "sha1",
                DeleteArtifactStatus.DELETED.getCode(), "message", null);
        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> nodesCleanedStorageService.insertCleanedNode(node));
        assertEquals("Can't insert cleaned node. Policy key pkey, policy run id 1", storageException.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void insertCleanedNodeEmptyPolicyName(String policyKey) {
        CleanedNode node = new CleanedNode(policyKey, 1, "pname", "pversion", false, "repo",
                "npath", "nname", 2L, "sha1", DeleteArtifactStatus.DELETED.getCode(), "message",null);
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class,
                        () -> nodesCleanedStorageService.insertCleanedNode(node));
        assertEquals("Policy key can't be empty", illegalArgumentException.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void insertCleanedNodeEmptyRepo(String repo) {
        CleanedNode node = new CleanedNode("pkey", 1, "pname", "pversion", false, repo,
                "npath", "nname", 2L, "sha1", DeleteArtifactStatus.DELETED.getCode(), "message",null);
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class,
                        () -> nodesCleanedStorageService.insertCleanedNode(node));
        assertEquals("Repo can't be empty", illegalArgumentException.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void insertCleanedNodeEmptyNodePath(String nodePath) {
        CleanedNode node = new CleanedNode("pkey", 1, "pname", "pversion", false, "repo",
                nodePath, "nname", 2L, "sha1", DeleteArtifactStatus.DELETED.getCode(), "message",null);
        if (nodePath == null) {
            IllegalArgumentException illegalArgumentException =
                    assertThrows(IllegalArgumentException.class,
                            () -> nodesCleanedStorageService.insertCleanedNode(node));
            assertEquals("Node path can't be null", illegalArgumentException.getMessage());
        } else {
            assertDoesNotThrow(() -> nodesCleanedStorageService.insertCleanedNode(node));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    void insertCleanedNodeEmptyNodeName(String nodeName) {
        CleanedNode node = new CleanedNode("pkey", 1, "pname", "pversion", false, "repo",
                "npath", nodeName, 2L, "sha1", DeleteArtifactStatus.DELETED.getCode(), "message",null);
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class,
                        () -> nodesCleanedStorageService.insertCleanedNode(node));
        assertEquals("Node name can't be empty", illegalArgumentException.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void insertCleanedNodeEmptySha1(String sha1) {
        CleanedNode node = new CleanedNode("pkey", 1, "pname", "pversion", false, "repo",
                "npath", "nname", 0L, sha1, DeleteArtifactStatus.DELETED.getCode(), "message",null);
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class,
                        () -> nodesCleanedStorageService.insertCleanedNode(node));
        assertEquals("SHA can't be empty", illegalArgumentException.getMessage());
    }

    @Test
    void insertCleanedNodeNullableStatus() {
        CleanedNode node = new CleanedNode("pkey", 1, "pname", "pversion", false, "repo",
                "npath", "nname", 0L, "sha1", null, null, null);
        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class,
                        () -> nodesCleanedStorageService.insertCleanedNode(node));
        assertEquals("Status can't be null", illegalArgumentException.getMessage());
    }

    @Test
    void estimateCleanedBinLengthByPolicyKeyAndRunIdSuccessfully() throws SQLException {
        when(nodeCleanedDao.estimateActualCleanedBinLength(any(), anyInt())).thenReturn(100L);
        assertEquals(100L, nodesCleanedStorageService.estimateCleanedBinLengthByPolicyKeyAndRunId("pkey", 1));
    }

    @Test
    void estimateCleanedBinLengthByPolicyKeyAndRunIdFailure() throws SQLException {
        when(nodeCleanedDao.estimateActualCleanedBinLength(any(), anyInt())).thenThrow(
                new SQLException("Expected exception"));

        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> nodesCleanedStorageService.estimateCleanedBinLengthByPolicyKeyAndRunId("pkey", 1));
        assertEquals(
                "Failed to calculate estimate cleaned size. Policy key pkey, policy run id 1 with an error: Expected " +
                        "exception",
                storageException.getMessage());
    }

    @Test
    void estimateProcessedBinLengthByPolicyKeyAndRunIdSuccessfully() throws SQLException {
        when(nodeCleanedDao.estimateActualProcessedBinLength(any(), anyInt(), any())).thenReturn(100L);
        assertEquals(100L, nodesCleanedStorageService.estimateProcessedBinLengthByPolicyKeyAndRunId("pkey", 1,
                DeleteArtifactStatus.DELETED));
    }

    @Test
    void estimateProcessedBinLengthByPolicyKeyAndRunIdFailure() throws SQLException {
        when(nodeCleanedDao.estimateActualProcessedBinLength(any(), anyInt(), any())).thenThrow(
                new SQLException("Expected exception"));

        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> nodesCleanedStorageService.estimateProcessedBinLengthByPolicyKeyAndRunId("pkey", 1,
                                DeleteArtifactStatus.DELETED));
        assertEquals(
                "Failed to calculate processed bin length. Policy key pkey, policy run id 1 with an error Expected " +
                        "exception",
                storageException.getMessage());
    }

    @Test
    void estimateProcessedBinLengthByPolicyKeyAndRunIdByStatusSuccessfully() throws SQLException {
        when(nodeCleanedDao.estimateActualProcessedBinLengthByStatus(any(), anyInt(), any())).thenReturn(100L);
        assertEquals(100L, nodesCleanedStorageService.estimateProcessedBinLengthByPolicyKeyAndRunIdByStatus("pkey", 1,
                DeleteArtifactStatus.DELETED));
    }

    @Test
    void estimateProcessedBinLengthByPolicyKeyAndRunIdByStatusFailure() throws SQLException {
        when(nodeCleanedDao.estimateActualProcessedBinLengthByStatus(any(), anyInt(), any())).thenThrow(
                new SQLException("Expected exception"));

        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> nodesCleanedStorageService.estimateProcessedBinLengthByPolicyKeyAndRunIdByStatus("pkey",
                                1, DeleteArtifactStatus.RESTORED));
        assertEquals("Can't estimate RESTORED size. Policy key pkey, run id 1", storageException.getMessage());
    }


    @Test
    void cleanNodesCleanedRecordsByPolicyKeyAndRunIdSuccessfully() throws SQLException {
        when(nodeCleanedDao.deleteByPolicyKeyAndRun(any(), anyInt())).thenReturn(100);
        assertDoesNotThrow(() -> nodesCleanedStorageService.cleanNodesCleanedRecordsByPolicyKeyAndRunId("pkey", 1));
    }

    @Test
    void cleanNodesCleanedRecordsByPolicyKeyAndRunIdFailure() throws SQLException {
        when(nodeCleanedDao.deleteByPolicyKeyAndRun(any(), anyInt())).thenThrow(
                new SQLException("Expected exception"));

        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> nodesCleanedStorageService.cleanNodesCleanedRecordsByPolicyKeyAndRunId("pkey", 1));
        assertEquals("Can't clean records. Policy key pkey, policy run id 1", storageException.getMessage());
    }

    @Test
    void testGetDeletedNodesSuccessfully() throws SQLException {
        when(nodeCleanedDao.getDeletedNodesRecords(anyString(), anyInt())).thenReturn(List.of(new
                CleanedPackage("cool-package", "cool-version", "cool-repo", 100L, 0)));
        assertDoesNotThrow(() -> nodesCleanedStorageService.getNodesCleanedRecordsByPolicyKeyAndRunId("pkey", 1));
    }

    @Test
    void testGetDeletedNodesFailure() throws SQLException {
        when(nodeCleanedDao.getDeletedNodesRecords(anyString(), anyInt())).thenThrow(
                new SQLException("Expected exception"));
        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> nodesCleanedStorageService.getNodesCleanedRecordsByPolicyKeyAndRunId("pkey", 1));
        assertEquals("Can't get deleted packages. Policy key pkey, policy run id 1", storageException.getMessage());
    }

    @Test
    void testGetCleanedNodesByRunIdAndPolicyKey() throws SQLException {
        when(nodeCleanedDao.getCleanedNodesByRun(anyString(), anyInt())).thenReturn(List.of(new
                CleanedNode("cool-package", 1, "cool-repo", "1.0", false,
                "repo", "path", "cool-node", 1, "sha", 1, "message", null)));
        assertDoesNotThrow(() -> nodesCleanedStorageService.getCleanedNodesByRunIdAndPolicyKey("pkey", 1));
    }

    @Test
    void testGetCleanedNodesByRunIdAndPolicyKeyFailure() throws SQLException {
        when(nodeCleanedDao.getCleanedNodesByRun(anyString(), anyInt())).thenThrow(
                new SQLException("Expected exception"));
        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> nodesCleanedStorageService.getCleanedNodesByRunIdAndPolicyKey("pkey", 1));
        assertEquals("Can't get processed retention nodes. Policy key pkey, policy run id 1",
                storageException.getMessage());
    }

    @Test
    void testCalculateCleanupRunTotalBinSize() throws SQLException {
        when(nodeCleanedDao.calculateCleanupRunTotalBinSize(anyString(), anyLong())).thenReturn(123L);
        assertEquals(123, nodesCleanedStorageService.calculateCleanupRunTotalBinSize("s", 1));
    }

    @Test
    void testCalculateVirtualBinarySize() throws SQLException {
        when(nodeCleanedDao.calculateVirtualBinarySize(anyString(), anyLong(), any())).thenReturn(123L);
        assertEquals(123, nodesCleanedStorageService.calculateVirtualBinarySize("s", 1, DeleteArtifactStatus.ARCHIVED));
    }

    @Test
    void testCalculateCleanupRunTotalBinSizeHandlesSqlException() throws SQLException {
        when(nodeCleanedDao.calculateCleanupRunTotalBinSize(anyString(), anyLong())).thenThrow(
                new SQLException("test"));
        assertThrows(StorageException.class, () -> nodesCleanedStorageService.calculateCleanupRunTotalBinSize("1", 1));
    }

    @Test
    void testCalculateVirtualBinarySizeHandlesSqlException() throws SQLException {
        when(nodeCleanedDao.calculateVirtualBinarySize(anyString(), anyLong(), any())).thenThrow(
                new SQLException("test"));
        assertThrows(StorageException.class,
                () -> nodesCleanedStorageService.calculateVirtualBinarySize("1", 1, DeleteArtifactStatus.ARCHIVED));
    }

    @Test
    void testGetLeadMetadataMapSuccessWithNoManifests() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode node1 = new CleanedNode("pkey", 1, "pkg1", "1.0", true, "repo", "path", 
                "artifact.jar", 100L, "sha1", 0, null, metadata);
        CleanedNode node2 = new CleanedNode("pkey", 1, "pkg2", "2.0", true, "repo", "path", 
                "artifact.pom", 200L, "sha2", 0, null, metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(node1, node2));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(2, result.size());
        assertEquals(node1, result.get("pkg1|1.0|repo|"));
        assertEquals(node2, result.get("pkg2|2.0|repo|"));
    }

    @Test
    void testGetLeadMetadataMapSuccessWithManifestPreference() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode manifestNode = new CleanedNode("pkey", 1, "demo", "1.0", true, "repo", "path", 
                "maven-demo-1.0.json", 100L, "sha1", 0, null, metadata);
        CleanedNode actualNode = new CleanedNode("pkey", 1, "demo", "1.0", true, "repo", "path", 
                "demo-1.0.pom", 200L, "sha2", 0, null, metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(manifestNode, actualNode));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(1, result.size());
        assertEquals(actualNode, result.get("demo|1.0|repo|"));
    }

    @Test
    void testGetLeadMetadataMapSuccessWithOnlyManifests() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode manifestNode = new CleanedNode("pkey", 1, "demo", "1.0", true, "repo", "path", 
                "maven-demo-1.0.json", 100L, "sha1", 0, null, metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(manifestNode));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(1, result.size());
        assertEquals(manifestNode, result.get("demo|1.0|repo|"));
    }

    @Test
    void testGetLeadMetadataMapSuccessWithEmptyList() throws SQLException {
        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of());

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(0, result.size());
    }

    @Test
    void testGetLeadMetadataMapFailureHandling() throws SQLException {
        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenThrow(new SQLException("test error"));

        StorageException exception = assertThrows(StorageException.class,
                () -> nodesCleanedStorageService.getLeadMetadataMap("pkey", 1));

        assertEquals("Can't get lead metadata map. Policy key pkey, policy run id 1", exception.getMessage());
    }

    @Test
    void testGetLeadMetadataMapWithDifferentMessages() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode node1 = new CleanedNode("pkey", 1, "pkg", "1.0", true, "repo", "path", 
                "artifact.jar", 100L, "sha1", 0, "msg1", metadata);
        CleanedNode node2 = new CleanedNode("pkey", 1, "pkg", "1.0", true, "repo", "path", 
                "artifact.jar", 100L, "sha1", 0, "msg2", metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(node1, node2));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(2, result.size());
        assertEquals(node1, result.get("pkg|1.0|repo|msg1"));
        assertEquals(node2, result.get("pkg|1.0|repo|msg2"));
    }

    @Test
    void testGetLeadMetadataMapManifestDetectionHelmOCI() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode helmManifest = new CleanedNode("pkey", 1, "helm/chart", "1.0.0", true, "repo", "path", 
                "manifest.json", 100L, "sha1", 0, null, metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(helmManifest));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(1, result.size());
        assertEquals(helmManifest, result.get("helm/chart|1.0.0|repo|"));
    }

    @Test
    void testGetLeadMetadataMapWithDockerManifestPreference() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode dockerManifest = new CleanedNode("pkey", 1, "docker-image", "latest", true, "repo", "path",
                "docker-image-latest.json", 100L, "sha1", 0, null, metadata);
        CleanedNode dockerActual = new CleanedNode("pkey", 1, "docker-image", "latest", true, "repo", "path",
                "manifest.json", 200L, "sha2", 0, null, metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(dockerManifest, dockerActual));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(1, result.size());
        assertEquals(dockerActual, result.get("docker-image|latest|repo|"));
    }

    @Test
    void testGetLeadMetadataMapWithNpmManifest() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode npmManifest = new CleanedNode("pkey", 1, "@scope/package", "1.2.3", true, "repo", "path",
                "@scope-package-1.2.3.json", 100L, "sha1", 0, null, metadata);
        CleanedNode npmPackage = new CleanedNode("pkey", 1, "@scope/package", "1.2.3", true, "repo", "path",
                "package.tgz", 200L, "sha2", 0, null, metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(npmManifest, npmPackage));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(1, result.size());
        assertEquals(npmPackage, result.get("@scope/package|1.2.3|repo|"));
    }

    @Test
    void testGetLeadMetadataMapWithNonManifestJsonFile() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode jsonFile = new CleanedNode("pkey", 1, "package", "1.0", true, "repo", "path",
                "config.json", 100L, "sha1", 0, null, metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(jsonFile));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(1, result.size());
        assertEquals(jsonFile, result.get("package|1.0|repo|"));
    }

    @Test
    void testGetLeadMetadataMapWithNullNodeName() throws SQLException {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedNode nodeWithNullName = new CleanedNode("pkey", 1, "package", "1.0", true, "repo", "path",
                null, 100L, "sha1", 0, null, metadata);

        when(nodeCleanedDao.getLeadMetadata("pkey", 1)).thenReturn(List.of(nodeWithNullName));

        var result = nodesCleanedStorageService.getLeadMetadataMap("pkey", 1);

        assertEquals(1, result.size());
        assertEquals(nodeWithNullName, result.get("package|1.0|repo|"));
    }

    @Test
    void getFirstErrorMessageForRun_delegatesToDao() throws SQLException {
        when(nodeCleanedDao.getFirstErrorMessageForRun("pkey", 5)).thenReturn("S3 timeout");

        String result = nodesCleanedStorageService.getFirstErrorMessageForRun("pkey", 5);

        assertEquals("S3 timeout", result);
    }

    @Test
    void getFirstErrorMessageForRun_daoReturnsNull_propagatesNull() throws SQLException {
        when(nodeCleanedDao.getFirstErrorMessageForRun("pkey", 5)).thenReturn(null);

        String result = nodesCleanedStorageService.getFirstErrorMessageForRun("pkey", 5);

        assertNull(result);
    }

    @Test
    void getFirstErrorMessageForRun_sqlException_throwsStorageException() throws SQLException {
        when(nodeCleanedDao.getFirstErrorMessageForRun(anyString(), anyInt()))
                .thenThrow(new SQLException("db error"));

        StorageException ex = assertThrows(StorageException.class,
                () -> nodesCleanedStorageService.getFirstErrorMessageForRun("pkey", 5));

        assertTrue(ex.getMessage().contains("pkey"));
    }
}