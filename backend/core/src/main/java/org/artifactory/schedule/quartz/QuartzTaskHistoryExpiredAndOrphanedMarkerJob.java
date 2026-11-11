package org.artifactory.schedule.quartz;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.fs.service.QuartzTaskHistoryService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;

@Slf4j
@JobCommand(jobName = JobName.QUARTZ_TASK_HISTORY_EXPIRED_AND_ORPHANED_MARKER,
        singleton = true,
        allowedAffinity = false,
        shutdownTransparent = true,
        description = "Mark expired and orphan quartz task history records job",
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM)
public class QuartzTaskHistoryExpiredAndOrphanedMarkerJob extends QuartzCommand {
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
            QuartzTaskHistoryService quartzTaskHistoryService = context.beanForType(QuartzTaskHistoryService.class);
            log.trace("Scanning records to mark...");
            quartzTaskHistoryService.markExpiredTasks();
            quartzTaskHistoryService.markPendingExpiredCheckTasks();
            quartzTaskHistoryService.markOrphanedTasks();
            log.trace("Records marked successfully");
        } catch (Exception e) {
            log.error("Job could not be completed.", e);
        }
    }
}