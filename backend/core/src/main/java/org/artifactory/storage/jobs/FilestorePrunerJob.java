package org.artifactory.storage.jobs;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.jfrog.storage.common.prune.PruneRequestModel;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@Slf4j
@JobCommand(jobName = JobName.PRUNE,
        singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Prune Job"
        )
public class FilestorePrunerJob extends QuartzCommand {
    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            ArtifactoryBinaryService binaryStore = ContextHelper.get().beanForType(ArtifactoryBinaryService.class);
            PruneRequestModel pruneRequestModel =
                    (PruneRequestModel) getActiveTask().getAttribute("PRUNE_REQUEST_MODEL");
            binaryStore.prune(pruneRequestModel);
        } catch (Exception e) {
            log.error("Could not execute Prune routine due to {}", e.getMessage(), e);
        }
    }
}