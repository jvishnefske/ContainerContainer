package org.artifactory.schedule;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.LockingException;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.locks.service.MarkerDbLocksService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.artifactory.common.ConstantValues.taskCompletionLockTimeoutRetries;
import static org.artifactory.common.ConstantValues.taskPauseTimeoutSecs;
import static org.artifactory.concurrent.NonStandardLockCategory.CLUSTER_SINGLETON;
import static org.artifactory.schedule.HaQuartzTask.PAUSE_MARKER_PREFIX;
import static org.artifactory.schedule.HaQuartzTask.STOP_MARKER_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


class HaQuartzTaskTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private MarkerDbLocksService dbLocksService;
    @Mock
    private QuartzTask quartzTask;
    @Mock
    private ArtifactoryApplicationContext artifactoryContext;

    private static final String DUMMY_TASK_ID = "dummyTaskID";
    private final String stopMarkerLockPrefix = STOP_MARKER_PREFIX + Job1.getMyJobCommand() + "_";
    private final String pauseMarkerLockPrefix = PAUSE_MARKER_PREFIX + Job1.getMyJobCommand() + "_";
    private final String stopTaskIdMarkerLock = STOP_MARKER_PREFIX + DUMMY_TASK_ID;
    private final String pauseTaskIdMarkerLock = PAUSE_MARKER_PREFIX + DUMMY_TASK_ID;
    private MockitoSession mockitoSession;


    @BeforeEach
    public void init() {
        mockitoSession = mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .startMocking();

        when(artifactoryContext.getServerId()).thenReturn("serverID");
        defineMockJobType(Job1.class);
        when(quartzTask.getTaskId()).thenReturn(DUMMY_TASK_ID);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    public void tearDown() {
        mockitoSession.finishMocking();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testStartClusterSingletonJob() throws JobExecutionException, TimeoutException {
        when(quartzTask.isClusterSingleton()).thenReturn(true);
        when(quartzTask.getTaskLockKeyIncludingKeyAttributes(anyList())).thenReturn("lockKey");
        InternalQuartzCommand command = mock(InternalQuartzCommand.class);

        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.start(command);

        verify(dbLocksService).acquireLock(CLUSTER_SINGLETON.getName(), "lockKey", "serverID", 0L,
                SECONDS);
        verify(command).runCommand();
    }

    @Test
    void testStartClusterSingletonJobExecutionFails() throws JobExecutionException {
        when(quartzTask.isClusterSingleton()).thenReturn(true);

        InternalQuartzCommand command = mock(InternalQuartzCommand.class);
        doThrow(new IllegalStateException()).when(command).runCommand();

        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertThrows(IllegalStateException.class, () -> haQuartzTask.start(command), "Unable to execute job");
    }
    
    @Test
    void testStartNonClusterSingletonJob() throws JobExecutionException {
        when(quartzTask.isClusterSingleton()).thenReturn(false);
        when(quartzTask.getTaskLockKeyIncludingKeyAttributes(anyList())).thenReturn("lockKey");
        InternalQuartzCommand command = mock(InternalQuartzCommand.class);

        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.start(command);

        verifyNoInteractions(dbLocksService);
        verify(quartzTask).start(command);
    }

    @Test
    void testPauseWithPausingTaskIdNull() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);

        haQuartzTask.pause(true, null);

        verify(quartzTask).pause(true);
        verifyNoMoreInteractions(dbLocksService);
    }

    @Test
    void testPauseWithPausePrefixMarkerAndLockNotAcquired() throws TimeoutException {
        String pausingTaskId = "pausingTaskId";
        String uniqueMarkerLock = pauseMarkerLockPrefix + pausingTaskId;
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock)).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);

        haQuartzTask.pause(true, pausingTaskId);

        verify(quartzTask).pause(true);
        verify(dbLocksService).acquireLock(CLUSTER_SINGLETON.getName(), uniqueMarkerLock, "serverID", 0, SECONDS);
    }

    @Test
    void testPauseWithPauseMarkerAndLockAlreadyAcquired() throws TimeoutException {
        String pausingTaskId = "pausingTaskId";
        String uniqueMarkerLock = pauseMarkerLockPrefix + pausingTaskId;
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock)).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);

        haQuartzTask.pause(true, pausingTaskId);

        verify(quartzTask).pause(true);
        verify(dbLocksService).isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock);
        verify(dbLocksService, times(0)).acquireLock(CLUSTER_SINGLETON.getName(), uniqueMarkerLock, "serverID", 0,
                SECONDS);
    }

    @Test
    void testStopWithTaskIdNull() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.stop(true, null);
        verify(quartzTask).stop(true);
        verifyNoMoreInteractions(dbLocksService);
    }

    @Test
    void testStopWithStopMarkerAndLockNotAcquired() throws TimeoutException {
        String stoppingTaskId = "stoppingTaskId";
        String uniqueMarkerLock = stopMarkerLockPrefix + stoppingTaskId;
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock)).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);

        haQuartzTask.stop(true, stoppingTaskId);

        verify(quartzTask).stop(true);
        verify(dbLocksService).acquireLock(CLUSTER_SINGLETON.getName(), uniqueMarkerLock, "serverID", 0,
                SECONDS);
    }

    @Test
    void testStopWithTaskIdAndLockAlreadyAcquired() throws TimeoutException {
        String stopTaskId = "stopTaskId";
        String uniqueMarkerLock = stopMarkerLockPrefix + stopTaskId;
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock)).thenReturn(true);
        
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);

        haQuartzTask.stop(true, stopTaskId);

        verify(quartzTask).stop(true);
        verify(dbLocksService).isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock);
        verify(dbLocksService, times(0)).acquireLock(CLUSTER_SINGLETON.getName(), uniqueMarkerLock, "serverID", 0,
                SECONDS);
    }

    @Test
    void testResumeTaskWhenStoppingTaskIdIsNull() {
        when(quartzTask.resume()).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.resume(null));
        verifyNoInteractions(dbLocksService);
    }

    @Test
    void testResumePausedTask() {
        String pausingTaskId = "pausingTaskId";
        String uniqueMarkerLock = pauseMarkerLockPrefix + pausingTaskId;
        when(quartzTask.resume()).thenReturn(true);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock)).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.pause(true, pausingTaskId);

        boolean isScheduled = haQuartzTask.resume(pausingTaskId);
        assertTrue(isScheduled);
        verify(dbLocksService).unlock(CLUSTER_SINGLETON.getName(), uniqueMarkerLock, "serverID");
    }

    @Test
    void testResumeStoppedTask() {
        String stoppingTaskId = "stoppingTaskId";
        String uniqueMarkerLock = stopMarkerLockPrefix + stoppingTaskId;
        when(quartzTask.resume()).thenReturn(true);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock)).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.stop(true, stoppingTaskId);
        boolean isScheduled = haQuartzTask.resume(stoppingTaskId);
        assertTrue(isScheduled);
        verify(dbLocksService).unlock(CLUSTER_SINGLETON.getName(), uniqueMarkerLock, "serverID");
    }

    @Test
    void testResumeNoStoppedTask() {
        String stoppingTaskId = "stoppingTaskId";
        String uniqueMarkerLock = pauseMarkerLockPrefix + stoppingTaskId;
        when(quartzTask.resume()).thenReturn(true);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), uniqueMarkerLock)).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        boolean isScheduled = haQuartzTask.resume(stoppingTaskId);
        assertTrue(isScheduled);
        verify(dbLocksService, times(0)).unlock(CLUSTER_SINGLETON.getName(), uniqueMarkerLock, "serverID");
    }

    @Test
    void testStartedWhenTaskIsRunningAndNoStopMarker() {
        when(quartzTask.started()).thenReturn(true);
        when(quartzTask.getTaskId()).thenReturn(DUMMY_TASK_ID);
        when(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), stopMarkerLockPrefix,
                stopMarkerLockPrefix + DUMMY_TASK_ID, false))
                .thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.started());
    }

    @Test
    void testCanStartTrueAndNoStopMarker() {
        when(quartzTask.canStart()).thenReturn(true);
        when(quartzTask.getTaskId()).thenReturn(DUMMY_TASK_ID);
        when(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), stopMarkerLockPrefix,
                stopMarkerLockPrefix + DUMMY_TASK_ID, false))
                .thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.canStart());
    }

    @Test
    void testCanStartTrueAndStopMarkerExists() {
        when(quartzTask.canStart()).thenReturn(true);
        when(quartzTask.getTaskId()).thenReturn(DUMMY_TASK_ID);
        when(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), stopMarkerLockPrefix,
                stopMarkerLockPrefix + DUMMY_TASK_ID, false))
                .thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.canStart());
    }

    @Test
    void testCanStartFalseAndNoStopMarker() {
        when(quartzTask.canStart()).thenReturn(false);
        when(quartzTask.getTaskId()).thenReturn(DUMMY_TASK_ID);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), quartzTask.getTaskLockKeyIncludingKeyAttributes(
                Collections.emptyList()))).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.canStart());
    }

    @Test
    void testCanStartFalseAndStopMarkerExists() {
        when(quartzTask.canStart()).thenReturn(false);
        when(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), stopMarkerLockPrefix,
                stopMarkerLockPrefix + DUMMY_TASK_ID, false))
                .thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.canStart());
    }

    @Test
    void testStartedTrue() {
        when(quartzTask.started()).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.started());
    }

    @Test
    void testStartedFalse() {
        when(quartzTask.started()).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.started());
    }

    @Test
    void testCanStartTaskLockTakenFalse() {
        when(quartzTask.canStart()).thenReturn(true);
        when(quartzTask.getTaskLockKeyIncludingKeyAttributes(anyList())).thenReturn("TaskLockKey");
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), "TaskLockKey")).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.canStart());
    }

    @Test
    void testCanStartWhenTaskIsRunningAndStopMarkerExists() {
        when(quartzTask.canStart()).thenReturn(true);
        when(quartzTask.getTaskId()).thenReturn(DUMMY_TASK_ID);
        when(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), stopMarkerLockPrefix,
                stopMarkerLockPrefix + DUMMY_TASK_ID, false))
                .thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.canStart());
    }

    @Test
    void testCanStartWhenTaskIsNotRunningAndNoStopMarker() {
        when(quartzTask.canStart()).thenReturn(false);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), quartzTask.getTaskLockKeyIncludingKeyAttributes(
                Collections.emptyList()))).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.canStart());
    }

    @Test
    void testCanStartWhenTaskIsNotRunningAndStopMarkerExists() {
        when(quartzTask.canStart()).thenReturn(false);
        when(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), stopMarkerLockPrefix,
                stopMarkerLockPrefix + DUMMY_TASK_ID, false))
                .thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.canStart());
    }

    @Test
    void testIsRunningOnClusterWhenJobRunningOnCurrentNode() {
        when(quartzTask.isRunning()).thenReturn(true);
        
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.isRunningOnCluster());
    }

    @Test
    void testIsRunningOnClusterWhenJobIsRunningOnDifferentNode() {
        when(quartzTask.isRunning()).thenReturn(false);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), quartzTask.getTaskLockKeyIncludingKeyAttributes(
                Collections.emptyList()))).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.isRunningOnCluster());
    }

    @Test
    void testIsRunningOnClusterWhenJobIsNotRunning() {
        when(quartzTask.isRunning()).thenReturn(false);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), quartzTask.getTaskLockKeyIncludingKeyAttributes(
                Collections.emptyList()))).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.isRunningOnCluster());
    }

    @Test
    void testProcessActiveOnClusterWhenProcessIsActiveOnCurrentNode() {
        when(quartzTask.processActive()).thenReturn(true);
        
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.processActiveOnCluster());
    }

    @Test
    void testProcessActiveOnClusterWhenProcessIsActiveOnDifferentNode() {
        when(quartzTask.processActive()).thenReturn(false);
        when(quartzTask.isRunning()).thenReturn(true);
        
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.processActiveOnCluster());
    }

    @Test
    void testProcessActiveOnClusterWhenProcessAcquiredLock() {
        when(quartzTask.processActive()).thenReturn(false);
        when(quartzTask.isRunning()).thenReturn(false);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), quartzTask.getTaskLockKeyIncludingKeyAttributes(
                Collections.emptyList()))).thenReturn(true);
        
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.processActiveOnCluster());
    }

    @Test
    void testProcessActiveOnClusterWhenProcessNotActiveAndDidNotAcquiredLock() {
        when(quartzTask.processActive()).thenReturn(false);
        when(quartzTask.isRunning()).thenReturn(false);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), quartzTask.getTaskLockKeyIncludingKeyAttributes(
                Collections.emptyList()))).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.processActiveOnCluster());
    }

    @Test
    void testCancelTaskWithWhenWaitForRunningIsTrue() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.cancel(true, 10);
        verify(quartzTask).cancel(true, 10);
    }

    @Test
    void testCancelTaskWithWhenWaitForRunningIsFalse() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.cancel(false, 10);
        verify(quartzTask).cancel(false, 10);
    }

    @Test
    void testIsAboutToPauseDelegateTrue() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        when(quartzTask.isAboutToPause()).thenReturn(true);
        assertTrue(haQuartzTask.isAboutToPause());
    }

    @Test
    void testIsAboutToPauseNoMarkerFalse() {
        when(dbLocksService.isLockedByPrefixExcluding(any(), any(), anyString(), anyBoolean())).thenReturn(false);
        when(quartzTask.isAboutToPause()).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertFalse(haQuartzTask.isAboutToPause());
    }

    @Test
    void testIsAboutToPauseMarkerExistsTrue() {
        when(dbLocksService.isLockedByPrefixExcluding(any(), any(), anyString(), anyBoolean())).thenReturn(true);
        when(quartzTask.isAboutToPause()).thenReturn(false);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.isAboutToPause());
    }

    @Test
    void testBlockIfPausedAndShouldBreakStopWithoutMarker() {
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.blockIfPausedAndShouldBreak());
    }

    @Test
    void testBlockIfPausedAndShouldBreakStopWithPrefixMarker() {
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(false);
        when(quartzTask.getTaskId()).thenReturn(DUMMY_TASK_ID);
        when(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), stopMarkerLockPrefix,
                stopMarkerLockPrefix + DUMMY_TASK_ID, false)).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.blockIfPausedAndShouldBreak());
    }

    @Test
    void testBlockIfPausedAndShouldBreakStopWithTaskIdMarker() {
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(false);
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), stopTaskIdMarkerLock)).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        assertTrue(haQuartzTask.blockIfPausedAndShouldBreak());
    }

    @Test
    void testBlockIfPausedAndShouldBreakWithPausePrefixMarkerTimeoutThrowsException() {
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(false);
        getBound().setProperty(taskCompletionLockTimeoutRetries, "1");
        getBound().setProperty(taskPauseTimeoutSecs, "0");
        when(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), pauseMarkerLockPrefix,
                pauseMarkerLockPrefix + DUMMY_TASK_ID, false)).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        LockingException lockingException =
                assertThrows(LockingException.class, haQuartzTask::blockIfPausedAndShouldBreak);
        assertEquals("Task: " + haQuartzTask.getTaskId() + " paused for more than: " +
                ConstantValues.taskCompletionLockTimeoutRetries.getInt() + " times.", lockingException.getMessage());
    }

    @Test
    void testBlockIfPausedAndShouldBreakWithPauseTaskIdMarkerTimeoutThrowsException() {
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(false);
        getBound().setProperty(taskCompletionLockTimeoutRetries, "1");
        getBound().setProperty(taskPauseTimeoutSecs, "0");
        when(dbLocksService.isLocked(CLUSTER_SINGLETON.getName(), pauseTaskIdMarkerLock)).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        LockingException lockingException =
                assertThrows(LockingException.class, haQuartzTask::blockIfPausedAndShouldBreak);
        assertEquals("Task: " + haQuartzTask.getTaskId() + " paused for more than: " +
                ConstantValues.taskCompletionLockTimeoutRetries.getInt() + " times.", lockingException.getMessage());
    }

    @Test
    void testWaitForCompletion() {
        when(quartzTask.waitForCompletion(10000)).thenReturn(true);
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.waitForCompletion(10000);
        assertTrue(haQuartzTask.waitForCompletion(10000));
    }

    @Test
    void testRescheduleTask() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.rescheduleTask(1000);
        verify(quartzTask, times(1)).rescheduleTask(1000);
    }

    @Test
    void testShouldRescheduleTask() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.shouldRescheduleTask();
        verify(quartzTask, times(1)).shouldRescheduleTask();
    }

    @Test
    void testSetShouldRescheduleTask() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.setShouldRescheduleTask(true);
        verify(quartzTask, times(1)).setShouldRescheduleTask(true);
    }

    @Test
    void testGetERunIdThrowsException() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, haQuartzTask::getRunId);
        assertEquals(
                "Task is not logged and does not have run ID. Did you forget to mark the @JobCommand annotation" +
                        " as 'monitored=true'?", exception.getMessage());
    }

    @Test
    void testIsRunning() {
        HaQuartzTask haQuartzTask = new HaQuartzTask(quartzTask, dbLocksService);
        haQuartzTask.isRunning();
        verify(quartzTask).isRunning();
        verify(dbLocksService).isLocked(CLUSTER_SINGLETON.getName(), haQuartzTask.getTaskLockKeyIncludingKeyAttributes(
                List.of()));
    }

    private void defineMockJobType(Class<? extends QuartzCommand> jobType) {
        doReturn(jobType).when(quartzTask).getType();
    }

    @JobCommand(jobName = JobName.DUMMY, schedulerUser = TaskUser.SYSTEM, manualUser = TaskUser.SYSTEM)
    public static class Job1 extends QuartzCommand {

        @Override
        protected void onExecute(JobExecutionContext callbackContext) {
        }

        static JobName getMyJobCommand() {
            return Job1.class.getAnnotation(JobCommand.class).jobName();
        }
    }
}