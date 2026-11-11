
package org.artifactory.repo;

import lombok.NonNull;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.federated.exception.RequestTunnelingException;
import org.artifactory.federated.model.CrossMemberRequest;
import org.artifactory.federated.model.CrossMemberResponse;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.reponse.RecoveryRtfsResponse;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.rtfs.ArtifactoryFederationClient;
import org.jfrog.rtfs.dto.tasks.BinaryTask;
import org.jfrog.rtfs.dto.tenant.snapshot.TenantFederatedConfigSnapshot;
import org.jfrog.rtfs.module.member.MemberRemovalResponse;
import org.jfrog.rtfs.module.tenant_move.ImportRequest;
import org.jfrog.rtfs.module.tenant_move.ImportResponse;
import org.jfrog.rtfs.module.tenant_move.ImportStatusResponse;
import org.jfrog.storage.binstore.providers.base.PersistedRemoteTask;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for managing Artifactory federation client.
 *
 * @author Daniel Simantov
 */
public interface FederationClientService extends ReloadableBean {

    /**
     * Initialize the Artifactory federation client, if it's used through the bean it will be async
     * the reason for that is we don't want to stop artifactory from coming up if the RTFS client is down
     */
    void initArtifactoryFederationClient() throws ArtifactoryFederationServiceException;

    /**
     * Connects the client to RTFS
     */
    void connectClient();

    ArtifactoryFederationClient getFederationClient();

    /**
     * @return the current status of the client
     */
    default FederationMigrationStatus getFederationClientStatus() {
        return null;
    }

    /**
     * @param newStatus - The new status to be set. Can be null upon shutting down
     * @throws ArtifactoryFederationServiceException - Upon trying to change status while client wasn't init
     */
    FederationMigrationStatus updateMigrationStatus(FederationMigrationStatus newStatus, boolean internal)
            throws ArtifactoryFederationServiceException;

    /**
     * Enable or disable the federation client. Upon enable, will connect to the client and
     * change the system property to identify that the client enabled.
     * Upon disable, will shut down the client and remove the status
     *
     * @param rtfsEnabled - true for enable false for disable
     * @param internal    - If the request comes from internal HA member. This is required to have
     *                    the ability to distinguish between first request or propagated HA request
     * @throws ArtifactoryFederationServiceException - Upon error connecting to RTFS or acting upon db
     */
    void actOnFederationStatusChange(boolean rtfsEnabled, boolean internal)
            throws ArtifactoryFederationServiceException;

    /**
     * Validates a remote member for a given local repository in the federation service.
     *
     * @param sourceRepoKey The key of the local repository.
     * @param repoType      The type of the repository.
     * @param targetRepoKey The key of the remote repository.
     * @param remoteUrl     The URL of the remote host.
     * @return The response of the remote member validation.
     * @throws ArtifactoryFederationServiceException Thrown when the validation of the remote member fails.
     */
    ValidateProcessRtfsResponse validateRemoteMember(String sourceRepoKey, String repoType, String targetRepoKey,
            String remoteUrl)
            throws ArtifactoryFederationServiceException;

    /**
     * Propagates a repository event to the Artifactory Federation Service.
     *
     * @param newConfig   The new federated repository config.
     * @param action      The action associated with the repository event.
     * @param projectKey  The project key.
     * @return List of error messages, empty if successful.
     */
    default List<String> propagateRepoEvent(FederatedRepoConfig newConfig, ActionStatus action, String projectKey)
            throws ArtifactoryFederationServiceException {
        return List.of();
    }

    /**
     * Activate full sync on a given repository in the federation service.
     *
     * @param repoKey   - The repository key
     * @param mirrorUrl - A specific member of which the full sync should be done
     * @return Response with a message on the status of the initiation of the full sync
     * @throws ArtifactoryFederationServiceException Upon exception during full sync initiation
     */
    FullSyncRtfsResponse runFullSync(String repoKey, String mirrorUrl, boolean isDry)
            throws ArtifactoryFederationServiceException;

