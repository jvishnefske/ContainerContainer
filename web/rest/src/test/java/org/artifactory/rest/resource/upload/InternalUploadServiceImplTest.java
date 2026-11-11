package org.artifactory.rest.resource.upload;

import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.InternalSystemFolderUploadRequest;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.api.request.UploadService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemFolderUploadMarker;
import org.artifactory.repo.virtual.VirtualRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InternalUploadServiceImpl}.
 *
 * <p>Covers: path-traversal, system-folder resolution, repo-type dispatch (LOCAL → allowed,
 * REMOTE → allowed, FEDERATED → allowed, VIRTUAL → 400), and the {@link SystemFolderUploadMarker}
 * lifecycle (set while the upload runs, cleared afterwards even on failure). Authorization is
 * enforced at the resource boundary, not in this service, so there are no token-scope tests here.
 *
 * <p>Storage is delegated to a mocked {@link UploadService}. Tests verify that the correct
 * {@link InternalSystemFolderUploadRequest} is constructed — the routing of the request to the
 * {@code -cache} half is tested at the {@code UploadServiceUtils} level, not here.
 *
 * @author Fawaz Atwa
 */
class InternalUploadServiceImplTest {

    private static final String REMOTE_REPO_KEY = "my-remote";
    private static final String LOCAL_REPO_KEY = "my-local";
    private static final String EVIDENCE_FILE_PATH = ".evidence/foo.evidence";

