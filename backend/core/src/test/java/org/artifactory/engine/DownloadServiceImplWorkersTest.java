package org.artifactory.engine;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.download.DownloadCtx;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.ControlledCacheRedirectService;
import org.artifactory.api.request.DownloadErrorNotificationResult;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.fs.RepoResource;
import org.artifactory.plugin.AltResponseHelper;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RequestResponseHelper;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.service.SmartRepoService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.UnifiedEventServiceImpl;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.ResponseContextData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.stream.Stream;

import static org.artifactory.engine.DownloadServiceTestUtils.mockIsRedirectFromCachingProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dvir Twina
 */
class DownloadServiceImplWorkersTest extends ArtifactoryHomeBoundJUnitTest {
    private DownloadServiceImpl downloadService;
    @Mock
    private InternalRequestContext requestContext;
    @Mock
    private ArtifactoryRequest request;
    @Mock
    private PluginsAddon pluginAddon;
    @Mock
    private WorkersAddon workersAddon;
    @Mock
    private AltResponseHelper altResponseHelper;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CentralConfigService centralConfig;
    @Mock
    private SmartRepoService smartRepoService;
    @Mock
    private ControlledCacheRedirectService controlledCacheRedirectService;
    @Mock
    private DownloadRedirectHelper downloadRedirectHelper;
    @Mock
    private RequestResponseHelper requestResponseHelper;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    UnifiedEventServiceImpl unifiedEventService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(requestContext.getRequest()).thenReturn(request);
        downloadService = spy(DownloadServiceImpl.class);
        ReflectionTestUtils.setField(downloadService, "altResponseHelper", altResponseHelper);
        ReflectionTestUtils.setField(downloadService, "downloadRedirectHelper", downloadRedirectHelper);
        ReflectionTestUtils.setField(downloadService, "requestResponseHelper", requestResponseHelper);
        downloadService.repositoryService = repositoryService;
        downloadService.unifiedEventService = unifiedEventService;
        when(unifiedEventService.sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD),
                any(ArtifactoryRequest.class), any(RepoPath.class))).thenReturn(mock(DispatcherResponse.class));
        when(addonsManager.addonByType(WorkersAddon.class)).thenReturn(workersAddon);
        downloadService.addonsManager = addonsManager;
        downloadService.centralConfig = centralConfig;
        downloadService.smartRepoService = smartRepoService;
        mockAddonsManager();
        downloadService.controlledCacheRedirectService = controlledCacheRedirectService;
        mockPluginsAddonAndDisable();
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    public void unbind() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    private void mockPluginsAddonAndDisable() {
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());
    }

    private void mockAddonsManager() {
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());
        when(addonsManager.addonByType(WorkersAddon.class)).thenReturn(workersAddon);
    }

    @Test
    void testSendUnifiedEventNullHeaders() {
        DownloadCtx downloadCtx = new DownloadCtx();
        RepoPath mockRepoPath = mock(RepoPath.class);
        doReturn(DispatcherResponse.builder().data(ResponseContextData.builder().headers(null).build()).build()).when(
                unifiedEventService).sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD),
                any(ArtifactoryRequest.class), any(RepoPath.class));
        downloadService.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request, mockRepoPath, downloadCtx);
        assertNull(downloadCtx.getHeaders());
        verify(unifiedEventService, times(1))
                .sendArtifactEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request, mockRepoPath);
    }

    @Test
    void testSendUnifiedEventNullResponse() {
        DownloadCtx downloadCtx = new DownloadCtx();
        RepoPath mockRepoPath = mock(RepoPath.class);
        doReturn(DispatcherResponse.builder().data(null).build()).when(unifiedEventService)
                .sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD),
                        any(ArtifactoryRequest.class), any(RepoPath.class));
        downloadService.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request, mockRepoPath, downloadCtx);
        verify(unifiedEventService, times(1))
                .sendArtifactEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request, mockRepoPath);
    }

    @Test
    void testSendUnifiedEventWorkerStopStatus() {
        DownloadCtx downloadCtx = new DownloadCtx();
        RepoPath mockRepoPath = mock(RepoPath.class);
        doThrow(new UnifiedEventException(HttpStatus.SC_CONFLICT, false, "Thrown from worker")).when(
                unifiedEventService).sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD),
                any(ArtifactoryRequest.class), any(RepoPath.class));
        assertThrows(WorkerStopException.class,
                () -> downloadService.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request, mockRepoPath,
                        downloadCtx));
    }

    @Test
    void testSendUnifiedEventNonConflictStatus() {
        DownloadCtx downloadCtx = new DownloadCtx();
        RepoPath mockRepoPath = mock(RepoPath.class);
        doThrow(new UnifiedEventException(HttpStatus.SC_BAD_REQUEST, false, "Thrown from worker")).when(
                unifiedEventService).sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD),
                any(ArtifactoryRequest.class), any(RepoPath.class));
        assertThrows(UnifiedEventException.class,
                () -> downloadService.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request, mockRepoPath,
                        downloadCtx));
    }

    @Test
    void testRespondFoundResourceThrowingException() throws IOException {
        ArtifactoryResponse response = new InternalArtifactoryResponse();
        RepoResource repoResource = mock(RepoResource.class);
        RepoPath responseRepoPath = mock(RepoPath.class);
        when(repoResource.getResponseRepoPath()).thenReturn(responseRepoPath);
        ResourceStreamHandle handle = mock(ResourceStreamHandle.class);
        when(altResponseHelper.alternateResponse(eq(requestContext), eq(response), eq(responseRepoPath), anyInt()))
                .thenReturn(handle);
        Repo<?, ?> responseRepo = mock(Repo.class);
        when(repositoryService.repositoryByKey(any())).thenReturn(responseRepo);
        when(addonsManager.addonByType(WorkersAddon.class)).thenReturn(workersAddon);
        when(controlledCacheRedirectService.shouldRedirect(any(), any(), any())).thenReturn(false);
        doThrow(WorkerStopException.class).when(downloadService).sendUnifiedEvent(any(), any(), any(), any());
        ReflectionTestUtils.invokeMethod(downloadService, "respondFoundResource",
                requestContext, response, repoResource, true);
        assertEquals(HttpStatus.SC_CONFLICT, response.getStatus());
    }

    @ParameterizedTest
    @MethodSource("afterDownloadWorkerTriggeringSource")
    void testAfterDownloadWorkerTriggering(boolean isHeadRequest, boolean isSuccessfulResponse,
            int expectedInvocationTimes) throws IOException {
        AolService aolService = mock(AolService.class);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(false);

        when(downloadRedirectHelper.isRedirectEnable(any(), any())).thenReturn(true);

        mockIsRedirectFromCachingProxy(request, false);
        when(request.getPath()).thenReturn("path");
        doReturn(isHeadRequest).when(request).isHeadOnly();

        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repoDescriptor.isReal()).thenReturn(true);
        Repo<RepoDescriptor, ?> repo = mock(Repo.class);
        when(repo.getDescriptor()).thenReturn(repoDescriptor);
        when(repositoryService.repositoryByKey(any())).thenReturn(repo);

        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        when(downloadRequestContext.getRequest()).thenReturn(request);

        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());
        RepoResource repoResource = mock(RepoResource.class);
        doReturn(true).when(repoResource).isFound();
        when(repoResource.getRepoPath()).thenReturn(RepoPathFactory.create("repoKey", "path"));
        when(repoResource.getResponseRepoPath()).thenReturn(
                RepoPathFactory.create("responseRepoKey", "responseRepoPath"));

        doReturn(repoResource).when(downloadService).getRepoResource(any(), any(), any());

        doNothing().when(smartRepoService).onFileDownload(any());
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);

        doReturn(isSuccessfulResponse).when(response).isSuccessfulOrRedirect();
        doNothing().when(downloadService).respond(downloadRequestContext, response, repoResource,
                false);
        doNothing().when(downloadService).sendUnifiedAfterEvent(any(), any());
        downloadService.processDownload(request, response);
        verify(downloadService, times(expectedInvocationTimes))
                .sendUnifiedAfterEvent(eq(SupportedUnifiedEvents.AFTER_DOWNLOAD), any());
    }

    public static Stream<Arguments> afterDownloadWorkerTriggeringSource() {
        return Stream.of(
                Arguments.of(false, true, 1),
                Arguments.of(true, false, 0),
                Arguments.of(true, true, 0),
                Arguments.of(false, false, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("afterDownloadErrorSuccessScenarios")
    void sendAfterDownloadErrorUnifiedEvent_shouldUpdateResponseCtx_whenResponseDataIsProvided(
            Integer statusCode, String message, Long size) {
        downloadService.unifiedEventService = unifiedEventService;
        ResponseCtx responseCtx = new ResponseCtx();
        ResponseContextData contextData = ResponseContextData.builder()
                .statusCode(statusCode)
                .message(message)
                .size(size)
                .build();
        DispatcherResponse response = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(contextData)
                .build();

        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenReturn(response);
        downloadService.sendAfterDownloadErrorUnifiedEvent(request, responseCtx);

        assertEquals(message, responseCtx.getMessage());
        verify(unifiedEventService).sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null);
    }

    private static Stream<Arguments> afterDownloadErrorSuccessScenarios() {
        return Stream.of(
                Arguments.of(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal error", null),
                Arguments.of(HttpStatus.SC_BAD_REQUEST, null, 512L),
                Arguments.of(null, "Only message", 4096L)
        );
    }

    @Test
    void notifyDownloadError_shouldRunPluginsAndWorkers() {
        downloadService.unifiedEventService = unifiedEventService;
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenReturn(DispatcherResponse.builder().shouldProceed(true)
                        .data(ResponseContextData.builder().statusCode(HttpStatus.SC_BAD_REQUEST).build())
                        .build());

        DownloadErrorNotificationResult result =
                downloadService.notifyDownloadError(request, HttpStatus.SC_NOT_FOUND, "missing");

        assertEquals(HttpStatus.SC_BAD_REQUEST, result.status());
        verify(unifiedEventService).sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null);
    }

    @Test
    void notifyDownloadError_returnsConflictWhenWorkerStops() {
        downloadService.unifiedEventService = unifiedEventService;
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenThrow(new UnifiedEventException("worker blocked"));

        DownloadErrorNotificationResult result =
                downloadService.notifyDownloadError(request, HttpStatus.SC_NOT_FOUND, "missing");

        assertEquals(HttpStatus.SC_CONFLICT, result.status());
        assertEquals("worker blocked", result.message());
        verify(unifiedEventService).sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null);
    }

    @Test
    void notifyDownloadError_preservesOriginalReasonWhenWorkerLeavesMessageUnset() {
        downloadService.unifiedEventService = unifiedEventService;
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenReturn(DispatcherResponse.builder().shouldProceed(true)
                        .data(ResponseContextData.builder().statusCode(HttpStatus.SC_BAD_REQUEST).build())
                        .build());

        DownloadErrorNotificationResult result =
                downloadService.notifyDownloadError(request, HttpStatus.SC_NOT_FOUND, "original reason");

        assertEquals(HttpStatus.SC_BAD_REQUEST, result.status());
        assertEquals("original reason", result.message());
    }

    @Test
    void sendAfterDownloadErrorUnifiedEvent_shouldNotUpdateResponseCtx_whenResponseDataIsNull() {
        downloadService.unifiedEventService = unifiedEventService;
        ResponseCtx responseCtx = new ResponseCtx();
        DispatcherResponse response = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(null)
                .build();

        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenReturn(response);
        downloadService.sendAfterDownloadErrorUnifiedEvent(request, responseCtx);

        assertEquals(ResponseCtx.UNSET_STATUS, responseCtx.getStatus());
        assertNull(responseCtx.getMessage());
        assertEquals(-1L, responseCtx.getSize());
        verify(unifiedEventService).sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null);
    }

    @Test
    void sendAfterDownloadErrorUnifiedEvent_shouldThrowWorkerStopException_whenUnifiedEventExceptionWithConflict() {
        downloadService.unifiedEventService = unifiedEventService;
        ResponseCtx responseCtx = new ResponseCtx();

        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenThrow(new UnifiedEventException("Conflict error"));

        WorkerStopException exception = assertThrows(WorkerStopException.class,
                () -> downloadService.sendAfterDownloadErrorUnifiedEvent(request, responseCtx));
        assertTrue(exception.getMessage().contains("Conflict error"));
        verify(unifiedEventService).sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null);
    }

    @Test
    void sendAfterDownloadErrorUnifiedEvent_shouldRethrowUnifiedEventException_whenNonConflictStatusCode() {
        downloadService.unifiedEventService = unifiedEventService;
        ResponseCtx responseCtx = new ResponseCtx();
        UnifiedEventException originalException =
                new UnifiedEventException(HttpStatus.SC_BAD_REQUEST, false, "Bad request error");

        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenThrow(originalException);

        UnifiedEventException exception = assertThrows(UnifiedEventException.class,
                () -> downloadService.sendAfterDownloadErrorUnifiedEvent(request, responseCtx));
        assertEquals(originalException, exception);
        verify(unifiedEventService).sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null);
    }

    @Test
    void sendUnifiedAfterEvent_shouldReturnResponse_whenEventIsSentSuccessfully() {
        downloadService.unifiedEventService = unifiedEventService;
        DispatcherResponse expectedResponse = DispatcherResponse.builder()
                .shouldProceed(true)
                .build();
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD, request, null))
                .thenReturn(expectedResponse);

        downloadService.sendUnifiedAfterEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD, request);

        verify(unifiedEventService).sendArtifactEvent(
                SupportedUnifiedEvents.AFTER_DOWNLOAD, request, null);
    }

    @Test
    void sendUnifiedAfterEvent_shouldThrowWorkerStopException_whenUnifiedEventExceptionWithConflictStatusCode() {
        downloadService.unifiedEventService = unifiedEventService;
        UnifiedEventException conflictException =
                new UnifiedEventException(HttpStatus.SC_CONFLICT, false, "Conflict error");
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD, request, null))
                .thenThrow(conflictException);

        WorkerStopException exception = assertThrows(WorkerStopException.class,
                () -> downloadService.sendUnifiedAfterEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD, request));

        assertEquals("Conflict error", exception.getMessage());
        verify(unifiedEventService).sendArtifactEvent(
                SupportedUnifiedEvents.AFTER_DOWNLOAD, request, null);
    }

    @Test
    void sendUnifiedAfterEvent_shouldRethrowUnifiedEventException_whenNonConflictStatusCode() {
        downloadService.unifiedEventService = unifiedEventService;
        UnifiedEventException originalException =
                new UnifiedEventException(HttpStatus.SC_BAD_REQUEST, false, "Bad request error");
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD, request, null))
                .thenThrow(originalException);

        UnifiedEventException exception = assertThrows(UnifiedEventException.class,
                () -> downloadService.sendUnifiedAfterEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD, request));

        assertEquals(originalException, exception);
        verify(unifiedEventService).sendArtifactEvent(
                SupportedUnifiedEvents.AFTER_DOWNLOAD, request, null);
    }
}