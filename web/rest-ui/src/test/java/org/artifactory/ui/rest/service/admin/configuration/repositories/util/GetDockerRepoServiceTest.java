package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.configuration.docker.repo.DockerRepo;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Author: Ivan Perepelytsya
 * Date: 7/28/20.
 */
public class GetDockerRepoServiceTest extends ArtifactoryHomeBoundTest {

    @InjectMocks
    private GetDockerRepoService getDockerRepoService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private AolService aolService;
    @Mock
    private CoreAddons coreAddons;
    @Mock
    private RepositoryService repoService;

    private String artifactoryServerName = "sample-artifactory";
    private String repoKey = "docker-repo-key";
    private ArtifactoryHome artifactoryHome;
    private AutoCloseable mocks;

    @BeforeMethod
    public void before() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        CentralConfigService centralConfigService = spy(CentralConfigService.class);
        when(addonsManager.lockdown()).thenReturn(false);
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(addonsManager.addonByType(CoreAddons.class)).thenReturn(coreAddons);
        when(coreAddons.getArtifactoryServerName()).thenReturn(artifactoryServerName);
        when(aolService.isAol()).thenReturn(true);
        when(aolService.skipDockerDns()).thenReturn(true);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
    }

    @AfterMethod
    public void after() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testExecute() {
        ArtifactoryRestRequest request = prepareRequest(false);
        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();
        getDockerRepoService.execute(request, response);
        DockerRepo dockerRepo = response.getIModel();
        String dockerPathExpected = "test.jfrog.io/" + repoKey;
        String baseUrlExpected = "https://test.jfrog.io/artifactory";
        assertEquals(dockerRepo.getDockerPath(), dockerPathExpected);
        assertEquals(dockerRepo.getBaseUrl(), baseUrlExpected);
        assertTrue(dockerRepo.isSkipDockerDns());
    }

    @Test
    public void testExecuteWithHeader() {
        ArtifactoryRestRequest request = prepareRequest(true);
        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();
        getDockerRepoService.execute(request, response);
        DockerRepo dockerRepo = response.getIModel();
        String expected = "header.jfrog.io/" + repoKey;
        assertEquals(dockerRepo.getDockerPath(), expected);
        assertTrue(dockerRepo.isSkipDockerDns());
    }

    @Nonnull
    private ArtifactoryRestRequest prepareRequest(boolean shouldUseHeader) {
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getUrlBase()).thenReturn("https://test.jfrog.io/");
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getScheme()).thenReturn("https");
        if (shouldUseHeader) {
            when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL))
                    .thenReturn("https://header.jfrog.io");
        }
        when(request.getServletRequest()).thenReturn(servletRequest);
        when(request.getQueryParamByKey("repoKey")).thenReturn(repoKey);
        return request;
    }

    @Test
    public void testExecuteFlagNotSet() {
        when(aolService.skipDockerDns()).thenReturn(false);
        ArtifactoryRestRequest request = prepareRequest(false);
        when(request.getQueryParamByKey("repoKey")).thenReturn(repoKey);
        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();
        getDockerRepoService.execute(request, response);
        DockerRepo dockerRepo = response.getIModel();
        String expected = artifactoryServerName + "-" + repoKey + ".jfrog.io";
        assertEquals(dockerRepo.getDockerPath(), expected);
    }

    @Test
    public void testExecuteNoAol() {
        when(aolService.skipDockerDns()).thenReturn(false);
        when(aolService.isAol()).thenReturn(false);
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getQueryParamByKey("repoKey")).thenReturn(repoKey);
        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();
        getDockerRepoService.execute(request, response);
        DockerRepo dockerRepo = response.getIModel();
        assertNull(dockerRepo.getDockerPath());
    }
}