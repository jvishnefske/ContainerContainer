package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class FolderPruningJobTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private InternalFolderPruningService folderPruningService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(InternalFolderPruningService.class)).thenReturn(folderPruningService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testDoExecute() throws JobExecutionException {
        FolderPruningJob folderPruningJob = new FolderPruningJob();
        folderPruningJob.onExecute(mock(JobExecutionContext.class));
        verify(folderPruningService, times(1)).doPrune();
        verifyNoMoreInteractions(folderPruningService);
    }
}
