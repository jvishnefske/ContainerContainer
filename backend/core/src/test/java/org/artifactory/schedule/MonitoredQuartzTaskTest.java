package org.artifactory.schedule;

import org.artifactory.quartz.task.history.QuartzTaskHistoryInfo;
import org.artifactory.storage.fs.service.QuartzTaskHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.quartz.JobExecutionException;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED_WITH_ERRORS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.FAILED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.PAUSED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.SKIPPED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.STOPPED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MonitoredQuartzTaskTest {

    @Mock
    private InternalTask quartzTask;
    @Mock
    private QuartzTaskHistoryService taskHistoryService;
    private MonitoredQuartzTask monitoredQuartzTask;
    private MockitoSession mockitoSession;

    private static final int RUN_ID = 1;
    private static final int RUN_ID2 = 2;

    @BeforeEach
    void setUp() {
        mockitoSession = mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();

        monitoredQuartzTask = new MonitoredQuartzTask(quartzTask, taskHistoryService, "externalId");
    }

    @AfterEach
    void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    void constructorNullQuartzTaskThrowsException() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new MonitoredQuartzTask(null, taskHistoryService, "externalId"));
        assertEquals("quartzTask must be non-null", exception.getMessage());
    }

    @Test
    void constructorNullTaskHistoryServiceThrowsException() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new MonitoredQuartzTask(quartzTask, null, "externalId"));
        assertEquals("taskHistoryService must be non-null", exception.getMessage());
    }

    @Test
    void canStartNotMonitored() {
        when(quartzTask.started()).thenReturn(false);
        assertFalse(monitoredQuartzTask.started());
    }

    @Test
    void resumeTrue() {
        when(quartzTask.resume()).thenReturn(true);
        when(quartzTask.isRunning()).thenReturn(true);
        assertTrue(monitoredQuartzTask.resume());
        verify(taskHistoryService).updateStatus(0, IN_PROGRESS);
    }

    @Test
    void resumeFalse() {
        when(quartzTask.resume()).thenReturn(false);
        assertFalse(monitoredQuartzTask.resume());
    }

    @Test
    void resumeHATrue() {
        String stoppingTaskToken = "stoppingTaskToken";
        when(quartzTask.resume(stoppingTaskToken)).thenReturn(true);
        when(quartzTask.isRunning()).thenReturn(true);
        assertTrue(monitoredQuartzTask.resume(stoppingTaskToken));
        verify(taskHistoryService).updateStatus(0, IN_PROGRESS);
    }

    @Test
    void start() throws JobExecutionException {
        when(quartzTask.getTaskId()).thenReturn("taskId");
        when(quartzTask.isManuallyActivated()).thenReturn(false);
        when(quartzTask.getUsername()).thenReturn("username");
        when(taskHistoryService.insert("externalId", QuartzCommand.class.getSimpleName(), "taskId", false,
                "username")).thenReturn(RUN_ID, RUN_ID2);
        QuartzCommand quartzCommand = mock(QuartzCommand.class);
        doReturn(QuartzCommand.class).when(quartzTask).getType();
        monitoredQuartzTask.start(quartzCommand);
        verify(taskHistoryService).insert("externalId", "QuartzCommand", "taskId", false, "username");
        assertEquals(RUN_ID, monitoredQuartzTask.getRunId());
        verify(quartzTask).start(quartzCommand);
    }

    @Test
    void consecutiveRunsOverrideRunId() throws JobExecutionException {
        start();
        monitoredQuartzTask.start(mock(QuartzCommand.class));
        assertEquals(RUN_ID2, monitoredQuartzTask.getRunId());
    }

    @Test
    void failUpdateSuccess() {
        when(taskHistoryService.update(any(QuartzTaskHistoryInfo.class))).thenReturn(true);
        monitoredQuartzTask.fail("errMsg");
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(FAILED, taskHistoryInfo.status());
        assertEquals("errMsg", taskHistoryInfo.statusMessage());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
        verifyNoMoreInteractions(taskHistoryService);
    }

    @Test
    void failUpdateFailure() {
        when(taskHistoryService.update(any(QuartzTaskHistoryInfo.class))).thenReturn(false);
        monitoredQuartzTask.fail("errMsg");
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(FAILED, taskHistoryInfo.status());
        assertEquals("errMsg", taskHistoryInfo.statusMessage());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
        verifyNoMoreInteractions(taskHistoryService);
    }

    @Test
    void skip() {
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        monitoredQuartzTask.skip("skipMsg");
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(SKIPPED, taskHistoryInfo.status());
        assertEquals("skipMsg", taskHistoryInfo.statusMessage());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
        verifyNoMoreInteractions(taskHistoryService);
    }

    @Test
    void completedWithStatusAndMessage() {
        String statusMessage = "statusMessage";
        monitoredQuartzTask.setCompletionStatus(COMPLETED_WITH_ERRORS, statusMessage);
        monitoredQuartzTask.completed();
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(COMPLETED_WITH_ERRORS, taskHistoryInfo.status());
        assertEquals(statusMessage, taskHistoryInfo.statusMessage());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
    }

    @Test
    void completedWithStatusOnly() {
        monitoredQuartzTask.setCompletionStatus(COMPLETED_WITH_ERRORS);
        monitoredQuartzTask.completed();
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(COMPLETED_WITH_ERRORS, taskHistoryInfo.status());
        assertNull(taskHistoryInfo.statusMessage());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
    }

    @Test
    void completedWithoutStatusAndMessage() {
        monitoredQuartzTask.completed();
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(COMPLETED, taskHistoryInfo.status());
        assertNull(taskHistoryInfo.statusMessage());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
    }

    @Test
    void completedAfterFailure() {
        failUpdateSuccess();
        monitoredQuartzTask.completed();
        verifyNoMoreInteractions(taskHistoryService);
    }

    @Test
    void completedAfterSkipped() {
        skip();
        monitoredQuartzTask.completed();
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(SKIPPED, taskHistoryInfo.status());
        assertEquals("skipMsg", taskHistoryInfo.statusMessage());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
        verifyNoMoreInteractions(taskHistoryService);
    }

    @Test
    void blockIfPausedAndShouldBreakFalse() {
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(false);
        assertFalse(monitoredQuartzTask.blockIfPausedAndShouldBreak());
        verify(taskHistoryService).updateStatus(0, IN_PROGRESS);
    }

    @Test
    void blockIfPausedAndShouldBreakTrue() {
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(true);
        assertTrue(monitoredQuartzTask.blockIfPausedAndShouldBreak());
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(STOPPED, taskHistoryInfo.status());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
    }

    @Test
    void blockIfPausedAndShouldBreakPauseAndResume() {
        when(quartzTask.isAboutToPause()).thenReturn(true);
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(false);
        assertFalse(monitoredQuartzTask.blockIfPausedAndShouldBreak());
        verify(taskHistoryService).updateStatus(0, PAUSED);
        verify(taskHistoryService).updateStatus(0, IN_PROGRESS);
    }

    @Test
    void blockIfPausedAndShouldBreakPauseAndStop() {
        when(quartzTask.isAboutToPause()).thenReturn(true);
        when(quartzTask.blockIfPausedAndShouldBreak()).thenReturn(true);
        assertTrue(monitoredQuartzTask.blockIfPausedAndShouldBreak());
        verify(taskHistoryService).updateStatus(0, PAUSED);
        ArgumentCaptor<QuartzTaskHistoryInfo> argumentCaptor = ArgumentCaptor.forClass(QuartzTaskHistoryInfo.class);
        verify(taskHistoryService).update(argumentCaptor.capture());
        QuartzTaskHistoryInfo taskHistoryInfo = argumentCaptor.getValue();
        assertNull(taskHistoryInfo.taskType());
        assertNull(taskHistoryInfo.taskId());
        assertNull(taskHistoryInfo.username());
        assertNull(taskHistoryInfo.serverId());
        assertEquals(STOPPED, taskHistoryInfo.status());
        assertNotEquals(-1, taskHistoryInfo.finishedDate());
    }

    @Test
    void testToString() {
        assertEquals(monitoredQuartzTask.getTaskId(), monitoredQuartzTask.toString());
    }

    @Test
    void manuallyRunSingleRunId() throws JobExecutionException {
        when(quartzTask.getTaskId()).thenReturn("taskId");
        when(quartzTask.isManuallyActivated()).thenReturn(true);
        when(quartzTask.getUsername()).thenReturn("username");
        when(taskHistoryService.insert("externalId", QuartzCommand.class.getSimpleName(), "taskId", true,
                "username")).thenReturn(RUN_ID, RUN_ID2);
        QuartzCommand quartzCommand = mock(QuartzCommand.class);
        doReturn(QuartzCommand.class).when(quartzTask).getType();
        monitoredQuartzTask = new MonitoredQuartzTask(quartzTask, taskHistoryService, "externalId");
        assertEquals(RUN_ID, monitoredQuartzTask.getRunId());
        monitoredQuartzTask.start(quartzCommand);
        assertEquals(RUN_ID, monitoredQuartzTask.getRunId());
    }
}