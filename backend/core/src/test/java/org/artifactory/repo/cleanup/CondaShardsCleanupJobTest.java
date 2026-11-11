package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class CondaShardsCleanupJobTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private CondaShardsCleanupServiceImpl condaShardsCleanupService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(CondaShardsCleanupServiceImpl.class))
                .thenReturn(condaShardsCleanupService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void onExecuteShouldCallCleanWhenJobRuns() throws JobExecutionException {
        CondaShardsCleanupJob condaShardsCleanupJob = new CondaShardsCleanupJob();
        condaShardsCleanupJob.onExecute(null);
        Mockito.verify(condaShardsCleanupService, times(1)).clean();
    }
}
