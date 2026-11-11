package org.artifactory.traffic.stats;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Quartz job that periodically flushes in-memory repo traffic stats to the database.
 * Frequency is controlled by {@code ConstantValues.repoTrafficStatsCollectionCron}.
 * Configured by {@link RepoTrafficStatsServiceImpl#setupCollectionJob()}.
 *
 * @author georgyr
 */
@Slf4j
@JobCommand(jobName = JobName.REPO_TRAFFIC_STATS_COLLECTION,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        clusterSingleton = false,
        description = "Flushes in-memory repo traffic stats to the database")
public class RepoTrafficStatsCollectionJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            log.debug("Starting repo traffic stats collection");
            ContextHelper.get().beanForType(RepoTrafficStatsService.class).flushStats();
            log.debug("Repo traffic stats flushed successfully");
        } catch (Exception e) {
            log.warn("Failed to flush repo traffic stats", e);
        }
    }
}
