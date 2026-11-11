package org.artifactory.storage.jobs;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Triggers {@link RemoteRepoOfflineGuardService#evaluate()} on the cron configured by
 * {@link org.artifactory.common.ConstantValues#remoteRepoOfflineGuardCron}.
 *
 * @author Hussein Alshqerat
 */
@JobCommand(jobName = JobName.REMOTE_REPO_OFFLINE_GUARD,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        allowedAffinity = false,
        description = "Remote Repo Offline Guard")
@Slf4j
public class RemoteRepoOfflineGuardJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            ContextHelper.get().beanForType(RemoteRepoOfflineGuardService.class).evaluate();
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
