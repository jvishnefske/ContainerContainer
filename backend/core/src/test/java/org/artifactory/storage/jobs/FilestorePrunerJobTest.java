package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.Task;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.jfrog.storage.common.prune.PruneRequestModel;
import org.jfrog.storage.common.prune.PruneStatusReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.quartz.JobExecutionException;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class FilestorePrunerJobTest {
    private FilestorePrunerJob x;
    @BeforeEach
    void setUp() {
        x = spy(FilestorePrunerJob.class);
    }

    @AfterEach
    void tearDown() {
    }

    @ParameterizedTest(name = "with throw Exception: {arguments}")
    @ValueSource(booleans = {true, false})
    void onExecute(boolean throwException) {
        try (MockedStatic<ContextHelper> contextHelper = Mockito.mockStatic(ContextHelper.class)) {

            ArtifactoryBinaryService binaryStore = Mockito.mock(ArtifactoryBinaryService.class);

            ArtifactoryContext artifactoryContext = Mockito.mock(ArtifactoryContext.class);
            when(artifactoryContext.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryStore);

            contextHelper.when(ContextHelper::get).thenReturn(artifactoryContext);

            Task task = Mockito.mock(Task.class);
            doReturn(task).when(x).getActiveTask();

            PruneRequestModel pruneRequestModel = PruneRequestModel.EMPTY;
            when(task.getAttribute(anyString())).thenReturn(pruneRequestModel);

            if(throwException) {
                doThrow(new RuntimeException("TEST")).when(binaryStore).prune(pruneRequestModel);
            } else {
                when(binaryStore.prune(pruneRequestModel)).thenReturn(new PruneStatusReport());
            }
            x.onExecute(null);
        } catch (JobExecutionException e) {
            fail("No exception should be thrown", e);
        }
    }
}