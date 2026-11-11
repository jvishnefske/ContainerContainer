package org.artifactory.repo;

import jakarta.annotation.Nonnull;
import lombok.NonNull;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.federated.exception.RequestTunnelingException;
import org.artifactory.federated.model.CrossMemberRequest;
import org.artifactory.federated.model.CrossMemberResponse;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.reponse.RecoveryRtfsResponse;
import org.artifactory.spring.Reloadable;
import org.jfrog.rtfs.ArtifactoryFederationClient;
import org.jfrog.rtfs.dto.tasks.BinaryTask;
import org.jfrog.rtfs.dto.tenant.snapshot.TenantFederatedConfigSnapshot;
import org.jfrog.rtfs.module.member.MemberRemovalResponse;
import org.jfrog.rtfs.module.tenant_move.ImportRequest;
import org.jfrog.rtfs.module.tenant_move.ImportResponse;
import org.jfrog.rtfs.module.tenant_move.ImportStatusResponse;
import org.jfrog.storage.binstore.providers.base.PersistedRemoteTask;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Since the service is used by both OSS and Storage, we have created a default interface that enables
 * Services in Storage
 * to access the bean. However, after the initialization of the OSS project, the default interface is no
 * longer used.
 * Instead, an interface in the OSS project with the `@Primary` annotation overrides the default class.
 */
@Service
@Reloadable(beanClass = FederationClientService.class, initAfter = {StorageInterceptors.class})
public class DefaultFederationClientServiceImpl implements FederationClientService {

    @Override
    public void actOnFederationStatusChange(boolean rtfsEnabled, boolean internal) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public ValidateProcessRtfsResponse validateRemoteMember(String sourceRepoKey, String repoType,
            String targetRepoKey, String remoteUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void propagateArtifactEvents(List<? extends QueueEvent> events) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updateQueueStatus(String queueStatus) throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void updatePropagateCreatedTimeProperty(boolean shouldPropagateCreatedTime)
            throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<String> propagateRepoEvent(FederatedRepoConfig newConfig, ActionStatus action,
            String projectKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FullSyncRtfsResponse runFullSync(String repoKey, String mirrorUrl, boolean isDry) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean shareMasterToken(String hostUrl, String jwtToken) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void changeFederatedUrl(String oldUrl, String newUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public StateListRtfsResponse getStateList(List<String> statuses) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public StateSummaryRtfsResponse getStateSummary() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public ConnectionStatesRtfsResponse getConnectionStates(String repoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public RecoveryRtfsResponse runRecovery(String repoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean isRepoReady(String repoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public String runGetFileListTaskPath(String repoKey) throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean hasMigrationStateReached(FederationMigrationStatus federationMigrationStatus) {
        return false;
    }

    @Override
    public boolean isRtfsClientConnected() {
        return false;
    }

    @Override
    public void reconnectFederationClient() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void initArtifactoryFederationClient() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public ArtifactoryFederationClient getFederationClient() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void connectClient() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public CrossMemberResponse tunnelRequest(CrossMemberRequest request) throws RequestTunnelingException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FederationPriorityUpdateRtfsResponse updateFederationPriority(String repoKey, RepoPriority priority) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FederationMigrationStatus updateMigrationStatus(FederationMigrationStatus newStatus, boolean internal) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public RepoBinariesStateRtfsResponse getRepoBinariesState(String repoKey, String remoteUrl)
            throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public ValidateProcessRtfsResponse validateLocalServer(String localKey, String originRepoKey, String originUrl,
            boolean importInProgress, Long time) throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void propagateLocalRepoEvent(String localKey, String remoteKey, String originUrl,
            ActionStatus actionStatus, boolean enabled,
            List<FederatedMember> federatedMembers, long modificationDate)
            throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Map<String, String> heartbeatLegacy(String remoteUrl, List<HeartbeatLegacyRequest> heartbeatLegacyRequests)
            throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void replaceUrlOnFederationService(String oldUrl, String newUrl)
            throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public void insertBinariesTask(List<BinaryTask> tasks) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public boolean federationNodeExistsInTopology() {
        return false;
    }


    @Nonnull
    private String getUnsupportedMessage() {
        return "Unsupported operation: federated";
    }

    @Override
    public Collection<PersistedRemoteTaskInfo> getFailingTasks(@NonNull String repoKey, Integer limit, Integer page)
            throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public int deleteFailingTasks(@NonNull String repoName, String sha1) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public int replayFailingTasks(@NonNull String repoKey, String sha1) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<PersistedRemoteTask> getBinaryTasks() throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public int deleteBinaryTasks(List<Long> taskIds) throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public Set<String> getDisabledBySystemRepos() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public MemberRemovalResponse forceRemoveMember(@NonNull String memberUrl)
            throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public TenantFederatedConfigSnapshot getFederatedConfigSnapshot() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public ImportResponse tenantMoveImportToNewRegion(ImportRequest request)
            throws ArtifactoryFederationServiceException {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public ImportStatusResponse getImportStatus()  {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }
}
