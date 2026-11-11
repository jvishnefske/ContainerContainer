package org.artifactory.schedule.observe;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.Task;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static org.artifactory.util.ExceptionUtils.getErrorCause;

/**
 * This {@link JobListener} monitors execution of scheduled jobs and reports metrics for them.
 *
 * <p>
 *     This class reports the following metrics:
 *     <ul>
 *         <li><b>GAUGE jobs_active_total</b> - the number of scheduled jobs that are running at the moment</li>
 *         <li><b>SUMMARY jobs_exec_seconds</b> - the total number and total duration of scheduled jobs</li>
 *         <li><b>GAUGE jobs_exec_seconds_max</b> - the maximal duration of a scheduled job</li>
 *         <li><b>COUNTER jobs_exec_vetoed_total</b> (optional) - the total number of jobs that were scheduled
 *         but then vetoed by trigger listeners.</li>
 *     </ul>
 * </p>
 * <p>
 *     Monitoring the number of currently running jobs requires us to keep a thread-safe counter per job type.
 *     The listener increments this counter when a job starts ({@link #jobToBeExecuted(JobExecutionContext)})
 *     and decrements it when job completes (in {@link #jobWasExecuted(JobExecutionContext, JobExecutionException)}.
 * </p>
 *
 * <p>
 *   In the future we may move this class to the infrastructure project - to allow using it in other JFrog services.
 * </p>
 *
 * @author Sergey Steinvil
 */
@Component
@Slf4j
public class ScheduledJobMetricsListener implements JobListener {
    private final MeterRegistry meterRegistry;
    private final Map<String, LongAdder> runningJobs = new ConcurrentHashMap<>();

    public ScheduledJobMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String getName() {
        return "ScheduledJobMetricsListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        JobDetail details = context.getJobDetail();
        String jobName = getJobName(details);
        runningJobs
                .computeIfAbsent(jobName, key -> createActiveJobsGaugeOnFirstRun(context.getScheduler(), details, key))
                .increment();
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        JobDetail details = context.getJobDetail();
        String jobName = getJobName(details);

        // Update the number of currently running jobs (for the gauge metric)
        decrementRunningJobsCounter(details, jobName);

        // Report the duration of the completed job
        Timer.builder("jobs.exec")
                .tags(buildJobTags(context.getScheduler(), details, jobName))
                .tags(
                        "result", getJobOutcome(details, jobException),
                        "ex", getErrorCause(jobException)
                )
                .description("The time of scheduled job execution")
                .register(this.meterRegistry)
                .record(Math.max(context.getJobRunTime(), 0), TimeUnit.MILLISECONDS);
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        JobDetail details = context.getJobDetail();
        Counter.builder("jobs.exec.vetoed")
                .tags(buildJobTags(context.getScheduler(), details, getJobName(details)))
                .description("The total number of scheduled jobs that were vetoed by trigger listeners")
                .register(this.meterRegistry)
                .increment();
    }

    //#region ---------- Monitor the number of running jobs (per type) ----------
    protected LongAdder createActiveJobsGaugeOnFirstRun(Scheduler scheduler, JobDetail details, String jobName) {
        LongAdder activeJobsCounter = new LongAdder();

        Gauge.builder("jobs.active", activeJobsCounter::doubleValue)
                .description("The number of scheduled jobs running at the moment")
                .tags(buildJobTags(scheduler, details, jobName))
                .register(this.meterRegistry);

        return activeJobsCounter;
    }

    protected void decrementRunningJobsCounter(JobDetail details, String jobName) {
        LongAdder activeJobs = this.runningJobs.get(jobName);
        if (activeJobs != null) {
            activeJobs.decrement();
        } else {
            log.warn("Count not find active jobs counter for job name={} (key={})", jobName, details.getKey());
        }
    }
    //#endregion --- Monitor the number of running jobs (per type) ---

    //#region ---------- Helpers ----------
    protected String getJobName(JobDetail details) {
        if (details.getJobDataMap().get(Task.JOB_NAME) instanceof JobName name) {
            return name.asText();
        }
        return details.getJobClass().getSimpleName();
    }

    protected String getJobOutcome(JobDetail details, JobExecutionException jobException) {
        try {
            String result = details.getJobDataMap().getString(Task.JOB_OUTCOME);
            if (result != null) {
                return result;
            }
        } catch (Exception ex) {
            // Ignore exceptions
        }
        return jobException == null ? "ok" : "failed";
    }

    static Tags buildJobTags(Scheduler scheduler, JobDetail details, String jobName) {
        return Tags.of(
                "scheduler", getSchedulerName(scheduler),
                "group", details.getKey().getGroup(),
                "name", jobName);
    }

    static String getSchedulerName(Scheduler scheduler) {
        if (scheduler == null) {
            return "";
        }
        try {
            return scheduler.getSchedulerName();
        } catch (Exception ex) {
            // Note: swallow exception
            return "(unknown)";
        }
    }
    //#endregion --- Helpers ---
}