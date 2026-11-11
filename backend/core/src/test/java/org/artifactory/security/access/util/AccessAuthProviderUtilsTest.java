package org.artifactory.security.access.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.security.AnonymousAuthenticationToken;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.access.AccessAuthenticationPreconditionException;
import org.artifactory.security.access.AccessTokenAuthenticationException;
import org.artifactory.storage.db.security.service.access.GroupMapper;
import org.fest.assertions.Condition;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.client.model.ErrorsModel;
import org.jfrog.access.client.model.MessageModel;
import org.jfrog.access.model.MessageModelCode;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.user.UserWithGroupsResponse;
import org.jfrog.access.user.UserMFAStatus;
import org.jfrog.access.user.UsersUtil;
import org.jfrog.common.JsonUtils;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

public class AccessAuthProviderUtilsTest {

    @DataProvider
    public static Object[][] userScopes() {
        return new Object[][] {
                {Set.of("applied-permissions/groups:group1"), new Condition<SimpleUser>() {
                    @Override
                    public boolean matches(SimpleUser simpleUser) {
                        return simpleUser.isAppliedPermissionsGroups() && !simpleUser.isIdentity();
                    }
                }},
                {Set.of("applied-permissions/groups:group1,group2"), new Condition<SimpleUser>() {
                    @Override
                    public boolean matches(SimpleUser simpleUser) {
                        return simpleUser.isAppliedPermissionsGroups() && !simpleUser.isIdentity();
                    }
                }},
                {Set.of("applied-permissions/groups"), new Condition<SimpleUser>() {
                    @Override
                    public boolean matches(SimpleUser simpleUser) {
                        return !simpleUser.isAppliedPermissionsGroups() && !simpleUser.isIdentity();
                    }
                }},
                {Set.of("applied-permissions/user"), new Condition<SimpleUser>() {
                    @Override
                    public boolean matches(SimpleUser simpleUser) {
                        return !simpleUser.isAppliedPermissionsGroups() && simpleUser.isIdentity();
                    }
                }},
                {Set.of("member-of-groups:*"), new Condition<SimpleUser>() {
                    @Override
                    public boolean matches(SimpleUser simpleUser) {
                        return !simpleUser.isAppliedPermissionsGroups() && simpleUser.isIdentity();
                    }
                }},
                {Set.of("applied-permissions/user", "applied-permissions/groups:group1"), new Condition<SimpleUser>() {
                    @Override
                    public boolean matches(SimpleUser simpleUser) {
                        return simpleUser.isAppliedPermissionsGroups() && simpleUser.isIdentity();
                    }
                }},
                {Set.of("applied-permissions/user", "applied-permissions/groups:group1"), new Condition<SimpleUser>() {
                    @Override
                    public boolean matches(SimpleUser simpleUser) {
                        return simpleUser.isAppliedPermissionsGroups() && simpleUser.isIdentity();
                    }
                }},
        };
    }

    @DataProvider
    public static Object[][] exceptionMessageModel() {
        return new Object[][] {
                {MessageModelCode.CODE_TOO_MANY_REQUESTS, LockedException.class},
                {MessageModelCode.CODE_PASSWORD_EXPIRED, CredentialsExpiredException.class},
                {MessageModelCode.CODE_DISABLED, DisabledException.class},
                {MessageModelCode.CODE_PRECONDITION_FAILED, AccessAuthenticationPreconditionException.class},
                {MessageModelCode.CODE_TOKEN_VERIFICATION_FAILED, AccessTokenAuthenticationException.class},
        };
    }

    @Test(dataProvider = "userScopes")
    public void testToArtifactoryUserIsAppliedPermissionsGroups(Set<String> scopes, Condition<Object> matcher) {
        UserWithGroupsResponse user = new UserWithGroupsResponse("identityuser")
                .status(UserStatus.ENABLED)
                .customData(Map.of(GroupMapper.ArtifactoryBuiltInGroupProperty.artifactory_admin.name(), "false"))
                .mfaStatus(UserMFAStatus.NONE);
        SimpleUser artifactoryUser = AccessAuthProviderUtils.toArtifactoryUser(
                new UserPrincipal(user, scopes, Set.of()), "pass");
        assertThat(artifactoryUser).satisfies(matcher);
    }

