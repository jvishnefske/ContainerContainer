package org.artifactory.remote.stats;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Quartz job that periodically flushes in-memory remote repository stats to the database.
 * Frequency is controlled by {@code ConstantValues.remoteRepoStatsCollectionCron}.
 * Configured by {@link RemoteRepoStatsServiceImpl#setupCollectionJob()}.
 *
 * @author husseina
 */
@Slf4j
@JobCommand(jobName = JobName.REMOTE_REPO_STATS_COLLECTION,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        clusterSingleton = false,
        description = "Flushes in-memory remote repo stats to the database")
public class RemoteRepoStatsCollectionJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            log.debug("Starting remote repo stats collection");
            ContextHelper.get().beanForType(RemoteRepoStatsService.class).flushStats();
            log.debug("Remote repo stats flushed successfully");
        } catch (Exception e) {
            log.warn("Failed to flush remote repo stats", e);
        }
    }
}
