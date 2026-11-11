package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import jakarta.ws.rs.core.Response;
import lombok.SneakyThrows;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.ph.RepoListType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAllowedReposModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.AssertionErrors.assertEquals;

public class GetVirtualAllowedReposTest {
    @Mock
    protected PackageResourceService packageResourceService;
    @Mock
    protected RepoConfigValidator repoValidator;
    @Mock
    protected RepoConfigDescriptorBuilder repoBuilder;
    @Mock
    protected CentralConfigService configService;
    @Mock
    protected MutableCentralConfigDescriptor mutableCentralConfigDescriptor;

    @InjectMocks
    private GetVirtualAllowedRepos getVirtualAllowedRepos;

    private AutoCloseable mocks;

    @BeforeMethod
    public void before() {
        mocks = MockitoAnnotations.openMocks(this);
        getVirtualAllowedRepos.setRepoValidator(repoValidator);
        getVirtualAllowedRepos.setRepoBuilder(repoBuilder);
        getVirtualAllowedRepos.setConfigService(configService);
        when(configService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        getVirtualAllowedRepos.setPackageResourceService(packageResourceService);
    }

    @AfterMethod
    public void after() throws Exception {
        mocks.close();
    }

    @Test
    @SneakyThrows
    void testGetVirtualAllowedRepos() {
        // Arrange
        var restRequest = (ArtifactoryRestRequest<VirtualAllowedReposModel>) mock(ArtifactoryRestRequest.class);
        var restResponse = new ArtifactoryRestResponse();
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("vir-nug-repo", RepoType.NuGet);
        VirtualAllowedReposModel request = new VirtualAllowedReposModel();
        request.setVirtualConfigModel(createVirtualRepositoryConfigModel());
        request.setRepoLists(Map.of(
                RepoListType.AVAILABLE, List.of("nuget-remote", "nuget-remote-enforce"),
                RepoListType.SELECTED, List.of("nuget-local-enforce")
        ));

        Map<RepoListType, List<String>> expectedResult = Map.of(
                RepoListType.AVAILABLE, List.of("nuget-remote", "nuget-remote-enforce"),
                RepoListType.SELECTED, List.of("nuget-local-enforce")
        );

        when(restRequest.getImodel()).thenReturn(request);
        when(request.getVirtualConfigModel().toRepoConfig(repoValidator, repoBuilder,
                configService.getMutableDescriptor())).thenReturn(repoConfig);
        when(packageResourceService.getVirtualAllowedRepos(any(), any()))
                .thenReturn(expectedResult);

        // Act
        getVirtualAllowedRepos.execute(restRequest, restResponse);

        // Assert
        assertNotNull(restResponse);
        assertEquals("verify response code",
                Response.Status.OK.getStatusCode(), restResponse.getResponseCode());
        assertEquals("verify if response is correct", restResponse.getEntity(), expectedResult);
        verify(packageResourceService, times(1)).getVirtualAllowedRepos(any(), any());
    }

    @Test
    @SneakyThrows
    void testGetVirtualAllowedReposEncountersRepoConfigExceptionInToRepoConfig() {
        // Arrange
        var restRequest = (ArtifactoryRestRequest<VirtualAllowedReposModel>) mock(ArtifactoryRestRequest.class);
        var restResponse = new ArtifactoryRestResponse();
        GeneralRepositoryConfigModel generalConfigModel = new GeneralRepositoryConfigModel();
        generalConfigModel.setRepoKey("vir-nug-repo");
        VirtualAllowedReposModel request = new VirtualAllowedReposModel();
        VirtualRepositoryConfigModel mockVirtualConfigModel = mock(VirtualRepositoryConfigModel.class);
        request.setVirtualConfigModel(mockVirtualConfigModel);
        request.setRepoLists(Map.of(
                RepoListType.AVAILABLE, List.of("nuget-remote", "nuget-remote-enforce"),
                RepoListType.SELECTED, List.of("nuget-local-enforce")
        ));

        when(restRequest.getImodel()).thenReturn(request);
        when(mockVirtualConfigModel.getGeneral()).thenReturn(generalConfigModel);
        when(mockVirtualConfigModel.toRepoConfig(repoValidator, repoBuilder, configService.getMutableDescriptor()))
                .thenThrow(new RepoConfigException("Error", Response.Status.BAD_REQUEST.getStatusCode()));

        // Act
        getVirtualAllowedRepos.execute(restRequest, restResponse);

        // Assert
        assertNotNull(restResponse);
        assertEquals("verify response code",
                Response.Status.BAD_REQUEST.getStatusCode(), restResponse.getResponseCode());
        verify(packageResourceService, times(0)).getVirtualAllowedRepos(any(), any());
    }

    @Test
    @SneakyThrows
    void testGetVirtualAllowedReposReturnsBadRequestForNonVirtualRepoDetailsType() {
        // Arrange
        var restRequest = (ArtifactoryRestRequest<VirtualAllowedReposModel>) mock(ArtifactoryRestRequest.class);
        var restResponse = new ArtifactoryRestResponse();
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        VirtualAllowedReposModel request = new VirtualAllowedReposModel();
        request.setVirtualConfigModel(createVirtualRepositoryConfigModel());
        request.setRepoLists(Map.of(
                RepoListType.AVAILABLE, List.of("nuget-remote", "nuget-remote-enforce"),
                RepoListType.SELECTED, List.of("nuget-local-enforce")
        ));

        Map<RepoListType, List<String>> expectedResult = Map.of(
                RepoListType.AVAILABLE, List.of("nuget-remote", "nuget-remote-enforce"),
                RepoListType.SELECTED, List.of("nuget-local-enforce")
        );

        when(restRequest.getImodel()).thenReturn(request);
        when(request.getVirtualConfigModel().toRepoConfig(repoValidator, repoBuilder,
                configService.getMutableDescriptor())).thenReturn(repoConfig);
        when(packageResourceService.getVirtualAllowedRepos(any(), any()))
                .thenReturn(expectedResult);
        // Non-Virtual RepoDetailsType
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);

        // Act
        getVirtualAllowedRepos.execute(restRequest, restResponse);

        // Assert
        assertNotNull(restResponse);
        assertEquals("verify response code",
                Response.Status.BAD_REQUEST.getStatusCode(), restResponse.getResponseCode());
        verify(packageResourceService, times(0)).getVirtualAllowedRepos(any(), any());
    }

