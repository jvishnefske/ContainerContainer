package org.artifactory.rest.common.service;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ResearchServiceGetRepoInfoTest {

    private static final URI URI_SAMPLE = URI.create("https://www.google.com/");

    @Test
    public void whenNoResponseFromClientThenRepoTypeIsUnknown() throws IOException {
        CloseableHttpClient client = Mockito.mock(CloseableHttpClient.class);
        when(client.execute(any())).thenReturn(null);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.UNKNOWN);
    }

    @Test
    public void whenUnauthorizedFromClientThenRepoTypeIsUnauthorized() throws IOException {
        CloseableHttpResponse response = mockResponse(401, "");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.UNAUTHORIZED);
    }

    @Test
    public void whenForbiddenFromClientThenRepoTypeIsUnauthorized() throws IOException {
        CloseableHttpResponse response = mockResponse(403, "");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.UNAUTHORIZED);
    }

    @Test
    public void whenTooManyRequestsFromClientThenRepoTypeIsNotAvailable() throws IOException {
        CloseableHttpResponse response = mockResponse(429, "");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.NOT_AVAILABLE);
    }

    @Test
    public void whenServiceUnavailableFromClientThenRepoTypeIsNotAvailable() throws IOException {
        CloseableHttpResponse response = mockResponse(503, "");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.NOT_AVAILABLE);
    }

    @Test
    public void whenNotOkAndNotUnauthorizedFromClientThenRepoTypeIsUnknown() throws IOException {
        CloseableHttpResponse response = mockResponse(500, "");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.UNKNOWN);
    }

    @Test
    public void whenOkButNotJsonResponseThenRepoTypeIsUnknown() throws IOException {
        CloseableHttpResponse response = mockResponse(200, "not-json");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.UNKNOWN);
    }

    @Test
    public void whenLocalRepoThenSmartAllowed() throws IOException {
        CloseableHttpResponse response = mockResponse(200, "{\"rclass\" : \"local\"}");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.ALLOWED_SMART);
    }

    @Test
    public void whenRemoteRepoThenSmartAllowed() throws IOException {
        CloseableHttpResponse response = mockResponse(200, "{\"rclass\" : \"remote\"}");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.ALLOWED_SMART);
    }

    @Test
    public void whenVirtualRepoOfNotSupportedPackageThenSmartNotAllowed() throws IOException {
        CloseableHttpResponse response = mockResponse(200, "{\"rclass\" : \"virtual\", \"packageType\" : \"generic\"}");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.NOT_ALLOWED_SMART);
    }

    @Test
    public void whenVirtualRepoOfSupportedPackageThenSmartAllowed() throws IOException {
        CloseableHttpResponse response = mockResponse(200, "{\"rclass\" : \"virtual\", \"packageType\" : \"go\"}");
        CloseableHttpClient client = mockClient(response);
        ResearchServiceImpl researchService = createResearchService();

        ResearchServiceImpl.RepoInfo repoInfo = researchService.getRepoInfo(client, URI_SAMPLE, "repoKey");

        assertEquals(repoInfo, ResearchServiceImpl.RepoInfo.ALLOWED_SMART);
    }

    private CloseableHttpClient mockClient(CloseableHttpResponse response) throws IOException {
        CloseableHttpClient client = Mockito.mock(CloseableHttpClient.class);
        when(client.execute(any())).thenReturn(response);
        return client;
    }

    private ResearchServiceImpl createResearchService() {
        AddonsManager addonsManager = mock(AddonsManager.class);
        HaCommonAddon addon = mock(HaCommonAddon.class);
        when(addon.getHostId()).thenReturn("host123");
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(addon);
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        researchService.setAddonsManager(addonsManager);
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

}
