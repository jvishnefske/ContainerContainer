package org.artifactory.ph.jobs;

import com.jfrog.ph.acl.service.PackageJobController;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.cron.CronUtils;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;

@JobCommand(jobName = JobName.GO_CACHE_CLEANUP, singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        clusterSingleton = true,
        allowedAffinity = false,
        description = "Go Remote Cache Cleanup Job")
public class GoCacheCleanupJob extends QuartzCommand {

    public static final String GO_CLEANUP_SERVICE = "GoCacheCleanupService";

    @Override
    protected void onExecute(JobExecutionContext callbackContext) {
        PackageJobController packageJobController = ContextHelper.get().beanForType(PackageJobController.class);
        packageJobController.run(GO_CLEANUP_SERVICE);
    }

    /**
     * @return Quartz scheduling expression
     */
    public static String buildQuartzExp() {
        // Adding an option to override CRON expression
        String predefinedExp = ConstantValues.goRemoteCacheCleanupJobCron.getString();
        if (!StringUtils.isBlank(predefinedExp) && CronUtils.isValid(predefinedExp)) {
            return predefinedExp;
        }
        return "0 16 3 1/7 * ? *";
    }
}
