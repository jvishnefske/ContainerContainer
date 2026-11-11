package org.artifactory.postgresql.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.postgresql.service.PgSchemaService;
import org.artifactory.postgresql.service.PgSchemaServiceImpl;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Scheduled job that periodically refreshes PostgreSQL schema metrics by calling
 * {@link PgSchemaService#refreshMetrics()}.
 * <p>
 * Runs on all cluster nodes (not a singleton). Scheduled by {@link PgSchemaServiceImpl} with configurable interval.
 * On failure, error is logged and stale data is preserved.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
@JobCommand(
        jobName = JobName.PG_INDEX_METRICS,
        description = "Artifactory database indexes metrics collection job",
        clusterSingleton = false,
        allowedAffinity = false,
        schedulerUser = TaskUser.SYSTEM
)
public class PgSchemaMetricsJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        log.debug("Database indexes metrics collection job has started");
        ContextHelper.get().beanForType(PgSchemaService.class).refreshMetrics();
        log.debug("Database indexes metrics collection job has finished");
    }
}