    boolean shareMasterToken(String hostUrl, String jwtToken) throws ArtifactoryFederationServiceException;

    void changeFederatedUrl(String oldUrl, String newUrl) throws ArtifactoryFederationServiceException;

    /**
     * Runs get state list on RTFS
     *
     * @param statuses - List of specific statuses to include
     * @return StateListRtfsResponse with the state list of all the federations
     * @throws ArtifactoryFederationServiceException - Upon any error of communication or invalid response from RTFS
     */
    StateListRtfsResponse getStateList(List<String> statuses) throws ArtifactoryFederationServiceException;

    /**
     * Runs get state summary on RTFS
     *
     * @return StateSummaryRtfsResponse with the federation states summary
     * @throws ArtifactoryFederationServiceException - Upon any error of communication or invalid response from RTFS
     */
    StateSummaryRtfsResponse getStateSummary() throws ArtifactoryFederationServiceException;

    /**
     * Runs get connection states of the repo on RTFS
     *
     * @throws ArtifactoryFederationServiceException
     */
    ConnectionStatesRtfsResponse getConnectionStates(String repoKey) throws ArtifactoryFederationServiceException;

    RepoBinariesStateRtfsResponse getRepoBinariesState(String repoKey, String remoteUrl)
            throws ArtifactoryFederationServiceException;

    /**
     * Validates the local server information.
     * Those can happen when a non rtfs server requests to validate a server that is using RTFS
     *
     * @return The validation response information.
     * @throws ArtifactoryFederationServiceException - Upon any RPC exception
     */
    ValidateProcessRtfsResponse validateLocalServer(String localKey, String originRepoKey,
            String originUrl, boolean importInProgress, Long time) throws ArtifactoryFederationServiceException;

    /**
     * Propagate local repo event to RTFS.
     * Those can happen when a non rtfs server requests to perform a repo event on
     * a server that is using RTFS
     *
     * @throws ArtifactoryFederationServiceException - Upon any RPC exception
     */
    void propagateLocalRepoEvent(String localKey, String remoteKey, String originUrl, ActionStatus actionStatus,
            boolean enabled, List<FederatedMember> federatedMembers, long modificationDate)
            throws ArtifactoryFederationServiceException;

    /**
     * Propagate the legacy heartbeat info to RTFS.
     * Those can happen when a non rtfs server perform a heartbeat to a server that is using RTFS
     *
     * @return a map of repo per status
     * @throws ArtifactoryFederationServiceException - Upon any RPC exception
     */
    Map<String /*source-target*/, String /*status*/> heartbeatLegacy(String remoteUrl,
            List<HeartbeatLegacyRequest> heartbeatLegacyRequests)
            throws ArtifactoryFederationServiceException;

    void replaceUrlOnFederationService(String oldUrl, String newUrl) throws ArtifactoryFederationServiceException;

    RecoveryRtfsResponse runRecovery(String repoKey);

    boolean isRepoReady(String repoKey) throws ArtifactoryFederationServiceException;

    String runGetFileListTaskPath(String repoKey) throws ArtifactoryFederationServiceException;

    /**
     * Checks if the provided status reached the minimum required upon current status
     *
     * @param federationMigrationStatus - The status to check
     * @return true if reached, false otherwise
     */
    boolean hasMigrationStateReached(FederationMigrationStatus federationMigrationStatus);

    /**
     * @return true if events can be processed with federation client. False if not reached the status yet.
     */
    default boolean shouldProcessEventsToRTFS() {
        return false;
    }

    /**
     * @return true if the client is connected, false otherwise
     */
    boolean isRtfsClientConnected();

    /**
     * This method is called to avoid the @Async annotation being used in initArtifactoryFederationClient
     */
    void reconnectFederationClient() throws ArtifactoryFederationServiceException;

    CrossMemberResponse tunnelRequest(CrossMemberRequest request) throws RequestTunnelingException;

