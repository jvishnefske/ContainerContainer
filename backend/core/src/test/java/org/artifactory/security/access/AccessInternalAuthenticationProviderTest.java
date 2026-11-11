package org.artifactory.security.access;

import org.artifactory.api.security.SecurityService;
import org.artifactory.security.InternalUsernamePasswordAuthenticationToken;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.access.providers.AccessInternalAuthenticationProvider;
import org.artifactory.util.SecurityUtils;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.client.auth.GrpcAuthClient;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.model.Realm;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.user.UserWithGroups;
import org.jfrog.access.user.UserMFAStatus;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class AccessInternalAuthenticationProviderTest {
    private AccessInternalAuthenticationProvider accessInternalAuthenticationProvider;
    private AccessClient accessClient;
    private AuthClient authClient;
    private GrpcAuthClient grpcAuthClient;
    @Mock
    private AccessService accessService;
    private AutoCloseable mocks;
    private MockedStatic<SecurityUtils> securityUtilsMockedStatic;

    @BeforeClass
    public void before() {
        mocks = MockitoAnnotations.openMocks(this);
        authClient = createMock(AuthClient.class);
        accessClient = createMock(AccessClient.class);
        grpcAuthClient = createMock(GrpcAuthClient.class);
        SecurityService securityService = createMock(SecurityService.class);
        expect(accessClient.cachedAuth()).andReturn(authClient).anyTimes();
        expect(securityService.isAnonAccessEnabled()).andReturn(true).anyTimes();
        securityUtilsMockedStatic = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMockedStatic.when(SecurityUtils::isAnyExternalAuthEnabled).thenReturn(true);
        replay(securityService);
        accessInternalAuthenticationProvider =
                new AccessInternalAuthenticationProvider(accessService, securityService);
    }

    @AfterClass
    public void afterClass() {
        securityUtilsMockedStatic.close();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(expectedExceptions = BadCredentialsException.class)
    void testAuthenticateFailed() {
        String errorMessage = "not authed";
        LoginResult loginResult = mock(LoginResult.class);
        when(accessService.ensureAuth(any())).thenThrow(new AccessClientHttpException(401, errorMessage))
                .thenReturn(loginResult);
        ;
        when(accessService.isLdapEnabled()).thenReturn(true);
        when(accessService.isAnyExternalAuthEnabled()).thenReturn(true);
        when(accessService.isBasicAuthEnabled()).thenReturn(false);
        Authentication auth = new UsernamePasswordAuthenticationToken("nitzanm", "password");
        Authentication authenticate = accessInternalAuthenticationProvider.authenticate(auth);
        replay(accessService.ensureAuth(any()));

    }

    @Test
    public void testAuthenticateSuccess() {
        LoginResult loginResult = mock(LoginResult.class);
        when(accessService.ensureAuth(any())).thenReturn(loginResult);
        expect(grpcAuthClient.authenticate(anyObject(String.class), anyObject(String.class))).andReturn(loginResult);
        UserPrincipal userPrincipal = mock(UserPrincipal.class);
        when(loginResult.isSuccessful()).thenReturn(true);
        when(loginResult.getPrincipal()).thenReturn(userPrincipal);
        UserWithGroups user = mock(UserWithGroups.class);
        when(userPrincipal.user()).thenReturn(user);
        when(userPrincipal.userProperties()).thenReturn(Set.of());
        UserStatus userStatus = mock(UserStatus.class);
        when(user.getStatus()).thenReturn(userStatus);
        when(user.getUsername()).thenReturn("nitzanm");
        when(userStatus.getName()).thenReturn("enabled");
        when(user.getMfaStatus()).thenReturn(UserMFAStatus.NONE);
        when(accessService.isBasicAuthEnabled()).thenReturn(true);
        when(accessService.isLdapEnabled()).thenReturn(false);
        Authentication auth = new UsernamePasswordAuthenticationToken("nitzanm", "password");
        Authentication authen = accessInternalAuthenticationProvider.authenticate(auth);
        assertTrue(authen.isAuthenticated());
    }

    @Test
    public void testUiLoginSuccess() {
        LoginResult loginResult = mock(LoginResult.class);
        doReturn(loginResult).when(accessService).ensureAuth(any());
        expect(grpcAuthClient.uiLogin(anyObject(String.class), anyObject(String.class))).andReturn(loginResult);
        UserPrincipal userPrincipal = mock(UserPrincipal.class);
        when(loginResult.isSuccessful()).thenReturn(true);
        when(loginResult.getPrincipal()).thenReturn(userPrincipal);
        UserWithGroups user = mock(UserWithGroups.class);
        when(userPrincipal.user()).thenReturn(user);
        when(userPrincipal.userProperties()).thenReturn(Set.of());
        UserStatus userStatus = mock(UserStatus.class);
        when(user.getStatus()).thenReturn(userStatus);
        when(user.getUsername()).thenReturn("nitzanm");
        when(userStatus.getName()).thenReturn("enabled");
        when(user.getMfaStatus()).thenReturn(UserMFAStatus.NONE);
        when(accessService.isBasicAuthEnabled()).thenReturn(true);
        when(accessService.isLdapEnabled()).thenReturn(false);
        Authentication auth = new InternalUsernamePasswordAuthenticationToken("nitzanm", "password");
        SecurityService securityService = createMock(SecurityService.class);
        expect(securityService.isAnonAccessEnabled()).andReturn(true).anyTimes();
        Authentication authen = accessInternalAuthenticationProvider.authenticate(auth);

        assertTrue(authen.isAuthenticated());
    }

    @Test(expectedExceptions = AccessClientException.class)
    public void testUiLoginNotSuccess() {
        LoginResult loginResult = mock(LoginResult.class);
        doReturn(loginResult).when(accessService).ensureAuth(any());
        expect(grpcAuthClient.uiLogin(anyObject(String.class), anyObject(String.class))).andReturn(loginResult);
        UserPrincipal userPrincipal = mock(UserPrincipal.class);
        when(loginResult.isSuccessful()).thenReturn(false);
        when(loginResult.getPrincipal()).thenReturn(null);
        UserWithGroups user = mock(UserWithGroups.class);
        when(userPrincipal.user()).thenReturn(user);
        when(userPrincipal.userProperties()).thenReturn(Set.of());
        UserStatus userStatus = mock(UserStatus.class);
        when(user.getStatus()).thenReturn(userStatus);
        when(user.getUsername()).thenReturn("nitzanm");
        when(userStatus.getName()).thenReturn("enabled");
        when(user.getMfaStatus()).thenReturn(UserMFAStatus.NONE);
        when(accessService.isBasicAuthEnabled()).thenReturn(true);
        when(accessService.isLdapEnabled()).thenReturn(false);
        Authentication auth = new InternalUsernamePasswordAuthenticationToken("nitzanm", "password");
        SecurityService securityService = createMock(SecurityService.class);
        expect(securityService.isAnonAccessEnabled()).andReturn(true).anyTimes();
        Authentication authen = accessInternalAuthenticationProvider.authenticate(auth);

        assertFalse(authen.isAuthenticated());
    }

    @Test
    void testProviderSupportTrue() {
        when(accessService.isRealmAuthenticationEnabled(Realm.INTERNAL)).thenReturn(true);
        Authentication auth = new InternalUsernamePasswordAuthenticationToken("nitzanm", "password");
        assertTrue(accessInternalAuthenticationProvider.supports(auth.getClass()));
    }

    @Test
    void testProviderSupportFalse() {
        when(accessService.isRealmAuthenticationEnabled(Realm.INTERNAL)).thenReturn(false);
        Authentication auth = new InternalUsernamePasswordAuthenticationToken("nitzanm", "password");
        assertFalse(accessInternalAuthenticationProvider.supports(auth.getClass()));
    }

    @Test(expectedExceptions = BadCredentialsException.class)
    public void testUiLoginWhenProviderfails() {
        doThrow(new AccessClientHttpException(401, "no authed")).when(accessService).ensureAuth(any());
        Authentication auth = new InternalUsernamePasswordAuthenticationToken("anonymous", "anonymous");
        accessInternalAuthenticationProvider.authenticate(auth);
    }

    @Test(expectedExceptions = AccessAuthenticationPreconditionException.class)
    public void testUiLoginBlocked() {
        doThrow(new AccessClientHttpException(400,
                "{\"errors\":[{\"code\":\"PRECONDITION_FAILED\",\"message\":\"UI Access is Disabled For This " +
                        "User\"}]}")).when(accessService).ensureAuth(any());
        Authentication auth = new InternalUsernamePasswordAuthenticationToken("anonymous", "anonymous");
        accessInternalAuthenticationProvider.authenticate(auth);
    }

    @Test
    public void testProviderRealm() {
        assertEquals(accessInternalAuthenticationProvider.getRealm(), Realm.INTERNAL.getName());
    }

    @Test
    public void testIsUserExists() {
        assertFalse(accessInternalAuthenticationProvider.userExists("benny"));
        assertFalse(accessInternalAuthenticationProvider.userExists("admin"));
    }

    @Test
    public void testAddexternalGroups() {
        Set<UserGroupInfo> groups = new HashSet<>();
        accessInternalAuthenticationProvider.addExternalGroups("daltry", groups);
        assertTrue(groups.isEmpty());
        accessInternalAuthenticationProvider.addExternalGroups("admin", groups);
        assertTrue(groups.isEmpty());
    }
}
