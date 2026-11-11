package org.artifactory.federation;

import jakarta.annotation.Nullable;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ActionStatus;
import org.artifactory.repo.FullSyncRtfsResponse;
import org.artifactory.repo.RepoDetailsType;

/**
 * Shared contract for all repo types with federation enabled (local, remote, virtual).
 * Each sub-service implements this interface; {@link FederationRoutingServiceImpl} holds the full list
 * and uses {@link #supportedRepoType()} to route calls uniformly — replacing the old switch dispatch.
 * Introduced in RTDEV-83570 PR2; sub-service implementations are added in PR3.
 */
public interface FederationEnabledRepoService {

    void syncToMembers(ActionStatus action, RepoConfig newConfig, @Nullable RepoConfig oldConfig);

    void removeFromRemoteMembers(RepoConfig config);

    void configSync(String repoKey);

    /**
     * Validates the members on the given config.
     */
    void validateMembers(RepoConfig config);

    /**
     * Whether the local federation member of the given repo is in receiver mode.
     */
    boolean isLocalMemberInReceiverMode(RepoConfig config);

    /**
     * Triggers a full sync for the given repo. Remote/Virtual impls return {@code null} since they
     * don't support full sync; only the FEDERATED sub-service has a real implementation.
     */
    FullSyncRtfsResponse triggerFullSync(String repoKey, String mirrorUrl, boolean forceRun, boolean isDry);

    RepoDetailsType supportedRepoType();
}
