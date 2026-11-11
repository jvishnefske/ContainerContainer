package org.artifactory.federation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.async.Async;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.descriptor.repo.FederatedMember;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.federation.model.RepoStatus;
import org.artifactory.federation.model.State;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.GridTopology;
import org.artifactory.model.QueuePosition;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ActionStatus;
import org.artifactory.repo.ArtifactoryFederationServiceException;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.repo.FederationPriorityUpdateRtfsResponse;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoPriority;
import org.artifactory.repo.UrlReposDetails;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.EntitlementsEventListener;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.queue.services.FederatedPersistentQueue;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.jfrog.access.client.platformconfig.ConfigurationModel;
import org.jfrog.rtfs.dto.member.FederatedConfigInfo;
import org.jfrog.rtfs.dto.member.MemberQueueState;
import org.jfrog.rtfs.dto.member.MirrorMemberInfo;
import org.jfrog.rtfs.dto.migration.MigrationVerboseStatus;
import org.jfrog.rtfs.dto.properties.NodePath;
import org.jfrog.rtfs.dto.properties.Properties;
import org.jfrog.rtfs.dto.queue.QueueState;
import org.jfrog.rtfs.dto.tasks.TaskRequest;
import org.jfrog.rtfs.dto.tenant.snapshot.TenantMoveStatus;
import org.jfrog.rtfs.dto.traffic.ExtendedInfo;
import org.jfrog.rtfs.module.member.MemberRemovalResponse;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Alexei Vainshtein
 */
public interface FederatedRepoService extends EntitlementsEventListener, ContextCreationListener, ReloadableBean {

    /**
     * Handles the propagation of a repository event with RTFS, attempting to update remote members for a federated
     * repository based on the provided action and new config.
     * Throws a FederatedRemoteMembersUpdateException if the update fails.
     *
     * @param action    The ActionStatus indicating the type of repository event.
     * @param newConfig The new FederatedRepoConfig containing the updated information.
     */
    void handlePropagateRepoEventWithRTFS(ActionStatus action, FederatedRepoConfig newConfig);

    /**
     * Resets stale full sync operations for any federated repository.
     * Stale full operations are those that have been persisted as {@link NodeEventCursorStatus#FS_RUNNING} in the
     * database, but in fact, has no indication of running in memory.
     * This method resets any stale full sync operation, allowing for a fresh full sync attempt to be initiated.
     */
    void resetStaleFullSyncOperations();

    /**
     * Fetch from each remote url the existed federated repos.
     *
     * @param urls - The remote urls to check
     * @return a list that contains a json with url and federation repos of that url.
     */
    List<FederationReposResponse> getReposFromRemote(List<String> urls, String proxyName);

    /**
     * Fetch from each remote url the existed Baseurl or federated baseurl if existed, and federated repos.
     *
     * @param urls - The remote urls to check
     * @return a list that contains a json with url and federation repos of that url.
     */
    List<FederationReposResponse> getBaseUrlAndReposFromRemote(List<String> urls, String proxyName);

    /**
     * Checks if the package type provided is supported for federated repositories.
     * {@link BadRequestException} is thrown if the package type is unsupported.
     */
    void validateFederationPackageTypeSupported(RepoType repoType);

    /**
     * @return a list of all federated repositories and the info per repo
     */
    List<RepoDetails> getRepos();

    /**
     * @return a baseUrl and list of all federated repositories and the info per repo
     */
    UrlReposDetails getBaseUrlAndRepos();

    /**
     * @return the token that associated with remote url and the key provided
     */
    String getToken(String remoteUrl, String key);

    /**
     * Validates the given repository.
     *
     * @param key                      - repo name
     * @param clientBaseUrl            - base url of the requesting server
     * @param timeFromRequestingServer - the current time of the server that is requesting the validation.
     * @param repoType                 - the type of the repository to validate
     * @param sourceRepo               - the source repository to validate
     * @param reposImportInProgress    - flag indicating whether an import operation is in progress for any repository
     *                                 If reposImportInProgress is set to true, the validation process may need to
     *                                 take into account the fact that an import operation is currently in progress
     *                                 for some repository. For example, it may need to ensure that the repository
     *                                 being validated does not conflict with repositories being imported by other
     *                                 members.
     * @param rtfsLocalRequest         - True if the request came from federation service/false otherwise
     * @param projectKey               - The project key associated with the repository
     */
    void validate(String key, String clientBaseUrl, long timeFromRequestingServer, String repoType, String sourceRepo,
            boolean reposImportInProgress, boolean rtfsLocalRequest, String projectKey);

