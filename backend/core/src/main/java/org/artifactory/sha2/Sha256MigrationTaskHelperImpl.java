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

package org.artifactory.sha2;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.sha2.Sha256MigrationModel;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.storage.jobs.migration.MigrationJobBase;
import org.artifactory.storage.jobs.migration.sha256.Sha256MigrationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.artifactory.api.rest.constant.Sha256MigrationRestConstants.BATCH_THRESHOLD;
import static org.artifactory.api.rest.constant.Sha256MigrationRestConstants.FORCE_RUN_ON_NODE_ID;
import static org.artifactory.api.rest.constant.Sha256MigrationRestConstants.JOB_ENABLED;
import static org.artifactory.api.rest.constant.Sha256MigrationRestConstants.QUERY_LIMIT;
import static org.artifactory.api.rest.constant.Sha256MigrationRestConstants.SLEEP_INTERVAL_MILLIS;
import static org.artifactory.api.rest.constant.Sha256MigrationRestConstants.WAIT_FOR_CLUSTERS_SLEEP_INTERVAL_MILLIS;

/**
 * @author Inbar Tal
 */

@Component
public class Sha256MigrationTaskHelperImpl implements Sha256MigrationTaskHelper {
    private static final Logger log = LoggerFactory.getLogger(Sha256MigrationTaskHelperImpl.class);

    private TaskService taskService;
    private AddonsManager addonsManager;

    @Autowired
    public Sha256MigrationTaskHelperImpl(TaskService taskService, AddonsManager addonsManager) {
        this.taskService = taskService;
        this.addonsManager = addonsManager;
    }

    @Override
    public void stopMigrationTask(long sleepIntervalMillis) {
        assertNotAol();
        List<Task> activeTasks = taskService.getActiveTasks(input ->
                input != null && input.getType().equals(Sha256MigrationJob.class));
        if (activeTasks.isEmpty()) {
            return;
        }
        if (activeTasks.size() > 1) {
            log.warn("There is more than one active sha256 migration task");
            killAllSha2MigrationTasks(activeTasks);
        }
        try {
            taskService.cancelTask(activeTasks.get(0).getTaskId(), true, ((long) ((1.5) * sleepIntervalMillis)));
            log.debug("Sha2 migration task: {} was canceled", activeTasks.get(0).getTaskId());
        } catch (IllegalStateException e) {
            log.error("Error occurred while trying to stop sha256 migration task", e);
        }
    }

    @Override
    public Task createManualTask(Sha256MigrationModel model) {
        Task sha2JobContext = TaskUtils.createManualTask(Sha256MigrationJob.class, 0);
        addAttributes(sha2JobContext, model);
        return sha2JobContext;
    }

    @Override
    public boolean shouldPropagateStartRequest(String serverId) {
        return haAddon().isHaEnabled() && !shouldRunOnCurrentNode(serverId);
    }

    @Override
    public boolean shouldPropagateStopRequest() {
        return haAddon().isHaEnabled();
    }

    /**
     * Throw bad request if using AOL
     */
    @Override
    public void assertNotAol() {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            String message = "Artifactory Online does not require triggering or stopping sha256 migration manually," +
                    " Please contact support@jfrog.com for further assistance if required.";
            throw new BadRequestException(message);
        }
    }

    @Override
    public boolean jobIsAlreadyRunning() {
        return jobRunsOnThisNode() || MigrationJobBase.jobRunsOnOtherNode(Sha256MigrationJob.class.getName());
    }

    private void killAllSha2MigrationTasks(List<Task> activeTasks) {
        for (Task task : activeTasks) {
            try {
                taskService.cancelTask(task.getTaskId(), true);
            } catch (IllegalStateException e) {
                log.error("Error occurred while trying to stop sha2 migration task: {}", task.getTaskId(), e);
            }
        }
    }

    private boolean jobRunsOnThisNode() {
        List<Task> activeTasks = taskService.getActiveTasks(input -> (input != null) &&
                (Sha256MigrationJob.class.isAssignableFrom(input.getType())));
        return !activeTasks.isEmpty();
    }

    private void addAttributes(Task sha2JobContext, Sha256MigrationModel model) {
        sha2JobContext.addAttribute(SLEEP_INTERVAL_MILLIS, model.getSleepIntervalMillis());
        sha2JobContext.addAttribute(BATCH_THRESHOLD, model.getBatchThreshold());
        sha2JobContext.addAttribute(QUERY_LIMIT, model.getQueryLimit());
        sha2JobContext
                .addAttribute(WAIT_FOR_CLUSTERS_SLEEP_INTERVAL_MILLIS, model.getWaitForClusterSleepIntervalMillis());
        sha2JobContext.addAttribute(JOB_ENABLED, true);
        sha2JobContext.addAttribute(FORCE_RUN_ON_NODE_ID, model.getForceRunOnNodeId());
    }

    private boolean shouldRunOnCurrentNode(String serverId) {
        return (StringUtils.isBlank(serverId) && haAddon().isAllowedToRun(JobName.SHA_256_MIGRATION) ||
                (StringUtils.isNotBlank(serverId) &&
                        serverId.equals(haAddon().getCurrentMemberServerId())));
    }

    private HaAddon haAddon() {
        return addonsManager.addonByType(HaAddon.class);
    }
}