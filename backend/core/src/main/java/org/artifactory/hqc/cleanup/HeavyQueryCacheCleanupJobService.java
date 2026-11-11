package org.artifactory.hqc.cleanup;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUser;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ContextCreationListener;
import org.jfrog.storage.hqc.service.HeavyQueryCacheService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.hqcCleanupJobCron;
import static org.artifactory.cron.CronUtils.getNextExecution;
import static org.artifactory.cron.CronUtils.getNextExecutionAfter;
import static org.artifactory.cron.CronUtils.isValid;

/**
 * @author Andrey Tashlitsky
 */

@Slf4j
@Service
@AllArgsConstructor
public class HeavyQueryCacheCleanupJobService implements ContextCreationListener {

    @VisibleForTesting
    static final String CLEANUP_JOB_DEFAULT_CRON = "0 0 0 ? * * *";
    private static final int CLEANUP_JOB_MIN_DAYS = 1;
    private static final int CLEANUP_JOB_MAX_DAYS = 7;

    private TaskService taskService;

    @Override
    public void onContextCreated() {
        String cronExpression = getCronExpression();
        Task t = TaskUtils.createCronTask(HeavyQueryCacheCleanupJob.class, cronExpression);
        log.debug("Scheduling HeavyQueryCacheCleanupJob to run with Cron expression: {}", cronExpression);
        taskService.startTask(t, false);
        log.debug("Scheduled HeavyQueryCacheCleanupJob to run with Cron expression: {}", cronExpression);
    }

    /**
     * Get the cron expression from {@link ConstantValues#hqcCleanupJobCron} and validates it:
     * The cron must be in the range of {@link HeavyQueryCacheCleanupJobService#CLEANUP_JOB_MIN_DAYS} and
     * {@link HeavyQueryCacheCleanupJobService#CLEANUP_JOB_MAX_DAYS}. Otherwise,
     * {@link HeavyQueryCacheCleanupJobService#CLEANUP_JOB_DEFAULT_CRON} is returned.
     *
     * @return A valid cron expression for the cleanup job.
     */
    @VisibleForTesting
    @NonNull
    String getCronExpression() {
        String cronExpression = hqcCleanupJobCron.getString();
        if (!isValid(cronExpression)) {
            log.debug("The cron {} is invalid, setting default {}.", cronExpression, CLEANUP_JOB_DEFAULT_CRON);
            return CLEANUP_JOB_DEFAULT_CRON;
        }
        LocalDateTime nextExecution = LocalDateTime.ofInstant(getNextExecution(cronExpression).toInstant(),
                ZoneOffset.UTC);
        LocalDateTime nextExecutionAfter =
                LocalDateTime.ofInstant(getNextExecutionAfter(cronExpression,
                                nextExecution.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()).toInstant(),
                        ZoneOffset.UTC);
        Duration between = Duration.between(nextExecution, nextExecutionAfter);
        long intervalDays =  TimeUnit.SECONDS.toDays(between.getSeconds());
        if (intervalDays < CLEANUP_JOB_MIN_DAYS || intervalDays > CLEANUP_JOB_MAX_DAYS) {
            log.debug("The cron {} is out of bounds. Allowed interval is {}-{} days, setting default {}.",
                    cronExpression, CLEANUP_JOB_MIN_DAYS, CLEANUP_JOB_MAX_DAYS, CLEANUP_JOB_DEFAULT_CRON);
            return CLEANUP_JOB_DEFAULT_CRON;
        }
        return cronExpression;
    }

    @JobCommand(jobName = JobName.HEAVY_QUERY_CACHE_CLEANUP,
            description = "Heavy query cache DB table cleanup job",
            singleton = true,
            schedulerUser = TaskUser.SYSTEM)
    public static class HeavyQueryCacheCleanupJob extends QuartzCommand {


        @Override
        protected void onExecute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
            log.debug("Heavy query cache DB table cleanup job has started at {}", jobExecutionContext.getFireTime());
            ContextHelper.get().beanForType(HeavyQueryCacheService.class).runCleanup();
            log.debug("Heavy query cache DB table cleanup job has finished. Job run time: {}, Next run at: {}",
                    jobExecutionContext.getJobRunTime(), jobExecutionContext.getNextFireTime());
        }
    }

}