    /**
     * Checks if the license provided and subscription (if AOL) are enterprise and above.
     * {@link ForbiddenException} is thrown if the license/subscription are not enterprise and above.
     */
    void validateFederationLicenseInstalled();

    String getBaseUrl();

    /**
     * Validates the full remote member url
     *
     * @param fullUrl - The remote url including the repository to validate
     *                  (i.e. https://customer.jfrog.io/artifactory/fed-repo)
     */
    void validateRemoteServer(String fullUrl, String proxyName, String repoType, String sourceRepoKey);

    /**
     * Migrates local repository to a federated repository
     *
     * @param repoKey - The repository name that need to be migrated.
     */
    default void migrate(String repoKey) {

    }

    /**
     * Converts federated repository to a local repository
     *
     * @param repoKeys - The repository names that need to be converted to local.
     */
    default HashMap<String, String> convertFederatedToLocal(List<String> repoKeys) {
        return new HashMap<>();
    }

    /**
     * Returns whether federated repo is ready to use
     */
    default boolean isActive(String repoKey) {
        return false;
    }

    /**
     * Returns a json that include all the metadata from all the package types provider + the basic config
     *
     * @param types - All the package types that metadata need to be calculated for
     * @return a json with all the metadata info
     */
    default String getMetadata(Collection<String> types) {
        return StringUtils.EMPTY;
    }

    /**
     * Refreshes all master tokens with (configurable) near future expiry dates.
     */
    void refreshMasterTokens();

    default void invalidateMetadata(String packageType, String memberUrl, String proxyKey) {}

    void addInconsistentRepoMember(String memberUrl, String repoKey);

    Set<String> getInconsistentRepos();

    void removeInconsistentRepoMember(String memberUrl);

    void addImportsConflictErrors(Set<String> repos);

    Set<String> getImportsConflictErrors();

    /**
     * Remove an inconsistent repo from the list by repo key.
     *
     * @param repoKey - The repo key to remove from the list
     */
    default void removeInconsistentRepoByRepoKey(String repoKey) {

    }

    void saveAuthenticationMasterToken(String tokenId, String tokenType, String scope, String baseUrl,
            String exchangeUrl, String accessToken, Long expiresIn);

    Collection<AuthenticationToken> getPairedAuthenticationTokens();

    Optional<AuthenticationToken> getPairedAuthenticationToken(String baseUrl);

    void deleteAuthenticationToken(String baseUrl);

    FullSyncStatus getFullSyncStatus(@Nonnull String mirrorRemoteUrl);

    void putFullSyncStatus(@Nonnull String mirrorRemoteUrl, @Nonnull FullSyncStatus syncStatus);

    void invalidateFullSyncStatus(@Nonnull String mirrorRemoteUrl);

    /**
     * checks if the build info repo is federated.
     *
     * @return return true is the build info repo is federated otherwise returns false
     */
    boolean isProjectBuildInfoRepoFederated(String projectKey);

    /**
     * Validates the members collection for a federated repository (e.g. receiver mode rules).
     * Runs collection-level validators before RTFS or legacy validation in the config-change flow.
     *
     * @param repoDescriptor the federated repository descriptor whose members to validate
     * @deprecated use methods with FederatedRepoConfig instead
     */
    @Deprecated(forRemoval = true)
    default void validateMembers(FederatedRepoDescriptor repoDescriptor) {
    }

    /**
     * Validate there aren't any member that is disabled
     *
     * @param newRepoDescriptor - The descriptor to get the members from.
     *                          If a disabled member is found, a {@link FederatedRemoteMembersUpdateException thrown}
     * @deprecated use {@link #validateNoDisabledMember(List)} instead
     */
    @Deprecated(forRemoval = true)
    default void validateNoDisabledFederationMember(FederatedRepoDescriptor newRepoDescriptor) {
    }

    /**
     * Validates that there are no disabled members in the list.
     * Parallel to {@link #validateNoDisabledFederationMember(FederatedRepoDescriptor)} for federated repos.
     *
     * @param members - The members to validate
     *                If a disabled member is found, a {@link FederatedRemoteMembersUpdateException} is thrown
     */
    default void validateNoDisabledMember(List<org.artifactory.repo.federated.FederatedMember> members) {
    }

    /**
     * Checks if status has changed of the local member
     *
     * @param repoKey    - The repo key of the local member
     * @param newMembers - The new members
     * @param oldMembers - The old members
     * @return true if changed, false otherwise
     */
    default boolean isStatusChanged(String repoKey, List<FederatedMember> newMembers, Set<FederatedMember> oldMembers) {
        return false;
    }

