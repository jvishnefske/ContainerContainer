/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.service.admin.security.auth.login;

import org.apache.commons.io.FileUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.api.config.ArtifactorySystemConfig;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.md.Properties;
import org.artifactory.model.security.UserImpl;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.ui.rest.model.admin.security.general.SecurityConfig;
import org.artifactory.ui.rest.model.admin.security.login.UserLogin;
import org.artifactory.ui.rest.model.admin.security.password.PasswordSettings;
import org.artifactory.ui.rest.model.admin.security.user.BaseUser;
import org.artifactory.ui.rest.service.admin.security.general.GetSecurityConfigService;
import org.artifactory.util.SecurityUtils;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Shay Bagants
 */
class LoginServiceTest {

    private final LoginService loginService = new LoginService();
    private File homedir;

    @Mock
    private ArtifactoryRestRequest requestMock;
    @Mock
    private ArtifactoryRestResponse responseMock;
    @Mock
    private HttpServletRequest servletRequestMock;
    @Mock
    private ArtifactoryContext artifactoryContextMock;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private GetSecurityConfigService getSecurityConfigService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private PluginsAddon pluginsAddon;
    @Mock
    private SecurityService securityService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor configDescriptor;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityDescriptor securityDescriptor;
    @Mock
    private MutableCentralConfigDescriptor centralConfig;
    @Mock
    private ArtifactorySystemConfig artifactorySystemConfig;
    @Mock
    private AccessService accessService;
    @Mock
    private AuthSettingsClient authSettingsClient;

    private AutoCloseable mockitoClosable;

