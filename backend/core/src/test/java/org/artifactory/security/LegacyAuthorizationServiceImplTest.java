package org.artifactory.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.PermissionHeuristicScore;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.factory.InfoFactory;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.logging.throttle.LogThrottleMarker;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.security.AceImpl;
import org.artifactory.model.security.BuildPermissionTargetImpl;
import org.artifactory.model.security.MutableBuildAclImpl;
import org.artifactory.model.security.PrincipalPermissionImpl;
import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.artifactory.model.security.UserGroupImpl;
import org.artifactory.model.security.UserImpl;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.security.access.AccessResponseAuthentication;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.interceptor.SecurityConfigurationChangesInterceptors;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.signature.SignedUrlAuthenticationToken;
import org.artifactory.storage.db.init.migration.MarkerMigrationHelper;
import org.artifactory.storage.db.init.migration.dao.MigrationStatusDao;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageServiceImpl;
import org.artifactory.storage.security.service.AclCache;
import org.artifactory.storage.security.service.AclStoreService;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.storage.security.service.UserLockInMemoryService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.RepoPathUtils;
import org.easymock.EasyMock;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.v2.Action;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.Assertions;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.collections.Maps;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.api.security.AuthorizationService.INTERNAL_PERMISSION_PREFIX;
import static org.artifactory.api.security.SecurityService.USER_SYSTEM;
import static org.artifactory.descriptor.repo.RepoType.PipeInfo;
import static org.artifactory.repo.config.RepoConfigDefaultValues.POLICY_REPORTS_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.repo.trash.TrashService.TRASH_KEY;
import static org.artifactory.security.ArtifactoryPermission.ANNOTATE;
import static org.artifactory.security.ArtifactoryPermission.DELETE;
import static org.artifactory.security.ArtifactoryPermission.DEPLOY;
import static org.artifactory.security.ArtifactoryPermission.MANAGE;
import static org.artifactory.security.ArtifactoryPermission.READ;
import static org.artifactory.security.PermissionTarget.ANY_DISTRIBUTION_REPO;
import static org.artifactory.security.PermissionTarget.ANY_LOCAL_REPO;
import static org.artifactory.security.PermissionTarget.ANY_REMOTE_REPO;
import static org.artifactory.security.PermissionTarget.ANY_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.not;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests were moved here from SecurityServiceImplTest as part of the SecurityService/AuthorizationService separation.
 *
 * @author Andrey Tashlitsky
 */
public class LegacyAuthorizationServiceImplTest extends ArtifactoryHomeBoundTest {

    private static final String PROJECT_A = "proj";
    private static final String PROJECT_B = "proj2";
    private static final RepoPath REPO = RepoPathFactory.create("my-repo");
    private static final RepoPath SHARED_REPO = RepoPathFactory.create("shared-repo");
    private static final String PERMISSION_ACTOR = "permission_actor";

    private SecurityContextImpl securityContext;
    private AccessService accessService;
    private AuthSettingsClient authSettingsClient;
    private LegacyAuthorizationServiceImpl service;
    private List<RepoAcl> testAcls;
    private AclStoreService aclStoreServiceMock;
    private InternalRepositoryService repositoryServiceMock;
    private SecurityService securityServiceMock;
    private BuildFacade buildFacadeMock;
    private LocalRepo localRepoMock;
    private LocalRepo cacheRepoMock;
    private UserGroupStoreService userGroupStoreService;
    private ApiKeyManager apiKeyManager;
    private SecurityServiceImplTestHelper securityServiceImplTestHelper;
    private AclCache<RepoPermissionTarget> aclCache;
    private AclCache<RepoPermissionTarget> emptyAclCache;
    private MarkerMigrationHelper markerMigrationHelper;
    private SecurityConfigurationChangesInterceptors interceptors;
    private final MigrationStatusDao migrationStatusDao = Mockito.mock(MigrationStatusDao.class);
    private ProjectsService projectsService;
    private UserLockInMemoryService userLockInMemoryService;
    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;

    @BeforeClass
    public void initArtifactoryRoles() {
        staticAccessConfigHolder = mockStatic(AccessConfigHolder.class);
        AccessConfigHolder spyAccessConfigHolder = spy(AccessConfigHolder.class);
        staticAccessConfigHolder.when(AccessConfigHolder::getInstance).thenReturn(spyAccessConfigHolder);
        securityServiceImplTestHelper = new SecurityServiceImplTestHelper();
        testAcls = securityServiceImplTestHelper.createTestAcls();
        aclCache = securityServiceImplTestHelper.createUserAndGroupResultMap();
        emptyAclCache = securityServiceImplTestHelper.createEmptyAclCache();
        aclStoreServiceMock = createMock(AclStoreService.class);
        repositoryServiceMock = createRepoServiceMock();
        securityServiceMock = spy(new SecurityServiceImpl());
        projectsService = mock(ProjectsService.class);
        buildFacadeMock = createBuildFacadeMock();
        userGroupStoreService = createMock(UserGroupStoreService.class);
        apiKeyManager = mock(ApiKeyManager.class);
        localRepoMock = createLocalRepoMock();
        cacheRepoMock = createCacheRepoMock();
        accessService = createMock(AccessService.class);
        ReflectionTestUtils.setField(securityServiceMock, "accessService", accessService);
        authSettingsClient = createMock(AuthSettingsClient.class);
        markerMigrationHelper =
                new MarkerMigrationHelper(new MigrationStatusStorageServiceImpl(migrationStatusDao), homeStub);
        projectsService = mock(ProjectsService.class);
        interceptors = mock(SecurityConfigurationChangesInterceptors.class);
        MarkerMigrationHelper.bind(markerMigrationHelper);
        userLockInMemoryService = createMock(UserLockInMemoryService.class);
    }

    @AfterClass
    public void afterAll() {
        staticAccessConfigHolder.close();
    }

    private InternalRepositoryService createRepoServiceMock() {
        InternalRepositoryService repositoryService = createNiceMock(InternalRepositoryService.class);
        replay(repositoryService);
        return repositoryService;
    }

    private BuildFacade createBuildFacadeMock() {
        BuildFacade buildFacade = createMock(BuildFacade.class);
        expect(buildFacade.getBuildInfoRepoForDefaultProject())
                .andReturn(BUILDINFO_REPO_KEY)
                .anyTimes();
        expect(buildFacade.getBuildInfoRepoKeyByProject(anyObject(), EasyMock.anyBoolean()))
                .andReturn(BUILDINFO_REPO_KEY).anyTimes();
        expect(buildFacade.normaliseBuildRepoName(BUILDINFO_REPO_KEY))
                .andReturn(BUILDINFO_REPO_KEY).anyTimes();
        expect(buildFacade.isBuildInfoRepo(BUILDINFO_REPO_KEY)).andReturn(true).anyTimes();
        expect(buildFacade.isBuildInfoRepo(not(eq(BUILDINFO_REPO_KEY)))).andReturn(false).anyTimes();
        replay(buildFacade);
        return buildFacade;
    }

