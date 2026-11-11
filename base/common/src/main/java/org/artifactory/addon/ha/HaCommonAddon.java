/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon.ha;

import lombok.NonNull;
import org.artifactory.addon.ha.message.HaMessage;
import org.artifactory.addon.ha.message.HaMessageTopic;
import org.artifactory.addon.ha.propagation.uideploy.UIDeployPropagationResult;
import org.artifactory.addon.ha.workitem.HaMessageWorkItem;
import org.artifactory.addon.replication.event.ReplicationEventQueueData;
import org.artifactory.addon.replication.event.ReplicationOwnerModel;
import org.artifactory.async.Async;
import org.artifactory.async.WorkQueueConfig;
import org.artifactory.common.addon.Addon;
import org.artifactory.common.guard.ConflictsGuardProvider;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.debug.DebugLevelConfig;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.policy.PolicyPropagationModel;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.repo.RepoPath;
import org.artifactory.schedule.JobName;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.util.HttpUtils;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.support.rest.model.SupportBundleGenerationResult;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.artifactory.addon.ha.HaWorkQueueInfoConfig.HA_EVENT_MESSAGES_WQ_NAME;

/**
 * @author mamo, gidi, fred
 */
@Entitlement(EntitlementType.HA)
public interface HaCommonAddon extends Addon, ConflictsGuardProvider<String> {

    /**
     * Response header for an HA Artifactory Node
     */
    String ARTIFACTORY_NODE_ID = "X-Artifactory-Node-Id";

    String SUPPORT_BUNDLE_SEMAPHORE_NAME = "supportBundleSemaphore";
    String STATS_SEMAPHORE_NAME = "flushStatsSemaphore";
    String STATS_REMOTE_SEMAPHORE_NAME = "flushRemoteStatsSemaphore";
    String INDEXING_SEMAPHORE_NAME = "indexingSemaphore";
    String INDEX_MARKED_ARCHIVES_SEMAPHORE_NAME = "indexMarkedArchivesSemaphore";
    String XRAY_EVENTS_SEMAPHORE_NAME = "xrayEventsSemaphore";

    String AFFINITY_ANY = "any";
    String AFFINITY_NONE = "none";

    int DEFAULT_SEMAPHORE_PERMITS = 1;

    // we block concurrent executions to prevent race condition
    // between delete(async) and list(),
    //
    // also there is no justification to perform concurrent execution
    // as we support taking several thread dumps with or without interval
    int SUPPORT_BUNDLE_SEMAPHORE_INITIAL_PERMITS = 1;

    /**
     * Determines if HA is enabled.
     *
     * @return {@code true} if the current Artifactory instance is HA enabled
     */
    default boolean isHaEnabled() {
        return false;
    }

    /**
     * @return {@code true} if HA is enabled and activated, and the current Artifactory instance has affinity for the
     * given task name, or if there are no affinity settings at all.
     */
    default boolean isAllowedToRun(JobName jobName) {
        return isAllowedToRun(jobName, true, false);
    }

    /**
     * @return {@code true} if HA is enabled and activated, and the current Artifactory instance has affinity for the
     * given task name, or if there are no affinity settings at all.
     */
    default boolean isAllowedToRun(JobName jobName, boolean hardAffinity) {
        return isAllowedToRun(jobName, true, hardAffinity);
    }

    /**
     * @param hardAffinity    whether or not to allow affinity-fallback to other nodes in the cluster if the affinity
     *                        node
     *                        is not currently active
     * @param allowedAffinity whether the job to check is allowed affinity settings
     * @return {@code true} if HA is enabled and activated, and the current Artifactory instance has affinity for the
     * given task name, or if there are no affinity settings at all.
     */
    default boolean isAllowedToRun(JobName jobName, boolean allowedAffinity, boolean hardAffinity) {
        return true;
    }

