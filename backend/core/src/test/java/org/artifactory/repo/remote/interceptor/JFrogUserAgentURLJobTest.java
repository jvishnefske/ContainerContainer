package org.artifactory.repo.remote.interceptor;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class JFrogUserAgentURLJobTest extends ArtifactoryHomeBoundJUnitTest {

    private final JFrogUserAgentURLJob jfrogUserAgentURLJob = new JFrogUserAgentURLJob();
    @Mock
    private JFrogUserAgentService jfrogUserAgentService;
    @Mock
    private JobExecutionContext jobExecutionContext;
    @Mock
    private ArtifactoryContext artifactoryContext;

    @BeforeEach
    public void setUp() {
        when(artifactoryContext.beanForType(JFrogUserAgentService.class)).thenReturn(jfrogUserAgentService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testOnExecuteSuccess() throws JobExecutionException {
        doNothing().when(jfrogUserAgentService).fetchJFrogUserAgentURL();

        jfrogUserAgentURLJob.onExecute(jobExecutionContext);

        verify(jfrogUserAgentService, times(1)).fetchJFrogUserAgentURL();
    }

    @Test
    void testOnExecuteFailure() {
        doThrow(new RuntimeException("Error fetching URL")).when(jfrogUserAgentService).fetchJFrogUserAgentURL();
        try {
            jfrogUserAgentURLJob.onExecute(jobExecutionContext);
        } catch (JobExecutionException e) {
            verify(jfrogUserAgentService, times(1)).fetchJFrogUserAgentURL();
        }
    }
}