    default boolean isModeChanged(String repoKey, List<FederatedMember> newMembers, Set<FederatedMember> oldMembers) {
        return false;
    }

    /**
     * Checks if status changed for RepoConfig model (enabled/disabled).
     * Parallel to isStatusChanged but for org.artifactory.repo.federated.FederatedMember.
     */
    default boolean isStatusChangedForConfig(
            String repoKey,
            List<org.artifactory.repo.federated.FederatedMember> newMembers,
            Set<org.artifactory.repo.federated.FederatedMember> oldMembers,
            String baseUrl) {
        return false;
    }

    /**
     * Checks if mode changed for RepoConfig model.
     * Parallel to isModeChanged but for org.artifactory.repo.federated.FederatedMember.
     */
    default boolean isModeChangedForConfig(
            List<org.artifactory.repo.federated.FederatedMember> newMembers,
            Set<org.artifactory.repo.federated.FederatedMember> oldMembers,
            String repoKey,
            String baseUrl) {
        return false;
    }

    default void deRegister(String repoKey, String remoteUrl) {}

    default void enableRepos(List<String> repoTypes) {}

    /**
     * Gets the state of all the federated mirrors that are on this instance
     *
     * @return A set of all the states
     */
    default Set<State> getMirrorStates() {
        throw new NotImplementedException("Method not implemented");
    }

    /**
     * Gets the state of all the federated mirrors that are currently {@link RepoStatus#FULL_SYNC_RUNNING} on this
     * instance.
     *
     * @return A set of all running states
     */
    default Set<State> getRunningMirrorStates() {
        throw new NotImplementedException("Method not implemented");
    }

    default Properties getProperties(List<NodePath> nodePaths, String repoKey) {
        throw new NotImplementedException("Method not implemented");
    }

    default ExtendedInfo getExtendedInfo(String repoKey) {
        throw new NotImplementedException("Method not implemented");
    }


    /**
     * Triggers recovery for the all federated repositories.
     * This method attempts to recover federated queues from various error states,
     * if qualified.
     *
     * @param manualTrigger True if triggered via REST API, false if triggered by the async AutoHealing task.
     */
    default void triggerRecovery(boolean manualTrigger) {
        throw new NotImplementedException("Method not implemented");
    }

    /**
     * Triggers recovery for the specified repository key via REST API.
     * This method attempts to recover federated queues associated with the input repository key
     * from various error states, if qualified.
     *
     * @param repoKey The repository key to trigger recovery for.
     */
    default void triggerManualRecoveryForRepository(String repoKey) {
        throw new NotImplementedException("Method not implemented");
    }

    /**
     * Collects and return the config information that is relevant for federation
     * Such as: repo key, members, version and if the repo enabled or disabled
     *
     * @return {@link FederatedConfigInfo} that represents the information
     */
    List<FederatedConfigInfo> getFederatedRepoConfigInfo();

    /**
     * Collects and returns the federation config information for a single federated repo.
     * Such as: repo key, members, version and if the repo enabled or disabled
     *
     * @param repoKey the federated repo key
     * @return the {@link FederatedConfigInfo} for the repo, or {@code null} if no matching federated repo exists
     */
    FederatedConfigInfo getFederatedRepoConfigInfo(String repoKey);

    /**
     * Replace the url from old to new on the federation server
     *
     * @param oldUrl - The old base url
     * @param newUrl - The new base url
     * @throws ArtifactoryFederationServiceException - upon error that occurs like connection with RTFS
     */
    void replaceUrlOnFederationService(String oldUrl, String newUrl) throws ArtifactoryFederationServiceException;

    /**
     * @return the last id in the system. It combines the last event in the node_events table with total of events in
     * the node events temp table. This is required for buffering to catch all the events that during shift events.
     */
    long lastIdInTheSystem();

    /**
     * Check for the given repo and remote url how many events left
     * in the queue for the given cursor from the cursor marker to the provided event number.
     *
     * @param event - Object holds the info for the remote url, repo key and the event id
     * @return the amount of events left to reach
     */
    Long remainingEventsInQueue(QueuePosition event);

    /**
     * Stop queue for the given repo and the remote url that is the variant
     *
     * @param remoteUrl - The remote url
     * @param remoteKey - The remote repo key
     * @param repoKey   - The source repository name
     */
    default void queueStop(String remoteUrl, String remoteKey, String repoKey) {
    }

