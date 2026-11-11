package org.artifactory.federation;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.QueuePosition;
import org.artifactory.repo.ActionStatus;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.repo.RepoPriority;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.jfrog.rtfs.dto.member.MemberQueueState;
import org.jfrog.rtfs.dto.member.MirrorMemberInfo;
import org.jfrog.rtfs.dto.migration.MigrationVerboseStatus;
import org.jfrog.rtfs.dto.tasks.TaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DefaultFederatedRepoServiceImplTest {

    private DefaultFederatedRepoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DefaultFederatedRepoServiceImpl();
    }

    @Test
    void resetStaleFullSyncOperations_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.resetStaleFullSyncOperations());
    }

    @Test
    void getRepos_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getRepos());
    }

    @Test
    void getReposFromRemote_shouldThrowUnsupportedOperationException() {
        List<String> urls = List.of("https://repo1.jfrog.io/artifactory", "https://repo2.jfrog.io/artifactory");
        String proxyName = "default-proxy";

        assertThrows(UnsupportedOperationException.class, () -> service.getReposFromRemote(urls, proxyName));
    }

    @Test
    void getBaseUrlAndRepos_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getBaseUrlAndRepos());
    }

    @Test
    void getBaseUrl_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getBaseUrl());
    }

    @Test
    void getFederatedRepoConfigInfo_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getFederatedRepoConfigInfo());
    }

    @Test
    void lastIdInTheSystem_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.lastIdInTheSystem());
    }

    @Test
    void getInternalQueueState_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getInternalQueueState());
    }

    @Test
    void snapshotFederatedRepos_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.snapshotFederatedRepos());
    }

    @Test
    void getCurrentFederationStatus_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getCurrentFederationStatus());
    }

    @Test
    void isFederatedBinaryProviderV2Active_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.isFederatedBinaryProviderV2Active());
    }

    @Test
    void getImportsConflictErrors_shouldReturnEmptySet() {
        assertTrue(service.getImportsConflictErrors().isEmpty());
    }

    @Test
    void getMigrationVerboseStatus_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getMigrationVerboseStatus());
    }

    @Test
    void initFederationClientAsync_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.initFederationClientAsync());
    }

    @Test
    void addImportsConflictErrors_shouldThrowUnsupportedOperationException() {
        Set<String> errors = Set.of("repo-1", "repo-2");
        assertThrows(UnsupportedOperationException.class, () -> service.addImportsConflictErrors(errors));
    }

    @Test
    void getProperties_shouldThrowUnsupportedOperationException() {
        String repoKey = "maven-central-cache";
        assertThrows(UnsupportedOperationException.class, () -> service.getProperties(List.of(), repoKey));
    }

    @Test
    void getBaseUrlAndReposFromRemote_shouldThrowUnsupportedOperationException() {
        String proxyName = "corporate-proxy";
        assertThrows(UnsupportedOperationException.class,
                () -> service.getBaseUrlAndReposFromRemote(List.of(), proxyName));
    }

    @Test
    void handlePropagateRepoEventWithRTFS_shouldThrowUnsupportedOperationException() {
        FederatedRepoConfig config = mock(FederatedRepoConfig.class);
        assertThrows(UnsupportedOperationException.class,
                () -> service.handlePropagateRepoEventWithRTFS(ActionStatus.CREATE, config));
    }

    @Test
    void validate_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () ->
                service.validate("key", "client", 1L, "type", "source", true, true, "project-key"));
    }

    @Test
    void deregisterQueue_shouldThrowUnsupportedOperationException() {
        String repoKey = "npm-local";
        String memberUrl = "https://member.jfrog.io/artifactory";
        String remoteRepoKey = "npm-remote";
        assertThrows(UnsupportedOperationException.class,
                () -> service.deregisterQueue(repoKey, memberUrl, remoteRepoKey));
    }

    @Test
    void updateInternalQueuesStatus_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.updateInternalQueuesStatus(NodeEventCursorStatus.HEALTHY, "generic-local"));
    }

    @Test
    void replaceUrlOnFederationService_shouldThrowUnsupportedOperationException() {
        String oldUrl = "https://old-instance.jfrog.io";
        String newUrl = "https://new-instance.jfrog.io";
        assertThrows(UnsupportedOperationException.class,
                () -> service.replaceUrlOnFederationService(oldUrl, newUrl));
    }

    @Test
    void triggerManualRecoveryForRepository_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.triggerManualRecoveryForRepository("conan-local"));
    }

    @Test
    void triggerRecovery_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.triggerRecovery(true));
    }

    @Test
    void remainingEventsInQueue_shouldThrowUnsupportedOperationException() {
        QueuePosition queuePosition = new QueuePosition("pypi-local", "https://mirror.jfrog.io", "pypi-remote", 12345L);
        assertThrows(UnsupportedOperationException.class, () -> service.remainingEventsInQueue(queuePosition));
    }

    @Test
    void initFederationClient_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.initFederationClient(true, false));
    }

    @Test
    void updateFederationStatus_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.updateFederationStatus(FederationMigrationStatus.MIGRATION_COMPLETED, false));
    }

    @Test
    void updateBinaryTasksFeatureStatus_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.updateBinaryTasksFeatureStatus(true));
    }

    @Test
    void updateFederationPriority_shouldThrowUnsupportedOperationException() {
        String repoKey = "gradle-local";
        assertThrows(UnsupportedOperationException.class,
                () -> service.updateFederationPriority(repoKey, RepoPriority.HIGH));
    }

    @Test
    void updateCursor_shouldThrowUnsupportedOperationException() {
        long epochSecond = Instant.parse("2024-12-19T10:30:00Z").getEpochSecond();
        assertThrows(UnsupportedOperationException.class,
                () -> service.updateCursor(MemberQueueState.HEALTHY, epochSecond));
        assertThrows(UnsupportedOperationException.class,
                () -> service.updateCursorStatus(NodeEventCursorStatus.HEALTHY));
    }

    @Test
    void addOrUpdateConfig_shouldThrowUnsupportedOperationException() {
        String configData = "{\"repos\":[\"repo-1\",\"repo-2\"]}";
        assertThrows(UnsupportedOperationException.class,
                () -> service.addOrUpdateConfig(ConfigsServiceKey.RTFS_DISABLED_BY_SYSTEM_REPOS, configData));
    }

    @Test
    void deleteConfig_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.deleteConfig(ConfigsServiceKey.RTFS_DISABLED_BY_SYSTEM_REPOS));
    }

    @Test
    void getConfig_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.getConfig(ConfigsServiceKey.RTFS_DISABLED_BY_SYSTEM_REPOS));
    }

    @Test
    void forceRemoveMember_shouldThrowUnsupportedOperationException() {
        String memberUrl = "https://removed-member.jfrog.io/artifactory";
        assertThrows(UnsupportedOperationException.class, () -> service.forceRemoveMember(memberUrl));
    }

    @Test
    void updateMemberStatus_shouldThrowUnsupportedOperationException() {
        String localRepoKey = "helm-local";
        MirrorMemberInfo memberInfo = new MirrorMemberInfo("helm-remote", "https://mirror.jfrog.io/artifactory");
        long eventId = 98765L;
        assertThrows(UnsupportedOperationException.class,
                () -> service.updateMemberStatus(localRepoKey, memberInfo, true, eventId));
    }

    @Test
    void setMigrationVerboseStatus_shouldThrowUnsupportedOperationException() {
        MigrationVerboseStatus status = new MigrationVerboseStatus(List.of());
        assertThrows(UnsupportedOperationException.class, () -> service.setMigrationVerboseStatus(status));
    }

    @Test
    void reconnectFederationClient_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.reconnectFederationClient(false));
    }

    @Test
    void getExtendedInfo_shouldThrowUnsupportedOperationException() {
        String repoKey = "maven-releases";
        assertThrows(UnsupportedOperationException.class, () -> service.getExtendedInfo(repoKey));
    }

    @Test
    void updatePropagateCreatedTimeProperty_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.updatePropagateCreatedTimeProperty(true));
    }

    @Test
    void isRtfsClientConnected_shouldReturnFalse() {
        boolean result = service.isRtfsClientConnected();
        assertFalse(result);
    }

    @Test
    void shouldProcessEventsToRTFS_shouldReturnFalse() {
        boolean result = service.shouldProcessEventsToRTFS();
        assertFalse(result);
    }

    @Test
    void isMigrationOver_shouldReturnFalse() {
        boolean result = service.isMigrationOver();
        assertFalse(result);
    }

    @Test
    void isBinaryTasksEnabled_shouldReturnFalse() {
        boolean result = service.isBinaryTasksEnabled();
        assertFalse(result);
    }


    @Test
    void getToken_shouldThrowUnsupportedOperationException() {
        String remoteUrl = "https://remote.jfrog.io/artifactory";
        String key = "npm-local";

        assertThrows(UnsupportedOperationException.class, () -> service.getToken(remoteUrl, key));
    }

    @Test
    void validateFederationLicenseInstalled_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.validateFederationLicenseInstalled());
    }

    @Test
    void validateFederationPackageTypeSupported_shouldThrowUnsupportedOperationException() {
        RepoType repoType = RepoType.Maven;

        assertThrows(UnsupportedOperationException.class,
                () -> service.validateFederationPackageTypeSupported(repoType));
    }

    @Test
    void validateRemoteServer_shouldThrowUnsupportedOperationException() {
        String fullUrl = "https://remote.jfrog.io/artifactory/npm-local";
        String proxyName = "corporate-proxy";
        String repoType = "npm";
        String sourceRepoKey = "npm-remote";

        assertThrows(UnsupportedOperationException.class,
                () -> service.validateRemoteServer(fullUrl, proxyName, repoType, sourceRepoKey));
    }

    @Test
    void refreshMasterTokens_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.refreshMasterTokens());
    }

    @Test
    void addInconsistentRepoMember_shouldThrowUnsupportedOperationException() {
        String memberUrl = "https://inconsistent.jfrog.io/artifactory";
        String repoKey = "docker-local";

        assertThrows(UnsupportedOperationException.class,
                () -> service.addInconsistentRepoMember(memberUrl, repoKey));
    }

    @Test
    void saveAuthenticationMasterToken_shouldThrowUnsupportedOperationException() {
        String tokenId = "token-123-456-789";
        String tokenType = "Bearer";
        String scope = "api:*";
        String baseUrl = "https://acme.jfrog.io";
        String exchangeUrl = "https://acme.jfrog.io/access/api/v1/tokens";
        String accessToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...";
        Long expiresIn = 3600L;

        assertThrows(UnsupportedOperationException.class, () ->
                service.saveAuthenticationMasterToken(tokenId, tokenType, scope, baseUrl, exchangeUrl,
                        accessToken, expiresIn));
    }

    @Test
    void getPairedAuthenticationTokens_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getPairedAuthenticationTokens());
    }

    @Test
    void getPairedAuthenticationToken_shouldThrowUnsupportedOperationException() {
        String baseUrl = "https://partner.jfrog.io";

        assertThrows(UnsupportedOperationException.class,
                () -> service.getPairedAuthenticationToken(baseUrl));
    }

    @Test
    void deleteAuthenticationToken_shouldThrowUnsupportedOperationException() {
        String baseUrl = "https://old-partner.jfrog.io";

        assertThrows(UnsupportedOperationException.class, () -> service.deleteAuthenticationToken(baseUrl));
    }

    @Test
    void getFullSyncStatus_shouldThrowUnsupportedOperationException() {
        String mirrorRemoteUrl = "https://mirror.jfrog.io/artifactory";

        assertThrows(UnsupportedOperationException.class, () -> service.getFullSyncStatus(mirrorRemoteUrl));
    }

    @Test
    void putFullSyncStatus_shouldThrowUnsupportedOperationException() {
        String mirrorRemoteUrl = "https://mirror.jfrog.io/artifactory";
        FullSyncStatus syncStatus = mock(FullSyncStatus.class);

        assertThrows(UnsupportedOperationException.class,
                () -> service.putFullSyncStatus(mirrorRemoteUrl, syncStatus));
    }

    @Test
    void invalidateFullSyncStatus_shouldThrowUnsupportedOperationException() {
        String mirrorRemoteUrl = "https://mirror.jfrog.io/artifactory";

        assertThrows(UnsupportedOperationException.class,
                () -> service.invalidateFullSyncStatus(mirrorRemoteUrl));
    }

    @Test
    void isProjectBuildInfoRepoFederated_shouldReturnFalse() {
        assertFalse(service.isProjectBuildInfoRepoFederated("PROJ-456"));
    }

    @Test
    void triggerManualRecoveryForRepo_shouldThrowUnsupportedOperationException() {
        String repoKey = "cargo-local";

        assertThrows(UnsupportedOperationException.class,
                () -> service.triggerManualRecoveryForRepository(repoKey));
    }

    @Test
    void getTenantMoveStatus_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getTenantMoveStatus());
    }

    @Test
    void submitTask_shouldThrowUnsupportedOperationException() {
        TaskRequest request = mock(TaskRequest.class);

        assertThrows(UnsupportedOperationException.class, () -> service.submitTask(request));
    }

    @Test
    void enableRtfsAsync_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.enableRtfsAsync());
    }

    @Test
    void updateRtfsQueueStatus_shouldThrowUnsupportedOperationException() {
        NodeEventCursorStatus status = NodeEventCursorStatus.PENDING_FS;

        assertThrows(UnsupportedOperationException.class, () -> service.updateRtfsQueueStatus(status));
    }

    @Test
    void updateLocalMemberStatus_shouldThrowUnsupportedOperationException() {
        String repoKey = "helm-charts";
        boolean enable = true;

        assertThrows(UnsupportedOperationException.class,
                () -> service.updateLocalMemberStatus(repoKey, enable));
    }

    @Test
    void runMembersRepoSync_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.runMembersRepoSync());
    }

    @Test
    void getInconsistentRepos_shouldReturnEmptySet() {
        assertTrue(service.getInconsistentRepos().isEmpty());
    }

    @Test
    void removeInconsistentRepoMember_shouldThrowUnsupportedOperationException() {
        String memberUrl = "https://problematic.jfrog.io/artifactory";

        assertThrows(UnsupportedOperationException.class,
                () -> service.removeInconsistentRepoMember(memberUrl));
    }

    @Test
    void getInternalQueueCursorStatus_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> service.getInternalQueueCursorStatus());
    }

    @Test
    void onLicenseLoaded_shouldExecuteWithoutException() {
        assertDoesNotThrow(() -> service.onLicenseLoaded());
    }

    @Test
    void onContextCreated_shouldExecuteWithoutException() {
        assertDoesNotThrow(() -> service.onContextCreated());
    }

    @Test
    void deregisterRemoteMemberQueues_isNoOp() {
        assertDoesNotThrow(() -> service.deregisterRemoteMemberQueues("repo-key", List.of()));
    }

}