package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.suppliers.BuildInfoRepo;
import org.artifactory.repo.system.suppliers.FullSyncRepo;
import org.artifactory.repo.system.suppliers.InTransitRepo;
import org.artifactory.repo.system.suppliers.PipeinfoRepo;
import org.artifactory.repo.system.suppliers.UsageRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.PackageSearchCriteria;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isInfoRepo;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.PIPEINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.ui.utils.RequestUtils.FED_ATTR_OPT_IN_PARAM;
import static org.artifactory.ui.utils.RequestUtils.PROJECT_KEY_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
public abstract class RepositoriesDataHandlerTestBase extends ArtifactoryHomeBoundJUnitTest {
    public static final String PROJECT_KEY = "project-key";
    public static final String NON_MATCH_REPO_KEY_SEARCH = "non-match-repo-key-search";
    @Mock
    protected AuthorizationService authorizationService;
    @Mock
    protected SecurityService securityService;
    @Mock
    protected InternalRepositoryService repositoryService;
    @Mock
    protected CentralConfigService centralConfigService;
    @Mock
    protected ProjectsService projectsService;
    @Mock
    protected MutableCentralConfigDescriptor descriptor;
    @Mock
    protected ArtifactoryContext context;
    @Mock
    protected ArtifactoryRestRequest request;
    private AutoCloseable closeable;
    protected final List<RepoConfig> allRepoConfigs = new ArrayList<>();
    protected final List<RepoConfig> hiddenRepoConfigs = new ArrayList<>();
    protected final List<RepoConfig> infoRepoConfigs = new ArrayList<>();
    protected final Map<RepoType, PackageSearchCriteria.PackageSearchType> repoTypePackageSearchTypeMap =
            new EnumMap<>(RepoType.class);
    protected ArtifactoryRestResponse response;

