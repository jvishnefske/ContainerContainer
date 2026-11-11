package org.artifactory.schedule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.event.EventHintType;
import org.artifactory.spring.InternalArtifactoryContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;

import java.util.List;

import static org.artifactory.schedule.DummyJob.TASKTOKEN;
import static org.artifactory.schedule.quartz.PluginCommand.ACTION_CLOSURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class QuartzCommandTest {

    private InternalArtifactoryContext context;
    @Mock
    private InternalTask activeTask;
    @Mock
    private TaskService taskService;

    private JobExecutionContext jobContext;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(taskService.getActiveTask(TASKTOKEN, true)).thenReturn(activeTask);
        jobContext = mock(JobExecutionContext.class, RETURNS_DEEP_STUBS);
        context = mock(InternalArtifactoryContext.class, RETURNS_DEEP_STUBS);
        when(jobContext.getMergedJobDataMap().getString(Task.TASK_TOKEN)).thenReturn(TASKTOKEN);
        when(jobContext.getMergedJobDataMap().get(Task.JOB_NAME)).thenReturn(JobName.HEARTBEAT);
        when(context.getTaskService()).thenReturn(taskService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testBeforeExecuteFailsTaskNotRunning() throws JobExecutionException {
        when(activeTask.started()).thenThrow(new RuntimeException("Exception by test"));
        createQuartzCommand().execute(jobContext);
        verify(activeTask).completed();
    }

    @Test
    void testBeforeExecuteFalseTaskSkipped() throws JobExecutionException {
        when(activeTask.started()).thenReturn(false);
        createQuartzCommand().execute(jobContext);

        verify(activeTask).skip("Task execution skipped. View log for more details.");
        verify(activeTask).completed();
    }

    @Test
    void testExecuteFails() throws JobExecutionException {
        when(context.isReady()).thenReturn(true);
        when(activeTask.canStart()).thenReturn(true);
        doThrow(new IllegalStateException("Thrown by test")).when(activeTask).start(any());
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(haCommonAddon.isHaEnabled()).thenReturn(false);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> createQuartzCommand().execute(jobContext));

        verify(activeTask).fail("Thrown by test");
        verify(activeTask).completed();
        assertEquals("Thrown by test", exception.getMessage());
    }

    @Test
    void testRunCommandPlugin() throws JobExecutionException {
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(QuartzCommand.class, Level.DEBUG);
        when(jobContext.getMergedJobDataMap().get(ACTION_CLOSURE)).thenReturn("action");
        QuartzTask quartzTask = new QuartzTask(HaQuartzTaskTest.Job1.class, mock(Trigger.class), mock(JobDetail.class));
        new DummyQuartzCommand(quartzTask).execute(jobContext);
        List<ILoggingEvent> loggedEvents = appender.getLoggedEvents();
        assertEquals(2, loggedEvents.size());
        assertEquals("Executing task {} with token {}. replicationAllowed", loggedEvents.get(0).getMessage());
        assertEquals("Finished execution of task {} with token {}. replicationAllowed",
                loggedEvents.get(1).getMessage());
    }

    @Test
    void testRunCommand() throws JobExecutionException {
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(QuartzCommand.class, Level.DEBUG);
        QuartzTask quartzTask = new QuartzTask(DummyQuartzCommand.class, mock(Trigger.class), mock(JobDetail.class));
        new DummyQuartzCommand(quartzTask).execute(jobContext);
        List<ILoggingEvent> loggedEvents = appender.getLoggedEvents();
        assertEquals(2, loggedEvents.size());
        assertEquals("Executing task {} with token {}.", loggedEvents.get(0).getMessage());
        assertEquals("Finished execution of task {} with token {}.",
                loggedEvents.get(1).getMessage());
    }

    @Test
    void testExecuteClearsLeftoverEventLogHints() throws JobExecutionException {
        // Quartz jobs run on pooled threads; a job that leaves event-log hints behind (without its own cleanup)
        // must not leak them onto the thread. execute()'s outer finally is the safety net for ALL jobs.
        QuartzTask quartzTask = new QuartzTask(DummyQuartzCommand.class, mock(Trigger.class), mock(JobDetail.class));
        QuartzCommand command = new DummyQuartzCommand(quartzTask) {
            @Override
            protected void onExecute(JobExecutionContext callbackContext) {
                EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, "leaked");
                assertNotNull(EventLogHintManager.getHints(), "hint set during job execution");
            }
        };

        assertNull(EventLogHintManager.getHints(), "no hints before execution");
        command.execute(jobContext);
        assertNull(EventLogHintManager.getHints(), "hints cleared by execute()'s outer finally");
    }

    @Nonnull
    private static QuartzCommand createQuartzCommand() {
        return new QuartzCommand() {
            @Override
            protected void onExecute(JobExecutionContext callbackContext) {

            }
        };
    }

    private static class DummyQuartzCommand extends QuartzCommand {

        public DummyQuartzCommand(InternalTask task) {
            activeTask = task;
        }

        @Override
        protected void onExecute(JobExecutionContext callbackContext) {

        }

        @Override
        protected boolean beforeExecute(JobExecutionContext callbackContext) {
            return true;
        }

        @Override
        protected void afterExecute() {

        }
    }
}