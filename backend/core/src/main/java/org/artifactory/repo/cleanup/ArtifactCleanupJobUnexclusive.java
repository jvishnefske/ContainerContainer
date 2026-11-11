package org.artifactory.repo.cleanup;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.repo.replication.RemoteReplicationJob;
import org.artifactory.repo.service.ExportJob;
import org.artifactory.repo.service.ImportJob;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.StopStrategy;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * A quartz command job that periodically cleans remote-repository caches from unused artifacts.
 * This job is an extension of {@link ArtifactCleanupJob} and is identical to it, except it is not mutually exclusive
 * with {@link RemoteReplicationJob}. The purpose of this is to verify an issue at Bosch where the replication job is
 * preventing the cleanup job from running.
 * if this implementation proves to be successful, it should be made the default implementation.
 *
 * @author Dvir Twina
 */
@JobCommand(jobName = JobName.ARTIFACT_CLEANUP_UNEXCLUSIVE, singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Remote Repositories Cached Artifacts Cleanup (Unexclusive)",
        commandsToStop = {
                @StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE),
                @StopCommand(command = ExportJob.class, strategy = StopStrategy.IMPOSSIBLE)}
)
@Slf4j
public class ArtifactCleanupJobUnexclusive extends ArtifactCleanupJob {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        log.debug("Executing job {}", this.getClass().getSimpleName());
        callParent(callbackContext);
    }

    @VisibleForTesting
    void callParent(JobExecutionContext callbackContext) throws JobExecutionException {
        super.onExecute(callbackContext);
    }
}