package org.artifactory.ui.rest.service.setmeup;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.utils.RequestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetPackageSetMeUpServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private ArtifactoryRestRequest<Void> request;
    @Mock
    private RestResponse<SetMeUpModel> response;
    private List<RepoConfig> repoConfigs;
    @InjectMocks
    private GetAllReposByPackageHandler getPackageSetMeUpService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        repoConfigs = createRepoConfigs();
        doAnswer(invocation -> {
            Set<RepoDetailsType> repoTypeSet = invocation.getArgument(0);
            Set<RepoType> packageTypes = invocation.getArgument(1);
            String projectKey = invocation.getArgument(2);
            String searchKey = invocation.getArgument(3);
            return repoConfigs.stream()
                    .filter(repoConfig -> repoTypeSet.contains(repoConfig.getRepoType()))
                    .filter(repoConfig -> ProjectsUtils.isDefaultProject(projectKey) ||
                            repoConfig.getKey().startsWith(projectKey))
                    .filter(repoConfig -> packageTypes.contains(repoConfig.getPackageType()))
                    .filter(repoConfig -> StringUtils.isBlank(searchKey) || repoConfig.getKey().contains(searchKey))
                    .toList();
        }).when(repositoryConfigService).getRepoConfigsBySearchPattern(any(), any(), any(), any());

        doAnswer(invocation -> {
            String repoKey = invocation.getArgument(0);
            Set<ArtifactoryPermission> permissions = invocation.getArgument(1);
            ArtifactoryPermission permission = permissions.iterator().next();
            if (permission == ArtifactoryPermission.READ) {
                return !repoKey.contains("not-permitted-read") && !repoKey.contains("not-permitted-at-all");
            }
            return !repoKey.contains("not-permitted-deploy") && !repoKey.contains("not-permitted-at-all");
        }).when(authorizationService).userHasPermissionsOnRepositoryRoot(any(), any(), any());
        when(projectsService.canManageProjectResources(any())).thenReturn(false);
        when(projectsService.createVirtualReposVisibilityFilter(any())).thenReturn(repoConfig -> true);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("projectKeyAndPackageTypeProvider")
    void testExecuteWithValidPackageType(String projectKey, String packageType) {
        when(request.getPathParamByKey("packageType")).thenReturn(packageType);
        when(RequestUtils.getProjectKeyFromRequest(request)).thenReturn(projectKey);

        getPackageSetMeUpService.execute(request, response);

        ArgumentCaptor<SetMeUpModel> captor = ArgumentCaptor.forClass(SetMeUpModel.class);
        verify(response).iModel(captor.capture());
        SetMeUpModel model = captor.getValue();
        assertNotNull(model);
        assertEquals(model.getRepoKeyTypes().size(), ProjectsUtils.isDefaultProject(projectKey) ? 12 : 6);
        assertTrue(model.getRepoKeyTypes().stream()
                .allMatch(repoKeyType -> ProjectsUtils.isDefaultProject(projectKey) ||
                        repoKeyType.getRepoKey().startsWith(projectKey)));
        model.getRepoKeyTypes().forEach(repoKeyType -> {
            assertEquals(repoKeyType.getRepoType().getType().toLowerCase(), packageType);
            if (repoKeyType.getRepoKey().contains("not-permitted-read")) {
                assertFalse(repoKeyType.getCanRead());
            } else {
                assertTrue(repoKeyType.getCanRead());
            }
            if (repoKeyType.getRepoKey().contains("not-permitted-deploy")) {
                assertFalse(repoKeyType.getCanDeploy());
            } else if (repoKeyType.getRepoKey().contains("virtual") &&
                    !repoKeyType.getIsDefaultDeploymentConfigured()) {
                assertFalse(repoKeyType.getCanDeploy(),
                        "Virtual repo without default deployment should not allow deploy: " + repoKeyType.getRepoKey());
            } else {
                assertTrue(repoKeyType.getCanDeploy());
            }
            if (repoKeyType.getRepoKey().contains("local")) {
                assertTrue(repoKeyType.getIsLocal());
            }
            if (repoKeyType.getRepoKey().contains("remote")) {
                assertTrue(repoKeyType.getIsRemote());
            }
            if (repoKeyType.getRepoKey().contains("federated")) {
                assertTrue(repoKeyType.getIsFederated());
            }
            if (repoKeyType.getRepoKey().contains("virtual")) {
                assertTrue(repoKeyType.getIsVirtual());
                assertEquals(repoKeyType.getRepoKey().contains("default-deployment"),
                        repoKeyType.getIsDefaultDeploymentConfigured());
            }
        });
    }

    private static Stream<Arguments> projectKeyAndPackageTypeProvider() {
        return Stream.of(
                Arguments.of(null, "maven"),
                Arguments.of(null, "npm"),
                Arguments.of("", "maven"),
                Arguments.of("", "npm"),
                Arguments.of("default", "maven"),
                Arguments.of("default", "npm"),
                Arguments.of("proj-1", "maven"),
                Arguments.of("proj-1", "npm"),
                Arguments.of("proj-2", "maven"),
                Arguments.of("proj-2", "npm"));
    }

    @Test
    void testExecuteWithInvalidPackageType() {
        when(request.getPathParamByKey("packageType")).thenReturn("invalid");

        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> getPackageSetMeUpService.execute(request, response));

        assertEquals("Invalid package type: invalid", exception.getMessage());
    }

    @Test
    void testExecuteWithMissingPackageType() {
        when(request.getPathParamByKey("packageType")).thenReturn(null);

        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> getPackageSetMeUpService.execute(request, response));

        assertEquals("Missing package type", exception.getMessage());
    }

    @Test
    void testGetMavenGroupRepoTypeWithValidType() {
        RepoType.getMavenGroupExcludeP2Types().forEach(packageType -> {
            Set<RepoType> repoType = getPackageSetMeUpService.getRepoTypes(packageType.getType());
            assertEquals(RepoType.getMavenGroupExcludeP2Types(), repoType);
        });
    }

    @Test
    void testGetRepoTypeWithValidType() {
        Set<RepoType> repoType = getPackageSetMeUpService.getRepoTypes("docker");
        assertEquals(Set.of(RepoType.Docker), repoType);
    }

    @Test
    void testGetRepoTypeWithInvalidType() {
        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> getPackageSetMeUpService.getRepoTypes("invalid"));

        assertEquals("Invalid package type: invalid", exception.getMessage());
    }

    @Test
    void testGetRepoTypeWithMissingType() {
        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> getPackageSetMeUpService.getRepoTypes(null));

        assertEquals("Missing package type", exception.getMessage());
    }

    List<RepoConfig> createRepoConfigs() {
        VirtualRepoConfig virtualRepoConfig =
                VirtualRepoConfig.create("proj-1-virtual-with-default-deployment", RepoType.Maven);
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("proj-1-local");
        return List.of(
                LocalRepoConfig.create("proj-1-local", RepoType.Maven),
                RemoteRepoConfig.create("proj-1-remote", RepoType.Maven),
                FederatedRepoConfig.create("proj-1-federated", RepoType.Maven),
                virtualRepoConfig,
                LocalRepoConfig.create("proj-1-not-permitted-read", RepoType.Maven),
                LocalRepoConfig.create("proj-1-not-permitted-deploy", RepoType.Maven),
                LocalRepoConfig.create("proj-1-not-permitted-at-all", RepoType.Maven),
                LocalRepoConfig.create("proj-1-local-npm", RepoType.Npm),
                RemoteRepoConfig.create("proj-1-remote-npm", RepoType.Npm),
                FederatedRepoConfig.create("proj-1-federated-npm", RepoType.Npm),
                VirtualRepoConfig.create("proj-1-virtual-npm", RepoType.Npm),
                LocalRepoConfig.create("proj-1-not-permitted-read-npm", RepoType.Npm),
                LocalRepoConfig.create("proj-1-not-permitted-deploy-npm", RepoType.Npm),
                LocalRepoConfig.create("proj-1-not-permitted-at-all-npm", RepoType.Npm),
                LocalRepoConfig.create("proj-2-local", RepoType.Maven),
                RemoteRepoConfig.create("proj-2-remote", RepoType.Maven),
                FederatedRepoConfig.create("proj-2-federated", RepoType.Maven),
                VirtualRepoConfig.create("proj-2-virtual", RepoType.Maven),
                LocalRepoConfig.create("proj-2-not-permitted-read", RepoType.Maven),
                LocalRepoConfig.create("proj-2-not-permitted-deploy", RepoType.Maven),
                LocalRepoConfig.create("proj-2-not-permitted-at-all", RepoType.Maven),
                LocalRepoConfig.create("proj-2-local-npm", RepoType.Npm),
                RemoteRepoConfig.create("proj-2-remote-npm", RepoType.Npm),
                FederatedRepoConfig.create("proj-2-federated-npm", RepoType.Npm),
                VirtualRepoConfig.create("proj-2-virtual-npm", RepoType.Npm),
                LocalRepoConfig.create("proj-2-not-permitted-read-npm", RepoType.Npm),
                LocalRepoConfig.create("proj-2-not-permitted-deploy-npm", RepoType.Npm),
                LocalRepoConfig.create("proj-2-not-permitted-at-all-npm", RepoType.Npm),
                LocalRepoConfig.create(IN_TRANSIT_REPO_KEY, RepoType.Maven),
                LocalRepoConfig.create(USAGE_REPO_KEY, RepoType.Npm)
        );
    }
}