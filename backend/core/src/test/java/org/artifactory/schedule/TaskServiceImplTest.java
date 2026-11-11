package org.artifactory.schedule;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.request.GracefulShutdownAware;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.schedule.quartz.QuartzTaskHistoryExpiredAndOrphanedMarkerJob;
import org.artifactory.storage.db.locks.service.MarkerDbLocksService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.concurrency.ConflictGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.locksTimeoutSecs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * @author Alexei Vainshtein
 */
@SuppressWarnings("unchecked")
public class TaskServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private ArtifactorySystemProperties artifactProperties;
    private TaskServiceImpl taskService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        clearCurrentTaskId();
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        taskService = new TaskServiceImpl();
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactProperties);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
        clearCurrentTaskId();
    }

    /**
     * Clears the {@link TaskCallback} current-task thread-local, which is an {@link InheritableThreadLocal} that can
     * leak across tests (and test classes) sharing a Surefire fork. A leaked value makes
     * {@code canStartManualTask} fail with "Cannot check for manual run ... from inside a running task".
     */
    private static void clearCurrentTaskId() {
        ThreadLocal<?> storage =
                (ThreadLocal<?>) ReflectionTestUtils.getField(TaskCallback.class, "currentTaskIdStorage");
        if (storage != null) {
            storage.remove();
        }
    }

    @Test
    public void testRescheduleTask() throws SchedulerException {
        InternalTask task1 = (InternalTask) TaskUtils.createCronTask(DummyQuartzCommand.class, "* * * * * ?");
        taskService.startTask(task1, false);
        when(context.isReady()).thenReturn(true);
        Scheduler scheduler = mock(Scheduler.class);
        when(context.beanForType(Scheduler.class)).thenReturn(scheduler);
        taskService.onContextReady();
        task1.setShouldRescheduleTask(true);
        assertNotNull(taskService.getActiveTask(task1.getTaskId(), true));
        taskService.rescheduleTask(task1.getTaskId());
        verify(scheduler, times(1)).deleteJob(any());
        verify(scheduler, times(2)).scheduleJob(any(), any());
        assertNotNull(taskService.getActiveTask(task1.getTaskId(), true));
        assertTrue(taskService.hasActiveTasks(t -> t.getType().equals(DummyQuartzCommand.class)));
        assertFalse(task1.shouldRescheduleTask());
    }

    @Test
    public void startManualTaskClusterSingleton() {
        TaskServiceImpl taskServiceSpy = spy(TaskServiceImpl.class);
        Task task = mock(Task.class);
        when(task.getAttribute(Task.JOB_NAME)).thenReturn(JobName.DUMMY);
        when(task.getAttribute(Task.HARD_AFFINITY)).thenReturn(false);
        
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isAllowedToRun(any(), anyBoolean())).thenReturn(true);
        ReflectionTestUtils.setField(taskServiceSpy, "addonsManager", addonsManager);
        
        doReturn("taskId").when(taskServiceSpy).startTask(task, false);
        try (MockedStatic<TaskUtils> taskUtilsMockedStatic = mockStatic(TaskUtils.class)) {

            taskUtilsMockedStatic.when(() -> TaskUtils.createManualTask(DummyQuartzCommandA.class, 0, "externalId",
                    "desc (manual " + "trigger)")).thenReturn(task);
            taskUtilsMockedStatic.when(
                            () -> TaskUtils.getTaskLockKeyIncludingKeyAttributes(JobName.DUMMY.asText(), Map.of(),
                                    List.of()))
                    .thenReturn("key");

            QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
            ConflictGuard conflictGuard = mock(ConflictGuard.class);
            when(conflictsGuard.getLock("key")).thenReturn(conflictGuard);
            when(conflictGuard.isLocked()).thenReturn(false);
            ReflectionTestUtils.setField(taskServiceSpy, "conflictsGuard", conflictsGuard);

            BasicStatusHolder basicStatusHolder = new BasicStatusHolder();
            Task returnedTask =
                    taskServiceSpy.startManualTask(DummyQuartzCommandA.class, "externalId", basicStatusHolder, Map.of(),
                            0,
                            "desc");
            assertEquals(task, returnedTask);
            assertFalse(basicStatusHolder.isError());
            verify(taskServiceSpy).startTask(task, false);
        }
    }

    @Test
    public void startManualTask_shouldPropagateToAffinity_whenPropagateToAffinityIsTrue() {
        TaskServiceImpl taskServiceSpy = spy(TaskServiceImpl.class);
        Task task = mock(Task.class);
        when(task.getTaskId()).thenReturn("taskId");
        when(task.getAttribute(Task.JOB_NAME)).thenReturn(JobName.DUMMY);
        when(task.getAttribute(Task.HARD_AFFINITY)).thenReturn(true);
        
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isAllowedToRun(any(), anyBoolean())).thenReturn(false);
        ReflectionTestUtils.setField(taskServiceSpy, "addonsManager", addonsManager);
        
        try (MockedStatic<TaskUtils> taskUtilsMockedStatic = mockStatic(TaskUtils.class)) {
            taskUtilsMockedStatic.when(() -> TaskUtils.createManualTask(DummyQuartzCommandA.class, 0, "externalId",
                    "desc (manual trigger)")).thenReturn(task);
            taskUtilsMockedStatic.when(
                            () -> TaskUtils.getTaskLockKeyIncludingKeyAttributes(JobName.DUMMY.asText(), Map.of(),
                                    List.of()))
                    .thenReturn("key");

            QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
            ConflictGuard conflictGuard = mock(ConflictGuard.class);
            when(conflictsGuard.getLock("key")).thenReturn(conflictGuard);
            when(conflictGuard.isLocked()).thenReturn(false);
            ReflectionTestUtils.setField(taskServiceSpy, "conflictsGuard", conflictsGuard);

            BasicStatusHolder basicStatusHolder = new BasicStatusHolder();
            Task returnedTask = taskServiceSpy.startManualTask(
                    DummyQuartzCommandA.class, "externalId", basicStatusHolder, Map.of(), 0, "desc", true);
            
            assertEquals(task, returnedTask);
            assertFalse(basicStatusHolder.isError());
            verify(haAddon).propagateTaskToAvailableAffinityNode(task);
            verify(taskServiceSpy, times(0)).startTask(task, false);
        }
    }

    @Test
    public void startManualTask_shouldRunLocally_whenPropagateToAffinityIsTrueButAllowedToRun() {
        TaskServiceImpl taskServiceSpy = spy(TaskServiceImpl.class);
        Task task = mock(Task.class);
        when(task.getTaskId()).thenReturn("taskId");
        when(task.getAttribute(Task.JOB_NAME)).thenReturn(JobName.DUMMY);
        when(task.getAttribute(Task.HARD_AFFINITY)).thenReturn(true);
        
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isAllowedToRun(any(), anyBoolean())).thenReturn(true);
        ReflectionTestUtils.setField(taskServiceSpy, "addonsManager", addonsManager);
        doReturn("taskId").when(taskServiceSpy).startTask(task, false);
        
        try (MockedStatic<TaskUtils> taskUtilsMockedStatic = mockStatic(TaskUtils.class)) {
            taskUtilsMockedStatic.when(() -> TaskUtils.createManualTask(DummyQuartzCommandA.class, 0, "externalId",
                    "desc (manual trigger)")).thenReturn(task);
            taskUtilsMockedStatic.when(
                            () -> TaskUtils.getTaskLockKeyIncludingKeyAttributes(JobName.DUMMY.asText(), Map.of(),
                                    List.of()))
                    .thenReturn("key");

            QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
            ConflictGuard conflictGuard = mock(ConflictGuard.class);
            when(conflictsGuard.getLock("key")).thenReturn(conflictGuard);
            when(conflictGuard.isLocked()).thenReturn(false);
            ReflectionTestUtils.setField(taskServiceSpy, "conflictsGuard", conflictsGuard);

            BasicStatusHolder basicStatusHolder = new BasicStatusHolder();
            Task returnedTask = taskServiceSpy.startManualTask(
                    DummyQuartzCommandA.class, "externalId", basicStatusHolder, Map.of(), 0, "desc", true);
            
            assertEquals(task, returnedTask);
            assertFalse(basicStatusHolder.isError());
            verify(taskServiceSpy).startTask(task, false);
            verify(haAddon, times(0)).propagateTaskToAvailableAffinityNode(task);
        }
    }

    @Test
    public void startManualTask_shouldRunLocally_whenPropagateToAffinityIsFalse() {
        TaskServiceImpl taskServiceSpy = spy(TaskServiceImpl.class);
        Task task = mock(Task.class);
        when(task.getTaskId()).thenReturn("taskId");
        when(task.getAttribute(Task.JOB_NAME)).thenReturn(JobName.DUMMY);
        when(task.getAttribute(Task.HARD_AFFINITY)).thenReturn(false);
        
        doReturn("taskId").when(taskServiceSpy).startTask(task, false);
        
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isAllowedToRun(any(), anyBoolean())).thenReturn(false);
        ReflectionTestUtils.setField(taskServiceSpy, "addonsManager", addonsManager);
        
        try (MockedStatic<TaskUtils> taskUtilsMockedStatic = mockStatic(TaskUtils.class)) {
            taskUtilsMockedStatic.when(() -> TaskUtils.createManualTask(DummyQuartzCommandA.class, 0, "externalId",
                    "desc (manual trigger)")).thenReturn(task);
            taskUtilsMockedStatic.when(
                            () -> TaskUtils.getTaskLockKeyIncludingKeyAttributes(JobName.DUMMY.asText(), Map.of(),
                                    List.of()))
                    .thenReturn("key");

            QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
            ConflictGuard conflictGuard = mock(ConflictGuard.class);
            when(conflictsGuard.getLock("key")).thenReturn(conflictGuard);
            when(conflictGuard.isLocked()).thenReturn(false);
            ReflectionTestUtils.setField(taskServiceSpy, "conflictsGuard", conflictsGuard);

            BasicStatusHolder basicStatusHolder = new BasicStatusHolder();
            Task returnedTask = taskServiceSpy.startManualTask(
                    DummyQuartzCommandA.class, "externalId", basicStatusHolder, Map.of(), 0, "desc", false);
            
            assertEquals(task, returnedTask);
            assertFalse(basicStatusHolder.isError());
            verify(taskServiceSpy).startTask(task, false);
            verify(haAddon, never()).propagateTaskToAvailableAffinityNode(any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void stopNonHaTask(boolean wait) {
        InternalTask task = mock(InternalTask.class);
        when(task.getTaskId()).thenReturn("taskId");

        Map<String, InternalTask> activeTasksById =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasksById);
        activeTasksById.put("taskId", task);

        taskService.stopTask("taskId", wait);

        verify(task).stop(wait);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void pauseNonHaTask(boolean wait) {
        InternalTask task = mock(InternalTask.class);
        when(task.getTaskId()).thenReturn("taskId");

        Map<String, InternalTask> activeTasksById =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasksById);
        activeTasksById.put("taskId", task);

        taskService.pauseTask("taskId", wait);

        verify(task).pause(wait);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void stopHaTask(boolean wait) throws InterruptedException {
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(context.getServerId()).thenReturn("serverID");
        QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
        ReflectionTestUtils.setField(taskService, "conflictsGuard", conflictsGuard);
        ReflectionTestUtils.setField(taskService, "addonsManager", addonsManager);

        taskService.stopTask("taskId", wait);

        long timeout = wait ? locksTimeoutSecs.getLong() : 0;
        String stopTaskIdLockMarker = HaQuartzTask.STOP_MARKER_PREFIX + "taskId";
        verify(conflictsGuard).tryToLock(stopTaskIdLockMarker, timeout, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void stopHaTaskInterruptedException(boolean wait) throws InterruptedException {
        stopHaTaskException(wait, new InterruptedException("Thrown by test"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void stopHaTaskException(boolean wait) throws InterruptedException {
        stopHaTaskException(wait, new RuntimeException("Thrown by test"));
    }

    private void stopHaTaskException(boolean wait, Throwable thrownByTest) throws InterruptedException {
        Logger logger = (Logger) LoggerFactory.getLogger(TaskServiceImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);

        when(context.getServerId()).thenReturn("serverID");

        QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
        String stopTaskIdLockMarker = HaQuartzTask.STOP_MARKER_PREFIX + "taskId";
        long timeout = wait ? locksTimeoutSecs.getLong() : 0;
        doThrow(thrownByTest).when(conflictsGuard).tryToLock(stopTaskIdLockMarker, timeout, TimeUnit.SECONDS);

        ReflectionTestUtils.setField(taskService, "conflictsGuard", conflictsGuard);
        ReflectionTestUtils.setField(taskService, "addonsManager", addonsManager);

        taskService.stopTask("taskId", wait);

        List<ILoggingEvent> logEvents = listAppender.list;
        assertTrue(logEvents.stream().anyMatch(logEvent -> logEvent.getFormattedMessage()
                .equals("Failed to stop task with ID: taskId by locking stop marker: " + stopTaskIdLockMarker)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void pauseHaTask(boolean wait) throws InterruptedException {
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(context.getServerId()).thenReturn("serverID");
        QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
        ReflectionTestUtils.setField(taskService, "conflictsGuard", conflictsGuard);
        ReflectionTestUtils.setField(taskService, "addonsManager", addonsManager);

        taskService.pauseTask("taskId", wait);

        long timeout = wait ? locksTimeoutSecs.getLong() : 0;
        String pauseTaskIdLockMarker = HaQuartzTask.PAUSE_MARKER_PREFIX + "taskId";
        verify(conflictsGuard).tryToLock(pauseTaskIdLockMarker, timeout, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void pauseHaTaskInterruptedException(boolean wait) throws InterruptedException {
        pauseHaTaskException(wait, new InterruptedException("Thrown by test"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void pauseHaTaskException(boolean wait) throws InterruptedException {
        pauseHaTaskException(wait, new RuntimeException("Thrown by test"));
    }

    private void pauseHaTaskException(boolean wait, Throwable thrownByTest) throws InterruptedException {
        Logger logger = (Logger) LoggerFactory.getLogger(TaskServiceImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);

        when(context.getServerId()).thenReturn("serverID");

        QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
        String pauseTaskIdLockMarker = HaQuartzTask.PAUSE_MARKER_PREFIX + "taskId";
        long timeout = wait ? locksTimeoutSecs.getLong() : 0;
        doThrow(thrownByTest).when(conflictsGuard).tryToLock(pauseTaskIdLockMarker, timeout, TimeUnit.SECONDS);

        ReflectionTestUtils.setField(taskService, "conflictsGuard", conflictsGuard);
        ReflectionTestUtils.setField(taskService, "addonsManager", addonsManager);

        taskService.pauseTask("taskId", wait);

        List<ILoggingEvent> logEvents = listAppender.list;
        assertTrue(logEvents.stream().anyMatch(logEvent -> logEvent.getFormattedMessage()
                .equals("Failed to pause task with ID: taskId by locking stop marker: " + pauseTaskIdLockMarker)));
    }

    @Test
    void resumeHaTask() {
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(context.getServerId()).thenReturn("serverID");
        MarkerDbLocksService markerDbLocksService = mock(MarkerDbLocksService.class);
        ReflectionTestUtils.setField(taskService, "conflictsGuard", new QuartzConflictsGuard(markerDbLocksService));
        ReflectionTestUtils.setField(taskService, "addonsManager", addonsManager);
        assertTrue(taskService.resumeTask("taskId"));
    }

    @Test
    public void resumeHaTaskException() {
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(context.getServerId()).thenReturn("serverID");
        QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
        String pauseTaskIdLockMarker = HaQuartzTask.PAUSE_MARKER_PREFIX + "taskId";
        doThrow(new RuntimeException("Thrown by test")).when(conflictsGuard).forceUnlock(pauseTaskIdLockMarker);
        ReflectionTestUtils.setField(taskService, "conflictsGuard", conflictsGuard);
        ReflectionTestUtils.setField(taskService, "addonsManager", addonsManager);
        assertFalse(taskService.resumeTask("taskId"));
    }

    @Test
    public void checkCanStartManualTaskOffline() {
        when(context.isOffline()).thenReturn(true);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(DummyQuartzCommandC.class, statusHolder, Map.of());
        assertTrue(statusHolder.isError());
        assertEquals("Artifactory is in offline state, task are not allowed to run during offline state!",
                statusHolder.getLastError().getMessage());
    }

    @Test
    public void checkCanStartManualTaskNoJobCommandAnnotation() {
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(NoJobCommandAnnotation.class, statusHolder, Map.of());
        assertTrue(statusHolder.isError());
        assertEquals("Task type " + NoJobCommandAnnotation.class.getName() + " does not have the " +
                JobCommand.class.getName() + " annotation!", statusHolder.getLastError().getMessage());
    }

    @Test
    public void checkCanStartManualTaskNonStaticNoJobCommandAnnotation() {
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(null, NoJobCommandAnnotation.class, statusHolder, Map.of());
        assertTrue(statusHolder.isError());
        assertEquals("Task type " + NoJobCommandAnnotation.class.getName() + " does not have the " +
                JobCommand.class.getName() + " annotation!", statusHolder.getLastError().getMessage());
    }

    @Test
    public void checkCanStartManualTaskJobCommandUserInvalid() {
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(JobCommandManualUserInvalid.class, statusHolder, Map.of());
        assertTrue(statusHolder.isError());
        assertEquals("Task type " + JobCommandManualUserInvalid.class.getName() + " is not defined to run manually!",
                statusHolder.getLastError().getMessage());
    }

    @Test
    public void checkCanStartManualTaskFromInsideRunningTask() {
        try (var taskCallbackMockedStatic = mockStatic(TaskCallback.class)) {
            taskCallbackMockedStatic.when(TaskCallback::currentTaskId).thenReturn("taskId");
            MutableStatusHolder statusHolder = new BasicStatusHolder();
            taskService.checkCanStartManualTask(DummyQuartzCommand.class, statusHolder, Map.of());
            assertTrue(statusHolder.isError());
            assertEquals("Cannot check for manual run with status from inside a running task: taskId",
                    statusHolder.getLastError().getMessage());
        }
    }

    @Test
    public void checkCanStartManualTaskConflictingTaskRunning() {
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InternalTask dummyTask = mock(InternalTask.class);
        doReturn(DummyQuartzCommand.class).when(dummyTask).getType();
        when(dummyTask.processActiveOnCluster()).thenReturn(true);
        Map<String, InternalTask> activeTasks =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasks);
        activeTasks.put("dummyTaskId", dummyTask);
        taskService.checkCanStartManualTask(CommandImpossibleToRun.class, statusHolder, Map.of());
        assertTrue(statusHolder.isError());
        assertEquals("Task " + CommandImpossibleToRun.class.getName() + " cannot stop a mandatory related job " +
                dummyTask.getType().getName() + " while it's running!", statusHolder.getLastError().getMessage());
    }

    @Test
    public void checkCanStartManualTaskIdenticalTaskRunning() {
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InternalTask dummyTask = mock(InternalTask.class);
        doReturn(CommandImpossibleToRun.class).when(dummyTask).getType();
        when(dummyTask.processActiveOnCluster()).thenReturn(false);
        when(dummyTask.isManuallyActivated()).thenReturn(true);
        when(dummyTask.wasCompleted()).thenReturn(false);
        Map<String, InternalTask> activeTasks =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasks);
        activeTasks.put("dummyTaskId", dummyTask);
        taskService.checkCanStartManualTask(CommandImpossibleToRun.class, statusHolder, Map.of());
        assertTrue(statusHolder.isError());
        assertEquals("Another manual task " + CommandImpossibleToRun.class.getName() + " is still active!",
                statusHolder.getLastError().getMessage());
    }

    @Test
    public void checkCanStartManualTaskStoppingOtherTask() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        InternalTask dummyTask = mock(InternalTask.class);
        doReturn(DummyQuartzCommandA.class).when(dummyTask).getType();
        when(dummyTask.processActiveOnCluster()).thenReturn(true);

        InternalTask dummyTask2 = mock(InternalTask.class);
        doReturn(DummyQuartzCommandB.class).when(dummyTask2).getType();
        when(dummyTask2.processActiveOnCluster()).thenReturn(true);

        Map<String, InternalTask> activeTasks =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasks);
        activeTasks.put("dummyTaskId", dummyTask);
        activeTasks.put("dummyTaskId2", dummyTask2);
        taskService.checkCanStartManualTask(CommandStoppingOthers.class, statusHolder, Map.of());
        assertTrue(statusHolder.isWarning());

        List<StatusEntry> warnings = statusHolder.getWarnings();
        assertTrue(warnings.stream().anyMatch(warning -> warning.getMessage()
                .equals("Task " + DummyQuartzCommandA.class.getName() + " will be stopped by running " +
                        CommandStoppingOthers.class.getName() + " !")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.getMessage()
                .equals("Task " + DummyQuartzCommandB.class.getName() + " will be paused by running " +
                        CommandStoppingOthers.class.getName() + " !")));
    }

    @Test
    public void checkCanStartManualTaskByJobWithIdenticalAttributeReturnConflict() {
        QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
        ConflictGuard conflictGuard = mock(ConflictGuard.class);
        ReflectionTestUtils.setField(taskService, "conflictsGuard", conflictsGuard);

        String taskLockKey = TaskUtils.getTaskLockKeyIncludingKeyAttributes(JobName.DUMMY.asText(),
                Map.of(DummyQuartzCommandC.TEST_KEY, "testAttValue"), List.of(DummyQuartzCommandC.TEST_KEY));
        when(conflictsGuard.getLock(taskLockKey)).thenReturn(conflictGuard);
        when(conflictGuard.isLocked()).thenReturn(true);

        MutableStatusHolder statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(DummyQuartzCommandC.class, statusHolder,
                Map.of(DummyQuartzCommandC.TEST_KEY, "testAttValue"));

        assertTrue(statusHolder.isError());
        assertEquals(HttpStatus.SC_CONFLICT, statusHolder.getStatusCode());
        assertEquals("Cannot start task type (class org.artifactory.schedule.DummyQuartzCommandC) manually! The task " +
                "lock-key (dummy_testAttValue) is already locked.", statusHolder.getLastError().getMessage());
    }

    @Test
    public void checkCanStartManualTaskByJobWithDifferentAttributeReturnOK() {
        QuartzConflictsGuard conflictsGuard = mock(QuartzConflictsGuard.class);
        ConflictGuard conflictGuard1 = mock(ConflictGuard.class);
        ConflictGuard conflictGuard2 = mock(ConflictGuard.class);
        ReflectionTestUtils.setField(taskService, "conflictsGuard", conflictsGuard);

        String taskLockKey1 = TaskUtils.getTaskLockKeyIncludingKeyAttributes(JobName.DUMMY.asText(),
                Map.of(DummyQuartzCommandC.TEST_KEY, "testAttValue1"), List.of(DummyQuartzCommandC.TEST_KEY));
        when(conflictsGuard.getLock(taskLockKey1)).thenReturn(conflictGuard1);
        when(conflictGuard1.isLocked()).thenReturn(true);

        String taskLockKey2 = TaskUtils.getTaskLockKeyIncludingKeyAttributes(JobName.DUMMY.asText(),
                Map.of(DummyQuartzCommandC.TEST_KEY, "testAttValue2"), List.of(DummyQuartzCommandC.TEST_KEY));

        when(conflictsGuard.getLock(taskLockKey2)).thenReturn(conflictGuard2);
        when(conflictGuard1.isLocked()).thenReturn(false);

        MutableStatusHolder statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(DummyQuartzCommandC.class, statusHolder,
                Map.of(DummyQuartzCommandC.TEST_KEY, "testAttValue2"));

        assertFalse(statusHolder.isError());
    }

    @Test
    void canBeShutdownFalseWhileQuartzTaskActive() {
        // T1 (R1): shutdown readiness stays false while an active user Quartz task is present
        InternalTask activeQuartzTask = mock(InternalTask.class);
        doReturn(DummyQuartzCommand.class).when(activeQuartzTask).getType();
        when(activeQuartzTask.processActive()).thenReturn(true);

        Map<String, InternalTask> activeTasks =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasks);
        activeTasks.put("activeQuartzTask", activeQuartzTask);

        assertInstanceOf(GracefulShutdownAware.class, taskService,
                "RED anchor: task service must become graceful-shutdown aware");
        assertFalse(((GracefulShutdownAware) taskService).canBeShutdown(),
                "An active user Quartz task must block graceful shutdown readiness");
    }

    @Test
    void canBeShutdownTrueWhenOnlyInternalMarkerTaskIsActive() {
        // T2 (R1, R2, R3): an active internal marker task alone must not block shutdown readiness
        // (the marker job is annotated @JobCommand(shutdownTransparent = true))
        InternalTask markerTask = mock(InternalTask.class);
        doReturn(QuartzTaskHistoryExpiredAndOrphanedMarkerJob.class).when(markerTask).getType();
        when(markerTask.processActive()).thenReturn(true);

        Map<String, InternalTask> activeTasks =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasks);
        activeTasks.put("markerTask", markerTask);

        assertInstanceOf(GracefulShutdownAware.class, taskService,
                "RED anchor: task service must become graceful-shutdown aware");
        assertTrue(((GracefulShutdownAware) taskService).canBeShutdown(),
                "Only internal marker tasks should not block graceful shutdown readiness");
    }

    @Test
    void canBeShutdownFalseWhenMarkerAndActiveUserQuartzTaskPresent() {
        // T2 (R1, R2, R3): the marker exclusion must not let an active user Quartz task through
        InternalTask markerTask = mock(InternalTask.class);
        doReturn(QuartzTaskHistoryExpiredAndOrphanedMarkerJob.class).when(markerTask).getType();
        when(markerTask.processActive()).thenReturn(true);
        InternalTask activeQuartzTask = mock(InternalTask.class);
        doReturn(DummyQuartzCommand.class).when(activeQuartzTask).getType();
        when(activeQuartzTask.processActive()).thenReturn(true);

        Map<String, InternalTask> activeTasks =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasks);
        activeTasks.put("markerTask", markerTask);
        activeTasks.put("activeQuartzTask", activeQuartzTask);

        assertFalse(((GracefulShutdownAware) taskService).canBeShutdown(),
                "An active user Quartz task must block shutdown even when an internal marker task is also present");
    }

    @Test
    void canBeShutdownTrueWhenActiveTaskIsNotQuartz() {
        // T3 (R1): only Quartz jobs gate shutdown; a non-Quartz TaskCallback must not block readiness
        InternalTask nonQuartzTask = mock(InternalTask.class);
        doReturn(DummyJob.class).when(nonQuartzTask).getType();
        when(nonQuartzTask.processActive()).thenReturn(true);

        Map<String, InternalTask> activeTasks =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasks);
        activeTasks.put("nonQuartzTask", nonQuartzTask);

        assertTrue(((GracefulShutdownAware) taskService).canBeShutdown(),
                "A non-Quartz active task must not block graceful shutdown readiness");
    }

    @Test
    void canBeShutdownTrueWhenQuartzTaskIsNotProcessActive() {
        // T4 (R1): a Quartz task that is not currently processing must not block readiness
        InternalTask idleQuartzTask = mock(InternalTask.class);
        doReturn(DummyQuartzCommand.class).when(idleQuartzTask).getType();
        when(idleQuartzTask.processActive()).thenReturn(false);

        Map<String, InternalTask> activeTasks =
                (Map<String, InternalTask>) ReflectionTestUtils.getField(taskService, TaskServiceImpl.class,
                        "activeTasksById");
        assertNotNull(activeTasks);
        activeTasks.put("idleQuartzTask", idleQuartzTask);

        assertTrue(((GracefulShutdownAware) taskService).canBeShutdown(),
                "A Quartz task that is not process-active must not block graceful shutdown readiness");
    }

    private static abstract class NoJobCommandAnnotation extends QuartzCommand {
    }

    @JobCommand(jobName = JobName.DUMMY, manualUser = TaskUser.INVALID)
    private static abstract class JobCommandManualUserInvalid extends QuartzCommand {
    }

    @JobCommand(jobName = JobName.DUMMY, manualUser = TaskUser.CURRENT, commandsToStop =
            {@StopCommand(command = DummyQuartzCommand.class, strategy = StopStrategy.IMPOSSIBLE),
                    @StopCommand(command = CommandImpossibleToRun.class, strategy = StopStrategy.IMPOSSIBLE)})
    private static class CommandImpossibleToRun extends QuartzCommand {
        @Override
        protected void onExecute(JobExecutionContext callbackContext) {

        }
    }

    @JobCommand(jobName = JobName.DUMMY, manualUser = TaskUser.CURRENT, clusterSingleton = false, commandsToStop =
            {@StopCommand(command = DummyQuartzCommandA.class, strategy = StopStrategy.STOP), @StopCommand(command =
                    DummyQuartzCommandB.class, strategy = StopStrategy.PAUSE)})
    private static abstract class CommandStoppingOthers extends QuartzCommand {

    }
}