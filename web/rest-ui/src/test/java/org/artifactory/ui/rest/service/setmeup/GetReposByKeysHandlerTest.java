package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.setmeup.GetReposByKeysRequest;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.artifactory.security.ArtifactoryPermission.DEPLOY;
import static org.artifactory.security.ArtifactoryPermission.READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetReposByKeysHandlerTest {

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private InternalRepositoryConfigService repositoryConfigService;

    @Mock
    private ArtifactoryRestRequest<GetReposByKeysRequest> request;

    @Mock
    private RestResponse<SetMeUpModel> response;

    @InjectMocks
    private GetReposByKeysHandler handler;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testExecuteWithValidRepoKeys() {
        // Given
        List<String> repoKeys = List.of("repo1", "repo2");
        GetReposByKeysRequest requestModel = new GetReposByKeysRequest(repoKeys);

        RepoConfig repo1Config = LocalRepoConfig.create("repo1", RepoType.Maven);
        RepoConfig repo2Config = LocalRepoConfig.create("repo2", RepoType.Docker);

        when(request.getImodel()).thenReturn(requestModel);
        when(repositoryConfigService.getRepoConfigsByKeys(repoKeys)).thenReturn(
                Set.of(repo1Config, repo2Config));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("repo1"), eq(Set.of(READ)), any()))
                .thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("repo2"), eq(Set.of(READ)), any()))
                .thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("repo1"), eq(Set.of(DEPLOY)), any()))
                .thenReturn(false);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("repo2"), eq(Set.of(DEPLOY)), any()))
                .thenReturn(true);

        // When
        handler.execute(request, response);

        // Then
        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());

        SetMeUpModel result = captor.getValue();
        assertNotNull(result);
        List<RepoKeyType> repoKeyTypes = result.getRepoKeyTypes();
        assertEquals(2, repoKeyTypes.size());

        RepoKeyType repo1Type = repoKeyTypes.getFirst();
        assertTrue(repo1Type.getCanRead());
        assertFalse(repo1Type.getCanDeploy());

        RepoKeyType repo2Type = repoKeyTypes.get(1);
        assertTrue(repo2Type.getCanRead());
        assertTrue(repo2Type.getCanDeploy());
    }

    @Test
    void testExecuteWithNullRequest() {
        // Given
        when(request.getImodel()).thenReturn(null);

        // When/Then
        assertThrows(BadRequestException.class, () -> handler.execute(request, response));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testExecuteWithInvalidRepoKeysList(List<String> repoKeys) {
        // Given
        GetReposByKeysRequest requestModel = new GetReposByKeysRequest(repoKeys);
        when(request.getImodel()).thenReturn(requestModel);

        // When/Then
        assertThrows(BadRequestException.class, () -> handler.execute(request, response));
    }

    @Test
    void testExecuteWithNonExistentRepo() {
        List<String> repoKeys = List.of("existing-repo", "non-existent-repo");
        GetReposByKeysRequest requestModel = new GetReposByKeysRequest(repoKeys);

        RepoConfig existingRepoConfig = LocalRepoConfig.create("existing-repo", RepoType.Maven);

        when(request.getImodel()).thenReturn(requestModel);
        when(repositoryConfigService.getRepoConfigsByKeys(repoKeys)).thenReturn(Set.of(existingRepoConfig));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("existing-repo"), eq(Set.of(READ)), any()))
                .thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("existing-repo"), eq(Set.of(DEPLOY)), any()))
                .thenReturn(false);

        handler.execute(request, response);

        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());

        SetMeUpModel result = captor.getValue();
        assertNotNull(result);
        List<RepoKeyType> repoKeyTypes = result.getRepoKeyTypes();
        assertEquals(1, repoKeyTypes.size());
        assertEquals("existing-repo", repoKeyTypes.getFirst().getRepoKey());
    }

    @Test
    void testExecuteWithNoPermissions() {
        // Given
        List<String> repoKeys = List.of("no-permissions-repo");
        GetReposByKeysRequest requestModel = new GetReposByKeysRequest(repoKeys);

        RepoConfig repoConfig = LocalRepoConfig.create("no-permissions-repo", RepoType.Maven);

        when(request.getImodel()).thenReturn(requestModel);
        when(repositoryConfigService.getRepoConfigsByKeys(repoKeys)).thenReturn(Set.of(repoConfig));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("no-permissions-repo"), eq(Set.of(READ)),
                any()))
                .thenReturn(false);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("no-permissions-repo"), eq(Set.of(DEPLOY)),
                any()))
                .thenReturn(false);

        // When
        handler.execute(request, response);

        // Then
        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());

        SetMeUpModel result = captor.getValue();
        assertNotNull(result);
        List<RepoKeyType> repoKeyTypes = result.getRepoKeyTypes();
        assertTrue(repoKeyTypes.isEmpty());
    }

    @Test
    void testExecuteWithVirtualRepoWithDefaultDeployment() {
        // Given
        List<String> repoKeys = List.of("virtual-repo");
        GetReposByKeysRequest requestModel = new GetReposByKeysRequest(repoKeys);

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.Maven);
        VirtualRepositoryRepoTypeConfig virtualTypeConfig =
                virtualRepoConfig.getRepoTypeConfig();
        virtualTypeConfig.setDefaultDeploymentRepoRef("default-deploy-repo");

        when(request.getImodel()).thenReturn(requestModel);
        when(repositoryConfigService.getRepoConfigsByKeys(repoKeys)).thenReturn(Set.of(virtualRepoConfig));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("virtual-repo"), eq(Set.of(READ)), any()))
                .thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("default-deploy-repo"), eq(Set.of(DEPLOY)),
                any())).thenReturn(true);

        // When
        handler.execute(request, response);

        // Then
        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());

        SetMeUpModel result = captor.getValue();
        assertNotNull(result);
        List<RepoKeyType> repoKeyTypes = result.getRepoKeyTypes();
        assertEquals(1, repoKeyTypes.size());

        RepoKeyType repoKeyType = repoKeyTypes.getFirst();
        assertEquals("virtual-repo", repoKeyType.getRepoKey());
        assertTrue(repoKeyType.getCanRead());
        assertTrue(repoKeyType.getCanDeploy());
        assertTrue(repoKeyType.getIsVirtual());
        assertTrue(repoKeyType.getIsDefaultDeploymentConfigured());
    }

    @Test
    void testExecuteWithVirtualRepoWithoutDefaultDeployment() {
        // Given
        List<String> repoKeys = List.of("virtual-repo");
        GetReposByKeysRequest requestModel = new GetReposByKeysRequest(repoKeys);

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.Maven);
        // No default deployment repo set

        when(request.getImodel()).thenReturn(requestModel);
        when(repositoryConfigService.getRepoConfigsByKeys(repoKeys)).thenReturn(Set.of(virtualRepoConfig));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("virtual-repo"), eq(Set.of(READ)), any()))
                .thenReturn(true);

        // When
        handler.execute(request, response);

        // Then
        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());

        SetMeUpModel result = captor.getValue();
        assertNotNull(result);
        List<RepoKeyType> repoKeyTypes = result.getRepoKeyTypes();
        assertEquals(1, repoKeyTypes.size());

        RepoKeyType repoKeyType = repoKeyTypes.getFirst();
        assertEquals("virtual-repo", repoKeyType.getRepoKey());
        assertTrue(repoKeyType.getCanRead());
        assertFalse(repoKeyType.getCanDeploy());
        assertTrue(repoKeyType.getIsVirtual());
    }
}