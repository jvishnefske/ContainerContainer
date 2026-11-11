package org.artifactory.throttling.qrl.metrics;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@JobCommand(jobName = JobName.QRL_METRICS,
        description = "Artifactory query rate limiter metrics collection job",
        singleton = true,
        clusterSingleton = false,
        allowedAffinity = false,
        schedulerUser = TaskUser.SYSTEM)
public class QueryRateLimiterMetricsJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        log.trace("Query rate limiter metrics collection job has started");
        ContextHelper.get().beanForType(QueryRateLimiterService.class).collectMetrics();
        log.trace("Query rate limiter metrics collection job has finished");
    }
}
