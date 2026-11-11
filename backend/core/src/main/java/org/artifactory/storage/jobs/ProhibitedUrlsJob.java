package org.artifactory.storage.jobs;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.service.ProhibitedUrlsService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * A job that is responsible for fetching and updating the list of prohibited URLs in the system.
 * This job is scheduled to run according to a cron expression and uses the ProhibitedUrlsService to perform its
 * operations.
 *
 * @author Yuval Ohayon
 */
@JobCommand(jobName = JobName.PROHIBITED_URLS,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        allowedAffinity = false,
        description = "Prohibited Urls Job")
@Slf4j
public class ProhibitedUrlsJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            log.debug("Allowed Remote URLs job started");
            ProhibitedUrlsService service = ContextHelper.get().beanForType(ProhibitedUrlsService.class);
            service.fetchAllowedRemoteUrls();
            log.debug("Allowed Remote URLs job finished");
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
