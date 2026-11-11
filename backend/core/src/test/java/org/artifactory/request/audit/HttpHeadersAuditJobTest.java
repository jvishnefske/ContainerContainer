package org.artifactory.request.audit;

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
class HttpHeadersAuditJobTest {

    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    private HttpHeadersAuditService httpHeadersAuditService;

    private HttpHeadersAuditJob job;

    @BeforeEach
    void setup() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(HttpHeadersAuditService.class)).thenReturn(httpHeadersAuditService);
        job = new HttpHeadersAuditJob();
    }

    @AfterEach
    void teardown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void onExecute_shouldDelegateToService() throws Exception {
        JobExecutionContext context = mock(JobExecutionContext.class);

        job.onExecute(context);

        verify(httpHeadersAuditService).flush();
    }

    @Test
    void onExecute_shouldSwallowExceptionsFromFlush() throws Exception {
        JobExecutionContext context = mock(JobExecutionContext.class);
        doThrow(new RuntimeException("flush failed")).when(httpHeadersAuditService).flush();

        // The job logs and absorbs exceptions so a single flush failure does not abort the Quartz scheduler.
        job.onExecute(context);

        verify(httpHeadersAuditService).flush();
    }
}
