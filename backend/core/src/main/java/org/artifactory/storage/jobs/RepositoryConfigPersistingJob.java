package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.storage.db.fs.service.RepositoryPersistingService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A job that persists repositories configuration to a backup file: etc/artifactory/artifactory.repo.config.import.json
 *
 * @author shayb
 */
@JobCommand(jobName = JobName.REPO_CONFIG_PERSISTING_SERVICE,
        description = "Repository configurations persisting job",
        singleton = true, clusterSingleton = true, allowedAffinity = false, schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM)
public class RepositoryConfigPersistingJob extends QuartzCommand {
    private static final Logger log = LoggerFactory.getLogger(RepositoryConfigPersistingJob.class);

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        log.debug("Repository configuration persistence job started");
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        RepositoryPersistingService repoConfigPersistingService =
                artifactoryContext.beanForType(RepositoryPersistingService.class);
        repoConfigPersistingService.persist();
        log.debug("Repository configuration persistence job finished");
    }
}