    @BeforeEach
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        setupMocks();
        setupRepoConfigsAndRepoDescriptors();
        setupRepoTypePackageSearchTypeMap();
        response = new ArtifactoryRestResponse();
    }

    @AfterEach
    public void teardown() throws Exception {
        unbindArtifactoryHome();
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    protected <T> AllRequestHandler<T> createAllRequestHandler() {
        AllRequestHandler<T> allRequestHandler = new AllRequestHandler<>();
        configureHandler(allRequestHandler);
        return allRequestHandler;
    }

    protected <T> DefaultRequestHandler<T> createDefaultRequestHandler() {
        DefaultRequestHandler<T> defaultRequestHandler = new DefaultRequestHandler<>();
        configureHandler(defaultRequestHandler);
        return defaultRequestHandler;
    }

    protected <T> DeployRequestHandler<T> createDeployRequestHandler() {
        DeployRequestHandler<T> deployRequestHandlerTest = new DeployRequestHandler<>(centralConfigService);
        configureHandler(deployRequestHandlerTest);
        return deployRequestHandlerTest;
    }

    protected <T> PackageSearchRequestHandler<T> createPackageSearchRequestHandler() {
        PackageSearchRequestHandler<T> packageSearchRequestHandler = new PackageSearchRequestHandler<>();
        configureHandler(packageSearchRequestHandler);
        return packageSearchRequestHandler;
    }

    protected <T> PermissionRequestHandler<T> createPermissionRequestHandler() {
        PermissionRequestHandler<T> permissionRequestHandler = new PermissionRequestHandler<>();
        configureHandler(permissionRequestHandler);
        return permissionRequestHandler;
    }

    protected <T> SearchRequestHandler<T> createSearchRequestHandler() {
        SearchRequestHandler<T> searchRequestHandler = new SearchRequestHandler<>();
        configureHandler(searchRequestHandler);
        return searchRequestHandler;
    }

    protected <T> UserRequestHandler<T> createUserRequestHandler() {
        UserRequestHandler<T> userRequestHandler = new UserRequestHandler<>();
        configureHandler(userRequestHandler);
        return userRequestHandler;
    }

    private <T> void configureHandler(AbstractGetRepositoriesDataHandler<T> handler) {
        handler.setRepositoryService(repositoryService);
        handler.setAuthorizationService(authorizationService);
        handler.setProjectsService(projectsService);
        handler.setSecurityService(securityService);
    }

    void mockRequestForProjectKey(String projectKey, String searchParam) {
        boolean isDefaultProject = ProjectsUtils.isDefaultProject(projectKey);
        doAnswer(invocation -> {
            Set<RepoDetailsType> repoTypeSet = invocation.getArgument(0);
            String searchPattern = invocation.getArgument(2);
            boolean isSearchPatternEmpty = StringUtils.isBlank(searchPattern);
            return allRepoConfigs.stream()
                    .filter(repoConfig -> !(repoConfig instanceof LocalCacheRepoConfig))
                    .filter(repoConfig -> repoTypeSet.contains(repoConfig.getRepoType()))
                    .filter(repoConfig -> isDefaultProject || repoConfig.getKey().startsWith(projectKey))
                    .filter(repoConfig -> isSearchPatternEmpty || repoConfig.getKey().contains(searchPattern))
                    .collect(Collectors.toList());
        }).when(repositoryService).getRepoConfigsBySearchPattern(any(), any(), any());

        doAnswer(invocation -> {
            Set<RepoDetailsType> repoTypeSet = invocation.getArgument(0);
            Set<RepoType> packageTypes = invocation.getArgument(1);
            String searchPattern = invocation.getArgument(3);
            boolean isSearchPatternEmpty = StringUtils.isBlank(searchPattern);
            return allRepoConfigs.stream()
                    .filter(repoConfig -> !(repoConfig instanceof LocalCacheRepoConfig))
                    .filter(repoConfig -> packageTypes.contains(repoConfig.getPackageType()))
                    .filter(repoConfig -> repoTypeSet.contains(repoConfig.getRepoType()))
                    .filter(repoConfig -> isDefaultProject || repoConfig.getKey().startsWith(projectKey))
                    .filter(repoConfig -> isSearchPatternEmpty || repoConfig.getKey().contains(searchPattern))
                    .collect(Collectors.toList());
        }).when(repositoryService).getRepoConfigsBySearchPattern(any(), any(), any(), any());

        doAnswer(invocation -> allRepoConfigs.stream()
                .filter(repoConfig -> (repoConfig instanceof LocalCacheRepoConfig))
                .filter(repoConfig -> isDefaultProject || repoConfig.getKey().startsWith(projectKey))
                .collect(Collectors.toSet())).when(repositoryService).getLocalCacheRepoConfigsByProject(any());

        doAnswer(invocation -> {
            String repoKey = invocation.getArgument(0);
            return allRepoConfigs.stream()
                    .filter(repoConfig -> repoConfig.getKey().equals(repoKey))
                    .findFirst().orElse(null);
        }).when(repositoryService).repoConfigByKey(any());

        LocalRepo localRepo = mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn(SUPPORT_BUNDLE_REPO_KEY);
        when(localRepo.getRepoConfig()).thenReturn(LocalRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, RepoType.Support));
        when(repositoryService.localOrFederatedRepositoryByKey(SUPPORT_BUNDLE_REPO_KEY)).thenReturn(localRepo);
        when(request.getQueryParamByKey(PROJECT_KEY_PARAM)).thenReturn(projectKey);

        when(descriptor.getFileUploadMaxSizeMb()).thenReturn(100);
        if (searchParam != null) {
            when(request.getQueryParamByKey(searchParam)).thenReturn("true");
        }
    }

    private void setupMocks() {
        when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(authorizationService.canRead(any(RepoPath.class))).thenAnswer(invocation -> {
            RepoPath repoPath = invocation.getArgument(0);
            return !repoPath.getRepoKey().equals("unauthorized-read-repo");
        });
        when(authorizationService.canDeploy(any(RepoPath.class))).thenAnswer(invocation -> {
            RepoPath repoPath = invocation.getArgument(0);
            return !repoPath.getRepoKey().equals("unauthorized-deploy-repo");
        });
        when(authorizationService.hasBuildPermission(any(), any())).thenReturn(true);
    }

    private void setupRepoTypePackageSearchTypeMap() {
        Arrays.stream(PackageSearchCriteria.PackageSearchType.values())
                .forEach(packageSearchType -> repoTypePackageSearchTypeMap.put(packageSearchType.getRepoType(),
                        packageSearchType));
    }

    private void setupRepoConfigsAndRepoDescriptors() {
        int id = 1;
        for (RepoType repoType : RepoType.values()) {
            addRepoConfigAndDescriptor(repoType, id, null);
            id++;
            addRepoConfigAndDescriptor(repoType, id, PROJECT_KEY);
            id++;
        }
        addSpecialRepoConfigs();
    }

    private void addRepoConfigAndDescriptor(RepoType repoType, int id, String projectKey) {
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName("my-layout");
        repoLayout.setArtifactPathPattern("artifactPathPattern");
        repoLayout.setFileIntegrationRevisionRegExp("fileIntegrationRevisionRegExp");
        repoLayout.setFolderIntegrationRevisionRegExp("folderIntegrationRevisionRegExp");
        when(descriptor.getRepoLayout(repoLayout.getName())).thenReturn(repoLayout);
        String repoPrefix = projectKey != null ? projectKey + "-" : "";
        var localRepoConfig = LocalRepoConfig.create(String.format("%slocal-repo-%d", repoPrefix, id), repoType);
        localRepoConfig.getBaseConfig().setRepoLayoutRef(repoLayout.getName());
        var remoteRepoConfig = RemoteRepoConfig.create(String.format("%sremote-repo-%d", repoPrefix, id), repoType);
        remoteRepoConfig.getBaseConfig().setRepoLayoutRef(repoLayout.getName());
        var cacheRepoConfig = LocalCacheRepoConfig.create(remoteRepoConfig);
        var federatedRepoConfig =
                FederatedRepoConfig.create(String.format("%sfederated-repo-%d", repoPrefix, id), repoType);
        federatedRepoConfig.getBaseConfig().setRepoLayoutRef(repoLayout.getName());

        allRepoConfigs.addAll(List.of(localRepoConfig, remoteRepoConfig, cacheRepoConfig, federatedRepoConfig));
        LocalRepoDescriptor localRepoDescriptor = LocalRepoDescriptor.fromRepositoryConfig(descriptor, localRepoConfig);
        HttpRepoDescriptor remoteRepoDesc = HttpRepoDescriptor.fromRepositoryConfig(descriptor, remoteRepoConfig);
        LocalCacheRepoDescriptor localCacheRepoDescriptor = new LocalCacheRepoDescriptor();
        localCacheRepoDescriptor.setKey(remoteRepoDesc.getKey() + "-cache");
        localCacheRepoDescriptor.setRemoteRepo(remoteRepoDesc);

        var virtualRepoDesc = new VirtualRepoDescriptor();
        virtualRepoDesc.setKey(String.format("%svirtual-repo-%d", repoPrefix, id));
        virtualRepoDesc.setType(repoType);
        virtualRepoDesc.setDefaultDeploymentRepo(localRepoDescriptor);
        VirtualRepoConfig virtualRepoConfig = virtualRepoDesc.toRepositoryConfig();
        allRepoConfigs.add(virtualRepoConfig);

        if (projectKey != null && projectKey.equals(PROJECT_KEY)) {
            when(projectsService.getProjectRepositoriesKeys(eq(projectKey), anyBoolean())).thenReturn(
                    allRepoConfigs.stream().map(RepoConfig::getKey).collect(Collectors.toSet()));
        }
        if (isInfoRepo(repoType)) {
            infoRepoConfigs.addAll(
                    List.of(localRepoConfig, remoteRepoConfig, virtualRepoConfig, cacheRepoConfig,
                            federatedRepoConfig));
        }
    }

    private void addSpecialRepoConfigs() {
        var inTransitRepo = new InTransitRepo().buildRepoConfig(descriptor, IN_TRANSIT_REPO_KEY);
        var usageRepoConfig = new UsageRepo().buildRepoConfig(descriptor, USAGE_REPO_KEY);
        var fullSyncRepoConfig = new FullSyncRepo().buildRepoConfig(descriptor, FULL_SYNC_REPO_KEY);

        List.of(inTransitRepo, usageRepoConfig, fullSyncRepoConfig).forEach(systemRepo -> {
            allRepoConfigs.add(systemRepo);
            hiddenRepoConfigs.add(systemRepo);
        });

        var buildRepoConfig = new BuildInfoRepo().buildRepoConfig(descriptor, BUILDINFO_REPO_KEY);
        var pipeInfoRepoConfig = new PipeinfoRepo().buildRepoConfig(descriptor, PIPEINFO_REPO_KEY);

        Stream.of(buildRepoConfig, pipeInfoRepoConfig).forEach(infoRepo -> {
            allRepoConfigs.add(infoRepo);
            infoRepoConfigs.add(infoRepo);
        });

        var unauthorizedReadRepo = LocalRepoConfig.create("unauthorized-read-repo", RepoType.Generic);
        var unauthorizedDeployRepo = LocalRepoConfig.create("unauthorized-deploy-repo", RepoType.Generic);
        allRepoConfigs.add(unauthorizedReadRepo);
        allRepoConfigs.add(unauthorizedDeployRepo);
    }

    protected void setFedAttrOptInParam(boolean value) {
        when(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM)).thenReturn(value ? "true" : null);
    }

    protected void setLimitQueryParam(long limit) {
        when(request.getQueryParamByKey("limit")).thenReturn(String.valueOf(limit));
    }

    protected void setKeySearchQueryParam(String keySearch) {
        when(request.getQueryParamByKey("keySearch")).thenReturn(keySearch);
    }

    protected void assertLimit() {
        List<RepoKeyType> repoKeyTypes = getRepoKeyTypes();
        long limit = Long.parseLong(request.getQueryParamByKey("limit"));
        if (limit <= 0) {
            assertNull(repoKeyTypes);
        } else {
            assertNotNull(repoKeyTypes);
            assertEquals(limit, repoKeyTypes.size());
        }
    }

    protected void assertKeySearch(String keySearch) {
        if (keySearch == null) {
            return;
        }
        List<RepoKeyType> repoKeyTypes = getRepoKeyTypes();
        assertNotNull(repoKeyTypes);
        if (keySearch.equals(NON_MATCH_REPO_KEY_SEARCH)) {
            assertTrue(repoKeyTypes.isEmpty());
        } else {
            assertFalse(repoKeyTypes.isEmpty());
            assertTrue(repoKeyTypes.stream().allMatch(repoKeyType -> repoKeyType.getRepoKey().contains(keySearch)));
        }
    }

    protected static Stream<Arguments> searchAndProjectProvider() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(null, ""),
                Arguments.of(null, DEFAULT_PROJECT),
                Arguments.of(null, PROJECT_KEY),
                Arguments.of("", ""),
                Arguments.of("", DEFAULT_PROJECT),
                Arguments.of("", PROJECT_KEY),
                Arguments.of("repo", ""),
                Arguments.of("repo", DEFAULT_PROJECT),
                Arguments.of("repo", PROJECT_KEY),
                Arguments.of("local", ""),
                Arguments.of("local", DEFAULT_PROJECT),
                Arguments.of("local", PROJECT_KEY),
                Arguments.of("non-match-repo-key-search", ""),
                Arguments.of("non-match-repo-key-search", DEFAULT_PROJECT),
                Arguments.of("non-match-repo-key-search", PROJECT_KEY)
        );
    }

    @SuppressWarnings("unchecked")
    private List<RepoKeyType> getRepoKeyTypes() {
        List<RepoKeyType> repoKeyTypes;
        if (request.getQueryParamByKey("all") != null) {
            repoKeyTypes = (List<RepoKeyType>) response.getiModelList();
        } else {
            repoKeyTypes = ((RepositoriesData) response.getIModel()).getRepoTypesList();
        }
        return repoKeyTypes;
    }
}
