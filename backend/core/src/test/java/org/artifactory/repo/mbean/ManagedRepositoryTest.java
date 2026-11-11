package org.artifactory.repo.mbean;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.storage.fs.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ManagedRepository}
 *
 * @author oleksandrl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManagedRepositoryTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private ArtifactoryHome home;
    @Mock
    private FileService fileService;

    @BeforeEach
    void setUp() {
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.bind(home);
        when(context.beanForType(FileService.class)).thenReturn(fileService);
        when(context.getArtifactoryHome()).thenReturn(home);
    }

    @AfterEach
    void tearDown() {
        try {
            ArtifactoryContextThreadBinder.unbind();
            ArtifactoryHome.unbind();
        } catch (Exception e) {
            // Ignore - may not be bound
        }
    }

    @Test
    void getRepositoryKey_shouldReturnRepoKey_whenCalled() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        // Act
        String result = managedRepository.getRepositoryKey();
        // Assert
        assertEquals("test-repo", result);
    }

    @Test
    void getArtifactsCount_shouldReturnCount_whenCalledFromJmxThread() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        when(fileService.getFilesCount(any(RepoPathImpl.class))).thenReturn(42);
        // Unbind to simulate JMX thread without context
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        // Act
        long result = managedRepository.getArtifactsCount();
        // Assert
        assertEquals(42L, result);
    }

    @Test
    void getArtifactsCount_shouldThrowException_whenFileServiceThrowsException() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        RuntimeException expectedException = new RuntimeException("Database error");
        when(fileService.getFilesCount(any(RepoPathImpl.class))).thenThrow(expectedException);
        // Unbind to simulate JMX thread without context
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        // Act & Assert
        RuntimeException thrownException = assertThrows(RuntimeException.class, managedRepository::getArtifactsCount);
        assertEquals(expectedException, thrownException);
    }

    @Test
    void getArtifactsTotalSize_shouldReturnSize_whenCalledFromJmxThread() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        when(fileService.getFilesTotalSize(any(RepoPathImpl.class))).thenReturn(1024000L);
        // Unbind to simulate JMX thread without context
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        // Act
        long result = managedRepository.getArtifactsTotalSize();
        // Assert
        assertEquals(1024000L, result);
    }

    @Test
    void getArtifactsTotalSize_shouldThrowException_whenFileServiceThrowsException() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        RuntimeException expectedException = new RuntimeException("Database error");
        when(fileService.getFilesTotalSize(any(RepoPathImpl.class))).thenThrow(expectedException);
        // Unbind to simulate JMX thread without context
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        // Act & Assert
        RuntimeException thrownException = assertThrows(RuntimeException.class,
                managedRepository::getArtifactsTotalSize);
        assertEquals(expectedException, thrownException);
    }

    @Test
    void getArtifactsCount_shouldWorkCorrectly_whenContextIsAlreadyBound() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        when(fileService.getFilesCount(any(RepoPathImpl.class))).thenReturn(100);
        // Context is already bound from setUp - this simulates normal Artifactory thread
        // Act
        long result = managedRepository.getArtifactsCount();
        // Assert
        assertEquals(100L, result);
    }

    @Test
    void getArtifactsTotalSize_shouldWorkCorrectly_whenContextIsAlreadyBound() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        when(fileService.getFilesTotalSize(any(RepoPathImpl.class))).thenReturn(2048000L);
        // Context is already bound from setUp - this simulates normal Artifactory thread
        // Act
        long result = managedRepository.getArtifactsTotalSize();
        // Assert
        assertEquals(2048000L, result);
    }

    @Test
    void getArtifactsCount_shouldUnbindContext_afterExecution() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        when(fileService.getFilesCount(any(RepoPathImpl.class))).thenReturn(99);
        // Unbind to simulate JMX thread
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        // Act
        managedRepository.getArtifactsCount();
        // Assert - verify context is unbound after execution
        assertThrows(IllegalStateException.class, ArtifactoryHome::get,
                "Context should be unbound after MBean method execution");
    }

    @Test
    void getArtifactsTotalSize_shouldUnbindContext_afterExecution() {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository("test-repo");
        when(fileService.getFilesTotalSize(any(RepoPathImpl.class))).thenReturn(999L);
        // Unbind to simulate JMX thread
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        // Act
        managedRepository.getArtifactsTotalSize();
        // Assert - verify context is unbound after execution
        assertThrows(IllegalStateException.class, ArtifactoryHome::get,
                "Context should be unbound after MBean method execution");
    }
}
