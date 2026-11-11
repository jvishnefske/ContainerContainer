package org.artifactory.ui.rest.service.admin.security.auth.currentuser;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.security.general.SecurityConfig;
import org.artifactory.ui.rest.model.admin.security.password.PasswordSettings;
import org.artifactory.ui.rest.model.admin.security.user.BaseUser;
import org.artifactory.ui.rest.service.admin.security.general.GetSecurityConfigService;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.artifactory.api.security.UserGroupService.UI_VIEW_BLOCKED_USER_PROP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
@Test
public class GetCurrentUserServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryRestRequest requestMock;
    @Mock
    private RestResponse responseMock;
    @Mock
    private ArtifactoryContext artifactoryContextMock;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private PluginsAddon pluginsAddon;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor configDescriptor;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private SecurityDescriptor securityDescriptor;
    @Mock
    private GetSecurityConfigService securityConfigService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private AccessService accessService;
    @Mock
    private AuthSettingsClient authSettingsClient;
    @Mock
    private RepositoryService repositoryService;

    private final GetCurrentUserService service = new GetCurrentUserService();
    private MutableUserInfo userInfo;
    private PropertiesImpl props;
    private AutoCloseable mocks;

    @BeforeClass
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(artifactoryContextMock.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        ReflectionTestUtils.setField(service, "addonsManager", addonsManager);
        ReflectionTestUtils.setField(service, "getSecurityConfigService", securityConfigService);
        ReflectionTestUtils.setField(service, "authService", authorizationService);
        ReflectionTestUtils.setField(service, "securityService", securityService);
        ReflectionTestUtils.setField(service, "userGroupService", userGroupService);
        ReflectionTestUtils.setField(service, "accessService", accessService);
        ReflectionTestUtils.setField(service, "repositoryService", repositoryService);
        when(configDescriptor.getSecurity()).thenReturn(securityDescriptor);
        doNothing().when(securityConfigService).execute(any(), any());
        when(accessService.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        prepareAddonsManager();
        ArtifactoryContextThreadBinder.bind(artifactoryContextMock);
    }

    @BeforeMethod
    public void methodSetup() {
        userInfo = new UserInfoBuilder("myuser").build();
        when(userGroupService.currentUser()).thenReturn(userInfo);
        props = new PropertiesImpl();
        when(userGroupService.findPropertiesForUser(userInfo)).thenReturn(props);
    }

    @Test
    public void testDisabledUiAccess() {
        props.put(UI_VIEW_BLOCKED_USER_PROP, "true");
        SecurityConfig securityConfig =
                new SecurityConfig(true, false, false, true, new PasswordSettings(), null, true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        service.execute(requestMock, responseMock);
        when(responseMock.iModel(any())).then(invocationOnMock -> {
            assertTrue(invocationOnMock.getArguments()[0] instanceof BaseUser);
            BaseUser userResponse = (BaseUser) invocationOnMock.getArguments()[0];
            assertTrue(userResponse.isdisableUIAccess());
            return null;
        });
    }

    @Test(description = "bugfix RTFACT-25151")
    public void testExecuteManageFlagsTrue() {
        userInfo.setPolicyViewer(true);
        userInfo.setPolicyManager(true);
        userInfo.setWatchManager(true);
        userInfo.setReportsManager(true);

        SecurityConfig securityConfig =
                new SecurityConfig(true, false, false, true, new PasswordSettings(), null, true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        prepareAuthorizationService();
        when(responseMock.iModel(any())).then(invocationOnMock -> {
            assertTrue(invocationOnMock.getArguments()[0] instanceof BaseUser);
            BaseUser userResponse = (BaseUser) invocationOnMock.getArguments()[0];
            assertTrue(userResponse.isPolicyViewer());
            assertTrue(userResponse.isWatchManager());
            assertTrue(userResponse.isPolicyManager());
            assertTrue(userResponse.isReportsManager());
            return null;
        });

        service.execute(requestMock, responseMock);
    }

    @Test(dataProvider = "canDeployProvider")
    public void testCanDeploy(boolean canCreateReleaseBundle, boolean hasAnyDeployableRepo) {
        SecurityConfig securityConfig =
                new SecurityConfig(true, false, false, true, new PasswordSettings(), null, true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        when(repositoryService.hasAnyDeployableRepo()).thenReturn(hasAnyDeployableRepo);
        when(authorizationService.hasReleaseBundlePermission(ArtifactoryPermission.DEPLOY)).thenReturn(
                canCreateReleaseBundle);
        prepareAuthorizationService();
        when(responseMock.iModel(any())).then(invocationOnMock -> {
            assertTrue(invocationOnMock.getArguments()[0] instanceof BaseUser);
            BaseUser userResponse = (BaseUser) invocationOnMock.getArguments()[0];
            assertEquals(userResponse.isCanDeploy(), hasAnyDeployableRepo || canCreateReleaseBundle);
            return null;
        });

        service.execute(requestMock, responseMock);
    }

    @Test(description = "bugfix RTFACT-25151")
    public void testExecuteManageFlagsDefault() {
        SecurityConfig securityConfig =
                new SecurityConfig(true, false, false, true, new PasswordSettings(), null, true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        prepareAuthorizationService();
        when(responseMock.iModel(any())).then(invocationOnMock -> {
            assertTrue(invocationOnMock.getArguments()[0] instanceof BaseUser);
            BaseUser userResponse = (BaseUser) invocationOnMock.getArguments()[0];
            assertFalse(userResponse.isPolicyViewer());
            assertFalse(userResponse.isWatchManager());
            assertFalse(userResponse.isPolicyManager());
            assertFalse(userResponse.isReportsManager());
            return null;
        });

        service.execute(requestMock, responseMock);
    }

    @Test(description = "RTFACT-21233")
    public void testExecute() {
        SecurityConfig securityConfig =
                new SecurityConfig(true, false, false, true, new PasswordSettings(), null, true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        prepareAuthorizationService();
        when(responseMock.iModel(any())).then(invocationOnMock -> {
            assertTrue(invocationOnMock.getArguments()[0] instanceof BaseUser);
            return null;
        });
        service.execute(requestMock, responseMock);
    }

    @Test(dataProvider = "canManageProvider",
            description = "RTDEV-86579: canManage is true iff hasPermission(MANAGE,true) OR isResourceManager")
    public void testCanManage(boolean hasManagePermission, boolean isResourceManager, boolean expectedCanManage) {
        SecurityConfig securityConfig =
                new SecurityConfig(true, false, false, true, new PasswordSettings(), null, true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        prepareAuthorizationService();
        when(authorizationService.hasPermission(eq(ArtifactoryPermission.MANAGE), eq(true)))
                .thenReturn(hasManagePermission);
        when(securityService.isResourceManager()).thenReturn(isResourceManager);

        service.execute(requestMock, responseMock);

        ArgumentCaptor<BaseUser> baseUserCaptor = ArgumentCaptor.forClass(BaseUser.class);
        verify(responseMock).iModel(baseUserCaptor.capture());
        assertEquals(baseUserCaptor.getValue().isCanManage(), expectedCanManage);
    }

    @DataProvider
    private static Object[][] canManageProvider() {
        return new Object[][] {
                {true,  false, true},
                {false, true,  true},
                {false, false, false}
        };
    }

    @AfterMethod
    public void resetMocks() {
        Mockito.reset(responseMock);
    }

    @AfterClass
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    private void prepareAuthorizationService() {
        when(securityService.isAdmin()).thenReturn(true);
        when(securityService.isUpdatableProfile()).thenReturn(true);
        when(securityService.requireProfileUnlock(anyString(), anyString())).thenReturn(false);
        when(securityService.requireProfilePassword()).thenReturn(false);
        when(securityService.isTransientUser()).thenReturn(true);
    }

    private void prepareAddonsManager() {
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginsAddon);
        when(addonsManager.isLicenseInstalled()).thenReturn(true);
        when(artifactoryContextMock.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @DataProvider
    public static Object[][] canDeployProvider() {
        return new Object[][] {
                {true, true},
                {true, false},
                {false, true},
                {false, false}
        };
    }
}