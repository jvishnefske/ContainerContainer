package org.artifactory.probes.jobs;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.probes.ProbesService;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * A job for triggering Artifactory readiness check.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
@JobCommand(jobName = JobName.READINESS, description = "Artifactory readiness check job", singleton = true,
        clusterSingleton = false, allowedAffinity = false, schedulerUser = TaskUser.SYSTEM)
public class ReadinessJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        log.debug("Readiness job has started");
        ContextHelper.get().beanForType(ProbesService.class).triggerReadinessCheck();
        log.debug("Readiness job has finished");
    }
}