    @BeforeEach
    public void beforeMethod() throws IOException {
        prepareHome();
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(getSecurityConfigService, "accessService", accessService);
        ReflectionTestUtils.setField(loginService, "userGroupService", userGroupService);
        ReflectionTestUtils.setField(loginService, "getSecurityConfigService", getSecurityConfigService);
        ReflectionTestUtils.setField(loginService, "accessService", accessService);
        when(requestMock.getServletRequest()).thenReturn(servletRequestMock);
        ReflectionTestUtils.setField(loginService, "addonsManager", addonsManager);
        when(artifactoryContextMock.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        when(configDescriptor.getSecurity()).thenReturn(securityDescriptor);
        ReflectionTestUtils.setField(loginService, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(loginService, "securityService", securityService);
        when(artifactoryContextMock.beanForType(SecurityService.class)).thenReturn(securityService);
        when(accessService.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        ArtifactoryContextThreadBinder.bind(artifactoryContextMock);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryHomeStub.unbind();
        ArtifactoryContextThreadBinder.unbind();
        FileUtils.deleteDirectory(homedir);
        this.mockitoClosable.close();
    }

    @Test
    void testLastLoginUpdateOnUiAuthentication() {
        when(servletRequestMock.getRemoteAddr()).thenReturn("10.0.0.1");

        UserLogin user = new UserLogin("myuser");
        user.setPassword("password");
        when(requestMock.getImodel()).thenReturn(user);

        prepareAuthenticationMock(user);
        prepareUserGroupServiceMock(user);
        SecurityConfig securityConfig = new SecurityConfig(true, false, false, true, new PasswordSettings(), null,
                true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        prepareAddonsManager();
        prepareAuthorizationService();


        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {

            loginService.doExecute(requestMock, responseMock);
            verify(getSecurityConfigService, times(1)).execute(requestMock, responseMock);
            // ensure last login time is called
            verify(securityService, times(1)).updateUserLastLogin(any(), anyLong(), any());
        }
    }

    @Test
    void testAnonymousLoginDisabledIfAccessIsDisabled() {
        when(servletRequestMock.getRemoteAddr()).thenReturn("10.0.0.1");

        UserLogin user = new UserLogin("anonymous");
        user.setPassword("password");
        when(requestMock.getImodel()).thenReturn(user);

        prepareAuthenticationMock(user);
        prepareUserGroupServiceMock(user);
        SecurityConfig securityConfig = new SecurityConfig(false, false, false, true, new PasswordSettings(), null,
                true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        prepareAddonsManager();
        prepareAuthorizationService();
        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {

            Assertions.assertThrows(AuthenticationServiceException.class, () -> {
                loginService.doExecute(requestMock, responseMock);
            });

        }
    }

    @Test
    void testUpperCaseAnonymousLoginDisabledIfAccessIsDisabled() {
        when(servletRequestMock.getRemoteAddr()).thenReturn("10.0.0.1");

        UserLogin user = new UserLogin("Anonymous");
        user.setPassword("password");
        when(requestMock.getImodel()).thenReturn(user);

        prepareAuthenticationMock(user);
        prepareUserGroupServiceMock(user);
        SecurityConfig securityConfig = new SecurityConfig(false, false, false, true, new PasswordSettings(), null,
                true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        prepareAddonsManager();
        prepareAuthorizationService();

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {

            Assertions.assertThrows(AuthenticationServiceException.class, () -> {
                loginService.doExecute(requestMock, responseMock);
            });
        }
    }

    @Test
    void testCrowdHeader() {
        when(servletRequestMock.getRemoteAddr()).thenReturn("10.0.0.1");

        UserLogin user = new UserLogin("myuser");
        user.setPassword("password");
        when(requestMock.getImodel()).thenReturn(user);

        prepareAuthenticationMock(user);
        prepareUserGroupServiceMock(user);
        SecurityConfig securityConfig = new SecurityConfig(true, false, false, true, new PasswordSettings(), null,
                true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        CrowdSetting crowdSettings = mock(CrowdSetting.class);
        when(crowdSettings.isEnable()).thenReturn(true);
        when(crowdSettings.getSessionValidationInterval()).thenReturn(2L);
        when(accessService.getAccessAuthSettingsClient().getCrowdSetting()).thenReturn(Optional.of(crowdSettings));
        prepareAddonsManager();
        prepareAuthorizationService();
        HttpServletResponse servletResponse = mock(HttpServletResponse.class);
        doNothing().when(servletResponse).addHeader(anyString(), anyString());
        when(responseMock.getServletResponse()).thenReturn(servletResponse);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {

            loginService.doExecute(requestMock, responseMock);
            verify(servletResponse, times(1)).addHeader(anyString(), anyString());
            verify(getSecurityConfigService, times(1)).execute(requestMock, responseMock);
            // ensure last login time is called
            verify(securityService, times(1)).updateUserLastLogin(any(), anyLong(), any());

        }
    }

    @Test
    void testExistingUserWrongPasswordLoginEnumeration() {
        when(servletRequestMock.getRemoteAddr()).thenReturn("10.0.0.1");

        UserLogin user = new UserLogin("admin");
        user.setPassword("wrong-pass");
        when(requestMock.getImodel()).thenReturn(user);

        prepareAuthenticationMock(user);
        prepareUserGroupServiceMock(user);
        prepareAddonsManager();
        prepareSecurityConfigService();
        UserLockPolicyConfig userLockPolicyConfig = mock(UserLockPolicyConfig.class);
        when(accessService.getUserLockPolicyConfig()).thenReturn(userLockPolicyConfig);
        when(userLockPolicyConfig.getAttempts()).thenReturn(2);
        when(responseMock.iModel(any())).thenCallRealMethod();
        when(responseMock.getIModel()).thenCallRealMethod();

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {

            loginService.doExecute(requestMock, responseMock);
        }
        BaseUser baseUser = (BaseUser) responseMock.getIModel();
        assertNull(baseUser.getCurrentPasswordValidFor());
    }

    @ParameterizedTest(name = "hasManage={0}, isResourceManager={1} → canManage={2}")
    @CsvSource({
            "true,  false, true",
            "false, true,  true",
            "false, false, false"
    })
    void testCanManage(boolean hasManagePermission, boolean isResourceManager, boolean expectedCanManage) {
        when(servletRequestMock.getRemoteAddr()).thenReturn("10.0.0.1");

        UserLogin user = new UserLogin("myuser");
        user.setPassword("password");
        when(requestMock.getImodel()).thenReturn(user);

        prepareAuthenticationMock(user);
        prepareUserGroupServiceMock(user);
        SecurityConfig securityConfig = new SecurityConfig(true, false, false, true, new PasswordSettings(), null,
                true, null, false, false);
        when(responseMock.getIModel()).thenReturn(securityConfig);
        prepareAddonsManager();
        prepareAuthorizationService();
        when(authorizationService.hasPermission(eq(ArtifactoryPermission.MANAGE), eq(true)))
                .thenReturn(hasManagePermission);
        when(securityService.isResourceManager()).thenReturn(isResourceManager);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            loginService.doExecute(requestMock, responseMock);
        }

        ArgumentCaptor<BaseUser> baseUserCaptor = ArgumentCaptor.forClass(BaseUser.class);
        verify(responseMock).iModel(baseUserCaptor.capture());
        assertEquals(expectedCanManage, baseUserCaptor.getValue().isCanManage());
    }

    private void prepareSecurityConfigService() {
        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        securityDescriptor.getPasswordSettings().getExpirationPolicy()
                .setCurrentPasswordValidFor(20);
        doCallRealMethod().when(getSecurityConfigService).execute(requestMock, responseMock);
        ReflectionTestUtils.setField(getSecurityConfigService, "centralConfigService", centralConfigService);
        ReflectionTestUtils.setField(getSecurityConfigService, "addonsManager", addonsManager);
        when(centralConfig.getSecurity()).thenReturn(securityDescriptor);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfig);

        CentralOAuthSettings yamlCentralOAuth = new CentralOAuthSettings(false, List.of());
        when(artifactorySystemConfig.getCentralOAuthDefaultConfig()).thenReturn(yamlCentralOAuth);
        when(centralConfigService.getSystemConfiguration()).thenReturn(artifactorySystemConfig);
        when(addonsManager.addonByType(CurationAddon.class)).thenReturn(mock(CurationAddon.class));
    }

    private void prepareUserGroupServiceMock(UserLogin user) {
        Properties props = Mockito.mock(Properties.class);
        when(props.get(anyString())).thenReturn(null);
        when(userGroupService.findPropertiesForUser(user.getUser())).thenReturn(props);
    }

    private void prepareAuthenticationMock(UserLogin user) {
        when(artifactoryContextMock.beanForType("authenticationManager", AuthenticationManager.class))
                .thenReturn(authenticationManager);
        Authentication authenticationMock = Mockito.mock(Authentication.class);
        when(authenticationMock.isAuthenticated()).thenReturn(true);
        when(authenticationMock.getPrincipal()).thenReturn(new SimpleUser(new UserImpl(user.getUser())));
        when(authenticationManager.authenticate(any())).thenReturn(authenticationMock);
    }

    private void prepareAddonsManager() {
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginsAddon);
        when(addonsManager.isLicenseInstalled()).thenReturn(true);
        when(artifactoryContextMock.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    private void prepareAuthorizationService() {
        when(securityService.isAdmin()).thenReturn(true);
        when(securityService.isUpdatableProfile()).thenReturn(true);
        when(securityService.requireProfileUnlock(anyString(), anyString())).thenReturn(false);
        when(securityService.requireProfilePassword()).thenReturn(false);
        when(securityService.isTransientUser()).thenReturn(true);
    }

    private void prepareHome() throws IOException {
        homedir = Files.createTempDirectory(this.getClass().getSimpleName()).toFile();
        ArtifactoryHomeStub ArtifactoryHomeStub = new ArtifactoryHomeStub(homedir.getAbsolutePath());
        ArtifactoryHomeStub.initArtifactorySystemProperties();
        ArtifactoryHomeStub.getArtifactoryProperties().setProperty("artifactory.security.disableRememberMe", "true");
        ArtifactoryHomeStub.getArtifactoryProperties().setProperty("version.query.enabled", "false");
        ArtifactoryHomeStub.bind(ArtifactoryHomeStub);
    }
}