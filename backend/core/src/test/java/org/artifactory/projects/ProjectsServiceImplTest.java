package org.artifactory.projects;

import org.artifactory.aol.AolService;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.security.UserGroupImpl;
import org.artifactory.model.security.UserImpl;
import org.artifactory.projects.exception.AccessClientStateException;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.schedule.TaskService;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.client.group.GroupsClient;
import org.jfrog.access.client.project.EnvironmentEntity;
import org.jfrog.access.client.project.GrpcProjectClient;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.client.project.ProjectMembership;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.access.client.project.RoleActions;
import org.jfrog.access.client.project.StorageQuotaEntity;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.common.RoleConstants;
import org.jfrog.access.common.v2.Action;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.rest.user.Users;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.projects.ProjectsService.UNLIMITED_STORAGE;
import static org.artifactory.projects.ProjectsUtils.PROJ_LIMIT_MSG;
import static org.artifactory.util.bundles.BundleConfigurationUtil.APP_VERSIONS_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_DISTRIBUTION_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author BarakH
 */
class ProjectsServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String PROJECT_A = "proj1";
    private static final String PROJECT_B = "proj2";
    private static final String PROJECT_C = "proj3";
    private static final String USER = "user";
    private static final String ADMIN = "admin";
    private static final RepoPath REPO_A = RepoPathFactory.create("repo1");
    private static final RepoPath REPO_B = RepoPathFactory.create("repo2");
    private static final ProjectSlimEntity PROJECT_A_ENTITY =
            ProjectSlimEntity.builder().name(PROJECT_A).projectKey(PROJECT_A).build();
    private static final ProjectSlimEntity PROJECT_C_ENTITY =
            ProjectSlimEntity.builder().name(PROJECT_C).build();
    @Mock
    private GrpcProjectClient grpcProjectClient;
    @Mock
    private AccessService accessService;
    @Mock
    private SecurityContextImpl securityContext;
    @Mock
    private StorageService storageService;
    @Mock
    private AccessClient accessClient;
    @Mock
    private TaskService taskService;
    @Mock
    private AolService aolService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    private ProjectsServiceImpl projectsService;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessClient.cachedGrpcProject()).thenReturn(grpcProjectClient);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);

        getBound().setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(), "false");
        homeStub.setProperty(ConstantValues.restrictRemoteRepositoryManagement.getPropertyName(), "true");

        when(authorizationService.getCurrentAccessPrincipal()).thenReturn(Optional.empty());
        UsersClient usersClient = mock(UsersClient.class);
        when(usersClient.findUserByUsername(anyString(), anyBoolean())).thenReturn(Optional.empty());
        when(accessClient.cachedUsers()).thenReturn(usersClient);

        securityContext = new SecurityContextImpl();
        SecurityContextHolder.setContext(securityContext);
        projectsService = spy(new ProjectsServiceImpl());
        projectsService.setAccessService(accessService);
        projectsService.setStorageService(storageService);
        projectsService.setTaskService(taskService);
        projectsService.setRepositoryService(repositoryService);
        projectsService.setSecurityService(securityService);
        projectsService.setAuthorizationService(authorizationService);
        projectsService.init();
    }

    @AfterEach
    void teardown() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @MethodSource("provideUserTypeAndLicense")
    void testIsProjectAdmin(UserType userType, boolean expectedResults) {
        SimpleUser simpleUser = null;
        switch (userType) {
            case ADMIN:
                simpleUser = setAdminAuthentication();
                when(grpcProjectClient.isProjectAdmin(ADMIN, PROJECT_A, null)).thenReturn(true);
                break;
            case PROJECT_ADMIN:
                simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
                break;
            case TRANSIENT_PROJECT_ADMIN:
                simpleUser = setSimpleUserAuthentication(true, "externalGroup");
                when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, List.of("externalGroup"))).thenReturn(true);
                break;
            case SIMPLE:
                simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(false);
                break;
        }
        assertEquals(expectedResults, projectsService.isProjectAdmin(PROJECT_A, simpleUser));
    }

    @ParameterizedTest
    @MethodSource("authScopeProvider")
    void testIsProjectAdminParameters(boolean isIdentity, boolean isAppliedPermissionsGroups,
            String expectedUsername, List<String> expectedGroups) {
        UserImpl userInfo = new UserImpl();
        userInfo.setUsername("migi");
        UserGroupImpl group = new UserGroupImpl("group");
        userInfo.setGroups(new HashSet<>(List.of(group)));
        userInfo.setIsAppliedPermissionsGroups(isAppliedPermissionsGroups);
        userInfo.setIsIdentity(isIdentity);
        SimpleUser simpleUser = new SimpleUser(userInfo);

        projectsService.isProjectAdmin("migi", simpleUser);
        verify(grpcProjectClient).isProjectAdmin(eq(expectedUsername), anyString(), eq(expectedGroups));

        projectsService.isResourceManager(simpleUser);
        verify(grpcProjectClient).isProjectAdmin(expectedUsername, DEFAULT_PROJECT, expectedGroups);

        when(grpcProjectClient.isProjectAdmin(expectedUsername, "migi", expectedGroups))
                .thenReturn(true);
        projectsService.canDoInternal(RepoActionType.CREATE, "cool-repo", "migi",
                simpleUser, RepoDetailsType.LOCAL);
        verify(grpcProjectClient).canDoAction(eq(expectedUsername), anyString(), eq(expectedGroups),
                eq(RoleActions.MANAGE_RESOURCES));

        when(grpcProjectClient.getProjectForResource("migi", ResourceType.REPO)).thenReturn("migi");
        when(grpcProjectClient.isProjectAdmin(expectedUsername, "migi", expectedGroups))
                .thenReturn(true);
        projectsService.canDoInternal(RepoActionType.UPDATE, "migi", null, simpleUser, RepoDetailsType.LOCAL);
        verify(grpcProjectClient).canProjectAdminDoOnResource(anyString(), eq(ResourceType.REPO),
                eq(List.of(RoleActions.MANAGE_RESOURCES)), eq(expectedUsername), eq(expectedGroups));
    }

    @Test
    void testIsResourceManager() {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, ProjectsService.DEFAULT_PROJECT, null)).thenReturn(true);
        assertTrue(projectsService.isResourceManager(simpleUser));
    }

    @ParameterizedTest
    @MethodSource("provideUserTypeAndLicenseForCanDoToProjectResources")
    void testCanDoToProjectResources(UserType userType, boolean expectedResults) {
        switch (userType) {
            case ADMIN -> {
                setAdminAuthentication();
                when(securityService.isAdmin()).thenReturn(true);
                when(grpcProjectClient.isProjectAdmin(ADMIN, PROJECT_A, null)).thenReturn(true);
                when(grpcProjectClient.canDoAction(ADMIN, PROJECT_A, null, RoleActions.MANAGE_RESOURCES))
                        .thenReturn(true);
                when(grpcProjectClient.canProjectAdminDoOnResource("cool-repo", ResourceType.REPO,
                        List.of(RoleActions.MANAGE_RESOURCES), ADMIN, null)).thenReturn(true);
            }
            case PROJECT_ADMIN -> {
                setSimpleUserAuthentication(false);
                when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
                when(grpcProjectClient.canDoAction(USER, PROJECT_A, null, RoleActions.MANAGE_RESOURCES))
                        .thenReturn(true);
                when(grpcProjectClient.canProjectAdminDoOnResource("cool-repo", ResourceType.REPO,
                        List.of(RoleActions.MANAGE_RESOURCES), USER, null)).thenReturn(true);
            }
            case TRANSIENT_PROJECT_ADMIN -> {
                setSimpleUserAuthentication(true, "externalGroup");
                when(securityService.isAdmin()).thenReturn(false);
                when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, List.of("externalGroup"))).thenReturn(true);
                when(grpcProjectClient.canDoAction(USER, PROJECT_A, List.of("externalGroup"),
                        RoleActions.MANAGE_RESOURCES)).thenReturn(true);
                when(grpcProjectClient.canProjectAdminDoOnResource("cool-repo", ResourceType.REPO,
                        List.of(RoleActions.MANAGE_RESOURCES), USER, List.of("externalGroup"))).thenReturn(true);
            }
            case SIMPLE -> {
                setSimpleUserAuthentication(false);
                when(grpcProjectClient.canDoAction(USER, PROJECT_A, null, RoleActions.MANAGE_RESOURCES))
                        .thenReturn(false);
            }
        }
        for (RepoActionType repoAction : RepoActionType.values()) {
            assertEquals(expectedResults, projectsService.canDoToProjectRepoResource(repoAction, "cool-repo",
                    PROJECT_A, RepoDetailsType.LOCAL));
        }
    }

    @ParameterizedTest
    @MethodSource("provideUserTypeAndLicense")
    void testGetUserProjects(UserType userType, boolean projectShouldBeExist) {
        switch (userType) {
            case ADMIN:
                setAdminAuthentication();
                when(grpcProjectClient.getAllProjectKeys())
                        .thenReturn(List.of(ProjectsService.DEFAULT_PROJECT, PROJECT_A));
                break;
            case PROJECT_ADMIN:
                setSimpleUserAuthentication(false);
                when(grpcProjectClient.getAllProjectsManagedByUser(USER, null))
                        .thenReturn(List.of(PROJECT_A, ProjectsService.DEFAULT_PROJECT));
                break;
            case TRANSIENT_PROJECT_ADMIN:
                setSimpleUserAuthentication(true, "externalGroup");
                when(grpcProjectClient
                        .getAllProjectsManagedByUser(USER, List.of("externalGroup")))
                        .thenReturn(List.of(PROJECT_A, ProjectsService.DEFAULT_PROJECT));
                break;
            case SIMPLE:
                setSimpleUserAuthentication(false);
                when(grpcProjectClient.getAllProjectsManagedByUser(USER, null))
                        .thenReturn(List.of(ProjectsService.DEFAULT_PROJECT));
                break;
        }

        List<String> userProjects = projectsService.getAllProjectsManagedByUser();
        assertEquals(projectShouldBeExist, userProjects.contains(PROJECT_A));
        if (UserType.ADMIN.equals(userType) && projectShouldBeExist) {
            assertTrue(userProjects.contains(DEFAULT_PROJECT));
        } else {
            assertFalse(userProjects.contains(DEFAULT_PROJECT));
        }
    }

    @ParameterizedTest
    @MethodSource("provideUserTypeAndLicense")
    void testCanProjectAdminManageResources(UserType userType, boolean expectedResults) {
        SimpleUser simpleUser = null;
        when(grpcProjectClient.getProjectForResource(REPO_A.getRepoKey(), ResourceType.REPO))
                .thenReturn(PROJECT_A);
        switch (userType) {
            case ADMIN:
                simpleUser = setAdminAuthentication();
                when(grpcProjectClient.isProjectAdmin(ADMIN, PROJECT_A, null)).thenReturn(true);
                when(grpcProjectClient.canProjectAdminDoOnResource(REPO_A.getRepoKey(), ResourceType.REPO,
                        List.of(RoleActions.MANAGE_RESOURCES), ADMIN, null))
                        .thenReturn(true);
                break;
            case PROJECT_ADMIN:
                simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
                when(grpcProjectClient.canProjectAdminDoOnResource(REPO_A.getRepoKey(), ResourceType.REPO,
                        List.of(RoleActions.MANAGE_RESOURCES), USER, null))
                        .thenReturn(true);
                break;
            case TRANSIENT_PROJECT_ADMIN:
                simpleUser = setSimpleUserAuthentication(true, "externalGroup");
                when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, List.of("externalGroup"))).thenReturn(true);
                when(grpcProjectClient
                        .canProjectAdminDoOnResource(REPO_A.getRepoKey(), ResourceType.REPO,
                                List.of(RoleActions.MANAGE_RESOURCES), USER, List.of("externalGroup")))
                        .thenReturn(true);
                break;
            case SIMPLE:
                simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.canProjectAdminDoOnResource(REPO_A.getRepoKey(), ResourceType.REPO,
                        List.of(RoleActions.MANAGE_RESOURCES), USER, null))
                        .thenReturn(false);
                break;
        }
        assertEquals(expectedResults,
                projectsService.canDoInternal(RepoActionType.UPDATE, REPO_A.getRepoKey(),
                        null, simpleUser, RepoDetailsType.LOCAL));
    }

    @Test
    void testGetProjectRepositories() {
        String repoKey = REPO_A.getRepoKey();
        when(grpcProjectClient.getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false))
                .thenReturn(Map.of(REPO_A.getRepoKey(), ProjectResourceEntity.builder().resourceName(repoKey).build()));

        Set<String> repositories = projectsService.getProjectRepositoriesKeys(PROJECT_A, false);
        assertTrue(repositories.contains(REPO_A.getRepoKey()));
    }

    @ParameterizedTest
    @MethodSource("ignoreCache")
    void testGetProjectRepositoriesIncludeRemoteCache(boolean ignoreCache) {
        String repoKey = REPO_A.getRepoKey();
        when(grpcProjectClient.getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false))
                .thenReturn(Map.of(REPO_A.getRepoKey(), ProjectResourceEntity.builder().resourceName(repoKey).build()));
        LocalConfig remoteCacheRepo = mock(LocalRepoConfig.class);
        when(repositoryService.isRemoteRepoExist(REPO_A.getRepoKey())).thenReturn(true);
        when(repositoryService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(List.of(remoteCacheRepo));
        when(remoteCacheRepo.isCache()).thenReturn(true);
        when(remoteCacheRepo.getKey()).thenReturn(REPO_A.getRepoKey() + "-cache");
        Set<String> repositories = projectsService.getProjectRepositoriesKeys(PROJECT_A, ignoreCache);
        if (ignoreCache) {
            assertEquals(1, repositories.size());
        } else {
            assertEquals(2, repositories.size());
        }
    }

    @ParameterizedTest
    @MethodSource("provideLicenseRepoKeyAndExpectedProject")
    void testGetProjectOfRepository(String repoKey, String expectedProject) {
        when(grpcProjectClient.getProjectForResource(REPO_A.getRepoKey(), ResourceType.REPO))
                .thenReturn(PROJECT_A);
        when(grpcProjectClient.getProjectForResource(REPO_B.getRepoKey(), ResourceType.REPO))
                .thenReturn(null);

        String repoProject = projectsService.getProjectKeyOfRepository(repoKey);
        assertEquals(expectedProject, repoProject);
    }

    @ParameterizedTest
    @MethodSource("provideLicenseRepoConfigAndExpectedError")
    void testValidateRepoConfigIsMatchingToProjectSpec(String project, String repoKey,
            Set<String> environments, String expectedError) {
        when(grpcProjectClient.getProjectSlimEntity(PROJECT_A)).thenReturn(PROJECT_A_ENTITY);
        when(grpcProjectClient.getProjectSlimEntity(PROJECT_B)).thenReturn(null);
        when(grpcProjectClient.getProjectSlimEntity(PROJECT_C)).thenReturn(PROJECT_C_ENTITY);
        try {
            projectsService.validateRepoConfigIsMatchingToProjectSpec(project, repoKey, environments);
        } catch (IllegalArgumentException e) {
            assertEquals(expectedError, e.getMessage());
        }
    }

    @Test
    void testGetProjectRepositoryEntity() {
        when(grpcProjectClient.getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false))
                .thenReturn(new HashMap<>());

        ProjectResourceEntity projectEntity =
                projectsService.getProjectRepositoryEntity(PROJECT_A, REPO_B.getRepoKey());
        assertNull(projectEntity);
        verify(grpcProjectClient).getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false);
        verifyNoMoreInteractions(grpcProjectClient);
        clearInvocations(grpcProjectClient);
        when(grpcProjectClient.getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false))
                .thenReturn(Map.of(REPO_A.getRepoKey(),
                        ProjectResourceEntity.builder().resourceName(REPO_A.getRepoKey()).environment("dev")
                                .environment("test").build()));

        projectEntity = projectsService.getProjectRepositoryEntity(PROJECT_A, REPO_B.getRepoKey());
        assertNull(projectEntity);
        verify(grpcProjectClient).getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false);
        verifyNoMoreInteractions(grpcProjectClient);
        clearInvocations(grpcProjectClient);

        projectEntity = projectsService.getProjectRepositoryEntity(PROJECT_A, REPO_A.getRepoKey());
        assertNotNull(projectEntity);
        verify(grpcProjectClient).getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false);
        verifyNoMoreInteractions(grpcProjectClient);
    }

    @Test
    void testGetAllProjectsRecourses() {
        when(grpcProjectClient.getAllProjectKeys()).thenReturn(List.of(PROJECT_A, PROJECT_B));
        when(grpcProjectClient.getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false))
                .thenReturn(Map.of(REPO_A.getRepoKey(),
                        ProjectResourceEntity.builder().resourceName(REPO_A.getRepoKey()).build()));
        when(grpcProjectClient.getProjectResourceEntitiesMap(PROJECT_B, ResourceType.REPO, false))
                .thenReturn(Map.of(REPO_B.getRepoKey(),
                        ProjectResourceEntity.builder().resourceName(REPO_B.getRepoKey()).build()));

        Map<String, Map<String, ProjectResourceEntity>> resourcesMap = projectsService.getAllProjectsRepositoriesMap(
                false);

        assertEquals(2, resourcesMap.size());
        assertEquals(REPO_A.getRepoKey(), resourcesMap.get(PROJECT_A).get(REPO_A.getRepoKey()).getResourceName());
        assertEquals(REPO_B.getRepoKey(), resourcesMap.get(PROJECT_B).get(REPO_B.getRepoKey()).getResourceName());

    }

    @Test
    void testGetProjectRecourses() {
        when(grpcProjectClient.getProjectResourceEntitiesMap(PROJECT_A, ResourceType.REPO, false))
                .thenReturn(Map.of(REPO_A.getRepoKey(),
                        ProjectResourceEntity.builder().resourceName(REPO_A.getRepoKey()).build()));

        Map<String, ProjectResourceEntity> resourcesMap = projectsService.getProjectRepositoriesMap(PROJECT_A, false);
        assertEquals(1, resourcesMap.size());
        assertEquals(REPO_A.getRepoKey(), resourcesMap.get(REPO_A.getRepoKey()).getResourceName());
    }

    @Test
    void testAssertProjectStorageQuotaForDefaultProject() {
        String repoKey = "generic";
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        // when the storage quota feature is disabled
        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(false);
        assertFalse(statusHolder.hasErrors());
        verify(projectsService, never()).getProjectKeyOfRepository(anyString());
        verify(projectsService, never()).getProjectStorageQuotaBytes(anyString());

        // when the storage quota feature is enabled
        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        doReturn(DEFAULT_PROJECT).when(projectsService).getProjectKeyOfRepository(repoKey);
        projectsService.assertProjectStorageQuota(repoKey, statusHolder, Long.MAX_VALUE);
        assertFalse(statusHolder.hasErrors());
        verify(projectsService).getProjectKeyOfRepository(repoKey);
        verify(projectsService, never()).getProjectStorageQuotaBytes(anyString());
    }

    @Test
    void testAssertProjectStorageQuotaForUnlimitedQuota() {
        // given
        String repoKey = "proj-generic";
        String projectName = "the_project";
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        doReturn(projectName).when(projectsService).getProjectKeyOfRepository(repoKey);
        doReturn(UNLIMITED_STORAGE).when(projectsService).getProjectStorageQuotaBytes(projectName);

        // when
        projectsService.assertProjectStorageQuota(repoKey, statusHolder, Long.MAX_VALUE);

        // then
        assertFalse(statusHolder.hasErrors());
        verify(projectsService).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectName);
    }

    @Test
    void testAssertProjectStorageQuotaForLimitedQuota() {
        // given
        String repoKey = "proj-generic";
        String projectKey = "the_project";
        ProjectSlimEntity projectEntity =
                ProjectSlimEntity.builder().storageQuota(StorageQuotaEntity.builder().isSoftLimit(false).build())
                        .build();
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        doReturn(projectKey).when(projectsService).getProjectKeyOfRepository(repoKey);
        doReturn(100L).when(projectsService).getProjectStorageQuotaBytes(projectKey);
        doReturn(projectEntity).when(projectsService).getProjectSpec(projectKey);
        when(storageService.getProjectStorageUsage(projectKey)).thenReturn(50L);

        // when limit not yet reached
        projectsService.assertProjectStorageQuota(repoKey, statusHolder, 49L);

        // then no errors
        assertFalse(statusHolder.hasErrors());
        verify(projectsService, times(2)).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectKey);
        verify(projectsService, times(2)).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectKey);
        clearInvocations(projectsService, storageService);

        // when limit reached and soft limit false
        statusHolder = new BasicStatusHolder();
        projectsService.assertProjectStorageQuota(repoKey, statusHolder, 100L);

        // then an error message expected
        assertTrue(statusHolder.hasErrors());
        assertEquals(PROJ_LIMIT_MSG, statusHolder.getLastError().getMessage());
        verify(projectsService, times(2)).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectKey);
        clearInvocations(projectsService);

        // when limit reached and soft limit true
        projectEntity =
                ProjectSlimEntity.builder().storageQuota(StorageQuotaEntity.builder().isSoftLimit(true).build())
                        .build();
        doReturn(projectEntity).when(projectsService).getProjectSpec(projectKey);
        statusHolder = new BasicStatusHolder();
        projectsService.assertProjectStorageQuota(repoKey, statusHolder, 100L);

        // then no warnings are expected
        assertFalse(statusHolder.hasErrors());
        assertFalse(statusHolder.hasWarnings());
        verify(projectsService, times(2)).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectKey);
    }

    @Test
    void testAssertProjectStorageQuotaWhenStorageUsageSummaryCacheIsUnavailable() {
        // given
        String repoKey = "proj-generic";
        String projectKey = "the_project";
        ProjectSlimEntity projectEntity =
                ProjectSlimEntity.builder().storageQuota(StorageQuotaEntity.builder().isSoftLimit(false).build())
                        .build();
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        doReturn(projectKey).when(projectsService).getProjectKeyOfRepository(repoKey);
        doReturn(100L).when(projectsService).getProjectStorageQuotaBytes(projectKey);
        doReturn(projectEntity).when(projectsService).getProjectSpec(projectKey);

        doThrow(CacheUnAvailableException.class).when(storageService).getProjectStorageUsage(projectKey);

        // when limit not yet reached
        projectsService.assertProjectStorageQuota(repoKey, statusHolder, 49L);

        // then no errors
        assertFalse(statusHolder.hasErrors());
        verify(projectsService, times(2)).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectKey);
        verify(projectsService, times(2)).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectKey);
        clearInvocations(projectsService, storageService);
    }

    @Test
    void testAssertProjectStorageQuotaWhenAOLAndHardLimit() {
        String repoKey = "proj-generic";
        String projectKey = "projx";
        ProjectSlimEntity
                projectEntity =
                ProjectSlimEntity.builder().storageQuota(StorageQuotaEntity.builder().isSoftLimit(false).build())
                        .build();
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        when(aolService.isAol()).thenReturn(true);
        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        doReturn(projectKey).when(projectsService).getProjectKeyOfRepository(repoKey);
        doReturn(100L).when(projectsService).getProjectStorageQuotaBytes(projectKey);
        doReturn(projectEntity).when(projectsService).getProjectSpec(projectKey);
        when(storageService.getProjectStorageUsage(projectKey)).thenReturn(50L);

        // when limit reached and soft limit false, but is AOL
        projectsService.assertProjectStorageQuota(repoKey, statusHolder, 100L);

        // then no warnings are expected
        assertFalse(statusHolder.hasErrors());
        assertFalse(statusHolder.hasWarnings());

        verify(projectsService, times(2)).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectKey);
        verify(aolService).isAol();
    }

    @Test
    void testAssertProjectStorageQuotaWhenUsingMoveMultiStatusHolder() {
        String repoKey = "proj-generic";
        String projectName = "the_project";
        ProjectSlimEntity
                projectEntity =
                ProjectSlimEntity.builder().storageQuota(StorageQuotaEntity.builder().isSoftLimit(false).build())
                        .build();
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();

        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        doReturn(projectName).when(projectsService).getProjectKeyOfRepository(repoKey);
        doReturn(100L).when(projectsService).getProjectStorageQuotaBytes(projectName);
        doReturn(projectEntity).when(projectsService).getProjectSpec(projectName);
        when(storageService.getProjectStorageUsage(projectName)).thenReturn(50L);

        // when limit reached and hard limit true
        projectsService.assertProjectStorageQuota(repoKey, statusHolder, 100L);

        // then a warning is expected
        assertTrue(statusHolder.hasErrors());
        assertNull(statusHolder.getLastWarning());
        verify(projectsService, times(2)).getProjectKeyOfRepository(repoKey);
        verify(projectsService).getProjectStorageQuotaBytes(projectName);
    }

    @Test
    void testGetProjectAdmins() {
        // given
        UsersClient usersClient = mock(UsersClient.class);
        when(accessClient.cachedUsers()).thenReturn(usersClient);
        GroupsClient groupsClient = mock(GroupsClient.class);
        when(accessClient.groups()).thenReturn(groupsClient);

        User adminUser = mock(User.class);
        when(usersClient.findUserByUsername("project_admin")).thenReturn(Optional.of(adminUser));
        Users groupUsersResponse = mock(Users.class);
        List<User> adminGroupUsers = List.of(mock(User.class));
        when(groupUsersResponse.getUsers()).thenReturn(adminGroupUsers);
        when(groupsClient.findGroupUsers("admin_group")).thenReturn(groupUsersResponse);

        ProjectEntity project = ProjectEntity.builder()
                .user("not_admin_user", ProjectMembership.builder().role(RoleConstants.CONTRIBUTOR).build())
                .user("project_admin", ProjectMembership.builder()
                        .role(RoleConstants.DEVELOPER)
                        .role(RoleConstants.PROJECT_ADMIN).build())
                .group("not_admin_group", ProjectMembership.builder().role(RoleConstants.CONTRIBUTOR).build())
                .group("admin_group", ProjectMembership.builder()
                        .role(RoleConstants.DEVELOPER)
                        .role(RoleConstants.PROJECT_ADMIN).build())
                .build();

        // when
        Set<User> foundProjectAdminUsers = projectsService.getProjectAdmins(project);
        assertEquals(2, foundProjectAdminUsers.size());
        assertTrue(foundProjectAdminUsers.containsAll(List.of(adminUser, adminGroupUsers.getFirst())));

        // then
        verify(usersClient).findUserByUsername("project_admin");
        verify(groupsClient).findGroupUsers("admin_group");
        verifyNoMoreInteractions(usersClient);
        verifyNoMoreInteractions(groupsClient);
    }

    @Test
    void testStorageQuotaPreconditionsAndGetProjQuotaSize() {
        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(false);
        assertEquals(ProjectsService.UNLIMITED_STORAGE,
                projectsService.getProjectQuotaSizeByRepository(REPO_A.getRepoKey()));

        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        when(projectsService.getProjectKeyOfRepository(REPO_A.getRepoKey()))
                .thenReturn(ProjectsService.DEFAULT_PROJECT);
        assertEquals(ProjectsService.UNLIMITED_STORAGE,
                projectsService.getProjectQuotaSizeByRepository(REPO_A.getRepoKey()));

        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        when(projectsService.getProjectKeyOfRepository(REPO_A.getRepoKey())).thenReturn(PROJECT_A);
        doReturn(UNLIMITED_STORAGE).when(projectsService).getProjectStorageQuotaBytes(PROJECT_A);
        assertEquals(ProjectsService.UNLIMITED_STORAGE,
                projectsService.getProjectQuotaSizeByRepository(REPO_A.getRepoKey()));

        when(storageService.isProjectsStorageQuotaEnabled()).thenReturn(true);
        when(projectsService.getProjectKeyOfRepository(REPO_A.getRepoKey())).thenReturn(PROJECT_A);
        when(projectsService.getProjectStorageQuotaBytes(PROJECT_A)).thenReturn(10L);
        assertEquals(10, projectsService.getProjectQuotaSizeByRepository(REPO_A.getRepoKey()));
    }

    @Test
    void testRemoveRepositoryFromProject() {
        when(grpcProjectClient.getProjectForResource(REPO_A.getRepoKey(), ResourceType.REPO))
                .thenReturn(PROJECT_A);
        projectsService.removeRepositoryFromCurrentProject(REPO_A.getRepoKey());
        verify(grpcProjectClient).getProjectForResource(REPO_A.getRepoKey(), ResourceType.REPO);
        verify(grpcProjectClient).removeProjectResource(PROJECT_A, ResourceType.REPO, REPO_A.getRepoKey());
        verifyNoMoreInteractions(grpcProjectClient);
    }

    @Test
    void testRemoveRepositoryFromDefault() {
        when(grpcProjectClient.getProjectForResource(REPO_B.getRepoKey(), ResourceType.REPO))
                .thenReturn(DEFAULT_PROJECT);
        projectsService.removeRepositoryFromCurrentProject(REPO_B.getRepoKey());
        verify(grpcProjectClient).getProjectForResource(REPO_B.getRepoKey(), ResourceType.REPO);
        verify(grpcProjectClient).removeProjectResource(DEFAULT_PROJECT, ResourceType.REPO, REPO_B.getRepoKey());
        verifyNoMoreInteractions(grpcProjectClient);
    }

    @Test
    void testGetRepoKeyToProjectMap() {
        // original data
        Set<String> repoKeys = Set.of("proj-remote-cache");
        Set<String> repoKeysExpected = Set.of("proj-remote");
        Map<String, String> repoToProjMap = new HashMap<>();
        // expected data
        repoToProjMap.put("proj-remote", "project");
        when(grpcProjectClient.getProjectForResourceMap(repoKeysExpected, ResourceType.REPO, false))
                .thenReturn(repoToProjMap);

        Map<String, String> repoKeyToProjectMap = projectsService.getRepoKeyToProjectMap(repoKeys, false);

        assertEquals(1, repoKeyToProjectMap.size());
        assertEquals("project", repoKeyToProjectMap.get("proj-remote"));
        verify(grpcProjectClient).getProjectForResourceMap(repoKeysExpected, ResourceType.REPO, false);
        verifyNoMoreInteractions(grpcProjectClient);
    }

    @Test
    void testGetAllProjects() {
        when(grpcProjectClient.getAllProjectKeys()).thenReturn(List.of(PROJECT_A));

        List<String> projectKeys = projectsService.getAllProjects();
        assertEquals(1, projectKeys.size());
        assertTrue(projectKeys.contains(PROJECT_A));
    }

    @Test
    void testGetAllProjectsEntities() {
        ProjectEntity projectEntity = ProjectEntity.builder().projectKey(PROJECT_A).build();
        when(grpcProjectClient.getAllProjects()).thenReturn(List.of(projectEntity));

        List<ProjectEntity> projectEntities = projectsService.getAllProjectsEntities();
        assertEquals(1, projectEntities.size());
        assertTrue(projectEntities.contains(projectEntity));
    }

    @Test
    void verifyProjectStorageQuotaNotificationJobStarted() {
        projectsService.onContextCreated();
        verify(taskService).startTask(
                argThat(task -> task.getType().equals(ProjectStorageQuotaNotificationJob.class)), eq(false));
    }

    @Test
    void testNpeWhenGettingGrpcClient() {
        when(accessService.getAccessClient()).thenReturn(null);
        AccessClientStateException exception = assertThrows(AccessClientStateException.class,
                () -> projectsService.getGrpcProjectClient());
        assertTrue(exception.getMessage().contains("Failed to acquire Access project gRPC client"));
    }

    @Test
    void testIsAnyProjectAdmin() {
        when(grpcProjectClient.isAnyProjectAdminByRoleScope(any())).thenReturn(true);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        boolean anyProjectAdmin = projectsService.isAnyProjectAdmin(jwtAccessToken);
        assertTrue(anyProjectAdmin);
        assertThrows(NullPointerException.class, () -> projectsService.isAnyProjectAdmin(null));
    }

    @Test
    void testIsProjectAdminForProject() {
        when(grpcProjectClient.isProjectAdminByRoleScopeAndProjectKey(any(), any())).thenReturn(true);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        boolean projectAdmin = projectsService.isProjectAdmin(jwtAccessToken, "");
        assertTrue(projectAdmin);
        assertThrows(NullPointerException.class, () -> projectsService.isProjectAdmin(null, ""));
    }

    @Test
    void testIsProjectExists() {
        // Test case 1: projectKey is blank
        assertFalse(projectsService.isProjectExists(null));
        assertFalse(projectsService.isProjectExists(""));
        assertFalse(projectsService.isProjectExists(" "));

        // Test case 2: projectKey is not blank, but grpcProjectClient returns blank display name
        when(grpcProjectClient.getProjectDisplayName("proj-1")).thenReturn("");
        assertFalse(projectsService.isProjectExists("proj-1"));
        verify(grpcProjectClient).getProjectDisplayName("proj-1");

        // Test case 3: projectKey is not blank, and grpcProjectClient returns non-blank display name
        when(grpcProjectClient.getProjectDisplayName("proj-2")).thenReturn("proj-2-display-name");
        assertTrue(projectsService.isProjectExists("proj-2"));
        verify(grpcProjectClient).getProjectDisplayName("proj-2");

        // Test case 4: Exception occurs while checking project existence
        when(grpcProjectClient.getProjectDisplayName("proj-3")).thenThrow(new RuntimeException("Some error"));
        assertFalse(projectsService.isProjectExists("proj-3"));
        verify(grpcProjectClient).getProjectDisplayName("proj-3");
    }

    @ParameterizedTest
    @MethodSource("upsertProjectRepositoryParams")
    void testAddProjectRepository(String repoKey, String project, Set<String> envs, boolean fail) {
        Exception result = null;
        try {
            projectsService.addProjectRepository(repoKey, project, envs);
        } catch (Exception e) {
            result = e;
        }
        assertEquals(fail, nonNull(result));
    }

    @Test
    void testUpsertProjectResource() {
        Exception result = null;
        try {
            projectsService.upsertProjectResource("test", ResourceType.CLEANUP_POLICY,
                    ProjectResourceEntity.builder().build(), false);
        } catch (Exception e) {
            result = e;
        }
        assertNull(result);
    }

    @Test
    void testUpsertProjectResourceNullPointerException() {
        var ent = ProjectResourceEntity.builder().build();
        assertThrows(NullPointerException.class,
                () -> projectsService.upsertProjectResource(null, ResourceType.CLEANUP_POLICY,
                        ent, false));
        assertThrows(NullPointerException.class, () -> projectsService.upsertProjectResource("test", null,
                ent, false));
        assertThrows(NullPointerException.class,
                () -> projectsService.upsertProjectResource("test", ResourceType.CLEANUP_POLICY,
                        null, false));
    }

    @Test
    void testGetProjectForResource() {
        Exception result = null;
        try {
            projectsService.getProjectForResource("test", ResourceType.CLEANUP_POLICY);
        } catch (Exception e) {
            result = e;
        }
        assertNull(result);
    }

    @Test
    void testGetProjectForResourceMap() {
        Exception result = null;
        try {
            projectsService.getProjectForResourceMap(Set.of("test"), ResourceType.CLEANUP_POLICY, false);
        } catch (Exception e) {
            result = e;
        }
        assertNull(result);
    }

    @Test
    void testRemoveProjectResource() {
        Exception result = null;
        try {
            projectsService.removeProjectResource("proj", ResourceType.CLEANUP_POLICY, "policy");
        } catch (Exception e) {
            result = e;
        }
        assertNull(result);
    }

    @Test
    void testGetProjectResourceEntitiesMap() {
        Exception result = null;
        try {
            projectsService.getProjectResourceEntitiesMap("proj", ResourceType.CLEANUP_POLICY, false);
        } catch (Exception e) {
            result = e;
        }
        assertNull(result);
    }

    @Test
    void testGetProjectResourceEntitiesMapNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> projectsService.getProjectResourceEntitiesMap(null, ResourceType.CLEANUP_POLICY, false));
        assertThrows(NullPointerException.class,
                () -> projectsService.getProjectResourceEntitiesMap("proj", null, false));
    }

    @Test
    void testCanUserManageDefaultProjectAndNullProject() {
        UserImpl userInfo = new UserImpl();
        SimpleUser simpleUser = new SimpleUser(userInfo);
        assertFalse(projectsService.canManageProjectResourcesInternal(null, simpleUser));
        assertFalse(projectsService.canManageProjectResourcesInternal("default", simpleUser));
    }

    @Test
    void testCanUserManageProject() {
        Exception result = null;
        UserImpl userInfo = new UserImpl();
        SimpleUser simpleUser = new SimpleUser(userInfo);
        try {
            when(grpcProjectClient.isProjectAdmin(any(), eq("project1"), any())).thenReturn(true);
            when(grpcProjectClient.canDoAction(any(), eq("project1"), any(),
                    eq(RoleActions.MANAGE_RESOURCES))).thenReturn(true);
            assertTrue(projectsService.canManageProjectResourcesInternal("project1", simpleUser));
        } catch (Exception e) {
            result = e;
        }
        assertNull(result);
    }

    @Test
    void testCanUserManageNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> projectsService.canManageProjectResourcesInternal("default", null));
    }

    @Test
    void testCanUserManageProjectAccessClientException() {
        UserImpl userInfo = new UserImpl();
        SimpleUser simpleUser = new SimpleUser(userInfo);
        when(grpcProjectClient.isProjectAdmin(any(), eq("project1"), any())).thenReturn(true);
        when(grpcProjectClient.canDoAction(any(), eq("project1"), any(),
                eq(RoleActions.MANAGE_RESOURCES))).thenThrow(new AccessClientException("Access denied"));
        assertFalse(projectsService.canManageProjectResourcesInternal("project1", simpleUser));
    }

    @ParameterizedTest
    @MethodSource("upsertProjectRepositoryParams")
    void testUpsertProjectRepository(String repoKey, String project, Set<String> envs, boolean fail) {
        Exception result = null;
        try {
            projectsService.upsertProjectRepository(repoKey, project, envs);
        } catch (Exception e) {
            result = e;
        }
        assertEquals(fail, nonNull(result));
    }

    @Test
    void testUpsertProjectRepositoryWithProjectEnvironments() {
        String repoKey = "prj-repo-key";
        String projectKey = "prj";
        Set<String> environments = Set.of("DEV", "PROD", "PROJECT_ENV");
        when(grpcProjectClient.getEnvironments(projectKey)).thenReturn(List.of("PROJECT_ENV"));
        when(grpcProjectClient.getGlobalEnvironments()).thenReturn(List.of("DEV", "PROD"));
        projectsService.upsertProjectRepository(repoKey, projectKey, environments);
        verify(grpcProjectClient).addProjectResource(projectKey, ResourceType.REPO,
                repoKey, environments, false);
    }

    @Test
    void testUpsertProjectRepositoryWithEnvironmentUnassignedToProject() {
        String repoKey = "prj-repo-key";
        String projectKey = "prj";
        Set<String> environments = Set.of("DEV", "PROD", "UNASSIGNED_ENV");
        String expectedMessage =
                String.format("Undefined environment '%s' in the project '%s'", "UNASSIGNED_ENV", projectKey);
        when(grpcProjectClient.getEnvironments(projectKey)).thenReturn(List.of("PROJECT_ENV"));
        when(grpcProjectClient.getGlobalEnvironments()).thenReturn(List.of("DEV", "PROD"));
        try {
            projectsService.upsertProjectRepository(repoKey, projectKey, environments);
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectRepoResourceSuperAdmin(RepoActionType repoAction) {
        when(securityService.isAdmin()).thenReturn(true);
        boolean canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", PROJECT_A,
                RepoDetailsType.LOCAL);
        assertTrue(canManage);
        canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", RepoDetailsType.LOCAL);
        assertTrue(canManage);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectResourceProjectAdmin(RepoActionType repoAction) {
        when(securityService.isAdmin()).thenReturn(false);
        setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(anyString(), anyString(), eq(null),
                eq(RoleActions.MANAGE_RESOURCES))).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource(anyString(), eq(ResourceType.REPO),
                eq(List.of(RoleActions.MANAGE_RESOURCES)), anyString(), eq(null))).thenReturn(true);
        boolean canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", PROJECT_A,
                RepoDetailsType.LOCAL);
        assertTrue(canManage);
        when(grpcProjectClient.getProjectForResource("cool-repo", ResourceType.REPO)).thenReturn(PROJECT_A);
        canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", RepoDetailsType.LOCAL);
        assertTrue(canManage);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectRepoResourceSimpleUser(RepoActionType repoAction) {
        when(securityService.isAdmin()).thenReturn(false);
        setSimpleUserAuthentication(false);
        boolean canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", PROJECT_A,
                RepoDetailsType.LOCAL);
        assertFalse(canManage);
        canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", RepoDetailsType.LOCAL);
        assertFalse(canManage);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectRepoResourceUnauthenticated(RepoActionType repoAction) {
        when(securityService.isAdmin()).thenReturn(false);
        boolean canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", PROJECT_A,
                RepoDetailsType.LOCAL);
        assertFalse(canManage);
        canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", RepoDetailsType.LOCAL);
        assertFalse(canManage);
    }

    @Test
    void testProjectPermissions() {
        when(securityService.isAdmin()).thenReturn(false);
        assertFalse(projectsService.canManageProjectResources("test"));
        assertFalse(projectsService.isProjectAdmin("test"));

        when(securityService.isAdmin()).thenReturn(true);
        assertTrue(projectsService.canManageProjectResources("test"));

        setSimpleUserAuthentication(false, "group");
        when(securityService.isAdmin()).thenReturn(false);
        assertFalse(projectsService.canManageProjectResources("test"));

        when(securityService.isAdmin()).thenReturn(true);
        assertTrue(projectsService.isProjectAdmin("test"));

        setSimpleUserAuthentication(false, "group");
        when(securityService.isAdmin()).thenReturn(false);
        assertFalse(projectsService.isProjectAdmin("test"));
    }

    @ParameterizedTest
    @MethodSource("provideRepos")
    void testGetIneligibleRepos(String repoKey, int listSize) {
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repoDescriptor.getKey()).thenReturn(repoKey);
        String virtualRepoKey = "virtual-repo";
        String projectKey = "project1";
        Set<String> assignedAndSharedRepositories = Set.of("repo1", "repo2");
        doReturn(assignedAndSharedRepositories).when(projectsService).getProjectRepositoriesKeys(projectKey, true);
        when(repositoryService.virtualRepoDescriptorByKey(virtualRepoKey)).thenReturn(virtualRepoDescriptor);
        when(virtualRepoDescriptor.getRepositories()).thenReturn(List.of(repoDescriptor));
        Set<RepoDescriptor> result = projectsService.getIneligibleRepos(virtualRepoKey, projectKey);
        assertEquals(listSize, result.size());
        if (listSize > 0) {
            assertTrue(result.stream().toList().getFirst().getKey().contains("ineligible-repo"));
        }
        verify(repositoryService).virtualRepoDescriptorByKey(virtualRepoKey);
    }

    @ParameterizedTest
    @MethodSource("provideRepos")
    void testGetIneligibleReposInNestedVirtual(String repoKey, int listSize) {
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        VirtualRepoDescriptor nestedVirtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        when(nestedVirtualRepoDescriptor.getKey()).thenReturn(repoKey);
        RepoDescriptor localRepoDescriptor = mock(RepoDescriptor.class);
        when(localRepoDescriptor.getKey()).thenReturn("B");
        when(nestedVirtualRepoDescriptor.getRepositories()).thenReturn(List.of(localRepoDescriptor));
        String virtualRepoKey = "virtual-repo";
        String projectKey = "project1";
        Set<String> assignedAndSharedRepositories = Set.of("repo1", "repo2");
        doReturn(assignedAndSharedRepositories).when(projectsService).getProjectRepositoriesKeys(projectKey, true);
        when(repositoryService.virtualRepoDescriptorByKey(virtualRepoKey)).thenReturn(virtualRepoDescriptor);
        when(virtualRepoDescriptor.getRepositories()).thenReturn(List.of(nestedVirtualRepoDescriptor));
        Set<RepoDescriptor> result = projectsService.getIneligibleRepos(virtualRepoKey, projectKey);
        assertEquals(listSize, result.size());
        if (listSize == 1) {
            assertTrue(result.stream().toList().getFirst().getKey().contains("ineligible-repo"));
        }
        verify(repositoryService).virtualRepoDescriptorByKey(virtualRepoKey);
    }

    @Test
    void getEnvironmentEntitiesSuccess() {
        String repoKey = "virtual-repo";
        EnvironmentEntity environmentEntity = EnvironmentEntity.builder()
                .name("env1")
                .build();
        when(grpcProjectClient.getEnvironmentsEntity(repoKey, ResourceType.REPO.getKey())).thenReturn(
                List.of(environmentEntity));
        List<EnvironmentEntity> result = projectsService.getEnvironmentEntities(repoKey);
        assertEquals(1, result.size());
        assertTrue(result.contains(environmentEntity));
        verify(grpcProjectClient).getEnvironmentsEntity(repoKey, ResourceType.REPO.getKey());
    }

    @Test
    void getEnvironmentEntitiesFailure_ReturnsEmpty() {
        String repoKey = "virtual-repo";
        when(grpcProjectClient.getEnvironmentsEntity(repoKey, ResourceType.REPO.getKey()))
                .thenThrow(new RuntimeException("Error fetching environments"));
        List<EnvironmentEntity> result = projectsService.getEnvironmentEntities(repoKey);
        assertTrue(result.isEmpty());
        verify(grpcProjectClient).getEnvironmentsEntity(repoKey, ResourceType.REPO.getKey());
    }

    @Test
    void testGetIneligibleReposWithException() {
        String repoKey = "virtual-repo";
        String projectKey = "project1";
        when(repositoryService.virtualRepoDescriptorByKey(repoKey)).thenThrow(new RuntimeException("Error"));
        Set<RepoDescriptor> result = projectsService.getIneligibleRepos(repoKey, projectKey);
        assertEquals(Collections.emptySet(), result);
    }

    @Test
    void testValidateProjectAndRepositoryKeyCombination() {
        projectsService.validateProjectAndRepositoryKeyCombination("some-repo-key", EMPTY);
        projectsService.validateProjectAndRepositoryKeyCombination(DEFAULT_RBV2_REPO, EMPTY);
        projectsService.validateProjectAndRepositoryKeyCombination(DEFAULT_RBV2_DISTRIBUTION_REPO, EMPTY);
        projectsService.validateProjectAndRepositoryKeyCombination("project-" + DEFAULT_RBV2_REPO, "project");
        projectsService.validateProjectAndRepositoryKeyCombination("project-" + APP_VERSIONS_REPO, "project");
        assertThrows(IllegalArgumentException.class, () ->
                projectsService.validateProjectAndRepositoryKeyCombination(APP_VERSIONS_REPO, EMPTY));
        assertThrows(IllegalArgumentException.class, () ->
                projectsService.validateProjectAndRepositoryKeyCombination("project-" + APP_VERSIONS_REPO, EMPTY));
        assertThrows(IllegalArgumentException.class, () ->
                projectsService.validateProjectAndRepositoryKeyCombination("project-" + DEFAULT_RBV2_REPO, EMPTY));
        assertThrows(IllegalArgumentException.class, () ->
                projectsService.validateProjectAndRepositoryKeyCombination(DEFAULT_RBV2_REPO, "project"));
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoInternal_shouldReturnTrue_whenProjectAdminHasManageResourcesPermission(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, PROJECT_A, null, RoleActions.MANAGE_RESOURCES)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource("local-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_RESOURCES), USER, null)).thenReturn(true);

        boolean result = projectsService.canDoInternal(repoAction, "local-repo",
                PROJECT_A, simpleUser, RepoDetailsType.LOCAL);

        assertTrue(result);
        if (repoAction == RepoActionType.CREATE) {
            verify(grpcProjectClient).canDoAction(USER, PROJECT_A, null, RoleActions.MANAGE_RESOURCES);
        } else {
            verify(grpcProjectClient).canProjectAdminDoOnResource("local-repo", ResourceType.REPO,
                    List.of(RoleActions.MANAGE_RESOURCES), USER, null);
        }
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoInternal_shouldReturnTrue_whenProjectAdminHasManageRemoteRepoPermission(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, PROJECT_A, null,
                RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null)).thenReturn(true);

        boolean result = projectsService.canDoInternal(repoAction, "remote-repo", PROJECT_A,
                simpleUser, RepoDetailsType.REMOTE);

        assertTrue(result);
        if (repoAction == RepoActionType.CREATE) {
            verify(grpcProjectClient).canDoAction(USER, PROJECT_A, null,
                    RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE);
        } else {
            verify(grpcProjectClient).canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                    List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null);
        }
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoInternal_shouldReturnFalse_whenProjectAdminLacksRemoteRepoPermission(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, PROJECT_A, null,
                RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE)).thenReturn(false);
        when(grpcProjectClient.canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null)).thenReturn(false);
        when(authorizationService.isGrantedByAccessToken(any())).thenReturn(false);

        boolean result = projectsService.canDoInternal(repoAction, "remote-repo",
                PROJECT_A, simpleUser, RepoDetailsType.REMOTE);

        assertFalse(result);
        if (repoAction == RepoActionType.CREATE) {
            verify(grpcProjectClient).canDoAction(USER, PROJECT_A, null,
                    RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE);
        } else {
            verify(grpcProjectClient).canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                    List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null);
        }
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoInternal_shouldReturnFalse_whenProjectIsDefault_andNoPermissionsGranted(RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);

        boolean result = projectsService.canDoInternal(repoAction, "repo", DEFAULT_PROJECT,
                simpleUser, RepoDetailsType.LOCAL);

        assertFalse(result);
    }

    /**
     * RTDEV-83999: the default-project guard inside {@code canDoInternal} was narrowed to
     * {@link RepoActionType#CREATE} only. CREATE in the default project is still rejected up-front
     * for non-admins, even when an access token would otherwise grant the permission.
     */
    @Test
    void canDoInternal_shouldReturnFalse_forCreateInDefaultProject_evenWhenAccessTokenWouldGrant() {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(authorizationService.isGrantedByAccessToken(any(PermissionScope.class))).thenReturn(true);

        boolean result = projectsService.canDoInternal(RepoActionType.CREATE, "repo", DEFAULT_PROJECT,
                simpleUser, RepoDetailsType.LOCAL);

        assertFalse(result);
        verify(authorizationService, never()).isGrantedByAccessToken(any());
    }

    /**
     * RTDEV-83999: in legacy mode, UPDATE/DELETE on default-project repos no longer short-circuits to
     * false; an access token granting WRITE on the repo is now honoured (matching how non-default
     * projects behave). Previously, the broad default-project guard rejected this case unconditionally.
     */
    @ParameterizedTest
    @EnumSource(value = RepoActionType.class, names = {"UPDATE", "DELETE"})
    void canDoInternal_shouldReturnTrue_forUpdateOrDeleteInDefaultProject_whenAccessTokenGrantsWrite(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(authorizationService.isGrantedByAccessToken(any(PermissionScope.class))).thenAnswer(invocation -> {
            PermissionScope scope = invocation.getArgument(0);
            return scope != null && scope.getAction() == Action.WRITE;
        });

        boolean result = projectsService.canDoInternal(repoAction, "repo", DEFAULT_PROJECT,
                simpleUser, RepoDetailsType.LOCAL);

        assertTrue(result);
    }

    @Test
    void canDoInternal_shouldResolveProjectKeyFromRepo_whenProjectKeyParamIsNull() {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        String repoKey = "my-repo";
        when(grpcProjectClient.getProjectForResource(repoKey, ResourceType.REPO)).thenReturn(PROJECT_A);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource(repoKey, ResourceType.REPO,
                List.of(RoleActions.MANAGE_RESOURCES), USER, null)).thenReturn(true);

        projectsService.canDoInternal(RepoActionType.UPDATE, repoKey, null, simpleUser,
                RepoDetailsType.LOCAL);

        verify(grpcProjectClient).getProjectForResource(repoKey, ResourceType.REPO);
    }

    @Test
    void canDoInternal_shouldUseProvidedProjectKey_whenProjectKeyParamIsNotBlank() {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, PROJECT_A, null, RoleActions.MANAGE_RESOURCES))
                .thenReturn(true);

        boolean result = projectsService.canDoInternal(RepoActionType.CREATE, "any-repo", PROJECT_A,
                simpleUser, RepoDetailsType.LOCAL);

        assertTrue(result);
        verify(grpcProjectClient).canDoAction(USER, PROJECT_A, null, RoleActions.MANAGE_RESOURCES);
        verify(grpcProjectClient, never()).getProjectForResource(anyString(), any());
    }

    @ParameterizedTest
    @EnumSource(value = RepoActionType.class, names = {"UPDATE", "DELETE"})
    void canDoInternal_shouldReturnTrue_whenHasDoToResourcesPermissionForLocal(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.getProjectForResource("local-repo", ResourceType.REPO)).thenReturn(PROJECT_A);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource("local-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_RESOURCES), USER, null)).thenReturn(true);

        boolean result = projectsService.canDoInternal(repoAction, "local-repo", null, simpleUser,
                RepoDetailsType.LOCAL);

        assertTrue(result);
        verify(grpcProjectClient).canProjectAdminDoOnResource("local-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_RESOURCES), USER, null);
    }

    @ParameterizedTest
    @EnumSource(value = RepoActionType.class, names = {"UPDATE", "DELETE"})
    void canDoInternal_shouldReturnTrue_whenHasManageRemoteRepoPermission(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.getProjectForResource("remote-repo", ResourceType.REPO)).thenReturn(PROJECT_A);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null)).thenReturn(true);

        boolean result = projectsService.canDoInternal(repoAction, "remote-repo", null, simpleUser,
                RepoDetailsType.REMOTE);

        assertTrue(result);
        verify(grpcProjectClient).canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null);
    }

    @ParameterizedTest
    @EnumSource(value = RepoActionType.class, names = {"UPDATE", "DELETE"})
    void canDoInternal_shouldReturnFalse_whenLacksManageRemoteRepoPermission(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.getProjectForResource("remote-repo", ResourceType.REPO)).thenReturn(PROJECT_A);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null)).thenReturn(false);
        when(authorizationService.isGrantedByAccessToken(any())).thenReturn(false);

        boolean result = projectsService.canDoInternal(repoAction, "remote-repo", null, simpleUser,
                RepoDetailsType.REMOTE);

        assertFalse(result);
        verify(grpcProjectClient).canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null);
    }

    @ParameterizedTest
    @EnumSource(value = RepoActionType.class, names = {"UPDATE", "DELETE"})
    void canDoInternal_shouldReturnFalse_whenAccessClientExceptionIsThrown_resolvedProjectKey(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.getProjectForResource("remote-repo", ResourceType.REPO)).thenReturn(PROJECT_A);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null))
                .thenThrow(new AccessClientException("Failed to connect to access service"));

        boolean result = projectsService.canDoInternal(repoAction, "remote-repo", null, simpleUser,
                RepoDetailsType.REMOTE);

        assertFalse(result);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoInternal_shouldReturnFalse_whenAccessClientExceptionIsThrown_explicitProjectKey(
            RepoActionType repoAction) {
        SimpleUser simpleUser = setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, PROJECT_A, null,
                RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE))
                .thenThrow(new AccessClientException("Failed to connect to access service"));

        boolean result = projectsService.canDoInternal(repoAction, "remote-repo", PROJECT_A,
                simpleUser, RepoDetailsType.REMOTE);

        assertFalse(result);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectRepoResource_shouldReturnTrue_whenAdminManagesRemoteRepo(RepoActionType repoAction) {
        when(securityService.isAdmin()).thenReturn(true);
        boolean result = projectsService.canDoToProjectRepoResource(repoAction, "remote-repo", PROJECT_A,
                RepoDetailsType.REMOTE);
        assertTrue(result);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectRepoResource_shouldReturnFalse_whenUnauthenticatedUserTriesToUpdateRemoteRepo(
            RepoActionType repoAction) {
        when(securityService.isAdmin()).thenReturn(false);
        boolean result = projectsService.canDoToProjectRepoResource(repoAction, "remote-repo", PROJECT_A,
                RepoDetailsType.REMOTE);
        assertFalse(result);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectRepoResource_shouldReturnTrue_whenProjectAdminWithPermissionManagesRemoteRepo(
            RepoActionType repoAction) {
        when(securityService.isAdmin()).thenReturn(false);
        setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, PROJECT_A, null,
                RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource("remote-repo", ResourceType.REPO,
                List.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE), USER, null)).thenReturn(true);

        boolean result = projectsService.canDoToProjectRepoResource(repoAction, "remote-repo", PROJECT_A,
                RepoDetailsType.REMOTE);

        assertTrue(result);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectRepoResource_shouldReturnFalse_whenProjAdminWithoutPermissionTriesToDoToRemoteRepo(
            RepoActionType repoAction) {
        when(securityService.isAdmin()).thenReturn(false);
        setSimpleUserAuthentication(false);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, PROJECT_A, null,
                RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE)).thenReturn(false);
        when(authorizationService.isGrantedByAccessToken(any())).thenReturn(false);

        boolean result = projectsService.canDoToProjectRepoResource(repoAction, "remote-repo", PROJECT_A,
                RepoDetailsType.REMOTE);

        assertFalse(result);
    }

    @ParameterizedTest
    @EnumSource(RepoActionType.class)
    void canDoToProjectRepoResource_returnsTrue_whenNotProjectAdminButGrantedByAccessToken(RepoActionType repoAction) {
        homeStub.setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(), "false");
        try {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(false);
            when(grpcProjectClient.canDoAction(USER, PROJECT_A, null, RoleActions.MANAGE_RESOURCES))
                    .thenReturn(false);
            when(grpcProjectClient.canProjectAdminDoOnResource("token-repo", ResourceType.REPO,
                    List.of(RoleActions.MANAGE_RESOURCES), USER, null)).thenReturn(false);
            when(authorizationService.isGrantedByAccessToken(any(PermissionScope.class))).thenReturn(true);

            boolean result = projectsService.canDoToProjectRepoResource(repoAction, "token-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);

            assertTrue(result);
            verify(authorizationService).isGrantedByAccessToken(argThat(scope ->
                    scope != null && scope.getResourceType() == PermissionScopeResourceType.REPOSITORY
                            && "token-repo".equals(scope.getTarget())));
            if (repoAction == RepoActionType.CREATE) {
                verify(grpcProjectClient).canDoAction(USER, PROJECT_A, null, RoleActions.MANAGE_RESOURCES);
            } else {
                verify(grpcProjectClient).canProjectAdminDoOnResource("token-repo", ResourceType.REPO,
                        List.of(RoleActions.MANAGE_RESOURCES), USER, null);
            }
        } finally {
            homeStub.setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(),
                    ConstantValues.enableGranularProjectRepoPermissions.getDefValue());
        }
    }

    @Test
    void canDoToProjectRepoResource_returnsFalse_forDelete_whenAccessTokenHasOnlyWritePermission() {
        homeStub.setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(), "true");
        try {
            AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
            when(authorizationService.getCurrentAccessPrincipal()).thenReturn(Optional.of(accessPrincipal));
            AuthClient authClient = mock(AuthClient.class);
            when(accessClient.cachedAuth()).thenReturn(authClient);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                    .thenAnswer(invocation -> {
                        PermissionScope scope = invocation.getArgument(1);
                        return scope != null && scope.getAction() == Action.WRITE;
                    });

            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(false);

            boolean result = projectsService.canDoToProjectRepoResource(RepoActionType.DELETE, "token-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
            verify(authClient).principalCanDo(eq(accessPrincipal), argThat(scope ->
                    scope != null && scope.getResourceType() == PermissionScopeResourceType.REPOSITORY
                            && "token-repo".equals(scope.getTarget()) && scope.getAction() == Action.DELETE));
        } finally {
            homeStub.setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(),
                    ConstantValues.enableGranularProjectRepoPermissions.getDefValue());
        }
    }

    @Test
    void canDoToProjectRepoResource_returnsTrue_forDelete_whenGranularPermCheckDisabled_andTokenHasWriteOnly() {
        homeStub.setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(), "false");
        try {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(false);
            when(authorizationService.isGrantedByAccessToken(any(PermissionScope.class))).thenAnswer(invocation -> {
                PermissionScope scope = invocation.getArgument(0);
                return scope != null && scope.getAction() == Action.WRITE;
            });

            boolean result = projectsService.canDoToProjectRepoResource(RepoActionType.DELETE, "token-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);

            assertTrue(result);
            verify(authorizationService).isGrantedByAccessToken(argThat(scope ->
                    scope != null && scope.getAction() == Action.WRITE));
        } finally {
            homeStub.setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(),
                    ConstantValues.enableGranularProjectRepoPermissions.getDefValue());
        }
    }

    @Test
    void canDoToProjectRepoResource_returnsFalse_forUpdate_whenGranularPermCheckEnabled_andTokenHasDeleteOnly() {
        homeStub.setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(), "true");
        try {
            AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
            when(authorizationService.getCurrentAccessPrincipal()).thenReturn(Optional.of(accessPrincipal));
            AuthClient authClient = mock(AuthClient.class);
            when(accessClient.cachedAuth()).thenReturn(authClient);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                    .thenAnswer(invocation -> {
                        PermissionScope scope = invocation.getArgument(1);
                        return scope != null && scope.getAction() == Action.DELETE;
                    });

            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(false);

            boolean result = projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "token-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
            verify(authClient).principalCanDo(eq(accessPrincipal), argThat(scope ->
                    scope != null && scope.getAction() == Action.WRITE));
        } finally {
            homeStub.setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(),
                    ConstantValues.enableGranularProjectRepoPermissions.getDefValue());
        }
    }

    @ParameterizedTest
    @MethodSource("provideValidRoleActionsByRepoTypeTestData")
    void canDoToProjectRepoResource_shouldUseCorrectRoleActions(RoleActions expectedRoleActions,
            RepoDetailsType repoType) {
        when(securityService.isAdmin()).thenReturn(false);
        setSimpleUserAuthentication(false);
        when(grpcProjectClient.getProjectForResource("repo", ResourceType.REPO)).thenReturn(PROJECT_A);
        when(grpcProjectClient.isProjectAdmin(USER, DEFAULT_PROJECT, null)).thenReturn(true);
        when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, DEFAULT_PROJECT, null, expectedRoleActions)).thenReturn(true);
        when(grpcProjectClient.canDoAction(USER, PROJECT_A, null, expectedRoleActions)).thenReturn(true);
        when(grpcProjectClient.canProjectAdminDoOnResource("repo", ResourceType.REPO,
                List.of(expectedRoleActions), USER, null)).thenReturn(true);

        for (RepoActionType repoAction : RepoActionType.values()) {
            boolean result = projectsService.canDoToProjectRepoResource(repoAction, "repo", repoType);

            assertTrue(result);
        }
        verify(grpcProjectClient).canDoAction(USER, PROJECT_A, null, expectedRoleActions);
        verify(grpcProjectClient, times(2))
                .canProjectAdminDoOnResource("repo", ResourceType.REPO,
                        List.of(expectedRoleActions), USER, null);
    }

    @ParameterizedTest
    @MethodSource("provideGetManageRepoRoleActionsTestData")
    void getManageRepoRoleActions_shouldReturnCorrectRoleAction_basedOnPropertyAndRepoType(
            boolean propertyEnabled, RepoDetailsType repoType, RoleActions expectedRoleAction) {
        homeStub.setProperty(ConstantValues.restrictRemoteRepositoryManagement.getPropertyName(),
                String.valueOf(propertyEnabled));
        RoleActions result = projectsService.getManageRepoRoleActions(repoType);
        assertEquals(expectedRoleAction, result);
    }

    private static SimpleUser createNonAdminUser(boolean isTransient, String... groups) {
        UserInfo userInfo;
        if (!isTransient) {
            userInfo = new UserInfoBuilder(ProjectsServiceImplTest.USER).updatableProfile(true)
                    .internalGroups(new HashSet<>(Arrays.asList(groups))).build();
        } else {
            userInfo = new UserInfoBuilder(ProjectsServiceImplTest.USER).updatableProfile(true)
                    .groups(Set.of(new UserGroupImpl(groups[0]))).transientUser().build();
        }
        return new SimpleUser(userInfo);
    }

    private static SimpleUser createAdminUser() {
        UserInfo userInfo = new UserInfoBuilder(ADMIN).admin(true).updatableProfile(true).build();
        return new SimpleUser(userInfo);
    }

    private enum UserType {SIMPLE, PROJECT_ADMIN, TRANSIENT_PROJECT_ADMIN, ADMIN}

    private SimpleUser setAdminAuthentication() {
        SimpleUser adminUser = createAdminUser();
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                adminUser, null, SimpleUser.ADMIN_GAS);
        securityContext.setAuthentication(authenticationToken);
        return adminUser;
    }

    private SimpleUser setSimpleUserAuthentication(boolean isTransient, String... groups) {
        SimpleUser simpleUser = createNonAdminUser(isTransient, groups);
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                simpleUser, "password", SimpleUser.USER_GAS);
        securityContext.setAuthentication(authenticationToken);
        return simpleUser;
    }

    private static Stream<Arguments> provideUserTypeAndLicense() {
        return Stream.of(
                Arguments.of(UserType.SIMPLE, false),
                Arguments.of(UserType.PROJECT_ADMIN, true),
                Arguments.of(UserType.ADMIN, true),
                Arguments.of(UserType.TRANSIENT_PROJECT_ADMIN, true)
        );
    }

    /**
     * User types for testCanDoToProjectResources excluding TRANSIENT (covered by testCanDoToProjectResourcesTransient).
     */
    private static Stream<Arguments> provideUserTypeAndLicenseForCanDoToProjectResources() {
        return Stream.of(
                Arguments.of(UserType.SIMPLE, false),
                Arguments.of(UserType.PROJECT_ADMIN, true),
                Arguments.of(UserType.ADMIN, true)
        );
    }

    private static Stream<Arguments> authScopeProvider() {
        return Stream.of(
                Arguments.of(true, false, "migi", List.of("group")),
                Arguments.of(false, true, "", List.of("group")),
                Arguments.of(true, true, "migi", List.of("group")),
                Arguments.of(false, false, "migi", List.of("group"))
        );
    }

    private static Stream<Arguments> provideLicenseRepoKeyAndExpectedProject() {
        return Stream.of(
                Arguments.of(REPO_A.getRepoKey(), PROJECT_A),
                Arguments.of("", DEFAULT_PROJECT),
                Arguments.of(REPO_B.getRepoKey(), null)
        );
    }

    private static Stream<Arguments> provideLicenseRepoConfigAndExpectedError() {
        return Stream.of(
                Arguments.of(PROJECT_A, REPO_A.getRepoKey(), null,
                        "The repository key 'repo1' should start with the project key and a dash: 'proj1-'"),
                Arguments.of(DEFAULT_PROJECT, REPO_A.getRepoKey(), null, null),
                Arguments.of(PROJECT_B, REPO_A.getRepoKey(), Set.of(), "The project 'proj2' does not exist"),
                Arguments.of(PROJECT_A, REPO_A.getRepoKey(), Set.of(),
                        "The repository key 'repo1' should start with the project key and a dash: 'proj1-'"),
                Arguments.of(PROJECT_A, PROJECT_A + "-" + REPO_A.getRepoKey(), Set.of(),
                        "The repository configuration must include at least one environment")
        );
    }

    private static Stream<Arguments> provideValidRoleActionsByRepoTypeTestData() {
        return Stream.of(
                Arguments.of(RoleActions.MANAGE_RESOURCES, RepoDetailsType.VIRTUAL),
                Arguments.of(RoleActions.MANAGE_RESOURCES, RepoDetailsType.LOCAL),
                Arguments.of(RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE, RepoDetailsType.REMOTE),
                Arguments.of(RoleActions.MANAGE_RESOURCES, RepoDetailsType.FEDERATED),
                Arguments.of(RoleActions.MANAGE_RESOURCES, RepoDetailsType.RELEASE_BUNDLE)
        );
    }

    private static Stream<Arguments> provideGetManageRepoRoleActionsTestData() {
        return Stream.of(
                // Property disabled - all repo types should use MANAGE_RESOURCES
                Arguments.of(false, RepoDetailsType.REMOTE, RoleActions.MANAGE_RESOURCES),
                Arguments.of(false, RepoDetailsType.LOCAL, RoleActions.MANAGE_RESOURCES),
                Arguments.of(false, RepoDetailsType.VIRTUAL, RoleActions.MANAGE_RESOURCES),
                Arguments.of(false, RepoDetailsType.FEDERATED, RoleActions.MANAGE_RESOURCES),
                Arguments.of(false, RepoDetailsType.RELEASE_BUNDLE, RoleActions.MANAGE_RESOURCES),
                // Property enabled - remote repos use MANAGE_REMOTE_REPOSITORY_RESOURCE, others use MANAGE_RESOURCES
                Arguments.of(true, RepoDetailsType.REMOTE, RoleActions.MANAGE_REMOTE_REPOSITORY_RESOURCE),
                Arguments.of(true, RepoDetailsType.LOCAL, RoleActions.MANAGE_RESOURCES),
                Arguments.of(true, RepoDetailsType.VIRTUAL, RoleActions.MANAGE_RESOURCES),
                Arguments.of(true, RepoDetailsType.FEDERATED, RoleActions.MANAGE_RESOURCES),
                Arguments.of(true, RepoDetailsType.RELEASE_BUNDLE, RoleActions.MANAGE_RESOURCES)
        );
    }

    private static Stream<Arguments> ignoreCache() {
        return Stream.of(
                Arguments.of(true),
                Arguments.of(false)
        );
    }

    private static Stream<Arguments> upsertProjectRepositoryParams() {
        return Stream.of(
                Arguments.of("repo-key", "default", Set.of(), false),
                Arguments.of("repo-key", "unicorn", Set.of(), false),
                Arguments.of("repo-key", "unicorn", Set.of("DEV"), false),
                Arguments.of("repo-key", "unicorn", Set.of("DEV", "PROD"), false),
                Arguments.of(DEFAULT_RBV2_REPO, "default", Set.of("DEV"), false),
                Arguments.of(DEFAULT_RBV2_REPO, "unicorn", Set.of("DEV"), true),
                Arguments.of(DEFAULT_RBV2_DISTRIBUTION_REPO, "default", Set.of("DEV"), false),
                Arguments.of(DEFAULT_RBV2_DISTRIBUTION_REPO, "unicorn", Set.of("DEV"), true),
                Arguments.of("unicorn-release-bundles-v2-jfds", "default", Set.of("DEV"), false),
                Arguments.of("unicorn-release-bundles-v2-jfds", "unicorn", Set.of("DEV"), true),
                Arguments.of("unicorn-release-bundles-v2", "unicorn", Set.of("DEV"), false),
                Arguments.of("unicorn-release-bundles-v2", "default", Set.of("DEV"), true)
        );
    }

    private static Stream<Arguments> provideRepos() {
        return Stream.of(
                Arguments.of("ineligible-repo", 1),
                Arguments.of("repo1", 0)
        );
    }

    @Nested
    class CreateUserRepoVisibilityFilter {

        @BeforeEach
        void enableFlag() {
            getBound().setProperty(
                    ConstantValues.uiHideProjectScopedVirtualRepos.getPropertyName(), "true");
        }


        @Test
        void createVirtualReposVisibilityFilter_shouldReturnPassThrough_whenFlagIsOff() {
            // Arrange
            getBound().setProperty(
                    ConstantValues.uiHideProjectScopedVirtualRepos.getPropertyName(), "false");

            // Act
            Predicate<RepoConfig> filter = projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT);

            // Assert - pass-through regardless of repo type; no Access call made
            VirtualRepoConfig virtualRepo = mock(VirtualRepoConfig.class);
            assertTrue(filter.test(virtualRepo));
            verify(grpcProjectClient, never()).getProjectKeysByUsernameAndGroupNames(anyString(), any());
        }

        @Test
        void createVirtualReposVisibilityFilter_shouldReturnPassThrough_whenProjectKeyIsNotDefault() {
            // Arrange
            setSimpleUserAuthentication(false);
            when(securityService.isAdmin()).thenReturn(false);

            // Act
            Predicate<RepoConfig> filter = projectsService.createVirtualReposVisibilityFilter("proj1");

            // Assert - feature is scoped to the default-project context only
            VirtualRepoConfig virtualRepo = mock(VirtualRepoConfig.class);
            assertTrue(filter.test(virtualRepo));
            verify(grpcProjectClient, never()).getProjectKeysByUsernameAndGroupNames(anyString(), any());
        }

        @Test
        void createVirtualReposVisibilityFilter_shouldReturnPassThrough_whenUserIsGlobalAdmin() {
            // Arrange
            when(securityService.isAdmin()).thenReturn(true);

            // Act
            Predicate<RepoConfig> filter = projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT);

            // Assert - global admins always see everything
            VirtualRepoConfig virtualRepo = mock(VirtualRepoConfig.class);
            assertTrue(filter.test(virtualRepo));
            verify(grpcProjectClient, never()).getProjectKeysByUsernameAndGroupNames(anyString(), any());
        }

        @Test
        void createVirtualReposVisibilityFilter_shouldReturnPassThrough_whenNotAuthenticated() {
            // Arrange - SecurityContext has no authentication token (anonymous request)
            when(securityService.isAdmin()).thenReturn(false);

            // Act
            Predicate<RepoConfig> filter = projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT);

            // Assert
            VirtualRepoConfig virtualRepo = mock(VirtualRepoConfig.class);
            assertTrue(filter.test(virtualRepo));
            verify(grpcProjectClient, never()).getProjectKeysByUsernameAndGroupNames(anyString(), any());
        }


        @Test
        void createVirtualReposVisibilityFilter_shouldPassNonVirtualRepoThrough_whenFilterIsActive() {
            // Arrange
            setSimpleUserAuthentication(false);
            when(securityService.isAdmin()).thenReturn(false);
            when(grpcProjectClient.getProjectKeysByUsernameAndGroupNames(USER, Collections.emptyList()))
                    .thenReturn(List.of("proj1"));

            // Act
            Predicate<RepoConfig> filter = projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT);

            // Assert - only virtual repos are subject to visibility filtering; locals/remotes pass through
            assertTrue(filter.test(mock(LocalRepoConfig.class)));
            verify(grpcProjectClient, never()).getProjectForResource(anyString(), any());
        }

        @ParameterizedTest
        @MethodSource("virtualRepoVisibilityTestData")
        void createVirtualReposVisibilityFilter_shouldFilterVirtualRepoVisibility(
                String repoKey, String owningProject, List<String> userProjects, boolean expectedVisible) {
            // Arrange
            setSimpleUserAuthentication(false);
            when(securityService.isAdmin()).thenReturn(false);
            when(grpcProjectClient.getProjectKeysByUsernameAndGroupNames(USER, Collections.emptyList()))
                    .thenReturn(userProjects);
            VirtualRepoConfig virtualRepo = mock(VirtualRepoConfig.class);
            when(virtualRepo.getKey()).thenReturn(repoKey);
            when(grpcProjectClient.getProjectForResource(repoKey, ResourceType.REPO)).thenReturn(owningProject);

            // Act
            Predicate<RepoConfig> filter = projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT);

            // Assert
            assertEquals(expectedVisible, filter.test(virtualRepo));
        }

        private static Stream<Arguments> virtualRepoVisibilityTestData() {
            return Stream.of(
                    // Repo not owned by any named project (global scope) - always visible
                    Arguments.of("global-virtual", DEFAULT_PROJECT, List.of(), true),
                    // Repo owned by project user IS a member of - visible
                    // Note: project admins are not global admins; they reach this check the same way
                    Arguments.of("proj1-virtual", "proj1", List.of("proj1"), true),
                    // Repo owned by project user is NOT a member of - hidden
                    // Note: project admins of proj1 should not see repos belonging to proj2
                    Arguments.of("proj2-virtual", "proj2", List.of("proj1"), false)
            );
        }

        @Test
        void createVirtualReposVisibilityFilter_shouldMakeExactlyOneAccessCall_whenFilterAppliedToMultipleRepos() {
            // Arrange - verifies user membership is fetched once at predicate creation, not once per repo
            setSimpleUserAuthentication(false);
            when(securityService.isAdmin()).thenReturn(false);
            when(grpcProjectClient.getProjectKeysByUsernameAndGroupNames(USER, Collections.emptyList()))
                    .thenReturn(List.of("proj1"));
            when(grpcProjectClient.getProjectForResource(anyString(), eq(ResourceType.REPO)))
                    .thenReturn(DEFAULT_PROJECT);
            Predicate<RepoConfig> filter = projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT);

            // Act - apply predicate to 5 virtual repos, each owned by the default project → all visible
            for (int i = 0; i < 5; i++) {
                VirtualRepoConfig repo = mock(VirtualRepoConfig.class);
                when(repo.getKey()).thenReturn("repo-" + i);
                assertTrue(filter.test(repo));
            }

            // Assert - single gRPC call regardless of number of repos tested
            verify(grpcProjectClient, times(1)).getProjectKeysByUsernameAndGroupNames(USER, Collections.emptyList());
        }

        @Test
        void createVirtualReposVisibilityFilter_shouldPropagateException_whenAccessClientFails() {
            // Arrange
            setSimpleUserAuthentication(false);
            when(securityService.isAdmin()).thenReturn(false);
            when(grpcProjectClient.getProjectKeysByUsernameAndGroupNames(anyString(), any()))
                    .thenThrow(new AccessClientException("Access service unavailable"));

            // Act + Assert - fail-fast: let the exception propagate so the caller gets a clear error
            assertThrows(AccessClientException.class,
                    () -> projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT));
        }

        @Test
        void createVirtualReposVisibilityFilter_shouldHideProjectVirtual_whenUserHasNoGroups() {
            setSimpleUserAuthentication(false);
            when(securityService.isAdmin()).thenReturn(false);
            when(grpcProjectClient.getProjectKeysByUsernameAndGroupNames(USER, Collections.emptyList()))
                    .thenReturn(List.of());
            VirtualRepoConfig virtualRepo = mock(VirtualRepoConfig.class);
            when(virtualRepo.getKey()).thenReturn("proj1-virtual");
            when(grpcProjectClient.getProjectForResource("proj1-virtual", ResourceType.REPO)).thenReturn("proj1");

            Predicate<RepoConfig> filter = projectsService.createVirtualReposVisibilityFilter(DEFAULT_PROJECT);

            // Assert — user not in proj1 → virtual hidden; no NPE from null groups
            assertFalse(filter.test(virtualRepo));
        }
    }

    /**
     * Tests that run with {@link ConstantValues#enableGranularProjectRepoPermissions} set to "true".
     * Same mocks as parent (no Access principal, so fallback path is used); verifies behavior
     * when granular flag is enabled.
     */
    @Nested
    class WhenGranularProjectRepoPermissionsEnabled {

        @BeforeEach
        void setGranularEnabled() {
            getBound().setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(), "true");
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void canDoToProjectRepoResourceSuperAdmin(RepoActionType repoAction) {
            when(securityService.isAdmin()).thenReturn(true);
            boolean canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);
            assertTrue(canManage);
            canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", RepoDetailsType.LOCAL);
            assertTrue(canManage);
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void cannotDoToProjectRepoResource_whenProjAdminButAccessPrincipalCannotBeObtained(RepoActionType repoAction) {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(grpcProjectClient.canDoAction(anyString(), anyString(), eq(null),
                    eq(RoleActions.MANAGE_RESOURCES))).thenReturn(true);
            when(grpcProjectClient.canProjectAdminDoOnResource(anyString(), eq(ResourceType.REPO),
                    eq(List.of(RoleActions.MANAGE_RESOURCES)), anyString(), eq(null))).thenReturn(true);
            boolean canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);
            assertFalse(canManage);
            when(grpcProjectClient.getProjectForResource("cool-repo", ResourceType.REPO)).thenReturn(PROJECT_A);
            canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", RepoDetailsType.LOCAL);
            assertFalse(canManage);
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void canDoToProjectRepoResourceSimpleUser(RepoActionType repoAction) {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            boolean canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);
            assertFalse(canManage);
            canManage = projectsService.canDoToProjectRepoResource(repoAction, "cool-repo", RepoDetailsType.LOCAL);
            assertFalse(canManage);
        }
    }

    /**
     * Tests the path when granular project repo permissions are enabled and an Access principal is present.
     * The code uses {@code cachedAuth().principalCanDo(accessPrincipal, permissionScope)} and does NOT
     * fall back to legacy canDoAction/canProjectAdminDoOnResource.
     */
    @Nested
    class WhenAccessPrincipalPresent {

        private AuthClient authClient;

        @BeforeEach
        void setGranularPermissionsEnabledAndAccessPrincipal() {
            getBound().setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(), "true");
            AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
            when(authorizationService.getCurrentAccessPrincipal()).thenReturn(Optional.of(accessPrincipal));
            authClient = mock(AuthClient.class);
            when(accessClient.cachedAuth()).thenReturn(authClient);
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void canDoToProjectRepoResource_returnsTrue_whenPrincipalCanDoReturnsTrue(RepoActionType repoAction) {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class))).thenReturn(true);

            boolean result = projectsService.canDoToProjectRepoResource(repoAction, "my-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);

            assertTrue(result);
            verify(authClient).principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class));
            verify(grpcProjectClient, never()).canDoAction(anyString(), anyString(), any(), any(RoleActions.class));
            verify(grpcProjectClient, never()).canProjectAdminDoOnResource(anyString(), any(), any(), anyString(),
                    any());
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void canDoToProjectRepoResource_returnsFalse_whenPrincipalCanDoReturnsFalse(RepoActionType repoAction) {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class))).thenReturn(false);

            boolean result = projectsService.canDoToProjectRepoResource(repoAction, "my-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
            verify(authClient).principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class));
            verify(grpcProjectClient, never()).canDoAction(anyString(), anyString(), any(), any(RoleActions.class));
            verify(grpcProjectClient, never()).canProjectAdminDoOnResource(anyString(), any(), any(), anyString(),
                    any());
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void canDoInternal_returnsTrue_whenPrincipalCanDoReturnsTrue(RepoActionType repoAction) {
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class))).thenReturn(true);

            boolean result = projectsService.canDoInternal(repoAction, "local-repo", PROJECT_A, simpleUser,
                    RepoDetailsType.LOCAL);

            assertTrue(result);
            verify(authClient).principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class));
            verify(grpcProjectClient, never()).canDoAction(anyString(), anyString(), any(), any(RoleActions.class));
            verify(grpcProjectClient, never()).canProjectAdminDoOnResource(anyString(), any(), any(), anyString(),
                    any());
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void canDoInternal_returnsFalse_whenPrincipalCanDoReturnsFalse(RepoActionType repoAction) {
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class))).thenReturn(false);

            boolean result = projectsService.canDoInternal(repoAction, "local-repo", PROJECT_A, simpleUser,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
            verify(authClient).principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class));
        }

        @Test
        void canDoToProjectRepoResource_usesResolvedProjectKey_whenProjectKeyParamNull() {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.getProjectForResource("resolved-repo", ResourceType.REPO)).thenReturn(PROJECT_A);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class))).thenReturn(true);

            boolean result = projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "resolved-repo",
                    RepoDetailsType.LOCAL);

            assertTrue(result);
            verify(grpcProjectClient).getProjectForResource("resolved-repo", ResourceType.REPO);
            verify(authClient).principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class));
        }

        @Test
        void canDoToProjectRepoResource_returnsFalse_whenPrincipalCanDoThrowsAccessClientException() {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                    .thenThrow(new AccessClientException("Access service unavailable"));

            boolean result = projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "my-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
            verify(authClient).principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class));
        }

        @ParameterizedTest
        @EnumSource(RepoDetailsType.class)
        void canDoToProjectRepoResource_includesRepoTypeInPermissionScope(RepoDetailsType repoType) {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class))).thenReturn(true);

            projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo", PROJECT_A, repoType);

            // RTDEV-93295: federated is normalized to local because Access has no federated repo role actions.
            String expectedRepoType = repoType == RepoDetailsType.FEDERATED ?
                    RepoDetailsType.LOCAL.typeNameLowercase() : repoType.typeNameLowercase();
            verify(authClient).principalCanDo(any(AccessPrincipal.class),
                    argThat(scope -> scope != null && scope.getAttributes().map(attr -> attr.containsKey("repoType") &&
                            attr.get("repoType").contains(expectedRepoType)).orElse(false)));
        }

        @Test
        void canDoToProjectRepoResource_mapsFederatedToLocal_inPermissionScope() {
            // RTDEV-93295 regression: a federated repo is a local-repo variant and Access defines repo role actions
            // only for local/remote/virtual. Passing FEDERATED must therefore query Access with repoType=local,
            // otherwise the role-action lookup finds no entry and denies an otherwise-authorized project admin
            // (this broke the federation E2E suite with a spurious 403 on federated full-config reads).
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class))).thenReturn(true);

            projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "fed-repo", PROJECT_A,
                    RepoDetailsType.FEDERATED);

            verify(authClient).principalCanDo(any(AccessPrincipal.class),
                    argThat(scope -> scope != null && scope.getAttributes()
                            .map(attr -> List.of(RepoDetailsType.LOCAL.typeNameLowercase())
                                    .equals(attr.get("repoType")))
                            .orElse(false)));
        }
    }

    /**
     * When granular project repo permissions are enabled but the access principal cannot be obtained,
     * canDoInternal returns false (no fallback to legacy MANAGE_RESOURCES check).
     */
    @Nested
    class WhenGranularEnabledButNoAccessPrincipal {

        @BeforeEach
        void setGranularEnabledAndNoAccessPrincipal() {
            getBound().setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(), "true");
            when(authorizationService.getCurrentAccessPrincipal()).thenReturn(Optional.empty());
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void canDoToProjectRepoResource_returnsFalse_whenProjectAdminButNoAccessPrincipal(RepoActionType repoAction) {
            when(securityService.isAdmin()).thenReturn(false);
            setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);

            boolean result = projectsService.canDoToProjectRepoResource(repoAction, "my-repo", PROJECT_A,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
            verify(authorizationService).getCurrentAccessPrincipal();
            verify(grpcProjectClient, never()).canDoAction(anyString(), anyString(), any(), any(RoleActions.class));
            verify(grpcProjectClient, never()).canProjectAdminDoOnResource(anyString(), any(), any(), anyString(),
                    any());
        }

        @ParameterizedTest
        @EnumSource(RepoActionType.class)
        void canDoInternal_returnsFalse_whenProjectAdminButNoAccessPrincipal(RepoActionType repoAction) {
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);

            boolean result = projectsService.canDoInternal(repoAction, "my-repo", PROJECT_A, simpleUser,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
            verify(authorizationService).getCurrentAccessPrincipal();
            verify(grpcProjectClient, never()).canDoAction(anyString(), anyString(), any(), any(RoleActions.class));
            verify(grpcProjectClient, never()).canProjectAdminDoOnResource(anyString(), any(), any(), anyString(),
                    any());
        }
    }

    @Test
    void canXrayIndexProjectRepoResource_returnsTrue_whenAdmin() {
        when(securityService.isAdmin()).thenReturn(true);

        assertTrue(projectsService.canXrayIndexProjectRepoResource(REPO_A.getRepoKey()));

        verify(grpcProjectClient, never()).canProjectAdminDoOnResource(anyString(), any(), any(), anyString(), any());
    }

    @Test
    void canXrayIndexProjectRepoResource_returnsFalse_whenUnauthenticated() {
        when(securityService.isAdmin()).thenReturn(false);
        SecurityContextHolder.clearContext();

        assertFalse(projectsService.canXrayIndexProjectRepoResource(REPO_A.getRepoKey()));

        verify(grpcProjectClient, never()).canProjectAdminDoOnResource(anyString(), any(), any(), anyString(), any());
    }


    @Test
    void canXrayIndexProjectRepoResource_returnsTrue_whenUserHasTriggerSecurityPermission() {
        when(securityService.isAdmin()).thenReturn(false);
        setSimpleUserAuthentication(false);
        when(grpcProjectClient.getProjectForResource(REPO_A.getRepoKey(), ResourceType.REPO)).thenReturn(PROJECT_A);
        when(grpcProjectClient.canProjectAdminDoOnResource(REPO_A.getRepoKey(), ResourceType.REPO,
                List.of(RoleActions.TRIGGER_SECURITY), USER, null)).thenReturn(true);

        assertTrue(projectsService.canXrayIndexProjectRepoResource(REPO_A.getRepoKey()));

        verify(grpcProjectClient).canProjectAdminDoOnResource(REPO_A.getRepoKey(), ResourceType.REPO,
                List.of(RoleActions.TRIGGER_SECURITY), USER, null);
    }

    @Test
    void canXrayIndexProjectRepoResource_returnsFalse_whenUserLacksTriggerSecurityPermission() {
        when(securityService.isAdmin()).thenReturn(false);
        setSimpleUserAuthentication(false);
        when(grpcProjectClient.getProjectForResource(REPO_A.getRepoKey(), ResourceType.REPO)).thenReturn(PROJECT_A);
        when(grpcProjectClient.canProjectAdminDoOnResource(REPO_A.getRepoKey(), ResourceType.REPO,
                List.of(RoleActions.TRIGGER_SECURITY), USER, null)).thenReturn(false);

        assertFalse(projectsService.canXrayIndexProjectRepoResource(REPO_A.getRepoKey()));
    }

    /**
     * Tests for {@link ProjectsServiceImpl#canReadFullRepoConfig} and
     * {@link ProjectsServiceImpl#canReadFullRepoConfigInternal}, including the RTDEV-83999 regression fix
     * that allows users with a UPDATE/DELETE grant on a default-project repo to read its full config.
     */
    @Nested
    class CanReadFullRepoConfig {

        private static final String REPO_KEY = "full-config-repo";

        @Test
        void canReadFullRepoConfig_returnsTrue_whenUserIsPlatformAdmin() {
            when(securityService.isAdmin()).thenReturn(true);

            boolean result = projectsService.canReadFullRepoConfig(REPO_KEY, RepoDetailsType.LOCAL);

            assertTrue(result);
            verify(grpcProjectClient, never()).getProjectForResource(anyString(), any());
        }

        @Test
        void canReadFullRepoConfig_returnsFalse_whenUnauthenticated() {
            when(securityService.isAdmin()).thenReturn(false);

            boolean result = projectsService.canReadFullRepoConfig(REPO_KEY, RepoDetailsType.LOCAL);

            assertFalse(result);
        }

        // ---------- legacy mode (granular OFF) ----------

        @Test
        void canReadFullRepoConfigInternal_legacyMode_returnsTrue_whenProjectAdminHasManageResources() {
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(grpcProjectClient.canProjectAdminDoOnResource(REPO_KEY, ResourceType.REPO,
                    List.of(RoleActions.MANAGE_RESOURCES), USER, null)).thenReturn(true);

            boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                    RepoDetailsType.LOCAL);

            assertTrue(result);
        }

        @Test
        void canReadFullRepoConfigInternal_legacyMode_returnsTrue_whenAccessTokenGrantsWrite() {
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
            when(grpcProjectClient.canProjectAdminDoOnResource(REPO_KEY, ResourceType.REPO,
                    List.of(RoleActions.MANAGE_RESOURCES), USER, null)).thenReturn(false);
            when(authorizationService.isGrantedByAccessToken(any(PermissionScope.class))).thenAnswer(invocation -> {
                PermissionScope scope = invocation.getArgument(0);
                return scope != null && scope.getAction() == Action.WRITE;
            });

            boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                    RepoDetailsType.LOCAL);

            assertTrue(result);
        }

        @Test
        void canReadFullRepoConfigInternal_legacyMode_returnsFalse_whenNoPermissionGranted() {
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
            when(authorizationService.isGrantedByAccessToken(any(PermissionScope.class))).thenReturn(false);

            boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
        }

        @Test
        void canReadFullRepoConfigInternal_legacyMode_onlyChecksUpdateAction_notDelete() {
            // Doc item 2: granular OFF maps every action to Action.WRITE so checking DELETE would be redundant.
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
            when(authorizationService.isGrantedByAccessToken(any(PermissionScope.class))).thenReturn(false);

            projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser, RepoDetailsType.LOCAL);

            // Single legacy check, with WRITE permission (UPDATE maps to WRITE in legacy mode).
            verify(authorizationService, times(1)).isGrantedByAccessToken(argThat(scope ->
                    scope != null && scope.getAction() == Action.WRITE));
        }

        @Test
        void canReadFullRepoConfigInternal_legacyMode_returnsFalse_whenAccessClientExceptionIsThrown() {
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
            when(grpcProjectClient.isProjectAdmin(USER, PROJECT_A, null)).thenReturn(true);
            when(grpcProjectClient.canProjectAdminDoOnResource(REPO_KEY, ResourceType.REPO,
                    List.of(RoleActions.MANAGE_RESOURCES), USER, null))
                    .thenThrow(new AccessClientException("Access service unavailable"));

            boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                    RepoDetailsType.LOCAL);

            assertFalse(result);
        }

        /**
         * RTDEV-83999 regression test (legacy mode): a user with an access token granting WRITE on a
         * repo in the {@code default} project must be able to read its full configuration. Previously
         * the broad default-project guard rejected this case unconditionally.
         */
        @Test
        void canReadFullRepoConfigInternal_legacyMode_returnsTrue_inDefaultProject_whenTokenGrantsWrite() {
            SimpleUser simpleUser = setSimpleUserAuthentication(false);
            when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(DEFAULT_PROJECT);
            when(authorizationService.isGrantedByAccessToken(any(PermissionScope.class))).thenAnswer(invocation -> {
                PermissionScope scope = invocation.getArgument(0);
                return scope != null && scope.getAction() == Action.WRITE
                        && REPO_KEY.equals(scope.getTarget());
            });

            boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                    RepoDetailsType.LOCAL);

            assertTrue(result);
        }

        // ---------- granular mode (granular ON, with access principal) ----------

        @Nested
        class GranularMode {

            private AuthClient authClient;

            @BeforeEach
            void setGranularEnabledAndAccessPrincipal() {
                getBound().setProperty(ConstantValues.enableGranularProjectRepoPermissions.getPropertyName(),
                        "true");
                AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
                when(authorizationService.getCurrentAccessPrincipal()).thenReturn(Optional.of(accessPrincipal));
                authClient = mock(AuthClient.class);
                when(accessClient.cachedAuth()).thenReturn(authClient);
            }

            @Test
            void canReadFullRepoConfigInternal_granular_returnsTrue_whenPrincipalCanWrite() {
                SimpleUser simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
                when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                        .thenAnswer(invocation -> {
                            PermissionScope scope = invocation.getArgument(1);
                            return scope != null && scope.getAction() == Action.WRITE;
                        });

                boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                        RepoDetailsType.LOCAL);

                assertTrue(result);
                // UPDATE branch is enough — DELETE branch must not be evaluated due to short-circuit
                verify(authClient, times(1)).principalCanDo(any(AccessPrincipal.class),
                        any(PermissionScope.class));
            }

            @Test
            void canReadFullRepoConfigInternal_granular_returnsTrue_whenPrincipalCanDeleteButNotWrite() {
                SimpleUser simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
                when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                        .thenAnswer(invocation -> {
                            PermissionScope scope = invocation.getArgument(1);
                            return scope != null && scope.getAction() == Action.DELETE;
                        });

                boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                        RepoDetailsType.LOCAL);

                assertTrue(result);
                // Both UPDATE (mapped to WRITE) and DELETE branches are evaluated
                verify(authClient, times(2)).principalCanDo(any(AccessPrincipal.class),
                        any(PermissionScope.class));
            }

            @Test
            void canReadFullRepoConfigInternal_granular_returnsFalse_whenPrincipalCannotWriteOrDelete() {
                SimpleUser simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
                when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                        .thenReturn(false);

                boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                        RepoDetailsType.LOCAL);

                assertFalse(result);
                verify(authClient, times(2)).principalCanDo(any(AccessPrincipal.class),
                        any(PermissionScope.class));
            }

            /**
             * RTDEV-83999 regression test (granular mode): the narrowed CREATE-only guard inside
             * canDoInternal lets UPDATE/DELETE checks reach Access for default-project repos as well.
             */
            @Test
            void canReadFullRepoConfigInternal_granular_returnsTrue_inDefaultProject_whenPrincipalCanWrite() {
                SimpleUser simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(DEFAULT_PROJECT);
                when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                        .thenAnswer(invocation -> {
                            PermissionScope scope = invocation.getArgument(1);
                            return scope != null && scope.getAction() == Action.WRITE;
                        });

                boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                        RepoDetailsType.LOCAL);

                assertTrue(result);
            }

            @Test
            void canReadFullRepoConfigInternal_granular_returnsTrue_inDefaultProject_whenPrincipalCanDelete() {
                SimpleUser simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(DEFAULT_PROJECT);
                when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                        .thenAnswer(invocation -> {
                            PermissionScope scope = invocation.getArgument(1);
                            return scope != null && scope.getAction() == Action.DELETE;
                        });

                boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                        RepoDetailsType.LOCAL);

                assertTrue(result);
            }

            @Test
            void canReadFullRepoConfigInternal_granular_returnsFalse_inDefaultProject_whenPrincipalLacksGrants() {
                SimpleUser simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(DEFAULT_PROJECT);
                when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                        .thenReturn(false);

                boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                        RepoDetailsType.LOCAL);

                assertFalse(result);
            }

            @Test
            void canReadFullRepoConfigInternal_granular_returnsFalse_whenAccessClientExceptionIsThrown() {
                SimpleUser simpleUser = setSimpleUserAuthentication(false);
                when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
                when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                        .thenThrow(new AccessClientException("Access service unavailable"));

                boolean result = projectsService.canReadFullRepoConfigInternal(REPO_KEY, simpleUser,
                        RepoDetailsType.LOCAL);

                assertFalse(result);
            }

            @Test
            void canReadFullRepoConfig_publicApi_endToEnd_returnsTrueViaPrincipalCanDo() {
                setSimpleUserAuthentication(false);
                when(securityService.isAdmin()).thenReturn(false);
                when(grpcProjectClient.getProjectForResource(REPO_KEY, ResourceType.REPO)).thenReturn(PROJECT_A);
                when(authClient.principalCanDo(any(AccessPrincipal.class), any(PermissionScope.class)))
                        .thenReturn(true);

                boolean result = projectsService.canReadFullRepoConfig(REPO_KEY, RepoDetailsType.LOCAL);

                assertTrue(result);
            }
        }
    }

    @Nested
    class GrpcClientCachingTests {

        @Test
        void grpcClientIsBuiltOnFirstCall() {
            GrpcProjectClient client = projectsService.getGrpcProjectClient();

            assertNotNull(client, "Expected a non-null GrpcProjectClient on first call");
            verify(accessClient, times(1)).cachedGrpcProject();
        }

        @Test
        void grpcClientIsReusedAcrossMultipleCalls() {
            for (int i = 0; i < 3; i++) {
                projectsService.getGrpcProjectClient();
            }

            verify(accessClient, times(1)).cachedGrpcProject();
        }

        @Test
        void nullAccessClientThrows() {
            when(accessService.getAccessClient()).thenReturn(null);

            assertThrows(AccessClientStateException.class, () -> projectsService.getGrpcProjectClient());
        }

        @Test
        void onClientUpdateNullsTheClient() {
            projectsService.getGrpcProjectClient();
            verify(accessClient, times(1)).cachedGrpcProject();

            projectsService.onClientUpdate();
            projectsService.getGrpcProjectClient();

            verify(accessClient, times(2)).cachedGrpcProject();
        }

        @Test
        void grpcClientBuiltOnceUnderConcurrentFirstAccess() throws Exception {
            int threadCount = 10;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<GrpcProjectClient>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return projectsService.getGrpcProjectClient();
                }));
            }

            executor.shutdown();
            GrpcProjectClient expected = futures.get(0).get();
            for (Future<GrpcProjectClient> future : futures) {
                assertSame(expected, future.get(), "All threads must receive the same GrpcProjectClient instance");
            }
            verify(accessClient, times(1)).cachedGrpcProject();
        }
    }
}