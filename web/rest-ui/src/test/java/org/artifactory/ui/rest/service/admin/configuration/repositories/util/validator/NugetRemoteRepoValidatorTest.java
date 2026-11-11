package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.service.NugetRepoConfigValidator;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.RemoteRepositoryProvider;
import org.jfrog.client.util.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author arkojd
 */
class NugetRemoteRepoValidatorTest {

    private NugetRemoteRepoValidator nugetRemoteRepoValidator;

    @Mock
    private RemoteRepoConfig mockRepoConfig;

    @Mock
    private RemoteRepositoryRepoTypeConfig mockRepoTypeConfig;

    @Mock
    private RemoteRepositoryConfigModel model;
    AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        nugetRemoteRepoValidator = new NugetRemoteRepoValidator();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void validateToNotThrowException() throws IOException {
        try (MockedStatic<RemoteRepositoryProvider> mockedStatic = mockStatic(
                RemoteRepositoryProvider.class); MockedStatic<EntityUtils> utilsMockedStatic = mockStatic(
                        EntityUtils.class);
                MockedStatic<NugetRepoConfigValidator> nugetRepoConfigValidatorMockedStatic =
                        mockStatic(NugetRepoConfigValidator.class)) {
            CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
            CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
            mockedStatic.when(() -> RemoteRepositoryProvider.getRemoteRepoHttpClient(anyString(),
                            any(RemoteRepositoryConfigModel.class), anyBoolean()))
                    .thenReturn(httpClient);
            when(httpClient.execute(any())).thenReturn(response);
            nugetRepoConfigValidatorMockedStatic.when(() -> NugetRepoConfigValidator.isNugetSmartRepoNormalized(any()))
                    .thenReturn(true);
            StatusLine statusLine = Mockito.mock(StatusLine.class);
            when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
            when(response.getStatusLine()).thenReturn(statusLine);
            when(response.getEntity()).thenReturn(Mockito.mock(HttpEntity.class));
            String jsonResponse = """
                    {"enableNormalizedVersion": true}
                    """;
            utilsMockedStatic.when(() -> EntityUtils.toString(any(), (Charset) any())).thenReturn(jsonResponse);
            String mockUrl = "https://example.com/artifactory/nuget-local/";
            when(mockRepoConfig.getRepoTypeConfig()).thenReturn(mockRepoTypeConfig);
            when(mockRepoTypeConfig.getUrl()).thenReturn(mockUrl);
            when(model.getAdvanced()).thenReturn(null);
            assertDoesNotThrow(() -> nugetRemoteRepoValidator.validate(mockRepoConfig, null, model));
        }
    }

    @Test
    void validateForIsNugetSmartRepoNormalizedFalse() {
        try (MockedStatic<NugetRepoConfigValidator> nugetRepoConfigValidatorMockedStatic =
                     mockStatic(NugetRepoConfigValidator.class)) {
            nugetRepoConfigValidatorMockedStatic.when(() -> NugetRepoConfigValidator.isNugetSmartRepoNormalized(any()))
                    .thenReturn(false);
            assertDoesNotThrow(() -> nugetRemoteRepoValidator.validate(mockRepoConfig, null, model));
        }
    }

    @Test
    void validateToThrowExceptionWhenRemoteResponseFailure() throws IOException {
        try (MockedStatic<RemoteRepositoryProvider> mockedStatic = mockStatic(
                RemoteRepositoryProvider.class);
                MockedStatic<NugetRepoConfigValidator> nugetRepoConfigValidatorMockedStatic =
                        mockStatic(NugetRepoConfigValidator.class)) {
            CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
            CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
            mockedStatic.when(() -> RemoteRepositoryProvider.getRemoteRepoHttpClient(anyString(),
                            any(RemoteRepositoryConfigModel.class), anyBoolean()))
                    .thenReturn(httpClient);
            when(httpClient.execute(any())).thenReturn(response);
            nugetRepoConfigValidatorMockedStatic.when(() -> NugetRepoConfigValidator.isNugetSmartRepoNormalized(any()))
                    .thenReturn(true);
            StatusLine statusLine = Mockito.mock(StatusLine.class);
            when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            when(response.getStatusLine()).thenReturn(statusLine);
            String mockUrl = "https://example.com/artifactory/nuget-local/";
            when(mockRepoConfig.getRepoTypeConfig()).thenReturn(mockRepoTypeConfig);
            when(mockRepoTypeConfig.getUrl()).thenReturn(mockUrl);
            when(model.getAdvanced()).thenReturn(null);
            assertThrowsExactly(RepoConfigException.class,
                    () -> nugetRemoteRepoValidator.validate(mockRepoConfig, null, model));
        }
    }

