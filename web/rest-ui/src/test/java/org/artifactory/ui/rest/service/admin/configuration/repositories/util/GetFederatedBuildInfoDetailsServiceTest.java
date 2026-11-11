package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedBuildInfoDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static com.jfrog.build.acl.util.BuildConstants.BUILD_INFO_REPO_KEY_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetFederatedBuildInfoDetailsServiceTest {

    @Mock
    FederatedRepoService federatedRepoService;
    @Mock
    BuildFacade buildFacade;
    @Mock
    InternalRepositoryService repositoryService;

    @InjectMocks
    GetFederatedBuildInfoDetailsService service;
    private AutoCloseable mocks;

    @BeforeEach
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    private void mockFederationAttributeEnabled(boolean value) {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactProperties = mock(ArtifactorySystemProperties.class);
        when(artifactProperties.getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled))
                .thenReturn(value);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactProperties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @Test
    void getFederatedBuildInfo() {
        ArtifactoryRestRequest<String> request = mock(ArtifactoryRestRequest.class);
        RestResponse<FederatedBuildInfoDetails> response = new ArtifactoryRestResponse();

        when(request.getImodel()).thenReturn(null);
        when(federatedRepoService.isProjectBuildInfoRepoFederated(null)).thenReturn(true);
        when(buildFacade.getBuildInfoRepoKeyByProject(null, true)).thenReturn(SystemRepos.BUILDINFO_REPO_KEY);
        service.execute(request, response);

        assertFalse(((FederatedBuildInfoDetails) response.getIModel()).isEnableConvert());
        assertEquals(((FederatedBuildInfoDetails) response.getIModel()).getBuildInfoRepo(),
                SystemRepos.BUILDINFO_REPO_KEY);
    }

    @Test
    void testProjectBuildInfoRepo() {
        mockFederationAttributeEnabled(false);
        ArtifactoryRestRequest<String> request = mock(ArtifactoryRestRequest.class);
        RestResponse<FederatedBuildInfoDetails> response = new ArtifactoryRestResponse();
        String projectKey = "proja";
        String projectRepo = projectKey + BUILD_INFO_REPO_KEY_SUFFIX;
        when(request.getImodel()).thenReturn(projectKey);
        when(federatedRepoService.isProjectBuildInfoRepoFederated(projectKey)).thenReturn(false);
        when(buildFacade.getBuildInfoRepoKeyByProject(projectKey, true)).thenReturn(projectRepo);
        service.execute(request, response);

        assertTrue(((FederatedBuildInfoDetails) response.getIModel()).isEnableConvert());
        assertEquals(((FederatedBuildInfoDetails) response.getIModel()).getBuildInfoRepo(), projectRepo);
    }

    static Stream<Arguments> fedAttrOptInConfigs() {
        String projectRepo = "proja" + BUILD_INFO_REPO_KEY_SUFFIX;

        LocalRepoConfig federatedLocal = LocalRepoConfig.create(projectRepo, RepoType.Generic);
        federatedLocal.getBaseConfig().getFederationConfig().setFederationOnGrid(true);

        RemoteRepoConfig federatedRemote = RemoteRepoConfig.create(projectRepo, RepoType.Generic);
        federatedRemote.getBaseConfig().getFederationConfig().setFederationOnGrid(true);

        VirtualRepoConfig federatedVirtual = VirtualRepoConfig.create(projectRepo, RepoType.Generic);
        federatedVirtual.getBaseConfig().getFederationConfig().setFederationOnGrid(true);

        LocalRepoConfig plainLocal = LocalRepoConfig.create(projectRepo, RepoType.Generic);

        return Stream.of(
                Arguments.of(federatedLocal, false),
                Arguments.of(federatedRemote, false),
                Arguments.of(federatedVirtual, false),
                Arguments.of(plainLocal, true)
        );
    }

    @ParameterizedTest
    @MethodSource("fedAttrOptInConfigs")
    void fedAttrOptIn_repoConfig_enableConvertReflectsFederatedState(
            RepoConfig repoConfig, boolean expectedEnableConvert) {
        mockFederationAttributeEnabled(true);
        String projectKey = "proja";
        String projectRepo = projectKey + BUILD_INFO_REPO_KEY_SUFFIX;

        ArtifactoryRestRequest<String> request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(projectKey);
        when(federatedRepoService.isProjectBuildInfoRepoFederated(projectKey)).thenReturn(false);
        when(buildFacade.getBuildInfoRepoKeyByProject(projectKey, true)).thenReturn(projectRepo);
        when(repositoryService.getRepositoryConfigByKey(projectRepo)).thenReturn(repoConfig);

        RestResponse<FederatedBuildInfoDetails> response = new ArtifactoryRestResponse();
        service.execute(request, response);

        assertEquals(expectedEnableConvert, ((FederatedBuildInfoDetails) response.getIModel()).isEnableConvert());
        assertEquals(projectRepo, ((FederatedBuildInfoDetails) response.getIModel()).getBuildInfoRepo());
    }
}
