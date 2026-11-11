package org.artifactory.rest.resource.mfe.server;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@Slf4j
@JobCommand(
        clusterSingleton = false,
        schedulerUser = TaskUser.SYSTEM,
        jobName = JobName.CORE_MFE_VERSION_REFRESH,
        description = "Update MFE version"
)
public class RefreshMFEVersionJob extends QuartzCommand {

    @Override
    protected void onExecute(@Nullable JobExecutionContext callbackContext) throws JobExecutionException {
        ExternalMFE externalMFE = ContextHelper.get().beanForType(ExternalMFE.class);
        log.debug("Refreshing MFE versions for all projects");
        externalMFE.refreshMFEVersionForProjects();
    }
}

