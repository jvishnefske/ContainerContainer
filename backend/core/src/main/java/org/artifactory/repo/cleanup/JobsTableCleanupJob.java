package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * @author Yoaz Menda
 */
@JobCommand(singleton = true,
        jobName = JobName.JOB_TABLE_CLEANUP,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        clusterSingleton = true,
        description = "Jobs table cleanup")
public class JobsTableCleanupJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        JobsTableCleanupService jobsTableCleanupService = artifactoryContext.beanForType(
                JobsTableCleanupService.class);
        jobsTableCleanupService.clean();
    }
}
