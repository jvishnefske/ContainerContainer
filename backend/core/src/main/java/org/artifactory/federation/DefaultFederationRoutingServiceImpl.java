package org.artifactory.federation;

import jakarta.annotation.Nullable;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.ActionStatus;
import org.artifactory.repo.FullSyncRtfsResponse;
import org.springframework.stereotype.Service;

/**
 * OSS no-op implementation of {@link FederationRoutingService}.
 * All methods are no-ops; the Pro implementation {@code FederationRoutingServiceImpl} overrides this bean.
 */
@Service
public class DefaultFederationRoutingServiceImpl implements FederationRoutingService {

    @Override
    public void syncFederatedRepoConfigToMembers(ActionStatus action,
            @Nullable FederatedRepoConfig newConfig, @Nullable FederatedRepoConfig oldConfig) {
        // no-op: federation is not supported in OSS
    }

    @Override
    public void syncRemoteRepoConfigToMembers(ActionStatus action,
            RemoteRepoConfig newRepoConfig, @Nullable RemoteRepoConfig oldRepoConfig) {
        // no-op: federation is not supported in OSS
    }

    @Override
    public void syncVirtualRepoConfigToMembers(ActionStatus action,
            @Nullable VirtualRepoConfig newRepoConfig, @Nullable VirtualRepoConfig oldRepoConfig) {
        // no-op: federation is not supported in OSS
    }

    @Override
    public void validateMembersConfig(RepoConfig config) {
        // no-op: federation is not supported in OSS
    }

    @Override
    public void removeRepoFromFederationMembers(RepoConfig repoConfig) {
        // no-op: federation is not supported in OSS
    }

    @Override
    public boolean isLocalMemberInReceiverMode(String repoKey) {
        return false;
    }

    @Override
    public boolean isLocalMemberInReceiverMode(FederatedRepoConfig repoConfig) {
        return false;
    }

    @Override
    public void configSync(String repoKey) {
        // no-op: federation is not supported in OSS
    }

    @Override
    public void removeRepoFromRemoteMembers(FederatedRepoConfig federatedRepoConfig) {
        // no-op: federation is not supported in OSS
    }

    @Override
    public FullSyncRtfsResponse triggerFullSync(String repoKey, String mirrorUrl, boolean forceRun, boolean isDry) {
        return new FullSyncRtfsResponse("");
    }
}
