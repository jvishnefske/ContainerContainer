package org.artifactory.storage.jobs;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.cron.CronUtils;
import org.artifactory.metrics.TimeRandomizer;
import org.artifactory.repo.service.ImportJob;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.StopStrategy;
import org.artifactory.schedule.TaskUser;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.service.InternalStorageService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.schedule.JobName.STORAGE_USAGE_SYNC;

/**
 * Cron job that sync the local storage diffs with the DB
 */
@JobCommand(jobName = STORAGE_USAGE_SYNC, singleton = true, clusterSingleton = false, allowedAffinity = false,
        schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.SYSTEM,
        description = "Sync local storage usage diffs with DB",
        commandsToStop = {@StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE)})
public class SyncStorageUsageJob extends QuartzCommand {
    private static final Logger log = LoggerFactory.getLogger(SyncStorageUsageJob.class);

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        InternalArtifactoryContext context = InternalContextHelper.get();
        if (!context.isReady()) {
            log.debug("Skipping execution of '{}', server is not ready yet",
                    SyncStorageUsageJob.class.getName());
            return;
        }
        log.debug("Running SyncStorageUsageJob");
        InternalStorageService storageService = context.beanForType(InternalStorageService.class);
        storageService.syncStorageUsageDiffsIfNeeded();
    }

    /**
     * To avoid the chances of two nodes syncing on the same time we set random execution time
     *
     * @return Quartz scheduling expression
     */
    public static String buildRandomQuartzExp() {
        // Adding an option to override CRON expression
        String predefinedExp = ConstantValues.storageUsageSyncJobCron.getString();
        if (!StringUtils.isBlank(predefinedExp) && CronUtils.isValid(predefinedExp)) {
            return predefinedExp;
        }
        return String.format("%d 0/1 * ? * * *", TimeRandomizer.randomMinute());
    }
}
