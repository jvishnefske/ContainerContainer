package org.artifactory.schedule.observe;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.SneakyThrows;
import org.artifactory.repo.cleanup.ArtifactCleanupJob;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.Task;
import org.artifactory.util.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledJobMetricsListenerTest {
    @Test
    void jobToBeExecuted_shouldRegisterActiveJobsGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionContext context = mockContext("std", mockJobDetail(JobName.ANY));

        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(registry);
        listener.jobToBeExecuted(context);

        // Now make sure we have the correct metrics
        String metrics = registry.getMetersAsString();
        assertEquals("jobs.active(GAUGE)[group='artifactory', name='any', scheduler='std']; value=1.0", metrics);
    }

    @Test
    void jobExecutionVetoed_shouldReportMetric() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionContext context = mockContext("std", mockJobDetail(JobName.ANY));

        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(registry);
        listener.jobExecutionVetoed(context);

        // Now make sure we have the correct metrics
        String metrics = registry.getMetersAsString();
        assertEquals("jobs.exec.vetoed(COUNTER)[group='artifactory', name='any', scheduler='std']; count=1.0",
                metrics);
    }

    @Test
    void jobWasExecuted_withoutErrors_shouldReportCorrectMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionContext context = mockContext("std", mockJobDetail(JobName.DUMMY));
        when(context.getJobRunTime()).thenReturn(1500L);

        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(registry);

        listener.jobToBeExecuted(context); // Report the beginning of job execution
        listener.jobWasExecuted(context, null); // Report the completion of job execution

        // Now make sure we have the correct metrics
        String metrics = registry.getMetersAsString();
        assertEquals("jobs.active(GAUGE)[group='artifactory', name='dummy', scheduler='std']; value=0.0\n" +
                "jobs.exec(TIMER)[ex='', group='artifactory', name='dummy', result='ok', scheduler='std'];" +
                " count=1.0, total_time=1.5 seconds, max=1.5 seconds", metrics);
    }

    @Test
    void completeJob_thatWasNotStarted_shouldNotFail() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionContext context = mockContext("std", mockJobDetail(JobName.DUMMY));
        when(context.getJobRunTime()).thenReturn(1000L);

        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(registry);
        listener.jobWasExecuted(context, null); // Report the completion of job execution

        // Note: we do not expect to have the 'jobs.active' gauge, because we did not start the job
        String metrics = registry.getMetersAsString();
        assertEquals("jobs.exec(TIMER)[ex='', group='artifactory', name='dummy', result='ok', scheduler='std'];" +
                " count=1.0, total_time=1.0 seconds, max=1.0 seconds", metrics);
    }

    @Test
    void jobWasExecuted_withErrors_shouldReportCorrectMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionContext context = mockContext("std", mockJobDetail(JobName.DUMMY));
        when(context.getJobRunTime()).thenReturn(750L);

        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(registry);

        listener.jobToBeExecuted(context);
        listener.jobWasExecuted(context, new JobExecutionException(new IllegalArgumentException()));

        String metrics = registry.getMetersAsString();
        assertEquals("jobs.active(GAUGE)[group='artifactory', name='dummy', scheduler='std']; value=0.0\n" +
                "jobs.exec(TIMER)[ex='java.lang.IllegalArgumentException', group='artifactory', name='dummy'," +
                " result='failed', scheduler='std']; count=1.0, total_time=0.75 seconds, max=0.75 seconds", metrics);
    }

    @Test
    void listener_shouldHaveName() {
        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(null);
        assertEquals("ScheduledJobMetricsListener", listener.getName());
    }

    @Test
    void getJobName_shouldReturnClassName_whenNoCustomNameProvided() {
        JobDetail jobDetails = mockJobDetail("sample", Map.of(), ArtifactCleanupJob.class);
        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(null);

        String actual = listener.getJobName(jobDetails);
        assertEquals("ArtifactCleanupJob", actual);
    }

    @Test
    void getErrorCause_shouldSupportExceptionWithoutRootCause() {
        String actual = ExceptionUtils.getErrorCause(new JobExecutionException("Simple Error"));
        assertEquals("JobExecutionException", actual);
    }

    @Test
    void getSchedulerName_shouldReturnUnknown_onException() throws SchedulerException {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.getSchedulerName()).thenThrow(SchedulerException.class);
        String actual = ScheduledJobMetricsListener.getSchedulerName(scheduler);
        assertEquals("(unknown)", actual);
    }

    @Test
    void getSchedulerName_shouldReturnEmptyString_onNull() throws SchedulerException {
        String actual = ScheduledJobMetricsListener.getSchedulerName(null);
        assertEquals("", actual);
    }

    @Test
    void getJobOutcome_shouldSupportValueFromDataMap() throws SchedulerException {
        JobDetail jobDetails = mockJobDetail("sample", Map.of(Task.JOB_OUTCOME, "skip"), Job.class);
        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(null);

        String actual = listener.getJobOutcome(jobDetails, null);
        assertEquals("skip", actual);
    }

    @Test
    void getJobOutcome_shouldNotFail_whenDataMapHasWrongValue() throws SchedulerException {
        JobDetail jobDetails = mockJobDetail("sample", Map.of(Task.JOB_OUTCOME, Map.of()), Job.class);
        ScheduledJobMetricsListener listener = new ScheduledJobMetricsListener(null);

        String actual = listener.getJobOutcome(jobDetails, null);
        assertEquals("ok", actual);
    }

    static JobDetail mockJobDetail(JobName jobName) {
        return mockJobDetail("test", Map.of(Task.JOB_NAME, jobName), Job.class);
    }

    static JobDetail mockJobDetail(String name, Map<String, Object> data, Class<? extends Job> jobClass) {
        JobDataMap dataMap = new JobDataMap(data);

        JobDetail detailMock = mock(JobDetail.class);
        when(detailMock.getKey()).thenReturn(new JobKey(name, "artifactory"));
        when(detailMock.getJobDataMap()).thenReturn(dataMap);
        when(detailMock.getJobClass()).thenAnswer((ignored) -> jobClass);

        return detailMock;
    }

    @SneakyThrows
    static JobExecutionContext mockContext(String schedulerName, JobDetail details) {
        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobDetail()).thenReturn(details);

        Scheduler schedulerMock = mock(Scheduler.class);
        when(schedulerMock.getSchedulerName()).thenReturn(schedulerName);
        when(context.getScheduler()).thenReturn(schedulerMock);

        return context;
    }
}