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

package org.artifactory.schedule;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.cron.CronUtils;
import org.artifactory.event.EventHintType;
import org.artifactory.activity.ActivityScope;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.metrics.TimeRandomizer;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_KIND_JOB;
import static org.artifactory.mirror.MirrorConstants.REPLICATION_ALLOWED;
import static org.artifactory.schedule.quartz.PluginCommand.ACTION_CLOSURE;

/**
 * @author yoavl
 */
@Slf4j
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public abstract class QuartzCommand extends TaskCallback<JobExecutionContext> implements InternalQuartzCommand, Job {
    private JobExecutionContext jobContext;

    @Override
    public final void execute(final JobExecutionContext jobContext) throws JobExecutionException {
        try (ActivityScope ignored = CurrentActivity.newScope(ACTIVITY_KIND_JOB)) {
            updateCurrentActivityDetails(jobContext);

            boolean shouldExecute = beforeExecute(jobContext);
            String token = currentTaskId();
            String taskName = getClass().getName();
            try {
                if (!shouldExecute) {
                    log.debug("Skipping execution of task: {} for token: {}.", taskName, token);
                    if (activeTask != null) {
                        activeTask.skip("Task execution skipped. View log for more details.");
                    }
                } else {
                    this.jobContext = jobContext;
                    try {
                        activeTask.started();
                        activeTask.start(this);
                    } catch (Exception e) {
                        activeTask.fail(e.getMessage());
                        throw e;
                    }
                }
            } finally {
                EventLogHintManager.clear();
                ArtifactoryTracer.enrichAndRenameSpanFromActivity();
                afterExecute();
            }
        }
    }

    protected void updateCurrentActivityDetails(final JobExecutionContext jobContext) {
        JobName jobName = getJobName(jobContext);
        if (jobName != null) {
            CurrentActivity.GROUP.put(jobName.name());
        }

        CurrentActivity.updateFromAnnotations(getClass());
    }

    @Override
    public final void runCommand() throws JobExecutionException {
        String taskName = getActiveTask().getTaskName();
        String token = currentTaskId();
        if (jobContext.getMergedJobDataMap().get(ACTION_CLOSURE) != null) {
            try {
                EventLogHintManager.put(EventHintType.ReplicationAllowed, REPLICATION_ALLOWED);
                log.debug("Executing task {} with token {}. replicationAllowed", taskName, token);
                onExecute(jobContext);
                log.debug("Finished execution of task {} with token {}. replicationAllowed", taskName, token);
            } finally {
                EventLogHintManager.remove(EventHintType.ReplicationAllowed);
            }
        } else {
            log.debug("Executing task {} with token {}.", taskName, token);
            onExecute(jobContext);
            log.debug("Finished execution of task {} with token {}.", taskName, token);
        }
    }

    public String getCommandLockKey() {
        return activeTask.getTaskLockKeyIncludingKeyAttributes(getLockKeyAttributes());
    }

    @Override
    protected String triggeringTaskTokenFromWorkContext(JobExecutionContext jobContext) {
        return jobContext.getMergedJobDataMap().getString(Task.TASK_TOKEN);
    }

    @Override
    protected Authentication getAuthenticationFromWorkContext(JobExecutionContext jobContext) {
        return (Authentication) jobContext.getMergedJobDataMap().get(Task.TASK_AUTHENTICATION);
    }

    @Override
    protected JobName getJobName(JobExecutionContext jobContext) {
        return (JobName) jobContext.getMergedJobDataMap().get(Task.JOB_NAME);
    }

    @Override
    protected boolean isHardAffinity(JobExecutionContext jobExecutionContext) {
        return (boolean) jobExecutionContext.getMergedJobDataMap().get(Task.HARD_AFFINITY);
    }

    @Override
    protected boolean isAllowedAffinity(JobExecutionContext jobExecutionContext) {
        return (boolean) jobExecutionContext.getMergedJobDataMap().get(Task.ALLOWED_AFFINITY);
    }

    /**
     * @return a list of strings that will be incorporated into the cluster-singleton lock of this job
     */
    protected List<String> getLockKeyAttributes() {
        return List.of();
    }

    /**
     * Generates a random execution time once per day if no predefined cron is provided.
     * Can be used to reduce load on AOLs we set random execution time.
     *
     * @param predefinedExp Predefined cron expression. If valid, it will be used as is.
     *                      Otherwise, a random cron will be generated.
     * @param serviceId     Service ID to use for randomization. If null or blank,
     *                      a completely random time will be generated.
     * @param taskName      Name of the task for logging purposes.
     * @return Quartz scheduling expression
     */
    @NonNull
    protected static String getRandomHourMinuteCron(@Nullable String predefinedExp, @Nullable CharSequence serviceId,
            @NonNull String taskName) {
        if (!StringUtils.isBlank(predefinedExp) && CronUtils.isValid(predefinedExp)) {
            return predefinedExp;
        }
        // Generate random time once per day: random hour (0-23) and random minute (0-59)
        // based on serviceId hash to have consistent time across Artifactory nodes.
        int hash = StringUtils.isBlank(serviceId) ? TimeRandomizer.randomMinute() : serviceId.hashCode();
        int randomHour = Math.abs(hash % 24);
        int randomMinute = Math.abs(hash % 60);
        String cronExp = String.format("0 %d %d ? * *", randomMinute, randomHour);
        log.info("Using random cron expression for {} calculation: {} (runs once daily at {}:{})",
                taskName, cronExp, String.format("%02d", randomHour), String.format("%02d", randomMinute));
        return cronExp;
    }
}