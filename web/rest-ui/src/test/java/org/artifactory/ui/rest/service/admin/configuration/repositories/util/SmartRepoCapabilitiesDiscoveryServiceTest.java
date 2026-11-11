package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.http.HttpStatus;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.request.UrlVerifier;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.ResearchServiceImpl;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.security.crypto.exception.CryptoRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartRepoCapabilitiesDiscoveryServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryRestRequest<RemoteRepositoryConfigModel> request;
    @Mock
    private ArtifactoryRestResponse<String> response;
    @Mock
    private UrlVerifier urlVerifier;
    @Mock
    private ResearchServiceImpl researchService;
    @Mock
    private PackageSpecification packageSpecification;
    private SmartRepoCapabilitiesDiscoveryService<RemoteRepositoryConfigModel> service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(response.error(anyString())).thenReturn(response);
        service = spy(new SmartRepoCapabilitiesDiscoveryService<>(researchService, urlVerifier, packageSpecification));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testCryptoRuntimeExceptionHandlingFromHttpClient() {
        String url = "https://example.com";
        String urlTrailingSlash = url + "/";

        RemoteRepositoryConfigModel mockRepositoryModel = getRemoteRepositoryConfigModel(url);
        when(request.getImodel()).thenReturn(mockRepositoryModel);
        when(response.responseCode(anyInt())).thenReturn(response);
        when(response.error(anyString())).thenReturn(response);

        try (MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)) {
            pathUtilsMock.when(() -> PathUtils.addTrailingSlash(url))
                    .thenReturn(urlTrailingSlash);

            when(urlVerifier.isRemoteRepoBlocked(urlTrailingSlash, "test")).thenReturn(false);

            doThrow(new CryptoRuntimeException("Decryption failed")).when(service)
                    .getRemoteRepoHttpClient(urlTrailingSlash, mockRepositoryModel);

            service.execute(request, response);

            ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<String> errorMsgCaptor = ArgumentCaptor.forClass(String.class);
            verify(response).responseCode(statusCodeCaptor.capture());
            verify(response).error(errorMsgCaptor.capture());

            assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
            assertEquals("The password could not be decrypted", errorMsgCaptor.getValue());
        }
    }

    @Test
    void testExecute_HappyPath_serializesResearchResponse() {
        String url = "https://example.com/artifactory/docker-local";
        String urlTrailingSlash = url + "/";

        RemoteRepositoryConfigModel model = getRemoteRepositoryConfigModelWithUrl(url, true);
        when(request.getImodel()).thenReturn(model);
        when(response.responseCode(anyInt())).thenReturn(response);
        when(response.iModel(anyString())).thenReturn(response);

        try (MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)) {
            pathUtilsMock.when(() -> PathUtils.addTrailingSlash(url))
                    .thenReturn(urlTrailingSlash);
            when(urlVerifier.isRemoteRepoBlocked(urlTrailingSlash, "test")).thenReturn(false);

            CloseableHttpClient client = mock(CloseableHttpClient.class);
            doReturn(client).when(service).getRemoteRepoHttpClient(urlTrailingSlash, model);

            when(packageSpecification.getPrefixForSmartUrl(RepoType.Docker)).thenReturn("docker");

            ResearchServiceImpl.ArtifactoryCapabilities caps = new ResearchServiceImpl.ArtifactoryCapabilities(
                    ResearchServiceImpl.ResearchResponse.artifactory(), ResearchServiceImpl.RepoInfo.UNKNOWN);
            when(researchService.getArtifactoryCapabilities(urlTrailingSlash, client)).thenReturn(caps);

            service.execute(request, response);

            ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(response).iModel(modelCaptor.capture());
            verify(response).responseCode(statusCaptor.capture());

            String json = modelCaptor.getValue();
            assertEquals(HttpStatus.SC_OK, statusCaptor.getValue());
            ResearchServiceImpl.ResearchResponse researchResponse =
                    JsonUtils.getInstance().readValue(json, ResearchServiceImpl.ResearchResponse.class);
            assertEquals("https://example.com/artifactory/api/docker/docker-local", researchResponse.getFixedUrl());
        }
    }

    @Test
    void testExecute_AutoCorrectUrlDisabled_fixedUrlIsNull() {
        String url = "https://example.com/artifactory/docker-local";
        String urlTrailingSlash = url + "/";

        RemoteRepositoryConfigModel model = getRemoteRepositoryConfigModelWithUrl(url, true);
        when(request.getImodel()).thenReturn(model);
        when(response.responseCode(anyInt())).thenReturn(response);
        when(response.iModel(anyString())).thenReturn(response);

        try (MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)) {
            pathUtilsMock.when(() -> PathUtils.addTrailingSlash(url))
                    .thenReturn(urlTrailingSlash);
            when(urlVerifier.isRemoteRepoBlocked(urlTrailingSlash, "test")).thenReturn(false);

            CloseableHttpClient client = mock(CloseableHttpClient.class);
            doReturn(client).when(service).getRemoteRepoHttpClient(urlTrailingSlash, model);

            getBound().setProperty(ConstantValues.autoCorrectSmartRepoUrl.getPropertyName(), Boolean.FALSE.toString());

            ResearchServiceImpl.ArtifactoryCapabilities caps = new ResearchServiceImpl.ArtifactoryCapabilities(
                    ResearchServiceImpl.ResearchResponse.artifactory(), ResearchServiceImpl.RepoInfo.UNKNOWN);
            when(researchService.getArtifactoryCapabilities(urlTrailingSlash, client)).thenReturn(caps);

            service.execute(request, response);

            ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(response).iModel(modelCaptor.capture());
            verify(response).responseCode(statusCaptor.capture());

            String json = modelCaptor.getValue();
            assertEquals(HttpStatus.SC_OK, statusCaptor.getValue());
            ResearchServiceImpl.ResearchResponse researchResponse =
                    JsonUtils.getInstance().readValue(json, ResearchServiceImpl.ResearchResponse.class);
            assertNull(researchResponse.getFixedUrl());
        } finally {
            getBound().setProperty(ConstantValues.autoCorrectSmartRepoUrl.getPropertyName(),
                    ConstantValues.autoCorrectSmartRepoUrl.getDefValue());
        }
    }

    @Test
    void testExecute_BlockedUrl_returnsBadRequest_andErrorMessage() {
        String url = "https://blocked.example";
        String urlTrailingSlash = url + "/";

        RemoteRepositoryConfigModel model = getRemoteRepositoryConfigModelWithUrl(url, true);
        when(request.getImodel()).thenReturn(model);
        when(response.responseCode(anyInt())).thenReturn(response);
        when(response.error(anyString())).thenReturn(response);

        try (MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)) {
            pathUtilsMock.when(() -> PathUtils.addTrailingSlash(url))
                    .thenReturn(urlTrailingSlash);
            when(urlVerifier.isRemoteRepoBlocked(urlTrailingSlash, "test")).thenReturn(true);

            service.execute(request, response);

            ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            verify(response).responseCode(statusCaptor.capture());
            verify(response).error(errorCaptor.capture());

            assertEquals(HttpStatus.SC_BAD_REQUEST, statusCaptor.getValue());
            assertEquals("Connection failed: URL is blocked", errorCaptor.getValue());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.com, false, Network details was not sent.",
            "'',true, Remote Url was not sent.",
            ",true, Remote Url was not sent."
    })
    void testExecute_MissingModels_returnsBadRequest_andErrorMessage(String url, boolean hasAdvanced,
            String expectedMessage) {
        RemoteRepositoryConfigModel model = getRemoteRepositoryConfigModelWithUrl(url, hasAdvanced);
        when(request.getImodel()).thenReturn(model);
        when(response.responseCode(anyInt())).thenReturn(response);
        when(response.error(anyString())).thenReturn(response);

        service.execute(request, response);

        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).responseCode(statusCaptor.capture());
        verify(response).error(errorCaptor.capture());

        assertEquals(HttpStatus.SC_BAD_REQUEST, statusCaptor.getValue());
        assertEquals(expectedMessage, errorCaptor.getValue());
    }

    @Test
    void testExecute_BasicModelIsNull_returnsBadRequest_andErrorMessage() {
        RemoteRepositoryConfigModel model = mock(RemoteRepositoryConfigModel.class);
        when(model.getAdvanced()).thenReturn(mock(RemoteAdvancedRepositoryConfigModel.class));
        when(model.getBasic()).thenReturn(null);
        when(request.getImodel()).thenReturn(model);
        when(response.responseCode(anyInt())).thenReturn(response);
        when(response.error(anyString())).thenReturn(response);

        service.execute(request, response);

        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).responseCode(statusCaptor.capture());
        verify(response).error(errorCaptor.capture());

        assertEquals(HttpStatus.SC_BAD_REQUEST, statusCaptor.getValue());
        assertEquals("Remote Url was not sent.", errorCaptor.getValue());
    }

    @Test
    void testExecute_WhenHandleResearchRequestThrowsIOException_setsInternalServerError() throws Exception {
        String url = "https://example.com";
        String urlTrailingSlash = url + "/";

        RemoteRepositoryConfigModel model = getRemoteRepositoryConfigModelWithUrl(url, true);
        when(request.getImodel()).thenReturn(model);
        when(response.responseCode(anyInt())).thenReturn(response);

        try (MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)) {
            pathUtilsMock.when(() -> PathUtils.addTrailingSlash(url))
                    .thenReturn(urlTrailingSlash);
            when(urlVerifier.isRemoteRepoBlocked(urlTrailingSlash, "test")).thenReturn(false);

            CloseableHttpClient client = mock(CloseableHttpClient.class);
            try (MockedStatic<RemoteRepositoryProvider> mockedStatic = mockStatic(RemoteRepositoryProvider.class)) {
                mockedStatic.when(() -> RemoteRepositoryProvider.getRemoteRepoHttpClient(anyString(),
                                any(RemoteRepositoryConfigModel.class), anyBoolean()))
                        .thenReturn(client);

                // make handleResearchRequest throw IOException
                doThrow(new IOException("fail")).when(service)
                        .handleResearchRequest(anyString(), any(RepoType.class), any(CloseableHttpClient.class));

                service.execute(request, response);

                ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
                verify(response).responseCode(statusCaptor.capture());
                assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, statusCaptor.getValue());
            }
        }
    }

    @Test
    void testHandleResearchRequest_whenPrefixIsNull_fixedUrlIsNull() throws IOException {
        String url = "https://example.com/artifactory/docker-local";

        when(packageSpecification.getPrefixForSmartUrl(RepoType.Docker)).thenReturn(null);
        ResearchServiceImpl.ArtifactoryCapabilities caps = new ResearchServiceImpl.ArtifactoryCapabilities(
                ResearchServiceImpl.ResearchResponse.artifactory(), ResearchServiceImpl.RepoInfo.UNKNOWN);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(researchService.getArtifactoryCapabilities(url, client)).thenReturn(caps);

        ResearchServiceImpl.ResearchResponse resp =
                service.handleResearchRequest(url, RepoType.Docker, client);
        assertNull(resp.getFixedUrl());
    }

    @Test
    void testHandleResearchRequest_whenUrlToInnerFolder_nullFixedUrl() throws IOException {
        String url = "https://example.com/artifactory/docker-local/some/path";

        when(packageSpecification.getPrefixForSmartUrl(RepoType.Docker)).thenReturn("docker");
        ResearchServiceImpl.ArtifactoryCapabilities caps = new ResearchServiceImpl.ArtifactoryCapabilities(
                ResearchServiceImpl.ResearchResponse.artifactory(), ResearchServiceImpl.RepoInfo.UNKNOWN);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(researchService.getArtifactoryCapabilities(url, client)).thenReturn(caps);

        ResearchServiceImpl.ResearchResponse resp =
                service.handleResearchRequest(url, RepoType.Docker, client);
        assertNull(resp.getFixedUrl());
    }

    private static RemoteRepositoryConfigModel getRemoteRepositoryConfigModel(String url) {
        RemoteRepositoryConfigModel mockRepositoryModel = mock(RemoteRepositoryConfigModel.class);

        GeneralRepositoryConfigModel mockGeneralModel = mock(GeneralRepositoryConfigModel.class);
        RemoteAdvancedRepositoryConfigModel mockAdvancedModel = mock(RemoteAdvancedRepositoryConfigModel.class);
        RemoteBasicRepositoryConfigModel mockBasicModel = mock(RemoteBasicRepositoryConfigModel.class);
        TypeSpecificConfigModel typeSpecificConfigModel = mock(TypeSpecificConfigModel.class);
        when(typeSpecificConfigModel.getRepoType()).thenReturn(RepoType.Docker);

        when(mockRepositoryModel.getGeneral()).thenReturn(mockGeneralModel);
        when(mockRepositoryModel.getAdvanced()).thenReturn(mockAdvancedModel);
        when(mockRepositoryModel.getBasic()).thenReturn(mockBasicModel);
        when(mockRepositoryModel.getTypeSpecific()).thenReturn(typeSpecificConfigModel);
        when(mockBasicModel.getUrl()).thenReturn(url);
        return mockRepositoryModel;
    }

    private static RemoteRepositoryConfigModel getRemoteRepositoryConfigModelWithUrl(String url, boolean hasAdvanced) {
        RemoteRepositoryConfigModel mockRepositoryModel = mock(RemoteRepositoryConfigModel.class);

        GeneralRepositoryConfigModel mockGeneralModel = mock(GeneralRepositoryConfigModel.class);
        RemoteAdvancedRepositoryConfigModel mockAdvancedModel =
                hasAdvanced ? mock(RemoteAdvancedRepositoryConfigModel.class) : null;
        RemoteBasicRepositoryConfigModel mockBasicModel = mock(RemoteBasicRepositoryConfigModel.class);
        TypeSpecificConfigModel typeSpecificConfigModel = mock(TypeSpecificConfigModel.class);
        when(typeSpecificConfigModel.getRepoType()).thenReturn(RepoType.Docker);

        when(mockRepositoryModel.getGeneral()).thenReturn(mockGeneralModel);
        when(mockRepositoryModel.getAdvanced()).thenReturn(mockAdvancedModel);
        when(mockRepositoryModel.getBasic()).thenReturn(mockBasicModel);
        when(mockRepositoryModel.getTypeSpecific()).thenReturn(typeSpecificConfigModel);
        when(mockBasicModel.getUrl()).thenReturn(url);
        return mockRepositoryModel;
    }
}