    private InternalRepositoryService internalRepositoryService;
    private UploadService uploadService;
    private FileInfo storedFileInfo;
    private InternalUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        internalRepositoryService = mock(InternalRepositoryService.class);
        uploadService = mock(UploadService.class);
        service = new InternalUploadServiceImpl(internalRepositoryService, uploadService);
        storedFileInfo = mock(FileInfo.class);
        when(internalRepositoryService.getFileInfo(any())).thenReturn(storedFileInfo);
    }

    @AfterEach
    void clearMarker() {
        SystemFolderUploadMarker.unmark();
    }

    @Test
    void upload_validEvidenceRequest_delegatesToUploadService() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        service.upload(REMOTE_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("evidence-bytes"));

        verify(uploadService).upload(
                argThat(req -> req instanceof InternalSystemFolderUploadRequest
                        && REMOTE_REPO_KEY.equals(req.getRepoKey())
                        && EVIDENCE_FILE_PATH.equals(req.getPath())),
                any());
    }

    @Test
    void upload_returnsStoredItemDetails() throws Exception {
        givenLocalRepoExists(LOCAL_REPO_KEY);
        when(storedFileInfo.getSize()).thenReturn(42L);
        when(storedFileInfo.getSha2()).thenReturn("sha256-value");
        when(storedFileInfo.getSha1()).thenReturn("sha1-value");
        when(storedFileInfo.getMd5()).thenReturn("md5-value");

        InternalUploadResult result = service.upload(LOCAL_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("payload"));

        assertEquals(LOCAL_REPO_KEY + "/" + EVIDENCE_FILE_PATH, result.repoPath());
        assertEquals(42L, result.size());
        assertEquals("sha256-value", result.sha256());
        assertEquals("sha1-value", result.sha1());
        assertEquals("md5-value", result.md5());
    }

    @Test
    void upload_pathNotUnderAnySystemFolder_throws400() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, "other/foo.json", streamOf("x")));

        assertTrue(ex.getMessage().contains("system-folder"),
                "Error must explain the system-folder requirement, was: " + ex.getMessage());
        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_pathLooksLikeEvidenceButIsNot_throws400() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, ".evidencefoo/x.json", streamOf("x")));

        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_pathContainsTraversalSegment_throws400() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, ".evidence/../../etc/passwd", streamOf("x")));

        assertTrue(ex.getMessage().contains(".."));
        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_pathContainsTraversalSegmentMidPath_throws400() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, ".evidence/a/../b/file.json", streamOf("x")));

        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_pathHasTrailingSlash_throws400() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, ".evidence/foo.json/", streamOf("x")));

        assertTrue(ex.getMessage().contains("file inside"),
                "Error must explain the file-inside-folder requirement, was: " + ex.getMessage());
        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_pathIsBareSystemFolderName_throws400() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, ".evidence", streamOf("x")));

        assertTrue(ex.getMessage().contains("file inside"),
                "Error must explain the file-inside-folder requirement, was: " + ex.getMessage());
        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_pathIsBareSystemFolderNameWithSlash_throws400() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, ".evidence/", streamOf("x")));

        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_emptyBody_throws400_andDoesNotInvokeStorage() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, EVIDENCE_FILE_PATH,
                        new ByteArrayInputStream(new byte[0])));

        assertTrue(ex.getMessage().contains("empty"),
                "Error must explain the empty-body rule, was: " + ex.getMessage());
        verify(uploadService, never()).upload(any(), any());
        assertFalse(SystemFolderUploadMarker.isMarked(),
                "Marker must not leak when the upload is rejected before it starts");
    }

    @Test
    void upload_filenameContainsDoubleDotButNotAsSegment_isAllowed() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        service.upload(REMOTE_REPO_KEY, ".evidence/manifest..json", streamOf("x"));

        verify(uploadService).upload(any(InternalSystemFolderUploadRequest.class), any());
    }

    @Test
    void upload_anyRecognizedSystemFolder_isAccepted() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        service.upload(REMOTE_REPO_KEY, ".jfrog/anything.txt", streamOf("x"));

        verify(uploadService).upload(any(InternalSystemFolderUploadRequest.class), any());
    }

    @Test
    void upload_repoDoesNotExist_throws400() throws Exception {
        when(internalRepositoryService.repositoryByKey("ghost")).thenReturn(null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.upload("ghost", EVIDENCE_FILE_PATH, streamOf("x")));

        assertTrue(ex.getMessage().contains("ghost"));
        assertTrue(ex.getMessage().contains("does not exist"));
        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_localRepo_passesOriginalRepoKeyToUploadService() throws Exception {
        givenLocalRepoExists(LOCAL_REPO_KEY);

        service.upload(LOCAL_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("payload"));

        verify(uploadService).upload(
                argThat(req -> req instanceof InternalSystemFolderUploadRequest
                        && LOCAL_REPO_KEY.equals(req.getRepoKey())
                        && EVIDENCE_FILE_PATH.equals(req.getPath())),
                any());
    }

    @Test
    void upload_remoteRepo_passesOriginalRepoKeyToUploadService() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);

        service.upload(REMOTE_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("payload"));

        // InternalUploadServiceImpl passes the original key; UploadServiceUtils resolves
        // it to the -cache half when it detects InternalSystemFolderUploadRequest.
        verify(uploadService).upload(
                argThat(req -> req instanceof InternalSystemFolderUploadRequest
                        && REMOTE_REPO_KEY.equals(req.getRepoKey())
                        && EVIDENCE_FILE_PATH.equals(req.getPath())),
                any());
    }

    @Test
    void upload_federatedRepo_passesOriginalRepoKeyToUploadService() throws Exception {
        givenFederatedRepoExists("fed-repo");

        service.upload("fed-repo", EVIDENCE_FILE_PATH, streamOf("payload"));

        verify(uploadService).upload(
                argThat(req -> req instanceof InternalSystemFolderUploadRequest
                        && "fed-repo".equals(req.getRepoKey())
                        && EVIDENCE_FILE_PATH.equals(req.getPath())),
                any());
    }

    @Test
    void upload_virtualRepo_rejectedWith400() throws Exception {
        givenVirtualRepoExists("virt-repo");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.upload("virt-repo", EVIDENCE_FILE_PATH, streamOf("x")));

        assertTrue(ex.getMessage().contains("virtual"));
        verify(uploadService, never()).upload(any(), any());
    }

    @Test
    void upload_marksSystemFolderUploadDuringSave_andClearsAfter() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);
        AtomicBoolean markedDuringSave = new AtomicBoolean();
        doAnswer(inv -> {
            markedDuringSave.set(SystemFolderUploadMarker.isMarked());
            return null;
        }).when(uploadService).upload(any(), any());

        service.upload(REMOTE_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("x"));

        assertTrue(markedDuringSave.get(), "Marker must be set while the upload runs");
        assertFalse(SystemFolderUploadMarker.isMarked(), "Marker must be cleared after a successful upload");
    }

    @Test
    void upload_clearsMarkerEvenWhenUploadThrowsIOException() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);
        doThrow(new IOException("disk full")).when(uploadService).upload(any(), any());

        assertThrows(IOException.class,
                () -> service.upload(REMOTE_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("x")));

        assertFalse(SystemFolderUploadMarker.isMarked(), "Marker must be cleared after an IOException");
    }

    @Test
    void upload_clearsMarkerEvenWhenUploadThrowsRepoRejectException() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);
        doThrow(new RepoRejectException("rejected")).when(uploadService).upload(any(), any());

        assertThrows(RepoRejectException.class,
                () -> service.upload(REMOTE_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("x")));

        assertFalse(SystemFolderUploadMarker.isMarked(), "Marker must be cleared after a RepoRejectException");
    }

    @Test
    void upload_propagatesErrorStatusCodeFromResponse() throws Exception {
        givenRemoteRepoExists(REMOTE_REPO_KEY);
        doAnswer(inv -> {
            InternalArtifactoryResponse response = inv.getArgument(1);
            response.sendError(409, "conflict");
            return null;
        }).when(uploadService).upload(any(), any());

        RepoRejectException actual = assertThrows(RepoRejectException.class,
                () -> service.upload(REMOTE_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("x")));

        assertEquals(409, actual.getErrorCode(),
                "Error status code from the upload response must propagate");
    }

    @Test
    void upload_remoteRepoNotStoringLocally_rejectedWith400() throws Exception {
        givenNonCachingRemoteRepoExists(REMOTE_REPO_KEY);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.upload(REMOTE_REPO_KEY, EVIDENCE_FILE_PATH, streamOf("x")));

        assertTrue(ex.getMessage().contains(REMOTE_REPO_KEY),
                "Error must include the repo key, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Store Artifacts Locally"),
                "Error must guide the user to enable 'Store Artifacts Locally', was: " + ex.getMessage());
        verify(uploadService, never()).upload(any(), any());
    }

    private void givenRemoteRepoExists(String repoKey) {
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        when(internalRepositoryService.repositoryByKey(repoKey)).thenReturn(remoteRepo);
    }

    private void givenNonCachingRemoteRepoExists(String repoKey) {
        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(internalRepositoryService.repositoryByKey(repoKey)).thenReturn(remoteRepo);
    }

    private void givenLocalRepoExists(String repoKey) {
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(internalRepositoryService.repositoryByKey(repoKey)).thenReturn(localRepo);
    }

    private void givenFederatedRepoExists(String repoKey) {
        FederatedRepo federatedRepo = mock(FederatedRepo.class);
        when(internalRepositoryService.repositoryByKey(repoKey)).thenReturn(federatedRepo);
    }

    private void givenVirtualRepoExists(String repoKey) {
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(internalRepositoryService.repositoryByKey(repoKey)).thenReturn(virtualRepo);
    }

    private InputStream streamOf(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
