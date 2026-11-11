package org.artifactory.repo.interceptor.projects;

import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.exception.AccessClientStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RemoveRepoFromProjectStorageInterceptorTest {

    @Mock
    private ProjectsService projectsService;
    @InjectMocks
    private RemoveRepoFromProjectStorageInterceptor interceptor;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testAfterRepositoryContentDelete_Successful() {
        String repoKey = "test-repo";
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);

        interceptor.afterRepositoryContentDelete(repoKey, statusHolder);
        verify(projectsService).removeRepositoryFromCurrentProject(repoKey);
        verify(statusHolder, never()).warn(anyString(), any(Exception.class), any());
    }

    @Test
    void testAfterRepositoryContentDelete_AccessClientStateException() {
        String repoKey = "test-repo";
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);

        doThrow(new AccessClientStateException("Access error")).when(projectsService)
                .removeRepositoryFromCurrentProject(repoKey);
        assertThrows(AccessClientStateException.class,
                () -> interceptor.afterRepositoryContentDelete(repoKey, statusHolder));
        verify(statusHolder).warn(contains("Failed to remove repo"), any(AccessClientStateException.class), any());
    }

    @Test
    void testAfterRepositoryContentDelete_GenericException() {
        String repoKey = "test-repo";
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);

        doThrow(new RuntimeException("Generic error")).when(projectsService)
                .removeRepositoryFromCurrentProject(repoKey);
        assertThrows(RuntimeException.class, () -> interceptor.afterRepositoryContentDelete(repoKey, statusHolder));
        verify(statusHolder).warn(contains("Failed to remove repo"), any(RuntimeException.class), any());
    }
}