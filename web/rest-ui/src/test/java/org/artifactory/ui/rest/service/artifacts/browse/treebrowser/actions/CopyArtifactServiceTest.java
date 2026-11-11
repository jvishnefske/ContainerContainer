package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import org.artifactory.activity.ActivityScope;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.CopyMoveArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_REQUEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopyArtifactServiceTest {

    private static final String SOURCE_REPO = "source-repo";
    private static final String TARGET_REPO = "target-repo";
    private static final String FILE_PATH = "a/b/c.txt";

    private CopyArtifactService service;
    private AutoCloseable autoCloseable;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private Future<MoveMultiStatusHolder> future;
    @Mock
    private ExecutorService executorService;
    @Mock
    ArtifactoryContext artifactoryContext;
    private ArtifactoryTracer artifactoryTracer = new ArtifactoryTracer(
            ArtifactoryTracerConfiguration.testTracer());

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        service = new CopyArtifactService();
        service.setAuthorizationService(authorizationService);
        service.setSecurityService(securityService);
        service.setRepositoryService(repositoryService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class)).thenReturn(artifactoryTracer);
        bindArtifactoryHome();
    }

    private void bindArtifactoryHome() {
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(properties.getBooleanProperty(ConstantValues.moveCopyBlockInternalMetadataTarget)).thenReturn(true);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(properties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    private CopyMoveArtifact createCopyArtifact() {
        CopyMoveArtifact copyArtifact = new CopyMoveArtifact();
        copyArtifact.setRepoKey(SOURCE_REPO);
        copyArtifact.setPath(FILE_PATH);
        copyArtifact.setTargetRepoKey(TARGET_REPO);
        copyArtifact.setTargetPath(FILE_PATH);

        return copyArtifact;
    }

    @Test
    void testCopyForbiddenUser() {
        CopyMoveArtifact copyArtifact = createCopyArtifact();
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(copyArtifact);
        Mockito.when(authorizationService.canRead(Mockito.any())).thenReturn(false);

        service.execute(request, response);

        assertEquals(SC_FORBIDDEN, response.getResponseCode());
    }

    @Test
    void testCopyWithTimeout() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact copyArtifact = createCopyArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(TimeoutException.class);

        service.waitAndRespondWithTimeout(response, copyArtifact, future);

        assertEquals(SC_ACCEPTED, response.getResponseCode());
        assertEquals("Copy operation will continue in the background", response.getFeedbackMsg().getInfo());
    }

    @Test
    void testCopyNoTimeout() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact copyArtifact = createCopyArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        MoveMultiStatusHolder copyStatus = mock(MoveMultiStatusHolder.class);
        when(copyStatus.hasErrors()).thenReturn(false);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(copyStatus);

        service.waitAndRespondWithTimeout(response, copyArtifact, future);

        assertEquals(SC_OK, response.getResponseCode());
        assertTrue(response.getFeedbackMsg().getInfo().contains("Artifacts successfully copied"));
    }

    @Test
    void testErrorResponseWhenCopy() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact copyArtifact = createCopyArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        MoveMultiStatusHolder copyStatus = new MoveMultiStatusHolder();
        copyStatus.error("test error!", SC_BAD_REQUEST, null);
        copyStatus.error("test error but will not be the response code!",
                SC_REQUEST_TIMEOUT, null);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(copyStatus);

        service.waitAndRespondWithTimeout(response, copyArtifact, future);

        assertEquals(SC_BAD_REQUEST, response.getResponseCode());
        assertEquals("test error!",
                response.getFeedbackMsg().getErrors().get(0));
    }

    @Test
    void testUnifiedEventExceptionHandling() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact copyArtifact = createCopyArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        UnifiedEventException unifiedEventException = new UnifiedEventException("Worker stopped");
        ExecutionException executionException = new ExecutionException(unifiedEventException);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(executionException);

        assertThrows(UnifiedEventException.class, () -> {
            service.waitAndRespondWithTimeout(response, copyArtifact, future);
        });
    }

    @Test
    void testRuntimeExceptionHandling() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact copyArtifact = createCopyArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        RuntimeException runtimeException = new RuntimeException("Runtime stopped");
        ExecutionException executionException = new ExecutionException(runtimeException);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(executionException);

        assertThrows(RuntimeException.class, () -> {
            service.waitAndRespondWithTimeout(response, copyArtifact, future);
        });
    }

    /**
     * RTDEV-85931 — verify that a copy on a reused Tomcat worker thread overwrites stale
     * MDC values (package/repo/repo_type/group) left over from a previous request,
     * rather than inheriting them.
     */
    @Test
    void execute_shouldOverwriteStaleMdcFromPreviousRequestOnSameThread() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.PACKAGE.put("nuget");
            CurrentActivity.REPO.put("nuget-virt");
            CurrentActivity.REPO_TYPE.put("virtual");
            CurrentActivity.GROUP.put("download");

            CopyMoveArtifact copyArtifact = new CopyMoveArtifact();
            copyArtifact.setRepoKey("eh-docker-docker-local");
            copyArtifact.setPath("cbe/img/0.0.0/sha256__abc");
            copyArtifact.setTargetRepoKey("docker-local");
            copyArtifact.setTargetPath("cbe/img/0.0.0/sha256__abc");

            LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
            when(repositoryService.repoDescriptorByKey("eh-docker-docker-local")).thenReturn(descriptor);
            when(descriptor.getType()).thenReturn(RepoType.Docker);

            ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
            ArtifactoryRestResponse response = new ArtifactoryRestResponse();
            when(request.getImodel()).thenReturn(copyArtifact);
            when(authorizationService.canRead(any())).thenReturn(false);

            service.execute(request, response);

            assertEquals("docker", CurrentActivity.PACKAGE.get());
            assertEquals("eh-docker-docker-local", CurrentActivity.REPO.get());
            assertEquals("local", CurrentActivity.REPO_TYPE.get());
            assertEquals("copy", CurrentActivity.GROUP.get());
        }
    }

    /**
     * A target that only normalizes to {@code .jfrog/...} after slash-trimming (here via a leading
     * slash) must still be blocked with 404 — the guard checks the same normalized path the write
     * uses, not the raw request string.
     */
    @Test
    void testCopyIntoInternalMetadataWithLeadingSlashIsBlocked() {
        CopyMoveArtifact copyArtifact = createCopyArtifact();
        copyArtifact.setTargetPath("/.jfrog/evil.txt");
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(copyArtifact);
        when(authorizationService.canRead(any())).thenReturn(true);

        service.execute(request, response);

        assertEquals(SC_NOT_FOUND, response.getResponseCode());
        verify(repositoryService, never()).copyMultiTx(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void testExecuteWithMultiSlash() {
        CopyMoveArtifact copyArtifact = createCopyArtifact();
        copyArtifact.setPath("a/b//////c.txt");
        copyArtifact.setTargetPath("a/b//////c.txt");
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(copyArtifact);
        RepoPath sourceRepoPath = RepoPathFactory.create(SOURCE_REPO, "a/b//////c.txt");
        when(authorizationService.canRead(sourceRepoPath)).thenReturn(true);
        ItemInfo itemInfo = mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(sourceRepoPath);
        when(repositoryService.getItemInfo(sourceRepoPath)).thenReturn(itemInfo);
        MoveMultiStatusHolder copyStatus = mock(MoveMultiStatusHolder.class);
        when(copyStatus.hasErrors()).thenReturn(false);
        when(repositoryService.copyMultiTx(any(), any(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(
                copyStatus);
        ArgumentCaptor<RepoPath> targetPathCaptor = ArgumentCaptor.forClass(RepoPath.class);
        service.execute(request, response);
        verify(repositoryService).copyMultiTx(any(), targetPathCaptor.capture(), anyBoolean(), anyBoolean(),
                anyBoolean());
        assertEquals("a/b/c.txt", targetPathCaptor.getValue().getPath());
    }
}