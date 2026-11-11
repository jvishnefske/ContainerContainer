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

package org.artifactory.lock.service;

import com.google.common.collect.Sets;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.lock.LockingProviderTypeEnum;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUser;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ContextReadinessListener;
import org.artifactory.storage.db.locks.service.JFrogTableBasedDbLocksService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;

/**
 * @author gidis
 * @author Mor Merhav
 */
@Service
public class LockServiceImpl implements LockService, ContextReadinessListener {

    private static final Logger log = LoggerFactory.getLogger(LockServiceImpl.class);
    private final JFrogTableBasedDbLocksService dbLocksService;
    private final List<ReleaseLockStrategy> releaseLockStrategies;
    private final HaAddon haAddon;
    private String serviceId;

    @Autowired
    public LockServiceImpl(JFrogTableBasedDbLocksService dbLocksService,
                           List<ReleaseLockStrategy> releaseLockStrategies, HaAddon haAddon) {
        this.dbLocksService = dbLocksService;
        this.releaseLockStrategies = releaseLockStrategies;
        this.haAddon = haAddon;
    }

    @PostConstruct
    void init() {
        if (haAddon.isHaEnabled()) {
            if (LockingProviderTypeEnum.isDb() || LockingProviderTypeEnum.isOptimistic()) {
                log.info("Initiating HA orphaned and expired locks release sequence...");
                releaseAllLocksByOwner(ArtifactoryHome.get().getServerId());
                releaseExpiredLocks();
                releaseOrphanLocks();
                log.info("HA locks release sequence complete.");
                scheduleReleaseLocksTask();
            }
        }
    }

    @Override
    public void onContextReady() {
        serviceId = ArtifactoryHome.get().getServerId();
    }

    @PreDestroy
    public void destroy() {
        log.debug("Destroying all locks for server: {} ", serviceId);
        dbLocksService
                .releaseAllLocksByOwnerExcludingKeys(serviceId, Sets.newHashSet());
    }

    @Override
    public void releaseExpiredLocks() {
        releaseLockStrategies.forEach(ReleaseLockStrategy::releaseExpired);
    }

    @Override
    public void releaseOrphanLocks() {
        releaseLockStrategies.forEach(ReleaseLockStrategy::releaseOrphaned);
    }

    @Override
    public void releaseAllLocksByOwner(String owner) {
        dbLocksService.releaseAllLocksByOwner(owner);
    }

    @Override
    public void releaseStuckLocks() {
        dbLocksService.forceUnlockAllStuck();
    }

    private void scheduleReleaseLocksTask() {
        log.info("Scheduling release HA locks task...");
        Task releaseExpiredAndOrphanLockJob = TaskUtils
                .createRepeatingTask(ReleaseExpiredAndOrphanLocksJob.class,
                        TimeUnit.SECONDS.toMillis(ConstantValues.dbLockReleaseJobIntervalSec.getLong()),
                        TimeUnit.SECONDS.toMillis(ConstantValues.dbLockReleaseJobStaleIntervalSec.getLong()));
        TaskService taskService = ContextHelper.get().beanForType(TaskService.class);
        taskService.startTask(releaseExpiredAndOrphanLockJob, true);
    }

    @JobCommand(jobName = JobName.RELEASE_EXPIRED_LOCKS,
            singleton = true,
            allowedAffinity = false,
            description = "Release expired and orphan locks job",
            schedulerUser = TaskUser.SYSTEM,
            manualUser = TaskUser.SYSTEM)
    public static class ReleaseExpiredAndOrphanLocksJob extends QuartzCommand {

        private final Logger log = LoggerFactory.getLogger(ReleaseExpiredAndOrphanLocksJob.class);

        @Override
        protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
            withQueryRateLimiter(SYSTEM, this::executeInternal);
        }

        private void executeInternal() {
            try {
                log.trace("Started");
                ArtifactoryContext context = ContextHelper.get();
                if (context == null) {
                    log.warn("Context is not bound.");
                    return;
                }
                LockService lockService = context.beanForType(LockService.class);
                log.trace("Releasing locks...");
                lockService.releaseStuckLocks();
                lockService.releaseOrphanLocks();
                lockService.releaseExpiredLocks();
                log.trace("Locks released successfully");
            } catch (Exception e) {
                log.error("Expired locks release job could not be completed.", e);
            }
        }
    }
}
