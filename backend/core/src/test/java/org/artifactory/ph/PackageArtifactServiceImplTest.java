package org.artifactory.ph;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.ph.acl.exception.PackageCancelException;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageExpiredException;
import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.exception.PackageInternalServerError;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageDownloadContext;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.DownloadService;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.resource.BaseResource;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.rest.exception.ExpiredRestException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.artifactory.util.StringInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_REQUEST_TIMEOUT;
import static org.apache.http.HttpStatus.SC_TOO_MANY_REQUESTS;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@SuppressWarnings({"resource", "unchecked"})
class PackageArtifactServiceImplTest {
    private static final String REPO_KEY = "repoKey";
    private static final String PATH = "path";

    public static final String NOT_FOUND = "not found";

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private DownloadService downloadService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private VirtualRepo virtualRepo;
    private PackageArtifactServiceImpl packageDownloadService;
    private MockedStatic<TrafficContextThreadLocal> trafficContextThreadLocal;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setUp() {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        packageDownloadService = new PackageArtifactServiceImpl(downloadService, repositoryService);
        trafficContextThreadLocal = mockStatic(TrafficContextThreadLocal.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        trafficContextThreadLocal.close();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testDownload() throws PackageException, IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        context.header("key", "val");
        Response response = packageDownloadService.download(context);

        ArgumentCaptor<InternalArtifactoryRequest> captor = ArgumentCaptor.forClass(InternalArtifactoryRequest.class);
        verify(downloadService).process(captor.capture(), any(ArtifactoryResponse.class));
        final List<InternalArtifactoryRequest> params = captor.getAllValues();
        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertEquals("val", params.get(0).getHeaderValues("key").nextElement());
        Assertions.assertFalse(params.get(0).shouldSkipPackageUsageTrack());
    }

    @Test
    void testDownloadWithSkipPackageUsageTrack() throws PackageException, IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        context.setSkipPackageUsageTrack(true);
        Response response = packageDownloadService.download(context);

        ArgumentCaptor<InternalArtifactoryRequest> captor = ArgumentCaptor.forClass(InternalArtifactoryRequest.class);
        verify(downloadService).process(captor.capture(), any(ArtifactoryResponse.class));
        final List<InternalArtifactoryRequest> params = captor.getAllValues();
        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertTrue(params.get(0).shouldSkipPackageUsageTrack());
    }

    @Test
    void testDownloadFormRemoteCache() throws PackageException, IOException {
        LocalCacheRepo localRepo = mock(LocalCacheRepo.class);
        when(localRepo.getKey()).thenReturn("repoKey-cache");
        when(repositoryService.localFederatedOrCachedRepositoryByKey(REPO_KEY)).thenReturn(localRepo);
        PackageDownloadContext context = new PackageDownloadContext(REPO_KEY, PATH);
        context.header("key", "val");
        Response response = packageDownloadService.downloadFromRemoteCache(context);

        ArgumentCaptor<InternalArtifactoryRequest> captor = ArgumentCaptor.forClass(InternalArtifactoryRequest.class);
        verify(downloadService).process(captor.capture(), any(ArtifactoryResponse.class));
        final List<InternalArtifactoryRequest> params = captor.getAllValues();
        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertEquals("val", params.get(0).getHeaderValues("key").nextElement());
    }

