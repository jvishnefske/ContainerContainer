package org.artifactory.repo.remote.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.artifactory.schedule.JobName.JFROG_USER_AGENT_URL;

/**
 * A cron job that fetches the JFrog user agent URL from a repository in JFrog GitHub.
 *
 * @author Hussein Alshqerat
 */
@Slf4j
@JobCommand(jobName = JFROG_USER_AGENT_URL,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Fetch JFrog User Agent URL"
)
public class JFrogUserAgentURLJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            log.info("Fetching JFrog user agent URL.");
            ContextHelper.get().beanForType(JFrogUserAgentService.class).fetchJFrogUserAgentURL();
            log.info("Finished fetching JFrog user agent URL.");
        } catch (Exception e) {
            log.error("Failed to fetch JFrog user agent URL.", e);
            throw new JobExecutionException(e);
        }
    }
}