    @Test(dataProvider = "groupPropertiesProvider")
    public void testUserGroupProperties(Set<UsersUtil.BuiltInUserProperty> groupProperties) {
        UserWithGroupsResponse user = new UserWithGroupsResponse("test-user")
                .status(UserStatus.ENABLED)
                .customData(Map.of(GroupMapper.ArtifactoryBuiltInGroupProperty.artifactory_admin.name(), "false"))
                .mfaStatus(UserMFAStatus.NONE);
        SimpleUser artifactoryUser = AccessAuthProviderUtils.toArtifactoryUser(
                new UserPrincipal(user, Set.of(), Set.of(), groupProperties), "pass");
        assertEquals(artifactoryUser.getDescriptor().isGroupAdmin(),
                groupProperties.contains(UsersUtil.BuiltInUserProperty.artifactory_admin));
    }

    @Test
    public void testShouldNotHandleAccessException() {
        AccessAuthProviderUtils.handleAccessClientHttpException(new AnonymousAuthenticationToken(),
                new AccessClientHttpException(500, "Internal server error"));
    }

    @Test(expectedExceptions = BadCredentialsException.class)
    public void testExceptionNullModel() {
        AccessClientHttpException exception = new AccessClientHttpException(400, null);
        AccessAuthProviderUtils.handleAccessClientHttpException(new AnonymousAuthenticationToken(), exception);
    }

    @Test(expectedExceptions = LockedException.class, expectedExceptionsMessageRegExp = "User is temp locked")
    public void testExceptionStatusCode() {
        AccessClientHttpException exception = createAccessException(429, MessageModelCode.CODE_TOO_MANY_REQUESTS,
                "User is temp locked");
        AccessAuthProviderUtils.handleAccessClientHttpException(new AnonymousAuthenticationToken(), exception);
    }

    @Test(dataProvider = "exceptionMessageModel")
    public void testExceptionMessageModel(String msgCode, Class<? extends Throwable> exceptionClass) {
        AccessClientHttpException exception = createAccessException(400, msgCode, null);
        Assert.assertThrows(exceptionClass,
                () -> AccessAuthProviderUtils.handleAccessClientHttpException(new AnonymousAuthenticationToken(),
                        exception));
    }

    private AccessClientHttpException createAccessException(int statusCode, String msgCode,
            String msg) {
        ErrorsModel errorsModel = new ErrorsModel();
        errorsModel.setErrors(
                List.of(new MessageModel(msgCode, msg, null)));
        return new AccessClientHttpException(statusCode, JsonUtils.getInstance().valueToString(errorsModel));
    }

    @DataProvider
    public static Object[][] groupPropertiesProvider() {
        return new Object[][] {
                {Set.of(UsersUtil.BuiltInUserProperty.artifactory_admin)},
                {Set.of()},
        };
    }

    @Test
    public void testStatus400LogsDebug() {
        MemoryAppender memoryAppender =
                MemoryAppenderUtil.createMemoryAppender(AccessAuthProviderUtils.class, Level.DEBUG);
        Logger logger = (Logger) LoggerFactory.getLogger(AccessAuthProviderUtils.class);

        try {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("testuser", "password");
            AccessClientHttpException exception = new AccessClientHttpException(400, null);

            Assert.assertThrows(BadCredentialsException.class,
                    () -> AccessAuthProviderUtils.handleAccessClientHttpException(auth, exception));

            String msg = "Access authentication failed for user";
            assertEquals(memoryAppender.search(msg, Level.WARN).size(), 0,
                    "Status 400 should not log at WARN level");
            assertEquals(memoryAppender.search(msg, Level.DEBUG).size(), 1,
                    "Status 400 should log at DEBUG level");
        } finally {
            memoryAppender.stop();
            logger.detachAppender(memoryAppender);
        }
    }

}