package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GetMavenDistributionMgntServiceTest extends GetSettingSnippetServiceTest {

    private static final String SERVER_NAME = "baseurl";
    private static final String REPO_NAME = "test-repo";

    private GetMavenDistributionMgntService getMavenDistributionMgntService;
    private StreamRestResponse restResponse;
    private MockedStatic<ContextHelper> mockedContextHelper;
    private MockedStatic<ArtifactoryHome> mockedArtifactoryHome;

    @Mock
    private RepositoryService repositoryService;

    @BeforeEach
    @Override
    void setUp() {
        super.setUp();
        getMavenDistributionMgntService = Mockito.spy(new GetMavenDistributionMgntService());
        getMavenDistributionMgntService.repositoryService = repositoryService;
        restResponse = new StreamRestResponse();
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest("GET", "/path/to/resource");
        mockHttpServletRequest.setServerName(SERVER_NAME);
        mockedContextHelper = mockStatic(ContextHelper.class);
        mockedArtifactoryHome = mockStatic(ArtifactoryHome.class);

        when(request.getQueryParamByKey("repoKey")).thenReturn(REPO_NAME);
        when(request.getServletRequest()).thenReturn(mockHttpServletRequest);
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mockedContextHelper.close();
        mockedArtifactoryHome.close();
    }

    @Test
    void testExecuteWithNullRepoDescriptor() {
        when(repositoryService.repoDescriptorByKey(REPO_NAME)).thenReturn(null);

        getMavenDistributionMgntService.execute(request, restResponse);
        assertNull(restResponse.getIModel());
    }

    @Test
    void testExecuteWithLocalRepoDescriptor() {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setKey(REPO_NAME);
        when(repositoryService.repoDescriptorByKey(REPO_NAME)).thenReturn(localRepoDescriptor);
        mockUrlAndProperties();

        getMavenDistributionMgntService.execute(request, restResponse);
        assertNotNull(restResponse.getIModel());
        assertTrue(restResponse.getIModel().toString().contains(SERVER_NAME + "-" + REPO_NAME));
        assertTrue(restResponse.getIModel().toString().contains("https://baseurl/artifactory/" + REPO_NAME));
    }

    @Test
    void testExecuteWithVirtualRepoDescriptor() {
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        LocalRepoDescriptor defaultDeploymentRepo = new LocalRepoDescriptor();
        defaultDeploymentRepo.setHandleReleases(true);
        defaultDeploymentRepo.setHandleSnapshots(true);
        virtualRepoDescriptor.setDefaultDeploymentRepo(defaultDeploymentRepo);
        virtualRepoDescriptor.setKey(REPO_NAME);
        when(repositoryService.repoDescriptorByKey(REPO_NAME)).thenReturn(virtualRepoDescriptor);
        mockUrlAndProperties();

        getMavenDistributionMgntService.execute(request, restResponse);
        assertNotNull(restResponse.getIModel());
        assertTrue(restResponse.getIModel().toString().contains(SERVER_NAME + "-" + REPO_NAME));
        assertTrue(restResponse.getIModel().toString().contains("https://baseurl/artifactory/" + REPO_NAME));
    }

    @Test
    void testExecuteWithVirtualRepoDescriptorAndNoDefaultDeploymentRepo() {
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setKey(REPO_NAME);
        when(repositoryService.repoDescriptorByKey(REPO_NAME)).thenReturn(virtualRepoDescriptor);
        mockUrlAndProperties();

        getMavenDistributionMgntService.execute(request, restResponse);
        assertNotNull(restResponse.getIModel());
        assertTrue(restResponse.getIModel().toString().contains("Default deployment repository is not configured."));
    }

    private void mockUrlAndProperties() {
        mockBaseUrlFromConfig();
        ArtifactorySystemProperties artifactorySystemProperties = new ArtifactorySystemProperties();
        artifactorySystemProperties.getProperty("context.path", "/artifactory");
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        mockedArtifactoryHome.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
        mockedContextHelper.when(ContextHelper::get).thenReturn(artifactoryContext);
    }
}