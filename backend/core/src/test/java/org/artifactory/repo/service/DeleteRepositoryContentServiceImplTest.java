package org.artifactory.repo.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.CancelException;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.REPOSITORY_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
class DeleteRepositoryContentServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private StorageInterceptors storageInterceptors;
    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private ArtifactoryContext context;

    private DeleteRepositoryContentServiceImpl deleteService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repoService);
        when(context.beanForType(StorageInterceptors.class)).thenReturn(storageInterceptors);
        ArtifactoryContextThreadBinder.bind(context);
        setLongSystemProperty(ConstantValues.repoDeleteOrphanRetryCount, 3);
        setLongSystemProperty(ConstantValues.repoDeleteOrphanRetryIntervalMillis, 0);
        deleteService = new DeleteRepositoryContentServiceImpl();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testDeleteRepositoryContent_RealRepo_Success() {
        String repoKey = "real-repo";
        givenLocalStoringRepo(repoKey);
        givenRepositoryContentDeleted(repoKey);

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        assertDoesNotThrow(() -> deleteService.deleteRepositoryContent(repoKey, statusHolder));
        assertEquals(-1, statusHolder.getStatusCode());
    }

    @Test
    void testDeleteRepositoryContent_VirtualRepo_Success() {
        String repoKey = "virtual-repo";
        givenVirtualStoringRepo(repoKey);
        givenRepositoryContentDeleted(repoKey);

        assertDoesNotThrow(() -> deleteService.deleteRepositoryContent(repoKey, new BasicStatusHolder()));
    }

    @Test
    void testDeleteRepositoryContent_RepoNotFound() {
        String repoKey = "non-existent-repo";
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        givenStoringRepoNotFound(repoKey, statusHolder);

        RepositoryContentDeletionFailedException exception =
                assertThrows(RepositoryContentDeletionFailedException.class,
                        () -> deleteService.deleteRepositoryContent(repoKey, statusHolder));

        assertTrue(exception.getStatusHolder().hasWarnings());
        assertEquals(1, exception.getStatusHolder().getEntries().size());
        assertEquals(REPOSITORY_NOT_FOUND.getReason(repoKey), exception.getMessage());
    }

    @Test
    void testDeleteRepositoryContent_CancelException() {
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);

        CancelException cancelException = new CancelException("Cancel exception", SC_FORBIDDEN);
        givenDeleteRepositoryContentThrowsException(cancelException);

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepositoryContentDeletionFailedException exception =
                assertThrows(RepositoryContentDeletionFailedException.class,
                        () -> deleteService.deleteRepositoryContent(repoKey, statusHolder));

        assertEquals(SC_FORBIDDEN, exception.getStatusHolder().getStatusCode());
        assertTrue(exception.getStatusHolder().hasWarnings());
    }

    @Test
    void testDeleteRepositoryContent_CancelExceptionNullMessage() {
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);

        CancelException cancelException = new CancelException(null, SC_FORBIDDEN);
        givenDeleteRepositoryContentThrowsException(cancelException);

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepositoryContentDeletionFailedException exception =
                assertThrows(RepositoryContentDeletionFailedException.class,
                        () -> deleteService.deleteRepositoryContent(repoKey, statusHolder));

        assertEquals(SC_FORBIDDEN, exception.getStatusHolder().getStatusCode());

        exception.getStatusHolder().getStatusMsg();
        assertTrue(exception.getStatusHolder().hasWarnings());
    }

    @Test
    void testDeleteRepositoryContent_RepoNotEmpty() {
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);
        givenRepoNotEmptyAfterDeletion(repoKey);

        BasicStatusHolder negativeStatusHolder = createNegativeStatusHolder();
        RepositoryContentDeletionFailedException exception =
                assertThrows(RepositoryContentDeletionFailedException.class,
                        () -> deleteService.deleteRepositoryContent(repoKey, negativeStatusHolder));

        assertTrue(exception.getStatusHolder().hasWarnings());
        assertEquals(SC_FORBIDDEN, exception.getStatusHolder().getStatusCode());
    }

    private static BasicStatusHolder createNegativeStatusHolder() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.warn("Failure", SC_FORBIDDEN, log);
        return statusHolder;
    }

    @Test
    void testDeleteRepositoryContent_ForbiddenException() {
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);

        ForbiddenException forbiddenException = new ForbiddenException("Forbidden");
        givenDeleteRepositoryContentThrowsException(forbiddenException);

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepositoryContentDeletionFailedException exception =
                assertThrows(RepositoryContentDeletionFailedException.class,
                        () -> deleteService.deleteRepositoryContent(repoKey, statusHolder));

        assertEquals(SC_FORBIDDEN, exception.getStatusHolder().getStatusCode());
        assertTrue(exception.getStatusHolder().hasWarnings());
    }

    @Test
    void testDeleteRepositoryContent_AfterRepoDeleteContentInterceptorsThrowException() {
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);
        givenRepositoryContentDeleted(repoKey);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        givenAfterRepoDeleteContentInterceptorsThrowException(repoKey, statusHolder);

        RepositoryContentDeletionFailedException exception =
                assertThrows(RepositoryContentDeletionFailedException.class,
                        () -> deleteService.deleteRepositoryContent(repoKey, statusHolder));

        assertEquals(SC_FORBIDDEN, exception.getStatusHolder().getStatusCode());
        assertEquals("Failure", exception.getStatusHolder().getStatusMsg());
    }

    @Test
    void deleteRepositoryContent_shouldCleanupOrphanedNodes_whenContentRemainsAfterDeletion() {
        // Arrange
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);
        when(repoService.containsRealContent(repoKey)).thenReturn(true, false);

        // Act
        assertDoesNotThrow(
                () -> deleteService.deleteRepositoryContent(repoKey, new BasicStatusHolder()));

        // Assert
        verify(repoService, times(1))
                .deleteOrphanNodes(eq(repoKey), any(BasicStatusHolder.class), eq(true));
    }

    @Test
    void deleteRepositoryContent_shouldRetryOrphanCleanup_whenContentPersists() {
        // Arrange
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);
        when(repoService.containsRealContent(repoKey)).thenReturn(true, true, false);

        // Act
        assertDoesNotThrow(
                () -> deleteService.deleteRepositoryContent(repoKey, new BasicStatusHolder()));

        // Assert
        verify(repoService, times(2))
                .deleteOrphanNodes(eq(repoKey), any(BasicStatusHolder.class), eq(true));
    }

    @Test
    void deleteRepositoryContent_shouldFailAfterMaxRetries_whenOrphanCleanupCannotClear() {
        // Arrange
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);
        when(repoService.containsRealContent(repoKey)).thenReturn(true);

        // Act & Assert
        assertThrows(RepositoryContentDeletionFailedException.class,
                () -> deleteService.deleteRepositoryContent(repoKey, new BasicStatusHolder()));
        verify(repoService, times(3))
                .deleteOrphanNodes(eq(repoKey), any(BasicStatusHolder.class), eq(true));
    }

    @Test
    void deleteRepositoryContent_shouldNotCallOrphanCleanup_whenRepoIsEmptyAfterDeletion() {
        // Arrange
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);
        givenRepositoryContentDeleted(repoKey);

        // Act
        assertDoesNotThrow(
                () -> deleteService.deleteRepositoryContent(repoKey, new BasicStatusHolder()));

        // Assert
        verify(repoService, never())
                .deleteOrphanNodes(anyString(), any(BasicStatusHolder.class), anyBoolean());
    }

    @Test
    void deleteRepositoryContent_shouldNotCallOrphanCleanup_whenDeletionHasErrors() {
        // Arrange
        String repoKey = "test-repo";
        givenLocalStoringRepo(repoKey);
        givenDeleteRepositoryContentThrowsException(
                new CancelException("Cancel exception", SC_FORBIDDEN));

        // Act & Assert
        assertThrows(RepositoryContentDeletionFailedException.class,
                () -> deleteService.deleteRepositoryContent(repoKey, new BasicStatusHolder()));
        verify(repoService, never())
                .deleteOrphanNodes(anyString(), any(BasicStatusHolder.class), anyBoolean());
    }

    private void givenAfterRepoDeleteContentInterceptorsThrowException(String repoKey,
            BasicStatusHolder statusHolder) {
        doAnswer(invocation -> {
            statusHolder.warn("Failure", SC_FORBIDDEN, log);
            throw new RuntimeException("Interceptors exception");
        }).when(storageInterceptors).afterRepositoryContentDelete(eq(repoKey), any(BasicStatusHolder.class));
    }

    private void givenRepoNotEmptyAfterDeletion(String repoKey) {
        when(repoService.containsRealContent(repoKey)).thenReturn(true);
    }

    private void givenDeleteRepositoryContentThrowsException(Exception e) {
        doThrow(e).when(repoService).deleteRepositoryContent(anyString(), any(), anyBoolean());
    }

    private void givenLocalStoringRepo(String repoKey) {
        StoringRepo<?, ?> storingRepo = mock(StoringRepo.class);
        when(storingRepo.isLocal()).thenReturn(true);
        when(storingRepo.getKey()).thenReturn(repoKey);
        when(repoService.storingRepositoryByKey(repoKey)).thenReturn(storingRepo);
    }

    private void givenVirtualStoringRepo(String repoKey) {
        StoringRepo<?, ?> storingRepo = mock(StoringRepo.class);
        when(storingRepo.isLocal()).thenReturn(false);
        when(storingRepo.getKey()).thenReturn(repoKey);
        when(repoService.storingRepositoryByKey(repoKey)).thenReturn(storingRepo);
    }

    private void givenStoringRepoNotFound(String repoKey, BasicStatusHolder statusHolder) {
        doAnswer(invocationOnMock -> {
            int statusHolderParamIndex = 1;
            BasicStatusHolder statusHolderParam =
                    invocationOnMock.getArgument(statusHolderParamIndex);
            statusHolderParam.warn(REPOSITORY_NOT_FOUND.getReason(repoKey), REPOSITORY_NOT_FOUND.getStatusCode(),
                    log);
            return null;
        }).when(repoService).deleteRepositoryContent(eq(repoKey), eq(statusHolder), eq(true));
    }

    private void givenRepositoryContentDeleted(String repoKey) {
        when(repoService.containsRealContent(repoKey)).thenReturn(false);
    }
}
