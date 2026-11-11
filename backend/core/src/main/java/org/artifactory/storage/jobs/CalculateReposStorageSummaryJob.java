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
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.service.InternalStorageService;
import org.artifactory.storage.service.StorageSummaryBackofficeMetricLogger;
import org.jfrog.metrics.backoffice.exception.BackofficeMetricsException;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.artifactory.schedule.JobName.STORAGE_SUMMARY_CALCULATOR;

/**
 * Cron job that calculates and updates the repositories storage summary cache.
 *
 * @author Inbar Tal
 * @author Rotem Kfir
 */
@JobCommand(jobName = STORAGE_SUMMARY_CALCULATOR, singleton = true, schedulerUser = TaskUser.SYSTEM, manualUser =
        TaskUser.SYSTEM,
        description = "Calculate Repositories Storage Summary Job",
        commandsToStop = {@StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE)})
public class CalculateReposStorageSummaryJob extends QuartzCommand {
    private static final Logger log = LoggerFactory.getLogger(CalculateReposStorageSummaryJob.class);

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        InternalArtifactoryContext context = InternalContextHelper.get();
        if (!context.isReady()) {
            log.debug("Skipping execution of '{}', server is not ready yet",
                    CalculateReposStorageSummaryJob.class.getName());
            return;
        }
        InternalStorageService storageService = context.beanForType(InternalStorageService.class);
        storageService.calculateStorageSummary();
        try {
            StorageSummaryBackofficeMetricLogger storageSummaryBackofficeMetricLogger =
                    context.beanForType(StorageSummaryBackofficeMetricLogger.class);
            StorageSummaryService storageSummaryService = context.beanForType(StorageSummaryService.class);
            storageSummaryBackofficeMetricLogger.logMetrics(storageSummaryService.getLocalCache());
        } catch (CacheUnAvailableException e) {
            log.warn("Storage summary cache is not available, skipping backoffice metrics logging");
        } catch (BackofficeMetricsException e) {
            log.error("Failed to log storage summary metrics", e);
        }
    }

    /**
     * To reduce load on AOLs we set random execution time
     *
     * @return Quartz scheduling expression
     */
    public static String buildRandomQuartzExp(String serviceId) {
        // Adding an option to override CRON expression
        String predefinedExp = ConstantValues.calculateStorageSummaryJobCron.getString();
        if (!StringUtils.isBlank(predefinedExp) && CronUtils.isValid(predefinedExp)) {
            return predefinedExp;
        }
        int hash = StringUtils.isBlank(serviceId) ? TimeRandomizer.randomMinute() : serviceId.hashCode();
        int hour = Math.abs(hash % 6); //first hour in the day, 0-5 to have 4 runs in 24 hours
        int minute = Math.abs(hash % 60);
        return String.format ("0 %d %d/6 ? * *", minute, hour);
    }
}
