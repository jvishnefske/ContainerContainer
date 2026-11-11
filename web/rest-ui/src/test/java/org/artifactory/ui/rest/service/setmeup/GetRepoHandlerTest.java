package org.artifactory.ui.rest.service.setmeup;

import java.util.Set;

import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.artifactory.security.ArtifactoryPermission.DEPLOY;
import static org.artifactory.security.ArtifactoryPermission.READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetRepoHandlerTest {
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private InternalRepositoryConfigService repositoryConfigService;
    @Mock
    private ArtifactoryRestRequest<Void> request;
    @Mock
    private RestResponse<SetMeUpModel> response;
    @InjectMocks
    private GetRepoHandler getRepoHandler;
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
    void testExecuteWithValidRepoKey() {
        String repoKey = "valid-repo";
        RepoConfig repoConfig = LocalRepoConfig.create(repoKey, RepoType.Maven);

        when(request.getPathParamByKey(GetRepoHandler.REPO_KEY)).thenReturn(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(READ), null)).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(DEPLOY), null)).thenReturn(true);

        getRepoHandler.execute(request, response);

        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());
        SetMeUpModel setMeUpModel = captor.getValue();
        assertNotNull(setMeUpModel);
        List<RepoKeyType> repoKeyTypes = setMeUpModel.getRepoKeyTypes();
        assertNotNull(repoKeyTypes);
        assertEquals(1, repoKeyTypes.size());
        assertEquals(repoKey, repoKeyTypes.getFirst().getRepoKey());
    }

    @Test
    void testExecuteWithMissingRepoKey() {
        when(request.getPathParamByKey(GetRepoHandler.REPO_KEY)).thenReturn(null);

        assertThrows(BadRequestException.class, () -> getRepoHandler.execute(request, response));
    }

    @Test
    void testExecuteWithNonExistentRepo() {
        String repoKey = "non-existent-repo";

        when(request.getPathParamByKey(GetRepoHandler.REPO_KEY)).thenReturn(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey, true)).thenReturn(null);

        assertThrows(BadRequestException.class, () -> getRepoHandler.execute(request, response));
    }

    @Test
    void testExecuteWithNoPermissions() {
        String repoKey = "no-permissions-repo";
        RepoConfig repoConfig = LocalRepoConfig.create(repoKey, RepoType.Maven);

        when(request.getPathParamByKey(GetRepoHandler.REPO_KEY)).thenReturn(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(READ), null)).thenReturn(false);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(DEPLOY), null)).thenReturn(false);

        getRepoHandler.execute(request, response);

        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());
        SetMeUpModel setMeUpModel = captor.getValue();
        assertNotNull(setMeUpModel);
        List<RepoKeyType> repoKeyTypes = setMeUpModel.getRepoKeyTypes();
        assertTrue(repoKeyTypes.isEmpty());
    }

    @Test
    void testExecuteWithIllegalArgumentException() {
        String repoKey = "support-bundle";

        when(request.getPathParamByKey(GetRepoHandler.REPO_KEY)).thenReturn(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey, true)).thenThrow(
                new IllegalArgumentException("Invalid repository key"));

        assertThrows(BadRequestException.class, () -> getRepoHandler.execute(request, response));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExecuteOnVirtual(boolean hasDefaultDeployRepo) {
        String repoKey = "virtual-repo";
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create(repoKey, RepoType.Maven);
        String localRepoKey = "local-repo";
        repoConfig.getRepoTypeConfig().setRepositoryRefs(List.of(localRepoKey));
        if (hasDefaultDeployRepo) {
            repoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(localRepoKey);
        }
        when(request.getPathParamByKey(GetRepoHandler.REPO_KEY)).thenReturn(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey, true)).thenReturn(repoConfig);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(READ), null)).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(DEPLOY), null)).thenReturn(false);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(localRepoKey, Set.of(DEPLOY), null)).thenReturn(
                true);

        getRepoHandler.execute(request, response);

        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());
        SetMeUpModel setMeUpModel = captor.getValue();
        assertNotNull(setMeUpModel);
        List<RepoKeyType> repoKeyTypes = setMeUpModel.getRepoKeyTypes();
        assertFalse(repoKeyTypes.isEmpty());
        assertEquals(1, repoKeyTypes.size());
        RepoKeyType repoKeyType = repoKeyTypes.getFirst();
        assertEquals(hasDefaultDeployRepo, repoKeyType.getIsDefaultDeploymentConfigured());
        assertNull(repoKeyType.getIsLocal());
        assertNull(repoKeyType.getIsRemote());
        assertTrue(repoKeyType.getIsVirtual());
        assertTrue(repoKeyType.getCanRead());
        assertEquals(hasDefaultDeployRepo, repoKeyType.getCanDeploy());
    }
}