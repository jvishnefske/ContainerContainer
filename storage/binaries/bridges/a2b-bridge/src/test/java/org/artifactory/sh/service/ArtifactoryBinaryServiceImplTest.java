package org.artifactory.sh.service;

import com.google.common.collect.Maps;
import com.jfrog.storage.exceptions.StorageServiceException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHeaders;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.StringInputStream;
import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.jfrog.storage.binstore.InternalBinaryService;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.common.InternalEmptyInputStream;
import org.jfrog.storage.binstore.common.PostProcessChecksumBinaryStream;
import org.jfrog.storage.binstore.exceptions.BinaryDbException;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.exceptions.federation.FederationClientException;
import org.jfrog.storage.binstore.ifc.ArtificialInputStream;
import org.jfrog.storage.binstore.ifc.BinaryStream;
import org.jfrog.storage.binstore.ifc.model.BinariesDirInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders;
import org.jfrog.storage.binstore.ifc.model.BinaryElementRequest;
import org.jfrog.storage.common.FederationTaskModel;
import org.jfrog.storage.common.prune.PruneRequestModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author TamirH
 */
class ArtifactoryBinaryServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    @Test
    void addEmptyStream() throws IOException {
        final ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(new ArtifactoryBinaryServiceImpl());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        final InternalBinaryService binaryService = mock(InternalBinaryService.class);
        when(binaryService.createBinaryStream(any(), any())).thenAnswer(
                i -> new PostProcessChecksumBinaryStream((InputStream) i.getArguments()[0],
                        (Map<String, String>) i.getArguments()[1]));
        when(binaryService.addBinary(any(), any()))
                .thenAnswer(i ->
                        new BinaryElementImpl("sha1", "sha2", "md5", 3,
                                (i.getArgument(1))));
        artifactoryBinaryService.setBinaryService(binaryService);

        final AddonsManager addonsManager = mock(AddonsManager.class);
        final ArtifactRetentionAddon retentionAddon = mock(ArtifactRetentionAddon.class);
        when(retentionAddon.getCreateBinaryHeaders(anyString())).thenReturn(
                Map.of("X-Artifactory-Archive-Storage-Type", "cold"));
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(retentionAddon);
        artifactoryBinaryService.setAddonsManager(addonsManager);

        final BinaryElement binaryElement = artifactoryBinaryService.addBinary(new InternalEmptyInputStream(),
                new HashMap<>());
        final Map<String, String> resultHeaders = binaryElement.getHeaders();
        assertNotNull(resultHeaders);

        assertEquals(1, resultHeaders.entrySet().size());
        assertTrue(Boolean.parseBoolean(resultHeaders.get(BinaryElementHeaders.INTERNAL_EMPTY_FILE.getHeaderName())));
    }

    @Test
    void testEmptyStreamHeaders() throws IOException {
        final ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(new ArtifactoryBinaryServiceImpl());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        final InternalBinaryService binaryService = mock(InternalBinaryService.class);
        when(binaryService.addBinary(any(), any()))
                .thenAnswer(i ->
                        new BinaryElementImpl("sha1", "sha2", "md5", 3,
                                (i.getArgument(1))));
        artifactoryBinaryService.setBinaryService(binaryService);

        InputStream inputStream = new InternalEmptyInputStream();
        final AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(mock(ArtifactRetentionAddon.class));
        artifactoryBinaryService.setAddonsManager(addonsManager);
        when(binaryService.createBinaryStream(any(), any())).thenAnswer(
                i -> new PostProcessChecksumBinaryStream(i.getArgument(0), i.getArgument(1)));
        final BinaryElement binaryElement = artifactoryBinaryService.addBinary(inputStream, new HashMap<>());
        assertTrue(Boolean.parseBoolean(
                binaryElement.getHeaders().get(BinaryElementHeaders.INTERNAL_EMPTY_FILE.getHeaderName())));
    }

    @ParameterizedTest
    @MethodSource("provideAddBinaryHeaders")
    void addBinary(Map<String, String> headers, List<String> expectedHeaders) throws IOException {
        final ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(new ArtifactoryBinaryServiceImpl());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        final InternalBinaryService binaryService = mock(InternalBinaryService.class);
        when(binaryService.createBinaryStream(any(), any())).thenAnswer(
                i -> new PostProcessChecksumBinaryStream((InputStream) i.getArguments()[0],
                        (Map<String, String>) i.getArguments()[1]));
        when(binaryService.addBinary(any(), any()))
                .thenAnswer(i ->
                        new BinaryElementImpl("sha1", "sha2", "md5", 3,
                                (i.getArgument(1))));
        artifactoryBinaryService.setBinaryService(binaryService);

        final AddonsManager addonsManager = mock(AddonsManager.class);
        final ArtifactRetentionAddon retentionAddon = mock(ArtifactRetentionAddon.class);
        when(retentionAddon.getCreateBinaryHeaders(anyString())).thenReturn(
                Map.of("X-Artifactory-Archive-Storage-Type", "cold"));
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(retentionAddon);
        artifactoryBinaryService.setAddonsManager(addonsManager);

        final BinaryElement binaryElement = artifactoryBinaryService.addBinary(new StringInputStream("test"), headers);
        final Map<String, String> resultHeaders = binaryElement.getHeaders();
        assertNotNull(resultHeaders);

        assertEquals(expectedHeaders.size(), resultHeaders.entrySet().size());
        expectedHeaders.forEach(e -> assertNotNull(resultHeaders.get(e)));
    }

    @Test
    void testGetBinaryBySha() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        when(artifactoryBinaryService.binaryService.getBinary(any(), any())).thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getBinary("test", Map.of());
        verify(artifactoryBinaryService.binaryService, times(1)).getBinary(any(), any());
    }

    @Test
    void testGetBinaryByBinaryElement() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        when(artifactoryBinaryService.binaryService.getBinary(any(BinaryElement.class))).thenReturn(
                StreamUtils.emptyInput());
        artifactoryBinaryService.getBinary(new BinaryElementImpl("sha1", "sha2", "md5", 4));
        verify(artifactoryBinaryService.binaryService, times(1)).getBinary(any(BinaryElement.class));
    }

    @Test
    void testGetBinaryForChecksumDeploy() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getBinaryForChecksumDeploy(new BinaryElementImpl("sha1", "sha2", "md5", 4), true);
        verify(artifactoryBinaryService.binaryService, times(1)).getBinaryForChecksumDeploy(any(BinaryElement.class),
                anyBoolean());
    }

    @Test
    void getSignedUrl() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getSignedUrl(new BinaryElementImpl("sha1", "sha2", "md5", 4));
        verify(artifactoryBinaryService.binaryService, times(1)).getSignedUrl(any(BinaryElement.class));
    }

    @Test
    void testAddBinary() throws IOException {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        ArtifactRetentionAddon artifactRetentionAddon = mock(ArtifactRetentionAddon.class);
        when(artifactRetentionAddon.getCreateBinaryHeaders(anyString())).thenReturn(Maps.newHashMap());
        when(artifactoryBinaryService.addonsManager.addonByType(any())).thenReturn(artifactRetentionAddon);
        artifactoryBinaryService.addBinary(mock(InternalEmptyInputStream.class),
                Maps.newHashMap(Map.of(BinaryElementHeaders.REPOSITORY_KEY.getHeaderName(), "repo")));
        verify(artifactoryBinaryService.binaryService, times(1)).addBinary(any(), any());

    }

    @Test
    void testAddBinaryRecord() {
        final ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        final InternalBinaryService binaryService = mock(InternalBinaryService.class);
        artifactoryBinaryService.setBinaryService(binaryService);
        artifactoryBinaryService.addBinaryRecord(Map.of(), "sha1", "sha2", "md5", 5L);
        verify(binaryService, times(1))
                .addBinaryRecord(any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void testFindAllBinaries() {
        final ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        final InternalBinaryService binaryService = mock(InternalBinaryService.class);
        artifactoryBinaryService.setBinaryService(binaryService);
        artifactoryBinaryService.findAllBinaries();
        verify(binaryService, times(1))
                .findAllBinaries();
    }

    @Test
    void testFindBinary() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.findBinary(ChecksumType.sha1, "sha1");
        verify(artifactoryBinaryService.binaryService, times(1)).findBinary(org.jfrog.storage.common.ChecksumType.sha1,
                "sha1");
    }

    @Test
    void testFindBinaries() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.findBinaries(anyList())).thenReturn(Set.of());
        artifactoryBinaryService.findBinaries(List.of("sha1"));
        verify(artifactoryBinaryService.binaryService, times(1)).findBinaries(List.of("sha1"));
    }

    @Test
    void testFindBinariesEmptyList() {
        final ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        assertTrue(artifactoryBinaryService.findBinaries(null).isEmpty());
    }

    @Test
    void testGetBinaryProvidersInfo() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.getBinaryProvidersInfo();
        verify(artifactoryBinaryService.binaryService, times(1)).getBinaryProvidersInfo();
    }

    @Test
    void testGetFullBinaryProvidersInfo() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getFullBinaryProvidersInfo();
        verify(artifactoryBinaryService.binaryService, times(1)).getFullBinaryProvidersInfo();
    }

    @Test
    void testGetBinariesInfo() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getBinariesInfo();
        verify(artifactoryBinaryService.binaryService, times(1)).getBinariesInfo();
    }

    @Test
    void testGetStorageSize() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doReturn(StreamUtils.emptyInput()).when(artifactoryBinaryService)
                .getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        artifactoryBinaryService.getStorageSize();
        verify(artifactoryBinaryService.binaryService, times(1)).getStorageSize();
    }

    @Test
    void testGetBinariesDirInfo() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinariesDirInfo()).thenReturn(mock(BinariesDirInfo.class));
        BinariesDirInfo binariesDirInfo = artifactoryBinaryService.getBinariesDirInfo();
        assertNotNull(binariesDirInfo);
    }

    @Test
    void testIsCloudProviderSupportingRedirectionConfigured() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.isCloudProviderSupportingRedirectionConfigured();
        verify(artifactoryBinaryService.binaryService, times(1)).isCloudProviderSupportingRedirectionConfigured();
    }

    @Test
    void testGetStorageInfoSummary() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getStorageInfoSummary();
        verify(artifactoryBinaryService.binaryService, times(1)).getStorageInfoSummary();
    }

    @Test
    void testIsCdnAllowed() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.isCdnAllowed();
        verify(artifactoryBinaryService.binaryService, times(1)).isCdnAllowed();
    }

    @Test
    void testCanCloudProviderGenerateRedirection() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.canCloudProviderGenerateRedirection();
        verify(artifactoryBinaryService.binaryService, times(1)).canCloudProviderGenerateRedirection();
    }

    @Test
    void testIsFileRestoredFromColdStorage() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class),
                anyBoolean())).thenReturn(
                StreamUtils.emptyInput());
        artifactoryBinaryService.isFileRestoredFromColdStorage(mock(BinaryElementRequest.class));
        verify(artifactoryBinaryService.binaryService, times(1)).isFileRestoredFromColdStorage(any());
    }

    @Test
    void testInitiateRestoreFromColdStorage() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.initiateRestoreFromColdStorage(mock(BinaryElementRequest.class));
        verify(artifactoryBinaryService.binaryService, times(1)).initiateRestoreFromColdStorage(any());
    }

    @Test
    void testPing() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.ping(true);
        verify(artifactoryBinaryService.binaryService, times(1)).ping(anyBoolean());
    }

    @Test
    void testPrune() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.prune(mock(PruneRequestModel.class));
        verify(artifactoryBinaryService.binaryService, times(1)).prune(any());
    }

    @Test
    void testCreateBinaryElement() {
        final ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        BinaryElement result =
                artifactoryBinaryService.createBinaryElement("sha1", "sha2", "md5", 5, Map.of("key", "value"));
        assertNotNull(result);
        assertEquals("sha1", result.getSha1());
        assertEquals("sha2", result.getSha2());
        assertEquals("md5", result.getMd5());
        assertEquals(5, result.getLength());
        assertEquals("value", result.getHeaders().get("key"));
    }

    @Test
    void testCreateBinaryStream() {
        BinaryStream result =
                ArtifactoryBinaryServiceImpl.createBinaryStream(StreamUtils.emptyInput(), Map.of("key", "value"));
        assertInstanceOf(PostProcessChecksumBinaryStream.class, result);
        assertTrue(result.getHeaders().containsKey("key"));
        assertEquals("value", result.getHeaders().get("key"));

        ArtificialInputStream is = mock(ArtificialInputStream.class);
        result = ArtifactoryBinaryServiceImpl.createBinaryStream(is, Map.of("key", "value"));
        assertSame(is, result);
    }

    @Test
    void testUpdateFederateRepoBinaryProvider() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.updateFederateRepoBinaryProvider(List.of(), List.of(),
                mock(AuthenticationTokenProvider.class));
        verify(artifactoryBinaryService.binaryService, times(1)).updateFederateRepoBinaryProvider(any(), any(), any());
    }

    @Test
    void testUpdateSha2() throws SQLException {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.updateSha2ForSha1("sha1", "sha2");
        verify(artifactoryBinaryService.binaryService, times(1)).updateSha2ForSha1("sha1", "sha2");
    }

    @Test
    void testGetNextRemoteTaskFromDataDb() throws SQLException {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getNextRemoteTaskFromDataDb(3, 2, Duration.ofDays(1L), 3, 3);
        verify(artifactoryBinaryService.binaryService, times(1)).getNextRemoteTaskFromDataDb(3, 2, Duration.ofDays(1L),
                3, 3);
    }


    @Test
    void isCloudProviderConfigured() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.isCloudProviderConfigured();
        verify(artifactoryBinaryService.binaryService, times(1)).isCloudProviderConfigured();
    }

    @Test
    void getBinariesTasksRepoStatus() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class),
                anyBoolean())).thenReturn(
                StreamUtils.emptyInput());
        artifactoryBinaryService.getBinariesTasksRepoStatus("repo", 3L);
        verify(artifactoryBinaryService.binaryService, times(1)).getBinariesTasksRepoStatus("repo", 3L);
    }

    @Test
    void getBinariesTasksStatusPerRepo() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getBinariesTasksStatusPerRepo();
        verify(artifactoryBinaryService.binaryService, times(1)).getBinariesTasksStatusPerRepo(anyLong());
    }

    @Test
    void getAndManageErrors() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class), anyBoolean()))
                .thenReturn(StreamUtils.emptyInput());
        artifactoryBinaryService.getAndManageErrors();
        verify(artifactoryBinaryService.binaryService, times(1)).getAndManageErrors();
    }

    @Test
    void getSha1ForMissingSha2Count() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class),
                anyBoolean())).thenReturn(
                StreamUtils.emptyInput());
        artifactoryBinaryService.getSha1ForMissingSha2Count();
        verify(artifactoryBinaryService.binaryService, times(1)).getSha1ForMissingSha2Count();
    }

    @Test
    void getSha1ForMissingSha2() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.getSha1ForMissingSha2(1, 2);
        verify(artifactoryBinaryService.binaryService, times(1)).getSha1ForMissingSha2(1, 2);
    }

    @Test
    void testGetFailingTasks() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.getFailingTasks("repo", 3, 3);
        verify(artifactoryBinaryService.binaryService, times(1)).getFailingTasks("repo", 3, 3);
    }

    @Test
    void testReplayTasks() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.replayTasks("repo", "sha1");
        verify(artifactoryBinaryService.binaryService, times(1)).replayTasks("repo", "sha1");
    }

    @Test
    void deleteAllTasksPerRepo() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        artifactoryBinaryService.deleteAllTasksPerRepo("repo");
        verify(artifactoryBinaryService.binaryService, times(1)).deleteAllTasksPerRepo("repo");
    }

    @Test
    void testTryToOptimize() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.tryToOptimize();
        verify(artifactoryBinaryService.binaryService, times(1)).tryToOptimize();
    }


    @Test
    void testDeleteBinary() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.deleteBinary("sha1");
        verify(artifactoryBinaryService.binaryService, times(1)).deleteBinary("sha1");
    }

    @Test
    void testContextCreated() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.onContextCreated();
        verify(artifactoryBinaryService.binaryService, times(1)).contextCreated();
    }


    @Test
    void clusterTopologyChanged() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.clusterTopologyChanged(
                List.of(new ArtifactoryServer("id", 1, "contextUrl",
                        8080, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.MEMBER, 3,
                        "7.79.0", 1234567890, 123456789,
                        ArtifactoryRunningMode.HA, "sdfghjkl")));
        verify(artifactoryBinaryService.binaryService, times(1)).clusterTopologyChanged(any());
    }

    @ParameterizedTest
    @MethodSource("ioStorageException")
    void testGetBinaryByShaError(Class<? extends Exception> exceptionToThrow,
            Class<? extends Exception> expectedException) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        when(artifactoryBinaryService.binaryService.getBinary("sha1", Map.of())).thenThrow(exceptionToThrow);
        assertThrows(expectedException, () -> artifactoryBinaryService.getBinary("sha1", Map.of()));
    }

    @ParameterizedTest
    @MethodSource("ioStorageException")
    void testGetBinaryElementError(Class<? extends Exception> exceptionToThrow,
            Class<? extends Exception> expectedException) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        when(artifactoryBinaryService.binaryService.getBinary(any(BinaryElement.class))).thenThrow(exceptionToThrow);
        assertThrows(expectedException,
                () -> artifactoryBinaryService.getBinary(
                        new BinaryElementImpl("sha1", "sha2", "md5", 3)));
    }

    @ParameterizedTest
    @MethodSource("ioStorageDbException")
    void testGetBinaryForChecksumDeployError(Class<? extends Exception> exceptionToThrow) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = spy(prepareArtifactoryBinaryService());
        doNothing().when(artifactoryBinaryService).addFederatedHeaders(anyMap());
        when(artifactoryBinaryService.binaryService.getBinaryForChecksumDeploy(any(BinaryElement.class),
                anyBoolean())).thenThrow(exceptionToThrow);
        assertThrows(StorageException.class,
                () -> artifactoryBinaryService.getBinaryForChecksumDeploy(
                        new BinaryElementImpl("sha1", "sha2", "md5", 4), true));
    }

    @ParameterizedTest
    @MethodSource("ioStorageException")
    void testGetSignedUrlError(Class<? extends Exception> exceptionToThrow,
            Class<? extends Exception> expectedException) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getSignedUrl(any(BinaryElement.class))).thenThrow(exceptionToThrow);
        assertThrows(expectedException,
                () -> artifactoryBinaryService.getSignedUrl(
                        new BinaryElementImpl("sha1", "sha2", "md5", 4)));
    }

    @ParameterizedTest
    @MethodSource("dbStorageException")
    void testFindBinaryError(Class<? extends Exception> exceptionToThrow) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.findBinary(any(), anyString())).thenThrow(exceptionToThrow);
        assertThrows(StorageException.class,
                () -> artifactoryBinaryService.findBinary(ChecksumType.sha1, "sha"));
    }

    @ParameterizedTest
    @MethodSource("dbStorageException")
    void testGetBinariesInfoError(Class<? extends Exception> exceptionToThrow) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.getBinariesInfo()).thenThrow(exceptionToThrow);
        assertThrows(StorageException.class, artifactoryBinaryService::getBinariesInfo);
    }

    @Test
    void testStorageSizeError() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.getStorageSize()).thenThrow(BinaryDbException.class);
        assertThrows(StorageException.class, artifactoryBinaryService::getStorageSize);
    }

    @Test
    void testPruneError() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.prune(any())).thenThrow(org.jfrog.storage.StorageException.class);
        assertThrows(StorageException.class,
                () -> artifactoryBinaryService.prune(mock(PruneRequestModel.class)));
    }

    @Test
    void testUpdateSha2ForSha1Error() throws SQLException {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.updateSha2ForSha1(anyString(), anyString()))
                .thenThrow(BinaryDbException.class);
        assertThrows(StorageException.class,
                () -> artifactoryBinaryService.updateSha2ForSha1("sha1", "sha2"));
    }

    @ParameterizedTest
    @MethodSource("ioStorageDbException")
    void testFindBinariesError(Class<? extends Exception> exceptionToThrow) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.findBinaries(any())).thenThrow(exceptionToThrow);
        assertThrows(StorageException.class,
                () -> artifactoryBinaryService.findBinaries(List.of("sha")));
    }

    @Test
    void testIsNeedOptimization() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.isNeedOptimization()).thenReturn(true);
        assertTrue(artifactoryBinaryService.isNeedOptimization());
    }

    @Test
    void testHandleFederatedTaskWithException() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        FederationTaskModel taskModel = mock(FederationTaskModel.class);
        doThrow(FederationClientException.class).when(artifactoryBinaryService.binaryService)
                .handleFederatedTask(any());
        assertThrows(FederationClientException.class, () -> artifactoryBinaryService.handleFederatedTask(taskModel));
    }

    @Test
    void testHandleFederatedTask() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        FederationTaskModel taskModel = mock(FederationTaskModel.class);
        doNothing().when(artifactoryBinaryService.binaryService).handleFederatedTask(any());
        assertDoesNotThrow(() -> artifactoryBinaryService.handleFederatedTask(taskModel));
    }

    @Test
    void testIsFederatedV2Activated() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        when(artifactoryBinaryService.binaryService.isFederatedV2Activated()).thenReturn(true);
        assertTrue(artifactoryBinaryService.isFederatedV2Activated());
    }

    @Test
    void testIsRtfsEnabled() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        artifactoryBinaryService.isRtfsEnabled();
    }

    @Test
    void testAddFederatedHeadersWithRtfsDisabled() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get)
                    .thenAnswer(invocation -> {
                        ArtifactoryContext context = mock(ArtifactoryContext.class);
                        RepositoryConfigService configService = mock(RepositoryConfigService.class);
                        when(context.getRepositoryConfigService()).thenReturn(configService);
                        when(configService.existsByType(RepoDetailsType.FEDERATED, "repo")).thenReturn(true);
                        return context;
                    });
            ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
            when(artifactoryBinaryService.binaryService.isFederatedV2Activated()).thenReturn(true);
            when(artifactoryBinaryService.binaryService.isRtfsEnabled()).thenReturn(false);
            Map<String, String> headers = new HashMap<>();
            headers.put(BinaryElementHeaders.REPOSITORY_KEY.getHeaderName(), "repo");
            artifactoryBinaryService.addFederatedHeaders(headers);
            assertEquals(true,
                    Boolean.parseBoolean(headers.get(BinaryElementHeaders.FEDERATED_REPO.getHeaderName())));
            assertEquals(true, Boolean.parseBoolean(headers.get(BinaryElementHeaders.SKIP_FEDERATED.getHeaderName())));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addFederatedHeaders(boolean result) {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get)
                    .thenAnswer(invocation -> {
                        ArtifactoryContext context = mock(ArtifactoryContext.class);
                        RepositoryConfigService configService = mock(RepositoryConfigService.class);
                        when(context.getRepositoryConfigService()).thenReturn(configService);
                        when(configService.existsByType(RepoDetailsType.FEDERATED, "repo")).thenReturn(result);
                        return context;
                    });
            ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
            Map<String, String> headers = new HashMap<>();
            headers.put(BinaryElementHeaders.REPOSITORY_KEY.getHeaderName(), "repo");
            artifactoryBinaryService.addFederatedHeaders(headers);
            assertEquals(result,
                    Boolean.parseBoolean(headers.get(BinaryElementHeaders.FEDERATED_REPO.getHeaderName())));
        }
    }

    @Test
    void addRangeHeaders_shouldNotAddHeaders_whenRequestIsNull() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        Map<String, String> headers = new HashMap<>();
        homeStub.setProperty(ConstantValues.httpRangeSupport, "true");

        try (MockedStatic<RequestThreadLocal> requestMock = mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequest).thenReturn(null);
            artifactoryBinaryService.addRangeHeaders(headers);

            assertTrue(headers.isEmpty());
        }
    }

    @Test
    void addRangeHeaders_shouldNotAddHeaders_whenHttpRangeSupportIsDisabled() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        Map<String, String> headers = new HashMap<>();
        homeStub.setProperty(ConstantValues.httpRangeSupport, "false");

        try (MockedStatic<RequestThreadLocal> requestMock = mockStatic(RequestThreadLocal.class)) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.RANGE)).thenReturn("bytes=0-100");
            requestMock.when(RequestThreadLocal::getRequest).thenReturn(request);
            artifactoryBinaryService.addRangeHeaders(headers);

            assertTrue(headers.isEmpty());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void addRangeHeaders_shouldNotAddHeaders_whenRangeHeaderIsBlankOrNull(String rangeValue) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        Map<String, String> headers = new HashMap<>();
        homeStub.setProperty(ConstantValues.httpRangeSupport, "true");

        try (MockedStatic<RequestThreadLocal> requestMock = mockStatic(RequestThreadLocal.class)) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.RANGE)).thenReturn(rangeValue);
            requestMock.when(RequestThreadLocal::getRequest).thenReturn(request);
            artifactoryBinaryService.addRangeHeaders(headers);

            assertTrue(headers.isEmpty());
        }
    }

    @Test
    void addRangeHeaders_shouldAddRangeHeader_whenRangeHeaderPresent() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        Map<String, String> headers = new HashMap<>();
        homeStub.setProperty(ConstantValues.httpRangeSupport, "true");

        try (MockedStatic<RequestThreadLocal> requestMock = mockStatic(RequestThreadLocal.class)) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.RANGE)).thenReturn("bytes=0-100");
            when(request.getHeader(HttpHeaders.IF_RANGE)).thenReturn(null);
            requestMock.when(RequestThreadLocal::getRequest).thenReturn(request);
            artifactoryBinaryService.addRangeHeaders(headers);

            assertEquals(1, headers.size());
            assertEquals("bytes=0-100", headers.get(HttpHeaders.RANGE));
        }
    }

    @Test
    void addRangeHeaders_shouldAddBothHeaders_whenRangeAndIfRangeHeadersPresent() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        Map<String, String> headers = new HashMap<>();
        homeStub.setProperty(ConstantValues.httpRangeSupport, "true");

        try (MockedStatic<RequestThreadLocal> requestMock = mockStatic(RequestThreadLocal.class)) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.RANGE)).thenReturn("bytes=100-200");
            when(request.getHeader(HttpHeaders.IF_RANGE)).thenReturn("\"abc123\"");
            requestMock.when(RequestThreadLocal::getRequest).thenReturn(request);
            artifactoryBinaryService.addRangeHeaders(headers);

            assertEquals(2, headers.size());
            assertEquals("bytes=100-200", headers.get(HttpHeaders.RANGE));
            assertEquals("\"abc123\"", headers.get(HttpHeaders.IF_RANGE));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void addRangeHeaders_shouldAddOnlyRangeHeader_whenIfRangeHeaderIsBlankOrNull(String ifRangeValue) {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = prepareArtifactoryBinaryService();
        Map<String, String> headers = new HashMap<>();
        homeStub.setProperty(ConstantValues.httpRangeSupport, "true");

        try (MockedStatic<RequestThreadLocal> requestMock = mockStatic(RequestThreadLocal.class)) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.RANGE)).thenReturn("bytes=500-999");
            when(request.getHeader(HttpHeaders.IF_RANGE)).thenReturn(ifRangeValue);
            requestMock.when(RequestThreadLocal::getRequest).thenReturn(request);
            artifactoryBinaryService.addRangeHeaders(headers);

            assertEquals(1, headers.size());
            assertEquals("bytes=500-999", headers.get(HttpHeaders.RANGE));
        }
    }

    private static Stream<Arguments> dbStorageException() {
        return Stream.of(
                Arguments.of(BinaryDbException.class),
                Arguments.of(StorageServiceException.class));
    }

    private static Stream<Arguments> ioStorageException() {
        return Stream.of(
                Arguments.of(BinaryNotFoundException.class, BinaryNotFoundException.class),
                Arguments.of(StorageException.class, StorageException.class),
                Arguments.of(StorageServiceException.class, StorageException.class));
    }

    private static Stream<Arguments> ioStorageDbException() {
        return Stream.of(
                Arguments.of(BinaryDbException.class),
                Arguments.of(StorageServiceException.class));
    }

    @Nonnull
    private static ArtifactoryBinaryServiceImpl prepareArtifactoryBinaryService() {
        ArtifactoryBinaryServiceImpl artifactoryBinaryService = new ArtifactoryBinaryServiceImpl();
        artifactoryBinaryService.setAddonsManager(mock(AddonsManager.class));
        InternalBinaryService binaryService = mock(InternalBinaryService.class);
        artifactoryBinaryService.setBinaryService(binaryService);
        return artifactoryBinaryService;
    }

    // @formatter:off
    private static Stream<Arguments> provideAddBinaryHeaders() {
        return Stream.of(
                Arguments.of(Map.of(), List.of()),
                Arguments.of(new HashMap<String, String>() {{put("X-Artifactory-repositoryKey", "testRepo");}},
                        List.of("X-Artifactory-repositoryKey", "X-Artifactory-Archive-Storage-Type"))
        );
    }
}