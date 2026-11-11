package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.fs.service.RepoStatsService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A job to trigger repo statistics flushing.
 */
@JobCommand(jobName = JobName.REPO_STATS_FLUSH,
        description = "Repository Statistics Flushing",
        singleton = true, allowedAffinity = false, schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM)
public class RepoStatsFlushJob extends QuartzCommand {
    private static final Logger log = LoggerFactory.getLogger(RepoStatsFlushJob.class);

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        log.debug("Repo Stats FlushJob started");
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        RepoStatsService repoStatsService = artifactoryContext.beanForType(RepoStatsService.class);
        repoStatsService.flush();
        log.debug("Repo Stats FlushJob finished");
    }
}
