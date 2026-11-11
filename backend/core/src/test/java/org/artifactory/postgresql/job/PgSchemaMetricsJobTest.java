package org.artifactory.postgresql.job;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.postgresql.service.PgSchemaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class PgSchemaMetricsJobTest {

    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    private PgSchemaService pgSchemaService;

    private PgSchemaMetricsJob job;

    @BeforeEach
    void setup() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(PgSchemaService.class)).thenReturn(pgSchemaService);
        job = new PgSchemaMetricsJob();
    }

    @AfterEach
    void teardown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void onExecute_shouldCallRefreshMetrics_whenJobExecutes() throws Exception {
        // Arrange
        JobExecutionContext context = mock(JobExecutionContext.class);

        // Act
        job.onExecute(context);

        // Assert
        verify(pgSchemaService).refreshMetrics();
    }

    @Test
    void onExecute_shouldPropagateException_whenRefreshMetricsFails() {
        // Arrange
        JobExecutionContext context = mock(JobExecutionContext.class);
        doThrow(new RuntimeException("Database error")).when(pgSchemaService).refreshMetrics();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> job.onExecute(context));
        verify(pgSchemaService).refreshMetrics();
    }
}
