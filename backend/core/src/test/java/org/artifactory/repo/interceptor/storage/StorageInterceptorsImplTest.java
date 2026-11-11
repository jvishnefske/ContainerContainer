package org.artifactory.repo.interceptor.storage;

import ch.qos.logback.classic.Level;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.StorageInterceptor;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class StorageInterceptorsImplTest {

    @Mock
    private StorageInterceptor storageInterceptor1;
    @Mock
    private StorageInterceptor storageInterceptor2;
    @Mock
    private MutableStatusHolder statusHolder;
    @Mock
    private RepoPath repoPath;
    @Mock
    private VfsItem vfsItem;
    @Mock
    private DeleteContext deleteContext;
    private AutoCloseable mocks;


    private StorageInterceptorsImpl storageInterceptors;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        storageInterceptors = new StorageInterceptorsImpl();
        List<StorageInterceptor> interceptors = List.of(storageInterceptor1, storageInterceptor2);
        ReflectionTestUtils.setField(storageInterceptors, "interceptors", interceptors);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @CsvSource({
            "auto-trashcan, false, true, 0",
            "auto-trashcan, true, true, 0",
            "repo, false, false, 1",
            "repo, true, true, 1",
    })
    void canDelete(String repoKey, boolean interceptor1CanDelete, boolean expected, int interceptor1Times) {
        when(repoPath.getRepoKey()).thenReturn(repoKey);
        when(storageInterceptor1.canDelete(eq(repoPath), eq(statusHolder), any(DeleteContext.class)))
                .thenReturn(interceptor1CanDelete);
        when(storageInterceptor2.canDelete(eq(repoPath), eq(statusHolder), any(DeleteContext.class)))
                .thenReturn(true);

        boolean actual = storageInterceptors.canDelete(repoPath, statusHolder);
        assertEquals(expected, actual);
        verifyNoMoreInteractions(statusHolder);
        verify(storageInterceptor1, times(interceptor1Times)).canDelete(
                eq(repoPath),
                eq(statusHolder),
                any(DeleteContext.class));
        if (interceptor1Times == 1) {
            int interceptor2Times = interceptor1CanDelete ? 1 : 0;
            verify(storageInterceptor2, times(interceptor2Times)).canDelete(
                    eq(repoPath),
                    eq(statusHolder),
                    any(DeleteContext.class));
        }
    }

    @Test
    void canDelete_forwardsDeleteContext() {
        when(repoPath.getRepoKey()).thenReturn("repo");
        when(storageInterceptor1.canDelete(repoPath, statusHolder, deleteContext)).thenReturn(true);
        when(storageInterceptor2.canDelete(repoPath, statusHolder, deleteContext)).thenReturn(true);

        boolean actual = storageInterceptors.canDelete(repoPath, statusHolder, deleteContext);

        assertTrue(actual);
        verify(storageInterceptor1).canDelete(repoPath, statusHolder, deleteContext);
        verify(storageInterceptor2).canDelete(repoPath, statusHolder, deleteContext);
    }

    @Test
    void storageInterceptorDefaultCanDeleteWithContextDelegatesToLegacyMethod() {
        StorageInterceptor interceptor = Mockito.mock(StorageInterceptor.class, Mockito.CALLS_REAL_METHODS);
        when(interceptor.canDelete(repoPath, statusHolder)).thenReturn(false);

        assertFalse(interceptor.canDelete(repoPath, statusHolder, deleteContext));
    }

    @Test
    void storageInterceptorAdapterCanDeleteWithContextDelegatesToLegacyMethod() {
        StorageInterceptorAdapter interceptor = new StorageInterceptorAdapter() {
            @Override
            public boolean canDelete(RepoPath repoPath, MutableStatusHolder statusHolder) {
                return false;
            }
        };

        assertFalse(interceptor.canDelete(repoPath, statusHolder, deleteContext));
    }

    @Test
    void beforeDeleteCallsAllInterceptors() {
        when(vfsItem.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("repo");
        doNothing().when(storageInterceptor1).beforeDelete(vfsItem, statusHolder,deleteContext);
        doNothing().when(storageInterceptor2).beforeDelete(vfsItem, statusHolder,deleteContext);

        storageInterceptors.beforeDelete(vfsItem, statusHolder,deleteContext);

        verify(storageInterceptor1, times(1)).beforeDelete(vfsItem, statusHolder,deleteContext);
        verify(storageInterceptor2, times(1)).beforeDelete(vfsItem, statusHolder,deleteContext);
        verifyNoMoreInteractions(storageInterceptor1, storageInterceptor2);
    }

    @Test
    void beforeDeleteSkipsInterceptorsForTrashRepo() {
        when(vfsItem.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("auto-trashcan");
        storageInterceptors.beforeDelete(vfsItem, statusHolder, deleteContext);

        verifyNoInteractions(storageInterceptor1, storageInterceptor2);
    }

    @Test
    void beforeDeleteWithCancelException() {
        when(vfsItem.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("repo");
        CancelException exception = new CancelException("Operation cancelled", 500);
        doThrow(exception).when(storageInterceptor1).beforeDelete(vfsItem, statusHolder, deleteContext);

        storageInterceptors.beforeDelete(vfsItem, statusHolder, deleteContext);
        verify(storageInterceptor1, times(1)).beforeDelete(vfsItem, statusHolder, deleteContext);
        verifyNoInteractions(storageInterceptor2);
        verify(statusHolder, times(1))
                .errorWithDifferentLogLevel("Delete rejected: " + exception.getMessage(),
                        exception.getErrorCode(), exception, getLogger(), Level.WARN);
    }


    @Test
    void beforeDeleteWithIllegalArgumentException() {
        when(vfsItem.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("repo");
        IllegalArgumentException exception = new IllegalArgumentException("Invalid argument");
        doThrow(exception).when(storageInterceptor1).beforeDelete(vfsItem, statusHolder, deleteContext);

        storageInterceptors.beforeDelete(vfsItem, statusHolder, deleteContext);
        verify(storageInterceptor1, times(1)).beforeDelete(vfsItem, statusHolder, deleteContext);
        verifyNoInteractions(storageInterceptor2);
        verify(statusHolder, times(1))
                .warn("Delete rejected: " + exception.getMessage(), 400, exception, getLogger());
    }

    @Test
    void beforeDeleteWithForbiddenException() {
        when(vfsItem.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("repo");
        ForbiddenException exception = new ForbiddenException("Direct delete blocked");
        doThrow(exception).when(storageInterceptor1).beforeDelete(vfsItem, statusHolder, deleteContext);

        // The delete must still be rejected (unlike IllegalArgumentException): only the log severity changes.
        assertThrows(ForbiddenException.class, () ->
                storageInterceptors.beforeDelete(vfsItem, statusHolder, deleteContext));
        verify(storageInterceptor1, times(1)).beforeDelete(vfsItem, statusHolder, deleteContext);
        verifyNoInteractions(storageInterceptor2);
        verify(statusHolder, times(1))
                .warn("Delete not allowed: " + exception.getMessage(), SC_FORBIDDEN, exception, getLogger());
    }

    @Test
    void beforeDeleteThrowsException() {
        when(vfsItem.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("repo");
        RuntimeException exception = new RuntimeException("exception message");
        doThrow(exception).when(storageInterceptor1).beforeDelete(vfsItem, statusHolder, deleteContext);

        assertThrows(RuntimeException.class, () ->
                storageInterceptors.beforeDelete(vfsItem, statusHolder, deleteContext));
        verify(storageInterceptor1, times(1)).beforeDelete(vfsItem, statusHolder, deleteContext);
        verifyNoInteractions(storageInterceptor2);
    }

    @Test
    void assertOverwriteAllowed_implementorWithoutOverride_isNoOp() {
        when(repoPath.getRepoKey()).thenReturn("repo");

        storageInterceptors.assertOverwriteAllowed(repoPath, statusHolder);

        verify(storageInterceptor1, times(1)).assertOverwriteAllowed(repoPath, statusHolder);
        verify(storageInterceptor2, times(1)).assertOverwriteAllowed(repoPath, statusHolder);
        verifyNoMoreInteractions(statusHolder);
    }

    @Test
    void assertOverwriteAllowedSkipsInterceptorsForTrashRepo() {
        when(repoPath.getRepoKey()).thenReturn("auto-trashcan");

        storageInterceptors.assertOverwriteAllowed(repoPath, statusHolder);

        verifyNoInteractions(storageInterceptor1, storageInterceptor2);
    }

    @Test
    void storageInterceptorDefaultAssertOverwriteAllowedIsNoOp() {
        StorageInterceptor interceptor = Mockito.mock(StorageInterceptor.class, Mockito.CALLS_REAL_METHODS);

        interceptor.assertOverwriteAllowed(repoPath, statusHolder);

        verifyNoInteractions(statusHolder);
    }

    @Test
    void storageInterceptorAdapterAssertOverwriteAllowedIsNoOp() {
        StorageInterceptorAdapter interceptor = new StorageInterceptorAdapter() {
        };

        interceptor.assertOverwriteAllowed(repoPath, statusHolder);

        verifyNoInteractions(statusHolder);
    }

    @Test
    void checkDirCopyMove_shouldReturnAllow_whenAllInterceptorsAllow() {
        // Arrange
        when(repoPath.getRepoKey()).thenReturn("repo");
        when(storageInterceptor1.checkDirCopyMove(vfsItem, repoPath, statusHolder))
                .thenReturn(ALLOW);
        when(storageInterceptor2.checkDirCopyMove(vfsItem, repoPath, statusHolder))
                .thenReturn(ALLOW);

        // Act
        org.artifactory.sapi.interceptor.CopyMoveCheckResult result =
                storageInterceptors.checkDirCopyMove(vfsItem, repoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
        verify(storageInterceptor1, times(1)).checkDirCopyMove(vfsItem, repoPath, statusHolder);
        verify(storageInterceptor2, times(1)).checkDirCopyMove(vfsItem, repoPath, statusHolder);
    }

    @Test
    void checkDirCopyMove_shouldReturnReject_whenFirstInterceptorRejects() {
        // Arrange
        when(repoPath.getRepoKey()).thenReturn("repo");
        when(storageInterceptor1.checkDirCopyMove(vfsItem, repoPath, statusHolder))
                .thenReturn(REJECT);

        // Act
        org.artifactory.sapi.interceptor.CopyMoveCheckResult result =
                storageInterceptors.checkDirCopyMove(vfsItem, repoPath, statusHolder);

        // Assert
        assertEquals(REJECT, result);
        verify(storageInterceptor1, times(1)).checkDirCopyMove(vfsItem, repoPath, statusHolder);
        verifyNoInteractions(storageInterceptor2);
    }

    @Test
    void checkDirCopyMove_shouldReturnReject_whenSecondInterceptorRejects() {
        // Arrange
        when(repoPath.getRepoKey()).thenReturn("repo");
        when(storageInterceptor1.checkDirCopyMove(vfsItem, repoPath, statusHolder))
                .thenReturn(ALLOW);
        when(storageInterceptor2.checkDirCopyMove(vfsItem, repoPath, statusHolder))
                .thenReturn(REJECT);

        // Act
        org.artifactory.sapi.interceptor.CopyMoveCheckResult result =
                storageInterceptors.checkDirCopyMove(vfsItem, repoPath, statusHolder);

        // Assert
        assertEquals(REJECT, result);
        verify(storageInterceptor1, times(1)).checkDirCopyMove(vfsItem, repoPath, statusHolder);
        verify(storageInterceptor2, times(1)).checkDirCopyMove(vfsItem, repoPath, statusHolder);
    }

    @Test
    void checkDirCopyMove_shouldSkipInterceptors_whenTargetIsTrash() {
        // Arrange
        when(repoPath.getRepoKey()).thenReturn("auto-trashcan");

        // Act
        org.artifactory.sapi.interceptor.CopyMoveCheckResult result =
                storageInterceptors.checkDirCopyMove(vfsItem, repoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
        verifyNoInteractions(storageInterceptor1, storageInterceptor2);
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(StorageInterceptorsImpl.class);
    }
}