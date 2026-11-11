package org.artifactory.schedule;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.quartz.JobExecutionContext;

import static org.artifactory.schedule.DummyJob.TASKTOKEN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
@SuppressWarnings("unchecked")
class TaskCallbackTest {

    private InternalArtifactoryContext context;
    private InternalTask activeTask;
    private TaskService taskService;

    @BeforeEach
    void setup() {
        activeTask = mock(InternalTask.class);
        context = mock(InternalArtifactoryContext.class);
        taskService = mock(TaskService.class);
        when(taskService.getActiveTask(TASKTOKEN,true)).thenReturn(activeTask);
        when(context.getTaskService()).thenReturn(taskService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testBeforeExecuteNoContext() {
        JobExecutionContext jobContext = mock(JobExecutionContext.class);
        DummyJob dummyJob = new DummyJob();
        assertFalse(dummyJob.beforeExecute(jobContext));
    }

    @Test
    void testBeforeExecuteContextNotReady() {
        when(context.isReady()).thenReturn(false);
        JobExecutionContext jobContext = mock(JobExecutionContext.class);
        DummyJob dummyJob = new DummyJob();
        assertFalse(dummyJob.beforeExecute(jobContext));

        when(activeTask.isSingleExecution()).thenReturn(true);
        assertFalse(dummyJob.beforeExecute(jobContext));
        verify(activeTask).setShouldRescheduleTask(true);
    }

    @Test
    void testBeforeExecuteContextReady() {
        when(context.isReady()).thenReturn(true);
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(haCommonAddon.isHaEnabled()).thenReturn(false);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        JobExecutionContext jobContext = mock(JobExecutionContext.class);
        DummyJob dummyJob = new DummyJob();
        when(activeTask.canStart()).thenReturn(true);
        assertTrue(dummyJob.beforeExecute(jobContext));
    }

    @Test
    void testAfterExecuteWithReschedule() {
        DummyJob dummyJob = new DummyJob();
        JobExecutionContext jobContext = mock(JobExecutionContext.class);
        assertFalse(dummyJob.beforeExecute(jobContext));
        when(activeTask.shouldRescheduleTask()).thenReturn(true);
        dummyJob.afterExecute();
        verify(taskService).rescheduleTask(TASKTOKEN);
        verify(taskService, times(0)).cancelTask(TASKTOKEN, true);
        verify(activeTask, times(0)).completed();
    }

    @Test
    void testAfterExecuteNoReschedule() {
        DummyJob dummyJob = new DummyJob();
        JobExecutionContext jobContext = mock(JobExecutionContext.class);
        assertFalse(dummyJob.beforeExecute(jobContext));
        when(activeTask.shouldRescheduleTask()).thenReturn(false);
        when(activeTask.isSingleExecution()).thenReturn(true);
        dummyJob.afterExecute();
        verify(taskService, times(0)).rescheduleTask(TASKTOKEN);
        verify(taskService).cancelTask(TASKTOKEN, true);
        verify(activeTask).completed();
    }

    @Test
    void contextNotReadyShouldRescheduleManualJob() {
        when(context.isReady()).thenReturn(false);
        try (MockedStatic<StorageContextHelper> storageContextHelperMockedStatic = mockStatic(
                StorageContextHelper.class)) {
            ArtifactoryStorageContext storageContext = mock(ArtifactoryStorageContext.class);
            when(storageContext.isReady()).thenReturn(false);
            storageContextHelperMockedStatic.when(StorageContextHelper::get).thenReturn(storageContext);
            when(activeTask.isSingleExecution()).thenReturn(true);
            when(taskService.getActiveTask("token", true)).thenReturn(activeTask);

            DummyJob dummyJob = new DummyJob();
            assertFalse(dummyJob.beforeExecute(mock(JobExecutionContext.class)));

            verify(activeTask).setShouldRescheduleTask(true);

            when(activeTask.shouldRescheduleTask()).thenReturn(true);

            dummyJob.afterExecute();

            verify(taskService).rescheduleTask("token");
        }
    }
}