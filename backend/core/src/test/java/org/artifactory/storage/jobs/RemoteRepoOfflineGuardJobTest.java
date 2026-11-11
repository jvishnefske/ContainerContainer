package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class RemoteRepoOfflineGuardJobTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private RemoteRepoOfflineGuardService offlineGuardService;

    private RemoteRepoOfflineGuardJob job;

    @BeforeEach
    void setUp() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(RemoteRepoOfflineGuardService.class)).thenReturn(offlineGuardService);
        job = new RemoteRepoOfflineGuardJob();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void onExecute_delegatesToService() throws Exception {
        job.onExecute(mock(JobExecutionContext.class));

        verify(offlineGuardService).evaluate();
    }

    @Test
    void onExecute_wrapsExceptionAsJobExecutionException() {
        doThrow(new RuntimeException("boom")).when(offlineGuardService).evaluate();

        assertThrows(JobExecutionException.class, () -> job.onExecute(mock(JobExecutionContext.class)));
    }
}
