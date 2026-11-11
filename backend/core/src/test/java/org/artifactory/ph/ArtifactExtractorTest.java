package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageExpiredException;
import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.exception.SnapshotPolicyException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.ExpiredRepoResource;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.storage.db.fs.model.DbFsFile;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.artifactory.resource.BlockedRepoResource.BlockedType.XRAY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactExtractorTest {

    private static final String REPO = "repo";
    private static final String PATH = "path";


    @Test
    void testLocateFileWithBlockedRepoResource() {
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        DownloadRequestContext context = mock(DownloadRequestContext.class);

        when(context.getRepoKey()).thenReturn(REPO);
        when(context.getPath()).thenReturn(PATH);
        Repo<?, ?> repo = mock(Repo.class);
        when(repositoryService.repositoryByKey(REPO)).thenReturn(repo);
        when(repo.getInfo(context)).thenReturn(
                new BlockedRepoResource(RepoPathFactory.create(REPO, PATH), "Resource is blocked", XRAY));
        try {
            ArtifactExtractor.locateFile(context, repositoryService, true);
            Assertions.fail("Expected PackageForbiddenException");
        } catch (PackageForbiddenException e) {
            Assertions.assertEquals(SC_FORBIDDEN, e.getStatus());
            Assertions.assertEquals("Forbidden: artifact is blocked", e.getPublicReason());
        } catch (PackageException e) {
            Assertions.fail("Expected PackageForbiddenException");
        }
    }

    @Test
    void testLocateFileWhenExpiredRepoResourceShouldThrowsException() {
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        DownloadRequestContext context = mock(DownloadRequestContext.class);
        when(context.getRepoKey()).thenReturn(REPO);
        when(context.getPath()).thenReturn(PATH);
        Repo<?, ?> repo = mock(Repo.class);
        when(repositoryService.repositoryByKey(REPO)).thenReturn(repo);
        FileInfo fileInfo = new FileInfoImpl(RepoPathFactory.create(REPO, PATH));
        when(repo.getInfo(context)).thenReturn(new ExpiredRepoResource(new FileResource(fileInfo)));

        try {
            ArtifactExtractor.locateFile(context, repositoryService, true);
            Assertions.fail("Expected PackageExpiredException");
        } catch (PackageExpiredException e) {
            Assertions.assertEquals(SC_NOT_FOUND, e.getStatus());
            Assertions.assertTrue(
                    e.getInternalReason().contains("Failed to download artifact in repo:repo, path:path"));
        } catch (Exception e) {
            Assertions.fail("Expected PackageExpiredException");
        }
    }

    @Test
    void testLocateFileWithExpiredRepoResource() throws PackageException {
        InternalRepositoryService repositoryServiceMock = mock(InternalRepositoryService.class);
        DownloadRequestContext contextMock = mock(DownloadRequestContext.class);
        Repo<?, ?> repoMock = mock(Repo.class);
        LocalCacheRepo localCacheRepoMock = mock(LocalCacheRepo.class);

        FileInfo fileInfo = new FileInfoImpl(RepoPathFactory.create(REPO, PATH));
        RepoResource repoResource = new ExpiredRepoResource(new FileResource(fileInfo));
        RepoPath filePath = RepoPathFactory.create(REPO, PATH);
        VfsFile<?> expectedFile = new DbFsFile(mock(StoringRepo.class), 1L, fileInfo);

        when(localCacheRepoMock.getImmutableFile(filePath)).thenReturn(expectedFile);
        when(localCacheRepoMock.getKey()).thenReturn(REPO);
        when(repositoryServiceMock.storingRepositoryByKey(REPO)).thenReturn(localCacheRepoMock);
        when(repositoryServiceMock.repositoryByKey(REPO)).thenReturn(repoMock);
        when(repositoryServiceMock.remoteRepositoryByKey(REPO)).thenReturn(mock(RemoteRepo.class));
        when(repoMock.getInfo(contextMock)).thenReturn(repoResource);
        when(contextMock.getRepoKey()).thenReturn(REPO);
        when(contextMock.getPath()).thenReturn(PATH);

        VfsFile<?> resFile = ArtifactExtractor.locateFile(contextMock, repositoryServiceMock, false);
        Assertions.assertEquals(expectedFile, resFile);
    }

    @ParameterizedTest
    @MethodSource("getResourceStreamHandleProvider")
    void getResourceStreamHandle(Exception catchException, PackageException expectedException)
            throws RepoRejectException, IOException {
        RepoResource repoResourceMock = mock(RepoResource.class);
        DownloadRequestContext downloadRequestContextMock = mock(DownloadRequestContext.class);
        InternalRepositoryService internalRepositoryServiceMock = mock(InternalRepositoryService.class);
        Repo<?, ?> repoMock = mock(Repo.class);

        when(repoResourceMock.isFound()).thenReturn(true);
        when(repoResourceMock.isBlocked()).thenReturn(false);
        when(repoResourceMock.getRepoKey()).thenReturn(REPO);
        when(repoResourceMock.getPath()).thenReturn(PATH);

        doThrow(catchException).when(internalRepositoryServiceMock)
                .getResourceStreamHandle(downloadRequestContextMock, repoMock, repoResourceMock);
        PackageException actualException = Assertions.assertThrows(PackageException.class,
                () -> ArtifactExtractor.getResourceStreamHandle(repoResourceMock, downloadRequestContextMock, repoMock,
                        internalRepositoryServiceMock));

        assertPackageExceptionDetails(expectedException, actualException);
    }

    private static Stream<Arguments> getResourceStreamHandleProvider() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO);
        RepoPath repoPath = RepoPathFactory.create(REPO, PATH);
        SnapshotPolicyException snapshotPolicyException = new SnapshotPolicyException(repoConfig, repoPath);
        String catchConflictExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause:%s", REPO, PATH,
                        snapshotPolicyException.getMessage());
        BlackedOutException blackedOutException = new BlackedOutException(repoConfig, repoPath);
        String catchBlackedOutExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause: repo is blacked out", REPO, PATH);
        RepoRejectException repoRejectException = new RepoRejectException("message");
        String catchRepoRejectExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause:%s", REPO, PATH,
                        repoRejectException.getMessage());
        RemoteRequestException remoteRequestException = new RemoteRequestException("message", 402, "internal");
        String catchRemoteRequestExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause:%s", REPO, PATH,
                        remoteRequestException.getMessage());
        IOException ioException = new IOException("stam");
        String catchIOExceptionMessage =
                String.format("Failed to find artifact, repo:%s, path:%s, cause:%s", REPO, PATH, ioException);
        return Stream.of(Arguments.of(snapshotPolicyException,
                        new PackageException(SC_CONFLICT, "Conflict", catchConflictExceptionMessage)),
                Arguments.of(blackedOutException, new PackageNotFoundException(catchBlackedOutExceptionMessage)),
                Arguments.of(repoRejectException, new PackageNotFoundException(catchRepoRejectExceptionMessage)),
                Arguments.of(remoteRequestException, new PackageNotFoundException(catchRemoteRequestExceptionMessage)),
                Arguments.of(ioException,
                        new PackageException(SC_INTERNAL_SERVER_ERROR, "Internal server error", catchIOExceptionMessage,
                                ioException)));
    }

    @Test
    void testGetResourceStreamHandle_ValidResource() throws PackageException, IOException, RepoRejectException {
        RepoResource repoResourceMock = mock(RepoResource.class);
        DownloadRequestContext contextMock = mock(DownloadRequestContext.class);
        InternalRepositoryService repositoryServiceMock = mock(InternalRepositoryService.class);
        Repo<?, ?> repoMock = mock(Repo.class);
        ResourceStreamHandle resourceStreamHandleMock = mock(ResourceStreamHandle.class);

        mockGetResourceStreamHandle(repoResourceMock, contextMock, repositoryServiceMock,
                resourceStreamHandleMock);

        ResourceStreamHandle result = ArtifactExtractor.getResourceStreamHandle(repoResourceMock, contextMock, repoMock,
                repositoryServiceMock);

        Assertions.assertNotNull(result);
    }

    @Test
    void testRepoNotFoundException() {
        DownloadRequestContext context = mock(DownloadRequestContext.class);
        when(context.getRepoKey()).thenReturn(REPO);
        PackageRepoNotFoundException exception = Assertions.assertThrows(PackageRepoNotFoundException.class,
                () -> ArtifactExtractor.locateFile(context, mock(InternalRepositoryService.class), false));
        Assertions.assertEquals("Not found", exception.getMessage());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals("Failed to find repository:repo", exception.getInternalReason());
    }

    @Test
    void whenRemoteRepoForCachedNotFound() {
        RemoteRepoResource resource = mock(RemoteRepoResource.class);
        when(resource.isFound()).thenReturn(true);
        when(resource.isBlocked()).thenReturn(false);
        when(resource.getRepoKey()).thenReturn("abc-cache");
        DownloadRequestContext context = mock(DownloadRequestContext.class);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);

        PackageRepoNotFoundException exception = Assertions.assertThrows(PackageRepoNotFoundException.class,
                () -> ArtifactExtractor.locateAndGetByInfo(resource, context, false, repositoryService));

        Assertions.assertEquals("Not found", exception.getMessage());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals("Failed to find repository:abc", exception.getInternalReason());
    }

    void mockGetResourceStreamHandle(RepoResource repoResourceMock, DownloadRequestContext contextMock,
            InternalRepositoryService repositoryServiceMock, ResourceStreamHandle resourceStreamHandleMock)
            throws RepoRejectException, IOException {
        when(repoResourceMock.isFound()).thenReturn(true);
        when(repoResourceMock.isBlocked()).thenReturn(false);
        when(repoResourceMock.getRepoKey()).thenReturn(REPO);
        when(repoResourceMock.getPath()).thenReturn(PATH);
        when(contextMock.getRepoKey()).thenReturn(REPO);
        when(contextMock.getPath()).thenReturn(PATH);
        when(repositoryServiceMock.getResourceStreamHandle(any(), any(), any())).thenReturn(resourceStreamHandleMock);
    }

    private static void assertPackageExceptionDetails(PackageException expectedException,
            PackageException actualException) {
        Assertions.assertEquals(expectedException.getMessage(), actualException.getMessage());
        Assertions.assertEquals(expectedException.getStatus(), actualException.getStatus());
        Assertions.assertEquals(expectedException.getInternalReason(), actualException.getInternalReason());
        Assertions.assertEquals(expectedException.getPublicReason(), actualException.getPublicReason());
    }
}