    /**
     * @return {@code true} if HA is configured.
     */
    default boolean isHaConfigured() {
        try {
            return ArtifactoryHome.get().isHaConfigured();
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Similar to {@link HaCommonAddon#notify(HaMessageTopic, HaMessage)}, but async with a work queue.
     * To aggregate events, we implement the workItem as always, hashcode and equals are the same as the uniqueKey,
     * however,
     * if you don't want to aggregate the events but we do want to make sure only one worker will work on a topic, you
     * should pass a workItem implementation that it's uniqueKey is the subject (worker per subject), but the equals and
     * hashcode are the actual content and not the subject name.
     */
    @Async(delayUntilAfterCommit = true, workQueue = @WorkQueueConfig(name = HA_EVENT_MESSAGES_WQ_NAME))
    void notifyAsync(HaMessageWorkItem workItem);

    //todo move into HaAddon, should not be a common interface
    default void notify(HaMessageTopic haMessageTopic, HaMessage haMessage) {
    }

    /**
     * @return A unique hostId for a Pro/OSS installation, a hashed token for an HA node
     */
    default String getHostId() {
        return HttpUtils.getHostId();
    }

    default SessionRepository<? extends Session> createSessionRepository(int sessionDurationSecs) {
        MapSessionRepository mapSessionRepository = new MapSessionRepository(new ConcurrentHashMap<>());
        mapSessionRepository.setDefaultMaxInactiveInterval(Duration.of(sessionDurationSecs, ChronoUnit.SECONDS));
        return mapSessionRepository;
    }

    default void shutdown() {
    }

    default List<ArtifactoryServer> getAllArtifactoryServers() {
        return new ArrayList<>();
    }

    boolean deleteArtifactoryServer(String id);

    default boolean artifactoryServerHasHeartbeat(ArtifactoryServer artifactoryServer) {
        return false;
    }

    default String getCurrentMemberServerId() {
        return null;
    }

    /**
     * Notify other nodes to update metadata cache for {@param path} so next index requests run on correct copy of
     * metadata
     */
    default void propagateDebianUpdateCache(RepoPath path) {
    }

    default void propagateOpkgReindexAll(ArtifactoryServer server, String repoKey, boolean async, boolean writeProps) {
    }

    /**
     * Notify other nodes to update Xray client cache for repositories policies
     */
    default void propagateXrayPolicyChangeNotification() {
    }

    /**
     * In case where a UI upload request was started on another node (which means the file is in its local temp dir)
     * this call will propagate the second deploy request (when user presses the 'upload' button) to the relevant
     * {@param nodeId}.
     *
     * @param payload - The deploy request
     */
    default UIDeployPropagationResult propagateUiUploadRequest(String nodeId, String clientAddress, String payload,
            String oauth) {
        return null;
    }

    default void propagateArtifactoryEncryptionKeyChanged() {
    }

    /**
     * Tell other node to activate a license with excluding a specific license
     *
     * @param server      The server to communicate with
     * @param skipLicense The licenses to skip while trying to activate a license
     */
    default void propagateActivateLicense(ArtifactoryServer server, Set<String> skipLicense) {
    }

    default void propagateFederatedRepoOnline() {
    }

    default void propagatePluginReload() {
    }

    default void propagateMirrorMetadataInvalid(String packageType, String memberUrl, String proxyName) {
    }

    default void propagateMigrationStatusChangeOfFederationService(
            FederationMigrationStatus federationMigrationStatus) {
    }

    default List<SupportBundleGenerationResult> propagateGenerateSupportBundle(String bundleInfo, int timeout) {
        return Collections.emptyList();
    }

    default Boolean propagateFullSyncRunningOnFileOnOtherNodes(RepoPath path, int timeout) {
        return false;
    }

    default <T> Set<String> getFullSyncingRepos(Class<T> clazz) {
        return Set.of();
    }

    default <T> List<T> propagateTrafficCollector(long startLong, long endLong, List<String> ipsToFilter,
            List<ArtifactoryServer> servers, Class<T> clazz) {
        return null;
    }

    default <T> List<T> propagateTasksList(List<ArtifactoryServer> servers, Class<T> clazz) {
        return null;
    }

    /**
     * Issues a GET request to the given {@code uri} on each of the provided HA member servers in parallel,
     * deserialises each response body as {@code clazz}, and returns the collected results.
     * Servers that do not respond within the propagation timeout are silently skipped.
     * The default implementation (non-HA mode) returns an empty list.
     */
    default <T> List<T> propagateGetRequest(List<ArtifactoryServer> servers, String uri, String event,
            Class<T> clazz) {
        return Collections.emptyList();
    }

    void propagateConfigReload();

    default void propagateRepoConfigsChanges(RepoConfigPropagationModel model) {
    }

    default void propagatePolicyCronChanges(PolicyPropagationModel model) {
    }

    default void propagateDebugLevelChange(DebugLevelConfig debugLevelConfig) {
    }

    default void propagateCurationInvalidateSettingsCache() {
    }

    default void retryPropagateRepoConfigsUpdates() {
    }

    default void propagateSystemConfigReload() {
    }

    default void propagateQueryRateLimiterRecalculation() {
    }

    default void propagateRequestRateLimiterRecalculation() {
    }

    default void propagateGroupCacheInvalidation(String groupName) {

    }

    /**
     * Notifies other HA nodes that the transparent-repo config table has changed and their cache
     * needs to re-sync.
     * <p>
     * Behaviour is selected by the {@code repoKey} argument:
     * <ul>
     *   <li><b>{@code repoKey} is non-blank</b> — targeted single-key refresh. Cheaper hot path for
     *   singular insert/delete; the receiver only re-reads that one entry from the DB.</li>
     *   <li><b>{@code repoKey} is {@code null} or blank</b> — full reload. Used after a bulk
     *   transition so each peer does one re-sync instead of N per-key invalidations.</li>
     * </ul>
     * Best-effort: errors are logged but not thrown — peers will also self-heal via the cache's
     * {@code expireAfterWrite} window.
     */
    default void propagateTransparentRepoCacheChange(String repoKey) {
    }

    void updateArtifactoryServerRole();

    /**
     * Used to inform other nodes that the artifactory.cluster.licenses file has changed and a reload of the license
     * cache is required
     */
    void propagateLicenseChanges();

    ConflictGuard getConflictGuard(String key);

    void propagateReplicationListener(ReplicationOwnerModel replicationChannelModel);

    @Async
    void propagateRemoveReplicationListener(ReplicationOwnerModel replicationChannelModel);

    @Async
    void propagateReplicationEvents(String target, ReplicationEventQueueData item);

    void propagateStopSha256Migration(long sleepIntervalMillis);

    void propagateAddColumnMonitorMigration();

    default void propagateStorageSummaryCache(StorageSummaryInfo cache) {
    }

    default void propagateStorageUsageChanges() {
    }

    default void propagateAllowedRemoteUrlsSync() {
    }

    /**
     * Notify other HA nodes to reload the remote-repo offline triggers config (URL substrings, header names, mode)
     * from the DB. Used after an admin PUT to {@code /system/remoteRepoOfflineTriggers} so peers pick up the change
     * immediately instead of on their next guard cron tick.
     */
    default void propagateRemoteRepoOfflineTriggersChange() {
    }

    /**
     * Notify other HA nodes to reload the inbound-request guard triggers config (header names, user-agent substrings,
     * mode) from the DB. Used after an admin PUT to {@code /system/inboundRequestGuardTriggers} so peers enforce the
     * same rules without restart.
     */
    default void propagateInboundRequestGuardTriggersChange() {
    }

    /**
     * Notify other HA nodes to add the given OOM-recovery query blocklist patterns to their effective
     * blocklist. Used when a node activates its blocklist on startup (after detecting persisted
     * exceeding-rows violations) so the whole cluster enforces the same query limits without waiting
     * for each node to independently OOM.
     */
    default void propagateOomQueryBlockListChange(Set<String> blocklistPatterns) {
    }

    default void propagateGemsVersionsReindex(String repoKey) {
    }

    default void propagateGitInitRepository(String repoKey) {
    }

    default void propagateGitSyncRepository(String repoKey) {
    }

    default void propagateGitDeleteRepository(String repoKey) {
    }

    @NonNull
    default <T> List<T> propagateBrowseImportDirectory(@NonNull Class<T> clazz) {
        return Collections.emptyList();
    }

    default boolean propagateAirGapImportFromFileSystem(@NonNull String fileName, boolean dryRun) {
        return false;
    }

    @NonNull
    default Map<String, String> propagateTargetBundleRetentionOperation(@NonNull String operationModel) {
        return Map.of();
    }

    /**
     * Check if rollback migration from the federation service to artifactory is blocked
     * either because nodes are not reachable or because nodes are still using federated binary provider v2
     * @return true if rollback migration is blocked, false otherwise
     */
    default Boolean propagateCheckIfFederatedBinaryProviderV2IsUsed(){
        return false;
    }
}
