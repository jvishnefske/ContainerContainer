package org.artifactory.devopsagent.requestlog.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogFlushable;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Periodic job that drains all registered {@link RequestLogFlushable} queues to the DB.
 * New log directions (e.g. outbound) are picked up automatically when their service implements
 * {@link RequestLogFlushable} — this job requires no modification.
 * Frequency: {@code ConstantValues.devopsAgentRequestLogFlushIntervalMs}.
 * Registered by {@link org.artifactory.devopsagent.requestlog.RequestLogFlushScheduler}.
 */
@Slf4j
@JobCommand(jobName = JobName.DEVOPS_AGENT_REQUEST_LOG_FLUSH,
        description = "Request Log Batch Flush",
        singleton = true,
        clusterSingleton = false,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM)
public class RequestLogFlushJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ContextHelper.get().beansForType(RequestLogFlushable.class).values().forEach(flushable -> {
            try {
                flushable.flush();
            } catch (Exception e) {
                log.warn("agentic_api.request_logs.flush_job_error service={}",
                        flushable.getClass().getSimpleName(), e);
            }
        });
    }
}