    /**
     * Checks if Artifactory Federation Service (RTFS) is enabled and should process events to RTFS.
     * This method checks within the federation client if the client enabled and also
     * the current status has reached the minimum of {@link FederationMigrationStatus.MIGRATING_EVENTS_TO_RTFS}
     *
     * @return true if the Artifactory should process events to RTFS, otherwise false.
     */
    default boolean shouldProcessEventsToRTFS() {
        return false;
    }

    /**
     * Removes the queue completly including cursor, in memory queue and delete also errors
     *
     * @param repoKey         - The repo key
     * @param remoteMemberUrl - The full url of the member including repo and url
     */
    void deregisterQueue(String repoKey, String remoteMemberUrl, String remoteMemberKey);

    /**
     * Deregisters the federated-replication queues for every remote member of {@code repoKey}.
     * Failures are swallowed (logged at debug + error) so a queue cleanup cannot block a repo deletion.
     */
    void deregisterRemoteMemberQueues(String repoKey,
            List<org.artifactory.repo.federated.FederatedMember> members);

    /**
     * Starting the queue operation for the provided queue info
     *
     * @param queueInfo           - The queue information to start the queue
     * @param receivedFromOtherHa - If the request comes internally from one of the HA members or externally from a
     *                            user call
     * @param timeToResetCursor   - The timestamp to find the evnet id in the node events table
     */
    default void queueResetTo(QueueState queueInfo, boolean receivedFromOtherHa, Long timeToResetCursor){

    }

    NodeEventCursorStatus getInternalQueueCursorStatus();

    /**
     * Change the queue status to the provided status for all queues associated with the local repo key
     *
     * @param status         - The new status to change to
     * @param localRepoKey        - The source repository name
     */
    void updateInternalQueuesStatus(NodeEventCursorStatus status, String localRepoKey);

    /**
     * Enable or disable federation service client.
     *
     * @param rtfsEnabled         - true if to enable federation service client, false to disable
     * @param receivedFromOtherHa - If the request comes from internal HA member. This is required to have
     *                            the ability to distinguish between first request or propagated HA request
     */
    void initFederationClient(boolean rtfsEnabled, boolean receivedFromOtherHa)
            throws ArtifactoryFederationServiceException;

    /**
     * Attempts to reconnect to the federation client, if a client already exists it will recreate.
     * When {@code internal} is {@code false} (user or health-check initiated), the reconnect is also
     * propagated to all other HA members. When {@code true} (call received from another HA member),
     * only the local reconnect is performed.
     */
    void reconnectFederationClient(boolean internal) throws ArtifactoryFederationServiceException;

    /**
     * Checks if the rtfs client failed during the initial connection attempt, returns false by default
     */
    boolean isRtfsClientConnected();

    /**
     * @return the current status of federation client see {@link FederationMigrationStatus} for the available statuses
     */
    FederationMigrationStatus getCurrentFederationStatus();

    /**
     * Update the federation client status. Only allowed if the client was init from before.
     *
     * @param status              - The new status
     * @param receivedFromOtherHa - If the request comes from internal HA member. This is required to have
     *                            the ability to distinguish between first request or propagated HA request
     */
    void updateFederationStatus(FederationMigrationStatus status, boolean receivedFromOtherHa);

    /**
     * @return the status of the tenant move process
     */
    @Nonnull
    TenantMoveStatus getTenantMoveStatus();

    /**
     * @return the internal queue state
     */
    MemberQueueState getInternalQueueState();

    /**
     * Updates the federation service cursor to the given state
     * and the marker set to the event relevant for the provided time
     *
     * @param state - The new state
     * @param time  - The time to reset the marker
     */
    void updateCursor(MemberQueueState state, Long time);

    /**
     * Updates the federation service cursor to the given cursor status
     *
     * @param cursorStatus - The new status
     */
    void updateCursorStatus(NodeEventCursorStatus cursorStatus);

    /**
     * Registers the federation service queue if it has not been registered yet.
     *
     * @return the federation service persistent queue
     */
    FederatedPersistentQueue registerFederationServiceQueueIfNeeded();

    /**
     * Update the enabled state of a remote member in a federated repo
     * * @param localRepoKey      - The local repository key
     *
     * @param mirrorMemberInfo - The member info to update
     * @param newStatus        - The new status to set for the member
     * @param version          - The most recent modification date of the updated configuration;
     *                           If null, the current time will be used
      * @throws ArtifactoryFederationServiceException
     */
    Response updateMemberStatus(String localRepoKey, MirrorMemberInfo mirrorMemberInfo, boolean newStatus,
            @Nullable Long version) throws ArtifactoryFederationServiceException;

    /**
     * take a snapshot of all federated repos and save it in db
     * @return {@link Response} that represents the response
     */
    Response snapshotFederatedRepos() throws ArtifactoryFederationServiceException;

