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

/**
 * @author Natalie Berni
 */
class NixUploadsFolderCleanupJobTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private NixUploadsFolderCleanupServiceImpl nixUploadsFolderCleanupService;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(NixUploadsFolderCleanupServiceImpl.class))
                .thenReturn(nixUploadsFolderCleanupService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void onExecute_shouldCallClean_whenJobRuns() throws JobExecutionException {
        NixUploadsFolderCleanupJob nixUploadsFolderCleanupJob = new NixUploadsFolderCleanupJob();

        nixUploadsFolderCleanupJob.onExecute(null);

        Mockito.verify(nixUploadsFolderCleanupService, times(1)).clean();
    }
}

