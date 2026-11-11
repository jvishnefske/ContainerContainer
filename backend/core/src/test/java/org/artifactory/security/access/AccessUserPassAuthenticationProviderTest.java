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

package org.artifactory.security.access;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.security.AnonymousAuthenticationToken;
import org.artifactory.security.UserInfo;
import org.artifactory.util.SecurityUtils;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.model.MessageModelCode;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.user.LoginRequest;
import org.jfrog.access.rest.user.UserWithGroupsResponse;
import org.jfrog.access.user.UserMFAStatus;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Noam Shemesh
 */
public class AccessUserPassAuthenticationProviderTest {

    private AccessUserPassAuthenticationProvider accessUserPassAuthenticationProvider;
    private AccessClient accessClient;
    private AuthClient authClient;
    private AccessService accessService;
    private CentralConfigService centralConfigService;
    private SecurityDescriptor securityDescriptor;
    private UsernamePasswordAuthenticationToken defaultAuth;
    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    @BeforeClass
    public void before() {
        authClient = createMock(AuthClient.class);
        accessClient = createMock(AccessClient.class);
        accessService = createMock(AccessService.class);
        centralConfigService = createMock(CentralConfigService.class);
        SecurityService securityService = createMock(SecurityService.class);
        final CentralConfigDescriptor configDescriptor = createMock(CentralConfigDescriptor.class);
        securityDescriptor = Mockito.mock(SecurityDescriptor.class);
        expect(configDescriptor.getSecurity()).andReturn(securityDescriptor).anyTimes();
        expect(centralConfigService.getDescriptor()).andReturn(configDescriptor).anyTimes();

        expect(accessClient.cachedAuth()).andReturn(authClient).anyTimes();

        expect(securityService.isAnonAccessEnabled()).andReturn(true).anyTimes();

        replay(accessClient, centralConfigService, configDescriptor, securityService);

        accessUserPassAuthenticationProvider =
                new AccessUserPassAuthenticationProvider(accessService, null, securityService);
        securityUtilsMockedStatic = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMockedStatic.when(SecurityUtils::isAnyExternalAuthEnabled).thenReturn(true);
    }

    @AfterClass
    public void after() {
        verify(accessClient);
        securityUtilsMockedStatic.close();
    }

    @BeforeMethod
    public void beforeMethod() {
        expect(accessService.getAccessClient()).andReturn(accessClient).anyTimes();
        defaultAuth = new UsernamePasswordAuthenticationToken("noam", "psswrd");
    }

    @AfterMethod
    public void afterMethod() {
        reset(accessService);
        reset(authClient);
        Mockito.reset(securityDescriptor);
    }

    @Test
    public void testAuthenticateSuccess() {
        expect(authClient.authenticate(anyObject(LoginRequest.class))).andReturn(
                new UserWithGroupsResponse("noam")
                        .status(UserStatus.ENABLED)
                        .customData(new HashMap<>())
                        .mfaStatus(UserMFAStatus.NONE)
        ).once();
        expect(accessService.isBasicAuthEnabled()).andReturn(true).anyTimes();
        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false).anyTimes();
        expect(accessService.isLdapEnabled()).andReturn(false).anyTimes();
        replay(authClient);
        replay(accessService);

        Authentication auth = accessUserPassAuthenticationProvider.authenticate(defaultAuth);

