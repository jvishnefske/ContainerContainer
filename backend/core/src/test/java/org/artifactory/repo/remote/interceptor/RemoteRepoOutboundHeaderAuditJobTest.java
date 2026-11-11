package org.artifactory.repo.remote.interceptor;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteRepoOutboundHeaderAuditJobTest {

    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    private RemoteRepoOutboundHeaderAuditService remoteRepoOutboundHeaderAuditService;

    private RemoteRepoOutboundHeaderAuditJob job;

    @BeforeEach
    void setup() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(RemoteRepoOutboundHeaderAuditService.class))
                .thenReturn(remoteRepoOutboundHeaderAuditService);
        job = new RemoteRepoOutboundHeaderAuditJob();
    }

    @AfterEach
    void teardown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void onExecute_shouldDelegateToService() throws Exception {
        JobExecutionContext context = mock(JobExecutionContext.class);

        job.onExecute(context);

        verify(remoteRepoOutboundHeaderAuditService).flush();
    }

    @Test
    void onExecute_shouldSwallowExceptionsFromFlush() throws Exception {
        JobExecutionContext context = mock(JobExecutionContext.class);
        doThrow(new RuntimeException("flush failed"))
                .when(remoteRepoOutboundHeaderAuditService).flush();

        // The job logs and absorbs exceptions so a single flush failure does not abort the Quartz scheduler.
        job.onExecute(context);

        verify(remoteRepoOutboundHeaderAuditService).flush();
    }
}