    /**
     * This is used to store the master token as a config item set in Access platform config.
     *
     * @param accessToken the actual token to store.
     */
    record MasterTokenConfig(String accessToken) implements ConfigurationModel {}

    /**
     * @return true if the binary tasks feature is enabled, false otherwise
     */
    boolean isBinaryTasksEnabled();

    /**
     * Update the binary tasks feature enabled/disable state.
     *
     * @param enabled true to enable the feature, false to disable it.
     */
    void updateBinaryTasksFeatureStatus(boolean enabled);

    /**
     * Update the federation priority of the given repository.
     *
     * @param repoKey  - The repository key to update the priority for
     * @param priority - The new priority status
     */
    FederationPriorityUpdateRtfsResponse updateFederationPriority(String repoKey, RepoPriority priority)
            throws ArtifactoryFederationServiceException;

    /**
     * Init the client in async, connects, starts the internal queue and update the status
     */
    @Async
    void initFederationClientAsync();

    void submitTask(TaskRequest request);

    /**
     * @return true if the federated-binary-provider-V2 is active.
     */
    boolean isFederatedBinaryProviderV2Active();

    /**
     * The RTFS client is ready assuming {@code 'rtfs.enabled'} key is set to 'true' in config table,
     * and the client has reached migration status {@link FederationMigrationStatus#MIGRATION_COMPLETED}.
     *
     * @return true if the federation client is ready.
     */
    boolean isMigrationOver();

    /**
     * enable the federation service client in async
     */
    @Async
    void enableRtfsAsync();

    /**
     * Add a key-value config to the configs table
     */
    void addOrUpdateConfig(ConfigsServiceKey configKey, String data);

    void deleteConfig(ConfigsServiceKey configKey);

    String getConfig(ConfigsServiceKey configKey);

    /**
     * update the tenant queue status in RTFS
     */
    void updateRtfsQueueStatus(NodeEventCursorStatus status) throws ArtifactoryFederationServiceException;

    /**
     * Update the propagate created time property to enabled/disable state.
     */
    void updatePropagateCreatedTimeProperty(boolean enabled) throws ArtifactoryFederationServiceException;

    /**
     * force remove a member from all federated repos
     */
    MemberRemovalResponse forceRemoveMember(String memberUrl) throws ArtifactoryFederationServiceException;

    /**
     * send a sync message to each member to check that the repos are synced
     */
    void runMembersRepoSync();

    /**
     * @return the current migration status
     */
    MigrationVerboseStatus getMigrationVerboseStatus();

    /**
     * Upsert the verbose migration status.
     *
     * @param migrationVerboseStatus The verbose migration status to upsert.
     */
    void setMigrationVerboseStatus(MigrationVerboseStatus migrationVerboseStatus);

    void updateLocalMemberStatus(String repoKey, boolean enable);

    /**
     * Handles grid topology change notifications from the Grid service.
     * This updates the database with the new grid topology data and invalidates the cache.
     * This is a Pro-only feature.
     *
     * @param gridTopology The grid topology data (null for DELETED events)
     * @param changeType The type of change (CREATED, UPDATED, or DELETED)
     * @param gridTopologyId The grid topology ID (required for all event types)
     */
    default void handleGridTopologyChange(GridTopology gridTopology,
            GridTopologyChangeType changeType,
            String gridTopologyId) {
        throw new UnsupportedOperationException("Grid topology changes are only available in Pro");
    }

    /**
     * Checks if a repository exists by its key.
     *
     * @param repoKey The repository key to check.
     * @return true if the repository exists, false otherwise.
     */
    boolean repoExists(String repoKey);

    /**
     * Cleanup of a dangling child reference from federated virtual repos. Runs asynchronously:
     * queries remote federation members; if the child does not exist on any member, removes the
     * ref from the virtual repo and persists (which triggers sync to members).
     *
     * @param virtualRepoKey the federated virtual repo that may still reference the child
     * @param removedRepo    the repository that was removed (type determines which refs are cleared)
     */
    default void cleanupDanglingRefAsync(String virtualRepoKey, RepoConfig removedRepo) {
    }

    /**
     * Runs one retry pass of dangling child-ref cleanup on a federated virtual (e.g. JFBus consumer).
     *
     * @return {@code true} if nothing left to do on a later pass (removed, already clean, or not applicable);
     *         {@code false} if another retry may be needed (remote check inconclusive / members unreachable).
     */
    default boolean runDanglingRefCleanupRetry(String virtualRepoKey, String childRepoKey) {
        return true;
    }

}
