package org.artifactory.ui.rest.resource.admin.configuration.repositories;

import jakarta.ws.rs.core.Response;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.metrics.cuj.RepoCreationMetricsWrapper;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.repo.ProjectRepoPermissionsHelper;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.MetadataCachePeriodExclusionRequestModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAllowedReposModel;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Andrey Tashlitsky
 */
class RepoConfigResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    protected ConfigServiceFactory configServiceFactory;
    @Mock
    protected RepositoryConfigService repositoryConfigService;
    @Mock
    protected ProjectsService projectsService;
    @Mock
    protected ProjectRepoPermissionsHelper projectRepoPermissionsHelper;
    @Mock
    private SecurityService securityService;
    private RepoCreationMetricsWrapper repoCreationMetricsWrapperSpy;
    @Spy
    protected RepoConfigResource repoConfigResource;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        setField(repoConfigResource, "configServiceFactory", configServiceFactory);
        setField(repoConfigResource, "repositoryConfigService", repositoryConfigService);
        setField(repoConfigResource, "projectsService", projectsService);
        setField(repoConfigResource, "projectRepoPermissionsHelper", projectRepoPermissionsHelper);
        setField(repoConfigResource, "securityService", securityService);
        repoCreationMetricsWrapperSpy = Mockito.spy(new RepoCreationMetricsWrapper(null));
        setField(repoConfigResource, "repoCreationMetricsWrapper", repoCreationMetricsWrapperSpy);
        repoConfigResource.setArtifactoryResponse(mock(ArtifactoryRestResponse.class));
        doReturn(mock(Response.class)).when(repoConfigResource).runService(any(), any());
        homeStub.setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(), "false");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testCreateRepository() {
        RepositoryConfigModel<?, ?, ?> repositoryConfigModel = mock(RepositoryConfigModel.class);
        GeneralRepositoryConfigModel generalRepositoryConfigModel = mock(GeneralRepositoryConfigModel.class);
        when(repositoryConfigModel.getGeneral()).thenReturn(generalRepositoryConfigModel);
        when(generalRepositoryConfigModel.getRepoKey()).thenReturn("cool-repo");
        when(generalRepositoryConfigModel.getProjectKey()).thenReturn("cool-project");
        when(projectsService.canDoToProjectRepoResource(RepoActionType.CREATE, "cool-repo", "cool-project",
                null)).thenReturn(true);
        repoConfigResource.createRepository(repositoryConfigModel);
        verify(repoCreationMetricsWrapperSpy, times(1)).createRepoAndCollectMetrics(any(), any(), any(), any());
    }

    @Test
    void testExceptionThrownWhenNoProjectPermissions() {
        RepositoryConfigModel<?, ?, ?> repositoryConfigModel = mock(RepositoryConfigModel.class);
        GeneralRepositoryConfigModel generalRepositoryConfigModel = mock(GeneralRepositoryConfigModel.class);
        when(repositoryConfigModel.getGeneral()).thenReturn(generalRepositoryConfigModel);
        when(generalRepositoryConfigModel.getRepoKey()).thenReturn("cool-repo");
        when(generalRepositoryConfigModel.getProjectKey()).thenReturn("cool-project");
        when(projectsService.canDoToProjectRepoResource(RepoActionType.CREATE, "cool-repo",
                "cool-project", null)).thenReturn(false);
        doCallRealMethod().when(repoCreationMetricsWrapperSpy).createRepoAndCollectMetrics(any(), any(), any(), any());
        assertThrows(AuthorizationRestException.class,
                () -> repoConfigResource.createRepository(repositoryConfigModel));
        verifyNoInteractions(configServiceFactory);
    }

    @Test
    void testGetVirtualAllowedRepos() {
        // arrange
        VirtualAllowedReposModel requestModel = mock(VirtualAllowedReposModel.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        // act
        Response response = repoConfigResource.getVirtualAllowedRepos(requestModel);
        // assert
        assertNotNull(response);
    }

    @Test
    void testGetVirtualAllowedReposThrowsAuthorizationRestException() {
        // arrange
        VirtualAllowedReposModel requestModel = mock(VirtualAllowedReposModel.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(false);
        // act + assert
        assertThrows(AuthorizationRestException.class,
                () -> repoConfigResource.getVirtualAllowedRepos(requestModel));
    }

    @Test
    void validateMetadataCachePeriod_shouldSucceed_whenUserIsProjectAdmin() {
        MetadataCachePeriodExclusionRequestModel requestModel =
                mock(MetadataCachePeriodExclusionRequestModel.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);

        Response response = repoConfigResource.validateMetadataCachePeriod(requestModel);

        assertNotNull(response);
        verify(configServiceFactory).validateMetadataCachePeriod();
    }

    @Test
    void validateMetadataCachePeriod_shouldThrowAuthorizationRestException_whenUserIsNotProjectAdmin() {
        MetadataCachePeriodExclusionRequestModel requestModel =
                mock(MetadataCachePeriodExclusionRequestModel.class);
        when(securityService.isAnyProjectAdmin()).thenReturn(false);

        assertThrows(AuthorizationRestException.class,
                () -> repoConfigResource.validateMetadataCachePeriod(requestModel));
        verify(configServiceFactory, times(0)).validateMetadataCachePeriod();
    }

    @Test
    void deleteRepository_shouldThrowAuthorizationException_whenUserCannotManageRepo() {
        // Arrange
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repositoryConfigService.getRepoConfigByKey("test-repo")).thenReturn(repoConfig);
        doNothing().when(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        when(projectsService.canDoToProjectRepoResource(RepoActionType.DELETE, "test-repo", RepoDetailsType.LOCAL))
                .thenReturn(false);
        // Act & Assert
        assertThrows(AuthorizationRestException.class,
                () -> repoConfigResource.deleteRepository("test-repo"));
        // Verify
        verify(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        verify(repositoryConfigService).getRepoConfigByKey("test-repo");
        verify(projectsService).canDoToProjectRepoResource(RepoActionType.DELETE, "test-repo", RepoDetailsType.LOCAL);
        verify(configServiceFactory, times(0)).deleteRepositoryConfig();
    }

    @Test
    void deleteRepository_shouldThrowNotFoundException_whenRepoDoesNotExist() {
        // Arrange
        when(repositoryConfigService.getRepoConfigByKey("non-existent-repo")).thenReturn(null);
        doNothing().when(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        // Act & Assert
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> repoConfigResource.deleteRepository("non-existent-repo"));
        assertEquals("Repository 'non-existent-repo' not found", exception.getMessage());
        // Verify
        verify(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        verify(repositoryConfigService).getRepoConfigByKey("non-existent-repo");
        verify(projectsService, times(0))
                .canDoToProjectRepoResource(any(), anyString(), any(RepoDetailsType.class));
        verify(configServiceFactory, times(0)).deleteRepositoryConfig();
    }

    @Test
    void updateRepositoryConfig_shouldSucceed_whenUserHasPermission() {
        // Arrange
        RepositoryConfigModel<?, ?, ?> repositoryConfigModel = mock(RepositoryConfigModel.class);
        GeneralRepositoryConfigModel generalRepositoryConfigModel = mock(GeneralRepositoryConfigModel.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repositoryConfigModel.getGeneral()).thenReturn(generalRepositoryConfigModel);
        when(generalRepositoryConfigModel.getRepoKey()).thenReturn("test-repo");
        when(generalRepositoryConfigModel.getProjectKey()).thenReturn("projectA");
        when(repositoryConfigService.getRepoConfigByKey("test-repo")).thenReturn(repoConfig);
        doNothing().when(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        when(projectRepoPermissionsHelper.projAdminNotAuthorizedToUpdateRepo(repoConfig, "projectA"))
                .thenReturn(false);
        // Act
        repoConfigResource.updateRepositoryConfig(repositoryConfigModel);
        // Assert
        verify(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        verify(repositoryConfigService).getRepoConfigByKey("test-repo");
        verify(projectRepoPermissionsHelper).projAdminNotAuthorizedToUpdateRepo(repoConfig, "projectA");
        verify(configServiceFactory).updateRepositoryConfig();
    }

    @Test
    void updateRepositoryConfig_shouldThrowNotFoundException_whenRepoDoesNotExist() {
        // Arrange
        RepositoryConfigModel<?, ?, ?> repositoryConfigModel = mock(RepositoryConfigModel.class);
        GeneralRepositoryConfigModel generalRepositoryConfigModel = mock(GeneralRepositoryConfigModel.class);
        when(repositoryConfigModel.getGeneral()).thenReturn(generalRepositoryConfigModel);
        when(generalRepositoryConfigModel.getRepoKey()).thenReturn("non-existent-repo");
        when(repositoryConfigService.getRepoConfigByKey("non-existent-repo")).thenReturn(null);
        doNothing().when(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        // Act & Assert
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> repoConfigResource.updateRepositoryConfig(repositoryConfigModel));
        assertEquals("Repository 'non-existent-repo' not found", exception.getMessage());
        verify(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        verify(repositoryConfigService).getRepoConfigByKey("non-existent-repo");
        verify(configServiceFactory, times(0)).updateRepositoryConfig();
    }

    @Test
    void updateRepositoryConfig_shouldThrowAuthorizationException_whenUserLacksPermissionToUpdate() {
        // Arrange
        RepositoryConfigModel<?, ?, ?> repositoryConfigModel = mock(RepositoryConfigModel.class);
        GeneralRepositoryConfigModel generalRepositoryConfigModel = mock(GeneralRepositoryConfigModel.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repositoryConfigModel.getGeneral()).thenReturn(generalRepositoryConfigModel);
        when(generalRepositoryConfigModel.getRepoKey()).thenReturn("test-repo");
        when(generalRepositoryConfigModel.getProjectKey()).thenReturn("projectB");
        when(repositoryConfigService.getRepoConfigByKey("test-repo")).thenReturn(repoConfig);
        doNothing().when(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        when(projectRepoPermissionsHelper.projAdminNotAuthorizedToUpdateRepo(repoConfig, "projectB"))
                .thenReturn(true);
        // Act & Assert
        assertThrows(AuthorizationRestException.class,
                () -> repoConfigResource.updateRepositoryConfig(repositoryConfigModel));
        verify(projectRepoPermissionsHelper).validateAnyProjectAdmin();
        verify(repositoryConfigService).getRepoConfigByKey("test-repo");
        verify(projectRepoPermissionsHelper).projAdminNotAuthorizedToUpdateRepo(repoConfig, "projectB");
        verify(configServiceFactory, times(0)).updateRepositoryConfig();
    }

    @Test
    void getRepositoryConfigByType_shouldThrowAuthorizationRestException_whenCallerCannotReadFullRepoConfig() {
        // RTDEV-93295: a restricted project admin that cannot read the full repo config must be denied rather than
        // handed the credential-bearing model, matching the full-config read boundary the REST API enforces.
        when(projectsService.canReadFullRepoConfig("secret-repo", RepoDetailsType.REMOTE)).thenReturn(false);

        assertThrows(AuthorizationRestException.class,
                () -> repoConfigResource.getRepositoryConfigByType("remote", "secret-repo"));

        verify(projectsService).canReadFullRepoConfig("secret-repo", RepoDetailsType.REMOTE);
        verify(configServiceFactory, times(0)).getRepositoryConfig();
    }
}