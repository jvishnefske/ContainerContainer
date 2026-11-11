package org.artifactory.schedule;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
class QuartzTaskTest {

    private QuartzTask quartzTask;
    private final TriggerKey key = new TriggerKey("test", "artifactory");

    @BeforeEach
    void prepareQuartzTask() {
        Trigger trigger = mock(Trigger.class);
        when(trigger.getKey()).thenReturn(key);
        JobDetail jobDetail = mock(JobDetail.class);
        when(jobDetail.getKey()).thenReturn(new JobKey("test"));
        quartzTask = spy(new QuartzTask(HaQuartzTaskTest.Job1.class, trigger, jobDetail));
    }

    @Test
    void testStart() throws JobExecutionException {
        JobDetail jobDetail = mock(JobDetail.class);
        quartzTask = new QuartzTask(HaQuartzTaskTest.Job1.class, null, jobDetail);
        InternalQuartzCommand command = mock(InternalQuartzCommand.class);

        quartzTask.start(command);

        verify(command).runCommand();
    }

    @Test
    void testStartCommandFails() throws JobExecutionException {
        JobDetail jobDetail = mock(JobDetail.class);
        quartzTask = new QuartzTask(HaQuartzTaskTest.Job1.class, null, jobDetail);
        InternalQuartzCommand command = mock(InternalQuartzCommand.class);
        doThrow(new JobExecutionException()).when(command).runCommand();

        assertThrows(JobExecutionException.class, () -> quartzTask.start(command));
    }

    @Test
    void testGetScheduler() {
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = mock(JobDataMap.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        try {
            ArtifactoryContextThreadBinder.bind(context);

            QuartzTask mockTask = new QuartzTask(HaQuartzTaskTest.Job1.class, null, jobDetail);
            when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
            when(jobDataMap.get(anyString()))
                    .thenReturn(JobName.HEARTBEAT).thenReturn(JobName.IMPORT)
                    .thenReturn(JobName.PACKAGE_CLEANUP).thenReturn(JobName.PACKAGE_ARCHIVE)
                    .thenReturn(JobName.READINESS).thenReturn(JobName.SYSTEM_LOAD_MONITOR);

            Scheduler policyScheduler = mock(Scheduler.class);
            Scheduler monitoringScheduler = mock(Scheduler.class);
            when(context.beanForType("hbScheduler", Scheduler.class)).thenReturn(policyScheduler);
            when(context.beanForType("policyScheduler", Scheduler.class)).thenReturn(policyScheduler);
            when(context.beanForType("policyScheduler", Scheduler.class)).thenReturn(policyScheduler);
            when(context.beanForType("monitoringScheduler", Scheduler.class)).thenReturn(monitoringScheduler);
            when(context.beanForType(Scheduler.class)).thenReturn(mock(Scheduler.class));

            Scheduler first = mockTask.getScheduler();
            Scheduler second = mockTask.getScheduler();
            Scheduler third = mockTask.getScheduler();
            Scheduler fourth = mockTask.getScheduler();
            Scheduler fifth = mockTask.getScheduler();
            Scheduler sixth = mockTask.getScheduler();

            assertEquals(first, policyScheduler);
            assertNotEquals(second, policyScheduler);
            assertEquals(third, policyScheduler);
            assertEquals(fourth, policyScheduler);
            assertEquals(fifth, monitoringScheduler);
            assertEquals(sixth, monitoringScheduler);
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void testShouldRescheduleTask() {
        quartzTask.setShouldRescheduleTask(true);
        assertTrue(quartzTask.shouldRescheduleTask());
        quartzTask.setShouldRescheduleTask(false);
        assertFalse(quartzTask.shouldRescheduleTask());
    }

    @Test
    void testRescheduleTask() {
        Date currentTime = new Date(System.currentTimeMillis());
        doNothing().when(quartzTask).cancelTask();
        doNothing().when(quartzTask).scheduleTask();
        quartzTask.setShouldRescheduleTask(true);
        quartzTask.rescheduleTask(1000);
        verify(quartzTask, times(1)).cancelTask();
        verify(quartzTask, times(1)).scheduleTask();
        Trigger trigger = quartzTask.getTrigger();
        assertEquals(trigger.getKey(), key);
        assertInstanceOf(SimpleScheduleBuilder.class, trigger.getScheduleBuilder());
        Date startTime = trigger.getStartTime();
        assertTrue(startTime.getTime() >= currentTime.getTime() + 1000);
        assertFalse(quartzTask.shouldRescheduleTask());
    }

    @ParameterizedTest
    @EnumSource(TaskState.class)
    void testIsAboutToPause(TaskState taskState) {
        ReflectionTestUtils.setField(quartzTask, "state", taskState);
        boolean aboutToPause = quartzTask.isAboutToPause();
        if (taskState == TaskState.PAUSING) {
            assertTrue(aboutToPause);
        } else {
            assertFalse(aboutToPause);
        }
    }

    @ParameterizedTest
    @EnumSource(TaskState.class)
    void canStart(TaskState taskState) {
        ReflectionTestUtils.setField(quartzTask, "state", taskState);
        boolean canStart = quartzTask.canStart();
        if (taskState == TaskState.SCHEDULED) {
            assertTrue(canStart);
        } else {
            assertFalse(canStart);
        }
    }

    @Test
    void testGetExecutionIdThrowsException() {
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, quartzTask::getRunId);
        assertEquals(
                "Task is not logged and does not have run ID. Did you forget to mark the @JobCommand annotation" +
                        " as 'monitored=true'?", exception.getMessage());
    }
}