package org.artifactory.federation;

import jakarta.annotation.Nullable;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.ActionStatus;
import org.artifactory.repo.FullSyncRtfsResponse;

/**
 * Per-repo-type dispatcher over the three {@link FederationEnabledRepoService} sub-services
 * (FEDERATED / REMOTE / VIRTUAL). Owns the routing from the config-change interceptor to the
 * matching type-specific service.
 *
 * <p>Split out from {@link FederatedRepoService} to break the Spring cycle introduced by the
 * per-type sub-service split (RTDEV-83570): the sub-services need general federation logic from
 * {@code FederatedRepoService}, while only the interceptors and {@code FederatedRepoAddon} need
 * the dispatching surface. After the split:
 * <pre>
 *   Interceptors -&gt; FederationRoutingService -&gt; FederationEnabledRepoService (3 impls)
 *                                                       |
 *                                                       v
 *                                                FederatedRepoService (general only)
 * </pre>
 *
 * <p>OSS uses {@link DefaultFederationRoutingServiceImpl} which provides no-op implementations.
 */
public interface FederationRoutingService {

    void syncFederatedRepoConfigToMembers(ActionStatus action,
            @Nullable FederatedRepoConfig newConfig, @Nullable FederatedRepoConfig oldConfig);

    void syncRemoteRepoConfigToMembers(ActionStatus action,
            RemoteRepoConfig newRepoConfig, @Nullable RemoteRepoConfig oldRepoConfig);

    void syncVirtualRepoConfigToMembers(ActionStatus action,
            @Nullable VirtualRepoConfig newRepoConfig, @Nullable VirtualRepoConfig oldRepoConfig);

    void validateMembersConfig(RepoConfig config);

    void removeRepoFromFederationMembers(RepoConfig repoConfig);

    boolean isLocalMemberInReceiverMode(String repoKey);

    boolean isLocalMemberInReceiverMode(FederatedRepoConfig repoConfig);

    void configSync(String repoKey);

    void removeRepoFromRemoteMembers(FederatedRepoConfig federatedRepoConfig);

    FullSyncRtfsResponse triggerFullSync(String repoKey, String mirrorUrl, boolean forceRun, boolean isDry);
}
