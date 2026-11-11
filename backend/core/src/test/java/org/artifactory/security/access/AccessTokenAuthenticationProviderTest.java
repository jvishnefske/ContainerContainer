package org.artifactory.security.access;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LdapGroupAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.ldap.LdapUser;
import org.artifactory.api.security.ldap.LdapUserAttributes;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.model.security.GroupImpl;
import org.artifactory.model.security.UserImpl;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.security.ldap.LdapServiceImpl;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.ServiceType;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.model.Realm;
import org.jfrog.access.token.JwtAccessToken;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.naming.directory.Attributes;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.jfrog.access.common.AccessAuthz.ADMIN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author Shay Bagants
 */
@Test
public class AccessTokenAuthenticationProviderTest extends ArtifactoryHomeBoundTest {

    private AccessTokenAuthenticationProvider tokenAuthenticationProvider;

    @Mock
    private AccessService accessService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private LdapServiceImpl ldapService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private UserGroupStoreService userGroupStore;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);

        tokenAuthenticationProvider = new AccessTokenAuthenticationProvider();
        tokenAuthenticationProvider.setAccessService(accessService);
        tokenAuthenticationProvider.setUserGroupService(userGroupService);
        tokenAuthenticationProvider.setLdapService(ldapService);
        tokenAuthenticationProvider.setAddonsManager(addonsManager);
        tokenAuthenticationProvider.setUserGroupStore(userGroupStore);

        ServiceId serviceId = new ServiceId("jfrt", "xyz");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.loginFetchGroupsNyNames,
                Boolean.TRUE.toString());
        mocks.close();
    }

    @Test(dataProvider = "provideScopes")
    public void testSetAdminPrivileges(List<String> scopes, boolean expectedAdmin) {
        UserImpl user = new UserImpl();
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getScope()).thenReturn(scopes);
        tokenAuthenticationProvider.setAdminPrivileges(user, new UserImpl(), token);
        assertEquals(user.isAdmin(), expectedAdmin);
    }

    public void testSetAdminPrivilegesFromOriginalUser() {
        UserImpl user = new UserImpl();
        UserImpl userFromStorage = new UserImpl();
        userFromStorage.setAdmin(true);
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getScope()).thenReturn(ImmutableList.of("applied-permissions/user"));
        tokenAuthenticationProvider.setAdminPrivileges(user, userFromStorage, token);
        assertTrue(user.isAdmin());

        user = new UserImpl();
        when(token.getScope()).thenReturn(ImmutableList.of("applied-permissions/groups:group1"));
        tokenAuthenticationProvider.setAdminPrivileges(user, userFromStorage, token);
        // not an identity token, no admin rights
        assertFalse(user.isAdmin());

        user = new UserImpl();
        when(token.getScope())
                .thenReturn(ImmutableList.of("applied-permissions/user", "applied-permissions/groups:group1"));
        tokenAuthenticationProvider.setAdminPrivileges(user, userFromStorage, token);
        // both identity and non-identity scopes are combined
        assertTrue(user.isAdmin());
    }

    // RTDEV-92173: collectGroupNamesFromScope() used to be an if/else-if - when an
    // "applied-permissions/groups:" clause was present, any sibling "member-of-groups:" clause was
    // silently skipped, so the groups it named were never validated against the user's real
    // membership (see AccessServiceImpl#assertValidGroupsForNonAdmin). Both clause types must now
    // be collected whenever present, matching the union Access applies at enforcement time.
    @Test(dataProvider = "provideScopesForGroupCollection")
    public void testCollectGroupNamesFromScope(List<String> scope, Set<String> expectedGroups) {
        Set<String> actualGroups = AccessTokenAuthenticationProvider.collectGroupNamesFromScope(scope);
        assertEquals(actualGroups, expectedGroups);
    }

    @DataProvider
    public static Object[][] provideScopesForGroupCollection() {
        return new Object[][] {
                {ImmutableList.of("applied-permissions/groups:anchor-group"), Set.of("anchor-group")},
                {ImmutableList.of("member-of-groups:readers"), Set.of("readers")},
                // the combined-clause bypass scope from RTDEV-92173 Check 2a/3a
                {ImmutableList.of("applied-permissions/groups:anchor-group", "member-of-groups:readers"),
                        Set.of("anchor-group", "readers")},
                {ImmutableList.of("applied-permissions/groups:g1,g2", "member-of-groups:g3,g4"),
                        Set.of("g1", "g2", "g3", "g4")},
                {ImmutableList.of("applied-permissions/user"), Set.of()},
                {ImmutableList.of(), Set.of()},
                {null, Set.of()},
        };
    }

    @Test
    public void testGroupsPopulation() {
        getOrCreateArtifactoryHomeStub()
                .setProperty(ConstantValues.loginFetchGroupsNyNames, Boolean.FALSE.toString());

        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getScope()).thenReturn(ImmutableList.of(
                "applied-permissions/user",
                "applied-permissions/groups:group1,group2"));
        when(userGroupService.getAllGroups()).thenReturn(ImmutableList.of(
                new GroupImpl("group1"),
                new GroupImpl("group2"),
                new GroupImpl("notUsedGroup")));

        MutableUserInfo mutatedUser =
                tokenAuthenticationProvider.populateGroups(createJoeUser(), createJoeUser(), token);

        assertTrue(mutatedUser.getGroups().stream().anyMatch(group -> "group1".equals(group.getGroupName())));
        assertTrue(mutatedUser.getGroups().stream().anyMatch(group -> "group2".equals(group.getGroupName())));
        assertTrue(mutatedUser.getGroups().stream().noneMatch(group -> "notUsedGroup".equals(group.getGroupName())));
        verify(userGroupService, times(1)).getAllGroups();
        verify(userGroupService, times(0)).getAllGroupsByGroupNames(anyList());
    }

    @Test
    public void testGroupsPopulationWhenDoesNotExistInDB() {
        getOrCreateArtifactoryHomeStub()
                .setProperty(ConstantValues.loginFetchGroupsNyNames, Boolean.FALSE.toString());

        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getScope()).thenReturn(ImmutableList.of(
                "applied-permissions/user",
                "applied-permissions/groups:group1,group2"));
        when(userGroupService.getAllGroups()).thenReturn(ImmutableList.of(new GroupImpl("group1")));

        MutableUserInfo mutatedUser =
                tokenAuthenticationProvider.populateGroups(createJoeUser(), createJoeUser(), token);

        assertTrue(mutatedUser.getGroups().stream().anyMatch(group -> "group1".equals(group.getGroupName())));
        assertTrue(mutatedUser.getGroups().stream().noneMatch(group -> "group2".equals(group.getGroupName())));
        verify(userGroupService, times(1)).getAllGroups();
        verify(userGroupService, times(0)).getAllGroupsByGroupNames(anyList());
    }

    @Test
    public void testGroupsPopulationByNames() {
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getScope()).thenReturn(ImmutableList.of(
                "applied-permissions/user",
                "applied-permissions/groups:group1,group2"));
        when(userGroupService.getAllGroupsByGroupNames(anyList())).thenReturn(ImmutableMap.of(
                "group1", new GroupImpl("group1"),
                "group2", new GroupImpl("group2")));

        MutableUserInfo mutatedUser =
                tokenAuthenticationProvider.populateGroups(createJoeUser(), createJoeUser(), token);

        assertTrue(mutatedUser.getGroups().stream().anyMatch(group -> "group1".equals(group.getGroupName())));
        assertTrue(mutatedUser.getGroups().stream().anyMatch(group -> "group2".equals(group.getGroupName())));
        verify(userGroupService, times(1)).getAllGroupsByGroupNames(anyList());
        verify(userGroupService, times(0)).getAllGroups();
    }

    @Test
    public void testGroupsPopulationByNamesWhenDoesNotExistInDB() {
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getScope()).thenReturn(ImmutableList.of(
                "applied-permissions/user",
                "applied-permissions/groups:group1,group2"));
        when(userGroupService.getAllGroupsByGroupNames(anyList())).thenReturn(ImmutableMap.of(
                "group1", new GroupImpl("group1")));

        MutableUserInfo mutatedUser =
                tokenAuthenticationProvider.populateGroups(createJoeUser(), createJoeUser(), token);

        assertTrue(mutatedUser.getGroups().stream().anyMatch(group -> "group1".equals(group.getGroupName())));
        assertTrue(mutatedUser.getGroups().stream().noneMatch(group -> "group2".equals(group.getGroupName())));
        verify(userGroupService, times(1)).getAllGroupsByGroupNames(anyList());
        verify(userGroupService, times(0)).getAllGroups();
    }

    public void testNonLdapAuthenticate() {
        final String userName = "ldapadmin";
        String tokenValue = "tokenValue";
        JwtAccessToken token = mockToken(userName);
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        final Authentication authenticate =
                tokenAuthenticationProvider.authenticate(
                        new AccessTokenAuthentication(tokenValue, userName, List.of()));
        assertTrue(authenticate.isAuthenticated());
    }

    public void testUsernameNotLowercase() {
        final String userNameUpperCase = "Woohoo" + System.currentTimeMillis();
        final String userNameLowerCase = userNameUpperCase.toLowerCase();
        String tokenValue = "tokenValue";
        JwtAccessToken token = mockToken(userNameLowerCase);
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        final Authentication authenticate = tokenAuthenticationProvider
                .authenticate(new AccessTokenAuthentication(tokenValue, userNameUpperCase, List.of()));
        assertTrue(authenticate.isAuthenticated());
    }

    @Test(expectedExceptions = InternalAuthenticationServiceException.class,
            expectedExceptionsMessageRegExp = ".*'ldapadmin': user is locked, disabled or does not exist in LDAP")
    public void testLdapAuthenticationFailed() {
        final String userName = "ldapadmin";
        UserImpl userFromStorage = new UserImpl();
        userFromStorage.setRealm(Realm.LDAP.getName());
        userFromStorage.setAdmin(true);
        String tokenValue = "tokenValue";
        JwtAccessToken token = mockToken(userName);
        final LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setEnabled(false);
        mockLdapSettings(ldapSetting);
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        when(userGroupService.findUser(userName, false)).thenReturn(userFromStorage);
        when(userGroupService.findUser(userName)).thenReturn(userFromStorage);

        tokenAuthenticationProvider.authenticate(new AccessTokenAuthentication(tokenValue, userName, List.of()));
    }

    public void testLdapAuthenticationSucceeded() {
        final String userName = "ldapadmin";
        UserImpl userFromStorage = new UserImpl();
        userFromStorage.setRealm(Realm.LDAP.getName());
        userFromStorage.setAdmin(true);
        String tokenValue = "tokenValue";
        JwtAccessToken token = mockToken(userName);
        final LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setEnabled(true);
        mockLdapSettings(ldapSetting);
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        when(userGroupService.findUser(userName, false)).thenReturn(userFromStorage);
        when(userGroupService.findUser(userName)).thenReturn(userFromStorage);
        when(userGroupService.findOrCreateExternalAuthUser(userName, false, false)).thenReturn(userFromStorage);

        final LdapUserAttributes ldapUser = mockLdapUser();
        when(ldapService.getUserWithAttributesFromDomain(any(), eq(userName))).thenReturn(ldapUser);
        final LdapTemplate ldapTemplate = mock(LdapTemplate.class);
        when(ldapService.createLdapTemplate(any())).thenReturn(ldapTemplate);
        final DirContextOperations contextOperations = mock(DirContextOperations.class);
        when(ldapService.searchUserInLdap(ldapTemplate, userName, ldapSetting)).thenReturn(contextOperations);
        final LdapGroupAddon groupAddon = mock(LdapGroupAddon.class);
        when(addonsManager.addonByType(LdapGroupAddon.class)).thenReturn(groupAddon);
        final Authentication authenticate =
                tokenAuthenticationProvider.authenticate(
                        new AccessTokenAuthentication(tokenValue, userName, List.of()));
        assertTrue(authenticate.isAuthenticated());
    }

    @Test(dataProvider = "provideScopesForImpersonationToken")
    public void testLdapAuthenticationWithImpersonationToken(String issuer, List<String> scope, long expirationTime,
            String realm, boolean shouldRefreshLdapGroups) {
        final String userName = "ldapUser";
        UserImpl userFromStorage = new UserImpl();
        userFromStorage.setRealm(realm);
        userFromStorage.setUsername(userName);
        String tokenValue = "tokenValue";
        JwtAccessToken token = mockToken(userName);
        when(token.getIssuer()).thenReturn(issuer);
        when(token.getScope()).thenReturn(scope);
        when(token.getExpirationTime()).thenReturn(expirationTime);
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        final LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setAutoCreateUser(true);
        ldapSetting.setEnabled(true);
        mockLdapSettings(ldapSetting);
        when(userGroupService.findUser(userName, false)).thenReturn(userFromStorage);
        when(userGroupService.findUser(userName)).thenReturn(userFromStorage);
        when(userGroupService.findOrCreateExternalAuthUser(userName, !realm.equals(Realm.LDAP.getName()),
                false)).thenReturn(userFromStorage);
        final LdapUserAttributes ldapUser = mockLdapUser();
        when(ldapUser.getLdapUser().getDn()).thenReturn("userDN");
        when(ldapService.getUserWithAttributesFromDomain(any(), eq(userName))).thenReturn(ldapUser);
        final LdapTemplate ldapTemplate = mock(LdapTemplate.class);
        when(ldapService.createLdapTemplate(any())).thenReturn(ldapTemplate);
        final DirContextOperations contextOperations = mock(DirContextOperations.class);
        when(ldapService.searchUserInLdap(ldapTemplate, userName, ldapSetting)).thenReturn(contextOperations);
        final LdapGroupAddon groupAddon = mock(LdapGroupAddon.class);
        when(addonsManager.addonByType(LdapGroupAddon.class)).thenReturn(groupAddon);

        final Authentication authenticate =
                tokenAuthenticationProvider.authenticate(
                        new AccessTokenAuthentication(tokenValue, userName, List.of()));

        if (shouldRefreshLdapGroups) {
            verify(groupAddon, times(1)).populateGroups("userDN", userFromStorage);
        } else {
            verify(groupAddon, times(0)).populateGroups("userDN", userFromStorage);
        }
        assertTrue(authenticate.isAuthenticated());
    }

    public void testHeadLessLdapAuthenticationSucceeded() {
        homeStub.setProperty(ConstantValues.ldapApplyHeadlessRealmValidation, "false");
        final String userName = "ldapadmin";
        UserImpl userFromStorage = new UserImpl();
        userFromStorage.setRealm(Realm.LDAP.getName());
        userFromStorage.setAdmin(true);
        String tokenValue = "tokenValue";
        JwtAccessToken token = mockToken(userName);
        final LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setEnabled(true);
        mockLdapSettings(ldapSetting);
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        when(userGroupService.findUser(userName, false)).thenReturn(userFromStorage);
        when(userGroupService.findUser(userName)).thenReturn(userFromStorage);
        when(userGroupService.findOrCreateExternalAuthUser(userName, false, false)).thenReturn(userFromStorage);
        final UserInfo user = mock(UserInfo.class);
        when(userGroupStore.findUser(userName)).thenReturn(user);
        final LdapUserAttributes ldapUser = mockLdapUser();
        when(ldapService.getUserWithAttributesFromDomain(any(), eq(userName))).thenReturn(ldapUser);
        final LdapTemplate ldapTemplate = mock(LdapTemplate.class);
        when(ldapService.createLdapTemplate(any())).thenReturn(ldapTemplate);
        final DirContextOperations contextOperations = mock(DirContextOperations.class);
        when(ldapService.searchUserInLdap(ldapTemplate, userName, ldapSetting)).thenReturn(contextOperations);
        final LdapGroupAddon groupAddon = mock(LdapGroupAddon.class);
        when(addonsManager.addonByType(LdapGroupAddon.class)).thenReturn(groupAddon);
        final Authentication authenticate =
                tokenAuthenticationProvider.authenticate(
                        new AccessTokenAuthentication(tokenValue, userName, List.of()));
        assertTrue(authenticate.isAuthenticated());
    }

    @Test(expectedExceptions = InternalAuthenticationServiceException.class)
    public void testHeadLessLdapAuthenticationFailed() {
        homeStub.setProperty(ConstantValues.ldapApplyHeadlessRealmValidation, "true");
        final String userName = "ldapadmin";
        UserImpl userFromStorage = new UserImpl();
        userFromStorage.setRealm(Realm.LDAP.getName());
        userFromStorage.setAdmin(true);
        String tokenValue = "tokenValue";
        JwtAccessToken token = mockToken(userName);
        final LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setEnabled(true);
        mockLdapSettings(ldapSetting);
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        when(userGroupService.findUser(userName, false)).thenReturn(userFromStorage);
        when(userGroupService.findUser(userName)).thenReturn(userFromStorage);
        when(userGroupService.findOrCreateExternalAuthUser(userName, false, false)).thenReturn(userFromStorage);
        final UserInfo user = mock(UserInfo.class);
        when(userGroupStore.findUser(userName)).thenReturn(user);
        when(ldapService.getUserWithAttributesFromDomain(any(), eq(userName))).thenReturn(null);
        final LdapTemplate ldapTemplate = mock(LdapTemplate.class);
        when(ldapService.createLdapTemplate(any())).thenReturn(ldapTemplate);
        final DirContextOperations contextOperations = mock(DirContextOperations.class);
        when(ldapService.searchUserInLdap(ldapTemplate, userName, ldapSetting)).thenReturn(contextOperations);
        final LdapGroupAddon groupAddon = mock(LdapGroupAddon.class);
        when(addonsManager.addonByType(LdapGroupAddon.class)).thenReturn(groupAddon);
        final Authentication authenticate =
                tokenAuthenticationProvider.authenticate(
                        new AccessTokenAuthentication(tokenValue, userName, List.of()));
        assertTrue(authenticate.isAuthenticated());
    }

    @Test(dataProvider = "provideAppliedScopes")
    public void testAuthenticateAppliedGroupPermissionAndIdentityToken(List<String> tokenScope, boolean identity,
            boolean appliedPermissionsGroups) {
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getIssuer()).thenReturn("jffe@00000");
        when(token.getSubject()).thenReturn(ADMIN);
        when(token.getScope()).thenReturn(tokenScope);
        String tokenValue = "tokenValue";
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        final Authentication authenticate =
                tokenAuthenticationProvider.authenticate(
                        new AccessTokenAuthentication(tokenValue, ADMIN, List.of()));
        assertTrue(authenticate.isAuthenticated());
        SimpleUser principal = (SimpleUser) authenticate.getPrincipal();
        assertEquals(identity, principal.isIdentity());
        assertEquals(appliedPermissionsGroups, principal.isAppliedPermissionsGroups());
    }

    public void testTransientUser() {
        final String userName = "random-user";
        String tokenValue = "tokenValue";
        JwtAccessToken token = mockToken(userName);
        final TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(token);
        when(accessService.verifyAndGetResult(tokenValue)).thenReturn(verifyResult);
        final Authentication authenticate =
                tokenAuthenticationProvider.authenticate(
                        new AccessTokenAuthentication(tokenValue, userName, List.of()));
        assertTrue(authenticate.isAuthenticated());
        SimpleUser simpleUser = (SimpleUser) authenticate.getPrincipal();
        assertTrue(simpleUser.isTransientUser());
    }

    @Nonnull
    private LdapUserAttributes mockLdapUser() {
        final LdapUserAttributes ldapUser = mock(LdapUserAttributes.class);
        final Attributes attributes = mock(Attributes.class);
        final LdapUser ldapUserEntity = mock(LdapUser.class);
        when(ldapUser.getAttributes()).thenReturn(attributes);
        when(ldapUser.getLdapUser()).thenReturn(ldapUserEntity);
        return ldapUser;
    }

    private void mockLdapSettings(LdapSetting ldapSetting) {
        final MutableCentralConfigDescriptor mutableCentralConfig = mock(MutableCentralConfigDescriptor.class);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfig);
        final SecurityDescriptor securityDescriptor = mock(SecurityDescriptor.class);
        when(mutableCentralConfig.getSecurity()).thenReturn(securityDescriptor);
        when(accessService.getLdapSettings()).thenReturn(List.of(ldapSetting));
    }

    @Nonnull
    private JwtAccessToken mockToken(String userName) {
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getIssuer()).thenReturn(userName);
        when(token.getSubject()).thenReturn(userName);
        return token;
    }

    private UserImpl createJoeUser() {
        UserImpl user = new UserImpl();
        user.setUsername("joe");
        return user;
    }

    @DataProvider
    public static Object[][] provideScopes() {
        return new Object[][] {
                {ImmutableList.of("applied-permissions/admin"), true},
                {ImmutableList.of("api:*", "applied-permissions/admin"), true},
                {ImmutableList.of("applied-permissions/nonadmin"), false},
                {ImmutableList.of("applied-permissions/adminn"), false},
                {ImmutableList.of("applied-permissions:admin"), false},
                {ImmutableList.of("applied-permission/admin"), false},
                {ImmutableList.of("applied-permission"), false},
                {ImmutableList.of("admin"), false},
                {ImmutableList.of("applied-permissions/user"), false},
                {ImmutableList.of("jfrt@xyz:admin"), true},
                {ImmutableList.of("api:*", "jfrt@xyz:admin"), true},
                {ImmutableList.of("jfxr@xyz:admin"), false},
                {ImmutableList.of("api:*", "jfxr@xyz:admin"), false},
                {ImmutableList.of("jfrt@abcd:admin"), false},
                {ImmutableList.of("api:*", "jfrt@abcd:admin"), false},
                {ImmutableList.of("jfrt@xyz:user"), false},
                {ImmutableList.of("jfrt@xyz:user"), false},
        };
    }

    @DataProvider
    public Object[][] provideScopesForImpersonationToken() {
        long validExpiration =
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(homeStub.getArtifactoryProperties()
                        .getLongProperty(ConstantValues.tokenExpiryLimitToSkipRefreshGroupsSecs));
        long invalidExpiration =
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(homeStub.getArtifactoryProperties()
                        .getLongProperty(ConstantValues.tokenExpiryLimitToSkipRefreshGroupsSecs) + 10000);

        return new Object[][] {
                {ServiceType.FRONTEND + "@000", ImmutableList.of(
                        "internal:impersonation/user/",
                        "applied-permissions/user/"), validExpiration, Realm.LDAP.getName(), false},
                {ServiceType.FRONTEND + "@000", ImmutableList.of(
                        "applied-permissions/user/"), validExpiration, Realm.LDAP.getName(), true},
                {ServiceType.FRONTEND + "@000", ImmutableList.of(
                        "internal:impersonation/user/",
                        "applied-permissions/user/"), invalidExpiration, Realm.LDAP.getName(), true},
                {ServiceType.FRONTEND + "@000", ImmutableList.of(
                        "internal:impersonation/user/",
                        "applied-permissions/user/"), validExpiration, Realm.SAML.getName(), false},
                {ServiceType.ARTIFACTORY + "@000", ImmutableList.of(
                        "internal:impersonation/user/",
                        "applied-permissions/user"), validExpiration, Realm.LDAP.getName(), true},
                {ServiceType.ARTIFACTORY + "@000", ImmutableList.of(
                        "internal:impersonation/user/", "applied-permissions/user"), validExpiration, "", false}
        };
    }

    @DataProvider
    public static Object[][] provideAppliedScopes() {
        return new Object[][] {
                {ImmutableList.of("applied-permissions/user"), true, false},
                {ImmutableList.of("applied-permissions/groups:readers"), false, true},
                {ImmutableList.of("applied-permissions/groups:readers", "applied-permissions/user"), true, true},
                {ImmutableList.of("applied-permissions/admin"), false, false},
                {ImmutableList.of(""), false, false}
        };
    }
}