    @Test
    void testDownloadForbiddenRestException() throws IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        context.header("key", "val");
        doThrow(new RestException(HttpStatus.SC_FORBIDDEN, "Forbidden")).when(downloadService).process(any(), any());
        PackageException exception =
                Assertions.assertThrows(PackageException.class, () -> packageDownloadService.download(context));
        Assertions.assertEquals(403, exception.getStatus());
        Assertions.assertEquals("Forbidden", exception.getInternalReason());
        Assertions.assertEquals("Forbidden", exception.getPublicReason());
    }

    @Test
    void throwsPackageForbiddenExceptionWhenResourceIsBlocked() {
        BlockedRepoResource blockedRepoResource = mock(BlockedRepoResource.class);
        when(blockedRepoResource.getMessage()).thenReturn("Blocked by policy");
        PackageDownloadContext downloadContext = new PackageDownloadContext(REPO_KEY, PATH);

        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepoMock = createRepoMock(blockedRepoResource);
        RemoteRepoConfig remoteRepoConfigMock = createRepoConfigMock();

        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(remoteRepoMock);
        when(repositoryService.getRepositoryConfigByKey(REPO_KEY)).thenReturn(remoteRepoConfigMock);

        PackageForbiddenException exception = Assertions.assertThrows(
                PackageForbiddenException.class,
                () -> packageDownloadService.getResource(downloadContext)
        );
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, exception.getStatus());
        Assertions.assertEquals("Forbidden", exception.getPublicReason());
        Assertions.assertEquals("Blocked by policy", exception.getInternalReason());
    }

    @ParameterizedTest
    @MethodSource("provideBlockedRepoResourceMessages")
    void throwsPackageForbiddenExceptionWithNullMessage(String message, String path, String expected) {
        BlockedRepoResource blockedRepoResource = mock(BlockedRepoResource.class);
        when(blockedRepoResource.getMessage()).thenReturn(message);
        when(blockedRepoResource.getPath()).thenReturn(path);
        PackageDownloadContext downloadContext = new PackageDownloadContext(REPO_KEY, PATH);

        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepoMock = createRepoMock(blockedRepoResource);
        RemoteRepoConfig remoteRepoConfigMock = createRepoConfigMock();

        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(remoteRepoMock);
        when(repositoryService.getRepositoryConfigByKey(REPO_KEY)).thenReturn(remoteRepoConfigMock);

        PackageForbiddenException exception = Assertions.assertThrows(
                PackageForbiddenException.class,
                () -> packageDownloadService.getResource(downloadContext)
        );
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, exception.getStatus());
        Assertions.assertEquals("Forbidden", exception.getPublicReason());
        Assertions.assertEquals(expected, exception.getInternalReason());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("unexpectedResourceProvider")
    void testUnexpectedResourceType(RepoResource unexpectedResource, String resourceType) {
        MemoryAppender memoryAppender = MemoryAppenderUtil
                .createMemoryAppender(PackageArtifactServiceImpl.class, Level.WARN);
        try {
            PackageDownloadContext downloadContext = new PackageDownloadContext(REPO_KEY, PATH);

            RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepoMock = createRepoMock(unexpectedResource);
            RemoteRepoConfig remoteRepoConfigMock = createRepoConfigMock();

            when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(remoteRepoMock);
            when(repositoryService.getRepositoryConfigByKey(REPO_KEY)).thenReturn(remoteRepoConfigMock);

            BaseResource resource = packageDownloadService.getResource(downloadContext);
            if (unexpectedResource != null) {
                assertInstanceOf(unexpectedResource.getClass(), resource);
            }

            List<ILoggingEvent> messages = memoryAppender.search("", Level.WARN);
            Assertions.assertEquals(0, messages.size());

        } catch (Exception e) {
            Assertions.fail("Exception was thrown for " + resourceType + ": " + e.getMessage(), e);
        } finally {
            memoryAppender.stop();
            ((Logger) LoggerFactory.getLogger(PackageArtifactServiceImpl.class)).detachAppender(memoryAppender);
        }
    }

    @Test
    void testDownloadForbiddenRestExceptionWillPassHeaders() throws IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        context.header("key", "val");
        Map<String, String> headers = Map.of("header", "value");
        doThrow(new RestException(HttpStatus.SC_FORBIDDEN, "Forbidden", headers))
                .when(downloadService).process(any(), any());
        PackageException exception =
                Assertions.assertThrows(PackageException.class, () -> packageDownloadService.download(context));
        Assertions.assertEquals(403, exception.getStatus());
        Assertions.assertEquals("Forbidden", exception.getInternalReason());
        Assertions.assertEquals("Forbidden", exception.getPublicReason());
        Assertions.assertEquals(headers, exception.getAdditionalHeaders());
    }

    @Test
    void testDownloadCancelException() throws IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        context.header("key", "val");
        doThrow(new CancelException("Forbidden", HttpStatus.SC_FORBIDDEN)).when(downloadService).process(any(), any());
        PackageException exception =
                Assertions.assertThrows(PackageCancelException.class, () -> packageDownloadService.download(context));
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, exception.getStatus());
        Assertions.assertEquals("Forbidden", exception.getInternalReason());
        Assertions.assertEquals("Forbidden", exception.getPublicReason());
    }

    @Test
    void testDownloadPackageNotFoundException() throws IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        context.header("key", "val");
        doThrow(new FileExpectedException(null)).when(downloadService).process(any(), any());
        PackageNotFoundException exception = Assertions.assertThrowsExactly(PackageNotFoundException.class,
                () -> packageDownloadService.download(context));
        Assertions.assertEquals(404, exception.getStatus());
    }

    @Test
    void testDownloadForbiddenException() throws IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        context.header("key", "val");
        doThrow(new AuthorizationRestException()).when(downloadService).process(any(), any());
        PackageForbiddenException exception = Assertions.assertThrowsExactly(PackageForbiddenException.class,
                () -> packageDownloadService.download(context));
        Assertions.assertEquals(403, exception.getStatus());
    }

    @Test
    void testDownloadFromRemoteCacheRepoNotExist() {
        PackageDownloadContext context = getPackageDownloadWithContext();
        try {
            packageDownloadService.downloadFromRemoteCache(context);
            Assertions.fail("Expected exception");
        } catch (PackageException e) {
            Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, e.getStatus());
            Assertions.assertEquals("Repo 'repoKey' does not exist or storing artifacts locally is disabled",
                    e.getInternalReason());
        }
    }

    private PackageDownloadContext getPackageDownloadWithContext() {
        return PackageDownloadContext.builder()
                .repoKey(REPO_KEY)
                .path(PATH)
                .build();
    }

    @Test
    void testDownloadFrommRemoteCacheBadInput() {
        PackageDownloadContext context = new PackageDownloadContext("repoKey-cache", "path");
        try {
            packageDownloadService.downloadFromRemoteCache(context);
            Assertions.fail("Expected exception");
        } catch (PackageException e) {
            Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, e.getStatus());
            Assertions.assertEquals(
                    "Download request from remote-cache repo should not include '-cache' in the repo key",
                    e.getInternalReason());
        }
    }

    @Test
    void testDownloadIOException() throws IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        doThrow(new IOException()).when(downloadService).process(any(), any());
        Assertions.assertThrows(PackageInternalServerError.class, () -> packageDownloadService.download(context));
    }

    @Test
    void testGetArtifactFromVirtual() throws PackageException {
        String virtualRepoKey = "virtual";
        String localRepoKey = "local";
        String path = "path";
        RepoPath virtualRepoPath = RepoPathFactory.create(virtualRepoKey, path);
        RepoPath localRepoPath = RepoPathFactory.create(localRepoKey, path);
        @SuppressWarnings("unchecked")
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = Mockito.mock(LocalRepo.class);
        VirtualRepo virtualRepo = Mockito.mock(VirtualRepo.class);
        VirtualRepoConfig virtualRepoConfig = Mockito.mock(VirtualRepoConfig.class);
        Mockito.when(virtualRepoConfig.getKey()).thenReturn(virtualRepoKey);
        RepoResource repoResource = info(localRepoKey, path);
        Mockito.when(virtualRepo.getInfo(any())).thenReturn(repoResource);
        FileInfo fileInfo = mock(FileInfo.class);
        VfsFile<?> file = mock(VfsFile.class);
        Mockito.when(fileInfo.getRepoKey()).thenReturn(localRepoKey);
        Mockito.when(fileInfo.getRepoPath()).thenReturn(virtualRepoPath);
        Mockito.when(fileInfo.getRelPath()).thenReturn(path);
        Mockito.when(file.getRepoKey()).thenReturn(localRepoKey);
        Mockito.when(file.getRepoPath()).thenReturn(localRepoPath);
        Mockito.when(file.getPath()).thenReturn(path);
        Mockito.when(file.getProperties()).thenReturn(new PropertiesImpl());
        Mockito.when(localRepo.getKey()).thenReturn(localRepoKey);
        Mockito.when(localRepo.getImmutableFile(localRepoPath)).thenReturn(file);
        LocalRepoDescriptor localDescriptor = mock(LocalRepoDescriptor.class);
        VirtualRepoDescriptor virtualDescriptor = mock(VirtualRepoDescriptor.class);
        Mockito.when(localDescriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(virtualDescriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(localRepo.getDescriptor()).thenReturn(localDescriptor);
        Mockito.when(virtualRepo.getDescriptor()).thenReturn(virtualDescriptor);
        Mockito.when(repositoryService.storingRepositoryByKey(localRepoKey)).thenReturn(localRepo);
        Mockito.when(repositoryService.repositoryByKey(localRepoKey)).thenReturn(localRepo);
        Mockito.when(repositoryService.repositoryByKey(virtualRepoKey)).thenReturn(virtualRepo);
        Mockito.when(repositoryService.getRepositoryConfigByKey(virtualRepoKey)).thenReturn(virtualRepoConfig);
        Mockito.when(repositoryService.virtualRepositoryByKey(virtualRepoKey)).thenReturn(virtualRepo);
        Mockito.when(repositoryService.getVirtualFileInfo(virtualRepoPath)).thenReturn(fileInfo);
        PackageArtifact artifact = packageDownloadService.getArtifact(virtualRepoKey, path);
        Assertions.assertEquals(localRepoKey, artifact.getRepoKey());
        Assertions.assertEquals(path, artifact.getPath());
    }

    @Test
    void testGetArtifactFromLocal() throws PackageException, IOException {
        String repoKey = "repoKey";
        String path = "path";
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = Mockito.mock(LocalRepo.class);
        LocalRepoConfig localRepoConfig = Mockito.mock(LocalRepoConfig.class);
        VfsFile<?> file = Mockito.mock(VfsFile.class);
        Mockito.when(file.getStream()).thenReturn(new StringInputStream("Test"));
        Mockito.when(localRepo.getImmutableFile(repoPath)).thenReturn(file);
        Mockito.when(localRepo.isReal()).thenReturn(true);
        Mockito.when(localRepoConfig.isReal()).thenReturn(true);
        Mockito.when(localRepo.getKey()).thenReturn(repoKey);
        Mockito.when(localRepoConfig.getKey()).thenReturn(repoKey);
        Mockito.when(file.getRepoKey()).thenReturn(repoKey);
        Mockito.when(file.getRepoPath()).thenReturn(repoPath);
        Mockito.when(file.getPath()).thenReturn(path);
        Mockito.when(file.getProperties()).thenReturn(new PropertiesImpl());
        Mockito.when(localRepo.getImmutableFile(repoPath)).thenReturn(file);
        LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
        Mockito.when(descriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(localRepo.getDescriptor()).thenReturn(descriptor);
        RepoResource info = info(repoKey, path);
        Mockito.when(localRepo.getInfo(any())).thenReturn(info);
        Mockito.when(repositoryService.repositoryByKey(repoKey)).thenReturn(localRepo);
        Mockito.when(repositoryService.getRepositoryConfigByKey(repoKey)).thenReturn(localRepoConfig);
        Mockito.when(repositoryService.storingRepositoryByKey(repoKey)).thenReturn(localRepo);
        Mockito.when(repositoryService.localOrFederatedRepositoryByKey(repoKey)).thenReturn(localRepo);
        PackageArtifact artifact = packageDownloadService.getArtifact(repoKey, path);
        Assertions.assertEquals(repoKey, artifact.getRepoKey());
        Assertions.assertEquals(path, artifact.getPath());
    }

    @Test
    void testExecuteRemoteHttpRequestRemoteNotExists() {
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.executeRemoteHttpRequest("remote", null));
        Assertions.assertEquals("Remote repo remote does not exist", exception.getInternalReason());
        Assertions.assertEquals("Remote repo remote does not exist", exception.getPublicReason());
        Assertions.assertEquals(SC_NOT_FOUND, exception.getStatus());
    }

    @ParameterizedTest
    @MethodSource("testExecuteRemoteHttpRequestRemoteNotAvailableProvider")
    void testExecuteRemoteHttpRequestRemoteNotAvailable(boolean isOffline, boolean isAssumedOffline,
            boolean isBlackedOut) {
        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.isOffline()).thenReturn(isOffline);
        when(remoteRepo.isAssumedOffline()).thenReturn(isAssumedOffline);
        when(remoteRepo.isBlackedOut()).thenReturn(isBlackedOut);
        when(repositoryService.remoteRepositoryByKey("remote")).thenReturn(remoteRepo);
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.executeRemoteHttpRequest("remote", null));
        Assertions.assertEquals("Remote repo remote is offline", exception.getInternalReason());
        Assertions.assertEquals("Remote repo remote is offline", exception.getPublicReason());
        Assertions.assertEquals(SC_NOT_FOUND, exception.getStatus());
    }

    @ParameterizedTest
    @MethodSource("testExecuteRemoteHttpRequestTimeoutProvider")
    void testExecuteRemoteHttpRequestWithTimeout(Class<Exception> exceptionClass) throws IOException, PackageException {
        HttpRepo remoteRepo = mock(HttpRepo.class);
        HttpRequestBase httpRequestBase = mock(HttpRequestBase.class);
        when(remoteRepo.executeMethod(httpRequestBase)).thenThrow(exceptionClass);
        when(repositoryService.remoteRepositoryByKey("remote")).thenReturn(remoteRepo);
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.executeRemoteHttpRequest("remote", httpRequestBase));
        Assertions.assertEquals("Failed to execute http request with repo remote", exception.getInternalReason());
        Assertions.assertEquals("Failed to execute http request with repo remote", exception.getPublicReason());
        Assertions.assertEquals(SC_REQUEST_TIMEOUT, exception.getStatus());
    }

    @Test
    void testExecuteRemoteHttpRequest() throws IOException, PackageException {
        HttpRepo remoteRepo = mock(HttpRepo.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        HttpRequestBase httpRequestBase = mock(HttpRequestBase.class);
        when(remoteRepo.executeMethod(httpRequestBase)).thenReturn(closeableHttpResponse);
        when(repositoryService.remoteRepositoryByKey("remote")).thenReturn(remoteRepo);
        CloseableHttpResponse response = packageDownloadService.executeRemoteHttpRequest("remote", httpRequestBase);
        Assertions.assertEquals(closeableHttpResponse, response);
        verify(remoteRepo, times(1)).executeMethod(any());
    }

    @Test
    void testExecuteRemoteHttpRequestWithTrafficPath() throws IOException, PackageException {
        HttpRepo remoteRepo = mock(HttpRepo.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        HttpRequestBase httpRequestBase = mock(HttpRequestBase.class);
        when(remoteRepo.executeMethod(httpRequestBase)).thenReturn(closeableHttpResponse);
        when(repositoryService.remoteRepositoryByKey("remote")).thenReturn(remoteRepo);
        CloseableHttpResponse response =
                packageDownloadService.executeRemoteHttpRequest("remote", "path", httpRequestBase);
        Assertions.assertEquals(closeableHttpResponse, response);
        verify(remoteRepo, times(1)).executeMethod(httpRequestBase);
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context("remote", "path"));
    }

    @Test
    void testGetArtifactFromRemote() throws PackageException, IOException {
        String remoteRepoKey = "remoteRepoKey";
        String localRepoKey = "localRepoKey";
        String path = "path";
        RepoPath remoteRepoPath = RepoPathFactory.create(remoteRepoKey, path);
        RepoPath localRepoPath = RepoPathFactory.create(localRepoKey, path);
        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepo = Mockito.mock(RemoteRepo.class);
        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = Mockito.mock(LocalRepo.class);
        VfsFile<?> file = Mockito.mock(VfsFile.class);
        Mockito.when(file.getStream()).thenReturn(new StringInputStream("Test"));
        Mockito.when(localRepo.getImmutableFile(localRepoPath)).thenReturn(file);
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        Mockito.when(localRepoDescriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(localRepo.getDescriptor()).thenReturn(localRepoDescriptor);
        Mockito.when(localRepo.getKey()).thenReturn(localRepoKey);
        Mockito.when(remoteRepo.isReal()).thenReturn(true);
        Mockito.when(remoteRepoConfig.isReal()).thenReturn(true);
        Mockito.when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        Mockito.when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        Mockito.when(remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally()).thenReturn(true);
        Mockito.when(remoteRepo.getKey()).thenReturn(remoteRepoKey);
        Mockito.when(remoteRepoConfig.getKey()).thenReturn(remoteRepoKey);
        Mockito.when(file.getRepoKey()).thenReturn(remoteRepoKey);
        Mockito.when(file.getRepoPath()).thenReturn(remoteRepoPath);
        Mockito.when(file.getPath()).thenReturn(path);
        Mockito.when(file.getProperties()).thenReturn(new PropertiesImpl());
        RemoteRepoDescriptor descriptor = mock(RemoteRepoDescriptor.class);
        Mockito.when(descriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(remoteRepo.getDescriptor()).thenReturn(descriptor);
        RepoResource remote = remoteInfo(remoteRepoKey, path);
        RepoResource local = info(remoteRepoKey, path);
        Mockito.when(remoteRepo.getInfo(any())).thenReturn(remote);
        Mockito.when(localRepo.getInfo(any())).thenReturn(local);
        Mockito.when(repositoryService.repositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        Mockito.when(repositoryService.remoteRepositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        Mockito.when(repositoryService.repositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        Mockito.when(repositoryService.getRepositoryConfigByKey(remoteRepoKey)).thenReturn(remoteRepoConfig);
        Mockito.when(repositoryService.storingRepositoryByKey(remoteRepoKey)).thenReturn(localRepo);
        PackageArtifact artifact = packageDownloadService.getArtifact(remoteRepoKey, path);
        Assertions.assertEquals(remoteRepoKey, artifact.getRepoKey());
        Assertions.assertEquals(path, artifact.getPath());
    }

    @Test
    void testGetArtifactFromRemoteNoCache() throws PackageException {
        String remoteRepoKey = "remoteRepoKey";
        String path = "path";
        RemoteRepo<RemoteRepoDescriptor, ?> remoteRepo = mock(RemoteRepo.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        Mockito.when(remoteRepo.isReal()).thenReturn(true);
        Mockito.when(remoteRepo.getKey()).thenReturn(remoteRepoKey);
        Mockito.when(remoteRepoConfig.getKey()).thenReturn(remoteRepoKey);
        Mockito.when(remoteRepoConfig.isReal()).thenReturn(true);
        Mockito.when(remoteRepoConfig.getRepoTypeConfig())
                .thenReturn(Mockito.mock(RemoteRepositoryRepoTypeConfig.class));
        RemoteRepoDescriptor descriptor = mock(RemoteRepoDescriptor.class);
        Mockito.when(descriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(remoteRepo.getDescriptor()).thenReturn(descriptor);
        RepoResource remoteResource = remoteInfo(remoteRepoKey, path);
        MutableRepoResourceInfo info = mock(MutableRepoResourceInfo.class);
        Mockito.when(info.getRepoPath()).thenReturn(RepoPathFactory.create(remoteRepoKey, path));
        when(remoteResource.getInfo()).thenReturn(info);
        Mockito.when(remoteRepo.getInfo(any())).thenReturn(remoteResource);
        Mockito.when(repositoryService.repositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        Mockito.when(repositoryService.remoteRepositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        Mockito.when(repositoryService.getRepositoryConfigByKey(remoteRepoKey)).thenReturn(remoteRepoConfig);
        PackageArtifact artifact = packageDownloadService.getArtifact(remoteRepoKey, path);
        Assertions.assertEquals(remoteRepoKey, artifact.getRepoKey());
        Assertions.assertEquals(path, artifact.getPath());
    }

    @Test
    void testGetStreamFromLocal() throws PackageException, IOException {
        String repoKey = "repoKey";
        String path = "path";
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        LocalRepo<?, ?> localRepo = Mockito.mock(LocalRepo.class);
        LocalRepoConfig localRepoConfig = Mockito.mock(LocalRepoConfig.class);
        VfsFile<?> file = Mockito.mock(VfsFile.class);
        Mockito.when(file.getStream()).thenReturn(new StringInputStream("Test"));
        Mockito.when(localRepo.getImmutableFile(repoPath)).thenReturn(file);
        Mockito.when(localRepo.isReal()).thenReturn(true);
        Mockito.when(localRepo.getKey()).thenReturn(repoKey);
        Mockito.when(localRepoConfig.isReal()).thenReturn(true);
        Mockito.when(localRepoConfig.getKey()).thenReturn(repoKey);
        RepoResource info = info(repoKey, path);
        Mockito.when(localRepo.getInfo(any())).thenReturn(info);
        Mockito.when(repositoryService.repositoryByKey(repoKey)).thenReturn(localRepo);
        Mockito.when(repositoryService.getRepositoryConfigByKey(repoKey)).thenReturn(localRepoConfig);
        Mockito.when(repositoryService.storingRepositoryByKey(repoKey)).thenReturn(localRepo);
        Mockito.when(repositoryService.localOrFederatedRepositoryByKey(repoKey)).thenReturn(localRepo);
        PackageDownloadContext context = new PackageDownloadContext(repoKey, path, "alter");
        context.header("key", "val");
        InputStream stream = packageDownloadService.getStream(context);
        Assertions.assertEquals("Test", IOUtils.toString(stream, StandardCharsets.UTF_8));

        ArgumentCaptor<InternalRequestContext> captor = ArgumentCaptor.forClass(InternalRequestContext.class);
        verify(localRepo).getInfo(captor.capture());
        final List<InternalRequestContext> params = captor.getAllValues();
        Assertions.assertEquals("val", params.get(0).getRequest().getHeaderValues("key").nextElement());
        InternalArtifactoryRequest originRequest = (InternalArtifactoryRequest) params.get(0).getRequest();
        Assertions.assertEquals("alter",
                originRequest.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL));
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void testGetStreamFromRemote() throws PackageException, IOException {
        String remoteRepoKey = "remoteRepoKey";
        String localRepoKey = "localRepoKey";
        String path = "path";
        RepoPath remoteRepoPath = RepoPathFactory.create(remoteRepoKey, path);
        RepoPath localRepoPath = RepoPathFactory.create(localRepoKey, path);
        RemoteRepo<RemoteRepoDescriptor, ?> remoteRepo = mock(RemoteRepo.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        LocalRepo<LocalRepoDescriptor, ?> localRepo = mock(LocalRepo.class);
        VfsFile<?> file = mock(VfsFile.class);
        Mockito.when(file.getStream()).thenReturn(new StringInputStream("Test"));
        Mockito.when(localRepo.getImmutableFile(localRepoPath)).thenReturn(file);
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        Mockito.when(localRepoDescriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(localRepo.getDescriptor()).thenReturn(localRepoDescriptor);
        Mockito.when(localRepo.getKey()).thenReturn(localRepoKey);
        Mockito.when(remoteRepo.isReal()).thenReturn(true);
        Mockito.when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        Mockito.when(remoteRepo.getKey()).thenReturn(remoteRepoKey);
        Mockito.when(remoteRepoConfig.isReal()).thenReturn(true);
        Mockito.when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        Mockito.when(remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally()).thenReturn(true);
        Mockito.when(remoteRepoConfig.getKey()).thenReturn(remoteRepoKey);
        Mockito.when(file.getRepoKey()).thenReturn(remoteRepoKey);
        Mockito.when(file.getRepoPath()).thenReturn(remoteRepoPath);
        Mockito.when(file.getPath()).thenReturn(path);
        Mockito.when(file.getProperties()).thenReturn(new PropertiesImpl());
        RemoteRepoDescriptor descriptor = mock(RemoteRepoDescriptor.class);
        Mockito.when(descriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(remoteRepo.getDescriptor()).thenReturn(descriptor);
        RepoResource remoteResource = remoteInfo(remoteRepoKey, path);
        RepoResource localResource = info(remoteRepoKey, path);
        Mockito.when(remoteRepo.getInfo(any())).thenReturn(remoteResource);
        Mockito.when(localRepo.getInfo(any())).thenReturn(localResource);
        Mockito.when(repositoryService.repositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        Mockito.when(repositoryService.getRepositoryConfigByKey(remoteRepoKey)).thenReturn(remoteRepoConfig);
        Mockito.when(repositoryService.remoteRepositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        Mockito.when(repositoryService.storingRepositoryByKey(remoteRepoKey)).thenReturn(localRepo);
        PackageDownloadContext context = new PackageDownloadContext(remoteRepoKey, path, "alter");
        context.header("key", "val");
        InputStream stream = packageDownloadService.getStream(context);
        Assertions.assertEquals("Test", IOUtils.toString(stream, StandardCharsets.UTF_8));

        ArgumentCaptor<InternalRequestContext> captor = ArgumentCaptor.forClass(InternalRequestContext.class);
        verify(remoteRepo).getInfo(captor.capture());
        final List<InternalRequestContext> params = captor.getAllValues();
        Assertions.assertEquals("val", params.get(0).getRequest().getHeaderValues("key").nextElement());
        InternalArtifactoryRequest originRequest = (InternalArtifactoryRequest) params.get(0).getRequest();
        Assertions.assertEquals("alter",
                originRequest.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL));
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void testGetStreamFromRemoteNoCache() throws PackageException, IOException, RepoRejectException {
        String remoteRepoKey = "remoteRepoKey";
        String path = "path";
        RemoteRepo<RemoteRepoDescriptor, ?> remoteRepo = mock(RemoteRepo.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        Mockito.when(remoteRepo.isReal()).thenReturn(true);
        Mockito.when(remoteRepoConfig.isReal()).thenReturn(true);
        Mockito.when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        Mockito.when(remoteRepo.getKey()).thenReturn(remoteRepoKey);
        Mockito.when(remoteRepoConfig.getKey()).thenReturn(remoteRepoKey);
        RemoteRepoDescriptor descriptor = mock(RemoteRepoDescriptor.class);
        Mockito.when(descriptor.getType()).thenReturn(RepoType.CRAN);
        Mockito.when(remoteRepo.getDescriptor()).thenReturn(descriptor);
        RepoResource remoteResource = remoteInfo(remoteRepoKey, path);
        MutableRepoResourceInfo remoteInfo = mock(MutableRepoResourceInfo.class);
        Mockito.when(remoteInfo.getRepoPath()).thenReturn(RepoPathFactory.create(remoteRepoKey, path));
        when(remoteResource.getInfo()).thenReturn(remoteInfo);
        Mockito.when(remoteRepo.getInfo(any())).thenReturn(remoteResource);
        Mockito.when(repositoryService.repositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        Mockito.when(repositoryService.getRepositoryConfigByKey(remoteRepoKey)).thenReturn(remoteRepoConfig);
        Mockito.when(repositoryService.remoteRepositoryByKey(remoteRepoKey)).thenReturn(remoteRepo);
        ResourceStreamHandle resourceStreamHandle = mock(ResourceStreamHandle.class);
        when(resourceStreamHandle.getInputStream()).thenReturn(new StringInputStream("Test"));
        when(repositoryService.getResourceStreamHandle(any(), eq(remoteRepo), eq(remoteResource))).thenReturn(
                resourceStreamHandle);
        PackageDownloadContext context = new PackageDownloadContext(remoteRepoKey, path, "alter");
        context.header("key", "val");
        InputStream stream = packageDownloadService.getStream(context);
        Assertions.assertEquals("Test", IOUtils.toString(stream, StandardCharsets.UTF_8));

        ArgumentCaptor<InternalRequestContext> captor = ArgumentCaptor.forClass(InternalRequestContext.class);
        verify(remoteRepo).getInfo(captor.capture());
        final List<InternalRequestContext> params = captor.getAllValues();
        Assertions.assertEquals("val", params.get(0).getRequest().getHeaderValues("key").nextElement());
        InternalArtifactoryRequest originRequest = (InternalArtifactoryRequest) params.get(0).getRequest();
        Assertions.assertEquals("alter",
                originRequest.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL));
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void testGetResource() throws IOException, PackageException {
        String repoKey = "repoKey";
        String path = "path";
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        LocalRepo<?, ?> localRepo = Mockito.mock(LocalRepo.class);
        VfsFile<?> file = Mockito.mock(VfsFile.class);
        Mockito.when(file.getStream()).thenReturn(new StringInputStream("Test"));
        RepoResource resource = mock(RepoResource.class);
        Mockito.when(resource.getRepoPath()).thenReturn(repoPath);
        Mockito.when(resource.getRepoKey()).thenReturn(repoKey);
        Mockito.when(resource.getPath()).thenReturn(path);
        Mockito.when(localRepo.getInfo(any())).thenReturn(resource);
        Mockito.when(localRepo.isReal()).thenReturn(true);
        Mockito.when(repositoryService.repositoryByKey(repoKey)).thenReturn(localRepo);
        BaseResource result = packageDownloadService.getResource(new PackageDownloadContext(repoKey, path));
        Assertions.assertEquals(repoKey, result.getRepoKey());
        Assertions.assertEquals(path, result.getPath());
    }

    @ParameterizedTest
    @MethodSource("getTranslateErrorsResponses")
    void testTranslateErrorsToPackageException(int statusCode, PackageException exception)
            throws UnsupportedEncodingException {
        String repoKey = "repoKey";
        String path = "path";
        PackageDownloadContext packageDownloadContext = new PackageDownloadContext(repoKey, path);
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        LocalRepo<?, ?> localRepo = Mockito.mock(LocalRepo.class);
        VfsFile<?> file = Mockito.mock(VfsFile.class);
        Mockito.when(file.getStream()).thenReturn(new StringInputStream("Test"));
        UnfoundRepoResource unfoundRepoResource = mock(UnfoundRepoResource.class);
        Mockito.when(unfoundRepoResource.getRepoPath()).thenReturn(repoPath);
        Mockito.when(unfoundRepoResource.getRepoKey()).thenReturn(repoKey);
        Mockito.when(unfoundRepoResource.getPath()).thenReturn(path);
        Mockito.when(localRepo.getInfo(any())).thenReturn(unfoundRepoResource);
        when(unfoundRepoResource.getStatusCode()).thenReturn(statusCode);
        Mockito.when(localRepo.isReal()).thenReturn(true);
        Mockito.when(repositoryService.repositoryByKey(repoKey)).thenReturn(localRepo);

        assertThrows(exception.getClass(), () -> packageDownloadService.getResource(packageDownloadContext));
    }

    @Test
    void testGetResourceFromVirtualCacheRepoNotFound() {
        try {
            packageDownloadService.getResourceFromVirtualCache("repo1", "a/b/c.json");
            Assertions.fail("Should get bad request");
        } catch (PackageException e) {
            Assertions.assertEquals(HttpStatus.SC_NOT_FOUND, e.getStatus());
        }
    }

    @Test
    void testGetExpiredResourceFromVirtualCache() throws PackageException {
        when(repositoryService.virtualRepositoryByKey(Mockito.eq("repo1"))).thenReturn(virtualRepo);
        RepoResource resource = mock(RepoResource.class);
        when(resource.isFound()).thenReturn(true);
        when(virtualRepo.getInfoFromVirtualCache(any())).thenReturn(resource);
        when(virtualRepo.isCachedResourceExpired(any())).thenReturn(true);
        BaseResource cachedResource = packageDownloadService.getResourceFromVirtualCache("repo1",
                "a/b/c.json");
        Assertions.assertTrue(cachedResource.isExpired(), "Resource should be expired");

    }

    @Test
    void testGetResourceFromVirtualCache() throws PackageException {
        when(repositoryService.virtualRepositoryByKey(Mockito.eq("repo1"))).thenReturn(virtualRepo);
        RepoResource resource = mock(RepoResource.class);
        when(virtualRepo.getInfoFromVirtualCache(any())).thenReturn(resource);
        BaseResource cachedResource = packageDownloadService.getResourceFromVirtualCache("repo1",
                "a/b/c.json");
        Assertions.assertNotNull(cachedResource);
    }

    @Test
    void testGetStreamFromVirtualCacheRepoNotFound() {
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.getStreamFromVirtualCache("virtual", ".security/file"));
        Assertions.assertEquals("Virtual repo virtual does not exist", exception.getInternalReason());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals(SC_NOT_FOUND, exception.getStatus());
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void testGetStreamFromVirtualCacheResourceNotFound() {
        String repoKey = "virtual";
        String path = "path";
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey(repoKey)).thenReturn(virtualRepo);
        when(virtualRepo.getInfoFromVirtualCache(any())).thenReturn(
                new UnfoundRepoResource(null, null));
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.getStreamFromVirtualCache(repoKey, path));
        Assertions.assertEquals("Failed to find artifact in virtual repo:virtual, cache path:path, cause: not found",
                exception.getInternalReason());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals(SC_NOT_FOUND, exception.getStatus());
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void testGetStreamFromVirtualCacheResourceExpired() {
        String repoKey = "virtual";
        String path = "path";
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey(repoKey)).thenReturn(virtualRepo);
        RepoResource expiredResource = mock(RepoResource.class);
        when(expiredResource.isFound()).thenReturn(true);
        when(virtualRepo.isCachedResourceExpired(expiredResource)).thenReturn(true);
        when(virtualRepo.getInfoFromVirtualCache(any())).thenReturn(expiredResource);
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.getStreamFromVirtualCache(repoKey, path));
        Assertions.assertEquals("Failed to find artifact in virtual repo:virtual, cache path:path, cause: not found",
                exception.getInternalReason());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals(SC_NOT_FOUND, exception.getStatus());
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void testGetArtifactFromVirtualCacheRepoNotFound() {
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.getArtifactFromVirtualCache(
                        "virtual", ".security/file"));
        Assertions.assertEquals("Virtual repo virtual does not exist", exception.getInternalReason());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals(SC_NOT_FOUND, exception.getStatus());
    }

    @Test
    void testGetArtifactFromVirtualCache() throws PackageException {
        String repoKey = "virtual";
        String path = "path";
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey(repoKey)).thenReturn(virtualRepo);
        RepoResource resource = mock(RepoResource.class);
        when(resource.isFound()).thenReturn(true);
        VirtualRepoConfig virtualRepoConfig = mock(VirtualRepoConfig.class);
        when(virtualRepoConfig.getPackageType()).thenReturn(RepoType.Npm);
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);
        when(virtualRepo.isCachedResourceExpired(resource)).thenReturn(false);
        when(virtualRepo.getInfoFromVirtualCache(any())).thenReturn(resource);
        RepoResourceInfo resourceInfo = mock(RepoResourceInfo.class);
        when(resource.getInfo()).thenReturn(resourceInfo);
        when(resourceInfo.getRepoPath()).thenReturn(RepoPathFactory.create(repoKey, path));
        PackageArtifact res = packageDownloadService.getArtifactFromVirtualCache(repoKey, path);
        Assertions.assertNotNull(res);
    }

    @Test
    void testGetArtifactFromVirtualCacheResourceExpired() {
        String repoKey = "virtual";
        String path = "path";
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey(repoKey)).thenReturn(virtualRepo);
        RepoResource expiredResource = mock(RepoResource.class);
        when(expiredResource.isFound()).thenReturn(true);
        when(virtualRepo.isCachedResourceExpired(expiredResource)).thenReturn(true);
        when(virtualRepo.getInfoFromVirtualCache(any())).thenReturn(expiredResource);
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.getArtifactFromVirtualCache(
                        repoKey, path));
        Assertions.assertEquals("Failed to find artifact in virtual repo:virtual, cache path:path, cause: not found",
                exception.getInternalReason());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals(SC_NOT_FOUND, exception.getStatus());
    }

    @ParameterizedTest
    @MethodSource("getUnsuccessfulResponses")
    void testUnsuccessfulRepoResourceResponse(RepoResource resource, PackageException exception) {
        String repoKey = "remote";
        String path = "path";
        PackageDownloadContext downloadContext = new PackageDownloadContext(repoKey, path);
        RemoteRepo<RemoteRepoDescriptor, ?> repo = mock(RemoteRepo.class);
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        RemoteRepoDescriptor descriptor = mock(RemoteRepoDescriptor.class);
        when(repositoryService.repositoryByKey(repoKey)).thenReturn(repo);
        when(repositoryService.getRepositoryConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repo.isReal()).thenReturn(true);
        when(repoConfig.isReal()).thenReturn(true);
        when(repo.isLocal()).thenReturn(false);
        when(repoConfig.isLocal()).thenReturn(false);
        when(repo.isStoreArtifactsLocally()).thenReturn(false);
        when(repoConfig.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        when(repo.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getType()).thenReturn(RepoType.Npm);
        if (resource instanceof UnfoundRepoResource unfoundRepoResource) {
            when(unfoundRepoResource.getStatusCode()).thenReturn(SC_NOT_FOUND);
        }
        when(repo.getInfo(any(InternalRequestContext.class))).thenReturn(resource);
        assertThrows(exception.getClass(), () -> packageDownloadService.getArtifact(downloadContext));
    }

    @Test
    void testGetStreamFromVirtualCacheResource() throws PackageException, RepoRejectException, IOException {
        String repoKey = "virtual";
        String path = "path";
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey(repoKey)).thenReturn(virtualRepo);
        RepoResource resource = mock(RepoResource.class);
        when(resource.isFound()).thenReturn(true);
        when(virtualRepo.isCachedResourceExpired(resource)).thenReturn(false);
        when(virtualRepo.getInfoFromVirtualCache(any())).thenReturn(resource);
        ResourceStreamHandle resourceStreamHandle = mock(ResourceStreamHandle.class);
        InputStream inputStream = mock(InputStream.class);
        when(resourceStreamHandle.getInputStream()).thenReturn(inputStream);
        when(repositoryService.getResourceStreamHandle(any(), eq(virtualRepo), eq(resource))).thenReturn(
                resourceStreamHandle);
        InputStream res = packageDownloadService.getStreamFromVirtualCache(repoKey, path);
        Assertions.assertEquals(inputStream, res);
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void testGetStreamFromResourceWithoutRepoResource() {
        BaseResource baseResource = mock(BaseResource.class);
        when(baseResource.getRepoKey()).thenReturn("repo");
        when(baseResource.getPath()).thenReturn("path");

        PackageNotFoundException exception = Assertions.assertThrows(PackageNotFoundException.class,
                () -> packageDownloadService.getStreamFromResource(baseResource));
        Assertions.assertEquals("Failed to get stream from repo:repo, path:path, cause: not found",
                exception.getInternalReason());
        Assertions.assertEquals("Not found", exception.getMessage());
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @SneakyThrows
    @Test
    void testGetStreamFromResourceStoreArtifactsLocally() {
        String repoKey = "repoKey";
        String path = "path";
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = mock(LocalRepo.class);
        LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
        VfsFile<?> file = mock(VfsFile.class);
        when(file.getStream()).thenReturn(new StringInputStream("Test"));
        when(localRepo.getKey()).thenReturn(repoKey);
        when(localRepo.getImmutableFile(repoPath)).thenReturn(file);
        RepoResource repoResource = info(repoKey, path);
        when(repositoryService.repositoryByKey(repoKey)).thenReturn(localRepo);
        when(repositoryService.getRepositoryConfigByKey(repoKey)).thenReturn(localRepoConfig);
        when(repositoryService.storingRepositoryByKey(repoKey)).thenReturn(localRepo);
        InputStream inputStream = packageDownloadService.getStreamFromResource(repoResource);
        Assertions.assertEquals("Test", IOUtils.toString(inputStream, Charset.defaultCharset()));
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @SneakyThrows
    @Test
    void testGetStreamFromResourceRepoNotFound() {
        String repoKey = "repoKey";
        String path = "path";
        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepo = Mockito.mock(RemoteRepo.class);
        when(remoteRepo.getKey()).thenReturn(repoKey);
        when(remoteRepo.isReal()).thenReturn(true);
        when(remoteRepo.isLocal()).thenReturn(false);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        RepoResource repoResource = info(repoKey, path);
        when(repositoryService.repositoryByKey(repoKey)).thenReturn(remoteRepo).thenReturn(null);
        Assertions.assertThrows(PackageNotFoundException.class,
                () -> packageDownloadService.getStreamFromResource(repoResource));
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @SneakyThrows
    @Test
    void testGetStreamFromResourceWithoutStoreArtifactsLocally() {
        String repoKey = "repoKey";
        String path = "path";
        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepo = Mockito.mock(RemoteRepo.class);
        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        when(remoteRepo.getKey()).thenReturn(repoKey);
        when(remoteRepo.isReal()).thenReturn(true);
        when(remoteRepo.isLocal()).thenReturn(false);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepoConfig.getKey()).thenReturn(repoKey);
        when(remoteRepoConfig.isReal()).thenReturn(true);
        when(remoteRepoConfig.isLocal()).thenReturn(false);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        ResourceStreamHandle resourceStreamHandle = mock(ResourceStreamHandle.class);
        when(resourceStreamHandle.getInputStream()).thenReturn(new StringInputStream("Test"));
        when(repositoryService.getResourceStreamHandle(any(), any(), any())).thenReturn(resourceStreamHandle);
        RepoResource repoResource = info(repoKey, path);
        when(repositoryService.repositoryByKey(repoKey)).thenReturn(remoteRepo);
        when(repositoryService.getRepositoryConfigByKey(repoKey)).thenReturn(remoteRepoConfig);
        InputStream inputStream = packageDownloadService.getStreamFromResource(repoResource);
        Assertions.assertEquals("Test", IOUtils.toString(inputStream, Charset.defaultCharset()));
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @SneakyThrows
    @Test
    void testResourceStreamHandleWithUnFoundResource_throwException() {
        String expectedExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause: %s", REPO_KEY, PATH, NOT_FOUND);
        PackageNotFoundException expectedException = new PackageNotFoundException(expectedExceptionMessage);

        RepoResource unfoundRepoResourceMock = mock(UnfoundRepoResource.class);
        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepoMock = mock(RemoteRepo.class);
        RemoteRepoConfig remoteRepoConfigMock = mock(RemoteRepoConfig.class);
        PackageDownloadContext downloadRequestContextMock = new PackageDownloadContext(REPO_KEY, PATH);

        when(unfoundRepoResourceMock.getRepoKey()).thenReturn(REPO_KEY);
        when(unfoundRepoResourceMock.getPath()).thenReturn(PATH);
        when(unfoundRepoResourceMock.isFound()).thenReturn(false);
        when(unfoundRepoResourceMock.isBlocked()).thenReturn(false);
        when(remoteRepoMock.isReal()).thenReturn(true);
        when(remoteRepoConfigMock.isReal()).thenReturn(true);
        when(remoteRepoMock.isLocal()).thenReturn(false);
        when(remoteRepoConfigMock.isLocal()).thenReturn(false);
        when(remoteRepoMock.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepoConfigMock.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        when(remoteRepoMock.getInfo(any())).thenReturn(unfoundRepoResourceMock);
        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(remoteRepoMock);
        when(repositoryService.getRepositoryConfigByKey(REPO_KEY)).thenReturn(remoteRepoConfigMock);

        PackageException actualException = Assertions.assertThrows(PackageNotFoundException.class,
                () -> packageDownloadService.getStream(downloadRequestContextMock));

        assertPackageExceptionDetails(expectedException, actualException);
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }


    @SneakyThrows
    @Test
    void testResourceStreamHandleWithBlockedResource_throwException() {
        String expectedExceptionMessage =
                String.format("Failed to find artifact in repo:%s, path:%s, cause: artifact blocked", REPO_KEY, PATH);
        PackageForbiddenException expectedException =
                new PackageForbiddenException("Forbidden: artifact is blocked", expectedExceptionMessage);

        RepoResource unfoundRepoResourceMock = mock(BlockedRepoResource.class);
        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepoMock = mock(RemoteRepo.class);
        RemoteRepoConfig remoteRepoConfigMock = mock(RemoteRepoConfig.class);
        PackageDownloadContext downloadRequestContextMock = new PackageDownloadContext(REPO_KEY, PATH);

        when(unfoundRepoResourceMock.getRepoKey()).thenReturn(REPO_KEY);
        when(unfoundRepoResourceMock.getPath()).thenReturn(PATH);
        when(unfoundRepoResourceMock.isFound()).thenReturn(true);
        when(unfoundRepoResourceMock.isBlocked()).thenReturn(true);
        when(remoteRepoMock.isReal()).thenReturn(true);
        when(remoteRepoConfigMock.isReal()).thenReturn(true);
        when(remoteRepoMock.isLocal()).thenReturn(false);
        when(remoteRepoConfigMock.isLocal()).thenReturn(false);
        when(remoteRepoMock.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepoConfigMock.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        when(remoteRepoMock.getInfo(any())).thenReturn(unfoundRepoResourceMock);
        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(remoteRepoMock);
        when(repositoryService.getRepositoryConfigByKey(REPO_KEY)).thenReturn(remoteRepoConfigMock);

        PackageException actualException = Assertions.assertThrows(PackageForbiddenException.class,
                () -> packageDownloadService.getStream(downloadRequestContextMock));

        assertPackageExceptionDetails(expectedException, actualException);
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @SneakyThrows
    @Test
    void testResourceStreamHandleWithValidResource() {
        String expectedString = "Valid resource";
        InputStream inputStream = new ByteArrayInputStream(expectedString.getBytes(StandardCharsets.UTF_8));

        RepoResource repoResourceMock = info(REPO_KEY, PATH);
        ResourceStreamHandle resourceStreamHandleMock = mock(ResourceStreamHandle.class);
        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepoMock = Mockito.mock(RemoteRepo.class);
        RemoteRepoConfig remoteRepoConfigMock = Mockito.mock(RemoteRepoConfig.class);
        PackageDownloadContext downloadRequestContextMock = new PackageDownloadContext(REPO_KEY, PATH);

        when(resourceStreamHandleMock.getInputStream()).thenReturn(inputStream);
        when(repositoryService.getResourceStreamHandle(any(), eq(remoteRepoMock), eq(repoResourceMock)))
                .thenReturn(resourceStreamHandleMock);
        when(remoteRepoMock.isReal()).thenReturn(true);
        when(remoteRepoConfigMock.isReal()).thenReturn(true);
        when(remoteRepoMock.isLocal()).thenReturn(false);
        when(remoteRepoConfigMock.isLocal()).thenReturn(false);
        when(remoteRepoMock.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepoConfigMock.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        when(remoteRepoMock.getInfo(any())).thenReturn(repoResourceMock);
        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(remoteRepoMock);
        when(repositoryService.getRepositoryConfigByKey(REPO_KEY)).thenReturn(remoteRepoConfigMock);

        InputStream actual = packageDownloadService.getStream(downloadRequestContextMock);

        Assertions.assertEquals(expectedString, IOUtils.toString(actual, Charset.defaultCharset()));
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void getArtifactRepoNotFound() {
        PackageRepoNotFoundException exception = Assertions.assertThrows(
                PackageRepoNotFoundException.class, () -> packageDownloadService.getArtifact(REPO_KEY, PATH));
        Assertions.assertEquals("Not found", exception.getMessage());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals("Failed to find repository repoKey", exception.getInternalReason());
    }

    @Test
    void getResourceRepoNotFound() {
        PackageDownloadContext packageDownloadContext = mock(PackageDownloadContext.class);
        when(packageDownloadContext.getRepoKey()).thenReturn(REPO_KEY);
        PackageRepoNotFoundException exception = Assertions.assertThrows(
                PackageRepoNotFoundException.class, () -> packageDownloadService.getResource(packageDownloadContext));
        Assertions.assertEquals("Not found", exception.getMessage());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals("Failed to find repository repoKey", exception.getInternalReason());
    }

    @Test
    void getResourceFromVirtualCacheRepoNotExists() {
        PackageRepoNotFoundException exception = Assertions.assertThrows(
                PackageRepoNotFoundException.class,
                () -> packageDownloadService.getResourceFromVirtualCache(REPO_KEY, PATH));
        Assertions.assertEquals("Not found", exception.getMessage());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals("Virtual repo repoKey does not exist", exception.getInternalReason());
    }

    @Test
    void getStreamFromVirtualCacheRepoNotExists() {
        PackageRepoNotFoundException exception = Assertions.assertThrows(
                PackageRepoNotFoundException.class,
                () -> packageDownloadService.getStreamFromVirtualCache(REPO_KEY, PATH));
        Assertions.assertEquals("Not found", exception.getMessage());
        Assertions.assertEquals("Not found", exception.getPublicReason());
        Assertions.assertEquals("Virtual repo repoKey does not exist", exception.getInternalReason());
        trafficContextThreadLocal.verify(() ->
                TrafficContextThreadLocal.setTrafficLogV3Context(anyString(), anyString()));
    }

    @Test
    void downloadWithExpiredRestException() throws IOException {
        doThrow(new ExpiredRestException("message")).when(downloadService).process(any(), any());
        Assertions.assertThrows(PackageExpiredException.class,
                () -> packageDownloadService.download(getPackageDownloadWithContext()));

    }

    @Test
    void downloadWithLoadHealerException() throws IOException {
        PackageDownloadContext context = getPackageDownloadWithContext();
        doThrow(new LoadHealerException("Too many requests")).when(downloadService).process(any(), any());
        PackageException exception = Assertions.assertThrows(PackageException.class,
                () -> packageDownloadService.download(context));
        Assertions.assertEquals(SC_TOO_MANY_REQUESTS, exception.getStatus());
        Assertions.assertEquals(
                "Failed to process download for path:path in repo:repoKey because of LoadHealerException",
                exception.getInternalReason());
        Assertions.assertEquals("Too many requests", exception.getPublicReason());
    }

    private static void assertPackageExceptionDetails(PackageException expectedException,
            PackageException actualException) {
        Assertions.assertEquals(expectedException.getMessage(), actualException.getMessage());
        Assertions.assertEquals(expectedException.getStatus(), actualException.getStatus());
        Assertions.assertEquals(expectedException.getInternalReason(), actualException.getInternalReason());
        Assertions.assertEquals(expectedException.getPublicReason(), actualException.getPublicReason());
    }

    @NonNull
    private RepoResource info(String repoKey, String path) {
        RepoResource info = mock(RepoResource.class);
        Mockito.when(info.getRepoKey()).thenReturn(repoKey);
        Mockito.when(info.getPath()).thenReturn(path);
        Mockito.when(info.isExpired()).thenReturn(false);
        Mockito.when(info.isFound()).thenReturn(true);
        Mockito.when(info.isBlocked()).thenReturn(false);
        Mockito.when(info.getRepoPath()).thenReturn(RepoPathFactory.create(repoKey, path));
        return info;
    }

    @NonNull
    private RepoResource remoteInfo(String repoKey, String path) {
        RemoteRepoResource info = mock(RemoteRepoResource.class);
        Mockito.when(info.getRepoKey()).thenReturn(repoKey);
        Mockito.when(info.getPath()).thenReturn(path);
        Mockito.when(info.isExpired()).thenReturn(false);
        Mockito.when(info.isFound()).thenReturn(true);
        Mockito.when(info.isBlocked()).thenReturn(false);
        Mockito.when(info.getRepoPath()).thenReturn(RepoPathFactory.create(repoKey, path));
        return info;
    }

    private static Stream<Arguments> testExecuteRemoteHttpRequestRemoteNotAvailableProvider() {
        return Stream.of(
                Arguments.of(true, false, false),
                Arguments.of(false, true, false),
                Arguments.of(false, false, true));
    }

    private static Stream<Arguments> testExecuteRemoteHttpRequestTimeoutProvider() {
        return Stream.of(
                Arguments.of(ConnectTimeoutException.class),
                Arguments.of(SocketTimeoutException.class)
        );
    }

    private static Stream<Arguments> getUnsuccessfulResponses() {
        return Stream.of(
                Arguments.of(mock(UnfoundRepoResource.class), new PackageNotFoundException()),
                Arguments.of(mock(BlockedRepoResource.class), new PackageForbiddenException()));
    }

    private static Stream<Arguments> getTranslateErrorsResponses() {
        return Stream.of(
                Arguments.of(500, new PackageException(500, "Internal Server Error", "Internal Server Error")),
                Arguments.of(404, new PackageNotFoundException()),
                Arguments.of(403, new PackageForbiddenException()));
    }

    private static RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> createRepoMock(RepoResource resource) {
        RemoteRepo<RemoteRepoDescriptor, RemoteRepoConfig> remoteRepoMock = mock(RemoteRepo.class);
        when(remoteRepoMock.isReal()).thenReturn(true);
        when(remoteRepoMock.isLocal()).thenReturn(false);
        when(remoteRepoMock.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepoMock.getInfo(any())).thenReturn(resource);
        return remoteRepoMock;
    }

    private static RemoteRepoConfig createRepoConfigMock() {
        RemoteRepoConfig remoteRepoConfigMock = mock(RemoteRepoConfig.class);
        when(remoteRepoConfigMock.isReal()).thenReturn(true);
        when(remoteRepoConfigMock.isLocal()).thenReturn(false);
        when(remoteRepoConfigMock.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        return remoteRepoConfigMock;
    }

    private static Stream<Arguments> provideBlockedRepoResourceMessages() {
        return Stream.of(
                Arguments.of(null, "path/to/resource", "Blocked repo resource for path: path/to/resource"),
                Arguments.of(null, null, "Blocked repo resource without path"),
                Arguments.of("Blocked by policy", "path/to/resource", "Blocked by policy")
        );
    }

    private static Stream<Arguments> unexpectedResourceProvider() {
        return Stream.of(
                Arguments.of(mock(RepoResource.class), "org.artifactory.fs.RepoResource"),
                Arguments.of(null, "null")
        );
    }
}