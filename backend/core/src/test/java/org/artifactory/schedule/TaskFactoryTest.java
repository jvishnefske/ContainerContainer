package org.artifactory.schedule;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.locks.service.MarkerDbLocksService;
import org.artifactory.storage.fs.service.QuartzTaskHistoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.when;

class TaskFactoryTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private QuartzTaskHistoryService quartzTaskHistoryService;
    @Mock
    private MarkerDbLocksService markerDbLocksService;
    private MockitoSession mockitoSession;

    @BeforeEach
    void setUp() {
        mockitoSession = mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        mockitoSession.finishMocking();
        ArtifactoryContextThreadBinder.unbind();
        getBound().getHaNodeProperties().setProperty("enabled", "false");
    }

    @Test
    void nonHaNonMonitoredTaskCreationSuccess() {
        Trigger trigger = mock(Trigger.class);
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = mock(JobDataMap.class);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        Task quartzTask = TaskFactory.createQuartzTask(TestJob.class, trigger, jobDetail, null, false);
        assertNotNull(quartzTask);
        assertEquals(QuartzTask.class, quartzTask.getClass());
    }

    @Test
    void nonHaNonMonitoredManualTaskCreationSuccess() {
        Trigger trigger = mock(Trigger.class);
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = mock(JobDataMap.class);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        Task quartzTask = TaskFactory.createQuartzTask(TestJob.class, trigger, jobDetail, null, true);
        assertNotNull(quartzTask);
        assertEquals(QuartzTask.class, quartzTask.getClass());
        assertTrue(quartzTask instanceof InternalTask internalTask && internalTask.isManuallyActivated());
    }

    @Test
    void haNonMonitoredTaskCreationSuccess() {
        getBound().getHaNodeProperties().setProperty("enabled", "true");
        Trigger trigger = mock(Trigger.class);
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = mock(JobDataMap.class);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        when(jobDetail.getKey()).thenReturn(new JobKey("taskId"));
        Task quartzTask = TaskFactory.createQuartzTask(TestJob.class, trigger, jobDetail, null, false);
        assertNotNull(quartzTask);
        assertEquals(HaQuartzTask.class, quartzTask.getClass());
        Object innerQuartzTask = ReflectionTestUtils.getField(quartzTask, HaQuartzTask.class, "quartzTask");
        assertNotNull(innerQuartzTask);
        assertEquals(QuartzTask.class, innerQuartzTask.getClass());
    }

    @Test
    void nonHaMonitoredTaskCreationSuccess() {
        when(context.beanForType(QuartzTaskHistoryService.class)).thenReturn(quartzTaskHistoryService);
        Trigger trigger = mock(Trigger.class);
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = mock(JobDataMap.class);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        Task quartzTask = TaskFactory.createQuartzTask(MonitoredTestJob.class, trigger, jobDetail, null, false);
        assertNotNull(quartzTask);
        assertEquals(MonitoredQuartzTask.class, quartzTask.getClass());
        Object innerQuartzTask = ReflectionTestUtils.getField(quartzTask, MonitoredQuartzTask.class, "quartzTask");
        assertNotNull(innerQuartzTask);
        assertEquals(QuartzTask.class, innerQuartzTask.getClass());
    }

    @Test
    void haMonitoredTaskCreationSuccess() {
        when(context.beanForType(QuartzTaskHistoryService.class)).thenReturn(quartzTaskHistoryService);
        when(context.beanForType(MarkerDbLocksService.class)).thenReturn(markerDbLocksService);
        getBound().getHaNodeProperties().setProperty("enabled", "true");
        Trigger trigger = mock(Trigger.class);
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = mock(JobDataMap.class);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        when(jobDetail.getKey()).thenReturn(new JobKey("taskId"));
        Task quartzTask = TaskFactory.createQuartzTask(MonitoredTestJob.class, trigger, jobDetail, null, false);
        assertNotNull(quartzTask);
        assertEquals(MonitoredQuartzTask.class, quartzTask.getClass());
        Object innerQuartzTask = ReflectionTestUtils.getField(quartzTask, MonitoredQuartzTask.class, "quartzTask");
        assertNotNull(innerQuartzTask);
        assertEquals(HaQuartzTask.class, innerQuartzTask.getClass());
    }

    @Test
    void monitoredTaskCreationMandatoryExternalIdNotProvidedExceptionThrown() {
        String expectedErrMsg =
                "JobCommand annotation 'mandatoryExternalId' for type: org.artifactory.schedule" +
                        ".TaskFactoryTest$MonitoredMandatoryExternalIdTestJob is 'true' but no external ID was " +
                        "provided during creation";
        Trigger trigger = mock(Trigger.class);
        JobDetail jobDetail = mock(JobDetail.class);
        JobDataMap jobDataMap = mock(JobDataMap.class);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TaskFactory.createQuartzTask(MonitoredMandatoryExternalIdTestJob.class, trigger, jobDetail, null,
                        false));
        assertEquals(expectedErrMsg, exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> TaskFactory.createQuartzTask(MonitoredMandatoryExternalIdTestJob.class, trigger, jobDetail,
                        StringUtils.EMPTY, false));
        assertEquals(expectedErrMsg, exception.getMessage());
    }

    @JobCommand(jobName = JobName.TEST_JOB1, manualUser = TaskUser.CURRENT, schedulerUser = TaskUser.SYSTEM)
    private static class TestJob extends QuartzCommand {
        @Override
        protected void onExecute(JobExecutionContext callbackContext) {
        }
    }

    @JobCommand(jobName = JobName.TEST_JOB2, monitored = true, manualUser = TaskUser.CURRENT, schedulerUser =
            TaskUser.SYSTEM)
    private static class MonitoredTestJob extends QuartzCommand {
        @Override
        protected void onExecute(JobExecutionContext callbackContext) {
        }
    }

    @JobCommand(jobName = JobName.TEST_JOB3, monitored = true, externalIdMandatory = true, manualUser =
            TaskUser.CURRENT, schedulerUser = TaskUser.SYSTEM)
    private static class MonitoredMandatoryExternalIdTestJob extends QuartzCommand {
        @Override
        protected void onExecute(JobExecutionContext callbackContext) {
        }
    }
}