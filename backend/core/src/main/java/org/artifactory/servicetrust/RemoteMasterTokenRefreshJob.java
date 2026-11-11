package org.artifactory.servicetrust;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
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

/**
 * Refresh a master token from a remote instance if local and remote instances are paired with trust
 *
 * @author volodymyrm
 */
@Slf4j
@JobCommand(jobName = JobName.REMOTE_REFRESH_MASTER_TOKEN,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.CURRENT,
        singleton = true,
        description = "Refresh a master token from a remote instance if local and remote instances are paired with " +
                "trust",
        commandsToStop = {
                @StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE),
                @StopCommand(command = ExportJob.class, strategy = StopStrategy.IMPOSSIBLE)}
)
public class RemoteMasterTokenRefreshJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        addonsManager.addonByType(ArtifactRetentionAddon.class).refreshMasterToken();
    }
}