        assertTrue(auth.isAuthenticated());
        verify(authClient);
    }

    @Test(expectedExceptions = BadCredentialsException.class)
    public void testAuthenticateFailed() {
        String errorMessage = "not authed";
        expect(accessService.isLdapEnabled()).andReturn(false).anyTimes();
        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false).anyTimes();
        expect(authClient.authenticate(anyObject(LoginRequest.class)))
                .andThrow(new AccessClientHttpException(401, errorMessage)).once();
        expect(accessService.isBasicAuthEnabled()).andReturn(true).anyTimes();
        Mockito.when(securityDescriptor.isAnyExternalAuthEnabled()).thenReturn(true);
        replay(authClient);
        replay(accessService);

        accessUserPassAuthenticationProvider.authenticate(defaultAuth);
        verify(authClient);
    }

    @Test(expectedExceptions = LockedException.class)
    public void testAuthenticateForbidden() {
        String errorMessage = "{ \"errors\": [ { \"code\": \"" + MessageModelCode.CODE_TOO_MANY_REQUESTS + "\" } ] }";
        expect(authClient.authenticate(anyObject(LoginRequest.class)))
                .andThrow(new AccessClientHttpException(429, errorMessage)).once();
        expect(accessService.isBasicAuthEnabled()).andReturn(true).anyTimes();
        expect(accessService.isLdapEnabled()).andReturn(false).anyTimes();
        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false).anyTimes();
        Mockito.when(securityDescriptor.isAnyExternalAuthEnabled()).thenReturn(false);
        replay(authClient);
        replay(accessService);

        accessUserPassAuthenticationProvider.authenticate(defaultAuth);
        verify(authClient);
    }

    @Test(expectedExceptions = CredentialsExpiredException.class)
    public void testCredentialsExpired() {
        String errorMessage = "{ \"errors\": [ { \"code\": \"" + MessageModelCode.CODE_PASSWORD_EXPIRED + "\" } ] }";
        expect(authClient.authenticate(anyObject(LoginRequest.class))).andThrow(new AccessClientHttpException(403,
                errorMessage)).once();
        expect(accessService.isBasicAuthEnabled()).andReturn(true).anyTimes();
        expect(accessService.isLdapEnabled()).andReturn(false).anyTimes();
        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false).anyTimes();
        replay(authClient);
        replay(accessService);

        accessUserPassAuthenticationProvider.authenticate(defaultAuth);
        verify(authClient);
    }

    @Test(expectedExceptions = AuthenticationServiceException.class)
    public void shouldNotAuthenticateForEnabledExternalAuthAndDisabledBasicAuth() {
        Mockito.when(securityDescriptor.isAnyExternalAuthEnabled()).thenReturn(true);
        expect(accessService.isLdapEnabled()).andReturn(true).anyTimes();
        expect(accessService.isBasicAuthEnabled()).andReturn(false).anyTimes();
        replay(accessService);
        replay(authClient);

        accessUserPassAuthenticationProvider.authenticate(defaultAuth);

        verify(authClient);
    }

    @Test
    public void shouldAuthenticateAnonymousUserForEnabledExternalAuthAndDisabledBasicAuth() {
        expect(authClient.authenticate(anyObject(LoginRequest.class))).andReturn(
                new UserWithGroupsResponse(UserInfo.ANONYMOUS)
                        .status(UserStatus.ENABLED)
                        .customData(new HashMap<>())
                        .mfaStatus(UserMFAStatus.NONE)
        ).once();

        Mockito.when(securityDescriptor.isAnyExternalAuthEnabled()).thenReturn(true);
        expect(accessService.isLdapEnabled()).andReturn(true).anyTimes();
        expect(accessService.isAnyExternalAuthEnabled()).andReturn(true);
        expect(accessService.isBasicAuthEnabled()).andReturn(false).anyTimes();
        replay(authClient);
        replay(accessService);

        final Authentication authenticate = accessUserPassAuthenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(UserInfo.ANONYMOUS, ""));
        assertTrue(authenticate.isAuthenticated());
        verify(authClient);
    }

    @Test
    public void shouldAuthenticateForEnabledExternalAuthAndEnabledBasicAuth() {
        expect(authClient.authenticate(anyObject(LoginRequest.class))).andReturn(
                new UserWithGroupsResponse("noam")
                        .status(UserStatus.ENABLED)
                        .customData(new HashMap<>())
                        .mfaStatus(UserMFAStatus.NONE)
        ).once();
        Mockito.when(securityDescriptor.isAnyExternalAuthEnabled()).thenReturn(true);
        expect(accessService.isLdapEnabled()).andReturn(true).anyTimes();
        expect(accessService.isBasicAuthEnabled()).andReturn(true).anyTimes();
        replay(authClient);
        replay(accessService);

        final Authentication authenticate = accessUserPassAuthenticationProvider.authenticate(defaultAuth);
        assertTrue(authenticate.isAuthenticated());
        verify(authClient);
    }

    @Test
    public void shouldAuthenticateForDisabledExternalAuthAndEnabledBasicAuth() {
        expect(authClient.authenticate(anyObject(LoginRequest.class))).andReturn(
                new UserWithGroupsResponse("noam")
                        .status(UserStatus.ENABLED)
                        .customData(new HashMap<>())
                        .mfaStatus(UserMFAStatus.NONE)
        ).once();
        Mockito.when(securityDescriptor.isAnyExternalAuthEnabled()).thenReturn(false);
        expect(accessService.isLdapEnabled()).andReturn(false).anyTimes();
        expect(accessService.isBasicAuthEnabled()).andReturn(true).anyTimes();
        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false).anyTimes();
        replay(authClient);
        replay(accessService);

        final Authentication authenticate = accessUserPassAuthenticationProvider.authenticate(defaultAuth);
        assertTrue(authenticate.isAuthenticated());
        verify(authClient);
    }

    // RTDEV-24978
    @Test
    public void shouldNotCallAccessWhenAnonAccessDisabled() {
        SecurityService securityService = createMock(SecurityService.class);
        expect(securityService.isAnonAccessEnabled()).andReturn(true).anyTimes();
        expect(accessService.isLdapEnabled()).andReturn(false).anyTimes();
        expect(accessService.isBasicAuthEnabled()).andReturn(false).anyTimes();
        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false).anyTimes();
        replay(accessService);
        AccessUserPassAuthenticationProvider provider =
                new AccessUserPassAuthenticationProvider(accessService, null, securityService);
        final Authentication authentication = provider
                .authenticate(new UsernamePasswordAuthenticationToken(UserInfo.ANONYMOUS, ""));
        assertEquals(authentication.getClass(), AnonymousAuthenticationToken.class);
        assertTrue(authentication.isAuthenticated());
    }
}