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
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.CopyMoveArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoveArtifactServiceTest {

    private static final String SOURCE_REPO = "source-repo";
    private static final String TARGET_REPO = "target-repo";
    private static final String FILE_PATH = "a/b/c.txt";

    private MoveArtifactService service;
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
        service = new MoveArtifactService(repositoryService, authorizationService, securityService);
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

    private CopyMoveArtifact createMoveArtifact() {
        CopyMoveArtifact moveArtifact = new CopyMoveArtifact();
        moveArtifact.setRepoKey(SOURCE_REPO);
        moveArtifact.setPath(FILE_PATH);
        moveArtifact.setTargetRepoKey(TARGET_REPO);
        moveArtifact.setTargetPath(FILE_PATH);

        return moveArtifact;
    }

    @Test
    void testMoveForbiddenUser() {
        CopyMoveArtifact moveArtifact = createMoveArtifact();
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(moveArtifact);
        Mockito.when(authorizationService.canRead(Mockito.any())).thenReturn(false);
        Mockito.when(authorizationService.canDelete(Mockito.any())).thenReturn(false);

        service.execute(request, response);

        assertEquals(SC_FORBIDDEN, response.getResponseCode());
    }

    @Test
    void testMoveWithUnifiedEventException() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact moveArtifact = createMoveArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        ExecutionException executionException = new ExecutionException(new UnifiedEventException(
                "Worker stopped"));
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(executionException);

        assertThrows(UnifiedEventException.class, () -> {
            service.waitAndRespondWithTimeout(response, moveArtifact, future);
        });
    }

    @Test
    void testMoveRuntimeException() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact moveArtifact = createMoveArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        ExecutionException executionException = new ExecutionException(new RuntimeException("Worker stopped"));
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(executionException);

        assertThrows(RuntimeException.class, () -> {
            service.waitAndRespondWithTimeout(response, moveArtifact, future);
        });
    }

    @Test
    void testMoveWithTimeout() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact moveArtifact = createMoveArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(TimeoutException.class);

        service.waitAndRespondWithTimeout(response, moveArtifact, future);

        assertEquals(SC_ACCEPTED, response.getResponseCode());
        assertEquals("Move operation will continue in the background", response.getFeedbackMsg().getInfo());
    }

    @Test
    void testMoveNoTimeout() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact moveArtifact = createMoveArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        MoveMultiStatusHolder moveStatus = mock(MoveMultiStatusHolder.class);
        when(moveStatus.hasErrors()).thenReturn(false);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(moveStatus);

        service.waitAndRespondWithTimeout(response, moveArtifact, future);

        assertEquals(SC_OK, response.getResponseCode());
        assertTrue(response.getFeedbackMsg().getInfo().contains("Successfully moved artifact"));
    }

    @Test
    void testErrorResponseWhenMove() throws ExecutionException, InterruptedException, TimeoutException {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        CopyMoveArtifact moveArtifact = createMoveArtifact();
        Future<MoveMultiStatusHolder> future = mock(Future.class);
        MoveMultiStatusHolder moveStatus = new MoveMultiStatusHolder();
        moveStatus.error("test error!", SC_INTERNAL_SERVER_ERROR, null);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(moveStatus);

        service.waitAndRespondWithTimeout(response, moveArtifact, future);

        assertEquals(SC_INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getFeedbackMsg().getErrors().contains("test error!"));
    }

    /**
     * RTDEV-85931 — verify that a move on a reused Tomcat worker thread overwrites stale
     * MDC values (package/repo/repo_type/group) left over from a previous request,
     * rather than inheriting them.
     */
    @Test
    void testExecuteOverwritesStaleMdcOnReusedThread() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.PACKAGE.put("nuget");
            CurrentActivity.REPO.put("nuget-virt");
            CurrentActivity.REPO_TYPE.put("virtual");
            CurrentActivity.GROUP.put("download");

            CopyMoveArtifact moveArtifact = new CopyMoveArtifact();
            moveArtifact.setRepoKey("eh-docker-docker-local");
            moveArtifact.setPath("cbe/img/0.0.0/sha256__abc");
            moveArtifact.setTargetRepoKey("docker-local");
            moveArtifact.setTargetPath("cbe/img/0.0.0/sha256__abc");

            LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
            when(repositoryService.repoDescriptorByKey("eh-docker-docker-local")).thenReturn(descriptor);
            when(descriptor.getType()).thenReturn(RepoType.Docker);

            ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
            ArtifactoryRestResponse response = new ArtifactoryRestResponse();
            when(request.getImodel()).thenReturn(moveArtifact);
            when(authorizationService.canRead(any())).thenReturn(false);
            when(authorizationService.canDelete(any())).thenReturn(false);

            service.execute(request, response);

            assertEquals("docker", CurrentActivity.PACKAGE.get());
            assertEquals("eh-docker-docker-local", CurrentActivity.REPO.get());
            assertEquals("local", CurrentActivity.REPO_TYPE.get());
            assertEquals("move", CurrentActivity.GROUP.get());
        }
    }

    /**
     * RTDEV-85931 — a null source repo key sets only the operation group; with no descriptor to
     * look up, repo/package/repo_type stay unset.
     */
    @Test
    void testPopulateMdcFromSourceWithNullRepoKey() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            service.populateMdcFromSource(null, repositoryService);

            assertEquals("move", CurrentActivity.GROUP.get());
            assertNull(CurrentActivity.REPO.get());
            assertNull(CurrentActivity.PACKAGE.get());
            assertNull(CurrentActivity.REPO_TYPE.get());
        }
    }

    /**
     * RTDEV-85931 — an unknown source repo (descriptor not found) sets the group but short-circuits
     * before repo/package/repo_type are written.
     */
    @Test
    void testPopulateMdcFromSourceWhenDescriptorNotFound() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            when(repositoryService.repoDescriptorByKey("missing-repo")).thenReturn(null);

            service.populateMdcFromSource("missing-repo", repositoryService);

            assertEquals("move", CurrentActivity.GROUP.get());
            assertNull(CurrentActivity.REPO.get());
            assertNull(CurrentActivity.PACKAGE.get());
            assertNull(CurrentActivity.REPO_TYPE.get());
        }
    }

    /**
     * RTDEV-85931 — a virtual source repo resolves repo_type=virtual.
     */
    @Test
    void testPopulateMdcFromSourceWithVirtualRepo() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            VirtualRepoDescriptor descriptor = mock(VirtualRepoDescriptor.class);
            when(descriptor.getType()).thenReturn(RepoType.NuGet);
            when(repositoryService.repoDescriptorByKey("nuget-virt")).thenReturn(descriptor);

            service.populateMdcFromSource("nuget-virt", repositoryService);

            assertEquals("nuget-virt", CurrentActivity.REPO.get());
            assertEquals("nuget", CurrentActivity.PACKAGE.get());
            assertEquals("virtual", CurrentActivity.REPO_TYPE.get());
            assertEquals("move", CurrentActivity.GROUP.get());
        }
    }

    /**
     * RTDEV-85931 — a remote source repo resolves repo_type=remote.
     */
    @Test
    void testPopulateMdcFromSourceWithRemoteRepo() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            RemoteRepoDescriptor descriptor = mock(RemoteRepoDescriptor.class);
            when(descriptor.getType()).thenReturn(RepoType.Maven);
            when(repositoryService.repoDescriptorByKey("maven-remote")).thenReturn(descriptor);

            service.populateMdcFromSource("maven-remote", repositoryService);

            assertEquals("maven-remote", CurrentActivity.REPO.get());
            assertEquals("maven", CurrentActivity.PACKAGE.get());
            assertEquals("remote", CurrentActivity.REPO_TYPE.get());
            assertEquals("move", CurrentActivity.GROUP.get());
        }
    }

    /**
     * RTDEV-85931 — a federated source repo resolves repo_type=local (it extends LocalRepoDescriptor).
     */
    @Test
    void testPopulateMdcFromSourceWithFederatedRepo() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            FederatedRepoDescriptor descriptor = mock(FederatedRepoDescriptor.class);
            when(descriptor.getType()).thenReturn(RepoType.Docker);
            when(repositoryService.repoDescriptorByKey("docker-fed")).thenReturn(descriptor);

            service.populateMdcFromSource("docker-fed", repositoryService);

            assertEquals("local", CurrentActivity.REPO_TYPE.get());
        }
    }

    /**
     * RTDEV-85931 — a descriptor that is neither local/remote/virtual leaves repo_type unset,
     * while repo and package are still populated.
     */
    @Test
    void testPopulateMdcFromSourceWithUnknownDescriptorType() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            RepoDescriptor descriptor = mock(RepoDescriptor.class);
            when(descriptor.getType()).thenReturn(RepoType.Generic);
            when(repositoryService.repoDescriptorByKey("generic-repo")).thenReturn(descriptor);

            service.populateMdcFromSource("generic-repo", repositoryService);

            assertEquals("generic-repo", CurrentActivity.REPO.get());
            assertEquals("generic", CurrentActivity.PACKAGE.get());
            assertNull(CurrentActivity.REPO_TYPE.get());
        }
    }

    /**
     * RTDEV-85931 — a descriptor lookup failure is swallowed (fail-soft instrumentation): the group
     * remains set and the move/copy itself is never affected.
     */
    @Test
    void testPopulateMdcFromSourceFailSoftOnException() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            when(repositoryService.repoDescriptorByKey("boom-repo"))
                    .thenThrow(new RuntimeException("descriptor lookup failed"));

            service.populateMdcFromSource("boom-repo", repositoryService);

            assertEquals("move", CurrentActivity.GROUP.get());
            assertNull(CurrentActivity.REPO.get());
            assertNull(CurrentActivity.PACKAGE.get());
            assertNull(CurrentActivity.REPO_TYPE.get());
        }
    }

    /**
     * A target that only normalizes to {@code .jfrog/...} after slash-trimming (here via a leading
     * slash) must still be blocked with 404 — the guard checks the same normalized path the write
     * uses, not the raw request string.
     */
    @Test
    void testMoveIntoInternalMetadataWithLeadingSlashIsBlocked() {
        CopyMoveArtifact moveArtifact = createMoveArtifact();
        moveArtifact.setTargetPath("/.jfrog/evil.txt");
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(moveArtifact);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canDelete(any())).thenReturn(true);

        service.execute(request, response);

        assertEquals(SC_NOT_FOUND, response.getResponseCode());
        verify(repositoryService, never()).moveMultiTx(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void testExecuteWithMultiSlash() throws Exception {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        CopyMoveArtifact moveArtifact = mock(CopyMoveArtifact.class);
        MoveMultiStatusHolder moveStatusHolder = mock(MoveMultiStatusHolder.class);
        when(request.getImodel()).thenReturn(moveArtifact);
        when(moveArtifact.getRepoKey()).thenReturn("sourceRepo");
        when(moveArtifact.getPath()).thenReturn("sourcePath");
        when(moveArtifact.getTargetRepoKey()).thenReturn("targetRepo");
        when(moveArtifact.getTargetPath()).thenReturn("/targetPath/////////file.txt");
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(authorizationService.canDelete(any(RepoPath.class))).thenReturn(true);
        when(executorService.submit(any(Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(moveStatusHolder);
        service.execute(request, response);
        ArgumentCaptor<RepoPath> sourcePathCaptor = ArgumentCaptor.forClass(RepoPath.class);
        ArgumentCaptor<RepoPath> targetPathCaptor = ArgumentCaptor.forClass(RepoPath.class);
        verify(repositoryService).moveMultiTx(sourcePathCaptor.capture(), targetPathCaptor.capture(), anyBoolean(),
                anyBoolean(), anyBoolean());
        RepoPath capturedTargetPath = targetPathCaptor.getValue();
        assertEquals("targetRepo", capturedTargetPath.getRepoKey());
        assertEquals("targetPath/file.txt", capturedTargetPath.getPath());
    }
}