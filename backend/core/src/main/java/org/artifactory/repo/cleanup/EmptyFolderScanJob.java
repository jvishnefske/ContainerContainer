package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;

/**
 * A quartz command job that periodically scans for empty folders.
 * This job proactively discovers orphaned empty folders and queues them for pruning.
 *
 * @author annakv
 */
@JobCommand(jobName = JobName.EMPTY_FOLDER_SCAN,
        singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Systematic Empty Folder Scanning Job",
        allowedAffinity = false,
        clusterSingleton = true)
public class EmptyFolderScanJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        EmptyFolderScanService emptyFolderScanService = ContextHelper.get()
                .beanForType(EmptyFolderScanService.class);
        withQueryRateLimiter(LOW_PRIORITY, emptyFolderScanService::scanEmptyFolders);
    }

    /**
     * Runs every minute, ensuring only one node executes at a time.
     *
     * @return Quartz scheduling expression
     */
    public static String buildRandomQuartzExp() {
        int interval = ConstantValues.emptyFolderScanJobIntervalMin.getInt();
        return String.format("0 0/%d * ? * * *", interval);
    }
}


