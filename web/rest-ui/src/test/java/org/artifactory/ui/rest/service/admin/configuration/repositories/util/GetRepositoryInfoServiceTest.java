package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import com.google.common.collect.ImmutableSet;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.cache.RepoConfigCache;
import org.artifactory.repo.service.RepositoryConfigServiceImpl;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.LocalRepositoryInfo;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepositoryInfo;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectResourceStatus;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author BarakH
 */
@Test
public class GetRepositoryInfoServiceTest extends ArtifactoryHomeBoundTest {

    private enum UserType {SIMPLE, PROJECT_ADMIN, ADMIN;}

    private static final String REPO_TYPE = "repoType";
    private static final String PROJECT_A = "proj1";
    private static final String PROJECT_B = "proj2";
    private static final String REPO_A = "repo-a";
    private static final String DEFAULT_PROJECT_REPO = "DEFAULT_PROJECT_REPO";

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private CentralConfigService configService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private SecurityService securityService;
    @Mock
    private PackageSpecification packageSpecification;

    GetRepositoryInfoService getRepositoryInfoService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        getRepositoryInfoService =
                new GetRepositoryInfoService(repositoryService, configService, projectsService, securityService,
                        packageSpecification, repositoryConfigService);
        when(packageSpecification.hasReindex(any(), any())).thenReturn(true);
        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos(false)).thenReturn(
                List.of(LocalRepoConfig.create("A", RepoType.Generic)));
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getMultiLocalReplications("A")).thenReturn(List.of());
        when(configService.getDescriptor()).thenReturn(descriptor);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(dataProvider = "provideUserType")
    public void testGetRepositoriesInfo(UserType userType, String project, boolean shouldBeExist) {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getPathParamByKey(REPO_TYPE)).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(restRequest.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(project);
        switch (userType) {
            case SIMPLE:
                when(securityService.isAdmin()).thenReturn(false);
                when(securityService.isProjectAdmin(project)).thenReturn(false);
                break;
            case PROJECT_ADMIN:
                when(securityService.isAdmin()).thenReturn(false);
                when(securityService.isProjectAdmin(project)).thenReturn(true);
                when(projectsService.getProjectRepositoriesKeys(eq(PROJECT_A),
                        ArgumentMatchers.anyBoolean())).thenReturn(Set.of("A", "B"));
                break;
            case ADMIN:
                when(securityService.isAdmin()).thenReturn(true);
                when(securityService.isProjectAdmin(project)).thenReturn(true);
                when(projectsService.getProjectRepositoriesKeys(eq(PROJECT_A), anyBoolean())).thenReturn(
                        Set.of("A", "B"));
                break;
        }
        getRepositoryInfoService.execute(restRequest, response);
        List<RepositoryInfo> result = (List<RepositoryInfo>) response.getiModelList();
        if (shouldBeExist) {
            assertEquals("A", result.get(0).getRepoKey());
        } else {
            assertTrue(result.isEmpty());
        }
    }

    @Test
    public void testExecuteRepoInfoIsPopulatedWithProjectInfo() {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getPathParamByKey(REPO_TYPE)).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(restRequest.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(PROJECT_A);
        when(securityService.isProjectAdmin(PROJECT_A)).thenReturn(true);
        when(projectsService.getProjectRepositoriesKeys(eq(PROJECT_A), anyBoolean())).thenReturn(Set.of(REPO_A));

        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos(false))
                .thenReturn(List.of(LocalRepoConfig.create(REPO_A, RepoType.Maven)));
        when(projectsService.getRepoKeyToProjectMap(Set.of(REPO_A), false)).thenReturn(Map.of(REPO_A, PROJECT_A));
        when(projectsService.getProjectRepositoriesMap(PROJECT_A, false))
                .thenReturn(Map.of(REPO_A, ProjectResourceEntity.builder().resourceName(PROJECT_A)
                        .resourceStatus(ProjectResourceStatus.SHARED).sharedWithProject(PROJECT_B).build()));
        getRepositoryInfoService.execute(restRequest, response);

        assertEquals(response.getiModelList().size(), 1);
        LocalRepositoryInfo repoInfo = (LocalRepositoryInfo) response.getiModelList().stream().findFirst().orElse(null);
        assertEquals(repoInfo.getProjectKey(), PROJECT_A);
        assertNotNull(repoInfo.getEnvironments());
        assertEquals(repoInfo.getSharedWithProjects(), Set.of(PROJECT_B));
        assertFalse(repoInfo.isShareWithAllProjects());

        verify(projectsService).getRepoKeyToProjectMap(Set.of(REPO_A), false);
        verify(projectsService).getProjectRepositoriesKeys(PROJECT_A, true);
        verify(projectsService).getProjectRepositoriesMap(PROJECT_A, false);
        verify(projectsService).getProjectDisplayName(PROJECT_A);
        verifyNoMoreInteractions(projectsService);
    }

    @Test
    public void testExecuteRepoInfoIsPopulatedDefaultProjectInfo() {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getPathParamByKey(REPO_TYPE)).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(restRequest.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(null);
        when(securityService.isAdmin()).thenReturn(true);
        mockProjectsData();

        getRepositoryInfoService.execute(restRequest, response);
        assertEquals(response.getiModelList().size(), 2);
        List<LocalRepositoryInfo> repoInfo =
                (List<LocalRepositoryInfo>) response.getiModelList().stream().collect(Collectors.toList());

        LocalRepositoryInfo repoA = repoInfo.get(0);
        assertEquals(repoA.getProjectKey(), ProjectsService.DEFAULT_PROJECT);
        assertEquals(repoA.getProjectName(), ProjectsService.DEFAULT_PROJECT);
        assertNotNull(repoA.getEnvironments());
        assertEquals(repoA.getSharedWithProjects(), Set.of(PROJECT_A));
        assertTrue(repoA.isShareWithAllProjects());
        assertTrue(repoA.isSharedReadOnly());
        assertTrue(repoA.isTarget());

        LocalRepositoryInfo repoB = repoInfo.get(1);
        assertEquals(repoB.getProjectKey(), PROJECT_A);
        assertEquals(repoB.getProjectName(), PROJECT_A);
        assertNotNull(repoB.getEnvironments());
        assertEquals(repoB.getSharedWithProjects(), Set.of());
        assertFalse(repoB.isShareWithAllProjects());
        assertFalse(repoB.isSharedReadOnly());
        assertFalse(repoB.isTarget());

        verify(projectsService).getRepoKeyToProjectMap(Set.of(REPO_A, DEFAULT_PROJECT_REPO), false);
        verify(projectsService).getAllProjectsRepositoriesMap(false);
        verify(projectsService).getProjectDisplayName(PROJECT_A);
        verify(projectsService).getProjectDisplayName(ProjectsService.DEFAULT_PROJECT);
        verifyNoMoreInteractions(projectsService);
    }

    @Test
    public void testExecuteRepoInfoIsPopulatedWithUnassignedProject() {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getPathParamByKey(REPO_TYPE)).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(restRequest.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(ProjectsService.UNASSIGNED);
        when(repositoryService.getAllRepoKeys()).thenReturn(Set.of(REPO_A, DEFAULT_PROJECT_REPO));
        when(securityService.isAdmin()).thenReturn(true);
        mockProjectsData();

        getRepositoryInfoService.execute(restRequest, response);
        assertEquals(response.getiModelList().size(), 1);
        List<LocalRepositoryInfo> repoInfo =
                (List<LocalRepositoryInfo>) response.getiModelList().stream().collect(Collectors.toList());

        LocalRepositoryInfo repoA = repoInfo.get(0);
        assertEquals(repoA.getProjectKey(), ProjectsService.DEFAULT_PROJECT);
        assertEquals(repoA.getProjectName(), ProjectsService.DEFAULT_PROJECT);
        assertNotNull(repoA.getEnvironments());
        assertEquals(repoA.getSharedWithProjects(), Set.of(PROJECT_A));
        assertTrue(repoA.isShareWithAllProjects());
        assertTrue(repoA.isSharedReadOnly());

        verify(projectsService, times(2)).getRepoKeyToProjectMap(anySet(), anyBoolean());
        verify(projectsService).getAllProjectsRepositoriesMap(false);
        verify(projectsService).getProjectDisplayName(ProjectsService.DEFAULT_PROJECT);
        verifyNoMoreInteractions(projectsService);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test(dataProvider = "systemGeneratedRepos")
    public void testExecuteRepoInfoIsReturningIfRepoIsLogsShippingOrBilling(boolean includeShippingOrBillingLogsRepos,
            String systemGeneratedRepoKey, boolean isAdmin) {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getPathParamByKey(REPO_TYPE)).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(restRequest.getQueryParamByKey("withServiceGeneratedRepos"))
                .thenReturn(String.valueOf(includeShippingOrBillingLogsRepos));
        when(securityService.isAdmin()).thenReturn(isAdmin);

        RepositoryConfigServiceImpl repositoryConfigServiceImpl = Mockito.mock(RepositoryConfigServiceImpl.class);

        LocalRepoConfig billingRepoConfig =
                LocalRepoConfig.create(systemGeneratedRepoKey, RepoType.Generic);
        when(repositoryConfigServiceImpl.getLocalRepoConfigKeysExcludingInfoRepos(includeShippingOrBillingLogsRepos)).
                thenCallRealMethod();
        when(repositoryConfigServiceImpl.getLocalRepoConfigsExcludingInfoRepos(includeShippingOrBillingLogsRepos)).
                thenCallRealMethod();

        RepoConfigCache repoConfigCache = Mockito.mock(RepoConfigCache.class);
        if (includeShippingOrBillingLogsRepos && isAdmin) {
            when(repoConfigCache.getRepositoryConfigsByKeys(Set.of(systemGeneratedRepoKey)))
                    .thenReturn(ImmutableSet.of(billingRepoConfig));
        } else {
            when(repoConfigCache.getRepositoryConfigsByKeys(Set.of()))
                    .thenReturn(ImmutableSet.of());
        }
        when(repoConfigCache.getAllBasicConfigs()).thenReturn(Set.of(new BasicRepoConfig(billingRepoConfig)));
        ReflectionTestUtils.setField(repositoryConfigServiceImpl, "repoConfigCache", repoConfigCache);

        getRepositoryInfoService =
                new GetRepositoryInfoService(repositoryService, configService, projectsService, securityService,
                        packageSpecification, repositoryConfigServiceImpl);
        // then:
        getRepositoryInfoService.execute(restRequest, response);
        List<LocalRepositoryInfo> repoInfo = response.getiModelList().stream().toList();
        if (includeShippingOrBillingLogsRepos && isAdmin) {
            assertEquals(repoInfo.get(0).getRepoKey(), systemGeneratedRepoKey);
        } else {
            assertEquals(repoInfo.size(), 0);
        }

    }

    private void mockProjectsData() {
        when(projectsService.getProjectRepositoriesKeys(eq(ProjectsService.DEFAULT_PROJECT), anyBoolean()))
                .thenReturn(Set.of(DEFAULT_PROJECT_REPO));
        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos(false))
                .thenReturn(List.of(LocalRepoConfig.create(DEFAULT_PROJECT_REPO, RepoType.Maven),
                        LocalRepoConfig.create(REPO_A, RepoType.Maven)));

        when(projectsService.getRepoKeyToProjectMap(Set.of(REPO_A, DEFAULT_PROJECT_REPO), false))
                .thenReturn(Map.of(REPO_A, PROJECT_A, DEFAULT_PROJECT_REPO, ProjectsService.DEFAULT_PROJECT));
        when(projectsService.getAllProjectsRepositoriesMap(false)).thenReturn(Map.of(ProjectsService.DEFAULT_PROJECT,
                Map.of(DEFAULT_PROJECT_REPO,
                        ProjectResourceEntity.builder()
                                .resourceName(DEFAULT_PROJECT_REPO)
                                .sharedWithProject(PROJECT_A)
                                .resourceStatus(ProjectResourceStatus.SHARED_WITH_ALL_TARGET)
                                .sharedReadOnly(true)
                                .build()),
                PROJECT_A, Map.of(REPO_A, ProjectResourceEntity.builder()
                        .resourceName(PROJECT_A)
                        .resourceStatus(ProjectResourceStatus.SHARED)
                        .sharedReadOnly(false)
                        .build())));
        when(projectsService.getProjectDisplayName(PROJECT_A)).thenReturn(PROJECT_A);
        when(projectsService.getProjectDisplayName(ProjectsService.DEFAULT_PROJECT))
                .thenReturn(ProjectsService.DEFAULT_PROJECT);
    }

    @Test(dataProvider = "federationScenarios")
    @SuppressWarnings("unchecked")
    public void testFederatedAttributeConsistency(List<FederatedMember> members, Boolean expectedFederated) {
        getOrCreateArtifactoryHomeStub()
                .setProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getPathParamByKey(REPO_TYPE)).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(restRequest.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(PROJECT_A);
        when(restRequest.getQueryParamByKey("isFedAttrSupported")).thenReturn("true");
        when(securityService.isProjectAdmin(PROJECT_A)).thenReturn(true);
        when(projectsService.getProjectRepositoriesKeys(eq(PROJECT_A), anyBoolean())).thenReturn(Set.of("A"));
        LocalRepoConfig repoConfig = LocalRepoConfig.create("A", RepoType.Generic);
        repoConfig.getBaseConfig().getFederationConfig().setMembers(new ArrayList<>(members));
        when(repositoryService.getRepoConfigByKey("A")).thenReturn(repoConfig);

        getRepositoryInfoService.execute(restRequest, response);

        List<RepositoryInfo> result = (List<RepositoryInfo>) response.getiModelList();
        assertEquals(result.size(), 1);
        assertEquals(result.getFirst().getFederated(), expectedFederated);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFederatedAttributeNotSetWhenOptInDisabled() {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getPathParamByKey(REPO_TYPE)).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(restRequest.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(PROJECT_A);
        when(securityService.isProjectAdmin(PROJECT_A)).thenReturn(true);
        when(projectsService.getProjectRepositoriesKeys(eq(PROJECT_A), anyBoolean())).thenReturn(Set.of("A"));

        getRepositoryInfoService.execute(restRequest, response);

        List<RepositoryInfo> result = (List<RepositoryInfo>) response.getiModelList();
        assertEquals(result.size(), 1);
        assertNull(result.getFirst().getFederated());
        verify(repositoryService, never()).getRepoConfigByKey(anyString());
    }

    @Test
    public void testFederatedRepoTypeWithOptInReturnsMethodNotAllowed() {
        getOrCreateArtifactoryHomeStub()
                .setProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getPathParamByKey(REPO_TYPE)).thenReturn(RepoDetailsType.FEDERATED_REPO);
        when(restRequest.getQueryParamByKey("isFedAttrSupported")).thenReturn("true");

        getRepositoryInfoService.execute(restRequest, response);

        assertEquals(response.getResponseCode(), HttpStatus.SC_METHOD_NOT_ALLOWED);
        assertNull(response.getiModelList());
    }

    private static FederatedMember member(String url) {
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate(url);
        return member;
    }

    @DataProvider(name = "federationScenarios")
    public static Object[][] federationScenarios() {
        String self = FederatedMember.BASE_URL_PLACEHOLDER + "/A";
        String remote = "https://other.jfrog.io/artifactory/A";
        return new Object[][] {
                {List.of(), Boolean.FALSE},                                 // no members -> self added -> 1 -> false
                {List.of(member(self)), Boolean.FALSE},                     // self only -> not duplicated -> 1 -> false
                {List.of(member(self), member(remote)), Boolean.TRUE},      // self + mirror -> 2 -> true
                {List.of(member(remote)), Boolean.TRUE},                    // mirror only -> self added -> 2 -> true
        };
    }

    @DataProvider
    public static Object[][] provideUserType() {
        return new Object[][] {
                {UserType.SIMPLE, "", false},
                {UserType.SIMPLE, PROJECT_A, false},
                {UserType.PROJECT_ADMIN, "", false},
                {UserType.PROJECT_ADMIN, PROJECT_A, true},
                {UserType.ADMIN, "", true},
                {UserType.ADMIN, PROJECT_A, true},
        };
    }

    @DataProvider(name = "systemGeneratedRepos")
    public Object[][] systemGeneratedReposProvider() {
        return new Object[][] {
                {true, SystemRepos.BILLING_REPO_KEY, true},
                {true, SystemRepos.BILLING_REPO_KEY, false},
                {true, SystemRepos.LOG_SHIPPING_REPO_KEY, true},
                {true, SystemRepos.LOG_SHIPPING_REPO_KEY, false},
                {false, SystemRepos.BILLING_REPO_KEY, true}
        };
    }
}