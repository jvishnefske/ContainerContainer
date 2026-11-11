package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.request.UrlVerifier;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DockerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.artifactory.api.request.ArtifactoryResponse.ARTIFACTORY_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RemoteRepositoryTestUrlTest extends ArtifactoryHomeBoundTest {

    private static final String FIRST_TEST_URL = "docker/v2/";
    private static final String ALTERNATIVE_TEST_URL = "docker/";
    private static final String DOCKER_DISTRIBUTION_API_VERSION = "Docker-Distribution-Api-Version";
    private static final String S3_SERVER = "Server";

    private ArtifactoryRestResponse artifactoryResponse;

    @Mock
    private RemoteRepositoryConfigModel repositoryModel;
    @Mock
    private RemoteAdvancedRepositoryConfigModel remoteAdvanced;
    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    private UrlVerifier urlVerifier;
    @Mock
    private CloseableHttpClient httpClient;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaCommonAddon haCommonAddon;
    @Mock
    private StatusLine okStatusLine;
    @Mock
    private StatusLine badRequestStatusLine;
    @Mock
    private CloseableHttpResponse okFromArtifactory;
    @Mock
    private CloseableHttpResponse badRequestFromArtifactory;
    @Mock
    private CloseableHttpResponse badRequestFromDockerHub;

    private AutoCloseable mocks;

    @BeforeClass
    public void beforeClass() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterClass
     public void afterClass() throws Exception {
        mocks.close();
    }

    @BeforeMethod
    public void setUp() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);

        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.debianDefaultArchitectures.getPropertyName(), "");

        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getHostId()).thenReturn("");

        RemoteBasicRepositoryConfigModel basicModel = new RemoteBasicRepositoryConfigModel();
        RemoteNetworkRepositoryConfigModel networkModel = new RemoteNetworkRepositoryConfigModel();
        RemoteAdvancedRepositoryConfigModel remoteRepoAdvancedModel = new RemoteAdvancedRepositoryConfigModel();
        TypeSpecificConfigModel repoTypeModel = new DockerTypeSpecificConfigModel();

        Header artifactoryHeader = new BasicHeader(ARTIFACTORY_ID, ARTIFACTORY_ID);
        Header dockerHeader = new BasicHeader(DOCKER_DISTRIBUTION_API_VERSION, "registry");
        Header s3Header = new BasicHeader(S3_SERVER, "AmazonS3");

        when(remoteAdvanced.getNetwork()).thenReturn(networkModel);
        when(repositoryModel.getAdvanced()).thenReturn(remoteRepoAdvancedModel);
        when(repositoryModel.getBasic()).thenReturn(basicModel);
        when(repositoryModel.getTypeSpecific()).thenReturn(repoTypeModel);
        when(repositoryModel.getAdvanced()).thenReturn(remoteAdvanced);

        when(okStatusLine.getStatusCode()).thenReturn(200);
        when(okStatusLine.getReasonPhrase()).thenReturn("ok");

        when(badRequestStatusLine.getStatusCode()).thenReturn(400);
        when(badRequestStatusLine.getReasonPhrase()).thenReturn("thats why");

        when(okFromArtifactory.getStatusLine()).thenReturn(okStatusLine);
        when(okFromArtifactory.getHeaders(ARTIFACTORY_ID)).thenReturn(new Header[] {artifactoryHeader});
        when(okFromArtifactory.getFirstHeader(DOCKER_DISTRIBUTION_API_VERSION)).thenReturn(dockerHeader);
        when(okFromArtifactory.getFirstHeader(S3_SERVER)).thenReturn(s3Header);

        when(badRequestFromDockerHub.getStatusLine()).thenReturn(badRequestStatusLine);
        when(badRequestFromDockerHub.getFirstHeader(DOCKER_DISTRIBUTION_API_VERSION)).thenReturn(dockerHeader);
        when(badRequestFromDockerHub.getFirstHeader(S3_SERVER)).thenReturn(s3Header);

        when(badRequestFromArtifactory.getStatusLine()).thenReturn(badRequestStatusLine);
        when(badRequestFromArtifactory.getHeaders(ARTIFACTORY_ID)).thenReturn(new Header[] {artifactoryHeader});
        when(badRequestFromArtifactory.getFirstHeader(DOCKER_DISTRIBUTION_API_VERSION)).thenReturn(dockerHeader);
        when(badRequestFromArtifactory.getFirstHeader(S3_SERVER)).thenReturn(s3Header);

        artifactoryResponse = new ArtifactoryRestResponse();

        Mockito.reset(httpClient);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void whenFirstTestIsOkThenOk() throws IOException {
        repositoryModel.getBasic().setUrl("docker");
        setupClient(okFromArtifactory, null);
        testConnection();

        ArgumentCaptor<HttpRequestBase> captor = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(httpClient, times(1)).execute(captor.capture());
        assertEquals(captor.getAllValues().size(), 1);
        assertEquals(captor.getAllValues().get(0).getURI().toString(), FIRST_TEST_URL);
        assertFalse(artifactoryResponse.isFailed());
    }

    @Test
    public void whenFirstTestIsFailedButNotByArtifactoryThenFail() throws IOException {
        repositoryModel.getBasic().setUrl("docker");
        setupClient(badRequestFromDockerHub, null);
        testConnection();

        ArgumentCaptor<HttpRequestBase> captor = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(httpClient, times(2)).execute(captor.capture());
        assertEquals(captor.getAllValues().size(), 2);
        assertEquals(captor.getAllValues().get(0).getURI().toString(), FIRST_TEST_URL);
        assertEquals(captor.getAllValues().get(1).getURI().toString(), FIRST_TEST_URL);
        assertFalse(artifactoryResponse.isFailed());
    }

    @Test
    public void whenBothTestsFailedByArtifactoryThenFail() throws IOException {
        repositoryModel.getBasic().setUrl("docker");
        setupClient(badRequestFromArtifactory, badRequestFromArtifactory);
        testConnection();

        ArgumentCaptor<HttpRequestBase> captor = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(httpClient, times(3)).execute(captor.capture());
        assertEquals(captor.getAllValues().size(), 3);
        assertEquals(captor.getAllValues().get(0).getURI().toString(), FIRST_TEST_URL);
        assertEquals(captor.getAllValues().get(1).getURI().toString(), FIRST_TEST_URL);
        assertEquals(captor.getAllValues().get(2).getURI().toString(), ALTERNATIVE_TEST_URL);
        assertTrue(artifactoryResponse.isFailed());
    }

    @Test
    public void whenFirstTestUrlFailedByArtifactoryThenFallbackToAlternativeTestUrl() throws IOException {
        repositoryModel.getBasic().setUrl("docker");
        setupClient(badRequestFromArtifactory, okFromArtifactory);
        testConnection();

        ArgumentCaptor<HttpRequestBase> captor = ArgumentCaptor.forClass(HttpRequestBase.class);
        verify(httpClient, times(3)).execute(captor.capture());
        assertEquals(captor.getAllValues().size(), 3);
        assertEquals(captor.getAllValues().get(0).getURI().toString(), FIRST_TEST_URL);
        assertEquals(captor.getAllValues().get(1).getURI().toString(), FIRST_TEST_URL);
        assertEquals(captor.getAllValues().get(2).getURI().toString(), ALTERNATIVE_TEST_URL);
        assertFalse(artifactoryResponse.isFailed());
    }

    @Test
    public void tryS3AdvancedUrl() throws IOException {
        RemoteRepositoryTestUrl remoteRepositoryTestUrl = new RemoteRepositoryTestUrlTestChild(urlVerifier);
        setupClient(badRequestFromArtifactory, badRequestFromArtifactory);
        repositoryModel.getAdvanced().getNetwork().setUrl("https://s3.amazonaws.com/takipi-rpm-repo");

        assertFalse(remoteRepositoryTestUrl.tryS3(
                httpClient,
                repositoryModel,
                new BasicHeader(S3_SERVER, "AmazonS3")));
    }

    @Test
    public void tryS3BasicUrl() throws IOException {
        RemoteRepositoryTestUrl remoteRepositoryTestUrl = new RemoteRepositoryTestUrlTestChild(urlVerifier);
        setupClient(badRequestFromArtifactory, badRequestFromArtifactory);
        repositoryModel.getBasic().setUrl("https://s3.amazonaws.com/takipi-rpm-repo");

        assertFalse(remoteRepositoryTestUrl.tryS3(
                httpClient,
                repositoryModel,
                new BasicHeader(S3_SERVER, "AmazonS3")));
    }

    @Test
    public void tryS3NoUrl() throws IOException {
        RemoteRepositoryTestUrl remoteRepositoryTestUrl = new RemoteRepositoryTestUrlTestChild(urlVerifier);
        setupClient(badRequestFromArtifactory, badRequestFromArtifactory);

        assertFalse(remoteRepositoryTestUrl.tryS3(
                httpClient,
                repositoryModel,
                new BasicHeader(S3_SERVER, "AmazonS3")));
    }

    @Test
    public void tryS3NoHeader() throws IOException {
        RemoteRepositoryTestUrl remoteRepositoryTestUrl = new RemoteRepositoryTestUrlTestChild(urlVerifier);
        setupClient(badRequestFromArtifactory, badRequestFromArtifactory);

        assertFalse(remoteRepositoryTestUrl.tryS3(
                httpClient,
                repositoryModel,
                null));
    }

    private void testConnection() throws IOException {
        RemoteRepositoryTestUrl remoteRepositoryTestUrl = new RemoteRepositoryTestUrlTestChild(urlVerifier);
        remoteRepositoryTestUrl.testConnection(artifactoryResponse, repositoryModel);
    }

    private void setupClient(CloseableHttpResponse firstResponse, CloseableHttpResponse secondResponse)
            throws IOException {
        Answer<CloseableHttpResponse> answer = invocationOnMock -> {
            HttpRequestBase request = invocationOnMock.getArgument(0, HttpRequestBase.class);
            String url = request.getURI().toString();

            if (url.equals(FIRST_TEST_URL)) {
                return firstResponse;
            } else {
                return secondResponse;
            }
        };
        when(httpClient.execute(any())).thenAnswer(answer);
    }

    private class RemoteRepositoryTestUrlTestChild extends RemoteRepositoryTestUrl {
        public RemoteRepositoryTestUrlTestChild(UrlVerifier urlVerifier) {
            super(urlVerifier);
        }

        @Override
        protected CloseableHttpClient getRemoteRepoHttpClient(String remoteRepoUrl,
                RemoteRepositoryConfigModel repositoryModel) {
            return httpClient;
        }
    }
}