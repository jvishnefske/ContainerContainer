package org.artifactory.storage.jobs;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.cron.CronUtils;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.service.StorageMetricCollector;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.concurrent.ThreadLocalRandom;

@JobCommand(jobName = JobName.UPDATE_STORAGE_METRIC,
        description = "Update Storage Metric", clusterSingleton = false,
        singleton = true, allowedAffinity = false, schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM)
public class UpdateStorageMetricJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        InternalArtifactoryContext context = InternalContextHelper.get();
        if (context.isReady()) {
            StorageMetricCollector collector = context.beanForType(StorageMetricCollector.class);
            collector.update();
        }
    }

    /**
     * To reduce load on AOLs we set random execution time
     *
     * @return Quartz scheduling expression
     */
    public static String buildRandomQuartzExp() {
        // Adding an option to override CRON expression
        String predefinedExp = ConstantValues.updateStorageStatusJobCron.getString();
        if (!StringUtils.isBlank(predefinedExp) && CronUtils.isValid(predefinedExp)) {
            return predefinedExp;
        }
        int randomMinute = ThreadLocalRandom.current().nextInt(0, 14);
        return String.format ("0 %d/15 * ? * *", randomMinute);
    }

}
