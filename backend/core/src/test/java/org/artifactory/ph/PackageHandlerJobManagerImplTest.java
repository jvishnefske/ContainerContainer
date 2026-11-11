package org.artifactory.ph;

import org.artifactory.schedule.TaskService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;


class PackageHandlerJobManagerImplTest extends ArtifactoryHomeBoundJUnitTest {

    private PackageHandlerJobManager packageHandlerJobManager;
    @Mock
    private TaskService taskService;
    private AutoCloseable mocksClosable;

    @BeforeEach
    void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);
        packageHandlerJobManager = Mockito.spy(new PackageHandlerJobManagerImpl(taskService));
        getBound();
    }

    @Test
    void name() {
        packageHandlerJobManager.onContextCreated();
        Mockito.verify(taskService).startTask(ArgumentMatchers.any(), ArgumentMatchers.eq(false));
        Mockito.verifyNoMoreInteractions(taskService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocksClosable.close();
        unbindArtifactoryHome();
    }
}