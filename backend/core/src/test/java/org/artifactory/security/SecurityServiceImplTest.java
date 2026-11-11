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

package org.artifactory.security;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.addon.sso.CentralOAuthSSOAddon;
import org.artifactory.addon.sso.HttpSsoAddon;
import org.artifactory.addon.sso.crowd.CrowdAddon;
import org.artifactory.addon.sso.saml.SamlSsoAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.mail.MailService;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.security.PasswordEncryptionFailureException;
import org.artifactory.api.security.ResetPasswordException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityListener;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.api.security.ldap.LdapService;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.PasswordExpirationPolicy;
import org.artifactory.descriptor.security.PasswordResetPolicy;
import org.artifactory.descriptor.security.PasswordSettings;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.oauth.CentralOAuthProviders;
import org.artifactory.descriptor.security.oauth.OAuthProviderSettings;
import org.artifactory.exception.InvalidNameException;
import org.artifactory.model.security.GroupImpl;
import org.artifactory.model.security.UserImpl;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.schedule.QuartzTask;
import org.artifactory.schedule.Task;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.access.emigrate.AccessConverters;
import org.artifactory.security.exceptions.PasswordChangeException;
import org.artifactory.security.interceptor.SecurityConfigurationChangesInterceptors;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.db.init.migration.MarkerMigrationHelper;
import org.artifactory.storage.db.init.migration.dao.MigrationStatusDao;
import org.artifactory.storage.db.init.migration.entity.DbMigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageServiceImpl;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.storage.security.service.UserLockInMemoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.util.NameValidator;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.config.ConfigClient;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.SamlSetting;
import org.jfrog.access.client.permission.GrpcAffectedEntitiesResponse;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.model.Realm;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.group.Group;
import org.jfrog.access.rest.system.EnabledRealmsConfig;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.jfrog.access.rest.user.UserResponse;
import org.jfrog.access.rest.user.UsersResponse;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.user.UserMFAStatus;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.security.crypto.CipherAlg;
import org.jfrog.security.file.SecurityFolderHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.quartz.CronTrigger;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.api.security.AuthorizationService.ROLE_ADMIN;
import static org.artifactory.api.security.SecurityService.USER_SYSTEM;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.artifactory.security.SecurityServiceImpl.PASSWORD_EXPIRY_NOTIFICATION_PROPERTY_DEPRECATED;
import static org.artifactory.security.UserMFAStatus.ENROLL;
import static org.artifactory.security.UserMFAStatus.NONE;
import static org.artifactory.security.UserMFAStatus.VERIFY;
import static org.artifactory.security.props.auth.OauthManager.OAUTH_INFO_PROPERTY;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.not;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verifyUnexpectedCalls;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