    @Test
    void validateToThrowExceptionForNormalisedSmartWithNonNormalissedRegistry() throws IOException {
        try (MockedStatic<RemoteRepositoryProvider> mockedStatic = mockStatic(
                RemoteRepositoryProvider.class); MockedStatic<EntityUtils> utilsMockedStatic = mockStatic(
                        EntityUtils.class);
                MockedStatic<NugetRepoConfigValidator> nugetRepoConfigValidatorMockedStatic =
                        mockStatic(NugetRepoConfigValidator.class)) {
            CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
            CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
            mockedStatic.when(() -> RemoteRepositoryProvider.getRemoteRepoHttpClient(anyString(),
                            any(RemoteRepositoryConfigModel.class), anyBoolean()))
                    .thenReturn(httpClient);
            when(httpClient.execute(any())).thenReturn(response);
            nugetRepoConfigValidatorMockedStatic.when(() -> NugetRepoConfigValidator.isNugetSmartRepoNormalized(any()))
                    .thenReturn(true);
            StatusLine statusLine = Mockito.mock(StatusLine.class);
            when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
            when(response.getStatusLine()).thenReturn(statusLine);
            when(response.getEntity()).thenReturn(Mockito.mock(HttpEntity.class));
            String jsonResponse = """
                    {"enableNormalizedVersion": false}
                    """;
            utilsMockedStatic.when(() -> EntityUtils.toString(any(), (Charset) any())).thenReturn(jsonResponse);
            String mockUrl = "https://example.com/artifactory/nuget-local/";
            when(mockRepoConfig.getRepoTypeConfig()).thenReturn(mockRepoTypeConfig);
            when(mockRepoTypeConfig.getUrl()).thenReturn(mockUrl);
            when(model.getAdvanced()).thenReturn(null);
            assertThrowsExactly(RepoConfigException.class,
                    () -> nugetRemoteRepoValidator.validate(mockRepoConfig, null, model));
        }
    }

    @Test
    void validateToThrowExceptionWhenHttpClientThrowsException() throws IOException {
        try (MockedStatic<RemoteRepositoryProvider> mockedStatic = mockStatic(
                RemoteRepositoryProvider.class);
                MockedStatic<NugetRepoConfigValidator> nugetRepoConfigValidatorMockedStatic =
                        mockStatic(NugetRepoConfigValidator.class)) {
            CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
            mockedStatic.when(() -> RemoteRepositoryProvider.getRemoteRepoHttpClient(anyString(),
                            any(RemoteRepositoryConfigModel.class), anyBoolean()))
                    .thenReturn(httpClient);
            when(httpClient.execute(any())).thenThrow(new IOException());
            nugetRepoConfigValidatorMockedStatic.when(() -> NugetRepoConfigValidator.isNugetSmartRepoNormalized(any()))
                    .thenReturn(true);
            String mockUrl = "https://example.com/artifactory/nuget-local/";
            when(mockRepoConfig.getRepoTypeConfig()).thenReturn(mockRepoTypeConfig);
            when(mockRepoTypeConfig.getUrl()).thenReturn(mockUrl);
            when(model.getAdvanced()).thenReturn(null);
            assertThrowsExactly(RepoConfigException.class,
                    () -> nugetRemoteRepoValidator.validate(mockRepoConfig, null, model));
        }
    }

    @Test
    void getRepoType() {
        assertSame(nugetRemoteRepoValidator.getRepoType(), RepoType.NuGet);
    }

    @Test
    void testGetRemoteUrl() throws URISyntaxException {
        String mockUrl = "https://example.com/artifactory/nuget-local/";
        when(mockRepoConfig.getRepoTypeConfig()).thenReturn(mockRepoTypeConfig);
        when(mockRepoTypeConfig.getUrl()).thenReturn(mockUrl);
        try (MockedStatic<StringUtils> mockedStringUtils = mockStatic(StringUtils.class)) {
            mockedStringUtils.when(() -> StringUtils.removeEnd(mockUrl, "/"))
                    .thenReturn("https://example.com/artifactory/nuget-local");
            try (MockedStatic<PathUtils> mockedPathUtils = mockStatic(PathUtils.class)) {
                mockedPathUtils.when(() -> PathUtils.getLastPathElement("https://example.com/artifactory/nuget-local"))
                        .thenReturn("nuget-local");
                String result = nugetRemoteRepoValidator.getFetchRepoDetailsUrl(mockRepoConfig);
                assertNotNull(result);
                assertEquals("https://example.com/artifactory/api/repositories/nuget-local", result);
            }
        }
    }
}