package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.SecurityConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Nir
 */
class GetTerraformConfigSnippetServiceTest {
    public static final String REPO_KEY = "repoKey";
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private ArtifactoryRestRequest<Void> request;
    private RestResponse<String> response;
    private GetTerraformConfigSnippetService terraformConfigSnippetService;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        response = new ArtifactoryRestResponse<>();
        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        terraformConfigSnippetService = new GetTerraformConfigSnippetService(repositoryService, authorizationService);
    }

    @AfterEach
    protected void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void testExecuteRepoNotExists() {
        assertThrows(NotFoundException.class, () -> terraformConfigSnippetService.execute(request, response));
    }

    @Test
    void testExecuteNoReadPermission() {
        when(repositoryService.existsByPackageType(any(), any())).thenReturn(true);
        assertThrows(NotFoundException.class, () -> terraformConfigSnippetService.execute(request, response));
    }

    @Test
    void testExecuteRepoIsNotLocalOrFederated() {
        when(repositoryService.existsByPackageType(any(), any())).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any(), any(), any())).thenReturn(true);
        assertThrows(BadRequestException.class, () -> terraformConfigSnippetService.execute(request, response));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExecuteProvider(boolean withPrimaryKeyPairRef) {
        when(repositoryService.existsByPackageType(any(), any())).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any(), any(), any())).thenReturn(true);
        mockValidRepoConfig("provider", withPrimaryKeyPairRef ? "primaryKeyPairRef" : null);
        terraformConfigSnippetService.execute(request, response);
        assertNotNull(response.getIModel());
        if (withPrimaryKeyPairRef) {
            assertEquals("{registryType=providerRegistryProtocol}", response.getIModel().toString());
        } else {
            assertEquals("{registryType=providerNetworkMirror}", response.getIModel().toString());
        }
    }

    @Test
    void testExecuteModule() {
        when(repositoryService.existsByPackageType(any(), any())).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any(), any(), any())).thenReturn(true);
        mockValidRepoConfig("module", null);
        terraformConfigSnippetService.execute(request, response);
        assertNotNull(response.getIModel());
        assertEquals("{registryType=module}", response.getIModel().toString());
    }

    private void mockValidRepoConfig(String terraformType, String primaryKeyPairRef) {
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        Map<String, String> packageTypeConfig = new HashMap<>();
        packageTypeConfig.put("terraformType", terraformType);
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repositoryService.getRepositoryConfigByKey(REPO_KEY)).thenReturn(repoConfig);
        LocalRepo localRepo = mock(LocalRepo.class, RETURNS_DEEP_STUBS);
        SecurityConfig securityConfig = mock(SecurityConfig.class);
        when(securityConfig.getPrimaryKeyPairRef()).thenReturn(primaryKeyPairRef);
        when(repoConfig.getSecurityConfig()).thenReturn(securityConfig);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        when(repositoryService.localOrFederatedRepositoryByKey(REPO_KEY)).thenReturn(localRepo);
    }


}