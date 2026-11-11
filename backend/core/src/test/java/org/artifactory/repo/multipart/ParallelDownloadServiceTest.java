package org.artifactory.repo.multipart;

import com.jfrog.storage.sonic.DownloadResult;
import com.jfrog.storage.sonic.SonicDownloader;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.NullRequestContext;
import org.artifactory.request.RequestContext;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.service.SmartRepoService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParallelDownloadServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private ParallelDownloadService service;
    private SmartRepoService smartRepoService;
    private RequestContext requestContext;
    private SonicDownloader mockDownloader;
    private DownloadResult mockResult;

    @BeforeEach
    void setUp() {
        service = new ParallelDownloadService();
        smartRepoService = mock(SmartRepoService.class);
        service.setSmartRepoService(smartRepoService);
        setupEnabledWithSmartRepo();

        // Default requestContext with valid attributes
        requestContext = mock(RequestContext.class);
        when(requestContext.getAttribute("fileSize")).thenReturn("1000");
        when(requestContext.getAttribute(ParallelDownloadService.RCD_SUPPORTS_RANGE)).thenReturn("true");

        mockDownloader = mock(SonicDownloader.class);
        mockResult = mock(DownloadResult.class);
    }


    @Test
    void shouldUseParallelDownload_shouldReturnFalse_whenRetrieveInfoIsNull() {
        assertFalse(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker, null));
    }

    @Test
    void shouldUseParallelDownload_shouldReturnFalse_whenDisabled() {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.enabled", "false");

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertFalse(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    @Test
    void shouldUseParallelDownload_shouldReturnFalse_whenFileSizeBelowMinimum() {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.min.file.size.bytes", "1000000");
        when(requestContext.getAttribute("fileSize")).thenReturn("500");

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(500L, null);

        assertFalse(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    @Test
    void shouldUseParallelDownload_shouldReturnTrue_whenAllConditionsMet() {
        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertTrue(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    @Test
    void shouldUseParallelDownload_shouldGetFileSizeFromSupplier_whenNotInContext() {
        when(requestContext.getAttribute("fileSize")).thenReturn(null);
        when(smartRepoService.isSmartRepo("remote-repo")).thenReturn(true);

        RepoResource resource = mockRepoResource(1000L, "abc123sha1");
        @SuppressWarnings("unchecked")
        Supplier<RepoResource> retrieveInfo = mock(Supplier.class);
        when(retrieveInfo.get()).thenReturn(resource);

        assertTrue(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));

        verify(retrieveInfo).get();
        verify(requestContext).setAttribute(ParallelDownloadService.RCD_EXPECTED_SHA1, "abc123sha1");
    }

    @Test
    void shouldUseParallelDownload_shouldReturnFalse_whenSupplierThrowsException() {
        when(requestContext.getAttribute("fileSize")).thenReturn(null);

        Supplier<RepoResource> retrieveInfo = () -> {
            throw new RuntimeException("Failed to retrieve info");
        };

        assertFalse(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    // --- Repo Type Whitelist Tests ---

    @Test
    void shouldUseParallelDownload_shouldAllowAllTypes_whenWhitelistEmpty() {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.allowed.repo.types", "");

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertTrue(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    @ParameterizedTest
    @EnumSource(value = RepoType.class, names = {"Docker", "Maven"})
    void shouldUseParallelDownload_shouldAllowMatchingType_whenInWhitelist(RepoType repoType) {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.allowed.repo.types", "docker, maven");

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertTrue(service.shouldUseParallelDownload(requestContext, "remote-repo", repoType,
                retrieveInfo));
    }

    @Test
    void shouldUseParallelDownload_shouldBlockNonMatchingType_whenNotInWhitelist() {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.allowed.repo.types", "docker");

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertFalse(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Maven,
                retrieveInfo));
    }

    @Test
    void shouldUseParallelDownload_shouldMatchTypeIgnoreCase() {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.allowed.repo.types", "DOCKER");

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertTrue(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    // --- Range Support Tests ---

    @ParameterizedTest
    @CsvSource({
            "false, false",
            "true, true",
            ", false"
    })
    void shouldUseParallelDownload_shouldRespectRangeSupportAttribute(String supportsRange, boolean expected) {
        when(requestContext.getAttribute(ParallelDownloadService.RCD_SUPPORTS_RANGE)).thenReturn(supportsRange);

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        boolean result = service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo);

        assertEquals(expected, result);
    }

    // --- Smart Repo Only Tests ---

    @Test
    void shouldUseParallelDownload_shouldAllow_whenSmartRepoOnlyEnabledAndIsSmartRepo() {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.smart.repo.only", "true");
        when(smartRepoService.isSmartRepo(anyString())).thenReturn(true);

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertTrue(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    @Test
    void shouldUseParallelDownload_shouldBlock_whenSmartRepoOnlyEnabledAndNotSmartRepo() {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.smart.repo.only", "true");
        when(smartRepoService.isSmartRepo(anyString())).thenReturn(false);

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertFalse(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    @Test
    void shouldUseParallelDownload_shouldAllowNonSmartRepo_whenSmartRepoOnlyDisabled() {
        when(smartRepoService.isSmartRepo(anyString())).thenReturn(false);

        Supplier<RepoResource> retrieveInfo = () -> mockRepoResource(1000L, null);

        assertTrue(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                retrieveInfo));
    }

    // --- Destroy Tests ---

    @Test
    void destroy_shouldNotThrow_whenDownloaderIsNull() {
        service.destroy();
    }

    @Test
    void destroy_shouldCloseDownloader_whenDownloaderIsInitialized() {
        service.setDownloader(mockDownloader);

        service.destroy();

        verify(mockDownloader).close();
    }

    // --- Null Request Context Tests ---

    @Test
    void shouldUseParallelDownload_shouldReturnFalse_whenNullRequestContext() {
        NullRequestContext nullContext = new NullRequestContext(RepoPathFactory.create("test-repo", "test/path"));

        assertFalse(service.shouldUseParallelDownload(nullContext, "test-repo", RepoType.Docker,
                () -> mockRepoResource(1000L, null)));
    }

    // --- Disk Space Tests ---

    @Test
    void shouldUseParallelDownload_shouldReturnFalse_whenInsufficientDiskSpace() {
        long hugeSize = Long.MAX_VALUE / 4;
        when(requestContext.getAttribute("fileSize")).thenReturn(String.valueOf(hugeSize));
        homeStub.setProperty("artifactory.remote.concurrent.downloads.min.file.size.bytes", "100");

        assertFalse(service.shouldUseParallelDownload(requestContext, "remote-repo", RepoType.Docker,
                () -> mockRepoResource(hugeSize, null)));
    }

    // --- Download Resource Multipart Tests ---

    @Test
    void downloadResourceMultipart_shouldReturnNull_whenFileSizeNotInContext() {
        when(requestContext.getAttribute("fileSize")).thenReturn(null);

        assertNull(service.downloadResourceMultipart(URI.create("http://example.com/file"),
                requestContext, mock(CloseableHttpClient.class), null));
    }

    @Test
    void downloadResourceMultipart_shouldReturnResult_whenDownloadSucceeds() throws IOException {
        service.setDownloader(mockDownloader);
        when(mockDownloader.download(any(), any(), anyLong(), any())).thenReturn(mockResult);
        when(requestContext.getAttribute(ParallelDownloadService.RCD_EXPECTED_SHA1)).thenReturn(null);

        DownloadResult result = service.downloadResourceMultipart(URI.create("http://example.com/file"),
                requestContext, mock(CloseableHttpClient.class), null);

        assertEquals(mockResult, result);
    }

    @Test
    void downloadResourceMultipart_shouldReturnNull_whenSha1Mismatches() throws IOException {
        service.setDownloader(mockDownloader);
        when(mockDownloader.download(any(), any(), anyLong(), any())).thenReturn(mockResult);
        when(requestContext.getAttribute(ParallelDownloadService.RCD_EXPECTED_SHA1)).thenReturn("expectedSha1");
        when(mockResult.getSha1()).thenReturn("actualSha1");

        DownloadResult result = service.downloadResourceMultipart(URI.create("http://example.com/file"),
                requestContext, mock(CloseableHttpClient.class), null);

        assertNull(result);
        verify(mockResult).close();
    }

    @Test
    void downloadResourceMultipart_shouldReturnNull_whenDownloadThrows() throws IOException {
        service.setDownloader(mockDownloader);
        when(mockDownloader.download(any(), any(), anyLong(), any()))
                .thenThrow(new RuntimeException("download failed"));

        assertNull(service.downloadResourceMultipart(URI.create("http://example.com/file"),
                requestContext, mock(CloseableHttpClient.class), null));
    }

    @Test
    void createDownloader_registersMetrics_whenMeterRegistryAvailable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        service.setMeterRegistry(registry);

        SonicDownloader downloader = service.createDownloader();

        registry.find("remote.concurrent.download.parts.completed").functionCounter().count();

        for (String reason : new String[]{"408", "429", "500", "502", "503", "504"}) {
            registry.find("remote.concurrent.download.parts.retries")
                    .tag("reason", reason).functionCounter().count();
        }

        registry.find("remote.concurrent.download.suspended").gauge().value();

        for (String reason : new String[]{"200", "400", "401", "403", "404", "IOException"}) {
            registry.find("remote.concurrent.download.parts.nonretryable")
                    .tag("reason", reason).functionCounter().count();
        }

        downloader.close();
    }

    @Test
    void createDownloader_skipsMetrics_whenMeterRegistryIsNull() {
        service.setMeterRegistry(null);

        SonicDownloader downloader = service.createDownloader();

        assertNotNull(downloader);
        downloader.close();
    }

    // --- Helper Methods ---

    private void setupEnabledWithSmartRepo() {
        homeStub.setProperty("artifactory.remote.concurrent.downloads.enabled", "true");
        homeStub.setProperty("artifactory.remote.concurrent.downloads.smart.repo.only", "false");
        homeStub.setProperty("artifactory.remote.concurrent.downloads.min.file.size.bytes", "100");
        homeStub.setProperty("artifactory.remote.concurrent.downloads.max.parallel.downloads", "5");
    }

    private RepoResource mockRepoResource(long size, String sha1) {
        RepoResource resource = mock(RepoResource.class);
        when(resource.getSize()).thenReturn(size);
        RepoResourceInfo info = mock(RepoResourceInfo.class);
        when(info.getSha1()).thenReturn(sha1);
        when(resource.getInfo()).thenReturn(info);
        return resource;
    }
}
