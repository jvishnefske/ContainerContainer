package org.artifactory.repo.remote.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Flushes {@link RemoteRepoOutboundHeaderAuditService} on the cron configured by
 * {@link org.artifactory.common.ConstantValues#remoteRepoOutboundHeadersAuditCron}.
 */
@Slf4j
@JobCommand(jobName = JobName.REMOTE_REPO_OUTBOUND_HEADERS_AUDIT,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        clusterSingleton = false,
        description = "Logs distinct upstream response header names observed per remote repository")
public class RemoteRepoOutboundHeaderAuditJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            ContextHelper.get().beanForType(RemoteRepoOutboundHeaderAuditService.class).flush();
        } catch (Exception e) {
            log.warn("Failed to run scheduled task {}", JobName.REMOTE_REPO_OUTBOUND_HEADERS_AUDIT.asText(), e);
        }
    }
}
