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

package org.artifactory.addon;

import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.ha.message.HaMessage;
import org.artifactory.addon.onboarding.model.OnBoardingRecord;
import org.artifactory.debug.threads.SingleThreadDump;
import org.artifactory.schedule.Task;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.fs.lock.FsItemsVault;
import org.jfrog.rtfs.dto.queue.QueueState;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mamo
 */
public interface HaAddon extends HaCommonAddon {

    String DEBIAN_RECALCULATE_ALL_NOW = "debianRecalculateAllNow";
    String DEBIAN_RECALCULATE_ALL_ASYNC = "debianRecalculateAllAsync";
    String DEBIAN_CACHE_UPDATE = "debianCacheUpdate";
    String XRAY_POLICIES_UPDATE = "xrayPoliciesUpdate";
    String OPKG_RECALCULATE_ALL_NOW = "opkgRecalculateAllNow";
    String OPKG_RECALCULATE_ALL_ASYNC = "opkgRecalculateAllAsync";
    String TRAFFIC_COLLECTOR = "trafficCollector";
    String PROPAGATE_TASK_EVENT = "propagateTask";
    String UI_DEPLOY = "uiDeploy";
    String SUPPORT_BUNDLE_GENERATE = "supportBundleGenerate";
    String LICENSE_CHANGED = "licenseChanged";
    String ADD_REPLICATION_LISTENER = "addReplicationListener";
    String REMOVE_REPLICATION_LISTENER = "removeReplicationListener";
    String PULL_REPLICATION_EVENTS = "pullReplicationEvent";
    String STOP_SHA2_MIGRATION = "stopSha256Migration";
    String ADD_COLUMN_MONITOR_MIGRATION = "addColumnMonitorMigration";
    String SYNC_STORAGE_SUMMARY = "syncStorageSummary";
    String SAML_LOGIN_ID = "samlLoginId";
    String AIR_GAP_IMPORT_BROWSE = "airGapBrowse";
    String AIR_GAP_IMPORT = "airGapImport";
    String TARGET_BUNDLE_RETENTION_OPERATION = "targetBundleRetentionOperation";
    String SYNC_STORAGE_USAGE = "syncStorageUsage";
    String SYNC_ALLOWED_REMOTE_URLS = "syncAllowedRemoteUrls";
    String SYNC_REMOTE_REPO_OFFLINE_TRIGGERS = "syncRemoteRepoOfflineTriggers";
    String SYNC_INBOUND_REQUEST_GUARD_TRIGGERS = "syncInboundRequestGuardTriggers";
    String SYNC_UNIVERSAL_LIMIT_BLOCK_LIST = "syncUniversalLimitBlockList";
    String PRUNE_STOP = "pruneStop";
    String SYNC_ON_BOARDING_RECORD = "syncOnBoardingRecord";
    String FULL_SYNC_ACTIVE_ON_FILE = "fullSyncActiveOnFile";

    String FULL_SYNC_REPO_KEYS = "fullSyncRepoKeys";

    @Override
    default void updateArtifactoryServerRole() {
    }

    /**
     * Propagate the federation queue stop to other HA members
     */
    default void propagateFederationQueueDeregister(String remoteMember, String remoteKey, String repoKey) {
    }

    @Override
    default void propagateLicenseChanges() {
    }

    @Override
    default boolean deleteArtifactoryServer(String serverId) {
        return false;
    }

    default void propagateTaskToAvailableAffinityNode(Task task) {
    }

    default void propagateTask(Task task, String serverId) {
    }

    default void initConfigBroadcast(ArtifactoryApplicationContext context) {
    }

    FsItemsVault getFsFileItemVault();

    FsItemsVault getFsFolderItemVault();

    /**
     * @return True if the current authenticated user is another HA node in the cluster
     */
    default boolean isHaAuthentication() {
        return false;
    }

    @Override
    default void propagateConfigReload() {
    }

    /**
     * @return True if the current member is active and can propagate events to other members
     */
    default boolean isCurrentMemberActive() {
        return false;
    }

    /**
     * Handle incoming propagation event from another server.
     *
     * @param haMessage The message propagated to this server from another cluster member
     */
    default void handleIncomingPropagationEvent(String topic, HaMessage haMessage) {
        throw new RuntimeException("Propagation is only supported on HA. Ignoring incoming propagation event");
    }

    default boolean isSamlLoginIdExist(String id, String serverId) {
        return false;
    }

    /**
     * Propagate OnBoardingRecords on creation or update to all the other servers.
     */
    default void syncOnBoardingRecord(OnBoardingRecord onBoardingRecord) {
    }

    default void init() {
    }

    default List<SingleThreadDump> propagateThreadDumps() {
        return new ArrayList<>();
    }

    /**
     * Propagate the init status of RTFS client
     */
    default void propagateInitOfRtfsEnableState(boolean rtfsEnabled){
    }

    /**
     * Propagate the queue reset for other HA members
     */
    default void propagateFederationQueueReset(QueueState queuePosition){
    }

    /**
     * Propagate the shift events to the member that responsible for shifting.
     * @param serverId - The remote ha member
     */
    default void propagateShiftEventsOperation(String serverId){
    }

    /**
     * Propagate the reconnect of the RTFS gRPC client to other HA members
     */
    default void propagateFederationClientReconnect() {
    }
}