/**
 * SecurityServiceImpl unit tests.
 *
 * @author Yossi Shaul
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SecurityServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String SSH_KEY = "ssh.basictoken";
    private static final String PROJECT_A = "proj";
    private static final String PROJECT_B = "proj2";
    private static final RepoPath REPO = RepoPathFactory.create("my-repo");
    private static final RepoPath SHARED_REPO = RepoPathFactory.create("shared-repo");
    private static final String IS_SUPER_ADMIN_ERROR = "The user in test is super admin";
    private static final String IS_PROJECT_ADMIN_ERROR = "The user in test is project admin";
    private static final String NOT_A_PROJECT_ADMIN_ERROR = "The user in test is not a project admin";
    private static final String ACCESS_TOKEN =
            "eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiJheHlVMFFzc3J3S0p3ZllqMUtO" +
                    "bzNMbzZ6TnhLcnc3NEJ5YkVGVUJrSjB3In0" +
                    ".eyJzdWIiOiJqZnJ0QDAxZGtqMTR5bXNiamczMDRtbWJ4MXExbnNnXC91c2Vyc1wvYWxl" +
                    "eCIsInNjcCI6Im1lbWJlci1vZi1ncm91cHM6cmVhZGVycyBhcGk6KiIsImF1ZCI6ImpmcnRAMDFka" +
                    "2oxNHltc2JqZzMwNG1tYngxcTFu" +
                    "c2ciLCJpc3MiOiJqZnJ0QDAxZGtqMTR5bXNiamczMDRtbWJ4MXExbnNnXC91c2Vyc1wvYWRtaW4iL" +
                    "CJleHAiOjE1NjczMjkyMDAsImlh" +
                    "dCI6MTU2NzMyNTYwMCwianRpIjoiYzBhY2QzYjMtZWI4OS00M2NhLWIxNTMtY2FlZjk5OTMxOWFhIn0" +
                    ".DDACJum60qihTypKCZ7irlU1" +
                    "c6AfEmxqFZW5seYwxLsiumbxF3eKiVTHcCaWXNP3fOjsnRKUqie0AerJB7KLlhSSlH2Iu2tFK0tQThT" +
                    "h_Wzt_D27BD1K7ZQAaNLy5p-Leons9jK6HKaDk70wxK3FT08gHaN-HiOslfifbYA_gfOl0EeH5" +
                    "-iW6zOj2sK7d1ixqsNbqbbW4VGK1lF1A9YASFWirHgH8Oph0zA02cowyAWGZ6N7NA-oinyldIGhExN" +
                    "kOzQ5HwrWZHyplK6UZwtZ2aXWHihOBwcSjGklzSGqA4Xs1n5IBmlLcm__78HxUR0ptOW6U7R6mME0IZ8zJ3-uuA";

    private static final String ACCESS_TOKEN_WITH_INTERNAL_MC_SCOPE =
            "eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiJ3c3Q5RXdweXh0aEdZb202UXBRRV" +
                    "lVRkRmdnJtRWxBNDltbGlCZk94TXE0In0.eyJleHQiOiJ7XCJleGNoYW5nZV91cmxcIjpcImh0dHA6Ly9sb" +
                    "2NhbGhvc3Q6ODA0MC9hY2Nlc3MvYXBpL3YxL3NlcnZpY2VfdHJ1c3QvZXhjaGFuZ2VcIixcInJldm9jYWJsZVw" +
                    "iOnRydWV9Iiwic3ViIjoiamZtY0AwMWd5dnNkOWZrMnEzZTA3cWR5bXRkMXIzcCIsInNjcCI6ImludGVybmFsOnNl" +
                    "cnZpY2UtdHJ1c3QvcGFpcmluZy9taXNzaW9uLWNvbnRyb2w6eCBpbnRlcm5hbDptYzp4IGludGVybmFsOm1jOnIgc3" +
                    "lzdGVtOnBsYXRmb3JtY29uZmlnOnIgc3lzdGVtOmluZm86ciByZXBvOnJlcGxpY2F0aW9uOnIgcmVwbzpyZXBsaWNhd" +
                    "Glvbjp4IGludGVybmFsOmVudGl0bGVtZW50OnIgaW50ZXJuYWw6c2VydmljZS10cnVzdC9wYWlyaW5nL21pc3Npb24tY" +
                    "29udHJvbC1wZG46eCIsImF1ZCI6WyJqZmNvbkAqIiwiamZhY0AqIiwiamZydEAqIiwiamZ0cmtAKiIsImpmeGZlckAqIl" +
                    "0sImlzcyI6ImpmYWNAMDFneXZzZHpjZHI0NDQxNmF5cTdiMDE1ZjAiLCJleHAiOjE3MTM5NTAyNjMsImlhdCI6MTY4MjQxND" +
                    "I2MywianRpIjoiYzRjYzA0MWQtMmUyMS00ODg1LTk1ZW" +
                    "YtMjY5OGI5ZmJiYWIxIn0.fL_Zs_jU1tl4a421NsshCVU1qgCHNbzW" +
                    "XJynRL1cVuDmXvcFFiPvBi7yVX3LFPzGeY9zzN1Jqy7myFJW-vAeYRZySliTLLKrtrszAbG7kGZcwwNu0t49laJSXxw15dG0" +
                    "JvuECPl-tabI_41OcO-_m0kjJEqHMGA7n9gS8k8J82aUn_KgOJsqJtOBdtHQ85l2cFZY_sxZ_TdWKWAwM3NAGq3BRnySRs" +
                    "wwVFmWTntx62ugWR1XmqnXV_NbTVmPsaZLbdQCm8vOAGwblgeeWeSYdndoZaumR80o0JqPJxbCBB5XbqK4x45W2iEdz_yI" +
                    "gD5B2l8vEz2HAKcQReE60aL92Q";

    private SecurityContextImpl securityContext;
    private AccessService accessService;
    private AuthSettingsClient authSettingsClient;
    private SecurityServiceImpl service;
    private BuildFacade buildFacadeMock;
    private InternalCentralConfigService centralConfigServiceMock;
    private UserGroupStoreService userGroupStoreService;
    private ApiKeyManager apiKeyManager;
    private SecurityListener securityListenerMock;
    private UserPassAuthenticationProvider userPassAuthenticationProvider;
    private AccessConverters accessConverters;
    private MarkerMigrationHelper markerMigrationHelper;
    private SecurityConfigurationChangesInterceptors interceptors;
    private final MigrationStatusDao migrationStatusDao = Mockito.mock(MigrationStatusDao.class);
    private ProjectsService projectsService;
    private UserLockInMemoryService userLockInMemoryService;
    private ArtifactoryGroupCachingRepoImpl groupCachingRepo;
    private final List<DbMigrationStatus> mockDbStatusMigrations = Lists.newArrayList();
    private final Group groupMock = Mockito.mock(Group.class);
    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;

    public static Stream<Arguments> accessTokens() {
        return Stream.of(
                Arguments.of(ACCESS_TOKEN, false),
                Arguments.of(ACCESS_TOKEN_WITH_INTERNAL_MC_SCOPE, true));
    }

    @BeforeAll
    void initArtifactoryRoles() throws SQLException {
        staticAccessConfigHolder = mockStatic(AccessConfigHolder.class);
        AccessConfigHolder spyAccessConfigHolder = spy(AccessConfigHolder.class);
        staticAccessConfigHolder.when(AccessConfigHolder::getInstance).thenReturn(spyAccessConfigHolder);
        projectsService = mock(ProjectsService.class);
        buildFacadeMock = createBuildFacadeMock();
        centralConfigServiceMock = createMock(InternalCentralConfigService.class);
        userGroupStoreService = createMock(UserGroupStoreService.class);
        apiKeyManager = mock(ApiKeyManager.class);
        securityListenerMock = createMock(SecurityListener.class);
        userPassAuthenticationProvider = createMock(UserPassAuthenticationProvider.class);
        accessService = createMock(AccessService.class);
        authSettingsClient = createMock(AuthSettingsClient.class);
        markerMigrationHelper =
                new MarkerMigrationHelper(new MigrationStatusStorageServiceImpl(migrationStatusDao), homeStub);
        projectsService = mock(ProjectsService.class);
        interceptors = mock(SecurityConfigurationChangesInterceptors.class);
        MarkerMigrationHelper.bind(markerMigrationHelper);
        mockDaoRequests();
        accessConverters = new AccessConverters();
        userLockInMemoryService = createMock(UserLockInMemoryService.class);
    }

    @BeforeEach
    void setUp() {
        homeStub.setProperty(ConstantValues.allowAnyUpgrade, "7.0.0-m001");
        // create new security context
        securityContext = new SecurityContextImpl();
        SecurityContextHolder.setContext(securityContext);
        resetMockDbStatusMigrations();
        // new service instance
        service = spy(new SecurityServiceImpl());
        // set the aclManager mock on the security service
        ReflectionTestUtils.setField(service, "userGroupStoreService", userGroupStoreService);
        ReflectionTestUtils.setField(service, "apiKeyManager", apiKeyManager);
        ReflectionTestUtils.setField(service, "buildFacade", buildFacadeMock);
        ReflectionTestUtils.setField(service, "centralConfig", centralConfigServiceMock);
        ReflectionTestUtils.setField(service, "userPassAuthenticationProvider", userPassAuthenticationProvider);
        ReflectionTestUtils.setField(service, "accessConverters", accessConverters);
        ReflectionTestUtils.setField(service, "userLockInMemoryService", userLockInMemoryService);
        ReflectionTestUtils.setField(service, "markerMigrationHelper", markerMigrationHelper);
        ReflectionTestUtils.setField(service, "projectsService", projectsService);
        ReflectionTestUtils.setField(service, "interceptors", interceptors);
        ReflectionTestUtils.setField(service, "accessService", accessService);
        // reset mocks
        EasyMock.reset(centralConfigServiceMock, userLockInMemoryService, accessService, userGroupStoreService,
                authSettingsClient);
        Mockito.reset(apiKeyManager, interceptors);
        FileUtils.deleteQuietly(homeStub.getAccessUserCustomDataDecryptionMarkerFile());
    }

    @AfterEach
    void afterMethod() {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    @AfterAll
    static void afterAll() {
        staticAccessConfigHolder.close();
    }

    @Disabled
    @Test
    void currentUserEncryptedPasswordOrApiKey() {
        // Prepare regular user authentication
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(false).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        when(apiKeyManager.getToken(userName)).thenReturn(null);
        replay(accessService, userGroupStoreService, centralConfigServiceMock);

        setSimpleUserAuthentication(userName);
        // And expect password returning
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals("password", password);

        // Now switch to external user with AccessTokenAuthentication e.g. LDAP
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        Authentication authentication = mock(AccessTokenAuthentication.class);
        securityContext.setAuthentication(authentication);
        when(userInfoMock.isExternal()).thenReturn(true);
        when(userInfoMock.getRealm()).thenReturn(Realm.LDAP.getName());

        // For LDAP expect null (instead of password and JWT-token)
        password = service.currentUserEncryptedPasswordOrApiKey();
        assertNull(password);

        //Now switch to user with AccessToken
        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        securityContext.setAuthentication(accessTokenAuthentication);

        // And expect token returning
        password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals(ACCESS_TOKEN, password);

    }

    @Test
    void currentUserDidNotAuthenticated() {
        assertNull(service.currentUserEncryptedPasswordOrApiKey());
    }

    @Test
    void currentUserThrowPasswordEncryptionFailureException() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        when(userInfoMock.getPrivateKey()).thenReturn("privateKey");
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(true).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        when(apiKeyManager.getToken(userName)).thenReturn(null);

        when(userInfoMock.isExternal()).thenReturn(true);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        securityContext.setAuthentication(accessTokenAuthentication);
        String pass = (String) accessTokenAuthentication.getCredentials();
        expect(accessService.getAccessTokenType(pass)).andReturn(AccessTokenType.NOT_VALID_TOKEN).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);

        PasswordEncryptionFailureException passwordEncryptionFailureException =
                assertThrows(PasswordEncryptionFailureException.class,
                        () -> service.currentUserEncryptedPasswordOrApiKey());
        String exceptionMessage = """
        Failed to encrypt password. To retrieve a new encrypted password unlock your user profile page again.""";
        assertEquals(exceptionMessage, passwordEncryptionFailureException.getMessage());}

    @Test
    void currentUserEncryptedPasswordOrApiKeyWhenOIDC() {
        String userName = "token:oidcuser";
        setSimpleUserAuthentication(userName);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals("password", password);
    }

    @Test
    void currentUserEncryptedPasswordOrApiKeyIsNullWhenUserNotFound() {
        String userName = "notoidcuser";

        setSimpleUserAuthentication(userName);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertNull(password);
    }

    @Test
    void currentUserEncryptedPasswordOrApiKeyReturnsTokenWhenUserFound() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(false).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);
        when(userInfoMock.isExternal()).thenReturn(true);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        securityContext.setAuthentication(accessTokenAuthentication);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals(ACCESS_TOKEN, password);
        verifyNoInteractions(apiKeyManager);
    }

    @Test
    void currentUserEncryptedPasswordOrApiKeyReturnsTokenWhenUserFound2() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(true).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        when(apiKeyManager.getToken(userName)).thenReturn(null);

        when(userInfoMock.isExternal()).thenReturn(true);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        securityContext.setAuthentication(accessTokenAuthentication);
        String pass = (String) accessTokenAuthentication.getCredentials();
        expect(accessService.getAccessTokenType(pass)).andReturn(AccessTokenType.JWT_ACCESS_TOKEN).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals(ACCESS_TOKEN, password);
    }

    @Test
    void currentUserEncryptedPasswordOrApiKeyReturnsTokenWhenUserFound3() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(true).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        when(apiKeyManager.getToken(userName)).thenReturn(null);

        when(userInfoMock.isExternal()).thenReturn(true);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        securityContext.setAuthentication(accessTokenAuthentication);
        String pass = (String) accessTokenAuthentication.getCredentials();
        expect(accessService.getAccessTokenType(pass)).andReturn(AccessTokenType.REFERENCE_TOKEN).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals(ACCESS_TOKEN, password);
    }

    @Test
    void currentUserEncryptedPasswordOrApiKeyReturnsTokenWhenUserFound4() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(true).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();

        when(userInfoMock.isExternal()).thenReturn(true);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        securityContext.setAuthentication(accessTokenAuthentication);
        String pass = (String) accessTokenAuthentication.getCredentials();
        TokenKeyValue tokenKeyValue = mock(TokenKeyValue.class);
        when(tokenKeyValue.getToken()).thenReturn(pass);
        when(apiKeyManager.getToken(userName)).thenReturn(tokenKeyValue);
        expect(accessService.getAccessTokenType(pass)).andReturn(AccessTokenType.JWT_ACCESS_TOKEN).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals(ACCESS_TOKEN, password);
    }

    @Test
    void currentUserEncryptedPasswordOrApiKeyReturnsTokenWhenUserFound5() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(true).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();

        when(userInfoMock.isExternal()).thenReturn(false);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        securityContext.setAuthentication(accessTokenAuthentication);
        String pass = (String) accessTokenAuthentication.getCredentials();
        TokenKeyValue tokenKeyValue = mock(TokenKeyValue.class);
        when(tokenKeyValue.getToken()).thenReturn(pass);
        when(apiKeyManager.getToken(userName)).thenReturn(tokenKeyValue);
        expect(accessService.getAccessTokenType(pass)).andReturn(AccessTokenType.JWT_ACCESS_TOKEN).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals(ACCESS_TOKEN, password);
    }

    @Test
    void testPasswordNull() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        SimpleUser adminUser = createAdminUser(userName);
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(true).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        when(apiKeyManager.getToken(userName)).thenReturn(null);


        when(userInfoMock.isExternal()).thenReturn(true);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                adminUser, "", SimpleUser.USER_GAS);
        securityContext.setAuthentication(authenticationToken);

        String pass = (String) accessTokenAuthentication.getCredentials();

        expect(accessService.getAccessTokenType(pass)).andReturn(AccessTokenType.REFERENCE_TOKEN).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertNull(password);
    }

    @Test
    void testPasswordNull2() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        SimpleUser adminUser = createAdminUser(userName);
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(true).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        when(apiKeyManager.getToken(userName)).thenReturn(null);


        when(userInfoMock.isExternal()).thenReturn(false);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                adminUser, "", SimpleUser.USER_GAS);
        securityContext.setAuthentication(authenticationToken);

        String pass = (String) accessTokenAuthentication.getCredentials();

        expect(accessService.getAccessTokenType(pass)).andReturn(AccessTokenType.REFERENCE_TOKEN).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertNull(password);
    }


    @Test
    void testPasswordIsNotEncryptedIfSuppliedViaAPI() {
        String userName = "user1";
        String userEmail = "user1@gmail.com";
        SimpleUser adminUser = createAdminUser(userName);
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        expect(accessService.isEncryptionEnabled()).andReturn(true).anyTimes();
        expect(userGroupStoreService.findUser(eq(userName))).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        when(apiKeyManager.getToken(userName)).thenReturn(null);


        when(userInfoMock.isExternal()).thenReturn(true);

        AccessTokenAuthentication accessTokenAuthentication =
                (AccessTokenAuthentication) setAccessTokenAuthentication(userName, false);
        accessTokenAuthentication.setAuthenticated(true);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                adminUser, "AKpassword", SimpleUser.USER_GAS);
        securityContext.setAuthentication(authenticationToken);

        String pass = (String) accessTokenAuthentication.getCredentials();
        expect(accessService.getAccessTokenType(pass)).andReturn(AccessTokenType.REFERENCE_TOKEN).anyTimes();
        replay(accessService, userGroupStoreService, centralConfigServiceMock);
        String password = service.currentUserEncryptedPasswordOrApiKey();
        assertEquals("AKpassword", password);
    }

    @Test
    void isAdminOnAdminUser() {
        Authentication authentication = setAdminAuthentication();

        boolean admin = service.isAdmin();
        assertTrue(admin, "The user in test is admin");
        // un-authenticate
        authentication.setAuthenticated(false);
        admin = service.isAdmin();
        assertFalse(admin, "Unauthenticated token");
    }

    @Test
    void isAdminOnSimpleUser() {
        setSimpleUserAuthentication("isAdminOnSimpleUser");

        boolean admin = service.isAdmin();
        assertFalse(admin, "The user in test is not an admin");
    }

    @Test
    void isAuthenticatedAsSystemOnSystemUser() {
        assertTrue(isSystemUser(), "The default user is system");
        assertFalse(service.isAuthenticatedAsSystem(), "System user is currently not authenticated");
        service.authenticateAsSystem();
        assertTrue(service.isAuthenticatedAsSystem(), "System user is now authenticated");
        assertTrue(isSystemUser(), "The user in test is still system");
        // un-authenticate
        securityContext.getAuthentication().setAuthenticated(false);
        assertTrue(service.isAuthenticatedAsSystem(), "System authentication cannot be undone using this method");
        assertTrue(isSystemUser(), "The user in test is still system");
        securityContext.setAuthentication(null);
        assertFalse(service.isAuthenticatedAsSystem(), "System authentication removed");
        assertTrue(isSystemUser(), "The user in test is still system");
    }

    private boolean isSystemUser() {
        String userName = service.currentUsername();
        return SecurityService.USER_SYSTEM.equals(userName);
    }

    @Test
    void isAuthenticatedAsSystemOnSimpleUser() {
        setSimpleUserAuthentication("isSystemOnSimpleUser");
        assertFalse(service.isAuthenticatedAsSystem(), "The user in test is not system");
    }

    @ParameterizedTest
    @MethodSource("accessTokens")
    @Disabled
    void hasMcScopeToken(String token, boolean isMcToken) {
        AccessTokenAuthentication authenticationToken = new
                AccessTokenAuthentication(token, null, null);
        authenticationToken.setAuthenticated(true);
        securityContext.setAuthentication(authenticationToken);
        assertEquals(service.hasMcScope(), isMcToken);
    }

    @Test
    void hasMcScopeForNonAccessTokenAuth() {
        setProjectAdminAuthentication(true, false);
        assertFalse(service.hasMcScope());
    }

    @Test
    void isAdminOnProjectAdmin() {
        setProjectAdminAuthentication(true, false);
        boolean admin = service.isAdmin();
        assertFalse(admin, "The user in test is not an admin");
    }

    @Test
    void isProjectAdminOnSimpleUser() {
        setProjectAdminAuthentication(false, false);
        boolean projectAdmin = service.isProjectAdmin(PROJECT_A);
        assertFalse(projectAdmin, NOT_A_PROJECT_ADMIN_ERROR);
    }

    @Test
    void isResourceManagerOnSimpleUser() {
        setProjectAdminAuthentication(false, true);
        boolean resourceAdmin = service.isResourceManager();
        assertTrue(resourceAdmin);
    }

    @Test
    void isResourceManagerOnSimpleUser2() {
        setProjectAdminAuthentication(true, true);
        boolean resourceAdmin = service.isResourceManager();
        assertTrue(resourceAdmin);
    }

    @Test
    void isProjectAdminOnAdmin() {
        setAdminAuthentication();
        boolean projectAdmin = service.isProjectAdmin(PROJECT_A);
        assertTrue(projectAdmin, IS_SUPER_ADMIN_ERROR);
    }

    @Test
    void isProjectAdminOnProjectAdmin() {
        setProjectAdminAuthentication(true, false);
        boolean projectAdmin = service.isProjectAdmin(PROJECT_A);
        assertTrue(projectAdmin, IS_PROJECT_ADMIN_ERROR);
    }

    @Test
    void isProjectAdminOnResourceSimpleUser() {
        setProjectAdminAuthentication(false, false);
        boolean projectAdmin = service.isProjectAdmin(REPO);
        assertFalse(projectAdmin, NOT_A_PROJECT_ADMIN_ERROR);
    }

    @Test
    void isProjectAdminOnResourceAdminUser() {
        setAdminAuthentication();
        boolean projectAdmin = service.isProjectAdmin(REPO);
        assertTrue(projectAdmin, IS_SUPER_ADMIN_ERROR);
    }

    @Test
    void isProjectAdminOnResourceProjectAdminUser() {
        setProjectAdminAuthentication(true, false);
        boolean projectAdmin = service.isProjectAdmin(REPO);
        assertTrue(projectAdmin, IS_PROJECT_ADMIN_ERROR);
    }

    @Test
    void isAnyProjectAdminOnSimpleUser() {
        setProjectAdminAuthentication(false, false);
        boolean projectAdmin = service.isAnyProjectAdmin();
        assertFalse(projectAdmin, NOT_A_PROJECT_ADMIN_ERROR);
    }

    @Test
    void isAnyProjectAdminOnAdminUser() {
        setAdminAuthentication();
        boolean projectAdmin = service.isAnyProjectAdmin();
        assertTrue(projectAdmin, IS_PROJECT_ADMIN_ERROR);
    }

    @Test
    void isAnyProjectAdminOnProjectAdminUser() {
        setProjectAdminAuthentication(true, false);
        boolean projectAdmin = service.isAnyProjectAdmin();
        assertTrue(projectAdmin, IS_PROJECT_ADMIN_ERROR);
    }

    @Test
    void testIsValidAuthInCaseMfaEnabled() {
        setSimpleUserAuthentication("testUser", true);
        assertThrows(ForbiddenException.class, () -> service.assertValidAuthInCaseMfaEnabled());

        setSimpleUserAuthentication("testUser", false);
        Assertions.assertDoesNotThrow(() -> service.assertValidAuthInCaseMfaEnabled());

        setAccessTokenAuthentication("testUser", false);
        Assertions.assertDoesNotThrow(() -> service.assertValidAuthInCaseMfaEnabled());

        setAccessTokenAuthentication("testUser", true);
        Assertions.assertDoesNotThrow(() -> service.assertValidAuthInCaseMfaEnabled());
    }

    @Test
    void testResetPasswordWithExternalAuthFailedWhereUserPasswordDisabled() {
        String userName = "tester";
        String userEmail = "tester@gmail.com";
        UserInfo userInfoMock = mock(UserInfo.class);
        CentralConfigDescriptor centralConfigDescriptorMock = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptorMock = mock(SecurityDescriptor.class);
        PasswordSettings passwordSettingsMock = mock(PasswordSettings.class);
        PasswordResetPolicy passwordResetPolicyMock = mock(PasswordResetPolicy.class);
        when(centralConfigDescriptorMock.getSecurity()).thenReturn(securityDescriptorMock);
        when(securityDescriptorMock.getPasswordSettings()).thenReturn(passwordSettingsMock);
        when(passwordSettingsMock.getResetPolicy()).thenReturn(passwordResetPolicyMock);
        when(passwordResetPolicyMock.isEnabled()).thenReturn(false);
        when(userInfoMock.getEmail()).thenReturn(userEmail);
        when(userInfoMock.isUpdatableProfile()).thenReturn(true);
        when(userInfoMock.isPasswordDisabled()).thenReturn(true);
        expect(userGroupStoreService.findUser(userName)).andReturn(userInfoMock).anyTimes();
        expect(centralConfigServiceMock.getDescriptor()).andReturn(centralConfigDescriptorMock).anyTimes();
        replay(userGroupStoreService, centralConfigServiceMock);
        assertEquals(service.resetPassword(userName, null),
                "If you have permissions to reset a password, we will email you a code to reset your password. " +
                        "Copy the code to the code verification page.");
    }

    @Test
    void userPasswordMatches() {
        setSimpleUserAuthentication("user");

        assertTrue(service.userPasswordMatches("password"));
        assertFalse(service.userPasswordMatches(""));
        assertFalse(service.userPasswordMatches("Password"));
        assertFalse(service.userPasswordMatches("blabla"));
    }

    @Test
    protected void findOrCreateExternalAuthUserWithIllegalChars() {
        char[] forbiddenChars = NameValidator.getForbiddenChars();
        String userName = "testUserName";
        char charToReplace = 'a';
        for (char forbiddenChar : forbiddenChars) {
            userName = userName.replace(charToReplace, forbiddenChar);
            charToReplace = forbiddenChar;
            EasyMock.reset(userGroupStoreService);
            expect(userGroupStoreService.findUser(userName.toLowerCase())).andReturn(null).anyTimes();
            replay(userGroupStoreService);
            String finalUserName = userName;
            InvalidNameException thrown = assertThrows(InvalidNameException.class,
                    () -> service.findOrCreateExternalAuthUser(finalUserName, false),
                    "InvalidNameException was expected for " + finalUserName);

            assertEquals(
                    "Illegal name character: '" + forbiddenChar + "' at index 9: " + finalUserName +
                            " at index 9: " + finalUserName,
                    thrown.getMessage());
        }
        EasyMock.reset(userGroupStoreService);
    }

    public static Stream<Arguments> provideAccessDecryptionAndResourceTypeDataMarker() {
        return Stream.of(Arguments.of(new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v540.get(), "",
                                System.currentTimeMillis()),
                        new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()
                        ), false, true),
                Arguments.of(new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v400.get(), "",
                                System.currentTimeMillis()),
                        new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()
                        ), false, true),
                Arguments.of(new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()),
                        new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()
                        ), false, false),
                Arguments.of(new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v540.get(), "",
                                System.currentTimeMillis()),
                        new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()
                        ), true, true),
                Arguments.of(new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v400.get(), "",
                                System.currentTimeMillis()),
                        new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()
                        ), true, true),
                Arguments.of(new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v590.get(), "",
                                System.currentTimeMillis()),
                        new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()
                        ), true, true),
                Arguments.of(new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()),
                        new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "",
                                System.currentTimeMillis()
                        ), true, false));
    }

    @ParameterizedTest
    @MethodSource("provideAccessDecryptionAndResourceTypeDataMarker")
    void testIsInterestedDecryptProps(CompoundVersionDetails source, CompoundVersionDetails target,
            boolean createMarker, boolean expected) throws IOException, SQLException {
        if (createMarker) {
            FileUtils.write(homeStub.getAccessUserCustomDataDecryptionMarkerFile(), "", Charset.defaultCharset());
        }
        when(migrationStatusDao.findAllUnfinishedIn(any()))
                .thenReturn(Collections.emptyList());

        assertEquals(service.isInterested(source, target), expected);
    }

    @ParameterizedTest
    @MethodSource("provideAccessDecryptionAndResourceTypeDataMarker")
    void testIsInterestedResourceType(CompoundVersionDetails source, CompoundVersionDetails target,
            boolean createMarker, boolean expected) throws IOException, SQLException {
        if (createMarker) {
            FileUtils.write(homeStub.getAccessResourceTypeConverterMarkerFile(), "", Charset.defaultCharset());
        }
        when(migrationStatusDao.findAllUnfinishedIn(any()))
                .thenReturn(Collections.emptyList());

        assertEquals(service.isInterested(source, target), expected);
    }

    @Test
    void testGeneratePasswordResetKey() throws Exception {
        MailService mailService = mock(MailService.class);
        UserGroupStoreService storeService = mock(UserGroupStoreService.class);
        UserInfo userInfo = mock(UserInfo.class);
        when(userInfo.isUpdatableProfile()).thenReturn(true);
        when(userInfo.getEmail()).thenReturn("user@email.com");
        when(userInfo.getUsername()).thenReturn("username");
        when(storeService.findUser("username")).thenReturn(userInfo);
        service.setMailService(mailService);
        service.setUserGroupStoreService(storeService);

        service.generatePasswordResetKey("username", "remoteAddress");

        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> genPasswordCaptor = ArgumentCaptor.forClass(String.class);

        Mockito.verify(storeService).changeUserGenPassword(usernameCaptor.capture(), genPasswordCaptor.capture());

        assertEquals("username", usernameCaptor.getValue());
        assertTrue(StringUtils.isNotBlank(genPasswordCaptor.getValue()));

        Mockito.verify(mailService).sendMail(any(String[].class), anyString(), anyString());
    }

    @Test
    void testDecryptAllUserPropsWithOldEncryption() throws Exception {
        // unbind first, this test is already bound
        createAndSaveArtifactoryKey();
        AccessService accessServiceMock = Mockito.mock(AccessService.class);
        // dependency nightmare
        SecurityServiceImpl securityServiceImpl = new SecurityServiceImpl();
        injectField(securityServiceImpl, "accessService", accessServiceMock);
        // Prepare
        AccessClient accessClientMockWithData = Mockito.mock(AccessClient.class);
        UsersClient usersClient = Mockito.mock(UsersClient.class);
        when(accessClientMockWithData.cachedUsers()).thenReturn(usersClient);
        UsersResponse usersResponse = new UsersResponse();
        usersResponse.users(Collections.singletonList(new UserResponse("johnny")
                .status(UserStatus.ENABLED)
                .mfaStatus(UserMFAStatus.NONE)
                .addCustomData(SSH_KEY,
                        ArtifactoryHome.get().getArtifactoryEncryptionWrapper().encryptIfNeeded("notsecure"))));
        when(usersClient.findUsers()).thenReturn(usersResponse);
        when(accessServiceMock.getAccessClient()).thenReturn(accessClientMockWithData);

        securityServiceImpl.decryptAllUserProps();
        // Assert - the user should have been updated
        Mockito.verify(usersClient, times(1)).updateUser(any());
    }

    @Test
    void testDecryptAllUserPropsWithNoEncryptionAndSearchableProperty() throws Exception {
        // unbind first, this test is already bound
        createAndSaveArtifactoryKey();
        AccessService accessServiceMock = Mockito.mock(AccessService.class);
        // dependency nightmare
        SecurityServiceImpl securityServiceImpl = new SecurityServiceImpl();
        injectField(securityServiceImpl, "accessService", accessServiceMock);
        // Prepare
        AccessClient accessClientMockWithData = Mockito.mock(AccessClient.class);
        UsersClient usersClient = Mockito.mock(UsersClient.class);
        when(accessClientMockWithData.cachedUsers()).thenReturn(usersClient);
        UsersResponse usersResponse = new UsersResponse();
        usersResponse.users(Collections.singletonList(new UserResponse("johnny")
                .status(UserStatus.ENABLED)
                .mfaStatus(UserMFAStatus.NONE)
                .addCustomData(SSH_KEY, "notsecure")));
        when(usersClient.findUsers()).thenReturn(usersResponse);
        when(accessServiceMock.getAccessClient()).thenReturn(accessClientMockWithData);

        securityServiceImpl.decryptAllUserProps();
        // Assert - the user should not be updated
        Mockito.verify(usersClient, times(1)).updateUser(any());
    }

    @Test
    void testDecryptAllUserPropsWithNoEncryptionAndNonSearchableProperty() throws Exception {
        // unbind first, this test is already bound
        createAndSaveArtifactoryKey();
        AccessService accessServiceMock = Mockito.mock(AccessService.class);
        // dependency nightmare
        SecurityServiceImpl securityServiceImpl = new SecurityServiceImpl();
        injectField(securityServiceImpl, "accessService", accessServiceMock);
        // Prepare
        AccessClient accessClientMockWithData = Mockito.mock(AccessClient.class);
        UsersClient usersClient = Mockito.mock(UsersClient.class);
        when(accessClientMockWithData.cachedUsers()).thenReturn(usersClient);
        UsersResponse usersResponse = new UsersResponse();
        usersResponse.users(Collections.singletonList(new UserResponse("johnny")
                .status(UserStatus.ENABLED)
                .mfaStatus(UserMFAStatus.NONE)
                .addCustomData(SSH_KEY, "notsecure")));
        usersResponse.users(Collections.singletonList(new UserResponse("johnny")
                .status(UserStatus.ENABLED)
                .mfaStatus(UserMFAStatus.NONE)
                .addCustomData(SSH_KEY + "_shash", "whatever")));
        when(usersClient.findUsers()).thenReturn(usersResponse);
        when(accessServiceMock.getAccessClient()).thenReturn(accessClientMockWithData);

        securityServiceImpl.decryptAllUserProps();
        // Assert - the user should still be updated due to the searchable props being added
        Mockito.verify(usersClient, times(1)).updateUser(any());
    }

    private void injectField(SecurityService securityService, String fieldName, Object object)
            throws Exception {
        Field field = securityService.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(securityService, object);
    }

    private void createAndSaveArtifactoryKey() {
        ArtifactoryHome artifactoryHome = new ArtifactoryHomeStub(getClass().getName());
        artifactoryHome.getArtifactoryKey().delete();
        SecurityFolderHelper.createKeyFile(artifactoryHome.getArtifactoryKey(), CipherAlg.AES128);
    }

    @Test
    void updateLastLoginWithNotExistingUserTest() {
        // Make sure that if the user doesn't exists we stop the "updateLastLogin" process without  exception
        EasyMock.reset(userGroupStoreService);
        // Enable the update last login process "userLastAccessUpdatesResolutionSecs" must be greater or equals to "1"
        getBound().setProperty(ConstantValues.userLastAccessUpdatesResolutionSecs, "1");
        expect(userGroupStoreService.findUser("user")).andReturn(null).once();
        replay(userGroupStoreService);
        service.updateUserLastLogin("user", System.currentTimeMillis() + 1000, "momo");
        EasyMock.reset(userGroupStoreService);
    }

    @ParameterizedTest
    @MethodSource("anonymous")
    void testIsAnonymous(String username, boolean expected) {
        assertEquals(service.isAnonymousUser(username), expected);
    }

    public static Stream<Arguments> anonymous() {
        return Stream.of(
                Arguments.of("", false),
                Arguments.of(null, false),
                Arguments.of("asda:122", false),
                Arguments.of(UserInfo.ANONYMOUS, true),
                Arguments.of("token:" + UserInfo.ANONYMOUS, true),
                Arguments.of("token:saddkasnudnaoin", false));
    }

    @Test
    void testUserLastLoginTimeUpdateBuffer() {
        getBound().setProperty(ConstantValues.userLastAccessUpdatesResolutionSecs, "0");
        service.updateUserLastLogin("user", System.currentTimeMillis(), "127.0.0.1");
        getBound().setProperty(ConstantValues.userLastAccessUpdatesResolutionSecs,
                ConstantValues.userLastAccessUpdatesResolutionSecs.getDefValue());

        MutableUserInfo user = new UserInfoBuilder("user").build();
        user.setLastLoginTimeMillis(0);

        //Simulate No existing last login, expect an update
        expect(userGroupStoreService.findUser("user")).andReturn(user).times(1);
        long currentTimeMillis = System.currentTimeMillis();
        userGroupStoreService.updateUserLastLogin("user", currentTimeMillis, "127.0.0.1");
        EasyMock.expectLastCall();
        replay(userGroupStoreService);
        service.updateUserLastLogin("user", currentTimeMillis, "127.0.0.1");
        EasyMock.verify(userGroupStoreService);

        //Give a last login from the near past, expect no update
        long nearPastLogin = System.currentTimeMillis();
        EasyMock.reset(userGroupStoreService);
        user = new UserInfoBuilder("user").build();
        user.setLastLoginTimeMillis(nearPastLogin);
        expect(userGroupStoreService.findUser("user")).andReturn(user).once();
        replay(userGroupStoreService);
        service.updateUserLastLogin("user", nearPastLogin + 100L, "momo");
        EasyMock.verify(userGroupStoreService);

        //Give a last login from the future, expect an update
        EasyMock.reset(userGroupStoreService);
        expect(userGroupStoreService.findUser("user")).andReturn(user).times(1);
        userGroupStoreService.updateUserLastLogin("user", currentTimeMillis + 6000L, "127.0.0.1");
        EasyMock.expectLastCall();
        replay(userGroupStoreService);
        service.updateUserLastLogin("user", currentTimeMillis + 6000L, "127.0.0.1");
        EasyMock.verify(userGroupStoreService);

        // Give a last login from the future, expect an update (use an IPv6 Address)
        EasyMock.reset(userGroupStoreService);
        expect(userGroupStoreService.findUser("user")).andReturn(user).times(1);
        userGroupStoreService.updateUserLastLogin("user", currentTimeMillis + 10000L,
                "[2a05:d014:82c:c112:ecc8:404a:7917:dbc2]");
        EasyMock.expectLastCall();
        replay(userGroupStoreService);
        service.updateUserLastLogin("user", currentTimeMillis + 10000L,
                "[2a05:d014:82c:c112:ecc8:404a:7917:dbc2]");
        EasyMock.verify(userGroupStoreService);
    }

    @Test
    @Disabled
    void reloadNoExternalAuthProvidersAndBasicAuthDisabledForceEnable() {
        Set<SecurityListener> securityListeners = new TreeSet<>();
        securityListeners.add(securityListenerMock);
        ReflectionTestUtils.setField(service, "securityListeners", securityListeners);
        EasyMock.reset(securityListenerMock);
        securityListenerMock.onClearSecurity();
        expect(securityListenerMock.compareTo(securityListenerMock)).andReturn(0).anyTimes();
        replay(securityListenerMock);

        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);

        CentralConfigService centralConfigService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        when(applicationContext.getCentralConfig()).thenReturn(centralConfigService);
        when(applicationContext.beanForType(SecurityService.class)).thenReturn(service);
        when(centralConfigService.getDescriptor().getSecurity().isAnyExternalAuthEnabled()).thenReturn(false);

        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false);
        expect(accessService.isBasicAuthEnabled()).andReturn(false);
        Capture<Map<String, Object>> accessConfigCaptor = Capture.newInstance(CaptureType.FIRST);
        accessService.updateConfigurationModel(capture(accessConfigCaptor));
        replay(accessService);
        when(applicationContext.beanForType(AccessService.class)).thenReturn(accessService);

        ArtifactoryContextThreadBinder.bind(applicationContext);

        SecurityDescriptor newSecurityDescriptor = new SecurityDescriptor();
        SecurityDescriptor oldSecurityDescriptor = new SecurityDescriptor();
        oldSecurityDescriptor.setCentralOAuthProviders(new CentralOAuthProviders());

        CentralConfigDescriptor newConfigDescriptor = createMock(CentralConfigDescriptor.class);
        expect(newConfigDescriptor.getSecurity()).andReturn(newSecurityDescriptor).anyTimes();
        replay(newConfigDescriptor);

        CentralConfigDescriptor oldConfigDescriptor = createMock(CentralConfigDescriptor.class);
        expect(oldConfigDescriptor.getSecurity()).andReturn(oldSecurityDescriptor).anyTimes();
        replay(oldConfigDescriptor);

        expect(centralConfigServiceMock.getDescriptor()).andReturn(newConfigDescriptor).anyTimes();
        replay(centralConfigServiceMock);

        service.reload(oldConfigDescriptor, List.of());
        EasyMock.verify(securityListenerMock);

        String actualJsonSentToAccess =
                JsonUtils.getInstance().writeValueAsString(accessConfigCaptor.getValue());

        assertEquals(JsonParser.parseString(actualJsonSentToAccess), JsonParser.parseString("""
                {"security":{"authentication":{"basic-authentication-enabled":true}}}
                """));

        assertTrue(service.getAndResetForceEnabledBasicAuth());
        assertFalse(service.getAndResetForceEnabledBasicAuth());
    }

    @Test
    void testSelectiveReload() {
        TreeSet<SecurityListener> securityListeners = new TreeSet<>();
        securityListeners.add(securityListenerMock);
        ReflectionTestUtils.setField(service, "securityListeners", securityListeners);
        EasyMock.reset(securityListenerMock);
        securityListenerMock.onClearSecurity();
        expect(securityListenerMock.compareTo(securityListenerMock)).andReturn(0).anyTimes();
        replay(securityListenerMock);

        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);

        CentralConfigService centralConfigService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        when(applicationContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor().getSecurity().isAnyExternalAuthEnabled()).thenReturn(false);

        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false);
        expect(accessService.isBasicAuthEnabled()).andReturn(true);
        replay(accessService);
        when(applicationContext.beanForType(AccessService.class)).thenReturn(accessService);

        ArtifactoryContextThreadBinder.bind(applicationContext);

        SecurityDescriptor newSecurityDescriptor = new SecurityDescriptor();
        SecurityDescriptor oldSecurityDescriptor = new SecurityDescriptor();
        oldSecurityDescriptor.setCentralOAuthProviders(new CentralOAuthProviders());

        CentralConfigDescriptor newConfigDescriptor = createMock(CentralConfigDescriptor.class);
        expect(newConfigDescriptor.getSecurity()).andReturn(newSecurityDescriptor).anyTimes();
        replay(newConfigDescriptor);

        CentralConfigDescriptor oldConfigDescriptor = createMock(CentralConfigDescriptor.class);
        expect(oldConfigDescriptor.getSecurity()).andReturn(oldSecurityDescriptor).anyTimes();
        replay(oldConfigDescriptor);

        expect(centralConfigServiceMock.getDescriptor()).andReturn(newConfigDescriptor).anyTimes();
        replay(centralConfigServiceMock);

        service.reload(oldConfigDescriptor, List.of());
        EasyMock.verify(securityListenerMock);

        // The security conf is the same, so onClearSecurity should NOT be called
        service.reload(newConfigDescriptor, List.of());
        EasyMock.verify(securityListenerMock);
        ReflectionTestUtils.setField(service, "securityListeners", null);
    }

    @Test
    void createGroupWhichNotExistExpectCreatedAndGroupAdd() {
        final MutableGroupInfo groupInfo = new GroupImpl("testGroup");
        expect(userGroupStoreService.createGroup(groupInfo)).andReturn(groupMock).once();
        replay(userGroupStoreService);
        when(groupMock.getRealm()).thenReturn(Realm.INTERNAL);
        when(groupMock.getName()).thenReturn("testGroup");
        final Group expectedGroup = service.createGroup(groupInfo);
        assertEquals(groupMock, expectedGroup);
        Mockito.verify(interceptors).onGroupAdd("testGroup", Realm.INTERNAL.getName());
    }

    @Test
    void createGroupGetExceptionExpectInterceptorNotRun() {
        final MutableGroupInfo groupInfo = new GroupImpl("testGroup");
        expect(userGroupStoreService.createGroup(groupInfo))
                .andThrow(new AccessClientHttpException(409, "TestMessage")).once();
        replay(userGroupStoreService);
        assertThrows(AccessClientHttpException.class, () -> service.createGroup(groupInfo));
        Mockito.verify(interceptors, never())
                .onGroupAdd("testGroup", Realm.INTERNAL.getName());
    }

    @Test
    void updateGroupWhichExistExpectCreatedAndGroupAddInterceptorRun() {
        final MutableGroupInfo groupInfo = new GroupImpl("testGroup");
        when(groupMock.getRealm()).thenReturn(Realm.INTERNAL);
        when(groupMock.getName()).thenReturn("testGroup");
        expect(userGroupStoreService.updateGroup(groupInfo)).andReturn(groupMock).once();
        replay(userGroupStoreService);
        final Group expectedGroup = service.updateGroup(groupInfo);
        assertEquals(groupMock, expectedGroup);
        Mockito.verify(interceptors).onGroupUpdate("testGroup", Realm.INTERNAL.getName());
    }

    @Test
    void updateGroupGetExceptionExpectInterceptorNotRun() {
        final MutableGroupInfo groupInfo = new GroupImpl("testGroup");
        expect(userGroupStoreService.updateGroup(groupInfo))
                .andThrow(new AccessClientHttpException(409, "TestMessage"));
        replay(userGroupStoreService);
        assertThrows(AccessClientHttpException.class, () -> service.updateGroup(groupInfo));
        Mockito.verify(interceptors, never()).onGroupUpdate("testGroup", Realm.INTERNAL.getName());
    }

    private static SimpleUser createAdminUser(String name) {
        UserInfo userInfo = new UserInfoBuilder(name).admin(true).updatableProfile(true).build();
        return new SimpleUser(userInfo);
    }

    private SimpleUser createNonAdminUser(String username, boolean mfaEnabled, String... groups) {
        UserInfo userInfo = new UserInfoBuilder(username).updatableProfile(true)
                .mfaStatus(mfaEnabled ? VERIFY :
                        NONE)
                .internalGroups(new HashSet<>(Arrays.asList(groups))).build();
        return new SimpleUser(userInfo);
    }

    private Authentication setAdminAuthentication() {
        SimpleUser adminUser = createAdminUser("spiderman");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                adminUser, null, SimpleUser.ADMIN_GAS);
        securityContext.setAuthentication(authenticationToken);
        return authenticationToken;
    }

    private void setProjectAdminAuthentication(boolean enable, boolean enableResourceManager) {
        setProjectAdminAuthentication("user", enable, enableResourceManager);
    }

    private Authentication setSimpleUserAuthentication(String username, String... groups) {
        return setSimpleUserAuthentication(username, false, groups);
    }

    private Authentication setSimpleUserAuthentication(String username, boolean mfaEnabled, String... groups) {
        SimpleUser simpleUser = createNonAdminUser(username, mfaEnabled, groups);
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                simpleUser, "password", SimpleUser.USER_GAS);
        securityContext.setAuthentication(authenticationToken);
        return authenticationToken;
    }

    private Authentication setAccessTokenAuthentication(String username, boolean mfaEnabled, String... groups) {
        SimpleUser simpleUser = createNonAdminUser(username, mfaEnabled, groups);
        AccessTokenAuthentication authenticationToken = new AccessTokenAuthentication(ACCESS_TOKEN, simpleUser, null);
        securityContext.setAuthentication(authenticationToken);
        return authenticationToken;
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
            when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq("cool-repo"), Mockito.eq(PROJECT_B),
                    any())).thenReturn(true);
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
                Mockito.eq(PROJECT_A), any())).thenReturn(enable);
        when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq(SHARED_REPO.getRepoKey()), any()))
                .thenReturn(enable);
        when(projectsService.canDoToProjectRepoResource(any(), Mockito.eq(REPO.getRepoKey()), any()))
                .thenReturn(enable);
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

    @Test
    @Order(1)
    void changePasswordUsingIncorrectOldPassword() {
        SaltedPassword sp = new SaltedPassword("foo", "salt");

        UserInfo user = new UserImpl() {
            {
                setUsername("test");
                setUpdatableProfile(true);
                setPassword(sp);
            }
        };

        expect(userGroupStoreService.findUser("test")).andReturn(user).anyTimes();
        expect(userPassAuthenticationProvider.canUserLogin(EasyMock.anyObject(), EasyMock.anyObject()))
                .andReturn(false);

        MutableCentralConfigDescriptor mutableCentralConfigDescriptor = createMock(
                MutableCentralConfigDescriptor.class);
        PasswordExpirationPolicy expirationPolicy = new PasswordExpirationPolicy();
        expirationPolicy.setEnabled(Boolean.TRUE);

        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        securityDescriptor.setPasswordSettings(new PasswordSettings() {
            {
                setExpirationPolicy(expirationPolicy);
            }
        });

        expect(centralConfigServiceMock.getDescriptor()).andReturn(mutableCentralConfigDescriptor).anyTimes();
        expect(centralConfigServiceMock.getMutableDescriptor()).andReturn(mutableCentralConfigDescriptor).anyTimes();
        expect(mutableCentralConfigDescriptor.getSecurity()).andReturn(securityDescriptor).anyTimes();
        UserLockPolicyConfig userLockPolicyConfig = UserLockPolicyConfig.builder().attempts(0).build();
        expect(accessService.getUserLockPolicyConfig()).andReturn(userLockPolicyConfig).anyTimes();

        expect(userLockInMemoryService.getNextLoginTime("test")).andReturn(-1L).anyTimes();
        userLockInMemoryService.registerIncorrectLoginAttempt("test");
        EasyMock.expectLastCall();
        replay(userGroupStoreService, mutableCentralConfigDescriptor, centralConfigServiceMock, accessService);

        Exception exception = assertThrows(PasswordChangeException.class,
                () -> service.changePassword("test", "bar", "pass", "pass"));
        String expectedMessage = "Incorrect username/password or the user might be locked";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    @Order(2)
    void changePasswordForLockedOutUserTest() {
        SaltedPassword sp = new SaltedPassword("foo", "salt");

        UserInfo user = new UserImpl() {
            {
                setUsername("test");
                setUpdatableProfile(true);
                setPassword(sp);
            }
        };
        EasyMock.reset(userGroupStoreService);
        expect(userGroupStoreService.findUser("test")).andReturn(user).anyTimes();

        MutableCentralConfigDescriptor mutableCentralConfigDescriptor = createMock(
                MutableCentralConfigDescriptor.class);
        PasswordExpirationPolicy expirationPolicy = new PasswordExpirationPolicy();
        expirationPolicy.setEnabled(Boolean.TRUE);

        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        securityDescriptor.setPasswordSettings(new PasswordSettings() {
            {
                setExpirationPolicy(expirationPolicy);
            }
        });
        UserLockPolicyConfig userLockPolicyConfig = UserLockPolicyConfig.builder().attempts(2).build();
        expect(accessService.getUserLockPolicyConfig()).andReturn(userLockPolicyConfig).anyTimes();

        expect(centralConfigServiceMock.getDescriptor()).andReturn(mutableCentralConfigDescriptor).anyTimes();
        expect(centralConfigServiceMock.getMutableDescriptor()).andReturn(mutableCentralConfigDescriptor).anyTimes();
        expect(mutableCentralConfigDescriptor.getSecurity()).andReturn(securityDescriptor).anyTimes();
        expect(userGroupStoreService.isUserLocked("test")).andReturn(true);
        replay(userGroupStoreService, mutableCentralConfigDescriptor, centralConfigServiceMock, accessService);

        Exception exception = assertThrows(PasswordChangeException.class,
                () -> service.changePassword("test", "bar", "pass", "pass"));
        String expectedMessage = "Incorrect username/password or the user might be locked";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));
    }

    @ParameterizedTest
    @EnumSource(value = UserMFAStatus.class, names = {"VERIFY", "ENROLL"})
    void testChangeUserPasswordWhenMfaEnabledThrowsForbiddenExc(UserMFAStatus mfaStatus) {
        String userName = "user";
        UserInfo userInfo = mock(UserInfo.class);
        UserGroupStoreService storeService = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(storeService);

        when(storeService.findUser(userName)).thenReturn(userInfo);
        if (mfaStatus.equals(UserMFAStatus.VERIFY)) {
            when(userInfo.getMfaStatus()).thenReturn(VERIFY);
        }
        if (mfaStatus.equals(UserMFAStatus.ENROLL)) {
            when(userInfo.getMfaStatus()).thenReturn(ENROLL);
        }

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> {
            service.assertUserAllowedChangePassword(userName);
        });

        assertEquals(
                "Unable to change user's password due to multi-factor authentication is enabled for the user %s"
                        .formatted(userName), exception.getMessage());
    }

    @Test
    void testChangeUserPasswordWhenMfaChangeFlagEnabled() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.isMfaChangePasswordEnabled.getPropertyName(), String.valueOf(true));
        String userName = "user";
        UserGroupStoreService storeService = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(storeService);

        service.assertUserAllowedChangePassword(userName);

        verify(storeService, times(0)).findUser(userName);
    }

    @Test
    void testChangeUserPasswordWhenMfaDisabled() {
        String userName = "user";
        UserInfo userInfo = mock(UserInfo.class);
        UserGroupStoreService storeService = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(storeService);

        when(storeService.findUser(userName)).thenReturn(userInfo);
        when(userInfo.getMfaStatus()).thenReturn(NONE);

        service.assertUserAllowedChangePassword(userName);

        verify(storeService, times(1)).findUser(userName);
        verify(userInfo, times(1)).getMfaStatus();
    }

    @Test
    void testChangeUserPasswordWhenUserNotExist() {
        String userName = "user";
        UserGroupStoreService storeService = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(storeService);
        service.assertUserAllowedChangePassword(userName);

        verify(storeService, times(1)).findUser(userName);
        assertNull(storeService.findUser(userName));
    }

    @Test
    void testValidateResetPasswordAttempt() {
        PasswordResetPolicy policy = new PasswordResetPolicy() {
            {
                setEnabled(true);
                setMaxAttemptsPerAddress(3);
                setTimeToBlockInMinutes(60);
            }
        };
        PasswordSettings passwordSettings = new PasswordSettings() {
            {
                setResetPolicy(policy);
            }
        };
        SecurityDescriptor securityDescriptor = new SecurityDescriptor() {
            {
                setPasswordSettings(passwordSettings);
            }
        };

        PasswordResetPolicy oldPolicy = new PasswordResetPolicy();
        PasswordSettings oldPasswordSettings = new PasswordSettings() {
            {
                setResetPolicy(oldPolicy);
            }
        };
        SecurityDescriptor oldSecurityDescriptor = new SecurityDescriptor() {
            {
                setPasswordSettings(oldPasswordSettings);
            }
        };

        CentralConfigDescriptor oldConfigDescriptorMock = createMock(CentralConfigDescriptor.class);
        expect(oldConfigDescriptorMock.getSecurity()).andReturn(oldSecurityDescriptor).anyTimes();

        CentralConfigDescriptor configDescriptorMock = createMock(CentralConfigDescriptor.class);
        expect(centralConfigServiceMock.getDescriptor()).andReturn(configDescriptorMock).anyTimes();
        expect(configDescriptorMock.getSecurity()).andReturn(securityDescriptor).anyTimes();
        replay(centralConfigServiceMock, configDescriptorMock, oldConfigDescriptorMock);

        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);

        CentralConfigService centralConfigService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        when(applicationContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor().getSecurity().isAnyExternalAuthEnabled()).thenReturn(false);

        expect(accessService.isAnyExternalAuthEnabled()).andReturn(false);
        expect(accessService.isBasicAuthEnabled()).andReturn(true);
        replay(accessService);
        when(applicationContext.beanForType(AccessService.class)).thenReturn(accessService);

        ArtifactoryContextThreadBinder.bind(applicationContext);

        service.reload(oldConfigDescriptorMock, List.of());

        int timeBetweenAttempts = 600;
        String remoteAddress1 = "remote.address.1." + System.currentTimeMillis();

        service.validateResetPasswordAttempt(remoteAddress1);
        try {
            service.validateResetPasswordAttempt(remoteAddress1);
            fail("Validation should fail due to too frequent requests");
        } catch (ResetPasswordException e) {
            assertTrue(e.getMessage().toLowerCase().contains("too frequent"));
        }
        sleep(timeBetweenAttempts);
        service.validateResetPasswordAttempt(remoteAddress1);
        sleep(timeBetweenAttempts);
        service.validateResetPasswordAttempt(remoteAddress1);
        try {
            service.validateResetPasswordAttempt(remoteAddress1);
            fail("Validation should fail due to too many requests");
        } catch (ResetPasswordException e) {
            assertTrue(e.getMessage().toLowerCase().contains("too many"));
        }

        String remoteAddress2 = "remote.address.2." + System.currentTimeMillis();
        service.validateResetPasswordAttempt(remoteAddress2);
    }

    @Test
    void resetPasswordSuccess() {
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class, RETURNS_DEEP_STUBS);
        PasswordResetPolicy passwordResetPolicy = new PasswordResetPolicy();
        passwordResetPolicy.setEnabled(false);
        when(configDescriptor.getSecurity().getPasswordSettings().getResetPolicy()).thenReturn(passwordResetPolicy);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        UserImpl user = new UserImpl();
        user.setUsername("username");
        user.setEmail("user@jfrog.com");
        user.setUpdatableProfile(true);
        user.setPasswordDisabled(false);
        UserGroupStoreService groupStoreService = mock(UserGroupStoreService.class);
        when(groupStoreService.findUser("username")).thenReturn(user);
        MailService mailService = mock(MailService.class);
        service.setMailService(mailService);
        service.setCentralConfig(centralConfigService);
        service.setUserGroupStoreService(groupStoreService);

        service.resetPassword("username", "127.0.0.1");

        Mockito.verify(groupStoreService).changeUserGenPassword(anyString(), anyString());
        Mockito.verify(mailService).sendMail(any(String[].class), anyString(), anyString());
    }

    @Test
    void testAuthenticateAsSystem() {
        service.authenticateAsSystem();
        assertAuthenticatedAsSystem();
    }

    @Test
    void testDoAsSystem() {
        Authentication mockAuthentication = createMock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
        service.doAsSystem(this::assertAuthenticatedAsSystem);
        assertEquals(SecurityContextHolder.getContext().getAuthentication(), mockAuthentication,
                "original authentication was not restored");

        try {
            service.doAsSystem(() -> {
                throw new RuntimeException("expected exception");
            });
            fail("Exception was expected");
        } catch (Exception e) {
            //ignore expected exception
        }
        assertEquals(SecurityContextHolder.getContext().getAuthentication(), mockAuthentication,
                "original authentication was not restored");
    }

    /**
     * Test if user require password to unlock profile edit
     *
     * @param authClass             type of Authentication
     * @param allowSsoAuth          if Sso users are allowed to edit their profiles
     * @param allowSamlAuth         if saml users are allowed to edit their profiles
     * @param allowPropsAuth        if oauth users are allowed to edit their profiles
     * @param realm                 current user realm
     * @param authProviderName      current user auth provider name
     * @param requireUnlockExpected is password unlock is required
     */
    @ParameterizedTest
    @MethodSource("authProvider")
    void requireProfileUnlockTest(Class<Authentication> authClass, boolean allowSsoAuth, boolean allowSamlAuth,
            boolean allowPropsAuth, String realm, String authProviderName, boolean requireUnlockExpected) {
        ArtifactoryHome artifactoryHome = new ArtifactoryHomeStub(getClass().getName());
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryHome.bind(artifactoryHome);
        CentralConfigService configService = mock(CentralConfigService.class);
        CentralConfigDescriptorImpl descriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor security = new SecurityDescriptor();
        descriptor.setSecurity(security);
        when(configService.getDescriptor()).thenReturn(descriptor);

        OAuthProviderSettings googleProvider = new OAuthProviderSettings();
        googleProvider.setEnabled(true);
        googleProvider.setName("Google Central");
        CentralOAuthSSOAddon centralOAuthSSOAddon = mock(CentralOAuthSSOAddon.class);
        when(centralOAuthSSOAddon.getConfiguration()).thenReturn(
                new CentralOAuthSettings(true, List.of(googleProvider)));

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(CentralOAuthSSOAddon.class)).thenReturn(centralOAuthSSOAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        service.setAddons(addonsManager);
        when(context.getCentralConfig()).thenReturn(configService);

        ArtifactoryContextThreadBinder.bind(context);
        Authentication accessTokenAuthentication = mock(authClass);
        MutableUserInfo transientUser = new UserInfoBuilder("username").transientUser().build();
        SimpleUser simpleUser = new SimpleUser(transientUser);
        when(accessTokenAuthentication.getPrincipal()).thenReturn(simpleUser);
        securityContext.setAuthentication(accessTokenAuthentication);

        SamlSsoAddon samlSsoAddon = mock(SamlSsoAddon.class);
        if (authClass.equals(SamlTestAuthentication.class)) {
            when(samlSsoAddon.isSamlAuthentication()).thenReturn(true);
        }
        when(addonsManager.addonByType(SamlSsoAddon.class)).thenReturn(samlSsoAddon);
        HttpSsoAddon httpSsoAddon = mock(HttpSsoAddon.class);
        if (authClass.equals(HttpSsoTestAuthentication.class)) {
            when(httpSsoAddon.isHttpSsoAuthentication()).thenReturn(true);
        }
        when(addonsManager.addonByType(HttpSsoAddon.class)).thenReturn(httpSsoAddon);

        CrowdAddon crowdAddon = mock(CrowdAddon.class);
        if (authClass.equals(CrowdTestAuthentication.class)) {
            when(crowdAddon.isCrowdAuthenticationSupported(AuthenticationHelper.getAuthentication().getClass()))
                    .thenReturn(true);
        }
        when(addonsManager.addonByType(CrowdAddon.class)).thenReturn(crowdAddon);

        expect(accessService.getAccessAuthSettingsClient()).andReturn(authSettingsClient).anyTimes();

        HttpSsoSetting httpSsoSetting = HttpSsoSetting.builder()
                .allowUserToAccessProfile(allowSsoAuth)
                .build();
        expect(authSettingsClient.getHttpSsoSetting())
                .andReturn(Optional.of(httpSsoSetting)).anyTimes();
        CrowdSetting crowdSetting = CrowdSetting.builder()
                .allowUserToAccessProfile(allowSsoAuth)
                .build();
        expect(authSettingsClient.getCrowdSetting())
                .andReturn(Optional.of(crowdSetting)).anyTimes();

        SamlSetting samlSetting = SamlSetting.builder()
                .allowUserToAccessProfile(allowSamlAuth)
                .build();
        expect(accessService.getSamlSettings())
                .andReturn(samlSetting).anyTimes();
        String realmName = "null";
        if (realm != null && realm.equals("access ")) {
            realmName = realm;
        }
        expect(accessService.isRealmAuthenticationEnabled(Realm.SAML))
                .andReturn(realmName.equals("access saml")).anyTimes();

        OAuthGeneralSettings generalSettings = OAuthGeneralSettings.builder()
                .enable(true)
                .defaultSetting("default").build();
        List<OAuthSetting> oAuthSettings =
                List.of(OAuthSetting.builder().name("default").allowUserToAccessProfile(allowPropsAuth).build());
        expect(accessService.getOAuthGeneralSettings()).andReturn(generalSettings).anyTimes();

        expect(accessService.getAllOAuthSettings()).andReturn(oAuthSettings).anyTimes();

        replay(authSettingsClient);
        replay(accessService);

        boolean requireUnlock = service.requireProfileUnlock(realm, authProviderName);
        assertEquals(requireUnlock, requireUnlockExpected);
    }

    @Test
    void requireProfileUnlockWithCentralOAuthLoginTest() {
        ArtifactoryHome artifactoryHome = new ArtifactoryHomeStub(getClass().getName());
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryHome.bind(artifactoryHome);
        CentralConfigService configService = mock(CentralConfigService.class);
        CentralConfigDescriptorImpl descriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor security = new SecurityDescriptor();
        descriptor.setSecurity(security);
        when(configService.getDescriptor()).thenReturn(descriptor);

        OAuthProviderSettings googleProvider = new OAuthProviderSettings();
        googleProvider.setEnabled(true);
        googleProvider.setName("Google Central");
        CentralOAuthSSOAddon centralOAuthSSOAddon = mock(CentralOAuthSSOAddon.class);
        when(centralOAuthSSOAddon.getConfiguration()).thenReturn(
                new CentralOAuthSettings(true, List.of(googleProvider)));
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(CentralOAuthSSOAddon.class)).thenReturn(centralOAuthSSOAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        service.setAddons(addonsManager);
        when(context.getCentralConfig()).thenReturn(configService);
        ArtifactoryContextThreadBinder.bind(context);

        PropsAuthenticationToken accessTokenAuthentication = mock(PropsAuthenticationToken.class);
        when(accessTokenAuthentication.getPropsKey()).thenReturn(OAUTH_INFO_PROPERTY + "Google Central");
        MutableUserInfo transientUser = new UserInfoBuilder("username").transientUser().build();
        SimpleUser simpleUser = new SimpleUser(transientUser);
        when(accessTokenAuthentication.getPrincipal()).thenReturn(simpleUser);
        securityContext.setAuthentication(accessTokenAuthentication);

        SamlSsoAddon samlSsoAddon = mock(SamlSsoAddon.class);
        when(addonsManager.addonByType(SamlSsoAddon.class)).thenReturn(samlSsoAddon);
        HttpSsoAddon httpSsoAddon = mock(HttpSsoAddon.class);
        when(addonsManager.addonByType(HttpSsoAddon.class)).thenReturn(httpSsoAddon);
        CrowdAddon crowdAddon = mock(CrowdAddon.class);
        when(addonsManager.addonByType(CrowdAddon.class)).thenReturn(crowdAddon);

        HttpSsoSetting httpSsoSetting = HttpSsoSetting.builder()
                .allowUserToAccessProfile(false)
                .build();
        expect(accessService.getAccessAuthSettingsClient()).andReturn(authSettingsClient).anyTimes();
        expect(authSettingsClient
                .getHttpSsoSetting()).andReturn(Optional.of(httpSsoSetting)).anyTimes();

        CrowdSetting crowdSetting = CrowdSetting.builder()
                .build();
        expect(authSettingsClient.getCrowdSetting())
                .andReturn(Optional.of(crowdSetting)).anyTimes();

        SamlSetting samlSetting = SamlSetting.builder()
                .allowUserToAccessProfile(false)
                .build();
        expect(accessService
                .getSamlSettings()).andReturn(samlSetting).anyTimes();
        expect(accessService.isRealmAuthenticationEnabled(Realm.SAML))
                .andReturn(false).anyTimes();

        List<OAuthSetting> oAuthSettings =
                List.of(OAuthSetting.builder().name("default").allowUserToAccessProfile(false).build());
        expect(accessService.getAllOAuthSettings()).andReturn(oAuthSettings).anyTimes();

        replay(accessService);
        replay(authSettingsClient);

        boolean requireUnlock = service.requireProfileUnlock(null, null);
        assertFalse(requireUnlock);
    }

    @ParameterizedTest
    @MethodSource("profileUpdateProvider")
    void isProfileUpdatableTest(String requestedRealm, boolean userProfileUpdatable, String ldapSetting,
            boolean allowSsoAuth, boolean allowSamlAuth, boolean allowPropsAuth, boolean allowCrowdAuth,
            boolean profileUpdatableExpected) {
        ArtifactoryHome artifactoryHome = new ArtifactoryHomeStub(getClass().getName());
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryHome.bind(artifactoryHome);
        CentralConfigService configService = mock(CentralConfigService.class);
        CentralConfigDescriptorImpl descriptor = new CentralConfigDescriptorImpl();
        LdapService ldapService = mock(LdapService.class);
        service.setLdapService(ldapService);
        UserInfo userInfo = mock(UserInfo.class);
        when(userInfo.getRealm()).thenReturn(requestedRealm);
        when(userInfo.getUsername()).thenReturn("test");
        when(userInfo.isUpdatableProfile()).thenReturn(userProfileUpdatable);
        when(ldapService.getUserLdapSetting("test")).thenReturn(ldapSetting);
        SecurityDescriptor security = new SecurityDescriptor();
        descriptor.setSecurity(security);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(context.getCentralConfig()).thenReturn(configService);
        ArtifactoryContextThreadBinder.bind(context);

        SamlSetting samlSetting = SamlSetting.builder().
                allowUserToAccessProfile(allowSamlAuth)
                .build();
        expect(accessService.getSamlSettings())
                .andReturn(samlSetting).anyTimes();

        OAuthGeneralSettings generalSettings = OAuthGeneralSettings.builder()
                .enable(true)
                .defaultSetting("default").build();
        List<OAuthSetting> oAuthSettings =
                List.of(OAuthSetting.builder().name("default").allowUserToAccessProfile(allowPropsAuth).build());
        expect(accessService.getOAuthGeneralSettings()).andReturn(generalSettings).anyTimes();

        expect(accessService.getAllOAuthSettings()).andReturn(oAuthSettings).anyTimes();

        HttpSsoSetting httpSsoSetting = HttpSsoSetting.builder().
                allowUserToAccessProfile(allowSsoAuth)
                .build();
        expect(accessService.getAccessAuthSettingsClient()).andReturn(authSettingsClient);
        replay(accessService);
        expect(authSettingsClient.getHttpSsoSetting())
                .andReturn(Optional.of(httpSsoSetting)).anyTimes();

        CrowdSetting crowdSetting = CrowdSetting.builder()
                .allowUserToAccessProfile(allowCrowdAuth)
                .build();
        expect(authSettingsClient.getCrowdSetting())
                .andReturn(Optional.of(crowdSetting)).anyTimes();
        replay(authSettingsClient);

        boolean isProfileUpdatable = service.isProfileUpdatable(userInfo);
        assertEquals(isProfileUpdatable, profileUpdatableExpected);
    }

    @Test
    void isProfileUpdatableWithCentralOAuthTest() {
        ArtifactoryHome artifactoryHome = new ArtifactoryHomeStub(getClass().getName());
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryHome.bind(artifactoryHome);
        CentralConfigService configService = mock(CentralConfigService.class);
        CentralConfigDescriptorImpl descriptor = new CentralConfigDescriptorImpl();
        LdapService ldapService = mock(LdapService.class);
        when(ldapService.getUserLdapSetting("test")).thenReturn(null);
        service.setLdapService(ldapService);

        OAuthProviderSettings googleProvider = new OAuthProviderSettings();
        googleProvider.setEnabled(true);
        googleProvider.setName("Google Central");
        CentralOAuthSSOAddon centralOAuthSSOAddon = mock(CentralOAuthSSOAddon.class);
        when(centralOAuthSSOAddon.getConfiguration()).thenReturn(
                new CentralOAuthSettings(true, List.of(googleProvider)));
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(CentralOAuthSSOAddon.class)).thenReturn(centralOAuthSSOAddon);
        service.setAddons(addonsManager);

        PropsAuthenticationToken accessTokenAuthentication = mock(PropsAuthenticationToken.class);
        when(accessTokenAuthentication.getPropsKey()).thenReturn(OAUTH_INFO_PROPERTY + "Google Central");
        securityContext.setAuthentication(accessTokenAuthentication);

        SecurityDescriptor security = new SecurityDescriptor();
        descriptor.setSecurity(security);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(context.getCentralConfig()).thenReturn(configService);
        ArtifactoryContextThreadBinder.bind(context);

        List<OAuthSetting> oAuthSettings =
                List.of(OAuthSetting.builder().name("default").allowUserToAccessProfile(false).build());
        expect(accessService.getAllOAuthSettings()).andReturn(oAuthSettings).anyTimes();

        replay(accessService);


        UserInfo userInfo = mock(UserInfo.class);
        when(userInfo.getRealm()).thenReturn(OAuthHandler.REALM);
        when(userInfo.getUsername()).thenReturn("test");
        when(userInfo.isUpdatableProfile()).thenReturn(false);

        boolean isProfileUpdatable = service.isProfileUpdatable(userInfo);

        assertTrue(isProfileUpdatable);
    }

    @ParameterizedTest
    @MethodSource("isValidGenPasswordProvider")
    void isValidGenPassword(boolean expectedResult, String expirationValue) {
        homeStub.setProperty(ConstantValues.generatedPasswordExpirationPeriodMinutes, expirationValue);
        String genKey = ":" + System.currentTimeMillis() + ":";
        byte[] genKeyBytes = Base64.encodeBase64URLSafe(genKey.getBytes(Charsets.UTF_8));
        assertEquals(expectedResult, service.isValidGenPassword(new String(genKeyBytes, Charsets.UTF_8)));
    }

    @Test
    void testUpdateGroupUsers() {
        String groupName = "test-group";
        List<String> currentUsers = new ArrayList<>(List.of("user1", "user2"));
        List<String> givenUsers = new ArrayList<>(List.of("user1", "user3"));
        MutableGroupInfo mutableGroupInfo = mock(MutableGroupInfo.class);
        when(mutableGroupInfo.getGroupName()).thenReturn(groupName);
        expect(userGroupStoreService.findUsersInGroup(groupName)).andReturn(currentUsers);
        replay(userGroupStoreService);
        Mockito.doAnswer((Answer<?>) invocationOnMock -> {
            assert invocationOnMock.getArgument(0).toString().equals(groupName) &&
                    invocationOnMock.getArgument(1).equals(Set.of("user2"));
            return null;
        }).when(service).removeUsersFromGroup(any(), any());

        Mockito.doAnswer((Answer<?>) invocationOnMock -> {
            assert invocationOnMock.getArgument(0).toString().equals(groupName) &&
                    invocationOnMock.getArgument(1).equals(Set.of("user3"));
            return null;
        }).when(service).addUsersToGroup(any(), any());

        service.updateGroupUsers(mutableGroupInfo, givenUsers);
    }

    public static Stream<Arguments> isValidGenPasswordProvider() {
        return Stream.of(
                Arguments.of(true, "10"),
                Arguments.of(false, "-1"));
    }

    public static Stream<Arguments> authProvider() {
        return Stream.of(
                //Class<Authentication> authClass, boolean allowSsoAuth, boolean allowSamlAuth, boolean
                //allowPropsAuth, String realm, String authProviderName, boolean requireUnlockExpected
                Arguments.of(AccessTokenAuthentication.class, true, false, true, HttpSsoAddon.REALM, null, false),
                Arguments.of(AccessTokenAuthentication.class, false, false, true, HttpSsoAddon.REALM, null, true),
                Arguments.of(AccessTokenAuthentication.class, true, false, true, OAuthHandler.REALM, null, false),
                Arguments.of(AccessTokenAuthentication.class, false, false, false, OAuthHandler.REALM,
                        "Google Central",
                        false),
                Arguments.of(AccessTokenAuthentication.class, false, false, false, null, "other provider", true),
                Arguments.of(AccessTokenAuthentication.class, true, false, true, SamlSsoAddon.REALM, null, true),
                Arguments.of(AccessTokenAuthentication.class, false, true, false, SamlSsoAddon.REALM, null, false),
                Arguments.of(SamlTestAuthentication.class, false, true, false, "access saml", null, false),
                Arguments.of(PropsAuthenticationToken.class, false, false, true, null, null, false),
                Arguments.of(UsernamePasswordAuthenticationToken.class, true, false, false, HttpSsoAddon.REALM,
                        null,
                        true),
                Arguments.of(UsernamePasswordAuthenticationToken.class, false, true, false, SamlSsoAddon.REALM,
                        null,
                        true),
                Arguments.of(UsernamePasswordAuthenticationToken.class, false, false, true, OAuthHandler.REALM,
                        null,
                        true),
                Arguments.of(HttpSsoTestAuthentication.class, true, false, false, OAuthHandler.REALM, null, false),
                Arguments.of(HttpSsoTestAuthentication.class, false, false, true, OAuthHandler.REALM, null, true),
                Arguments.of(SamlTestAuthentication.class, false, true, false, OAuthHandler.REALM, null, false),
                Arguments.of(SamlTestAuthentication.class, false, false, true, OAuthHandler.REALM, null, true),
                Arguments.of(SamlTestAuthentication.class, false, true, false, CrowdAddon.REALM, null, false),
                Arguments.of(CrowdTestAuthentication.class, false, false, true, CrowdAddon.REALM, null, true),
                Arguments.of(CrowdTestAuthentication.class, false, false, false, LdapService.REALM, null, true)
        );
    }

    public static Stream<Arguments> profileUpdateProvider() {
        return Stream.of(
                //String requestedRealm, boolean userProfileUpdatable, String ldapSetting,
                //boolean allowSsoAuth, boolean allowSamlAuth, boolean allowPropsAuth, boolean allowCrowdAuth,
                // boolean profileUpdatableExpected
                Arguments.of("bla", true, null, false, false, false, false, true),
                Arguments.of(CrowdAddon.REALM, false, null, false, false, false, false, false),
                Arguments.of(CrowdAddon.REALM, false, null, false, false, false, true, true),
                Arguments.of(SamlSsoAddon.REALM, false, null, false, false, false, false, false),
                Arguments.of(SamlSsoAddon.REALM, false, null, false, true, false, false, true),
                Arguments.of(HttpSsoAddon.REALM, false, null, false, false, false, false, false),
                Arguments.of(HttpSsoAddon.REALM, false, null, true, false, false, false, true),
                Arguments.of(OAuthHandler.REALM, false, null, false, false, false, false, false),
                Arguments.of(OAuthHandler.REALM, false, null, false, false, true, false, true)
        );
    }

    private interface HttpSsoTestAuthentication extends Authentication {
    }

    private interface SamlTestAuthentication extends Authentication {
    }

    private interface CrowdTestAuthentication extends Authentication {
    }

    private void assertAuthenticatedAsSystem() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.getAuthorities().stream()
                        .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority())),
                "Authentication does not apply the admin role");
        assertEquals(USER_SYSTEM, ((UserDetails) authentication.getPrincipal()).getUsername());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void mockDaoRequests() throws SQLException {

        doAnswer(i -> {
            DbMigrationStatus dbMigrationStatus = i.getArgument(0);
            mockDbStatusMigrations.add(dbMigrationStatus);
            return null;
        })
                .when(migrationStatusDao)
                .insertMigration(any(DbMigrationStatus.class));

        doAnswer(i -> {
            String identifier = i.getArgument(0);
            mockDbStatusMigrations.removeIf(dbMigrationStatus ->
                    StringUtils.equals(dbMigrationStatus.getIdentifier(), identifier));
            return null;
        })
                .when(migrationStatusDao)
                .delete(Mockito.anyString());

        doAnswer(i -> {
            String identifier = i.getArgument(0);
            return StreamSupportUtils.stream(mockDbStatusMigrations)
                    .filter(dbMigrationStatus -> dbMigrationStatus.getIdentifier().equals(identifier))
                    .findFirst();
        })
                .when(migrationStatusDao)
                .findMigrationById(Mockito.anyString());

        when(migrationStatusDao.findAllUnfinishedIn(Mockito.anyList()))
                .thenAnswer(list ->
                        StreamSupportUtils.stream(mockDbStatusMigrations)
                                .filter(dbMigrationStatus ->
                                        ((List<String>) list).contains(dbMigrationStatus.getIdentifier())
                                                && dbMigrationStatus.getFinished() == 0)
                                .collect(Collectors.toList()));

        doAnswer(i -> {
            String identifier = i.getArgument(0);
            long finishTime = i.getArgument(1);
            Optional<DbMigrationStatus> optionalDbMigrationStatus = mockDbStatusMigrations.stream()
                    .filter(dbMigrationStatus -> dbMigrationStatus.getIdentifier().equals(identifier))
                    .findFirst();
            optionalDbMigrationStatus.ifPresent(dbMigrationStatus -> dbMigrationStatus.setFinished(finishTime));
            return null;
        }).when(migrationStatusDao).updateFinishTimeById(Mockito.anyString(), Mockito.anyLong());
    }

    private void resetMockDbStatusMigrations() {
        mockDbStatusMigrations.clear();
    }

    @Test
    void testCreatePasswordExpireNotificationJob() {
        Task passwordExpireNotificationJob = service.createPasswordExpireNotificationJob();
        Trigger trigger = ((QuartzTask) passwordExpireNotificationJob).getTrigger();
        assertInstanceOf(CronTrigger.class, trigger);
    }

    @Test
    void testCreatePasswordExpireNotificationJobWithDeprecatedProperty() {
        System.setProperty(PASSWORD_EXPIRY_NOTIFICATION_PROPERTY_DEPRECATED, "120");

        try {
            Task passwordExpireNotificationJob = service.createPasswordExpireNotificationJob();
            Trigger trigger = ((QuartzTask) passwordExpireNotificationJob).getTrigger();
            assertInstanceOf(SimpleTrigger.class, trigger);
        } finally {
            System.clearProperty(PASSWORD_EXPIRY_NOTIFICATION_PROPERTY_DEPRECATED);
        }
    }

    @Test
    void isProjectAdminWithProjectToken() {
        AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
        when(accessTokenAuthentication.isAuthenticated()).thenReturn(true);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        when(projectsService.isProjectAdmin(jwtAccessToken, "cool-project")).thenReturn(true);
        SimpleUser simpleUser = mock(SimpleUser.class);
        when(simpleUser.isEffectiveAdmin()).thenReturn(false);
        when(accessTokenAuthentication.getPrincipal()).thenReturn(simpleUser);
        securityContext.setAuthentication(accessTokenAuthentication);
        boolean projectAdmin = service.isProjectAdmin("cool-project");
        assertTrue(projectAdmin);
    }

    @Test
    void isProjectAdminWithProjectTokenNullProject() {
        AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
        when(accessTokenAuthentication.isAuthenticated()).thenReturn(true);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        when(projectsService.isProjectAdmin(jwtAccessToken, null)).thenReturn(true);
        SimpleUser simpleUser = mock(SimpleUser.class);
        when(simpleUser.isEffectiveAdmin()).thenReturn(false);
        when(accessTokenAuthentication.getPrincipal()).thenReturn(simpleUser);
        securityContext.setAuthentication(accessTokenAuthentication);
        boolean projectAdmin = service.isProjectAdmin((String) null);
        assertFalse(projectAdmin);
    }

    @Test
    void isAnyProjectAdminWithProjectToken() {
        AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
        when(accessTokenAuthentication.isAuthenticated()).thenReturn(true);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        when(projectsService.isAnyProjectAdmin(jwtAccessToken)).thenReturn(true);
        SimpleUser simpleUser = mock(SimpleUser.class);
        when(simpleUser.isEffectiveAdmin()).thenReturn(false);
        when(accessTokenAuthentication.getPrincipal()).thenReturn(simpleUser);
        securityContext.setAuthentication(accessTokenAuthentication);
        boolean projectAdmin = service.isAnyProjectAdmin();
        assertTrue(projectAdmin);
    }

    @Test
    void testRegisterIncorrectLoginAttemptDelegatedToAccess() {
        expect(accessService.getUserLockPolicyConfig()).andReturn(
                UserLockPolicyConfig.builder().clientLoginSuspensionEnabled(true).build()).anyTimes();
        replay(accessService);
        replay(userLockInMemoryService);
        service.registerIncorrectLoginAttempt("user", ClockUtils.epochMillis() - 200);
        verifyUnexpectedCalls(userLockInMemoryService);
    }

    @Test
    void testEnsureLoginShouldNotBeDelayedDelegatedToAccess() {
        expect(accessService.getUserLockPolicyConfig()).andReturn(
                UserLockPolicyConfig.builder().clientLoginSuspensionEnabled(true).build()).anyTimes();
        replay(accessService);
        replay(userLockInMemoryService);
        service.ensureLoginShouldNotBeDelayed("user");
        verifyUnexpectedCalls(userLockInMemoryService);
    }

    @Test
    void testInterceptLoginFailureDelegatedToAccess() {
        String realuser = "realuser";
        expect(accessService.getUserLockPolicyConfig()).andReturn(
                UserLockPolicyConfig.builder().clientLoginSuspensionEnabled(true).build()).anyTimes();
        expect(userGroupStoreService.findUser(eq(realuser))).andReturn(new UserImpl(realuser)).once();
        replay(accessService, userLockInMemoryService, userGroupStoreService);
        service.interceptLoginFailure(realuser, ClockUtils.epochMillis() - 200);
        verifyUnexpectedCalls(userLockInMemoryService);
    }

    @Test
    void testInterceptLoginFailureMissingUserDelegatedToAccess() {
        expect(accessService.getUserLockPolicyConfig()).andReturn(
                UserLockPolicyConfig.builder().clientLoginSuspensionEnabled(true).build()).anyTimes();
        replay(accessService);
        replay(userLockInMemoryService);
        service.interceptLoginFailure("user", ClockUtils.epochMillis() - 200);
        verifyUnexpectedCalls(userLockInMemoryService);
    }

    @ParameterizedTest
    @MethodSource("testGetMfaStatusData")
    void testGetMfaStatus(boolean authenticationExist) {
        if (authenticationExist) {
            setSimpleUserAuthentication(new UserImpl("cool-user"));
        }
        assertEquals(service.getMfaStatus(), org.artifactory.security.UserMFAStatus.NONE);
    }

    public Stream<Boolean> testGetMfaStatusData() {
        return Stream.of(true, false);
    }

    private void setSimpleUserAuthentication(UserInfo userInfo) {
        SimpleUser simpleUser = new SimpleUser(userInfo);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(simpleUser, "password", SimpleUser.USER_GAS);
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();
    }

    @Test
    void testGetMfaStatusUserNull() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(null, "password", SimpleUser.USER_GAS);
        SecurityContext securityContextMock = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContextMock);
        doReturn(authentication).when(securityContextMock).getAuthentication();
        assertEquals(org.artifactory.security.UserMFAStatus.NONE, service.getMfaStatus());
    }

    @Test
    void testGetMfaStatusDescriptorNull() {
        SimpleUser simpleUser = mock(SimpleUser.class);
        doReturn(null).when(simpleUser).getDescriptor();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(simpleUser, "password", SimpleUser.USER_GAS);
        SecurityContext securityContextMock = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContextMock);
        doReturn(authentication).when(securityContextMock).getAuthentication();
        assertEquals(org.artifactory.security.UserMFAStatus.NONE, service.getMfaStatus());
    }

    @Test
    void testRequireProfilePassword_WithPassword() {
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class, RETURNS_DEEP_STUBS);
        PasswordResetPolicy passwordResetPolicy = new PasswordResetPolicy();
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        when(applicationContext.getCentralConfig()).thenReturn(centralConfigService);
        when(applicationContext.beanForType(SecurityService.class)).thenReturn(service);
        ArtifactoryContextThreadBinder.bind(applicationContext);
        when(configDescriptor.getSecurity().getPasswordSettings().getResetPolicy()).thenReturn(passwordResetPolicy);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        SamlSsoAddon samlSsoAddon = mock(SamlSsoAddon.class);
        when(addonsManager.addonByType(SamlSsoAddon.class)).thenReturn(samlSsoAddon);
        HttpSsoAddon httpSsoAddon = mock(HttpSsoAddon.class);
        when(addonsManager.addonByType(HttpSsoAddon.class)).thenReturn(httpSsoAddon);
        AccessService accessServiceMock = mock(AccessService.class);
        service.setAccessService(accessServiceMock);
        AuthSettingsClient authSettingsClientMock = mock(AuthSettingsClient.class);
        when(accessServiceMock.getAccessAuthSettingsClient()).thenReturn(authSettingsClientMock);
        when(authSettingsClientMock.getHttpSsoSetting()).thenReturn(Optional.of(HttpSsoSetting.builder().build()));
        SamlSetting samlSetting = SamlSetting.builder().
                allowUserToAccessProfile(false)
                .build();
        when(accessServiceMock.getSamlSettings()).thenReturn(samlSetting);
        when(accessServiceMock.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());
        when(accessServiceMock.getAllOAuthSettings()).thenReturn(List.of());
        PropsAuthenticationToken mockAuthentication = createMock(PropsAuthenticationToken.class);
        SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
        UserImpl userInfo = new UserImpl();
        SaltedPassword saltedPassword = new SaltedPassword("password", "salt");
        userInfo.setPassword(saltedPassword);

        assertTrue(service.requireProfilePassword(userInfo),
                "Expected to require profile password when user has a password.");
    }

    @Test
    void testRequireProfilePassword_WithoutPassword() {
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class, RETURNS_DEEP_STUBS);
        PasswordResetPolicy passwordResetPolicy = new PasswordResetPolicy();
        ArtifactoryApplicationContext applicationContext = mock(ArtifactoryApplicationContext.class);
        when(applicationContext.getCentralConfig()).thenReturn(centralConfigService);
        when(applicationContext.beanForType(SecurityService.class)).thenReturn(service);
        ArtifactoryContextThreadBinder.bind(applicationContext);
        when(configDescriptor.getSecurity().getPasswordSettings().getResetPolicy()).thenReturn(passwordResetPolicy);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(applicationContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        SamlSsoAddon samlSsoAddon = mock(SamlSsoAddon.class);
        when(addonsManager.addonByType(SamlSsoAddon.class)).thenReturn(samlSsoAddon);
        HttpSsoAddon httpSsoAddon = mock(HttpSsoAddon.class);
        when(addonsManager.addonByType(HttpSsoAddon.class)).thenReturn(httpSsoAddon);
        AccessService accessServiceMock = mock(AccessService.class);
        service.setAccessService(accessServiceMock);
        AuthSettingsClient authSettingsClientMock = mock(AuthSettingsClient.class);
        when(accessServiceMock.getAccessAuthSettingsClient()).thenReturn(authSettingsClientMock);
        when(authSettingsClientMock.getHttpSsoSetting()).thenReturn(Optional.of(HttpSsoSetting.builder().build()));
        SamlSetting samlSetting = SamlSetting.builder().
                allowUserToAccessProfile(false)
                .build();
        when(accessServiceMock.getSamlSettings()).thenReturn(samlSetting);
        PropsAuthenticationToken mockAuthentication = createMock(PropsAuthenticationToken.class);
        SecurityContextHolder.getContext().setAuthentication(mockAuthentication);

        UserImpl userInfo = new UserImpl("name");
        SaltedPassword saltedPassword = new SaltedPassword("", "salt");
        userInfo.setPassword(saltedPassword);
        assertFalse(service.requireProfilePassword(userInfo),
                "Expected not to require profile password when user has no password.");
    }

    @Test
    void testMarkUsersCredentialsExpired() {
        int daysToKeepPassword = 30;
        List<String> expiredUsers = Arrays.asList("user1", "user2");
        UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(userGroupStoreServiceMock);
        when(userGroupStoreServiceMock.markUsersCredentialsExpired(daysToKeepPassword)).thenReturn(expiredUsers);
        SecurityListener securityListener = mock(SecurityListener.class);
        service.addListener(securityListener);
        service.markUsersCredentialsExpired(daysToKeepPassword);
        expiredUsers.forEach(user -> verify(securityListener).onUserUpdate(user));
    }

    @Test
    void testAssertValidAuthInCaseMfaEnabledNonAuthenticated() {
        String userName = "test";
        String password = "password";
        UserInfo userInfoMock = mock(UserInfo.class);
        UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(userGroupStoreServiceMock);

        when(userInfoMock.getMfaStatus()).thenReturn(ENROLL);
        when(userInfoMock.getPassword()).thenReturn(password);
        when(userGroupStoreServiceMock.findUser(userName)).thenReturn(userInfoMock);
        mockRealmsIncludeInternal(true);

        ForbiddenException forbiddenException = assertThrows(ForbiddenException.class,
                () -> service.assertUserAllowedToLoginWithMfaEnabled(userName, false));
        assertEquals("When multi-factor authentication is enabled only bearer token is accepted",
                forbiddenException.getMessage());
    }

    @Test
    void testAssertValidAuthInCaseMfaEnabledNonAuthenticatedUI() {
        String userName = "test";
        String password = "password";
        UserInfo userInfoMock = mock(UserInfo.class);
        UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(userGroupStoreServiceMock);
        when(userInfoMock.getMfaStatus()).thenReturn(ENROLL);
        when(userInfoMock.getPassword()).thenReturn(password);
        when(userGroupStoreServiceMock.findUser(userName)).thenReturn(userInfoMock);
        mockRealmsIncludeInternal(false);

        // Nothing should happen
        service.assertUserAllowedToLoginWithMfaEnabled(userName, true);
    }

    @ParameterizedTest
    @MethodSource("mfaStatuses")
    void testIsMfaEnabledNonAuthenticated(org.artifactory.security.UserMFAStatus mfaStatus, boolean enabled) {
        String userName = "test";
        UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(userGroupStoreServiceMock);
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getMfaStatus()).thenReturn(mfaStatus);
        when(userGroupStoreServiceMock.findUser(userName)).thenReturn(userInfoMock);
        mockRealmsIncludeInternal(false);

        assertEquals(enabled, service.isMfaEnabled(userName));
    }

    @Test
    void testIsMfaEnabledNonAuthenticatedMfaNotEnabled() {
        String userName = "test";
        UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);

        service.setUserGroupStoreService(userGroupStoreServiceMock);
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getMfaStatus()).thenReturn(NONE);
        when(userGroupStoreServiceMock.findUser(userName)).thenReturn(userInfoMock);
        mockRealmsIncludeInternal(false);

        // Nothing should happen
        service.assertUserAllowedToLoginWithMfaEnabled(userName, false);
    }

    @Test
    void testIsMfaEnabledNonAuthenticatedRealmsWithoutInternal() {
        String userName = "test";
        UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);

        service.setUserGroupStoreService(userGroupStoreServiceMock);
        UserInfo userInfoMock = mock(UserInfo.class);
        when(userInfoMock.getMfaStatus()).thenReturn(ENROLL);
        when(userGroupStoreServiceMock.findUser(userName)).thenReturn(userInfoMock);
        mockRealmsIncludeInternal(false);

        // Nothing should happen
        service.assertUserAllowedToLoginWithMfaEnabled(userName, false);
    }

    @Test
    void testIsMfaEnabledNonAuthenticatedUserNotFound() {
        String userName = "test";
        UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(userGroupStoreServiceMock);
        when(userGroupStoreServiceMock.findUser(userName)).thenThrow(new UsernameNotFoundException("user not found"));

        assertFalse(service.isMfaEnabled(userName));
    }

    @Test
    void testAssertValidAuthInCaseMfaEnabledNonAuthenticatedAccessToken() {
        try (MockedStatic<AuthenticationHelper> mocked = mockStatic(AuthenticationHelper.class)) {
            String userName = "test";
            String password = "password";
            UserInfo userInfoMock = mock(UserInfo.class);
            UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);

            service.setUserGroupStoreService(userGroupStoreServiceMock);

            AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
            mocked.when(AuthenticationHelper::getAuthentication).thenReturn(accessTokenAuthentication);

            when(userInfoMock.getMfaStatus()).thenReturn(ENROLL);
            when(userInfoMock.getPassword()).thenReturn(password);
            when(userGroupStoreServiceMock.findUser(userName)).thenReturn(userInfoMock);
            mockRealmsIncludeInternal(false);
            // Nothing should happen
            service.assertUserAllowedToLoginWithMfaEnabled(userName, false);
        }
    }

    @Test
    void testAssertValidAuthInCaseMfaEnabledNullRealms() {
        try (MockedStatic<AuthenticationHelper> mocked = mockStatic(AuthenticationHelper.class)) {
            String userName = "test";
            String password = "password";
            UserInfo userInfoMock = mock(UserInfo.class);
            UserGroupStoreService userGroupStoreServiceMock = mock(UserGroupStoreService.class);

            service.setUserGroupStoreService(userGroupStoreServiceMock);

            AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
            mocked.when(AuthenticationHelper::getAuthentication).thenReturn(accessTokenAuthentication);

            when(userInfoMock.getMfaStatus()).thenReturn(ENROLL);
            when(userInfoMock.getPassword()).thenReturn(password);
            when(userGroupStoreServiceMock.findUser(userName)).thenReturn(userInfoMock);
            AccessService accessServiceMock = mock(AccessService.class);
            service.setAccessService(accessServiceMock);
            AccessClient accessClient = mock(AccessClient.class);
            when(accessServiceMock.getAccessClient()).thenReturn(accessClient);

            ConfigClient configClient = mock(ConfigClient.class);
            when(accessClient.config()).thenReturn(configClient);
            when(configClient.getEnabledRealms()).thenReturn(null);
            // Nothing should happen
            service.assertUserAllowedToLoginWithMfaEnabled(userName, false);
        }
    }

    @Test
    void testAssertValidAuthInCaseMfaEnabledFeatureFlagDisabled() {
        homeStub.setProperty(ConstantValues.loginMfaCheckEnabled, "false");
        // Nothing should happen
        service.assertUserAllowedToLoginWithMfaEnabled("userName", false);
    }

    @ParameterizedTest
    @MethodSource("realms")
    public void testIsLegacyLoginFlowNotSupportingMfa(List<String> realms, boolean isLegacy) throws Exception {
        EnabledRealmsConfig enabledRealmsConfig = mock(EnabledRealmsConfig.class);
        when(enabledRealmsConfig.getEnabledRealms()).thenReturn(realms);
        boolean actual = service.isLegacyLoginFlowNotSupportingMfa(enabledRealmsConfig);
        assertEquals(isLegacy, actual);
    }

    private static Stream<Arguments> mfaStatuses() {
        return Stream.of(
                Arguments.of(ENROLL, true),
                Arguments.of(VERIFY, true),
                Arguments.of(NONE, false)
        );
    }

    private static Stream<Arguments> realms() {
        return Stream.of(
                Arguments.of(List.of("internal", "jira"), false),
                Arguments.of(List.of("cloud", "jira"), true),
                Arguments.of(null, true),
                Arguments.of(List.of(), true)
        );
    }

    private void mockRealmsIncludeInternal(boolean includeInternal) {
        AccessService accessServiceMock = mock(AccessService.class);
        service.setAccessService(accessServiceMock);
        AccessClient accessClient = mock(AccessClient.class);
        when(accessServiceMock.getAccessClient()).thenReturn(accessClient);

        ConfigClient configClient = mock(ConfigClient.class);
        when(accessClient.config()).thenReturn(configClient);
        EnabledRealmsConfig enabledRealmsConfigMock = mock(EnabledRealmsConfig.class);
        when(configClient.getEnabledRealms()).thenReturn(enabledRealmsConfigMock);
        List<String> realms = includeInternal ? List.of("realm1", "internal") : List.of("realms1", "realms2");
        when(enabledRealmsConfigMock.getEnabledRealms()).thenReturn(realms);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCanDoToRepoProjectResource(boolean canManageResource) {
        when(projectsService.canDoToProjectRepoResource(any(), anyString(), any())).thenReturn(canManageResource);
        assertEquals(canManageResource, service.canDoToRepoProjectResource(RepoActionType.UPDATE, "repoKey",
                RepoDetailsType.LOCAL));
    }

    @Test
    void deleteUser_notifiesAuthCacheListeners() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        service.setAuthorizationService(authorizationService);
        UserGroupStoreService storeService = mock(UserGroupStoreService.class);
        service.setUserGroupStoreService(storeService);
        SecurityListener securityListener = mock(SecurityListener.class);
        service.addListener(securityListener);

        service.deleteUser("victim");

        verify(authorizationService).removeAllUserAces("victim");
        verify(storeService).deleteUser("victim");
        verify(interceptors).onUserDelete("victim");
        // What the listener does with the notification is where the feature flag applies, see AccessFilterTest
        verify(securityListener).onUserUpdate("victim");
    }

    @Test
    void onPermissionsUpdateEvent_invalidatesAuthCache_whenUserNamesPresent() {
        SecurityListener securityListener = registerListenerForPermissionsEvent();

        onPermissionsUpdateEvent(invalidationEvent(List.of("victim", "other"), null));

        verify(securityListener).onUserUpdate("victim");
        verify(securityListener).onUserUpdate("other");
        verify(groupCachingRepo, never()).invalidateGroupsCache(any());
    }

    @Test
    void onPermissionsUpdateEvent_clearsWholeAuthCache_whenFullInvalidation() {
        SecurityListener securityListener = registerListenerForPermissionsEvent();

        onPermissionsUpdateEvent(fullInvalidationEvent());

        verify(securityListener).onClearSecurity();
    }

    @Test
    void onPermissionsUpdateEvent_leavesAuthCacheAlone_whenOnlyGroupNamesPresent() {
        SecurityListener securityListener = registerListenerForPermissionsEvent();

        onPermissionsUpdateEvent(invalidationEvent(null, List.of("readers")));

        verify(groupCachingRepo).invalidateGroupsCache("readers");
        verify(securityListener, never()).onUserUpdate(anyString());
        verify(securityListener, never()).onClearSecurity();
    }

    @Test
    void onPermissionsUpdateEvent_leavesAuthCacheAlone_whenFeatureFlagDisabled() {
        SecurityListener securityListener = registerListenerForPermissionsEvent(false);

        onPermissionsUpdateEvent(invalidationEvent(List.of("victim"), null));

        verify(securityListener, never()).onUserUpdate(anyString());
    }

    /**
     * The injected key is a string literal and cannot reference the constant, so nothing but this test stops a
     * typo there from silently pinning the feature to its default and disabling the kill switch.
     */
    @Test
    void authCacheImmediateInvalidationFlag_isBoundToTheConstantValuesProperty() throws NoSuchFieldException {
        Value injected = SecurityServiceImpl.class.getDeclaredField("authCacheImmediateInvalidationEnabled")
                .getAnnotation(Value.class);

        assertNotNull(injected, "the feature flag must stay injected; reading it needs no ArtifactoryHome");
        assertEquals("${" + ConstantValues.securityAuthCacheImmediateInvalidationEnabled.getPropertyName() + "}",
                injected.value());
    }

    /**
     * Access delivers these events on its own client threads, which carry no ArtifactoryHome. Reading the feature
     * flag there used to throw and abort the groups-cache invalidation with it (RTDEV-95134), which is why the flag
     * is injected now. This guards against anyone reintroducing a thread-local read on this path.
     */
    @Test
    void onPermissionsUpdateEvent_invalidatesBothCaches_whenArtifactoryHomeIsNotBoundToThread() {
        SecurityListener securityListener = registerListenerForPermissionsEvent();
        ArtifactoryHome boundHome = ArtifactoryHome.get();
        ArtifactoryHome.unbind();
        try {
            onPermissionsUpdateEvent(invalidationEvent(List.of("victim"), List.of("readers")));

            assertFalse(ArtifactoryHome.isBound(), "the event thread must be left as it was found");
        } finally {
            ArtifactoryHome.bind(boundHome);
        }

        verify(groupCachingRepo).invalidateGroupsCache("readers");
        verify(securityListener).onUserUpdate("victim");
    }

    @Test
    void onPermissionsUpdateEvent_stillInvalidatesGroupsCache_whenAuthCacheInvalidationFails() {
        SecurityListener securityListener = registerListenerForPermissionsEvent();
        doThrow(new RuntimeException("listener exploded")).when(securityListener).onUserUpdate(anyString());

        Assertions.assertDoesNotThrow(
                () -> onPermissionsUpdateEvent(invalidationEvent(List.of("victim"), List.of("readers"))));

        verify(groupCachingRepo).invalidateGroupsCache("readers");
    }

    private SecurityListener registerListenerForPermissionsEvent() {
        return registerListenerForPermissionsEvent(true);
    }

    /**
     * @param immediateInvalidation the value Spring would have injected into the feature-flag field. It has to be
     *                              set explicitly: this service is constructed directly here, so no injection runs
     *                              and the field would otherwise default to false whatever the property says.
     */
    private SecurityListener registerListenerForPermissionsEvent(boolean immediateInvalidation) {
        groupCachingRepo = mock(ArtifactoryGroupCachingRepoImpl.class);
        service.setGroupCachingRepo(groupCachingRepo);
        InternalArtifactoryContext artifactoryContext = mock(InternalArtifactoryContext.class);
        when(artifactoryContext.getArtifactoryHome()).thenReturn(homeStub);
        ReflectionTestUtils.setField(service, "context", artifactoryContext);
        ReflectionTestUtils.setField(service, "authCacheImmediateInvalidationEnabled", immediateInvalidation);
        SecurityListener securityListener = mock(SecurityListener.class);
        service.addListener(securityListener);
        return securityListener;
    }

    private void onPermissionsUpdateEvent(GrpcAffectedEntitiesResponse event) {
        ReflectionTestUtils.invokeMethod(service, "onPermissionsUpdateEvent", event);
    }

    private static GrpcAffectedEntitiesResponse invalidationEvent(List<String> userNames, List<String> groupNames) {
        GrpcAffectedEntitiesResponse event = mock(GrpcAffectedEntitiesResponse.class);
        when(event.isInvalidationEvent()).thenReturn(true);
        when(event.getUserNames()).thenReturn(userNames);
        when(event.getGroupNames()).thenReturn(groupNames);
        return event;
    }

    private static GrpcAffectedEntitiesResponse fullInvalidationEvent() {
        GrpcAffectedEntitiesResponse event = mock(GrpcAffectedEntitiesResponse.class);
        when(event.isFullInvalidationEvent()).thenReturn(true);
        return event;
    }
}