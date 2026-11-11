package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.repo.service.ExportJob;
import org.artifactory.repo.service.ImportJob;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.StopStrategy;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


/**
 * A quartz command job that periodically cleans old files from "_uploads" folders of Nix local repositories.
 *
 * @author Natalie Berni
 */
@JobCommand(jobName = JobName.NIX_UPLOADS_FOLDER_CLEANUP, singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Nix local repositories _uploads cleanup",
        commandsToStop = {
                @StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE),
                @StopCommand(command = ExportJob.class, strategy = StopStrategy.IMPOSSIBLE)}
)
public class NixUploadsFolderCleanupJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        NixUploadsFolderCleanupServiceImpl nixUploadsFolderCleanupService =
                artifactoryContext.beanForType(NixUploadsFolderCleanupServiceImpl.class);
        nixUploadsFolderCleanupService.clean();
    }
}
