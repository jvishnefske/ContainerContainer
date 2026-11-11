package org.artifactory.federation;

import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.Response;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.QueuePosition;
import org.artifactory.repo.ActionStatus;
import org.artifactory.repo.ArtifactoryFederationServiceException;
import org.artifactory.repo.FederationClientService;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.repo.FederationPriorityUpdateRtfsResponse;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoPriority;
import org.artifactory.repo.UrlReposDetails;
import org.artifactory.spring.Reloadable;
import org.artifactory.queue.services.FederatedPersistentQueue;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.jfrog.rtfs.dto.member.FederatedConfigInfo;
import org.jfrog.rtfs.dto.member.MemberQueueState;
import org.jfrog.rtfs.dto.member.MirrorMemberInfo;
import org.jfrog.rtfs.dto.migration.MigrationVerboseStatus;
import org.jfrog.rtfs.dto.tasks.TaskRequest;
import org.jfrog.rtfs.dto.tenant.snapshot.TenantMoveStatus;
import org.jfrog.rtfs.module.member.MemberRemovalResponse;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Alexei Vainshtein
 */
@Service
@Reloadable(beanClass = FederatedRepoService.class, initAfter = {FederationClientService.class})
public class DefaultFederatedRepoServiceImpl implements FederatedRepoService {

    @Override
    public void handlePropagateRepoEventWithRTFS(ActionStatus action, FederatedRepoConfig newConfig) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void resetStaleFullSyncOperations() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<FederationReposResponse> getReposFromRemote(List<String> urls, String proxyName) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<FederationReposResponse> getBaseUrlAndReposFromRemote(List<String> urls, String proxyName) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<RepoDetails> getRepos() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public UrlReposDetails getBaseUrlAndRepos() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public String getToken(String remoteUrl, String key) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void validate(String key, String clientBaseUrl, long timeFromRequestingServer, String repoType,
            String sourceRepo, boolean isReposImportInProgress, boolean rtfsLocalRequest, String projectKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void validateFederationLicenseInstalled() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public String getBaseUrl() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void validateFederationPackageTypeSupported(RepoType repoType) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void validateRemoteServer(String fullUrl, String proxyName,
            String repoType, String sourceRepoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void refreshMasterTokens() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void addInconsistentRepoMember(String memberUrl, String repoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void saveAuthenticationMasterToken(String tokenId, String tokenType, String scope,
            String baseUrl, String exchangeUrl, String accessToken, Long expiresIn) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Collection<AuthenticationToken> getPairedAuthenticationTokens() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Optional<AuthenticationToken> getPairedAuthenticationToken(String baseUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void deleteAuthenticationToken(String baseUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FullSyncStatus getFullSyncStatus(@Nonnull String mirrorRemoteUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void putFullSyncStatus(@Nonnull String mirrorRemoteUrl, @Nonnull FullSyncStatus syncStatus) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void invalidateFullSyncStatus(@Nonnull String mirrorRemoteUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean isProjectBuildInfoRepoFederated(String projectKey) {
        return false;
    }

    @Override
    public void triggerRecovery(boolean manualTrigger) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<FederatedConfigInfo> getFederatedRepoConfigInfo() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FederatedConfigInfo getFederatedRepoConfigInfo(String repoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void replaceUrlOnFederationService(String oldUrl, String newUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Long remainingEventsInQueue(QueuePosition event) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public long lastIdInTheSystem() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void initFederationClient(boolean rtfsEnabled, boolean receivedFromOtherHa) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void reconnectFederationClient(boolean internal) throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean isRtfsClientConnected() {
        return false;
    }

    @Override
    public FederationMigrationStatus getCurrentFederationStatus() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updateFederationStatus(FederationMigrationStatus status, boolean receivedFromOtherHa) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public TenantMoveStatus getTenantMoveStatus() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean isMigrationOver() {
        return false;
    }

    @Override
    public MemberQueueState getInternalQueueState() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updateCursor(MemberQueueState state, Long time) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updateCursorStatus(NodeEventCursorStatus cursorStatus) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FederatedPersistentQueue registerFederationServiceQueueIfNeeded() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Response updateMemberStatus(String localRepoKey, MirrorMemberInfo mirrorMemberInfo, boolean enabled,
            Long version) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Response snapshotFederatedRepos() {
        throw  new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean isBinaryTasksEnabled() {
        return false;
    }

    @Override
    public void updateBinaryTasksFeatureStatus(boolean enabled) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FederationPriorityUpdateRtfsResponse updateFederationPriority(String repoKey, RepoPriority priority) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void deleteConfig(ConfigsServiceKey configKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public String getConfig(ConfigsServiceKey configKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void initFederationClientAsync() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void submitTask(TaskRequest request) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean isFederatedBinaryProviderV2Active() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void enableRtfsAsync() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void addOrUpdateConfig(ConfigsServiceKey configKey, String data) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updateRtfsQueueStatus(NodeEventCursorStatus status) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updatePropagateCreatedTimeProperty(boolean enabled) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public MemberRemovalResponse forceRemoveMember(String memberUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public MigrationVerboseStatus getMigrationVerboseStatus() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void setMigrationVerboseStatus(MigrationVerboseStatus migrationVerboseStatus) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updateLocalMemberStatus(String repoKey, boolean enable) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean repoExists(String repoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void runMembersRepoSync() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    public Set<String> getInconsistentRepos() {
        return Set.of();
    }

    @Override
    public void removeInconsistentRepoMember(String memberUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void addImportsConflictErrors(Set<String> repos) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Set<String> getImportsConflictErrors() {
        return Set.of();
    }

    @Override
    public void deregisterQueue(String repoKey, String remoteMemberUrl, String remoteMemberKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public NodeEventCursorStatus getInternalQueueCursorStatus() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updateInternalQueuesStatus(NodeEventCursorStatus status, String localRepoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void deregisterRemoteMemberQueues(String repoKey,
            List<org.artifactory.repo.federated.FederatedMember> members) {
        // no-op: federation is not supported in OSS
    }

    @Nonnull
    private String getUnsupportedMessage() {
        return "Unsupported operation: federated";
    }

    @Override
    public void onLicenseLoaded() {

    }

    @Override
    public void onContextCreated() {

    }
}
