package org.artifactory.request.audit;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Logs the distinct HTTP header names observed since the previous run.</br>
 * Frequency: ConstantValues.httpHeadersAuditCron</br>
 * Configured by: HttpHeadersAuditServiceImpl.setupAuditJob
 */
@Slf4j
@JobCommand(jobName = JobName.HTTP_HEADERS_AUDIT,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        clusterSingleton = false,
        description = "Logs distinct HTTP header names observed by Artifactory")
public class HttpHeadersAuditJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            ContextHelper.get().beanForType(HttpHeadersAuditService.class).flush();
        } catch (Exception e) {
            log.warn("Failed to flush HTTP headers audit", e);
        }
    }
}