    private VirtualRepositoryConfigModel createVirtualRepositoryConfigModel() {
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();

        // General configuration
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("nugVir");
        general.setProjectKey("default");
        general.setEnvironments(new HashSet<>());
        model.setGeneral(general);

        // Basic configuration
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        basic.setPublicDescription("");
        basic.setInternalDescription("");
        basic.setIncludesPattern("**/*");
        basic.setExcludesPattern("");
        basic.setLayout("nuget-default");

        // Selected repositories
        List<VirtualSelectedRepository> selectedRepos = List.of();
        basic.setSelectedRepositories(selectedRepos);

        // Resolved repositories
        List<VirtualSelectedRepository> resolvedRepos = List.of();
        basic.setResolvedRepositories(resolvedRepos);

        basic.setDefaultDeploymentRepo("nuget-local-enforce");
        basic.setIneligibleRepositories(new HashSet<>());
        model.setBasic(basic);

        // Advanced configuration
        VirtualAdvancedRepositoryConfigModel advanced = new VirtualAdvancedRepositoryConfigModel();
        advanced.setRetrieveRemoteArtifacts(false);
        advanced.setHideUnauthorizedResources(false);
        advanced.setSignedUrlTtl(90);
        advanced.setPriorityResolution(false);
        model.setAdvanced(advanced);

        return model;
    }
}
