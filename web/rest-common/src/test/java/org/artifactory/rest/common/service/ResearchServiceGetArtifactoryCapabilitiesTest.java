package org.artifactory.rest.common.service;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.jackson.JacksonWriter;
import org.artifactory.api.rest.search.result.VersionRestResult;
import org.artifactory.features.matrix.SmartRepoVersionFeatures;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ResearchServiceGetArtifactoryCapabilitiesTest {

    @Test
    public void whenUrlIsNull() throws IOException {
        ResearchServiceImpl researchService = createResearchService(true);
        CloseableHttpResponse response = mockResponse(200, "body");
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities(null, client);

        assertFalse(capabilities.getResearchResponse().isArtifactory(), "it is not Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.UNKNOWN, "repo type is unknown");
    }

    @Test
    public void whenUnauthorized() throws IOException {
        ResearchServiceImpl researchService = createResearchService(true);
        CloseableHttpResponse response = mockResponse(401, "body");
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertTrue(capabilities.getResearchResponse().isArtifactory(), "it is Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.UNAUTHORIZED,
                "repo type is unauthorized");
    }

    @Test
    public void whenRequestFailed() throws IOException {
        ResearchServiceImpl researchService = createResearchService(true);
        CloseableHttpResponse response = mockResponse(500, "body");
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertFalse(capabilities.getResearchResponse().isArtifactory(), "it is not Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.UNKNOWN, "repo type is unknown");
    }

    @Test
    public void whenRequestThrowConnectionTimeout() throws IOException {
        ResearchServiceImpl researchService = createResearchService(true);
        CloseableHttpResponse response = mockResponse(200, "body");
        CloseableHttpClient client = mockClient(response);
        when(client.execute(any())).thenThrow(new HttpConnectTimeoutException("fdf"));

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertFalse(capabilities.getResearchResponse().isArtifactory(), "it is not Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.NOT_AVAILABLE,
                "repo type is not available");
    }

    @Test
    public void whenResponseHasIncorrectBody() throws IOException {
        ResearchServiceImpl researchService = createResearchService(true);
        CloseableHttpResponse response = mockResponse(200, "incorrect-body");
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertFalse(capabilities.getResearchResponse().isArtifactory(), "it is not Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.NOT_AVAILABLE,
                "repo type is not available");
    }

    @Test
    public void whenResponseHasEmptyBody() throws IOException {
        ResearchServiceImpl researchService = createResearchService(true);
        CloseableHttpResponse response = mockResponse(200, "");
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertFalse(capabilities.getResearchResponse().isArtifactory(), "it is not Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.UNKNOWN, "repo type is unknown");
    }

    @Test
    public void whenNoVersion() throws IOException {
        ResearchServiceImpl researchService = createResearchService(true);
        VersionRestResult version = new VersionRestResult("", "r", new ArrayList<>(), "l", null);
        String body = JacksonWriter.serialize(version);
        CloseableHttpResponse response = mockResponse(200, body);
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertFalse(capabilities.getResearchResponse().isArtifactory(), "it is not Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.UNKNOWN, "repo type is unknown");
    }

    @Test
    public void whenLicenseCheckFailed() throws IOException {
        // When license validation fails but we receive a valid version response,
        // we should still mark it as Artifactory (we successfully detected it's Artifactory)
        ResearchServiceImpl researchService = createResearchService(false);
        VersionRestResult version = new VersionRestResult("v", "r", new ArrayList<>(), "l", null);
        String body = JacksonWriter.serialize(version);
        CloseableHttpResponse response = mockResponse(200, body);
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertTrue(capabilities.getResearchResponse().isArtifactory(), "it is Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.UNKNOWN, "repo type is unknown");
    }

    @Test
    public void whenLicenseCheckFailedButVersionReceived() throws IOException {
        // Verify that when we receive a valid version response but license validation fails,
        // we correctly identify it as Artifactory but keep repoInfo as UNKNOWN
        ResearchServiceImpl researchService = createResearchService(false);
        VersionRestResult version = new VersionRestResult("v", "r", new ArrayList<>(), "l", null);
        String body = JacksonWriter.serialize(version);
        CloseableHttpResponse response = mockResponse(200, body);
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertTrue(capabilities.getResearchResponse().isArtifactory(), "it is Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.UNKNOWN, "repo type is unknown");
    }

    @Test
    public void whenTargetArtifactoryIsCompatible() throws IOException {
        ResearchServiceImpl researchService = createResearchService(true);
        VersionRestResult version = new VersionRestResult("v", "r", new ArrayList<>(), "l", null);
        String body = JacksonWriter.serialize(version);
        CloseableHttpResponse response = mockResponse(200, body);
        CloseableHttpClient client = mockClient(response);

        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                researchService.getArtifactoryCapabilities("url", client);

        assertTrue(capabilities.getResearchResponse().isArtifactory(), "it is Artifactory");
        assertEquals(capabilities.getRepoInfo(), ResearchServiceImpl.RepoInfo.ALLOWED_SMART,
                "repo type is from response");
    }

    ResearchServiceImpl createResearchService(boolean licenseValid) {
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaCommonAddon addon = mock(HaCommonAddon.class);
        when(addon.getHostId()).thenReturn("host123");
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(addon);
        SmartRepoVersionFeatures smartRepoVersionFeatures = mock(SmartRepoVersionFeatures.class);
        when(smartRepoVersionFeatures.getFeaturesByVersion(any())).thenReturn(new ArrayList<>());
        ResearchServiceForTest researchService = new ResearchServiceForTest();
        researchService.licenseValid = licenseValid;
        researchService.setAddonsManager(addonsManager);
        researchService.setSmartRepoVersionFeatures(smartRepoVersionFeatures);
        return researchService;
    }

    private CloseableHttpResponse mockResponse(int code, String body) throws UnsupportedEncodingException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(code);
        HttpEntity entity = new StringEntity(body);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(entity);
        return response;
    }

    private CloseableHttpClient mockClient(CloseableHttpResponse response) throws IOException {
        CloseableHttpClient client = Mockito.mock(CloseableHttpClient.class);
        when(client.execute(any())).thenReturn(response);
        return client;
    }

    private class ResearchServiceForTest extends ResearchServiceImpl {
        private boolean licenseValid;

        @Override
        protected RepoInfo getRepoInfo(CloseableHttpClient client, URI uri, String repoKey) {
            return RepoInfo.ALLOWED_SMART;
        }

        @Override
        boolean validateLicense(VersionRestResult versionRestResult, String artifactoryId) {
            return licenseValid;
        }
    }
}
