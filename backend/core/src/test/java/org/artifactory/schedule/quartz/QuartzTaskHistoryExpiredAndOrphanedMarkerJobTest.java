package org.artifactory.schedule.quartz;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.fs.service.QuartzTaskHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuartzTaskHistoryExpiredAndOrphanedMarkerJobTest {

    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private QuartzTaskHistoryService quartzTaskHistoryService;
    private QuartzTaskHistoryExpiredAndOrphanedMarkerJob markerJob;

    private MockitoSession mockitoSession;

    @BeforeEach
    void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
        ArtifactoryContextThreadBinder.bind(context);
        markerJob = new QuartzTaskHistoryExpiredAndOrphanedMarkerJob();
    }

    @AfterEach
    void tearDown() {
        mockitoSession.finishMocking();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testContextNotBoundDoNothing() throws JobExecutionException {
        ArtifactoryContextThreadBinder.bind(null);
        markerJob.onExecute(mock(JobExecutionContext.class));
        verifyNoInteractions(quartzTaskHistoryService);
    }

    @Test
    void testNormalFlow() throws JobExecutionException {
        when(context.beanForType(QuartzTaskHistoryService.class)).thenReturn(quartzTaskHistoryService);
        markerJob.onExecute(mock(JobExecutionContext.class));
        InOrder inOrder = inOrder(quartzTaskHistoryService);
        inOrder.verify(quartzTaskHistoryService).markExpiredTasks();
        inOrder.verify(quartzTaskHistoryService).markPendingExpiredCheckTasks();
        inOrder.verify(quartzTaskHistoryService).markOrphanedTasks();
    }

    @Test
    void testExceptionThrownDoNothing() throws JobExecutionException {
        when(context.beanForType(QuartzTaskHistoryService.class)).thenThrow(new RuntimeException("Thrown by test"));
        markerJob.onExecute(mock(JobExecutionContext.class));
        verifyNoInteractions(quartzTaskHistoryService);
    }
}