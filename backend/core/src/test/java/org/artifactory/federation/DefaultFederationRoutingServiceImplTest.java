package org.artifactory.federation;

import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.ActionStatus;
import org.artifactory.repo.FullSyncRtfsResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class DefaultFederationRoutingServiceImplTest {

    private final DefaultFederationRoutingServiceImpl service = new DefaultFederationRoutingServiceImpl();

    @Test
    void syncFederatedRepoConfigToMembers_isNoOp() {
        assertDoesNotThrow(() -> service.syncFederatedRepoConfigToMembers(
                ActionStatus.CREATE, mock(FederatedRepoConfig.class), null));
    }

    @Test
    void syncRemoteRepoConfigToMembers_isNoOp() {
        assertDoesNotThrow(() -> service.syncRemoteRepoConfigToMembers(
                ActionStatus.UPDATE, mock(RemoteRepoConfig.class), null));
    }

    @Test
    void syncVirtualRepoConfigToMembers_isNoOp() {
        assertDoesNotThrow(() -> service.syncVirtualRepoConfigToMembers(
                ActionStatus.DELETE, mock(VirtualRepoConfig.class), null));
    }

    @Test
    void validateMembersConfig_isNoOp() {
        assertDoesNotThrow(() -> service.validateMembersConfig(mock(RepoConfig.class)));
    }

    @Test
    void removeRepoFromFederationMembers_isNoOp() {
        assertDoesNotThrow(() -> service.removeRepoFromFederationMembers(mock(RepoConfig.class)));
    }

    @Test
    void isLocalMemberInReceiverMode_byKey_returnsFalse() {
        assertFalse(service.isLocalMemberInReceiverMode("repo-key"));
    }

    @Test
    void isLocalMemberInReceiverMode_byConfig_returnsFalse() {
        assertFalse(service.isLocalMemberInReceiverMode(mock(FederatedRepoConfig.class)));
    }

    @Test
    void configSync_isNoOp() {
        assertDoesNotThrow(() -> service.configSync("repo-key"));
    }

    @Test
    void removeRepoFromRemoteMembers_isNoOp() {
        assertDoesNotThrow(() -> service.removeRepoFromRemoteMembers(mock(FederatedRepoConfig.class)));
    }

    @Test
    void triggerFullSync_returnsNonNull() {
        FullSyncRtfsResponse result = service.triggerFullSync("repo-key", "http://mirror", false, false);
        assertNotNull(result);
    }
}
