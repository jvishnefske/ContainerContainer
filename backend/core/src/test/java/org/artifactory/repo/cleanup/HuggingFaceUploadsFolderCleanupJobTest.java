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
 * @author Volodymyr Shulga
 */
class HuggingFaceUploadsFolderCleanupJobTest {
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private HuggingFaceUploadsFolderCleanupServiceImpl huggingFaceUploadsFolderCleanupService;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(HuggingFaceUploadsFolderCleanupServiceImpl.class))
                .thenReturn(huggingFaceUploadsFolderCleanupService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void onExecute() throws JobExecutionException {
        HuggingFaceUploadsFolderCleanupJob huggingFaceUploadsFolderCleanupJob =
                new HuggingFaceUploadsFolderCleanupJob();
        huggingFaceUploadsFolderCleanupJob.onExecute(null);
        Mockito.verify(huggingFaceUploadsFolderCleanupService, times(1)).clean();
    }
}