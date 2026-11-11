package org.artifactory.ui.rest.service.setmeup;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

import lombok.NonNull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Alexei Vainshtein
 */
class GetSetMeUpServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String PROJECT_X = "projectX";
    private static final String REPO_A = "repoA";
    private static final String REPO_B = "repoB";
    private static final LocalRepoConfig defaultRepo =
            LocalRepoConfig.create(REPO_A, RepoType.Generic);
    private static final LocalRepoConfig projectRepo =
            LocalRepoConfig.create(REPO_B, RepoType.Generic);

    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @InjectMocks
    private GetSetMeUpService setMeUpService;
    private AutoCloseable mocks;

    private static Stream<Arguments> provideProjectName() {
        return Stream.of(
                Arguments.of(null, true, List.of(defaultRepo, projectRepo), 2),
                Arguments.of(EMPTY, true, List.of(defaultRepo, projectRepo), 2),
                Arguments.of(DEFAULT_PROJECT, true, List.of(defaultRepo, projectRepo), 2),
                Arguments.of(DEFAULT_PROJECT, false, List.of(defaultRepo, projectRepo), 0),
                Arguments.of(PROJECT_X, true, List.of(defaultRepo), 1),
                Arguments.of(PROJECT_X, false, List.of(defaultRepo), 0));
    }

    @BeforeEach
    public void setup() {
        mocks = openMocks(this);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testSetMeUpModelWithoutFlag() {
        setupMocks(false);
        ArtifactoryRestRequest request = getArtifactoryRestRequest();
        SetMeUpModel setMeUpModel = setMeUpService.createSetMeUpModel(request);
        assertFalse(setMeUpModel.getSkipDockerDns());
    }

    @Disabled("the test case is flaky")
    @Test
    public void testSetMeUpModelWithFlag() {
        setupMocks(true);
        ArtifactoryRestRequest request = getArtifactoryRestRequest();
        SetMeUpModel setMeUpModel = setMeUpService.createSetMeUpModel(request);
        assertTrue(setMeUpModel.getSkipDockerDns());
    }

    @ParameterizedTest
    @MethodSource("provideProjectName")
    public void filterRepositoriesByProjectName(String projectName, boolean hasPermissionOnRepoRoot,
            List<RepoConfig> repos, int expectedReposCount) {
        setupMocks(false);
        ArtifactoryRestRequest request = getArtifactoryRestRequest();
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(projectName);
        when(repositoryConfigService.getRepoConfigsByTypes(any())).thenReturn(repos);
        when(projectsService.getProjectRepositoriesKeys(eq(projectName), anyBoolean()))
                .thenReturn(repos.stream().map(RepoConfig::getKey).collect(toSet()));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(
                anyString(), eq(Set.of(ArtifactoryPermission.values())), anyMap())).thenReturn(hasPermissionOnRepoRoot);

        //act
        setMeUpService.execute(request, response);
        SetMeUpModel setMeUpModel = (SetMeUpModel) response.getIModel();

        assertEquals(expectedReposCount, setMeUpModel.getRepoKeyTypes().size());
    }

    @Test
    public void testInvisibleSystemReposAreFiltered() {
        setupMocks(false);
        ArtifactoryRestRequest request = getArtifactoryRestRequest();
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(DEFAULT_PROJECT);
        LocalRepoConfig repoA = LocalRepoConfig.create(REPO_A, RepoType.Generic);
        LocalRepoConfig usageRepo = LocalRepoConfig.create(USAGE_REPO_KEY, RepoType.Generic); //invisible
        LocalRepoConfig logShippingRepo = LocalRepoConfig.create(LOG_SHIPPING_REPO_KEY, RepoType.Generic);
        LocalRepoConfig supportBundleRepo = LocalRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, RepoType.Generic);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual", RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByTypes(any())).thenReturn(
                List.of(repoA, usageRepo, logShippingRepo, supportBundleRepo, virtualRepoConfig));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(
                anyString(), eq(Set.of(ArtifactoryPermission.values())), eq(new HashMap<>()))).thenReturn(true);
        setMeUpService.execute(request, response);
        SetMeUpModel setMeUpModel = (SetMeUpModel) response.getIModel();

        assertEquals(4, setMeUpModel.getRepoKeyTypes().size());
    }

    @Test
    public void testFederatedRepoKeyTypeIsSet() {
        setupMocks(false);
        ArtifactoryRestRequest request = getArtifactoryRestRequest();
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(request.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(DEFAULT_PROJECT);
        FederatedRepoConfig repoA = FederatedRepoConfig.create(REPO_A, RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByTypes(any())).thenReturn(List.of(repoA));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(
                anyString(), eq(Set.of(ArtifactoryPermission.values())), eq(new HashMap<>()))).thenReturn(true);
        setMeUpService.execute(request, response);
        SetMeUpModel setMeUpModel = (SetMeUpModel) response.getIModel();
        List<RepoKeyType> repoKeyTypes = setMeUpModel.getRepoKeyTypes();
        assertEquals(repoKeyTypes.size(), 1);
        assertTrue(repoKeyTypes.get(0).getIsFederated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    @NullAndEmptySource
    public void testSetRepoKeyTypes(String excludeRepoTypes) {
        setupMocks(false);
        var request = getArtifactoryRestRequest();
        var response = new ArtifactoryRestResponse<>();
        when(request.getQueryParamByKey("excludeRepoTypes")).thenReturn(excludeRepoTypes);
        when(request.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(DEFAULT_PROJECT);
        LocalRepoConfig repoA = LocalRepoConfig.create(REPO_A, RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByTypes(any())).thenReturn(List.of(repoA));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(
                anyString(), eq(Set.of(ArtifactoryPermission.values())), eq(new HashMap<>()))).thenReturn(true);

        setMeUpService.execute(request, response);
        SetMeUpModel setMeUpModel = response.getIModel();
        if ("true".equals(excludeRepoTypes)) {
            assertTrue(setMeUpModel.getRepoKeyTypes().isEmpty());
        } else {
            assertNotNull(setMeUpModel.getRepoKeyTypes());
            assertEquals(1, setMeUpModel.getRepoKeyTypes().size());
        }
    }

    @NonNull
    private ArtifactoryRestRequest getArtifactoryRestRequest() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL))
                .thenReturn("https://test.jfrog.ui/");
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getServletRequest()).thenReturn(servletRequest);
        return request;
    }

    private void setupMocks(boolean isSkipDns) {
        CoreAddons coreAddons = mock(CoreAddons.class);
        AolService aolService = mock(AolService.class);
        when(aolService.skipDockerDns()).thenReturn(isSkipDns);
        when(coreAddons.getArtifactoryServerName()).thenReturn("jfrog");
        when(addonsManager.addonByType(CoreAddons.class)).thenReturn(coreAddons);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(artifactoryContext.beanForType(AuthorizationService.class)).thenReturn(authorizationService);
        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(artifactoryContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        when(centralConfigService.getListeningPort()).thenReturn(-1);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }
}