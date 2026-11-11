package org.artifactory.repo.replication;


import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.repo.cleanup.ArtifactCleanupJob;
import org.artifactory.repo.service.ImportJob;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.StopStrategy;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * This job is an extension of {@link RemoteReplicationJob} and is identical to it, except it is not mutually exclusive
 * with {@link ArtifactCleanupJob}. The purpose of this is to verify an issue at Bosch where the replication job is
 * preventing the cleanup job from running.
 * if this implementation proves to be successful, it should be made the default implementation.
 *
 * @author Dvir Twina
 */
@JobCommand(jobName = JobName.REMOTE_REPLICATION, schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.CURRENT,
        keyAttributes = {Task.REPO_KEY},
        commandsToStop = {
                @StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE),
        }
)
@Slf4j
public class RemoteReplicationJobUnexclusive extends RemoteReplicationJob {

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