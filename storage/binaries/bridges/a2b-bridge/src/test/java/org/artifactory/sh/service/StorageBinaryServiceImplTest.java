package org.artifactory.sh.service;

import com.jfrog.sh.commons.rest.models.CloudInfo;
import com.jfrog.storage.StorageClient;
import com.jfrog.storage.exceptions.StorageServiceException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.StorageException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.fest.assertions.MapAssert;
import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.BinaryServiceImpl;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.common.PostProcessChecksumBinaryStream;
import org.jfrog.storage.binstore.exceptions.federation.FederationClientException;
import org.jfrog.storage.binstore.ifc.ArtificialInputStream;
import org.jfrog.storage.binstore.ifc.BinaryStream;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.jfrog.storage.binstore.ifc.SignedUrl;
import org.jfrog.storage.binstore.ifc.model.BinariesDirInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders;
import org.jfrog.storage.binstore.ifc.model.BinaryElementRequest;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.StorageInfo;
import org.jfrog.storage.binstore.providers.base.PersistedRemoteTask;
import org.jfrog.storage.common.ChecksumType;
import org.jfrog.storage.common.FederationTaskModel;
import org.jfrog.storage.common.prune.PruneRequestModel;
import org.jfrog.storage.common.prune.PruneStatusReport;
import org.jfrog.storage.config.BinaryStoreConfigService;
import org.jfrog.storage.mpu.MultipartUploadInitiateResponse;
import org.jfrog.storage.mpu.MultipartUploadParameters;
import org.jfrog.storage.mpu.entity.MultipartUploadTask;
import org.jfrog.storage.mpu.exceptions.MultipartUploadException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageBinaryServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AccessService accessService;

    @Mock
    private ArtifactoryTracer tracer;

    @Mock
    private StorageClient storageClientMock;

    @Mock
    private StorageClientMetricsProvider storageClientMetricsProvider;

    @Mock
    private BinaryStoreConfigService configService;

    private AutoCloseable autoCloseable;
    private StorageBinaryServiceImpl storageBinaryService;
    private static BinaryElement binaryElement;

    @BeforeAll
    static void forAll() {
        binaryElement = new BinaryElementImpl("sha1", "sha2", "md5", 42L);
    }

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        storageBinaryService =
                new StorageBinaryServiceImpl(accessService, tracer, storageClientMetricsProvider, configService);
        storageBinaryService.storageClient = storageClientMock;
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }


    @Test
    @DisplayName("Should add binary record successfully")
    void shouldAddBinaryRecord() {
        Map<String, String> headers = Map.of(BinaryElementHeaders.REPOSITORY_KEY.getHeaderName(), "repo");

        when(storageClientMock.addBinaryRecord(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(binaryElement);

        BinaryElement result =
                storageBinaryService.addBinaryRecord(headers, binaryElement.getSha1(), binaryElement.getSha2(),
                        binaryElement.getMd5(), binaryElement.getLength());
        assertEquals(binaryElement, result);
    }

    @Test
    public void after() {
        assertDoesNotThrow(() -> storageBinaryService.after());
        assertNotNull(storageBinaryService.storageClient);
    }


    @Test
    @DisplayName("Should delete binary successfully")
    void shouldDeleteBinary() {
        when(storageClientMock.deleteBinary(anyString(), anyBoolean()))
                .thenReturn(true)
                .thenThrow(new StorageServiceException("TEST SSE"));

        boolean result = storageBinaryService.deleteBinary(binaryElement.getSha1());
        assertTrue(result);
        StorageException storageException =
                assertThrows(StorageException.class, () -> storageBinaryService.deleteBinary(binaryElement.getSha1()));
        assertEquals("TEST SSE", storageException.getCause().getMessage());
    }


    @Test
    void isFileExist() {
        when(storageClientMock.exists(anyString())).thenReturn(true);
        boolean result = storageBinaryService.isFileExist(Map.of(), binaryElement.getSha1());
        assertTrue(result);
    }


    @Test
    void createBinaryElement() {
        BinaryElement result =
                storageBinaryService.createBinaryElement(binaryElement.getSha1(), binaryElement.getSha2(),
                        binaryElement.getMd5(), binaryElement.getLength(), Map.of("HeaderKey", "HeaderValue"));
        assertEquals(binaryElement.getSha1(), result.getSha1());
        assertEquals(binaryElement.getSha2(), result.getSha2());
        assertEquals(binaryElement.getMd5(), result.getMd5());
        assertThat(result.getHeaders()).includes(MapAssert.entry("HeaderKey", "HeaderValue"));
    }


    @Test
    void createBinaryStream() {
        BinaryStream result =
                storageBinaryService.createBinaryStream(mock(InputStream.class), Map.of("HeaderKey", "HeaderValue"));
        assertInstanceOf(PostProcessChecksumBinaryStream.class, result);
        InputStream is = mock(ArtificialInputStream.class);
        result = storageBinaryService.createBinaryStream(is, Map.of("HeaderKey", "HeaderValue"));
        assertSame(is, result);
    }


    @Test
    void isCloudProviderSupportingRedirectionConfigured() {
        assertFalse(storageBinaryService.isCloudProviderSupportingRedirectionConfigured());
    }


    @Test
    void canCloudProviderGenerateRedirection() {
        when(storageClientMock.getCloudProviderInfo())
                .thenReturn(new CloudInfo(false, true, true));
        boolean result = storageBinaryService.canCloudProviderGenerateRedirection();
        assertTrue(result);
    }


    @Test
    void isCloudProviderConfigured() {
        when(storageClientMock.getCloudProviderInfo())
                .thenReturn(new CloudInfo(true, true, true));
        boolean result = storageBinaryService.isCloudProviderConfigured();
        assertTrue(result);
    }

    @Test
    void getBinariesDirInfo() {
        when(storageClientMock.getBinariesDirInfo())
                .thenReturn(new BinariesDirInfo(42L, 24L, "path"));
        BinariesDirInfo result = storageBinaryService.getBinariesDirInfo();
        assertNotNull(result);
        assertEquals("path", result.absolutePath());
    }


    @Test
    void isCdnAllowed() {
        ArtifactoryHome.get()
                .getArtifactoryProperties()
                .setProperty(ConstantValues.providerCloudInfoCacheEvictionTime.getPropertyName(), "10");

        when(storageClientMock.getCloudProviderInfo())
                .thenReturn(new CloudInfo(true, true, true));
        boolean result = storageBinaryService.isCdnAllowed();
        assertTrue(result);
    }


    @Test
    @DisplayName("Should retrieve binary record successfully")
    void getBinaryBySha1() {
        InputStream inputStream = mock(InputStream.class);
        when(storageClientMock.getBinary(anyString(), anyMap()))
                .thenReturn(inputStream)
                .thenThrow(new StorageServiceException("TEST SSE"));

        InputStream result = storageBinaryService.getBinary(binaryElement.getSha1(), Map.of());
        assertSame(inputStream, result);
        StorageException storageException = assertThrows(StorageException.class,
                () -> storageBinaryService.getBinary(binaryElement.getSha1(), Map.of()));
        assertEquals("TEST SSE", storageException.getCause().getMessage());
    }


    @Test
    @DisplayName("Should retrieve binary record successfully")
    void getBinaryByBinaryElement() {
        InputStream inputStream = mock(InputStream.class);
        when(storageClientMock.getBinary(any(BinaryElement.class)))
                .thenReturn(inputStream)
                .thenThrow(new StorageServiceException("TEST SSE"));

        InputStream result = storageBinaryService.getBinary(binaryElement);
        assertSame(inputStream, result);
        StorageException storageException =
                assertThrows(StorageException.class, () -> storageBinaryService.getBinary(binaryElement));
        assertEquals("TEST SSE", storageException.getCause().getMessage());
    }

    @Test
    @DisplayName("Should call prune routine successfully")
    void prune() {
        PruneStatusReport pruneStatusReport = new PruneStatusReport();
        when(storageClientMock.prune(any(PruneRequestModel.class)))
                .thenReturn(pruneStatusReport);

        PruneStatusReport result = storageBinaryService.prune(mock(PruneRequestModel.class));
        assertSame(pruneStatusReport, result);
    }

    @Test
    @DisplayName("Should call cleanupMultipartUploads successfully")
    void cleanupMultipartUploads() {
        doNothing().when(storageClientMock).cleanupMultipartUploads();
        assertDoesNotThrow(() -> storageBinaryService.cleanupMultipartUploads());

        doThrow(new MultipartUploadException("TEST MUE")).when(storageClientMock).cleanupMultipartUploads();
        MultipartUploadException exception;
        exception = assertThrows(MultipartUploadException.class, () -> storageBinaryService.cleanupMultipartUploads());
        assertEquals("TEST MUE", exception.getMessage());
    }

    @Test
    @DisplayName("Should call completeMultipartUpload successfully")
    void completeMultipartUpload() {
        doNothing().when(storageClientMock).completeMultipartUpload(anyString(), anyString(), anyString());
        assertDoesNotThrow(() -> storageBinaryService.completeMultipartUpload("id", "/", "sha1"));

        doThrow(new MultipartUploadException("TEST MUE")).when(storageClientMock)
                .completeMultipartUpload(anyString(), anyString(), anyString());
        MultipartUploadException exception;
        exception = assertThrows(MultipartUploadException.class,
                () -> storageBinaryService.completeMultipartUpload("id", "/", "sha1"));
        assertEquals("TEST MUE", exception.getMessage());
    }

    @Test
    @DisplayName("Should call abortMultipartUpload successfully")
    void abortMultipartUpload() {
        doNothing().when(storageClientMock).abortMultipartUpload(anyString(), anyString());
        assertDoesNotThrow(() -> storageBinaryService.abortMultipartUpload("id", "/"));

        doThrow(new MultipartUploadException("TEST MUE")).when(storageClientMock)
                .abortMultipartUpload(anyString(), anyString());
        MultipartUploadException exception;
        exception = assertThrows(MultipartUploadException.class,
                () -> storageBinaryService.abortMultipartUpload("id", "/"));
        assertEquals("TEST MUE", exception.getMessage());
    }

    @Test
    @DisplayName("Should call initiateMultipartUpload successfully")
    void initiateMultipartUpload() {
        MultipartUploadInitiateResponse expected = new MultipartUploadInitiateResponse("repoKey", "/");
        when(storageClientMock.initiateMultipartUpload(anyString(), anyInt())).thenReturn(expected);
        MultipartUploadInitiateResponse result =
                storageBinaryService.initiateMultipartUpload("repoKey", 42);
        assertEquals(expected, result);

        doThrow(new MultipartUploadException("TEST MUE")).when(storageClientMock)
                .initiateMultipartUpload(anyString(), anyInt());
        MultipartUploadException exception = assertThrows(MultipartUploadException.class,
                () -> storageBinaryService.initiateMultipartUpload("repoKey", 42));
        assertEquals("TEST MUE", exception.getMessage());
    }

    @Test
    @DisplayName("Should call generateUploadUrl successfully")
    void generateUploadUrl() {
        String expected = "Preved, medved!";
        when(storageClientMock.generateUploadUrl(anyString(), any(MultipartUploadParameters.class))).thenReturn(
                expected);
        String result = storageBinaryService.generateUploadUrl("repoKey", mock(MultipartUploadParameters.class));
        assertEquals(expected, result);

        doThrow(new MultipartUploadException("TEST MUE"))
                .when(storageClientMock).generateUploadUrl(anyString(), any(MultipartUploadParameters.class));
        MultipartUploadException exception = assertThrows(MultipartUploadException.class,
                () -> storageBinaryService.generateUploadUrl("repoKey", mock(MultipartUploadParameters.class)));
        assertEquals("TEST MUE", exception.getMessage());
    }

    @Test
    @DisplayName("Should call getMultipartUploadStatus successfully")
    void getMultipartUploadStatus() {
        String uploadId = "id";
        Pair<MultipartUploadTask, String> expected = Pair.of(mock(MultipartUploadTask.class), "sha1");
        when(storageClientMock.getMultipartUploadStatus(uploadId)).thenReturn(expected);
        Pair<MultipartUploadTask, String> result = storageBinaryService.getMultipartUploadStatus(uploadId);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Should call isMultipartUploadSupported successfully")
    void isMultipartUploadSupported() {
        doReturn(true).when(storageClientMock).isMultipartUploadSupported();
        boolean result = storageBinaryService.isMultipartUploadSupported();
        assertTrue(result);
    }

    @Test
    @DisplayName("Should call getStorageInfoSummary successfully")
    void getStorageInfoSummary() {
        StorageInfo expected = mock(StorageInfo.class);
        doReturn(expected).when(storageClientMock).getStorageInfoSummary();
        StorageInfo result = storageBinaryService.getStorageInfoSummary();
        assertSame(expected, result);
    }


    @Test
    void getBinaryForChecksumDeploy() {
        when(storageClientMock.searchInFilestore(any(BinaryElement.class), anyBoolean()))
                .thenReturn(binaryElement)
                .thenThrow(new StorageServiceException("TEST SSE"));

        InputStream result = storageBinaryService.getBinaryForChecksumDeploy(binaryElement, true);
        assertTrue(result.getClass().isAssignableFrom(BinaryServiceImpl.BinaryServiceInputStreamWrapper.class));
        StorageException storageException = assertThrows(StorageException.class,
                () -> storageBinaryService.getBinaryForChecksumDeploy(binaryElement, true));
        assertEquals("TEST SSE", storageException.getCause().getMessage());
    }


    @Test
    void getSignedUrl() {
        SignedUrl signedUrl = new SignedUrl("url", RedirectType.S3);
        when(storageClientMock.getBinaryDownloadRedirect(any(BinaryElement.class)))
                .thenReturn(signedUrl)
                .thenThrow(new StorageServiceException("TEST SSE"));

        SignedUrl result = storageBinaryService.getSignedUrl(binaryElement);
        assertSame(signedUrl, result);
        StorageException storageException;
        storageException = assertThrows(StorageException.class, () -> storageBinaryService.getSignedUrl(binaryElement));
        assertEquals("TEST SSE", storageException.getCause().getMessage());
    }


    @Test
    @SuppressWarnings("unchecked")
    void getBinaryProvidersInfo() {
        BinaryProvidersInfo<Map<String, String>> binaryProvidersInfo = mock(BinaryProvidersInfo.class);
        when(storageClientMock.getProviderInfo(false)).thenReturn(binaryProvidersInfo);

        BinaryProvidersInfo<Map<String, String>> result = storageBinaryService.getBinaryProvidersInfo();
        assertSame(binaryProvidersInfo, result);
    }


    @Test
    @SuppressWarnings("unchecked")
    void getFullBinaryProvidersInfo() {
        BinaryProvidersInfo<Map<String, String>> binaryProvidersInfo = mock(BinaryProvidersInfo.class);
        when(storageClientMock.getProviderInfo(true)).thenReturn(binaryProvidersInfo);

        BinaryProvidersInfo<Map<String, String>> result = storageBinaryService.getFullBinaryProvidersInfo();
        assertSame(binaryProvidersInfo, result);
    }


    @Test
    void getBinariesInfo() {
        BinariesInfo binariesInfo = mock(BinariesInfo.class);
        when(storageClientMock.getBinariesInfo())
                .thenReturn(binariesInfo);

        BinariesInfo result = storageBinaryService.getBinariesInfo();
        assertEquals(binariesInfo, result);
    }


    @Test
    void findBinary() {
        when(storageClientMock.searchBinary(anyString(), any(ChecksumType.class))).thenReturn(binaryElement);

        BinaryElement result = storageBinaryService.findBinary(ChecksumType.sha1, "sha1");
        assertEquals(binaryElement, result);
    }


    @Test
    void findBinaries() {
        Set<BinaryElement> aa = Set.of(binaryElement);
        when(storageClientMock.searchBinaries(anySet()))
                .thenReturn(aa)
                .thenThrow(new StorageServiceException("TEST SSE"));

        Set<BinaryElement> result;
        result = storageBinaryService.findBinaries(Set.of("sha1"));
        assertThat(result).isSameAs(aa);

        result = storageBinaryService.findBinaries(Set.of());
        assertThat(result).isEmpty();

        StorageException storageException;
        storageException =
                assertThrows(StorageException.class, () -> storageBinaryService.findBinaries(Set.of("sha1")));
        assertEquals("TEST SSE", storageException.getCause().getMessage());
    }


    @Test
    void getStorageSize() {
        when(storageClientMock.getStorageSize()).thenReturn(42L);

        long result = storageBinaryService.getStorageSize();
        assertEquals(42L, result);
    }


    @Test
    @SneakyThrows(IOException.class)
    void addBinary() {
        when(storageClientMock.addBinary(any(BinaryStream.class))).thenReturn(binaryElement);

        BinaryElement result = storageBinaryService.addBinary(mock(BinaryStream.class));
        assertEquals(binaryElement, result);
    }

    @Test
    @SneakyThrows(IOException.class)
    void addBinaryArtificial() {
        when(storageClientMock.addBinary(any(BinaryStream.class))).thenReturn(binaryElement);

        BinaryStream binaryStream = mock(BinaryStream.class);
        ArtificialInputStream artificialInputStream = mock(ArtificialInputStream.class);
        when(binaryStream.getStream()).thenReturn(artificialInputStream);
        BinaryElement result = storageBinaryService.addBinary(binaryStream);
        assertEquals(binaryElement, result);
    }

    @Test
    @SneakyThrows(IOException.class)
    void addBinaryInputStreamArtificial() {
        when(storageClientMock.addBinary(any(InputStream.class), anyMap())).thenReturn(binaryElement);
        ArtificialInputStream artificialInputStream = mock(ArtificialInputStream.class);
        BinaryElement result = storageBinaryService.addBinary(artificialInputStream, new HashMap<>());
        assertEquals(binaryElement, result);
    }

    @Test
    void shouldInsertRecordInDb() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.insertRecordInDb("sha1", "sha2", "md5", 42L));
    }

    @Test
    void getBinaryProviderManager() {
        assertThrows(UnsupportedOperationException.class, () -> storageBinaryService.getBinaryProviderManager());
    }

    @Test
    void findAllBinaries() {
        assertThrows(UnsupportedOperationException.class, () -> storageBinaryService.findAllBinaries());
    }

    @Test
    void updateSha2ForSha1() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.updateSha2ForSha1("sha1", "sha2"));
    }

    @Test
    void isFileRestoredFromColdStorage() {
        assertNull(storageBinaryService.isFileRestoredFromColdStorage(mock(BinaryElementRequest.class)));
    }

    @Test
    void initiateRestoreFromColdStorage() {
        assertDoesNotThrow(() -> storageBinaryService.initiateRestoreFromColdStorage(mock(BinaryElementRequest.class)));
    }

    @Test
    void getNextRemoteTaskFromDataDb() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.getNextRemoteTaskFromDataDb(24L, 24, mock(Duration.class), 24, 42L));
    }

    @Test
    void getRemoteTasksBatch() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.getRemoteTasksBatch(42L, 24, mock(Duration.class), 24, 42L));
    }

    @Test
    void offerRemoteTask() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.offerRemoteTask(mock(PersistedRemoteTask.class)));
    }

    @Test
    void getTasksBySha1() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.getTasksBySha1("sha1"));
    }

    @Test
    void clusterTopologyChanged() {
        assertDoesNotThrow(() -> storageBinaryService.clusterTopologyChanged(List.of()));
    }

    @Test
    void getSha1ForMissingSha2Count() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.getSha1ForMissingSha2Count());
    }

    @Test
    void getSha1ForMissingSha2() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.getSha1ForMissingSha2(42, 24));
    }

    @Test
    void collectMetrics() {
        assertEquals(List.of(), storageBinaryService.collectMetrics());
    }

    @Test
    void tryToOptimize() {
        assertDoesNotThrow(() -> storageBinaryService.tryToOptimize());
    }

    @Test
    void ping() {
        assertDoesNotThrow(() -> storageBinaryService.ping(false));
        assertDoesNotThrow(() -> storageBinaryService.ping(true));
    }

    @Test
    void getAndManageErrors() {
        assertEquals(List.of(), storageBinaryService.getAndManageErrors());
    }

    @Test
    void updateFederateRepoBinaryProvider() {
        assertDoesNotThrow(() -> storageBinaryService.updateFederateRepoBinaryProvider(List.of(), List.of(),
                mock(AuthenticationTokenProvider.class)));
    }

    @Test
    void getBinariesTasksRepoStatus() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.getBinariesTasksRepoStatus("repoKey", 42L));
    }

    @Test
    void getBinariesTasksStatusPerRepo() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.getBinariesTasksStatusPerRepo(42L));
    }

    @Test
    void getFailingTasks() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.getFailingTasks("repoName", 42, 24));
    }

    @Test
    void replayTasks() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.replayTasks("repoName", "sha1"));
    }

    @Test
    void deleteRepoFailedTasksByBinary() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.deleteRepoFailedTasksByBinary("repoName", "sha1"));
    }

    @Test
    void deleteAllTasksPerRepo() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.deleteAllTasksPerRepo("repoName"));
    }

    @Test
    void contextCreated() {
        assertDoesNotThrow(() -> storageBinaryService.contextCreated());
    }

    @Test
    void contextDestroyed() {
        assertDoesNotThrow(() -> storageBinaryService.contextDestroyed());
    }

    @Test
    void generateUploadUrls() {
        assertThrows(UnsupportedOperationException.class,
                () -> storageBinaryService.generateUploadUrls(24, 42, "path", mock(MultipartUploadParameters.class)));
    }

    @Test
    void testHandleFederatedTaskWithException() {
        FederationTaskModel taskModel = mock(FederationTaskModel.class);
        doThrow(FederationClientException.class).when(storageClientMock).handleFederatedTask(any());
        assertThrows(FederationClientException.class, () -> storageBinaryService.handleFederatedTask(taskModel));
    }

    @Test
    void testHandleFederatedTask() {
        FederationTaskModel taskModel = mock(FederationTaskModel.class);
        doNothing().when(storageClientMock).handleFederatedTask(any());
        assertDoesNotThrow(() -> storageBinaryService.handleFederatedTask(taskModel));
    }

    @Test
    void testIsFederatedV2Activated() {
        assertTrue(storageBinaryService.isFederatedV2Activated());
    }

    @Test
    void testIsNeedOptimization() {
        assertFalse(storageBinaryService.isNeedOptimization());
    }

    @Test
    void testIsRtfsEnabled() {
        assertFalse(storageBinaryService.isRtfsEnabled());
    }
}