    /**
     * Update the priority of a repository
     *
     * @param repoKey  - The repository key
     * @param priority - The new priority
     * @return a {@link FederationPriorityUpdateRtfsResponse} that holds the status of the call
     */
    FederationPriorityUpdateRtfsResponse updateFederationPriority(String repoKey, RepoPriority priority);

    /**
     * Propagate events to the Artifactory Federation service using the provided client input.
     *
     * @param queueEvents the client input for propagating the event.
     */
    void propagateArtifactEvents(List<? extends QueueEvent> queueEvents);

    /**
     * Updates RTFS with the internal queue status
     *
     * @param queueStatus - The current status of the queue
     * @throws ArtifactoryFederationServiceException - Upon any error
     */
    void updateQueueStatus(String queueStatus) throws ArtifactoryFederationServiceException;

    /**
     * Updates RTFS about the propagate created time property to be enabled/disabled
     *
     * @param shouldPropagateCreatedTime - true if should be enabled, false otherwise
     * @throws ArtifactoryFederationServiceException - Upon any error
     */
    void updatePropagateCreatedTimeProperty(boolean shouldPropagateCreatedTime)
            throws ArtifactoryFederationServiceException;

    /**
     * Inserts a list of tasks to RTFS binaries tasks table
     *
     * @param tasks - The tasks
     * @throws ArtifactoryFederationServiceException - Upon any error
     */
    void insertBinariesTask(List<BinaryTask> tasks) throws ArtifactoryFederationServiceException;

    /**
     * Checks if the federation node exists in the topology
     * @return true if exists, false otherwise
     */
    boolean federationNodeExistsInTopology();

    /**
     * Get the failing tasks for a specific repository
     * @param repoKey The mandatory repository key
     * @param limit The limit of tasks to return
     * @param pageNumber The page number
     * @return A list of the failing tasks
     * @throws ArtifactoryFederationServiceException - Upon any error
     */
    Collection<PersistedRemoteTaskInfo> getFailingTasks(@NonNull String repoKey, Integer limit, Integer pageNumber)
            throws ArtifactoryFederationServiceException;

    /**
     * Delete the failing tasks for a specific repository and sha1
     * @param repoKey The mandatory repository key
     * @param sha1 The optional sha1, if null will delete all tasks for the repo
     * @return The number of rows deleted
     */
    int deleteFailingTasks(@NonNull String repoKey, String sha1) throws ArtifactoryFederationServiceException;

    /**
     * Reset the failing tasks for a specific repository and sha1
     * @param repoKey The mandatory repository key
     * @param sha1 The optional sha1, if null will reset all tasks for the repo
     * @return The number of rows reset
     */
    int replayFailingTasks(@NonNull String repoKey, String sha1) throws ArtifactoryFederationServiceException;
    /**
     * Get a list of binary tasks
     * @throws ArtifactoryFederationServiceException - Upon any error
     */
    List<PersistedRemoteTask> getBinaryTasks() throws ArtifactoryFederationServiceException;
    /**
     * Delete a list of binary tasks related to the provided task ids
     * @throws ArtifactoryFederationServiceException - Upon any error
     */
    int deleteBinaryTasks(List<Long> taskIds) throws ArtifactoryFederationServiceException;

    /**
     * Get the repositories that have been disabled by the system due to Metadata Negotiation failures.
     *
     * @return A set of repository keys
     */
    Set<String> getDisabledBySystemRepos();

    /**
     * Force remove a member from the all federated repositories
     * @param memberUrl
     * @return the response of the removal
     * @throws ArtifactoryFederationServiceException
     */
    MemberRemovalResponse forceRemoveMember(@NonNull String memberUrl) throws ArtifactoryFederationServiceException;

    TenantFederatedConfigSnapshot getFederatedConfigSnapshot() throws ArtifactoryFederationServiceException;

    ImportResponse tenantMoveImportToNewRegion(ImportRequest request) throws ArtifactoryFederationServiceException;

    ImportStatusResponse getImportStatus() throws ArtifactoryFederationServiceException;

}
