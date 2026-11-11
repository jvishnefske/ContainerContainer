package org.artifactory.repo;

import org.artifactory.model.FederatedRepoConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class DefaultFederationClientServiceImplTest {

    @Test
    void testErrorOnDefaultRunFullSync() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.runFullSync("", "", false));
    }

    @Test
    void testShareMasterToken() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.shareMasterToken("http://example.com", "jwtToken"));
    }

    @Test
    void propagateRepoEvent_shouldThrowUnsupportedOperationException_whenCalledWithFederatedRepoConfig() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.propagateRepoEvent(
                        (org.artifactory.model.FederatedRepoConfig) null, ActionStatus.CREATE, "default"));
    }

    @Test
    void propagateRepoEvent_shouldReturnEmptyList_whenDefaultMethodCalled()
            throws ArtifactoryFederationServiceException {
        // Arrange - create a mock that calls real methods on the interface default implementation
        FederationClientService service = mock(FederationClientService.class, withSettings().
                defaultAnswer(CALLS_REAL_METHODS));
        FederatedRepoConfig config = mock(FederatedRepoConfig.class);

        // Act
        List<String> result = service.propagateRepoEvent(config, ActionStatus.CREATE, "default");

        // Assert
        assertEquals(List.of(), result);
    }

    @Test
    void testPropagateArtifactEvents() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.propagateArtifactEvents(List.of()));
    }

    @Test
    void testChangeFederatedUrl() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.changeFederatedUrl("", ""));
    }

    @Test
    void testValidateLocalServer() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class, () ->
                defaultFederationClientService.validateLocalServer("local", "origin", "originUrl", true, 1L));
    }

    @Test
    void testPropagateLocalRepoEvent() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.propagateLocalRepoEvent("local", "remote",
                        "http://url/artifactory", ActionStatus.STATUS_CHANGE, true, List.of(), 1L));
    }

    @Test
    void testHeartbeatLegacy() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.heartbeatLegacy("remote", List.of()));
    }

    @Test
    void testReplaceUrlOnFederationService() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.replaceUrlOnFederationService("remote", "new"));
    }

    @Test
    void testRunRecovery() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.runRecovery("remote"));
    }

    @Test
    void testInitFederationClient() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                defaultFederationClientService::initArtifactoryFederationClient);
    }

    @Test
    void testIsRtfsClientConnected() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertFalse(defaultFederationClientService.isRtfsClientConnected());
    }

    @Test
    void testReconnectFederationClient() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class, defaultFederationClientService::reconnectFederationClient);
    }

    @Test
    void testHasMinimumStatusReached() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertFalse(
                defaultFederationClientService.hasMigrationStateReached(FederationMigrationStatus.MIGRATION_COMPLETED));
    }

    @Test
    void testShouldProcessEventsToRTFS() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertFalse(defaultFederationClientService.shouldProcessEventsToRTFS());
    }

    @Test
    void testSetMigrationStatus() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.updateMigrationStatus(
                        FederationMigrationStatus.MIGRATION_COMPLETED, true));
    }

    @Test
    void testSetFederationServiceEnabled() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.actOnFederationStatusChange(true, true));
    }

    @Test
    void testGetMigrationStatus() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertNull(defaultFederationClientService.getFederationClientStatus());
    }

    @Test
    void testTunnelRequest() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.tunnelRequest(null));
    }

    @Test
    void TestPriorityUpdate() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.updateFederationPriority("", null));
    }

    @Test
    void testConnectClient() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                defaultFederationClientService::connectClient);
    }

    @Test
    void testUpdateQueueStatus() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.updateQueueStatus("test"));
    }

    @Test
    void testInsertTasks() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.insertBinariesTask(List.of()));
    }

    @Test
    void federationNodeExistsInTopologyReturnsTrueWhenNodesExist() {
        DefaultFederationClientServiceImpl service = new DefaultFederationClientServiceImpl();
        assertFalse(service.federationNodeExistsInTopology());
    }

    @Test
    void testGetFailingTasks() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.getFailingTasks("repoKey", 10, 1));
    }

    @Test
    void testDeleteFailingTasks() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.deleteFailingTasks("repoName", "sha1"));
    }

    @Test
    void testReplayFailingTasks() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.replayFailingTasks("repoKey", "sha1"));
    }


    @Test
    void testGetBinaryTasks() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                defaultFederationClientService::getBinaryTasks);
    }

    @Test
    void testDeleteBinaryTasks() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.deleteBinaryTasks(List.of()));
    }

    @Test
    void testGetFederationClient() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                defaultFederationClientService::getFederationClient);
    }

    @Test
    void testGetDisabledBySystemRepos() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                defaultFederationClientService::getDisabledBySystemRepos);
    }

    @Test
    void testForceRemoveMember() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> defaultFederationClientService.forceRemoveMember("memberUrl"));
    }

    @Test
    void testFederatedReposSnapshot() {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                defaultFederationClientService::getFederatedConfigSnapshot);
    }

    @Test
    void test () {
        DefaultFederationClientServiceImpl defaultFederationClientService = new DefaultFederationClientServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                ()-> defaultFederationClientService.updatePropagateCreatedTimeProperty(true));
    }
}