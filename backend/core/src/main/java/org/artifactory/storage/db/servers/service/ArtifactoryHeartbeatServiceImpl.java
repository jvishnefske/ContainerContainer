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

package org.artifactory.storage.db.servers.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.ClusterOperationsService;
import org.artifactory.addon.license.ArtifactoryHaLicenseDetails;
import org.artifactory.addon.license.LicenseOperationStatus;
import org.artifactory.addon.license.VerificationResult;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.metrics.jobs.CallHomeJob;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUser;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.Reloadable;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.state.model.ArtifactoryStateManager;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;
import static org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService.hasHeartbeat;
import static org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService.isOther;
import static org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService.isRunning;
import static org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService.qualifiesForDeletion;

/**
 * @author gidis
 */
@Slf4j
@Service
@Reloadable(beanClass = ArtifactoryHeartbeatService.class, initAfter = {TaskService.class,
        ArtifactoryBinaryService.class},
        listenOn = CentralConfigKey.none)
public class ArtifactoryHeartbeatServiceImpl implements ArtifactoryHeartbeatService, ReloadableBean,
        ContextCreationListener {

    @Autowired
    private TaskService taskService;

    @Autowired
    private ArtifactoryServersCommonService serversService;

    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void init() {
    }

    @Override
    public void destroy() {
        taskService.cancelTasks(HeartbeatJob.class, true);
        taskService.cancelTasks(CallHomeJob.class, true);
    }

    /**
     * creates & starts HeartbeatJob
     */
    private void registersHeartbeatJob() {
        Task heartbeatJob = TaskUtils.createRepeatingTask(HeartbeatJob.class,
                TimeUnit.SECONDS.toMillis(ConstantValues.haHeartbeatIntervalSecs.getLong()),
                TimeUnit.SECONDS.toMillis(ConstantValues.haHeartbeatIntervalSecs.getLong()));
        taskService.startTask(heartbeatJob, false);
    }

    /**
     * creates & starts CallHomeJob
     */
    private void registerCallHomeJob() {
        String callHomeQuarzExpression = CallHomeJob.buildRandomQuartzExp();
        Task callHomeJob = TaskUtils.createCronTask(
                CallHomeJob.class,
                callHomeQuarzExpression
        );
        log.debug("Scheduling CallHomeJob to run at '{}'", callHomeQuarzExpression);
        taskService.startTask(callHomeJob, false);
    }

    @Override
    public void updateHeartbeat() {
        String serverId = "unknown server id";
        try {
            serverId = ContextHelper.get().getServerId();
            long heartbeat = System.currentTimeMillis();
            log.debug("Updating heartbeat for {} [{}]", serverId, heartbeat);
            String licenseKeyHash = addonsManager.getLicenseKeyHash(false);
            serversService.updateArtifactoryServerHeartbeat(serverId, heartbeat, licenseKeyHash);
            log.debug("Updated heartbeat for {} [{}]", serverId, heartbeat);
        } catch (Exception e) {
            log.error("Failed to update heartbeat for [" + serverId + "]", e);
        }
    }

    @Override
    public void activateLicenseIfNeeded() {
        String serverId = "unknown server id";
        try {
            boolean needToActivateLicense;
            boolean homeIsBound = ArtifactoryHome.isBound();
            if (homeIsBound) {
                needToActivateLicense = addonsManager.reloadLicensesStorageIfNeeded();
                if (ArtifactoryHome.get().isHaConfigured()) {
                    // Fist, verify all servers, remove license if there is duplicate and set to offline if needed
                    //(on startup) or skip the take license this time, so the heartbeat will update an empty license
                    //hash
                    if (VerificationResult.duplicateLicense == addonsManager.verifyAllArtifactoryServers(true)) {
                        return;
                    }
                    serverId = ContextHelper.get().getServerId();
                    log.trace("Attempting to perform internal activation if needed for {}", serverId);
                    boolean licenseInstalled = addonsManager.isLicenseInstalled();
                    // Activate only if license is not activated already, or activated license is expired and we have
                    //more licenses than running nodes
                    if (!licenseInstalled || addonsManager.shouldReAcquireLicense()) {
                        log.trace("Performing internal activation for '{}'", serverId);
                        needToActivateLicense = true;

                    } else {
                        log.trace("Skipping internal activation");
                    }
                }
                if (needToActivateLicense && (!addonsManager.getArtifactoryRunningMode().isOss() &&
                        !addonsManager.getArtifactoryRunningMode().isConan() &&
                        !addonsManager.getArtifactoryRunningMode().isJCR() &&
                        !addonsManager.isPartnerLicense())) {
                    LicenseOperationStatus status = new LicenseOperationStatus();
                    addonsManager.activateLicense(Collections.emptySet(), status, true, false);
                }
            } else {
                log.trace("Automatic activation is not required");
            }
        } catch (Exception e) {
            // Log entry should be vague, users shouldn't know that we are using the heartbeat to activate license,
            // otherwise, they can cheat and disable the job in order duplicate the license on multiple nodes
            log.debug("Failed to perform internal activation for {}.", serverId);
        }
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
    }

    @Override
    public void onContextCreated() {
        // Topology listeners may be interested in the cluster's state before the first heartbeat runs
        // This must be here and not in the init, because the logic both the updateServiceClisterChange and the
        //heartbeat
        // are triggering the online license activation, and the onlineLicenseActivaiton reloadableBean is not yet set
        // on the init phase
        HeartbeatJob.updateBinaryServiceClusterChanges(serversService.getAllArtifactoryServers());
        registersHeartbeatJob();
        registerCallHomeJob();
    }

    @JobCommand(jobName = JobName.HEARTBEAT,
            singleton = true, allowedAffinity = false, clusterSingleton = false, description = "Database Heartbeat",
            schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.SYSTEM)
    public static class HeartbeatJob extends QuartzCommand {

        @Override
        protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
            withQueryRateLimiter(SYSTEM, this::executeInternal);
        }

        private void executeInternal() {
            ArtifactoryContext context = ContextHelper.get();
            ArtifactoryServersCommonService serversService = context.beanForType(ArtifactoryServersCommonService.class);
            List<ArtifactoryServer> servers = serversService.getAllArtifactoryServers(false);
            ArtifactoryServer currentMember = recreateServerEntryIfNeeded(servers, context);
            if (context.isReady() && ArtifactoryServerState.RUNNING == currentMember.getServerState()) {
                ArtifactoryHeartbeatService heartbeatService = context.beanForType(ArtifactoryHeartbeatService.class);
                heartbeatService.activateLicenseIfNeeded();
                heartbeatService.updateHeartbeat();
                servers = serversService.getAllArtifactoryServers();
                log.debug("All Artifactory servers: {}", servers);
                serversService.publishServersUpdatedEvent(servers);
                updateBinaryServiceClusterChanges(servers);
                cleanupResidualServerEntries(serversService, servers);
            }
        }

        static void updateBinaryServiceClusterChanges(List<ArtifactoryServer> servers) {
            try {
                ArtifactoryContext context = ContextHelper.get();
                if (ArtifactoryHome.get().isHaConfigured()) {
                    List<String> haLicensedNodes = getValidHaLicensedNodes();
                    //These are the currently running nodes that have proper HA licenses
                    List<ArtifactoryServer> runningLicensedNodes = servers.stream()
                            .filter(Objects::nonNull)
                            .filter(isOther)
                            .filter(isRunning)
                            .filter(hasHeartbeat)
                            .filter(node -> haLicensedNodes.contains(node.getServerId()))
                            .collect(Collectors.toList());
                    log.trace("Notifying cluster topology listeners about current active nodes: {}",
                            runningLicensedNodes);
                    context.beanForType(ClusterOperationsService.class).clusterTopologyChanged(runningLicensedNodes);
                }
            } catch (Exception e) {
                log.error("Failed to update Binary store about cluster changes", e);
            }
        }

        /**
         * @return Node ids of all nodes in the cluster that have a valid, HA compatible license
         */
        private static List<String> getValidHaLicensedNodes() {
            return ContextHelper.get().beanForType(AddonsManager.class).getClusterLicensesDetails()
                    .stream()
                    .filter(HeartbeatJob::HACompatibleLicense)
                    .map(ArtifactoryHaLicenseDetails::getNodeId)
                    .collect(Collectors.toList());
        }

        //TODO [by shayb]: there are too many strings like this in the code, we should work with the enum of the
        //product license type instead
        private static boolean HACompatibleLicense(ArtifactoryHaLicenseDetails license) {
            String type = license.getType();
            return "Enterprise".equalsIgnoreCase(type) || "Trial".equalsIgnoreCase(type) ||
                    "Enterprise Plus".equalsIgnoreCase(type) || "Enterprise Plus Trial".equalsIgnoreCase(type) ||
                    "Edge".equalsIgnoreCase(type) || "Edge Trial".equalsIgnoreCase(type);
        }

        @VisibleForTesting
        ArtifactoryServer recreateServerEntryIfNeeded(List<ArtifactoryServer> servers,
                ArtifactoryContext context) {
            return servers.stream()
                    .filter(server -> server.getServerId().trim().equals(ContextHelper.get().getServerId()))
                    .findFirst()
                    .orElseGet(() ->
                            // This server was cleared up by one of the previous cycles of the heartbeat job -
                            // we need to rejoin
                            context.beanForType(ArtifactoryStateManager.class)
                                    .createOrUpdateArtifactoryServerState(ArtifactoryServerState.RUNNING)
                    );
        }

        private void cleanupResidualServerEntries(ArtifactoryServersCommonService serversService,
                List<ArtifactoryServer> allServers) {
            log.debug("Searching for residual Artifactory Server entries...");
            allServers.stream()
                    .filter(qualifiesForDeletion)
                    .peek(server -> log
                            .info("Deleting residual Artifactory Server entry with ID '{}' (last heartbeat was more " +
                                            "than {} minutes ago at '{}')",
                                    server.getServerId(),
                                    ConstantValues.haHeartbeatStaleServerCleanupPeriodMinutes.getInt(),
                                    server.getLastHeartbeat()))
                    .forEach(server -> serversService.removeServer(server.getServerId()));
        }
    }
}
