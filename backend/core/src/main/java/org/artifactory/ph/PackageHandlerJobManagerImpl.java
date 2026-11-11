package org.artifactory.ph;

import org.artifactory.ph.jobs.GoCacheCleanupJob;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;

/**
 * An Artifactory side service to initialize all the Jobs that should run in Package handler side.
 */
public class PackageHandlerJobManagerImpl implements PackageHandlerJobManager {

    private final TaskService taskService;

    public PackageHandlerJobManagerImpl(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void onContextCreated() {
        Task task = TaskUtils.createCronTask(GoCacheCleanupJob.class,
                GoCacheCleanupJob.buildQuartzExp(), "Go Remote Cache Cleanup Job");
        taskService.startTask(task, false);
    }
}