    @BeforeMethod
    public void setUp() {
        homeStub.setProperty(ConstantValues.allowAnyUpgrade, "7.0.0-m001");
        // create new security context
        securityContext = new SecurityContextImpl();
        SecurityContextHolder.setContext(securityContext);
        // new service instance
        service = spy(new LegacyAuthorizationServiceImpl());
        // set the aclManager mock on the security service
        setField(service, "aclStoreService", aclStoreServiceMock);
        setField(service, "accessService", accessService);
        setField(service, "repositoryService", repositoryServiceMock);
        setField(service, "securityService", securityServiceMock);
        setField(service, "userGroupStoreService", userGroupStoreService);
        setField(service, "buildFacade", buildFacadeMock);
        setField(service, "projectsService", projectsService);
        setField(service, "interceptors", interceptors);
        // reset mocks
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock,
                userLockInMemoryService, accessService, userGroupStoreService, authSettingsClient);
        Mockito.reset(securityServiceMock, apiKeyManager);
    }

    private Authentication setAdminAuthentication() {
        return setAdminAuthentication("spiderman");
    }

    private Authentication setAdminAuthentication(String name) {
        SimpleUser adminUser = createAdminUser(name);
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                adminUser, null, SimpleUser.ADMIN_GAS);
        securityContext.setAuthentication(authenticationToken);
        return authenticationToken;
    }

    private static SimpleUser createAdminUser(String name) {
        UserInfo userInfo = new UserInfoBuilder(name).admin(true).updatableProfile(true).build();
        return new SimpleUser(userInfo);
    }

    private void setProjectAdminAuthentication(boolean enable, boolean enableResourceManager) {
        setProjectAdminAuthentication("user", enable, enableResourceManager);
    }

    private void setProjectAdminAuthentication(String name, boolean enable, boolean enableResourceManager,
            String... groups) {
        Authentication authentication = setSimpleUserAuthentication(name, groups);
        SimpleUser user = (SimpleUser) authentication.getPrincipal();
        if (enable) {
            when(projectsService.getAllProjectsManagedByUser()).thenReturn(List.of(PROJECT_A, PROJECT_B));
            when(projectsService.getProjectKeyOfRepository(REPO.getRepoKey())).thenReturn(PROJECT_A);
            when(projectsService.getProjectRepositoriesKeys(Mockito.eq(PROJECT_B), anyBoolean()))
                    .thenReturn(Set.of(SHARED_REPO.getRepoKey()));
            when(projectsService.getProjectRepositoriesKeys(Mockito.eq(PROJECT_A), anyBoolean()))
                    .thenReturn(Set.of(REPO.getRepoKey()));
            when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq("cool-repo"),
                    Mockito.eq(PROJECT_B), any()))
                    .thenReturn(true);
            when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq(REPO.getRepoKey()), any()))
                    .thenReturn(true);
            when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq(SHARED_REPO.getRepoKey()), any()))
                    .thenReturn(true);
        } else {
            when(projectsService.getAllProjectsManagedByUser()).thenReturn(Collections.emptyList());
            when(projectsService.getProjectKeyOfRepository(REPO.getRepoKey()))
                    .thenReturn(ProjectsService.DEFAULT_PROJECT);
            when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq("cool-repo"),
                    Mockito.eq(PROJECT_B), any())).thenReturn(false);
        }
        when(projectsService.isProjectAdmin(PROJECT_A, user)).thenReturn(enable);
        when(projectsService.isResourceManager(user)).thenReturn(enableResourceManager);
        when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq("cool-repo"),
                Mockito.eq(PROJECT_A), any()))
                .thenReturn(enable);
        when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq(SHARED_REPO.getRepoKey()), any()))
                .thenReturn(enable);
        when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq(REPO.getRepoKey()), any()))
                .thenReturn(enable);
    }

    private Authentication setSimpleUserAuthentication(String username, String... groups) {
        return setSimpleUserAuthentication(username, false, groups);
    }

    private Authentication setSimpleUserAuthentication(String username, boolean mfaEnabled, String... groups) {
        SimpleUser simpleUser = createNonAdminUser(username, mfaEnabled, groups);
        return setSimpleUserAuthentication(simpleUser);
    }

    private Authentication setSimpleUserAuthentication(SimpleUser simpleUser) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                simpleUser, "password", SimpleUser.USER_GAS);
        securityContext.setAuthentication(authenticationToken);
        return authenticationToken;
    }

    private static SimpleUser createNonAdminUser(String username, String... groups) {
        return createNonAdminUser(username, false, groups);
    }

    private static SimpleUser createNonAdminUser(String username, boolean mfaEnabled, String... groups) {
        UserInfo userInfo = new UserInfoBuilder(username).updatableProfile(true)
                .mfaStatus(mfaEnabled ? org.artifactory.security.UserMFAStatus.VERIFY :
                        org.artifactory.security.UserMFAStatus.NONE)
                .internalGroups(new HashSet<>(Arrays.asList(groups))).build();
        return new SimpleUser(userInfo);
    }

    @Test
    public void testAnotherProjectAdminHasNoPermissionsOnProjectRelatedPath() {
        setProjectAdminAuthentication(false, false);
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(REPO.getRepoKey())).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, REPO.getRepoKey()))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        expectGetAllAclsCallWithAnyArray();
        expectAclScan();

        assertFalse(service.canManage(REPO), "User should not have manage permissions for this path");
        assertFalse(service.canDelete(REPO), "User should not have delete permissions for this path");
        assertFalse(service.canAnnotate(REPO), "User should not have annotate permissions for this path");
        assertFalse(service.canDeploy(REPO), "User should not have deploy permissions for this path");
        assertFalse(service.canRead(REPO), "User should not have read permissions for this path");

        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test
    public void spidermanCanDoAnything() {
        setAdminAuthentication();

        RepoPath path = InternalRepoPathFactory.create("someRepo", "blabla");
        boolean canRead = service.canRead(path);
        assertTrue(canRead);
        boolean canDeploy = service.canDeploy(path);
        assertTrue(canDeploy);
    }

    @Test
    public void userReadAndDeployPermissions() {
        setSimpleUserAuthentication("user");

        RepoPath securedPath = InternalRepoPathFactory.create("securedRepo", "blabla");
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey(securedPath.getRepoKey()))
                .andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(securedPath.getRepoKey())).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE,
                securedPath.getRepoKey())).andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();

        // cannot read the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        boolean hasPermissions =
                service.canRead(securedPath) || service.canManage(securedPath) || service.canDeploy(securedPath) ||
                        service.canDelete(securedPath);
        assertFalse(hasPermissions, "User should not have permissions for this path");
        EasyMock.verify(aclStoreServiceMock, repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        // cannot deploy to the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);

        boolean canDeploy = service.canDeploy(securedPath);
        assertFalse(canDeploy, "User should not have permissions for this path");
        EasyMock.verify(aclStoreServiceMock, repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        RepoPath allowedReadPath = InternalRepoPathFactory.create("testRepo1", "blabla");
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey(allowedReadPath.getRepoKey()))
                .andReturn(localRepoMock).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(allowedReadPath.getRepoKey())).andReturn(null)
                .anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE,
                allowedReadPath.getRepoKey())).andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();

        // can read the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        hasPermissions = service.canRead(allowedReadPath);
        assertTrue(hasPermissions, "User should have read permissions for this path");
        EasyMock.verify(aclStoreServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        // cannot deploy to the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        canDeploy = service.canDeploy(allowedReadPath);
        assertFalse(canDeploy, "User should not have permissions for this path");
        verify(aclStoreServiceMock, repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        // cannot admin the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        boolean canAdmin = service.canManage(allowedReadPath);
        assertFalse(canAdmin, "User should not have permissions for this path");
        EasyMock.verify(aclStoreServiceMock);
    }

    @Test
    public void adminRolePermissions() {
        // user with admin role on permission target 'target1'
        setSimpleUserAuthentication("yossis");

        RepoPath allowedReadPath = InternalRepoPathFactory.create("testRepo1", "blabla");

        // can read the specified path
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1"))
                .andReturn(false).anyTimes();
        expectGetAllAclsCall();
        replay(aclStoreServiceMock, repositoryServiceMock);
        boolean canRead = service.canRead(allowedReadPath);
        assertTrue(canRead, "User should have permissions for this path");
        EasyMock.verify(aclStoreServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        // can deploy to the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        boolean canDeploy = service.canDeploy(allowedReadPath);
        assertTrue(canDeploy, "User should have permissions for this path");
        EasyMock.verify(aclStoreServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        // can admin the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        boolean canAdmin = service.canManage(allowedReadPath);
        assertTrue(canAdmin, "User should have permissions for this path");
        EasyMock.verify(aclStoreServiceMock);
        EasyMock.reset(aclStoreServiceMock);
    }

    @Test
    public void groupPermissions() {
        RepoPath allowedReadPath = InternalRepoPathFactory.create("testRepo1", "**");
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey(allowedReadPath.getRepoKey()))
                .andReturn(localRepoMock).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1"))
                .andReturn(false).anyTimes();
        replay(repositoryServiceMock);

        // cannot deploy to the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        boolean canDeploy = service.canDeploy(allowedReadPath);
        assertFalse(canDeploy, "User should have permissions for this path");
        EasyMock.verify(aclStoreServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        // add the user to a group with permissions and expext permission garnted
        setSimpleUserAuthentication("userwithnopermissions", "deployGroup");
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        canDeploy = service.canDeploy(allowedReadPath);
        assertTrue(canDeploy, "User in a group with permissions for this path");
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        // user is permitted but group is not, expect permission denied as the token scope is by the group
        UserInfo userInfo = new UserInfoBuilder("yossis").updatableProfile(true)
                .mfaStatus(org.artifactory.security.UserMFAStatus.NONE)
                .appliedPermissionsGroups(true)
                .internalGroups(new HashSet<>(Arrays.asList("nodeployGroup"))).build();
        setSimpleUserAuthentication(new SimpleUser(userInfo));
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock, repositoryServiceMock);
        canDeploy = service.canDeploy(allowedReadPath);
        assertFalse(canDeploy, "User in a group with permissions for this path");
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test
    public void userWithPermissionsToAGroupWithTheSameName() {
        setSimpleUserAuthentication(securityServiceImplTestHelper.USER_AND_GROUP_SHARED_NAME,
                securityServiceImplTestHelper.USER_AND_GROUP_SHARED_NAME);

        RepoPath testRepo1Path = InternalRepoPathFactory.create("testRepo1", "**");
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canRead = service.canRead(testRepo1Path);
        assertTrue(canRead, "User should have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        RepoPath testRepo2Path = InternalRepoPathFactory.create("testRepo2", "**");
        expectGetAllAclsCallWithAnyArray();
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRepo2")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo2")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo2"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        replay(aclStoreServiceMock);
        canRead = service.canRead(testRepo2Path);
        assertTrue(canRead, "User belongs to a group with permissions to the path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test
    public void userWithPermissionsToANonUniqueGroupName() {
        // here we test that a user that belongs to a group which has
        // the same name of a nother user will only get the group permissions
        // and not the user with the same name permissions
        setSimpleUserAuthentication(PERMISSION_ACTOR, securityServiceImplTestHelper.USER_AND_GROUP_SHARED_NAME);

        RepoPath testRepo1Path = InternalRepoPathFactory.create("testRepo1", "**");
        expectGetAllAclsCallWithAnyArray();
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRepo1")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        replay(aclStoreServiceMock);
        boolean canRead = service.canRead(testRepo1Path);
        assertFalse(canRead, "User should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        RepoPath testRepo2Path = InternalRepoPathFactory.create("testRepo2", "**");
        expectGetAllAclsCallWithAnyArray();
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRepo2")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo2")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo2"))
                .andReturn(false).anyTimes();
        replay(aclStoreServiceMock, repositoryServiceMock);
        canRead = service.canRead(testRepo2Path);
        EasyMock.verify(repositoryServiceMock);
        assertTrue(canRead, "User belongs to a group with permissions to the path");
    }

    @Test
    public void hasPermissionPassingUserInfo() {
        SimpleUser user = createNonAdminUser("yossis");
        UserInfo userInfo = user.getDescriptor();
        expect(accessService.isAnonymousAccessEnabled()).andReturn(false).anyTimes();
        RepoPath testRepo1Path = InternalRepoPathFactory.create("testRepo1", "any/path");
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1"))
                .andReturn(false).anyTimes();
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock, repositoryServiceMock);
        boolean canRead = service.canRead(userInfo, testRepo1Path);
        assertTrue(canRead, "User should have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canDeploy = service.canDeploy(userInfo, testRepo1Path);
        assertTrue(canDeploy, "User should have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRepo1")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canDelete = service.canDelete(userInfo, testRepo1Path);
        assertFalse(canDelete, "User should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock, repositoryServiceMock);
        boolean canAdmin = service.canManage(userInfo, testRepo1Path);
        assertTrue(canAdmin, "User should have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        RepoPath testRepo2Path = InternalRepoPathFactory.create("testRepo2", "**");
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRepo2")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo2")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo2"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        canRead = service.canRead(userInfo, testRepo2Path);
        assertFalse(canRead, "User should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        SimpleUser anon = createNonAdminUser(UserInfo.ANONYMOUS);
        UserInfo anonUserInfo = anon.getDescriptor();

        RepoPath testMultiRepo = InternalRepoPathFactory.create("multi1", "**");
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("multi1")).andReturn(cacheRepoMock)
                .anyTimes();
        expectGetAllAclsCallWithAnyArray();
        replay(repositoryServiceMock);

        canRead = service.canRead(anonUserInfo, testMultiRepo);
        EasyMock.verify(repositoryServiceMock);
        assertFalse(canRead, "Anonymous user should have permissions for this path");
    }

    @Test
    public void hasPermissionWithSpecificTarget() {
        SimpleUser user = createNonAdminUser("shay");
        UserInfo userInfo = user.getDescriptor();
        RepoPath testRepo1Path = InternalRepoPathFactory.create("specific-repo", "com", true);

        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("specific-repo")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("specific-repo")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "specific-repo"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canRead = service.canRead(userInfo, testRepo1Path);
        assertTrue(canRead, "User should have read permissions for this path");
        EasyMock.reset(aclStoreServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canDeploy = service.canDeploy(userInfo, testRepo1Path);
        assertTrue(canDeploy, "User should have deploy permissions for this path");
        EasyMock.reset(aclStoreServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canDelete = service.canDelete(userInfo, testRepo1Path);
        assertFalse(canDelete, "User should not have delete permissions for this path");
        EasyMock.reset(aclStoreServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canAdmin = service.canManage(userInfo, testRepo1Path);
        assertFalse(canAdmin, "User should not have admin permissions for this path");
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test
    public void hasPermissionsOnRemoteRepoWhenUsingLocalRepoPermissions() {
        setSimpleUserAuthentication("user");

        expect(repositoryServiceMock.repositoryByKey("testRepo1")).andReturn(createRemoteRepoMock()).anyTimes();
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRepo1-cache")).andReturn(null)
                .anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1-cache"))
                .andReturn(false).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.REMOTE, "testRepo1"))
                .andReturn(true).anyTimes();
        expectRemoteByKeyNullForAnyConsts();

        // cannot read the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        boolean hasPermissionOnRemoteRoot = service.userHasPermissionsOnRepositoryRoot("testRepo1");
        assertTrue(hasPermissionOnRemoteRoot);
        EasyMock.verify(aclStoreServiceMock, repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test
    public void hasPermissionsOnLocalRepoWhenUsingRemoteRepoPermissions() {
        setSimpleUserAuthentication("user");

        setupRepoMocksForRootPermissionTests("testRemote", true, false, true);

        expectGetAllAclsCall();
        replay(aclStoreServiceMock);

        boolean hasPermissionOnRemoteRoot =
                service.userHasPermissionsOnRepositoryRoot("testRemote", Set.of(ArtifactoryPermission.READ), null);
        assertTrue(hasPermissionOnRemoteRoot);

        EasyMock.verify(aclStoreServiceMock, repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test
    public void hasPermissionOnRootActsTheSameToAllMutations() {
        setSimpleUserAuthentication("user");
        setupRepoMocksForRootPermissionTests("testRemote", true, false, true);
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        boolean hasPermissionOnRemoteRoot =
                service.userHasPermissionsOnRepositoryRoot("testRemote");
        assertTrue(hasPermissionOnRemoteRoot);
        hasPermissionOnRemoteRoot =
                service.userHasPermissionsOnRepositoryRoot("testRemote");
        assertTrue(hasPermissionOnRemoteRoot);
        hasPermissionOnRemoteRoot =
                service.userHasPermissionsOnRepositoryRoot("testRemote", Set.of(ArtifactoryPermission.READ), null);
        assertTrue(hasPermissionOnRemoteRoot);
        hasPermissionOnRemoteRoot =
                service.userHasPermissionsOnRepositoryRoot("testRemote", null, null);
        assertTrue(hasPermissionOnRemoteRoot);
        hasPermissionOnRemoteRoot =
                service.userHasPermissionsOnRepositoryRoot("testRemote", Set.of(DEPLOY, READ), null);
        assertTrue(hasPermissionOnRemoteRoot);
        EasyMock.verify(aclStoreServiceMock, repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test
    public void hasPermissionsOnLegacyLocalRepoWithCacheSuffix() {
        setSimpleUserAuthentication("user");

        // Legacy local repo with "-cache" suffix; permissions are defined for "legacyLocal" without suffix
        String legacyLocalRepoKey = "legacyLocal-cache";
        String realRepoName = "legacyLocal";

        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        AclStoreService aclStoreService = mock(AclStoreService.class);

        LocalRepo localRepo = mock(LocalRepo.class);
        when(localRepo.isLocal()).thenReturn(true);
        when(localRepo.isCache()).thenReturn(false);
        when(localRepo.isReal()).thenReturn(true);
        when(repositoryService.repositoryByKey(legacyLocalRepoKey)).thenReturn(localRepo);
        when(repositoryService.localFederatedOrCachedRepositoryByKey(legacyLocalRepoKey)).thenReturn(localRepo);
        when(repositoryService.existsByType(RepoDetailsType.REMOTE, realRepoName)).thenReturn(false);
        when(repositoryService.existsByType(RepoDetailsType.LOCAL, legacyLocalRepoKey)).thenReturn(true);
        when(repositoryService.existsByType(RepoDetailsType.RELEASE_BUNDLE, legacyLocalRepoKey)).thenReturn(false);

        InfoFactory factory = InfoFactoryHolder.get();
        MutableAceInfo userAce = factory.createAce("user", false, ArtifactoryPermission.READ.getMask());
        MutableRepoPermissionTarget permissionTarget =
                factory.createRepoPermissionTarget("legacyLocalPermission", Set.of(realRepoName));
        Set<AceInfo> aces = new HashSet<>(Collections.singletonList(userAce));
        RepoAcl repoAcl = factory.createRepoAcl(permissionTarget, aces, "admin", System.currentTimeMillis());

        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> userRepoMap = Maps.newHashMap();
        userRepoMap.put(realRepoName,
                Sets.newHashSet(new PrincipalPermissionImpl<>(permissionTarget, userAce, "accessIdentifier")));
        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> userResultMap = Maps.newHashMap();
        userResultMap.put("user", userRepoMap);
        AclCache<RepoPermissionTarget> legacyAclCache = new AclCache<>(Maps.newHashMap(), userResultMap);

        when(aclStoreService.getAllRepoAcls()).thenReturn(Collections.singletonList(repoAcl));
        when(aclStoreService.getAclCache()).thenReturn(legacyAclCache);

        service.setRepositoryService(repositoryService);
        service.setAclStoreService(aclStoreService);

        boolean hasPermission = service.userHasPermissionsOnRepositoryRoot(legacyLocalRepoKey);

        assertTrue(hasPermission,
                "User should have permissions on legacy local repo with -cache suffix");
    }

    private void setupRepoMocksForRootPermissionTests(String repoKey, boolean isLocal, boolean isCache,
            boolean isReal) {
        LocalRepo localRepo = createMock(LocalRepo.class);
        expect(localRepo.isLocal()).andReturn(isLocal).anyTimes();
        expect(localRepo.isCache()).andReturn(isCache).anyTimes();
        expect(localRepo.isReal()).andReturn(isReal).anyTimes();
        replay(localRepo);

        expect(repositoryServiceMock.repositoryByKey(repoKey)).andReturn(localRepo).anyTimes();
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey(repoKey)).andReturn(null).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(repoKey)).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, repoKey)).andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
    }

    @Test
    public void hasPermissionForGroupInfo() {
        GroupInfo groupInfo = InfoFactoryHolder.get().createGroup("deployGroup");

        RepoPath testRepo1Path = InternalRepoPathFactory.create("testRepo1", "any/path");
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRepo1")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo1"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canRead = service.canRead(groupInfo, testRepo1Path);
        assertFalse(canRead, "Group should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canDeploy = service.canDeploy(groupInfo, testRepo1Path);
        assertTrue(canDeploy, "Group should have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canDelete = service.canDelete(groupInfo, testRepo1Path);
        assertFalse(canDelete, "Group should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        boolean canAdmin = service.canManage(groupInfo, testRepo1Path);
        assertFalse(canAdmin, "Group should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        RepoPath testRepo2Path = InternalRepoPathFactory.create("testRepo2", "some/path");

        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRepo2")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRepo2")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRepo2"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        canRead = service.canRead(groupInfo, testRepo2Path);
        assertFalse(canRead, "Group should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        GroupInfo anyRepoGroupRead = InfoFactoryHolder.get().createGroup("anyRepoReadersGroup");
        RepoPath somePath = InternalRepoPathFactory.create("blabla", "some/path");

        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("blabla")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("blabla")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "blabla"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        canRead = service.canRead(anyRepoGroupRead, somePath);
        assertTrue(canRead, "Group should have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        canDeploy = service.canDeploy(anyRepoGroupRead, somePath);
        assertFalse(canDeploy, "Group should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);

        GroupInfo multiRepoGroupRead = InfoFactoryHolder.get().createGroup("multiRepoReadersGroup");
        RepoPath multiPath = InternalRepoPathFactory.create("multi1", "some/path");
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("multi1")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("multi1")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "multi1"))
                .andReturn(false).anyTimes();
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("multi2")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("multi2")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "multi2"))
                .andReturn(false).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        canRead = service.canRead(multiRepoGroupRead, multiPath);
        assertTrue(canRead, "Group should have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        RepoPath multiPath2 = InternalRepoPathFactory.create("multi2", "some/path");
        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        canRead = service.canRead(multiRepoGroupRead, multiPath2);
        assertTrue(canRead, "Group should have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock);

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock);
        canDeploy = service.canDeploy(multiRepoGroupRead, multiPath);
        assertFalse(canDeploy, "Group should not have permissions for this path");
        EasyMock.verify(repositoryServiceMock);
    }

    @Test
    public void getAllPermissionTargetsForAdminUser() {
        setAdminAuthentication();
        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> adminRepoToAclMap =
                aclCache.getUserResultMap().get("yossis");
        for (String repoPath : adminRepoToAclMap.keySet()) {
            RepoPath path = RepoPathFactory.create(repoPath);
            assertTrue(service.canManage(path), "Admin should be manager of all repos in his cache map");
        }
        assertTrue(service.canManage(RepoPathFactory.create("multi1")),
                "Admin should be manager of repos not belonging to his cache map");
    }

    @Test
    public void getAllPermissionTargetsForUserWithNoPermission() {
        setSimpleUserAuthentication("noadminpermissionsuser");

        expectAclScan();

        List<RepoPermissionTarget> permissionTargets = service.getRepoPermissionTargets(MANAGE);
        assertEquals(permissionTargets.size(), 0);

        EasyMock.verify(aclStoreServiceMock);
    }

    @Test
    public void getDeployPermissionTargetsForUserWithNoPermission() {
        setSimpleUserAuthentication("user");

        expectAclScan();

        List<RepoPermissionTarget> targets = service.getRepoPermissionTargets(DEPLOY);
        assertEquals(targets.size(), 0);

        EasyMock.verify(aclStoreServiceMock);
    }

    @Test
    public void getDeployPermissionTargetsForUserWithDeployPermission() {
        // yossis should have testRepo1 and testRemote-cache
        setSimpleUserAuthentication("yossis");

        expectAclScan();

        List<RepoPermissionTarget> targets = service.getRepoPermissionTargets(DEPLOY);
        assertEquals(targets.size(), 2, "Expecting two deploy permission");

        EasyMock.verify(aclStoreServiceMock);
    }

    @Test
    public void permissionOnRemoteRoot() {
        setSimpleUserAuthentication("user");

        expect(repositoryServiceMock.repositoryByKey("testRemote")).andReturn(createRemoteRepoMock()).anyTimes();
        expect(repositoryServiceMock.localFederatedOrCachedRepositoryByKey("testRemote-cache")).andReturn(null)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRemote-cache")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey("testRemote")).andReturn(new HttpRepoDescriptor())
                .anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "testRemote-cache"))
                .andReturn(false).anyTimes();
        replay(repositoryServiceMock);

        // cannot read the specified path
        expectGetAllAclsCall();
        replay(aclStoreServiceMock);
        boolean hasPermissionOnRemoteRoot = service.userHasPermissionsOnRepositoryRoot("testRemote");
        assertTrue(hasPermissionOnRemoteRoot, "User should have permissions for this path");

        HashMap<String, Boolean> cache = new HashMap<>();
        hasPermissionOnRemoteRoot =
                service.userHasPermissionsOnRepositoryRoot("testRemote", Set.of(ArtifactoryPermission.values()), cache);
        assertTrue(hasPermissionOnRemoteRoot, "User should have permissions for this path");
        EasyMock.verify(aclStoreServiceMock, repositoryServiceMock);
        EasyMock.reset(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test(dataProvider = "provideGetBuildNameAcls")
    public void testGetBuildNameAcls(String buildName, List<BuildAcl> acls, boolean expectingValues) {
        expect(aclStoreServiceMock.getAllBuildAcls()).andReturn(acls).anyTimes();
        replay(aclStoreServiceMock);
        assertEquals(!service.getBuildPathAcls(buildName, "1", "2015-05-15T11:49:04.804+02:00", BUILDINFO_REPO_KEY)
                .isEmpty(), expectingValues);
    }

    @Test
    public void getUserEffectiveSids() {
        SimpleUser userMock = Mockito.mock(SimpleUser.class);
        Mockito.when(userMock.getUsername()).thenReturn("tester");

        UserInfo userInfoMock = Mockito.mock(UserInfo.class);
        Mockito.when(userMock.getDescriptor()).thenReturn(userInfoMock);
        Set<UserGroupInfo> groups = new HashSet<>();
        for (int i = 1; i <= 1000; i++) {
            groups.add(new UserGroupImpl("grp" + i));
        }
        when(userInfoMock.getGroups()).thenReturn(groups);

        Set<ArtifactorySid> userEffectiveSids = service.getUserEffectiveSids(userMock);
        // verify first sid is the actual user
        ArtifactorySid firstSid = userEffectiveSids.iterator().next();
        assertFalse(firstSid.isGroup());
        assertEquals(firstSid.getPrincipal(), "tester");

        // make sure all groups exists
        assertEquals(groups.size(), userEffectiveSids.stream().filter(ArtifactorySid::isGroup).count());

        // make sure all expected groups existed in the result
        Set<String> returnedGroupNames = userEffectiveSids.stream()
                .filter(sid -> !sid.getPrincipal().equals("tester"))
                .map(ArtifactorySid::getPrincipal)
                .collect(Collectors.toSet());

        Set<String> expectedGroupNames = groups.stream()
                .map(UserGroupInfo::getGroupName)
                .collect(Collectors.toSet());
        assertTrue(returnedGroupNames.containsAll(expectedGroupNames));
    }

    @Test(dataProvider = "scopedActionsAndExpected")
    public void testActionsByPermissionScopedToken(Action permissionScopeAction,
            boolean canDeploy, boolean canRead, boolean canAnnotate, boolean canDelete, boolean canManage) {
        AccessService accessServiceMock = mock(AccessService.class, Answers.RETURNS_DEEP_STUBS);
        LoginResult loginResult = mock(LoginResult.class);
        AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
        when(loginResult.getPrincipal()).thenReturn(accessPrincipal);
        when(accessServiceMock.getAccessClient().cachedAuth().loginByAuthHeader(any(String.class)))
                .thenReturn(loginResult);
        when(accessServiceMock.getAccessClient().cachedAuth().principalCanDo(any(), any(), anyBoolean())).thenAnswer(
                (Answer<Boolean>) invocationOnMock -> {
                    PermissionScope permissionScope = invocationOnMock.getArgument(1);
                    return permissionScope.getAction() == permissionScopeAction;
                });
        service.setAccessService(accessServiceMock);

        authenticateUserWithScopes(false, Lists.newArrayList("artifact:repoKey:" + permissionScopeAction.getCode()));

        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, "repoKey")).andReturn(false)
                .anyTimes();
        replay(repositoryServiceMock);
        expectAclScan();

        RepoPath repoPath = RepoPathFactory.create("repoKey", "path");
        assertEquals(service.canDeploy(repoPath), canDeploy);
        assertEquals(service.canRead(repoPath), canRead);
        assertEquals(service.canAnnotate(repoPath), canAnnotate);
        assertEquals(service.canDelete(repoPath), canDelete);
        assertEquals(service.canManage(repoPath), canManage);
    }

    @DataProvider
    public static Object[][] scopedActionsAndExpected() {
        return new Object[][] {
                {Action.WRITE, true, false, false, false, false},
                {Action.READ, false, true, false, false, false},
                {Action.ANNOTATE, false, false, true, false, false},
                {Action.DELETE, false, false, false, true, false},
                {Action.MANAGE, false, false, false, false, false}
        };
    }

    @Test
    public void userReadPermissionsOnAnyRemote() {
        // user should have a read (only) permission on any remote
        setSimpleUserAuthentication("anyRemoteUser");
        //secureRepo is a cached remote repo
        RepoPath securedPath = InternalRepoPathFactory.create("securedRepo", "blabla");

        expectGetAllAclsCall();
        RemoteRepo<?, ?> remoteRepoMock = createRemoteRepoMock();
        expect(repositoryServiceMock.remoteRepositoryByKey("securedRepo")).andReturn(remoteRepoMock).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, securedPath.getRepoKey()))
                .andReturn(false).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.REMOTE, securedPath.getRepoKey()))
                .andReturn(true).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        replay(aclStoreServiceMock);
        assertTrue(service.canRead(securedPath), "User should have read permissions on any remote path");
        assertFalse(
                service.canDeploy(securedPath) || service.canManage(securedPath) || service.canDelete(securedPath) ||
                        service.canManage(securedPath), "User shouldn't have other permissions on any remote path");
        EasyMock.verify(aclStoreServiceMock);
        EasyMock.reset(aclStoreServiceMock);
    }

    @Test
    public void userDeployPermissionsOnAnyLocal() {
        // user should have deploy permission on any local
        setSimpleUserAuthentication("anyLocalUser");

        RepoPath securedPath = InternalRepoPathFactory.create("securedRepo", "blabla");

        expectGetAllAclsCall();
        expect(repositoryServiceMock.localOrFederatedRepositoryByKey("securedRepo")).andReturn(localRepoMock)
                .anyTimes();
        expect(repositoryServiceMock.remoteRepositoryByKey("securedRepo")).andReturn(null).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(securedPath.getRepoKey())).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, securedPath.getRepoKey()))
                .andReturn(false).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.LOCAL, securedPath.getRepoKey()))
                .andReturn(true).anyTimes();
        expectRemoteByKeyNullForAnyConsts();
        replay(aclStoreServiceMock);

        assertTrue(service.canDeploy(securedPath), "User should have deploy permissions on any local path");
        assertFalse(
                service.canRead(securedPath) || service.canManage(securedPath) || service.canDelete(securedPath) ||
                        service.canManage(securedPath), "User shouldn't have other permissions on any remote path");
        EasyMock.verify(aclStoreServiceMock);
        EasyMock.reset(aclStoreServiceMock);
    }

    @Test
    public void testPermissionCheckOnAclFitPermission() {
        SimpleUser nonAdminUser = createNonAdminUser("user1");

        AceImpl aceU1 = new AceImpl(nonAdminUser.getUsername(), false, DEPLOY.getMask());

        Set<AceInfo> aces = Sets.newHashSet(aceU1);

        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of("repo-a"),
                Lists.newArrayList("**"),
                Lists.newArrayList());

        PrincipalPermission principalPermission = new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        assertTrue(service.permissionCheckOnAcl(Collections.singletonList(principalPermission),
                new RepoPathImpl("repo-a", null),
                Collections.singletonList("repo-a"), DEPLOY, nonAdminUser.getUsername()));
    }

    @Test
    public void testPermissionCheckOnAclDifferentPermission() {
        SimpleUser nonAdminUser = createNonAdminUser("user1");

        AceImpl aceU1 = new AceImpl(nonAdminUser.getUsername(), false, DEPLOY.getMask());

        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of("repo-a"),
                Lists.newArrayList("**"),
                Lists.newArrayList());

        PrincipalPermission principalPermission = new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        assertFalse(service.permissionCheckOnAcl(Collections.singletonList(principalPermission),
                new RepoPathImpl("repo-a", null),
                Collections.singletonList("repo-a"), READ, nonAdminUser.getUsername()));
    }

    @Test
    public void testPermissionCheckOnAclOnGroupFailed() {
        SimpleUser nonAdminUser = createNonAdminUser("user1", "g1");

        AceImpl aceU1 = new AceImpl(nonAdminUser.getUsername(), false, DEPLOY.getMask());

        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of("repo-a"),
                Lists.newArrayList("**"),
                Lists.newArrayList());

        PrincipalPermission principalPermission = new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        assertFalse(service.permissionCheckOnAcl(Collections.singletonList(principalPermission),
                new RepoPathImpl("repo-a", null),
                Collections.singletonList("repo-a"), DEPLOY, "g1"));
    }

    @Test
    public void testPermissionCheckOnAclOnGroupSuccess() {
        SimpleUser nonAdminUser = createNonAdminUser("user1", "g1");

        AceImpl aceU1 = new AceImpl(nonAdminUser.getUsername(), false, DEPLOY.getMask());
        AceImpl aceU2 = new AceImpl("g1", true, DEPLOY.getMask());


        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of("repo-a"),
                Lists.newArrayList("**"),
                Lists.newArrayList());

        PrincipalPermission principalPermission1 = new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        PrincipalPermission principalPermission2 = new PrincipalPermissionImpl<>(pt, aceU2, "accessIdentifier");
        assertTrue(service.permissionCheckOnAcl(Arrays.asList(principalPermission1, principalPermission2),
                new RepoPathImpl("repo-a", null),
                Collections.singletonList("repo-a"), DEPLOY, "g1"));
    }

    @Test
    public void testPermissionCheckOnAclOnGroupSuccess2() {
        SimpleUser nonAdminUser = createNonAdminUser("user1", "g1");

        AceImpl aceU1 = new AceImpl(nonAdminUser.getUsername(), false, DEPLOY.getMask());
        AceImpl aceU2 = new AceImpl("g1", true, DEPLOY.getMask());


        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of("repo-a"),
                Lists.newArrayList("**"),
                Lists.newArrayList());

        PrincipalPermission principalPermission1 = new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        PrincipalPermission principalPermission2 = new PrincipalPermissionImpl<>(pt, aceU2, "accessIdentifier");
        assertTrue(service.permissionCheckOnAcl(Arrays.asList(principalPermission1, principalPermission2),
                new RepoPathImpl("repo-a", null),
                Collections.singletonList("repo-a"), DEPLOY, "g1"));
    }

    @Test
    public void testPermissionCheckOnAclAnySuccess() {
        SimpleUser nonAdminUser = createNonAdminUser("user1", "g1");

        AceImpl aceU1 = new AceImpl(nonAdminUser.getUsername(), false, DEPLOY.getMask());
        AceImpl aceU2 = new AceImpl("g1", true, DEPLOY.getMask());


        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of(ANY_REPO),
                Lists.newArrayList("**"),
                Lists.newArrayList());

        PrincipalPermission principalPermission1 = new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        PrincipalPermission principalPermission2 = new PrincipalPermissionImpl<>(pt, aceU2, "accessIdentifier");
        assertTrue(service.permissionCheckOnAcl(Arrays.asList(principalPermission1, principalPermission2),
                new RepoPathImpl("repo-a", null),
                Collections.singletonList("repo-a"), DEPLOY, "g1"));
    }

    @Test
    public void testPermissionCheckOnAclPatternSuccess() {
        SimpleUser nonAdminUser = createNonAdminUser("user1", "g1");

        AceImpl aceU1 = new AceImpl(nonAdminUser.getUsername(), false, DEPLOY.getMask());
        AceImpl aceU2 = new AceImpl("g1", true, DEPLOY.getMask());


        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of(ANY_REPO),
                Lists.newArrayList("**dod**"),
                Lists.newArrayList());

        PrincipalPermission principalPermission1 = new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        PrincipalPermission principalPermission2 = new PrincipalPermissionImpl<>(pt, aceU2, "accessIdentifier");
        assertTrue(service.permissionCheckOnAcl(Arrays.asList(principalPermission1, principalPermission2),
                new RepoPathImpl("repo-a", "dodge"),
                Collections.singletonList("repo-a"), DEPLOY, "g1"));
    }

    @Test
    public void testPermissionCheckOnAclPatternFailed() {
        SimpleUser nonAdminUser = createNonAdminUser("user1", "g1");

        AceImpl aceU1 = new AceImpl(nonAdminUser.getUsername(), false, DEPLOY.getMask());
        AceImpl aceU2 = new AceImpl("g1", true, DEPLOY.getMask());


        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of(ANY_REPO),
                Lists.newArrayList("**do/e**"),
                Lists.newArrayList());

        PrincipalPermission principalPermission1 = new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        PrincipalPermission principalPermission2 = new PrincipalPermissionImpl<>(pt, aceU2, "accessIdentifier");
        assertFalse(service.permissionCheckOnAcl(Arrays.asList(principalPermission1, principalPermission2),
                new RepoPathImpl("repo-a", "dodge"),
                Collections.singletonList("repo-a"), DEPLOY, "g1"));
    }

    @Test
    public void noPermissionsProjectAdminOnPipeInfoRepo() {
        setProjectAdminAuthentication(true, false);
        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(REPO.getRepoKey())).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByType(RepoDetailsType.RELEASE_BUNDLE, REPO.getRepoKey()))
                .andReturn(false).anyTimes();
        expect(repositoryServiceMock.existsByPackageType(PipeInfo, REPO.getRepoKey()))
                .andReturn(true).anyTimes();
        replay(aclStoreServiceMock, repositoryServiceMock);
        assertFalse(service.canManage(REPO));
        assertFalse(service.canDelete(REPO));
        assertFalse(service.canRead(REPO));
        assertFalse(service.canAnnotate(REPO));
        assertFalse(service.canDeploy(REPO));
        reset(repositoryServiceMock);
    }

    @Test
    public void adminOnPipeInfoRepo() {
        reset(repositoryServiceMock);
        setAdminAuthentication();
        RepoPath repoPath = RepoPathFactory.create("pipe-info");
        assertTrue(service.canManage(repoPath));
        assertTrue(service.canDelete(repoPath));
        assertTrue(service.canDeploy(repoPath));
        assertTrue(service.canRead(repoPath));
    }

    @Test
    public void adminOnInTransitRepo() {
        reset(repositoryServiceMock);
        setAdminAuthentication();
        RepoPath repoPath = RepoPathFactory.create(IN_TRANSIT_REPO_KEY);
        assertTrue(service.canManage(repoPath));
        assertTrue(service.canDelete(repoPath));
        assertTrue(service.canDeploy(repoPath));
        assertTrue(service.canRead(repoPath));
    }

    @Test
    public void systemOnFullSyncRepo() {
        reset(repositoryServiceMock);
        when(securityServiceMock.currentUsername()).thenReturn(USER_SYSTEM);
        setAdminAuthentication();
        RepoPath repoPath = RepoPathFactory.create(FULL_SYNC_REPO_KEY);
        assertTrue(service.canManage(repoPath));
        assertTrue(service.canDelete(repoPath));
        assertTrue(service.canDeploy(repoPath));
        assertTrue(service.canRead(repoPath));
    }

    @Test
    public void regularUserOnPipeInfoRepoNotAllowed() {
        reset(repositoryServiceMock);
        setSimpleUserAuthentication("regularUserOnPipeInfoRepoNotAllowed");
        RepoPath repoPath = RepoPathFactory.create("pipe-info");
        expect(repositoryServiceMock.existsByPackageType(PipeInfo, repoPath.getRepoKey()))
                .andReturn(true).anyTimes();
        replay(repositoryServiceMock);

        assertFalse(service.canManage(repoPath));
        assertFalse(service.canDelete(repoPath));
        assertFalse(service.canRead(repoPath));
        assertFalse(service.canAnnotate(repoPath));
        assertFalse(service.canDeploy(repoPath));
    }

    @Test
    public void regularUserOnObservabilityRepoNotAllowed() {
        reset(repositoryServiceMock);
        when(securityServiceMock.currentUsername()).thenReturn("user");
        setSimpleUserAuthentication("user");
        RepoPath repoPath = RepoPathFactory.create(LOG_SHIPPING_REPO_KEY);
        RepoPath repoPathConsumption = RepoPathFactory.create(USAGE_REPO_KEY);
        assertFalse(service.canManage(repoPath));
        assertFalse(service.canDelete(repoPath));
        assertFalse(service.canRead(repoPath));
        assertFalse(service.canAnnotate(repoPath));
        assertFalse(service.canDeploy(repoPath));
        assertFalse(service.canManage(repoPathConsumption));
        assertFalse(service.canDelete(repoPathConsumption));
        assertFalse(service.canRead(repoPathConsumption));
        assertFalse(service.canAnnotate(repoPathConsumption));
        assertFalse(service.canDeploy(repoPathConsumption));
    }

    @Test
    public void adminOnObservabilityRepo() {
        reset(repositoryServiceMock);
        when(securityServiceMock.isAdmin()).thenReturn(true);
        when(securityServiceMock.currentUsername()).thenReturn(USER_SYSTEM);
        setAdminAuthentication(USER_SYSTEM);
        RepoPath repoPath = RepoPathFactory.create(LOG_SHIPPING_REPO_KEY);
        RepoPath repoPathConsumption = RepoPathFactory.create(USAGE_REPO_KEY);
        assertTrue(service.canManage(repoPath));
        assertTrue(service.canDelete(repoPath));
        assertTrue(service.canDeploy(repoPath));
        assertTrue(service.canRead(repoPath));
        assertTrue(service.canManage(repoPathConsumption));
        assertTrue(service.canDelete(repoPathConsumption));
        assertTrue(service.canDeploy(repoPathConsumption));
        assertTrue(service.canRead(repoPathConsumption));
    }

    @Test
    public void regularUserOnPolicyReportRepoNotAllowed() {
        reset(repositoryServiceMock);
        setSimpleUserAuthentication("user");
        RepoPath repoPath = RepoPathFactory.create(POLICY_REPORTS_REPO_KEY);
        assertFalse(service.canManage(repoPath));
        assertFalse(service.canDelete(repoPath));
        assertFalse(service.canRead(repoPath));
        assertFalse(service.canAnnotate(repoPath));
        assertFalse(service.canDeploy(repoPath));
    }

    @Test
    public void adminOnPolicyReportRepo() {
        reset(repositoryServiceMock);
        setAdminAuthentication();
        RepoPath repoPath = RepoPathFactory.create(POLICY_REPORTS_REPO_KEY);
        assertTrue(service.canManage(repoPath));
        assertTrue(service.canDelete(repoPath));
        assertTrue(service.canDeploy(repoPath));
        assertTrue(service.canRead(repoPath));
    }

    @Test
    public void testHasPermissionByUser() {
        // Create mocks.
        RepoAcl generatedInternalRepoAcl =
                createRepoAcl(INTERNAL_PERMISSION_PREFIX + "repo_perm_target", 1, false,
                        PERMISSION_ACTOR);
        BuildAcl generatedInternalBuildAcl =
                createBuildAcl(INTERNAL_PERMISSION_PREFIX + "build_perm_target", "bu/**", 4);
        RepoAcl generatedInternalDefaultRepoAcl =
                createRepoAcl(INTERNAL_PERMISSION_PREFIX + ProjectsService.DEFAULT_PROJECT +
                        "repo_perm_target", 16, false, PERMISSION_ACTOR);
        BuildAcl generatedInternalDefaultBuildAcl =
                createBuildAcl(INTERNAL_PERMISSION_PREFIX + ProjectsService.DEFAULT_PROJECT +
                        "build_perm_target", "bu/**", 32);
        RepoAcl manuallyCreatedRepoAcl = createRepoAcl("not_internal_repo_perm_target", 2, false, PERMISSION_ACTOR);
        BuildAcl manuallyCreatedBuildAcl = createBuildAcl("not_internal_build_perm_target", "bu/**", 64);

        AclCache<RepoPermissionTarget> aclCache = createAclCache(
                Set.of(generatedInternalRepoAcl, generatedInternalBuildAcl, generatedInternalDefaultRepoAcl,
                        generatedInternalDefaultBuildAcl, manuallyCreatedRepoAcl, manuallyCreatedBuildAcl));
        AclCache<RepoPermissionTarget> buildAclCache = createAclCache(
                Set.of(generatedInternalBuildAcl, manuallyCreatedBuildAcl, generatedInternalDefaultBuildAcl));
        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
        expect(aclStoreServiceMock.getBuildsAclCache()).andReturn(buildAclCache).anyTimes();

        expect(aclStoreServiceMock.getAllRepoAcls())
                .andReturn(List.of(generatedInternalRepoAcl, manuallyCreatedRepoAcl, generatedInternalDefaultRepoAcl))
                .anyTimes();
        expect(aclStoreServiceMock.getAllBuildAcls()).andReturn(
                        List.of(generatedInternalBuildAcl, manuallyCreatedBuildAcl, generatedInternalDefaultBuildAcl))
                .anyTimes();

        doReturn(Collections.emptyList()).when(service).getAllReleaseBundleAclsByLicense();
        replay(aclStoreServiceMock);

        // Test simple user.
        setSimpleUserAuthentication(PERMISSION_ACTOR);
        assertHasPermission();

        // Test project-admin user.
        setProjectAdminAuthentication(PERMISSION_ACTOR, true, false);


        assertHasPermission();

        // Test admin.
        setAdminAuthentication(PERMISSION_ACTOR);
        // The result should always be true.
        Arrays.stream(ArtifactoryPermission.values()).forEach(permission -> {
            assertTrue(service.hasPermission(permission, true));
            assertTrue(service.hasPermission(permission, false));
        });
    }

    @Test
    public void testHasPermissionByGroup() {
        // Create mocks.
        RepoAcl generatedInternalRepoAcl =
                createRepoAcl(INTERNAL_PERMISSION_PREFIX + "repo_perm_target", 1, false,
                        PERMISSION_ACTOR);
        BuildAcl generatedInternalBuildAcl =
                createBuildAcl(INTERNAL_PERMISSION_PREFIX + "build_perm_target", "bu/**", 4);
        RepoAcl generatedInternalDefaultRepoAcl =
                createRepoAcl(INTERNAL_PERMISSION_PREFIX + ProjectsService.DEFAULT_PROJECT +
                        "repo_perm_target", 16, false, PERMISSION_ACTOR);
        BuildAcl generatedInternalDefaultBuildAcl =
                createBuildAcl(INTERNAL_PERMISSION_PREFIX + ProjectsService.DEFAULT_PROJECT +
                        "build_perm_target", "bu/**", 32);
        RepoAcl manuallyCreatedRepoAcl = createRepoAcl("not_internal_repo_perm_target", 2, false, PERMISSION_ACTOR);
        BuildAcl manuallyCreatedBuildAcl = createBuildAcl("not_internal_build_perm_target", "bu/**", 64);

        String group = "group1";

        AclCache<RepoPermissionTarget> aclCache = createAclCache(
                Set.of(generatedInternalRepoAcl, generatedInternalBuildAcl, generatedInternalDefaultRepoAcl,
                        generatedInternalDefaultBuildAcl, manuallyCreatedRepoAcl, manuallyCreatedBuildAcl), group);
        AclCache<RepoPermissionTarget> buildAclCache = createAclCache(
                Set.of(generatedInternalBuildAcl, generatedInternalDefaultBuildAcl, manuallyCreatedBuildAcl), group);
        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
        expect(aclStoreServiceMock.getBuildsAclCache()).andReturn(buildAclCache).anyTimes();

        expect(aclStoreServiceMock.getAllRepoAcls())
                .andReturn(List.of(generatedInternalRepoAcl, manuallyCreatedRepoAcl, generatedInternalDefaultRepoAcl))
                .anyTimes();
        expect(aclStoreServiceMock.getAllBuildAcls()).andReturn(
                        List.of(generatedInternalBuildAcl, manuallyCreatedBuildAcl, generatedInternalDefaultBuildAcl))
                .anyTimes();

        doReturn(Collections.emptyList()).when(service).getAllReleaseBundleAclsByLicense();
        replay(aclStoreServiceMock);

        // Test simple user.
        setSimpleUserAuthentication(PERMISSION_ACTOR, group);
        assertHasPermission();

        // Test project-admin user.
        setProjectAdminAuthentication(PERMISSION_ACTOR, true, false, group);
        assertHasPermission();

        // Test admin.
        setAdminAuthentication(PERMISSION_ACTOR);
        // The result should always be true.
        Arrays.stream(ArtifactoryPermission.values()).forEach(permission -> {
            assertTrue(service.hasPermission(permission, true));
            assertTrue(service.hasPermission(permission, false));
        });
    }

    @Test
    public void testGetRepoPermissionTargetsOnUserCacheAdminReturnsAll() {
        setAdminAuthentication();

        RepoAcl generatedInternalRepoAcl1 =
                createRepoAcl("repo_perm_target1", DEPLOY.getMask(), false, PERMISSION_ACTOR);
        RepoAcl generatedInternalRepoAcl2 =
                createRepoAcl("repo_perm_target2", DEPLOY.getMask(), false, PERMISSION_ACTOR);
        RepoAcl generatedInternalRepoAcl3 =
                createRepoAcl("repo_perm_target3", DELETE.getMask(), false, PERMISSION_ACTOR);


        expect(aclStoreServiceMock.getAllRepoAcls()).andReturn(
                List.of(generatedInternalRepoAcl1, generatedInternalRepoAcl2, generatedInternalRepoAcl3)).anyTimes();
        replay(aclStoreServiceMock);

        List<RepoPermissionTarget> repoPermissionTargets = service.getRepoPermissionTargets(DEPLOY);
        // admin should get all repo permissions, ignoring the requested permission
        Assert.assertEquals(repoPermissionTargets.size(), 3);
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target1")));
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target2")));
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target3")));
    }

    @Test
    public void testGetRepoPermissionTargetsOnGroupCacheAdminReturnsAll() {
        setAdminAuthentication();

        RepoAcl generatedInternalRepoAcl1 =
                createRepoAcl("repo_perm_target1", DEPLOY.getMask(), false, PERMISSION_ACTOR);
        RepoAcl generatedInternalRepoAcl2 =
                createRepoAcl("repo_perm_target2", DEPLOY.getMask(), false, PERMISSION_ACTOR);
        RepoAcl generatedInternalRepoAcl3 =
                createRepoAcl("repo_perm_target3", DELETE.getMask(), false, PERMISSION_ACTOR);

        expect(aclStoreServiceMock.getAllRepoAcls()).andReturn(
                List.of(generatedInternalRepoAcl1, generatedInternalRepoAcl2, generatedInternalRepoAcl3)).anyTimes();
        replay(aclStoreServiceMock);

        List<RepoPermissionTarget> repoPermissionTargets =
                service.getRepoPermissionTargets(DEPLOY);
        // admin should get all repo permissions, ignoring the requested permission
        Assert.assertEquals(repoPermissionTargets.size(), 3);
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target1")));
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target2")));
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target3")));
    }

    @Test
    public void testGetRepoPermissionTargetsOnUserCache() {
        setSimpleUserAuthentication(PERMISSION_ACTOR);

        RepoAcl generatedInternalRepoAcl1 =
                createRepoAcl("repo_perm_target1", DEPLOY.getMask(), false, PERMISSION_ACTOR);
        RepoAcl generatedInternalRepoAcl2 =
                createRepoAcl("repo_perm_target2", DEPLOY.getMask(), false, PERMISSION_ACTOR);
        RepoAcl generatedInternalRepoAcl3 =
                createRepoAcl("repo_perm_target3", DELETE.getMask(), false, PERMISSION_ACTOR);

        AclCache<RepoPermissionTarget> aclCache = createUserAclCache(
                Set.of(generatedInternalRepoAcl1, generatedInternalRepoAcl2, generatedInternalRepoAcl3));

        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
        replay(aclStoreServiceMock);

        List<RepoPermissionTarget> repoPermissionTargets =
                service.getRepoPermissionTargets(DEPLOY);
        Assert.assertEquals(repoPermissionTargets.size(), 2);
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target1")));
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target2")));
    }

    @Test
    public void testGetRepoPermissionTargetsOnGroupCache() {
        setSimpleUserAuthentication(PERMISSION_ACTOR, "group1");

        RepoAcl generatedInternalRepoAcl1 =
                createRepoAcl("repo_perm_target1", DEPLOY.getMask(), true, "group1");
        RepoAcl generatedInternalRepoAcl2 =
                createRepoAcl("repo_perm_target2", DEPLOY.getMask(), false, "group1");
        RepoAcl generatedInternalRepoAcl3 =
                createRepoAcl("repo_perm_target3", DELETE.getMask(), true, "group1");

        AclCache<RepoPermissionTarget> aclCache = createGroupAclCache(
                Set.of(generatedInternalRepoAcl1, generatedInternalRepoAcl2, generatedInternalRepoAcl3), "group1");

        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
        replay(aclStoreServiceMock);

        List<RepoPermissionTarget> repoPermissionTargets =
                service.getRepoPermissionTargets(DEPLOY);
        Assert.assertEquals(repoPermissionTargets.size(), 1);
        Assert.assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_perm_target1")));
    }

    @Test
    public void testGetRepoPermissionTargetsNoDuplicates() {
        setSimpleUserAuthentication(PERMISSION_ACTOR, "group1");

        RepoAcl useRepoAcl = createRepoAcl("repo_pt", DEPLOY.getMask(), false, PERMISSION_ACTOR);
        var userAclCache = createUserAclCache(Set.of(useRepoAcl));
        RepoAcl groupRepoAcl = createRepoAcl("repo_pt", DEPLOY.getMask(), true, "group1");
        var groupAclCache = createGroupAclCache(Set.of(groupRepoAcl), "group1");
        var aclCache = new AclCache<>(groupAclCache.getGroupResultMap(), userAclCache.getUserResultMap());

        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
        replay(aclStoreServiceMock);

        List<RepoPermissionTarget> repoPermissionTargets = service.getRepoPermissionTargets(DEPLOY);
        assertEquals(repoPermissionTargets.size(), 1);
        assertTrue(repoPermissionTargets.stream().anyMatch(p -> p.getName().equals("repo_pt")));
    }

    private void assertHasPermission() {
        // Test Repo permissions.
        // READ is internal-only.
        assertFalse(service.hasPermission(READ, true));
        assertTrue(service.hasPermission(READ, false));
        // MANAGE is for internal for default project, so should not be skipped.
        assertTrue(service.hasPermission(MANAGE, true));
        assertTrue(service.hasPermission(MANAGE, false));
        // DEPLOY is a user-created permission, so should not be skipped.
        assertTrue(service.hasPermission(DEPLOY, true));
        assertTrue(service.hasPermission(DEPLOY, false));
        // Test Build permissions.
        // ANNOTATE is internal-only.
        assertFalse(service.hasPermission(ANNOTATE, true));
        assertTrue(service.hasPermission(ANNOTATE, false));
        // MANAGED_XRAY_META is for internal for default project, so should not be skipped.
        assertTrue(service.hasPermission(MANAGE, true));
        assertTrue(service.hasPermission(MANAGE, false));
        // DISTRIBUTE is a user-created permission, so should not be skipped.
        assertTrue(service.hasPermission(DEPLOY, true));
        assertTrue(service.hasPermission(DEPLOY, false));
    }

    @Test
    public void testPermissionsForTrashcan() {
        // Prepare mocks.
        expectAclScan();
        expectIsPipeInfoRepositoryCheck();

        // User "shay" has read, deploy, annotate, delete permissions to testRepo1 as part of group permissions.
        setSimpleUserAuthentication("shay");

        // testRepo1 is one of the repos that a "shay" has a "deploy" permission for
        RepoPath trashPath = RepoPathFactory.create(TRASH_KEY + "/testRepo1/path");
        assertTrue(service.canRead(trashPath));
        assertTrue(service.canAnnotate(trashPath));
        assertTrue(service.canDeploy(trashPath));
        assertTrue(service.canDelete(trashPath));
        assertFalse(service.canManage(trashPath));

        setSimpleUserAuthentication("yossis"); // has manage permission
        assertTrue(service.canManage(trashPath));

        // Test access to the root trashcan path. Only read allowed for non-admins
        setSimpleUserAuthentication("shay");
        RepoPath trashRootPath = RepoPathFactory.create(TRASH_KEY);
        assertTrue(service.canRead(trashRootPath));
        assertFalse(service.canAnnotate(trashRootPath));
        assertFalse(service.canDeploy(trashRootPath));
        assertFalse(service.canDelete(trashRootPath));
        assertFalse(service.canManage(trashRootPath));

        // Test that admin has all the permissions
        setAdminAuthentication();
        assertTrue(service.canRead(trashPath));
        assertTrue(service.canAnnotate(trashPath));
        assertTrue(service.canDeploy(trashPath));
        assertTrue(service.canDelete(trashPath));
        assertTrue(service.canManage(trashPath));
        assertTrue(service.canRead(trashRootPath));
        assertTrue(service.canAnnotate(trashRootPath));
        assertTrue(service.canDeploy(trashRootPath));
        assertTrue(service.canDelete(trashRootPath));
        assertTrue(service.canManage(trashRootPath));
    }

    @Test
    public void testCanAccessTrashcan() {
        // Prepare mocks.
        expectAclScan();
        expectIsPipeInfoRepositoryCheck();

        // Simple user doesn't have deploy/manage permissions to any of the repos.
        setSimpleUserAuthentication(PERMISSION_ACTOR);
        assertFalse(service.canAccessTrashcan());

        // User "shay" has read, deploy, annotate, delete permissions to testRepo1 as part of group permissions.
        setSimpleUserAuthentication("shay");
        assertTrue(service.canAccessTrashcan());

        // User "manageOnlyUser" has manage permission for some repo. So also should be able to access trashcan.
        setSimpleUserAuthentication("manageOnlyUser");
        assertTrue(service.canAccessTrashcan());

        // Admin should be able to access trashcan.
        setAdminAuthentication();
        when(securityServiceMock.isAdmin()).thenReturn(true);
        assertTrue(service.canAccessTrashcan());
    }

    @Test
    public void testCanRestoreFromTrashcan() {
        RepoPath trashPath = RepoPathFactory.create(TRASH_KEY + "/testRepo1/path");
        RepoPath restrictedTrashPath =
                RepoPathFactory.create(TRASH_KEY + "/someRepoThatNoOneHasPermissionsTo/path");

        // Prepare mocks.
        expectAclScan();
        expectIsPipeInfoRepositoryCheck();

        // Simple user doesn't have deploy/manage permissions to testRepo1.
        setSimpleUserAuthentication(PERMISSION_ACTOR);
        assertFalse(service.canRestoreFromTrashcan(trashPath));
        assertFalse(service.canRestoreFromTrashcan(restrictedTrashPath));

        // User "shay" has read, deploy, annotate, delete permissions to testRepo1 as part of group permissions.
        setSimpleUserAuthentication("shay");
        assertTrue(service.canRestoreFromTrashcan(trashPath));
        assertFalse(service.canRestoreFromTrashcan(restrictedTrashPath));

        // User "manageOnlyUser" has manage permission for testRepo1. So also should be able to restore.
        setSimpleUserAuthentication("manageOnlyUser");
        assertTrue(service.canRestoreFromTrashcan(trashPath));
        assertFalse(service.canRestoreFromTrashcan(restrictedTrashPath));

        // Admin should be able to restore anything from trashcan.
        setAdminAuthentication();
        assertTrue(service.canRestoreFromTrashcan(trashPath));
        assertTrue(service.canRestoreFromTrashcan(restrictedTrashPath)); // true
    }

    private void expectAclScan() {
        expect(aclStoreServiceMock.getAllRepoAcls()).andReturn(testAcls).anyTimes();
        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
        replay(aclStoreServiceMock);
    }

    private void expectIsPipeInfoRepositoryCheck() {
        expect(repositoryServiceMock.existsByPackageType(PipeInfo, TRASH_KEY))
                .andReturn(false).anyTimes();
        replay(repositoryServiceMock);
    }

    private void expectGetAllAclsCall() {
        expect(aclStoreServiceMock.getAllRepoAcls()).andReturn(testAcls).anyTimes();
        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
    }

    private void expectGetBuildsAclCacheCall() {
        reset(repositoryServiceMock, aclStoreServiceMock);
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(BUILDINFO_REPO_KEY)).andReturn(null).anyTimes();
        expect(repositoryServiceMock.existsByPackageType(PipeInfo, BUILDINFO_REPO_KEY))
                .andReturn(false).anyTimes();
        expect(aclStoreServiceMock.getBuildsAclCache()).andReturn(emptyAclCache).anyTimes();
        replay(aclStoreServiceMock, repositoryServiceMock);
    }

    @Test
    public void testHasPermissionForReleaseBundleLicenseAcl() {
        ReleaseBundleAcl generatedInternalRepoAcl =
                createReleaseBundleAcl(INTERNAL_PERMISSION_PREFIX + "repo_perm_target", 1);
        ReleaseBundleAcl generatedInternalDefaultRepoAcl =
                createReleaseBundleAcl(INTERNAL_PERMISSION_PREFIX + ProjectsService.DEFAULT_PROJECT +
                        "repo_perm_target", 16);
        ReleaseBundleAcl manuallyCreatedRepoAcl = createReleaseBundleAcl("not_internal_repo_perm_target", 2);


        AclCache<RepoPermissionTarget> aclCache = createAclCache(
                Set.of(generatedInternalRepoAcl, generatedInternalDefaultRepoAcl, manuallyCreatedRepoAcl));
        AclCache<RepoPermissionTarget> buildAclCache = createAclCache(Set.of());
        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache).anyTimes();
        expect(aclStoreServiceMock.getBuildsAclCache()).andReturn(buildAclCache).anyTimes();
        expect(aclStoreServiceMock.getAllRepoAcls()).andReturn(Collections.emptyList()).anyTimes();
        expect(aclStoreServiceMock.getAllBuildAcls()).andReturn(Collections.emptyList()).anyTimes();
        doReturn(List.of(generatedInternalRepoAcl, generatedInternalDefaultRepoAcl, manuallyCreatedRepoAcl))
                .when(service).getAllReleaseBundleAclsByLicense();
        replay(aclStoreServiceMock);

        setSimpleUserAuthentication(PERMISSION_ACTOR);

        // READ is internal-only.
        assertFalse(service.hasPermission(READ, true));
        assertTrue(service.hasPermission(READ, false));
        // MANAGE is for internal for default project, so should not be skipped.
        assertTrue(service.hasPermission(MANAGE, true));
        assertTrue(service.hasPermission(MANAGE, false));
        // DEPLOY is a user-created permission, so should not be skipped.
        assertTrue(service.hasPermission(DEPLOY, true));
        assertTrue(service.hasPermission(DEPLOY, false));
    }

    @Test
    public void testIsNotInternalProjectSpecificAcl() {
        assertTrue(service.isNotInternalProjectSpecificAcl(createRepoAcl(null, 0, false, PERMISSION_ACTOR)));
        assertTrue(service.isNotInternalProjectSpecificAcl(createRepoAcl("", 0, false, PERMISSION_ACTOR)));
        assertTrue(service.isNotInternalProjectSpecificAcl(createRepoAcl("test", 0, false, PERMISSION_ACTOR)));
        assertFalse(
                service.isNotInternalProjectSpecificAcl(createRepoAcl(INTERNAL_PERMISSION_PREFIX, 0,
                        false, PERMISSION_ACTOR)));
        assertFalse(service.isNotInternalProjectSpecificAcl(
                createRepoAcl(INTERNAL_PERMISSION_PREFIX + "test", 0, false, PERMISSION_ACTOR)));
        assertTrue(service.isNotInternalProjectSpecificAcl(
                createRepoAcl(INTERNAL_PERMISSION_PREFIX + ProjectsService.DEFAULT_PROJECT, 0, false,
                        PERMISSION_ACTOR)));
        assertTrue(service.isNotInternalProjectSpecificAcl(
                createRepoAcl(INTERNAL_PERMISSION_PREFIX + ProjectsService.DEFAULT_PROJECT + "test",
                        0, false, PERMISSION_ACTOR)));
    }

    @Test
    public void testGetUserPermissionAndItsGroupsExcludingInternalOnce() {
        InfoFactory factory = InfoFactoryHolder.get();
        RepoAcl generatedRepoAcl = factory.createRepoAcl(factory.createRepoPermissionTarget(),
                Set.of(factory.createAce(PERMISSION_ACTOR, false, 0)), null, 0,
                INTERNAL_PERMISSION_PREFIX + "perm_target");
        RepoAcl manuallyCreatedRepoAcl = factory.createRepoAcl(factory.createRepoPermissionTarget(),
                Set.of(factory.createAce(PERMISSION_ACTOR, false, 0)), null, 0, "not_internal_repo_perm_target");

        expect(aclStoreServiceMock.getAllRepoAcls()).andReturn(List.of(generatedRepoAcl, manuallyCreatedRepoAcl));
        expect(userGroupStoreService.findUser(PERMISSION_ACTOR)).andReturn(new UserImpl(PERMISSION_ACTOR));
        replay(aclStoreServiceMock, userGroupStoreService);
        var permissionTargetAceInfoMap =
                service.getUserPermissionAndItsGroups(PERMISSION_ACTOR, ArtifactoryResourceType.REPO);
        assertEquals(permissionTargetAceInfoMap.size(), 1);
        assertNotNull(permissionTargetAceInfoMap.get(manuallyCreatedRepoAcl.getPermissionTarget()));

        reset(userGroupStoreService);
        expect(userGroupStoreService.findUser(PERMISSION_ACTOR)).andReturn(null);
        replay(userGroupStoreService);
        permissionTargetAceInfoMap =
                service.getUserPermissionAndItsGroups(PERMISSION_ACTOR, ArtifactoryResourceType.REPO);
        assertEquals(permissionTargetAceInfoMap.size(), 0);
    }

    @Test
    public void testGetGroupsPermissionsExcludingInternalOnce() {
        InfoFactory factory = InfoFactoryHolder.get();

        RepoAcl generatedRepoAcl = factory.createRepoAcl(factory.createRepoPermissionTarget(),
                Set.of(factory.createAce(PERMISSION_ACTOR, true, 0)),
                null, 0, INTERNAL_PERMISSION_PREFIX + "perm_target");

        RepoAcl manuallyCreatedRepoAcl = factory.createRepoAcl(factory.createRepoPermissionTarget(),
                Set.of(factory.createAce(PERMISSION_ACTOR, true, 0)),
                null, 0, "not_internal_repo_perm_target");

        expect(aclStoreServiceMock.getAllRepoAcls()).andReturn(List.of(generatedRepoAcl, manuallyCreatedRepoAcl));
        replay(aclStoreServiceMock);

        Multimap<PermissionTarget, AceInfo> permissionTargetAceInfoMap =
                service.getGroupsPermissions(List.of(PERMISSION_ACTOR), ArtifactoryResourceType.REPO);
        assertEquals(permissionTargetAceInfoMap.size(), 1);
        assertNotNull(permissionTargetAceInfoMap.get(manuallyCreatedRepoAcl.getPermissionTarget()));
    }

    @Test
    public void testGetAllBuildAcls() {
        BuildPermissionTargetImpl buildPermissionTarget = new BuildPermissionTargetImpl();
        MutableBuildAclImpl buildAcl = new MutableBuildAclImpl(buildPermissionTarget,
                Set.of(), "access", 12345L, INTERNAL_PERMISSION_PREFIX);
        expect(aclStoreServiceMock.getAllBuildAcls()).andReturn(List.of(buildAcl)).times(2);
        replay(aclStoreServiceMock);

        List<BuildAcl> results = service.getAllBuildAcls(false);
        assertEquals(results.size(), 1);

        results = service.getAllBuildAcls(true);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testGetBuildPathAcls() {
        BuildPermissionTargetImpl buildPermissionTarget = new BuildPermissionTargetImpl();
        buildPermissionTarget.setIncludes(List.of("proj-build-info"));
        MutableBuildAclImpl buildAcl = new MutableBuildAclImpl(buildPermissionTarget,
                Set.of(), "access", 12345L, INTERNAL_PERMISSION_PREFIX);
        expect(aclStoreServiceMock.getAllBuildAcls()).andReturn(List.of(buildAcl)).times(2);
        replay(aclStoreServiceMock);

        RepoPath repoPath = RepoPathFactory.create("proj-build-info");
        List<BuildAcl> results = service.getBuildPathAcls(repoPath, false);
        assertEquals(results.size(), 1);

        results = service.getBuildPathAcls(repoPath, true);
        assertEquals(results.size(), 0);
    }

    @Test(dataProvider = "permissionsProvider")
    public void isGrantedForSignedUrl(ArtifactoryPermission permission, boolean expectedResult) {
        PermissionScopeResourceType resourceType = Mockito.mock(PermissionScopeResourceType.class);
        Authentication authentication = Mockito.mock(Authentication.class);
        UserImpl userInfo = new UserImpl();
        userInfo.setUsername("simple");
        SimpleUser simpleUser = new SimpleUser(userInfo);
        when(authentication.getPrincipal()).thenReturn(simpleUser);
        RepoPath repoPath = RepoPathFactory.create("repo-key/path");
        Mockito.doReturn(false).when(service).isGranted(Mockito.eq(repoPath), Mockito.eq(permission),
                any(UserInfo.class));
        Mockito.doReturn(true).when(service).isGrantedBySignedUrl(repoPath, authentication, permission);

        assertEquals(expectedResult, service.isGranted(resourceType, repoPath, permission, authentication));
        int signedUrlExecuted = permission == READ || permission == DEPLOY ?
                1 : 0;
        Mockito.verify(service, times(signedUrlExecuted)).isGrantedBySignedUrl(repoPath, authentication, permission);
    }

    @DataProvider
    public static Object[][] permissionsProvider() {
        return new Object[][] {
                {READ, true},
                {DEPLOY, true},
                {DELETE, false},
                {MANAGE, false},
                {ANNOTATE, false}
        };
    }

    @Test
    public void testReturnTrueIfGranted() {
        Authentication authentication = mock(Authentication.class);

        RepoPath repoPath = RepoPathFactory.create("test-repo");
        UserImpl userInfo = new UserImpl();
        userInfo.setUsername("Genos-Sama");
        when(authentication.getPrincipal()).thenReturn(new SimpleUser(userInfo));

        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(repositoryService.existsByType(Mockito.eq(RepoDetailsType.RELEASE_BUNDLE),
                Mockito.eq((repoPath.getRepoKey())))).thenReturn(false);
        BuildFacade buildFacade = mock(BuildFacade.class);
        when(buildFacade.isBuildInfoRepo(any())).thenReturn(false);
        AclStoreService aclStoreService = mock(AclStoreService.class);
        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> userToRepoToAclMap = Maps.newHashMap();

        AceImpl aceU1 = new AceImpl(userInfo.getUsername(), false, READ.getMask());
        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of(repoPath.getRepoKey()),
                Lists.newArrayList("**"),
                Lists.newArrayList());

        PrincipalPermission<RepoPermissionTarget> principalPermission =
                new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        userToRepoToAclMap.put(userInfo.getUsername(), Map.of(repoPath.getRepoKey(), Set.of(principalPermission)));
        AclCache<RepoPermissionTarget> aclCache = new AclCache<>(Maps.newHashMap(), userToRepoToAclMap);
        when(aclStoreService.getAclCache()).thenReturn(aclCache);

        LegacyAuthorizationServiceImpl realAuthorizationService = new LegacyAuthorizationServiceImpl();
        realAuthorizationService.setRepositoryService(repositoryService);
        realAuthorizationService.setAclStoreService(aclStoreService);
        realAuthorizationService.setBuildFacade(buildFacade);

        LegacyAuthorizationServiceImpl authorizationService = spy(realAuthorizationService);
        // granted by permission target
        boolean granted = authorizationService.isGranted(PermissionScopeResourceType.ARTIFACT,
                repoPath, READ,
                authentication);

        assertTrue(granted);
        Mockito.verify(authorizationService).isGranted(repoPath, READ, userInfo);
        Mockito.verify(authorizationService, times(0)).isGrantedByAccessToken(repoPath, authentication);
        Mockito.verify(authorizationService, times(0))
                .isGrantedBySignedUrl(repoPath, authentication, READ);
        Mockito.verify(authorizationService, times(0))
                .isGrantedByResourcePermissionScopedToken(PermissionScopeResourceType.ARTIFACT, repoPath,
                        READ, authentication);

        // granted by access token
        authorizationService = spy(realAuthorizationService);

        when(aclStoreService.getAclCache()).thenReturn(new AclCache<>(Maps.newHashMap(), Maps.newHashMap()));

        AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
        when(accessTokenAuthentication.getPrincipal()).thenReturn(new SimpleUser(userInfo));
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope())
                .thenReturn(List.of("path.checksum:" + "c6c78f7c0994364f0057e63f756c018343f7278a" + ":read"));
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);

        granted = authorizationService.isGranted(PermissionScopeResourceType.ARTIFACT,
                repoPath, READ,
                accessTokenAuthentication);
        assertTrue(granted);
        Mockito.verify(authorizationService).isGranted(repoPath, READ, userInfo);
        Mockito.verify(authorizationService).isGrantedByAccessToken(repoPath, accessTokenAuthentication);
        Mockito.verify(authorizationService, times(0))
                .isGrantedBySignedUrl(repoPath, accessTokenAuthentication, READ);
        Mockito.verify(authorizationService, times(0))
                .isGrantedByResourcePermissionScopedToken(PermissionScopeResourceType.ARTIFACT, repoPath,
                        READ, accessTokenAuthentication);

        // granted by signed url
        authorizationService = spy(realAuthorizationService);

        when(aclStoreService.getAclCache()).thenReturn(new AclCache<>(Maps.newHashMap(), Maps.newHashMap()));

        SignedUrlAuthenticationToken signedUrlAuthentication = mock(SignedUrlAuthenticationToken.class);
        when(signedUrlAuthentication.getPrincipal()).thenReturn(new SimpleUser(userInfo));

        granted = authorizationService.isGranted(PermissionScopeResourceType.ARTIFACT,
                repoPath, READ,
                signedUrlAuthentication);

        assertTrue(granted);
        Mockito.verify(authorizationService).isGranted(repoPath, READ, userInfo);
        Mockito.verify(authorizationService).isGrantedByAccessToken(repoPath, signedUrlAuthentication);
        Mockito.verify(authorizationService)
                .isGrantedBySignedUrl(repoPath, signedUrlAuthentication, READ);
        Mockito.verify(authorizationService, times(0))
                .isGrantedByResourcePermissionScopedToken(PermissionScopeResourceType.ARTIFACT, repoPath,
                        READ, signedUrlAuthentication);

        // granted by scoped token
        authorizationService = spy(realAuthorizationService);

        when(aclStoreService.getAclCache()).thenReturn(new AclCache<>(Maps.newHashMap(), Maps.newHashMap()));
        when(jwtAccessToken.getScope()).thenReturn(List.of("artifact:test-repo/**:r"));
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);

        try {
            SecurityContextImpl securityContext = new SecurityContextImpl();
            securityContext.setAuthentication(accessTokenAuthentication);
            SecurityContextHolder.setContext(securityContext);

            AccessService accessService = mock(AccessService.class);
            AccessClient accessClient = mock(AccessClient.class);
            AuthClient authClient = mock(AuthClient.class);
            when(authClient.principalCanDo(any(), any(), anyBoolean())).thenReturn(true);
            LoginResult loginResult = mock(LoginResult.class);
            when(loginResult.getPrincipal()).thenReturn(mock(AccessPrincipal.class));
            when(authClient.loginByAuthHeader(anyString())).thenReturn(loginResult);
            when(accessClient.cachedAuth()).thenReturn(authClient);
            when(accessService.getAccessClient()).thenReturn(accessClient);
            authorizationService.setAccessService(accessService);

            granted = authorizationService.isGranted(PermissionScopeResourceType.ARTIFACT,
                    repoPath, READ,
                    accessTokenAuthentication);

            assertTrue(granted);
            Mockito.verify(authorizationService).isGranted(repoPath, READ, userInfo);
            Mockito.verify(authorizationService).isGrantedByAccessToken(repoPath, accessTokenAuthentication);
            Mockito.verify(authorizationService)
                    .isGrantedBySignedUrl(repoPath, accessTokenAuthentication, READ);
            Mockito.verify(authorizationService)
                    .isGrantedByResourcePermissionScopedToken(PermissionScopeResourceType.ARTIFACT, repoPath,
                            READ, accessTokenAuthentication);
        } finally {
            SecurityContextHolder.setContext(new SecurityContextImpl());
        }
    }

    @Test
    public void testHasAccessToReleaseBundleRepo() {
        Authentication authentication = mock(Authentication.class);
        RepoPath repoPathV1 = RepoPathFactory.create("release-bundles");
        RepoPath repoPathV2 = RepoPathFactory.create(DEFAULT_RBV2_REPO);
        UserImpl userInfo = new UserImpl();
        userInfo.setUsername("Genos-Sama");
        when(authentication.getPrincipal()).thenReturn(new SimpleUser(userInfo));

        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(repositoryService.existsByType(RepoDetailsType.RELEASE_BUNDLE, repoPathV1.getRepoKey()))
                .thenReturn(true);

        BuildFacade buildFacade = mock(BuildFacade.class);
        when(buildFacade.isBuildInfoRepo(any())).thenReturn(false);
        AclStoreService aclStoreService = mock(AclStoreService.class);
        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> userToRepoToAclMap = Maps.newHashMap();

        AceImpl aceU1 = new AceImpl(userInfo.getUsername(), false, READ.getMask());
        RepoPermissionTargetImpl pt = new RepoPermissionTargetImpl("pt1",
                Set.of(repoPathV1.getRepoKey(), repoPathV2.getRepoKey()),
                Lists.newArrayList("**"),
                Lists.newArrayList());

        PrincipalPermission<RepoPermissionTarget> principalPermission =
                new PrincipalPermissionImpl<>(pt, aceU1, "accessIdentifier");
        userToRepoToAclMap.put(userInfo.getUsername(), Map.of(
                repoPathV1.getRepoKey(), Set.of(principalPermission),
                repoPathV2.getRepoKey(), Set.of(principalPermission)
        ));
        AclCache<RepoPermissionTarget> aclCache = new AclCache<>(Maps.newHashMap(), userToRepoToAclMap);
        when(aclStoreService.getReleaseBundlesAclCache()).thenReturn(aclCache);

        AddonsManager addons = Mockito.mock(AddonsManager.class);
        when(addons.isEntitled(EntitlementType.RELEASE_BUNDLES_READ)).thenReturn(true);

        LegacyAuthorizationServiceImpl realAuthorizationService = new LegacyAuthorizationServiceImpl();
        realAuthorizationService.setRepositoryService(repositoryService);
        realAuthorizationService.setAclStoreService(aclStoreService);
        realAuthorizationService.setBuildFacade(buildFacade);
        realAuthorizationService.setAddons(addons);

        LegacyAuthorizationServiceImpl authorizationService = spy(realAuthorizationService);

        // Granted by permission target
        boolean grantedV1 = authorizationService.isGranted(PermissionScopeResourceType.ARTIFACT,
                repoPathV1, READ,
                authentication);
        boolean grantedV2 = authorizationService.isGranted(PermissionScopeResourceType.ARTIFACT,
                repoPathV2, READ,
                authentication);

        assertTrue(grantedV1);
        assertTrue(grantedV2);
        Mockito.verify(authorizationService).isGranted(repoPathV1, READ, userInfo);
        Mockito.verify(authorizationService).isGranted(repoPathV2, READ, userInfo);
        Mockito.verify(aclStoreService, times(2)).getReleaseBundlesAclCache();
        Mockito.verify(repositoryService, times(1))
                .existsByType(RepoDetailsType.RELEASE_BUNDLE, repoPathV2.getRepoKey());
    }

    @Test(dataProvider = "supportBundlePermissionScopesAndExpected")
    public void testSupportBundleActionsByPermissionScopedToken(String userTokenPermissionScope,
            boolean expectedCanDeploy, boolean expectedCanRead, boolean expectedCanAnnotate, boolean expectedCanDelete,
            boolean expectedCanManage, boolean isAdmin, boolean isEdge) {
        AccessService accessServiceMock = mock(AccessService.class, Answers.RETURNS_DEEP_STUBS);
        LoginResult loginResult = mock(LoginResult.class);
        AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
        when(loginResult.getPrincipal()).thenReturn(accessPrincipal);
        when(accessServiceMock.getAccessClient().cachedAuth().loginByAuthHeader(any(String.class)))
                .thenReturn(loginResult);
        when(accessServiceMock.getAccessClient().cachedAuth().principalCanDo(any(), any(), anyBoolean())).thenAnswer(
                (Answer<Boolean>) invocationOnMock -> {
                    PermissionScope requiredPermissionScope = invocationOnMock.getArgument(1);
                    String[] userScopeArr = userTokenPermissionScope.split(":");
                    return userScopeArr[0].equals(requiredPermissionScope.getResourceType().getName()) &&
                            userScopeArr[1].equals(requiredPermissionScope.getTarget()) &&
                            userScopeArr[2].contains(requiredPermissionScope.getAction().getCode());
                });
        service.setAccessService(accessServiceMock);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.isEntitled(EntitlementType.EDGE_MIXED)).thenReturn(isEdge);
        service.setAddons(addonsManager);
        SecurityService securityServiceMock = mock(SecurityService.class);
        when(securityServiceMock.isAdmin()).thenReturn(isAdmin);
        service.setSecurityService(securityServiceMock);
        List<String> scopes = // null scopes will use Basic Auth instead of token
                StringUtils.isEmpty(userTokenPermissionScope) ? null : Lists.newArrayList(userTokenPermissionScope);
        authenticateUserWithScopes(isAdmin, scopes);
        RepoPath repoPath = RepoPathFactory.create("jfrog-support-bundle", "p/a/t/h");
        assertEquals(service.canDeploy(repoPath), expectedCanDeploy);
        assertEquals(service.canRead(repoPath), expectedCanRead);
        assertEquals(service.canAnnotate(repoPath), expectedCanAnnotate);
        assertEquals(service.canDelete(repoPath), expectedCanDelete);
        assertEquals(service.canManage(repoPath), expectedCanManage);
    }

    @DataProvider
    public static Object[][] supportBundlePermissionScopesAndExpected() {
        return new Object[][] {
                //tokenScope, expectedCanDeploy, expectedCanRead, expectedCanAnnotate, expectedCanDelete,
                //                                                                   expectedCanManage, isAdmin, isEdge
                {"internal:supportbundle:w", true, false, false, false, false, false, false}, // not admin, not Edge
                {"internal:supportbundle:r", false, true, false, false, false, false, false},
                {"internal:supportbundle:a", false, false, false, false, false, false, false},
                {"internal:supportbundle:d", false, false, false, true, false, false, false},
                {"internal:supportbundle:m", false, false, false, false, false, false, false},
                {"internal:supportbundle:r,w,d", true, true, false, true, false, false, false},
                {"artifact:jfrog-support-bundle:w", false, false, false, false, false, false, false},
                {"artifact:jfrog-support-bundle:r", false, false, false, false, false, false, false},
                {"artifact:jfrog-support-bundle:a", false, false, false, false, false, false, false},
                {"artifact:jfrog-support-bundle:d", false, false, false, false, false, false, false},
                {"artifact:jfrog-support-bundle:m", false, false, false, false, false, false, false},
                {"artifact:jfrog-support-bundle:r,w,d", false, false, false, false, false, false, false},
                {"", false, false, false, false, false, false, false},
                {"internal:supportbundle:w", true, true, true, true, true, true, false},      // admin, not Edge
                {"internal:supportbundle:r", true, true, true, true, true, true, false},
                {"internal:supportbundle:r,d", true, true, true, true, true, true, false},
                {"artifact:jfrog-support-bundle:w", true, true, true, true, true, true, false},
                {"artifact:jfrog-support-bundle:r,w", true, true, true, true, true, true, false},
                {"", true, true, true, true, true, true, false},

                {"internal:supportbundle:w", true, false, false, false, false, false, true},  // not admin, Edge
                {"internal:supportbundle:r", false, true, false, false, false, false, true},
                {"internal:supportbundle:a", false, false, false, false, false, false, true},
                {"internal:supportbundle:d", false, false, false, true, false, false, true},
                {"internal:supportbundle:m", false, false, false, false, false, false, true},
                {"internal:supportbundle:r,w,d", true, true, false, true, false, false, true},
                {"artifact:jfrog-support-bundle:w", false, false, false, false, false, false, true},
                {"artifact:jfrog-support-bundle:r", false, false, false, false, false, false, true},
                {"artifact:jfrog-support-bundle:a", false, false, false, false, false, false, true},
                {"artifact:jfrog-support-bundle:d", false, false, false, false, false, false, true},
                {"artifact:jfrog-support-bundle:m", false, false, false, false, false, false, true},
                {"artifact:jfrog-support-bundle:r,w,d", false, false, false, false, false, false, true},
                {"", false, false, false, false, false, false, false},
                {"internal:supportbundle:w", true, true, true, true, true, true, true},       // admin, Edge
                {"internal:supportbundle:r", false, true, true, true, true, true, true},
                {"internal:supportbundle:r,d,m", false, true, true, true, true, true, true},
                {"internal:supportbundle:r,w,d", true, true, true, true, true, true, true},
                {"artifact:jfrog-support-bundle:w", false, true, true, true, true, true, true},
                {"artifact:jfrog-support-bundle:r,w", false, true, true, true, true, true, true},
                {"", false, true, true, true, true, true, true},

                // note: of scope "applied-permissions/admin" isn't needed to be covered here, as in real flow it
                // translates to role admin, so SecurityServiceImpl.isAdmin() returns true, and this is covered
        };
    }

    @Test
    public void permissionOnVirtualRootWhenRemoteIsConfiguredAndNotStoreArtifactsLocally() {
        setSimpleUserAuthentication("uesr");
        VirtualRepo repository = mock(VirtualRepo.class);
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        LegacyAuthorizationServiceImpl authorizationService = spy(LegacyAuthorizationServiceImpl.class);
        SecurityService securityService = mock(SecurityService.class);
        RemoteRepo remoteRepoMock = mock(RemoteRepo.class);
        when(remoteRepoMock.isStoreArtifactsLocally()).thenReturn(false);
        when(remoteRepoMock.isReal()).thenReturn(true);
        Set<RemoteRepo> remoteSetMock = new HashSet<>(Set.of(remoteRepoMock));
        setField(authorizationService, "repositoryService", internalRepositoryService);
        setField(authorizationService, "securityService", securityService);
        when(remoteRepoMock.getKey()).thenReturn("REPO");
        when(internalRepositoryService.repositoryByKey("REPO")).thenReturn(repository);
        when(repository.getResolvedRemoteRepos()).thenReturn(remoteSetMock);
        when(securityService.isAdmin()).thenReturn(false);
        doReturn(true).when(authorizationService)
                .isGranted(any(RepoPath.class), any(ArtifactoryPermission.class), any(UserInfo.class));
        boolean hasPermissionOnRemoteRoot = authorizationService.userHasPermissionsOnRepositoryRoot("REPO");
        assertTrue(hasPermissionOnRemoteRoot);
    }

    @Test
    public void permissionOnVirtualRootWhenRemoteIsConfiguredAndStoreArtifacts() {
        setSimpleUserAuthentication("uesr");
        VirtualRepo repository = mock(VirtualRepo.class);
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        LegacyAuthorizationServiceImpl authorizationService = spy(LegacyAuthorizationServiceImpl.class);
        SecurityService securityService = mock(SecurityService.class);
        RemoteRepo remoteRepoMock = mock(RemoteRepo.class);
        when(remoteRepoMock.isStoreArtifactsLocally()).thenReturn(true);
        when(remoteRepoMock.isReal()).thenReturn(true);
        RepoPath repoPath = mock(RepoPath.class);
        Set<RemoteRepo> remoteSetMock = new HashSet<>(Set.of(remoteRepoMock));
        setField(authorizationService, "repositoryService", internalRepositoryService);
        setField(authorizationService, "securityService", securityService);
        when(repoPath.getPath()).thenReturn("REPO-cache");
        when(remoteRepoMock.getKey()).thenReturn("REPO");
        when(internalRepositoryService.repositoryByKey("REPO")).thenReturn(repository);
        when(repository.getResolvedRemoteRepos()).thenReturn(remoteSetMock);
        when(securityService.isAdmin()).thenReturn(false);
        doReturn(true).when(authorizationService)
                .isGranted(any(RepoPath.class), any(ArtifactoryPermission.class), any(UserInfo.class));
        boolean hasPermissionOnRemoteRoot = authorizationService.userHasPermissionsOnRepositoryRoot("REPO");
        assertTrue(hasPermissionOnRemoteRoot);
    }

    @Test
    public void permissionOnVirtualRootWhenLocalIsConfigured() {
        setSimpleUserAuthentication("uesr");
        VirtualRepo repository = mock(VirtualRepo.class);
        InternalRepositoryService internalRepositoryService = mock(InternalRepositoryService.class);
        SecurityService securityService = mock(SecurityService.class);
        LegacyAuthorizationServiceImpl authorizationService = spy(LegacyAuthorizationServiceImpl.class);
        LocalRepo localRepoMock = mock(LocalRepo.class);
        Set<LocalRepo> localSetMock = new HashSet<>(Set.of(localRepoMock));
        setField(authorizationService, "repositoryService", internalRepositoryService);
        setField(authorizationService, "securityService", securityService);
        when(localRepoMock.getKey()).thenReturn("local");
        when(internalRepositoryService.repositoryByKey("REPO")).thenReturn(repository);
        when(repository.getResolvedLocalFederatedAndCachedRepos()).thenReturn(localSetMock);
        when(securityService.isAdmin()).thenReturn(false);
        doReturn(true).when(authorizationService)
                .isGranted(any(RepoPath.class), any(ArtifactoryPermission.class), any(UserInfo.class));
        boolean hasPermissionOnRemoteRoot = authorizationService.userHasPermissionsOnRepositoryRoot("REPO");
        assertTrue(hasPermissionOnRemoteRoot);
    }

    @Test
    public void testUserHasPermissions() {
        // user shouldn't have any permissions
        SimpleUser user = createNonAdminUser("noperm");
        EasyMock.reset(userGroupStoreService);
        expect(userGroupStoreService.findUser("noperm")).andReturn(user.getDescriptor()).once();

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock, userGroupStoreService);
        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> nopermPermMap =
                aclCache.getUserResultMap().get("noperm");
        boolean hasNoPermissions = nopermPermMap == null || nopermPermMap.isEmpty();
        assertTrue(hasNoPermissions, "User should not have permissions");
        EasyMock.reset(aclStoreServiceMock, userGroupStoreService);
    }

    @Test
    public void testUserHasPermissionsFromGroup() {
        // user don't have permissions on his own, but should have permission from his group
        SimpleUser user = createNonAdminUser("noperm", securityServiceImplTestHelper.USER_AND_GROUP_SHARED_NAME);
        EasyMock.reset(userGroupStoreService);
        expect(userGroupStoreService.findUser("noperm")).andReturn(user.getDescriptor()).once();

        expectGetAllAclsCallWithAnyArray();
        replay(aclStoreServiceMock, userGroupStoreService);
        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> nopermPermMap =
                aclCache.getUserResultMap().get("noperm");
        boolean hasNoPermissions = nopermPermMap == null || nopermPermMap.isEmpty();
        assertTrue(hasNoPermissions, "User shouldn't have permissions on this path");
        hasNoPermissions = user.getDescriptor()
                .getGroups()
                .stream()
                .allMatch(group -> {
                    Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> nopermGroupPermMap =
                            aclCache.getGroupResultMap()
                                    .get(group.getGroupName());
                    return nopermGroupPermMap == null || nopermGroupPermMap.isEmpty();
                });
        assertFalse(hasNoPermissions, "User should have permissions on this path from his groups");
        EasyMock.reset(aclStoreServiceMock, userGroupStoreService);
    }

    @Test
    public void testIsGrantedBySignedUrlWithRemoteRepoCache() {
        String remoteRepoKey = "remote-repo";
        SignedUrlAuthenticationToken signedUrlAuthentication = mock(SignedUrlAuthenticationToken.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(signedUrlAuthentication.getInternalRepo()).thenReturn(remoteRepoKey);
        when(securityContext.getAuthentication()).thenReturn(signedUrlAuthentication);
        SecurityContextHolder.setContext(securityContext);

        RepoPath cacheRepoPath = RepoPathFactory.create(RepoPathUtils.addDashCache(remoteRepoKey));
        assertTrue(service.isGrantedBySignedUrl(cacheRepoPath, signedUrlAuthentication, READ));
        assertTrue(service.isGrantedBySignedUrl(cacheRepoPath, signedUrlAuthentication, DEPLOY));
    }

    @Test
    public void testGetStrongestReadPermissionTarget_AdminUser() {
        AclStoreService aclStoreService = mock(AclStoreService.class);
        SimpleUser simpleUser = mock(SimpleUser.class);
        Authentication authentication = mock(Authentication.class);
        UserInfo userInfo = mock(UserInfo.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(simpleUser.isEffectiveAdmin()).thenReturn(true);
        when(simpleUser.getUsername()).thenReturn("cool-user");
        when(authentication.getPrincipal()).thenReturn(simpleUser);
        when(simpleUser.getDescriptor()).thenReturn(userInfo);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        LegacyAuthorizationServiceImpl legacyAuthorizationService = new LegacyAuthorizationServiceImpl();
        legacyAuthorizationService.setAclStoreService(aclStoreService);
        PermissionHeuristicScore result = legacyAuthorizationService.getStrongestReadPermissionTarget("repoKey");

        assertEquals(result, PermissionHeuristicScore.admin);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testGetStrongestReadPermissionTarget_ReadAll() {
        AclStoreService aclStoreService = mock(AclStoreService.class);
        AclCache<RepoPermissionTarget> aclCache = mock(AclCache.class);
        SimpleUser simpleUser = mock(SimpleUser.class);
        Authentication authentication = mock(Authentication.class);
        UserInfo userInfo = mock(UserInfo.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(simpleUser.isEffectiveAdmin()).thenReturn(false);
        when(simpleUser.getUsername()).thenReturn("cool-user");
        when(authentication.getPrincipal()).thenReturn(simpleUser);
        when(simpleUser.getDescriptor()).thenReturn(userInfo);
        when(userInfo.getGroupsReference()).thenReturn(new HashSet<>());
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> userResultMap = new HashMap<>();
        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> repoPermissions = new HashMap<>();
        Set<PrincipalPermission<RepoPermissionTarget>> permissions = new HashSet<>();
        PrincipalPermission<RepoPermissionTarget> principalPermission = mock(PrincipalPermission.class);
        RepoPermissionTarget permissionTarget = mock(RepoPermissionTarget.class);
        when(permissionTarget.getIncludes()).thenReturn(List.of("**"));
        when(permissionTarget.getExcludes()).thenReturn(List.of());
        when(principalPermission.getPermissionTarget()).thenReturn(permissionTarget);
        AceInfo aceInfo = mock(AceInfo.class);
        when(aceInfo.getMask()).thenReturn(READ.getMask());
        when(aceInfo.getPrincipal()).thenReturn("cool-user");
        when(principalPermission.getAce()).thenReturn(aceInfo);
        permissions.add(principalPermission);
        repoPermissions.put("repoKey", permissions);
        userResultMap.put("cool-user", repoPermissions);
        when(aclCache.getUserResultMap()).thenReturn(userResultMap);
        when(aclStoreService.getAclCache()).thenReturn(aclCache);

        LegacyAuthorizationServiceImpl legacyAuthorizationService = new LegacyAuthorizationServiceImpl();
        legacyAuthorizationService.setAclStoreService(aclStoreService);
        PermissionHeuristicScore result = legacyAuthorizationService.getStrongestReadPermissionTarget("repoKey");

        assertEquals(result, PermissionHeuristicScore.readAll);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testGetStrongestReadPermissionTarget_ReadWithExclusion() {
        AclStoreService aclStoreService = mock(AclStoreService.class);
        AclCache<RepoPermissionTarget> aclCache = mock(AclCache.class);
        SimpleUser simpleUser = mock(SimpleUser.class);
        Authentication authentication = mock(Authentication.class);
        UserInfo userInfo = mock(UserInfo.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(simpleUser.isEffectiveAdmin()).thenReturn(false);
        when(simpleUser.getUsername()).thenReturn("cool-user");
        when(authentication.getPrincipal()).thenReturn(simpleUser);
        when(simpleUser.getDescriptor()).thenReturn(userInfo);
        when(userInfo.getGroupsReference()).thenReturn(new HashSet<>());
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> userResultMap = new HashMap<>();
        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> repoPermissions = new HashMap<>();
        Set<PrincipalPermission<RepoPermissionTarget>> permissions = new HashSet<>();
        PrincipalPermission<RepoPermissionTarget> principalPermission = mock(PrincipalPermission.class);
        RepoPermissionTarget permissionTarget = mock(RepoPermissionTarget.class);
        when(principalPermission.getPermissionTarget()).thenReturn(permissionTarget);
        AceInfo aceInfo = mock(AceInfo.class);
        when(aceInfo.getMask()).thenReturn(READ.getMask());
        when(aceInfo.getPrincipal()).thenReturn("cool-user");
        when(principalPermission.getAce()).thenReturn(aceInfo);
        permissions.add(principalPermission);
        repoPermissions.put("repoKey", permissions);
        userResultMap.put("cool-user", repoPermissions);
        when(aclCache.getUserResultMap()).thenReturn(userResultMap);
        when(aclStoreService.getAclCache()).thenReturn(aclCache);

        LegacyAuthorizationServiceImpl legacyAuthorizationService = new LegacyAuthorizationServiceImpl();
        legacyAuthorizationService.setAclStoreService(aclStoreService);
        PermissionHeuristicScore result = legacyAuthorizationService.getStrongestReadPermissionTarget("repoKey");

        assertEquals(result, PermissionHeuristicScore.readWithExclusion);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testGetStrongestReadPermissionTarget_ReadNotAllowed() {
        AclStoreService aclStoreService = mock(AclStoreService.class);
        AclCache<RepoPermissionTarget> aclCache = mock(AclCache.class);
        SimpleUser simpleUser = mock(SimpleUser.class);
        Authentication authentication = mock(Authentication.class);
        UserInfo userInfo = mock(UserInfo.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(simpleUser.isEffectiveAdmin()).thenReturn(false);
        when(simpleUser.getUsername()).thenReturn("cool-user");
        when(authentication.getPrincipal()).thenReturn(simpleUser);
        when(simpleUser.getDescriptor()).thenReturn(userInfo);
        when(userInfo.getGroupsReference()).thenReturn(new HashSet<>());
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> userResultMap = new HashMap<>();
        when(aclCache.getUserResultMap()).thenReturn(userResultMap);
        when(aclStoreService.getAclCache()).thenReturn(aclCache);

        LegacyAuthorizationServiceImpl legacyAuthorizationService = new LegacyAuthorizationServiceImpl();
        legacyAuthorizationService.setAclStoreService(aclStoreService);
        PermissionHeuristicScore result = legacyAuthorizationService.getStrongestReadPermissionTarget("repoKey");

        assertEquals(result, PermissionHeuristicScore.readNotAllowed);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testForcingUserPermissionsOverScope() {
        AclStoreService aclStoreService = mock(AclStoreService.class);
        AclCache<RepoPermissionTarget> aclCache = mock(AclCache.class);
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("repoKey");
        when(repoPath.getPath()).thenReturn("path");
        ArtifactoryPermission permission = ArtifactoryPermission.READ;
        UserInfo userWithGroupScope = mock(UserInfo.class);
        when(userWithGroupScope.getUsername()).thenReturn("cool-user");
        when(userWithGroupScope.isAppliedPermissionsGroups()).thenReturn(true);
        when(aclStoreService.getAclCache()).thenReturn(aclCache);
        service.setAclStoreService(aclStoreService);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(repositoryService.existsByType(RepoDetailsType.RELEASE_BUNDLE, "repoKey")).thenReturn(false);
        service.setRepositoryService(repositoryService);

        grantUserPermission(aclCache);

        homeStub.setProperty(ConstantValues.forceUserPermissionsOverTokenScopes, "true");
        boolean result = service.isGranted(repoPath, permission, userWithGroupScope);
        assertTrue(result);

        homeStub.setProperty(ConstantValues.forceUserPermissionsOverTokenScopes, "false");
        boolean resultFalse = service.isGranted(repoPath, permission, userWithGroupScope);
        assertFalse(resultFalse);
    }

    /**
     * When {@code force.user.permissions.over.token.scopes=true} flips the natural scope predicate inside
     * {@link LegacyAuthorizationServiceImpl#isGrantedByUser} (i.e. {@code isTokenScopeOnUser=false} but
     * the FF forces user-scope evaluation), the emitted WARN must carry
     * {@link LogThrottleMarker#getInstance()} so {@code LogThrottleFilter} can suppress repeated identical
     * messages from the same logger. Otherwise a single misconfigured customer can flood the log.
     */
    @Test
    public void ffOverrideWarnCarriesLogThrottleMarker() {
        AclStoreService aclStoreService = mock(AclStoreService.class);
        AclCache<RepoPermissionTarget> aclCache = mock(AclCache.class);
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("repoKey");
        when(repoPath.getPath()).thenReturn("path");
        UserInfo userWithGroupScope = mock(UserInfo.class);
        when(userWithGroupScope.getUsername()).thenReturn("cool-user");
        when(userWithGroupScope.isAppliedPermissionsGroups()).thenReturn(true);
        when(aclStoreService.getAclCache()).thenReturn(aclCache);
        service.setAclStoreService(aclStoreService);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        when(repositoryService.existsByType(RepoDetailsType.RELEASE_BUNDLE, "repoKey")).thenReturn(false);
        service.setRepositoryService(repositoryService);
        grantUserPermission(aclCache);
        homeStub.setProperty(ConstantValues.forceUserPermissionsOverTokenScopes, "true");

        ch.qos.logback.classic.Logger legacyLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LegacyAuthorizationServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        legacyLogger.addAppender(appender);
        try {
            service.isGranted(repoPath, ArtifactoryPermission.READ, userWithGroupScope);

            List<ILoggingEvent> warns = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN)
                    .collect(Collectors.toList());
            assertEquals(warns.size(), 1, "expected exactly one WARN from the FF-override branch");
            List<Marker> markers = warns.get(0).getMarkerList();
            assertNotNull(markers, "WARN event must carry a marker list");
            assertTrue(markers.contains(LogThrottleMarker.getInstance()),
                    "FF-override WARN must carry LogThrottleMarker so LogThrottleFilter can suppress floods; "
                            + "actual markers=" + markers);
        } finally {
            legacyLogger.detachAppender(appender);
        }
    }

    @Test
    void testIsGrantedByAccessToken() {
        // Create Mockito mocks for this test
        AccessService mockAccessService = mock(AccessService.class);
        AccessClient accessClient = mock(AccessClient.class);
        AuthClient authClient = mock(AuthClient.class);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        LoginResult loginResult = mock(LoginResult.class);
        AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
        PermissionScope permissionScope = mock(PermissionScope.class);

        // Set up the mock access service on the service
        service.setAccessService(mockAccessService);

        // Configure mock behavior
        doReturn(accessClient).when(mockAccessService).getAccessClient();
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn("test-token").when(jwtAccessToken).getTokenValue();
        doReturn(jwtAccessToken).when(authentication).getAccessToken();
        doReturn(authentication).when(securityContext).getAuthentication();
        doReturn(accessPrincipal).when(loginResult).getPrincipal();
        doReturn(loginResult).when(authClient).loginByAuthHeader(anyString());
        doReturn(true).when(authClient).principalCanDo(accessPrincipal, permissionScope, false);

        // Set security context
        SecurityContextHolder.setContext(securityContext);

        // Execute test
        Assertions.assertTrue(service.isGrantedByAccessToken(permissionScope));

        // Verify interactions
        Mockito.verify(authClient).principalCanDo(accessPrincipal, permissionScope, false);
        
        // Restore original access service
        service.setAccessService(accessService);
    }

    @Test
    void testPrincipalCanDo() {
        // Create Mockito mocks for this test
        AccessService mockAccessService = mock(AccessService.class);
        AccessClient accessClient = mock(AccessClient.class);
        AuthClient authClient = mock(AuthClient.class);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        LoginResult loginResult = mock(LoginResult.class);
        AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
        PermissionScope permissionScope = mock(PermissionScope.class);

        // Set up the mock access service on the service
        service.setAccessService(mockAccessService);

        // Configure mock behavior
        doReturn(accessClient).when(mockAccessService).getAccessClient();
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn("test-token").when(jwtAccessToken).getTokenValue();
        doReturn(jwtAccessToken).when(authentication).getAccessToken();
        doReturn(authentication).when(securityContext).getAuthentication();
        doReturn(accessPrincipal).when(loginResult).getPrincipal();
        doReturn(loginResult).when(authClient).loginByAuthHeader(anyString());
        doReturn(true).when(authClient).principalCanDo(accessPrincipal, permissionScope, true);

        // Set security context
        SecurityContextHolder.setContext(securityContext);

        // Execute test
        Assertions.assertTrue(service.principalCanDo(permissionScope));

        // Verify interactions
        Mockito.verify(authClient).principalCanDo(accessPrincipal, permissionScope, true);
        
        // Restore original access service
        service.setAccessService(accessService);
    }

    private static void grantUserPermission(AclCache<RepoPermissionTarget> aclCache) {
        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> userResultMap = new HashMap<>();
        Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> repoPermissions = new HashMap<>();
        Set<PrincipalPermission<RepoPermissionTarget>> permissions = new HashSet<>();
        PrincipalPermission<RepoPermissionTarget> principalPermission = mock(PrincipalPermission.class);
        RepoPermissionTarget permissionTarget = mock(RepoPermissionTarget.class);
        when(permissionTarget.getIncludes()).thenReturn(List.of("**"));
        when(permissionTarget.getExcludes()).thenReturn(List.of());
        when(permissionTarget.getRepoKeys()).thenReturn(Set.of("repoKey"));
        when(principalPermission.getPermissionTarget()).thenReturn(permissionTarget);
        AceInfo aceInfo = mock(AceInfo.class);
        when(aceInfo.getMask()).thenReturn(READ.getMask());
        when(aceInfo.getPrincipal()).thenReturn("cool-user");
        when(principalPermission.getAce()).thenReturn(aceInfo);
        permissions.add(principalPermission);
        repoPermissions.put("repoKey", permissions);
        userResultMap.put("cool-user", repoPermissions);
        when(aclCache.getUserResultMap()).thenReturn(userResultMap);
    }

    private void expectRemoteByKeyNullForAnyConsts() {
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(ANY_LOCAL_REPO)).andReturn(null).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(ANY_REMOTE_REPO)).andReturn(null).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(ANY_DISTRIBUTION_REPO)).andReturn(null).anyTimes();
        expect(repositoryServiceMock.remoteRepoDescriptorByKey(ANY_REPO)).andReturn(null).anyTimes();
        replay(repositoryServiceMock);
    }

    private void expectGetAllAclsCallWithAnyArray() {
        expect(aclStoreServiceMock.getAllRepoAcls()).andReturn(testAcls);
        expect(aclStoreServiceMock.getAclCache()).andReturn(aclCache);
    }

    private static LocalRepo createLocalRepoMock() {
        LocalRepo localRepo = createMock(LocalRepo.class);
        expect(localRepo.isLocal()).andReturn(true).anyTimes();
        expect(localRepo.isCache()).andReturn(false).anyTimes();
        replay(localRepo);
        return localRepo;
    }

    private static LocalRepo createCacheRepoMock() {
        LocalRepo localRepo = createMock(LocalRepo.class);
        expect(localRepo.isLocal()).andReturn(true).anyTimes();
        expect(localRepo.isCache()).andReturn(true).anyTimes();
        replay(localRepo);
        return localRepo;
    }

    private RemoteRepo createRemoteRepoMock() {
        RemoteRepo remoteRepo = createMock(RemoteRepo.class);
        expect(remoteRepo.isReal()).andReturn(true).anyTimes();
        replay(remoteRepo);
        return remoteRepo;
    }

    private void authenticateUserWithScopes(boolean isAdmin, List<String> scopes) {
        SimpleUser simpleUser;
        if (isAdmin) {
            simpleUser = createAdminUser("admin");
        } else {
            simpleUser = createNonAdminUser("non-admin");
        }
        Authentication authentication = getAuthentication(scopes, simpleUser);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private Authentication getAuthentication(List<String> scopes, SimpleUser simpleUser) {
        Authentication authentication;
        if (CollectionUtils.isEmpty(scopes)) { // Basic Auth
            authentication = mock(AccessResponseAuthentication.class);
        } else {
            JwtAccessToken jwtToken = mock(JwtAccessToken.class);
            when(jwtToken.getScope()).thenReturn(scopes);
            AccessTokenAuthentication tokenAuthentication = mock(AccessTokenAuthentication.class);
            when(tokenAuthentication.getAccessToken()).thenReturn(jwtToken);
            authentication = tokenAuthentication;
        }
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(simpleUser);
        return authentication;
    }

    @DataProvider
    public static Object[][] provideGetBuildNameAcls() {
        return new Object[][] {
                // {Build name, permission target with pattern, should build be a match to pattern}
                {"test1", Collections.singletonList(createBuildAcl("test1/**")), true},
                {"test2", Collections.singletonList(createBuildAcl("no")), false},
                {"test3", Collections.singletonList(createBuildAcl("**/test3/**")), true},
                {"test4", Collections.singletonList(createBuildAcl("**/test4")), false},
                {"test5", Collections.singletonList(createBuildAcl("*/test5")), false},
                {"test6", Collections.singletonList(createBuildAcl("someDir/test6")), false},
                {"test7", Collections.singletonList(createBuildAcl("someDir/test7/**")), false},
                {"test8", Collections.singletonList(createBuildAcl("test8/1**")), true},
                {"test9", Collections.singletonList(createBuildAcl("test9/2**")), false},

        };
    }

    private static BuildAcl createBuildAcl(String pattern) {
        return createBuildAcl("1", pattern, 0);
    }

    private static BuildAcl createBuildAcl(String name, String pattern, int mask) {
        MutableBuildPermissionTarget buildPermissionTarget = InfoFactoryHolder.get()
                .createBuildPermissionTarget(name, Set.of("repoKey"));
        buildPermissionTarget.setIncludesPattern(pattern);
        return InfoFactoryHolder.get().createBuildAcl(
                buildPermissionTarget,
                Set.of(InfoFactoryHolder.get().createAce(PERMISSION_ACTOR, false, mask)),
                PERMISSION_ACTOR, 0, name);
    }

    private static RepoAcl createRepoAcl(String name, int mask, boolean group, String principal) {
        InfoFactory factory = InfoFactoryHolder.get();
        return factory.createRepoAcl(
                factory.createRepoPermissionTarget(name, Set.of("repoKey")),
                Set.of(factory.createAce(principal, group, mask)),
                null, 0, name);
    }

    private static ReleaseBundleAcl createReleaseBundleAcl(String name, int mask) {
        InfoFactory factory = InfoFactoryHolder.get();
        return factory.createReleaseBundleAcl(
                factory.createReleaseBundlePermissionTarget(name, Set.of("repoKey")),
                Set.of(factory.createAce(PERMISSION_ACTOR, false, mask)),
                null, 0, name);
    }

    @Nonnull
    private static AclCache<RepoPermissionTarget> createAclCache(
            Set<BaseRepoAcl<? extends RepoPermissionTarget>> repoAcls, String... groups) {
        Set<PrincipalPermission<RepoPermissionTarget>> principalPermissions = Sets.newHashSet();
        for (BaseRepoAcl<? extends RepoPermissionTarget> repoAcl : repoAcls) {
            principalPermissions.add(
                    new PrincipalPermissionImpl<>(repoAcl.getPermissionTarget(), repoAcl.getAces().iterator().next(),
                            repoAcl.getAccessIdentifier()));
        }

        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> groupToPermissionsMap =
                Maps.newHashMap();
        if (groups != null) {
            for (String group : groups) {
                groupToPermissionsMap.put(group, Map.of("repoPath", principalPermissions));
            }
        }
        return new AclCache<>(groupToPermissionsMap, Map.of(PERMISSION_ACTOR, Map.of(
                "repoPath", principalPermissions)));
    }

    @Nonnull
    private static AclCache<RepoPermissionTarget> createUserAclCache(
            Set<BaseRepoAcl<? extends RepoPermissionTarget>> repoAcls) {
        Set<PrincipalPermission<RepoPermissionTarget>> principalPermissions = Sets.newHashSet();
        for (BaseRepoAcl<? extends RepoPermissionTarget> repoAcl : repoAcls) {
            principalPermissions.add(
                    new PrincipalPermissionImpl<>(repoAcl.getPermissionTarget(), repoAcl.getAces().iterator().next(),
                            repoAcl.getAccessIdentifier()));
        }
        return new AclCache<>(Map.of(), Map.of(PERMISSION_ACTOR, Map.of("repoPath", principalPermissions)));
    }

    @Nonnull
    private static AclCache<RepoPermissionTarget> createGroupAclCache(
            Set<BaseRepoAcl<? extends RepoPermissionTarget>> repoAcls, String... groups) {
        Set<PrincipalPermission<RepoPermissionTarget>> principalPermissions = Sets.newHashSet();
        for (BaseRepoAcl<? extends RepoPermissionTarget> repoAcl : repoAcls) {
            principalPermissions.add(
                    new PrincipalPermissionImpl<>(repoAcl.getPermissionTarget(), repoAcl.getAces().iterator().next(),
                            repoAcl.getAccessIdentifier()));
        }

        Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> groupToPermissionsMap =
                Maps.newHashMap();
        if (groups != null) {
            for (String group : groups) {
                groupToPermissionsMap.put(group, Map.of("repoPath", principalPermissions));
            }
        }
        return new AclCache<>(groupToPermissionsMap, Map.of());
    }
}