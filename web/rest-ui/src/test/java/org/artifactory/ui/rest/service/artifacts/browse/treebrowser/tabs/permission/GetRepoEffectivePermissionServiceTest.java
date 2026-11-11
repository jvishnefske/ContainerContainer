package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.permission;

import com.google.common.collect.ImmutableSetMultimap;
import org.artifactory.addon.AddonsManager;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.ArtifactoryGroupCachingRepoImpl;
import org.artifactory.security.MutableAceInfo;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.SecurityServiceImpl;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessServiceImpl;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.security.service.access.AccessUserGroupStoreService;
import org.artifactory.storage.db.security.service.access.GroupMapper;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission.RepoPermissionsModel;
import org.artifactory.ui.rest.service.distribution.GetReleaseBundleEffectivePermissionService;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.group.GroupsClient;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.model.Realm;
import org.jfrog.access.rest.group.Group;
import org.jfrog.access.rest.group.Groups;
import org.jfrog.access.rest.user.Users;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Yevdo Abramov
 * Created on 27/10/2020
 */
class GetRepoEffectivePermissionServiceTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private AuthorizationService authService;
    @Mock
    private RepositoryService repoService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private BuildFacade buildFacade;
    @Mock
    private GetReleaseBundleEffectivePermissionService releaseBundleEffectivePermissionService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private RepoPath repoPath;
    @Mock
    private UserInfo userInfo;
    @Mock
    private RepoAcl acl1;
    @Mock
    private RepoAcl acl2;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private AolService aolService;
    @Mock
    private MutableAceInfo ace;
    @Mock
    private RepoPermissionTarget permissionTarget;

    private GetRepoEffectivePermissionService service;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new GetRepoEffectivePermissionService(
                buildFacade,
                authService,
                repoService,
                userGroupService,
                releaseBundleEffectivePermissionService,
                repositoryConfigService
        );
    }

    @Test
    void testExecuteWhenNoPermissions() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        when(request.getQueryParamByKey("repoKey")).thenReturn("my_repo");
        when(request.getQueryParamByKey("path")).thenReturn("my_path");
        when(authService.canManage(any(RepoPath.class))).thenReturn(false);

        service.execute(request, response);

        verify(request).getQueryParamByKey("repoKey");
        verify(request).getQueryParamByKey("path");
        verify(authService).canManage(any(RepoPath.class));
        verifyNoMoreInteractions(userGroupService);
    }

    @Test
    void testGetRepoPermissionModelReturnsExpectedModel() {
        when(authService.getRepoPathAcls(repoPath)).thenReturn(List.of(acl1, acl2));
        when(authService.isInternalAcl(acl1)).thenReturn(false);
        when(authService.isInternalAcl(acl2)).thenReturn(true);
        when(ace.getPrincipal()).thenReturn("user1");
        when(ace.isGroup()).thenReturn(false);
        when(acl1.getMutableAces()).thenReturn(Set.of(ace));
        when(acl1.getPermissionTarget()).thenReturn(permissionTarget);
        when(permissionTarget.getName()).thenReturn("target1");
        when(userInfo.getUsername()).thenReturn("user1");
        when(userGroupService.getAllUsersWithUserInfoWithOutPasswords(true)).thenReturn(List.of(userInfo));
        when(userGroupService.getAllUsersInGroups(List.of(userInfo))).thenReturn(ImmutableSetMultimap.of());

        try (MockedStatic<ContextHelper> contextHelperMockedStatic = mockStatic(ContextHelper.class)) {
            contextHelperMockedStatic.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(AolService.class)).thenReturn(aolService);

            final RepoPermissionsModel model = service.getRepoPermissionModel(repoPath);

            assertNotNull(model);
            assertEquals(1, model.getUserEffectivePermissions().size());
            assertTrue(model.getGroupEffectivePermissions().isEmpty());
            assertEquals(1, model.getPermissionTargets().size());
            assertEquals("target1", model.getPermissionTargets().getFirst().getPermissionName());
        }
    }

    @Test
    void testExecuteIsAcceptingManagePermissions() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        when(request.getQueryParamByKey("repoKey")).thenReturn("my_repo");
        when(request.getQueryParamByKey("path")).thenReturn("my_path");
        when(authService.canManage(any(RepoPath.class))).thenReturn(true);

        service.execute(request, response);

        verify(request).getQueryParamByKey("repoKey");
        verify(request).getQueryParamByKey("path");
        verify(authService).canManage(any(RepoPath.class));
        verify(authService).getRepoPathAcls(any());
    }

    @Test
    void testLogShippingRepo() {
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        RestResponse<?> response = mock(RestResponse.class);
        doAnswer(invocationOnMock -> {
            assertEquals(invocationOnMock.getArguments().length, 1);
            RepoPermissionsModel repoPermissionsModel = invocationOnMock.getArgument(0, RepoPermissionsModel.class);
            assertNotNull(repoPermissionsModel);
            assertEquals(repoPermissionsModel.getPermissionTargets().size(), 0);
            assertEquals(repoPermissionsModel.getGroupEffectivePermissions().size(), 1);
            assertEquals(repoPermissionsModel.getUserEffectivePermissions().size(), 1);
            repoPermissionsModel.getUserEffectivePermissions().forEach(curr -> {
                assertTrue(curr.getPermission().isRead());
                assertFalse(curr.getPermission().isAnnotate());
                assertTrue(curr.getPermission().isDelete());
                assertFalse(curr.getPermission().isDeploy());
                assertFalse(curr.getPermission().isDistribute());
            });
            repoPermissionsModel.getGroupEffectivePermissions().forEach(curr -> {
                assertTrue(curr.getPermission().isRead());
                assertFalse(curr.getPermission().isAnnotate());
                assertTrue(curr.getPermission().isDelete());
                assertFalse(curr.getPermission().isDeploy());
                assertFalse(curr.getPermission().isDistribute());
            });
            return null;
        }).when(response).iModel(any());

        when(request.getQueryParamByKey("repoKey")).thenReturn(LOG_SHIPPING_REPO_KEY);
        when(request.getQueryParamByKey("path")).thenReturn("my_path");

        // Repo type of pipe info
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setKey(LOG_SHIPPING_REPO_KEY);
        localRepoDescriptor.setType(RepoType.Generic);
        when(repoService.localOrFederatedRepoDescriptorByKey(LOG_SHIPPING_REPO_KEY)).thenReturn(localRepoDescriptor);

        // Security mocking
        when(authService.canManage(any(RepoPath.class))).thenReturn(true);
        HashMap<String, Boolean> adminUserMap = new HashMap<>();
        adminUserMap.put("test", true);
        when(userGroupService.getAllUsersAndAdminStatus(true)).thenReturn(adminUserMap);
        ArrayList<String> adminGroups = new ArrayList<>();
        adminGroups.add("groupAdmin");
        when(userGroupService.getAllAdminGroupsNames()).thenReturn(adminGroups);

        // Context creation
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AolService aolService = mock(AolService.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);

        service.execute(request, response);

        verify(request).getQueryParamByKey("repoKey");
        verify(request).getQueryParamByKey("path");
        verify(authService).canManage(any(RepoPath.class));
    }

    @Test
    void testExecutePipelineInfoRepo() {
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        RestResponse<?> response = mock(RestResponse.class);
        doAnswer(invocationOnMock -> {
            assertEquals(invocationOnMock.getArguments().length, 1);
            RepoPermissionsModel repoPermissionsModel = invocationOnMock.getArgument(0, RepoPermissionsModel.class);
            assertNotNull(repoPermissionsModel);
            assertEquals(repoPermissionsModel.getPermissionTargets().size(), 0);
            assertEquals(repoPermissionsModel.getGroupEffectivePermissions().size(), 1);
            assertEquals(repoPermissionsModel.getUserEffectivePermissions().size(), 1);
            return null;
        }).when(response).iModel(any());

        when(request.getQueryParamByKey("repoKey")).thenReturn("my_repo");
        when(request.getQueryParamByKey("path")).thenReturn("my_path");
        // Repo type of pipe info
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setKey("my_repo");
        localRepoDescriptor.setType(RepoType.PipeInfo);
        when(repoService.localOrFederatedRepoDescriptorByKey("my_repo")).thenReturn(localRepoDescriptor);

        // Security mocking
        when(authService.canManage(any(RepoPath.class))).thenReturn(true);
        HashMap<String, Boolean> adminUserMap = new HashMap<>();
        adminUserMap.put("test", true);
        when(userGroupService.getAllUsersAndAdminStatus(true)).thenReturn(adminUserMap);
        ArrayList<String> adminGroups = new ArrayList<>();
        adminGroups.add("groupAdmin");
        when(userGroupService.getAllAdminGroupsNames()).thenReturn(adminGroups);

        // Context creation
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AolService aolService = mock(AolService.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);

        service.execute(request, response);

        verify(request).getQueryParamByKey("repoKey");
        verify(request).getQueryParamByKey("path");
        verify(authService).canManage(any(RepoPath.class));
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testBuildEffectivePermissionsExcludingInternals() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        String buildRepoKey = "proj-build-info";
        RepoPath repoPath = RepoPathFactory.create(buildRepoKey, "my_path");
        when(request.getQueryParamByKey("repoKey")).thenReturn(buildRepoKey);
        when(request.getQueryParamByKey("path")).thenReturn(repoPath.getPath());
        when(authService.canManage(any(RepoPath.class))).thenReturn(true);
        LocalRepoDescriptor buildRepo = new LocalRepoDescriptor();
        buildRepo.setKey(buildRepoKey);
        buildRepo.setType(RepoType.BuildInfo);
        when(repoService.localOrFederatedRepoDescriptorByKey(buildRepoKey)).thenReturn(buildRepo);
        service.execute(request, response);

        verify(request).getQueryParamByKey("repoKey");
        verify(request).getQueryParamByKey("path");
        verify(authService).canManage(any(RepoPath.class));
        verify(buildFacade).getBuildEffectivePermissions(repoPath.getRepoKey(), repoPath.getPath());
    }

    @Test
    void effectivePermissionExecutesGetGroupAndUsersEffectively() {
        // given
        ArtifactoryApplicationContext context = mock(ArtifactoryApplicationContext.class);
        when(context.beanForType(AolService.class)).thenReturn(mock(AolService.class));
        ArtifactoryContextThreadBinder.bind(context);

        AccessServiceImpl accessService = new AccessServiceImpl(
                mock(ApplicationEventPublisher.class),
                mock(ConfigsService.class),
                context);

        AccessClient accessClient = mock(AccessClient.class);
        accessService.updateAccessClient(accessClient);

        GroupsClient groupsClient = mock(GroupsClient.class);
        when(accessClient.groups()).thenReturn(groupsClient);

        Realm realm = mock(Realm.class);
        when(realm.getName()).thenReturn("realName");

        String groupName = "groupName";
        Group group = mock(Group.class);
        when(group.getRealm()).thenReturn(realm);
        when(group.getName()).thenReturn(groupName);
        when(group.getCustomData(GroupMapper.ArtifactoryBuiltInGroupProperty.artifactory_admin.name()))
                .thenReturn(Boolean.TRUE.toString());

        Groups groups = mock(Groups.class);
        when(groups.getGroups()).thenReturn(List.of(group));
        when(groupsClient.findGroups()).thenReturn(groups);

        UsersClient usersClient = mock(UsersClient.class);
        when(accessClient.cachedUsers()).thenReturn(usersClient);

        Users users = mock(Users.class);
        when(usersClient.findUsers()).thenReturn(users);

        AccessUserGroupStoreService userGroupStoreService = new AccessUserGroupStoreService();
        userGroupStoreService.setAccessService(accessService);

        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(any())).thenReturn(
                mock(LocalRepoDescriptor.class));

        ArtifactoryGroupCachingRepoImpl artifactoryGroupCache =
                new ArtifactoryGroupCachingRepoImpl(userGroupStoreService, mock(AddonsManager.class));

        SecurityServiceImpl securityService = spy(new SecurityServiceImpl());
        securityService.setGroupCachingRepo(artifactoryGroupCache);
        securityService.setUserGroupStoreService(userGroupStoreService);

        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getQueryParamByKey("repoKey")).thenReturn("repoKey");

        AuthorizationService authorizationService = mock(AuthorizationService.class);
        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(true);

        GetRepoEffectivePermissionService getRepoEffectivePermissionService =
                new GetRepoEffectivePermissionService(
                        mock(BuildFacade.class),
                        authorizationService,
                        repositoryService,
                        securityService,
                        mock(GetReleaseBundleEffectivePermissionService.class),
                        repositoryConfigService
                );

        // when
        getRepoEffectivePermissionService.execute(artifactoryRestRequest, mock(RestResponse.class));

        // then
        verify(groupsClient, times(1)).findGroups();
        verify(usersClient, times(1)).findUsers();

        // second (and other) call gets only users since there are no users cache
        // when
        getRepoEffectivePermissionService.execute(artifactoryRestRequest, mock(RestResponse.class));

        // then
        verify(groupsClient, times(1)).findGroups();
        verify(usersClient, times(2)).findUsers();

        // third call
        // when
        getRepoEffectivePermissionService.execute(artifactoryRestRequest, mock(RestResponse.class));

        // then
        verify(groupsClient, times(1)).findGroups();
        verify(usersClient, times(3)).findUsers();
    }
}