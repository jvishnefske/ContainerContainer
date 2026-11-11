package org.artifactory.queue.execution;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


@JobCommand(jobName = JobName.PERSISTENT_QUEUE, singleton = true,
        description = "Trigger queues once in a while in case there was a problem or restart and events still left " +
                "unhandled",
        allowedAffinity = false, schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.SYSTEM)
public class PersistentQueueTriggerJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        QueueTriggeringService queueService = ContextHelper.get().beanForType(QueueTriggeringService.class);
        queueService.triggerQueuesByTime();
    }
}
