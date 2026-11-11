package org.artifactory.federation;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.federation.FileListTaskService;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@JobCommand(jobName = JobName.FEDERATE_FILE_LIST_TASKS_JOB, description = "File list tasks job",
        clusterSingleton = false, allowedAffinity = false, schedulerUser = TaskUser.SYSTEM)
@Slf4j
public class FileListTasksJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        log.trace("Job {} started", JobName.FEDERATE_FILE_LIST_TASKS_JOB);
        ContextHelper.get().beanForType(FileListTaskService.class).activateTasks();
    }

}
