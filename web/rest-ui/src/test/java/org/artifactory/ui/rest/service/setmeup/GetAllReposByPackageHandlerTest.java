package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetAllReposByPackageHandlerTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AuthorizationService authService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private ArtifactoryRestRequest<Void> request;
    @Mock
    private RestResponse<SetMeUpModel> response;
    @InjectMocks
    private GetAllReposByPackageHandler handler;
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
    void execute_shouldIncludeProjectScopedVirtualRepo_whenVisibilityFilterIsPassThrough() {
        arrangeGenericRequestWithGlobalLocalAndProjectScopedVirtual();
        when(projectsService.createVirtualReposVisibilityFilter(any())).thenReturn(repo -> true);

        handler.execute(request, response);

        List<String> resultKeys = captureReturnedRepoKeys();
        assertEquals(2, resultKeys.size());
        assertTrue(resultKeys.contains("general-local"));
        assertTrue(resultKeys.contains("proj-a-key-project-a-virtual"));
    }

    @Test
    void execute_shouldHideProjectScopedVirtualRepo_whenVisibilityFilterIsActive() {
        arrangeGenericRequestWithGlobalLocalAndProjectScopedVirtual();
        Predicate<RepoConfig> hideProjAKeyVirtuals = repo ->
                !(repo instanceof VirtualRepoConfig && repo.getKey().startsWith("proj-a-key"));
        when(projectsService.createVirtualReposVisibilityFilter(any())).thenReturn(hideProjAKeyVirtuals);

        handler.execute(request, response);

        List<String> resultKeys = captureReturnedRepoKeys();
        assertEquals(1, resultKeys.size());
        assertTrue(resultKeys.contains("general-local"));
    }

    private void arrangeGenericRequestWithGlobalLocalAndProjectScopedVirtual() {
        when(request.getPathParamByKey("packageType")).thenReturn("generic");
        when(repositoryConfigService.getRepoConfigsBySearchPattern(any(), any(), any(), any()))
                .thenReturn(List.of(
                        LocalRepoConfig.create("general-local", RepoType.Generic),
                        VirtualRepoConfig.create("proj-a-key-project-a-virtual", RepoType.Generic)
                ));
        when(authService.userHasPermissionsOnRepositoryRoot(any(), any(), any())).thenReturn(true);
    }

    private List<String> captureReturnedRepoKeys() {
        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());
        return captor.getValue().getRepoKeyTypes().stream()
                .map(RepoKeyType::getRepoKey)
                .toList();
    }
}
