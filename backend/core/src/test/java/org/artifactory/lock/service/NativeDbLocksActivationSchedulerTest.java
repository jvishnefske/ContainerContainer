package org.artifactory.lock.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.artifactory.lock.job.NativeDbLocksActivationJob;
import org.artifactory.lock.job.NativeDbLocksActivationJobScheduler;
import org.artifactory.schedule.SingletonTaskAlreadyScheduledException;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.AolUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeDbLocksActivationSchedulerTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private NativeDbLocksActivationService statusService;
    @Mock
    private TaskService taskService;

    private NativeDbLocksActivationJobScheduler activationService;
    private AutoCloseable mocks;
    private MockedStatic<AolUtils> aolUtils;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        aolUtils = Mockito.mockStatic(AolUtils.class);
        aolUtils.when(AolUtils::isAol).thenReturn(true);
        activationService = new NativeDbLocksActivationJobScheduler(statusService, taskService);
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        setStringSystemProperty(ConstantValues.dbLockingMechanism, DbLockingMechanism.AUTO.getName());
    }

    @AfterEach
    void tearDown() throws Exception {
        aolUtils.close();
        mocks.close();
    }

    @Test
    void onContextReadySchedulesActivationJobWhenPending() {
        when(taskService.hasActiveTasks(any())).thenReturn(false);

        try (MockedStatic<TaskUtils> taskUtilsMock = Mockito.mockStatic(TaskUtils.class)) {
            Task mockTask = mock(Task.class);
            taskUtilsMock.when(() -> TaskUtils.createRepeatingTask(
                            eq(NativeDbLocksActivationJob.class), anyLong(), anyLong()))
                    .thenReturn(mockTask);

            activationService.onContextReady();

            taskUtilsMock.verify(() -> TaskUtils.createRepeatingTask(
                    eq(NativeDbLocksActivationJob.class), anyLong(), anyLong()));
            verify(taskService).startTask(mockTask, false);
        }
    }

    @Test
    void onContextReadyDoesNotScheduleWhenAlreadyActive() {
        when(taskService.hasActiveTasks(any())).thenReturn(true);

        activationService.onContextReady();

        verify(taskService, never()).startTask(any(), eq(false));
    }

    @Test
    void onContextReadyDoesNotScheduleWhenActivationNotNeeded() {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.IN_USE);

        activationService.onContextReady();

        verify(taskService, never()).startTask(any(), eq(false));
    }

    @Test
    void onContextReadyIgnoresDuplicateScheduleAttempts() {
        when(taskService.hasActiveTasks(any())).thenReturn(false);
        doThrow(new SingletonTaskAlreadyScheduledException(null)).when(taskService).startTask(any(), eq(false));

        withMockRepeatingTask(() -> activationService.onContextReady());

        verify(taskService).startTask(any(), eq(false));
    }

    @Test
    void onContextReadyDoesNotScheduleWhenSupportedNotReady() {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY);

        activationService.onContextReady();

        verify(taskService, never()).startTask(any(), eq(false));
    }

    @Test
    void onContextReadyDoesNotScheduleWhenNotSupported() {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.NOT_SUPPORTED);

        activationService.onContextReady();

        verify(taskService, never()).startTask(any(), eq(false));
    }

    @Test
    void onContextReadyDoesNotScheduleOnSelfHosted() {
        aolUtils.when(AolUtils::isAol).thenReturn(false);

        activationService.onContextReady();

        verify(taskService, never()).startTask(any(), eq(false));
        verify(statusService, never()).resolveLocalStatus();
    }

    @Test
    void onContextReadyDoesNotScheduleWhenJobDisabled() {
        setStringSystemProperty(ConstantValues.dbLockNativeActivationJobEnabled, "false");

        activationService.onContextReady();

        verify(taskService, never()).startTask(any(), eq(false));
        verify(statusService, never()).resolveLocalStatus();
    }

    @Test
    void onContextReadySwallowsUnexpectedScheduleFailures() {
        when(taskService.hasActiveTasks(any())).thenReturn(false);
        doThrow(new RuntimeException("task service down")).when(taskService).startTask(any(), eq(false));

        withMockRepeatingTask(() -> activationService.onContextReady());

        verify(taskService).startTask(any(), eq(false));
    }

    @Test
    void onContextReadyUsesConfiguredIntervalAndInitialDelay() {
        setStringSystemProperty(ConstantValues.dbLockNativeActivationJobIntervalSec, "120");
        setStringSystemProperty(ConstantValues.dbLockNativeActivationJobInitialDelaySec, "45");
        when(taskService.hasActiveTasks(any())).thenReturn(false);

        try (MockedStatic<TaskUtils> taskUtilsMock = Mockito.mockStatic(TaskUtils.class)) {
            Task mockTask = mock(Task.class);
            taskUtilsMock.when(() -> TaskUtils.createRepeatingTask(
                            eq(NativeDbLocksActivationJob.class),
                            eq(TimeUnit.SECONDS.toMillis(120)),
                            eq(TimeUnit.SECONDS.toMillis(45))))
                    .thenReturn(mockTask);

            activationService.onContextReady();

            taskUtilsMock.verify(() -> TaskUtils.createRepeatingTask(
                    NativeDbLocksActivationJob.class,
                    TimeUnit.SECONDS.toMillis(120),
                    TimeUnit.SECONDS.toMillis(45)));
            verify(taskService).startTask(mockTask, false);
        }
    }

    @Test
    void onContextReadyUsesDefaultIntervalAndInitialDelayWhenNotConfigured() {
        when(taskService.hasActiveTasks(any())).thenReturn(false);

        try (MockedStatic<TaskUtils> taskUtilsMock = Mockito.mockStatic(TaskUtils.class)) {
            Task mockTask = mock(Task.class);
            long defaultDelayMillis = TimeUnit.SECONDS.toMillis(
                    ConstantValues.dbLockNativeActivationJobInitialDelaySec.getLong());
            long defaultIntervalMillis = TimeUnit.SECONDS.toMillis(
                    ConstantValues.dbLockNativeActivationJobIntervalSec.getLong());
            taskUtilsMock.when(() -> TaskUtils.createRepeatingTask(
                            eq(NativeDbLocksActivationJob.class),
                            eq(defaultIntervalMillis),
                            eq(defaultDelayMillis)))
                    .thenReturn(mockTask);

            activationService.onContextReady();

            taskUtilsMock.verify(() -> TaskUtils.createRepeatingTask(
                    NativeDbLocksActivationJob.class, defaultIntervalMillis, defaultDelayMillis));
        }
    }

    private void withMockRepeatingTask(Runnable runnable) {
        try (MockedStatic<TaskUtils> taskUtilsMock = Mockito.mockStatic(TaskUtils.class)) {
            Task mockTask = mock(Task.class);
            taskUtilsMock.when(() -> TaskUtils.createRepeatingTask(any(), anyLong(), anyLong()))
                    .thenReturn(mockTask);
            runnable.run();
        }
    }
}
