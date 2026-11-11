package org.artifactory.system.load.monitor.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.system.load.monitor.SystemLoadMonitorService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@Slf4j
@JobCommand(jobName = JobName.SYSTEM_LOAD_MONITOR, description = "Artifactory system load monitor job", singleton =
        true, clusterSingleton = false, allowedAffinity = false, schedulerUser = TaskUser.SYSTEM)
public class SystemLoadMonitorJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        log.debug("Initiating system load monitoring job");
        ContextHelper.get().beanForType(SystemLoadMonitorService.class).monitorSystemLoad();
        log.debug("System load monitoring job completed");
    }
}