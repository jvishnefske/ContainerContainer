package org.artifactory.security.access.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.model.security.UserProperty;
import org.artifactory.security.ArtifactoryUserStatus;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserMFAStatus;
import org.artifactory.security.access.AccessAuthenticationPreconditionException;
import org.artifactory.security.access.AccessTokenAuthenticationException;
import org.artifactory.security.access.AccessTokenAuthenticationProvider;
import org.artifactory.security.access.ArtifactoryAdminScopeToken;
import org.artifactory.storage.db.security.service.access.UserMapper;
import org.artifactory.util.CollectionUtils;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.client.model.MessageModel;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.model.MessageModelCode;
import org.jfrog.access.rest.user.UserWithGroups;
import org.jfrog.access.user.UsersUtil;
import org.jfrog.access.util.TokenScopeUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_TOO_MANY_REQUESTS;
import static org.artifactory.storage.db.security.service.access.GroupMapper.fromAccessRealm;
import static org.artifactory.storage.db.security.service.access.UserMapper.isGroupAdmin;


/**
 * @author Mor Merhav
 */
@Slf4j
@UtilityClass
public class AccessAuthProviderUtils {

    public void handleAccessClientHttpException(Authentication authentication, AccessClientHttpException e) {
        if (!shouldHandleHttpException(e)) {
            return;
        }
        log.debug("Access authentication failed for user: '{}'. Status code: {}", authentication.getPrincipal(),
                e.getStatusCode(), e);
        handleExceptionStatusCodes(e);
        handleExceptionMessageCodes(e);
        throw new BadCredentialsException("Bad Credentials");
    }

    private void handleExceptionMessageCodes(AccessClientHttpException e) {
        if (e.getErrorsModel() == null) {
            return;
        }
        for (MessageModel error : e.getErrorsModel().getErrors()) {
            RuntimeException exception = messageModelToException(e, error);
            if (exception != null) {
                throw exception;
            }
        }
    }

    private RuntimeException messageModelToException(AccessClientHttpException e, MessageModel error) {
        return switch (error.getCode()) {
            case MessageModelCode.CODE_TOO_MANY_REQUESTS -> new LockedException(
                    Optional.ofNullable(error.getMessage())
                            .orElse("Too many attempts, please try again later"), e);
            case MessageModelCode.CODE_PASSWORD_EXPIRED ->
                    throw new CredentialsExpiredException("Password has expired", e);
            case MessageModelCode.CODE_DISABLED -> new DisabledException("User is disabled", e);
            case MessageModelCode.CODE_PRECONDITION_FAILED -> new AccessAuthenticationPreconditionException(
                    Optional.ofNullable(error.getMessage())
                            .orElse("Authentication preconditions failed"), e);
            case MessageModelCode.CODE_TOKEN_VERIFICATION_FAILED ->
                    new AccessTokenAuthenticationException("Token failed verification: " +
                            Optional.ofNullable(error.getMessage()).orElse("invalid"), e);
            default -> null;
        };
    }

    private void handleExceptionStatusCodes(AccessClientHttpException e) {
        if (e.getErrorsModel() == null) {
            return;
        }
        if (e.getStatusCode() == SC_TOO_MANY_REQUESTS) {
            String msg = e.getErrorsModel().getErrors().stream().findFirst()
                    .map(MessageModel::getMessage)
                    .stream()
                    .peek(log::warn)
                    .findAny()
                    .orElse("Too many attempts, please try again later");
            throw new LockedException(msg, e);
        }
    }

    private boolean shouldHandleHttpException(AccessClientHttpException e) {
        int statusCode = e.getStatusCode();
        return statusCode < SC_NOT_FOUND || statusCode == SC_TOO_MANY_REQUESTS;
    }

    public SimpleUser toArtifactoryUser(UserPrincipal userPrincipal, String password) {
        return toArtifactoryUser(userPrincipal, password, null);
    }

    public SimpleUser toArtifactoryUser(UserPrincipal userPrincipal, String password, ServiceId serviceId) {
        UserWithGroups user = userPrincipal.user();
        Set<UsersUtil.BuiltInUserProperty> userProperties = userPrincipal.userProperties();
        Set<String> tokenScopes = userPrincipal.tokenScopes();
        boolean identityToken = AccessTokenAuthenticationProvider.isIdentityToken(new ArrayList<>(tokenScopes));
        UserInfoBuilder builder = new UserInfoBuilder(user.getUsername())
                .email(StringUtils.isBlank(user.getEmail()) ? null : user.getEmail())
                .admin(isAdmin(userProperties, tokenScopes, serviceId, identityToken))
                .policyViewer(userProperties.contains(UsersUtil.BuiltInUserProperty.policy_viewer))
                .policyManager(userProperties.contains(UsersUtil.BuiltInUserProperty.policy_manager))
                .watchManager(userProperties.contains(UsersUtil.BuiltInUserProperty.watch_manager))
                .reportsManager(userProperties.contains(UsersUtil.BuiltInUserProperty.reports_manager))
                .updatableProfile(userProperties.contains(UsersUtil.BuiltInUserProperty.updatable_profile))
                .passwordDisabled(userProperties.contains(UsersUtil.BuiltInUserProperty.disabled_password))
                .lastLogin(user.getLastLoginTime(), user.getLastLoginIp())
                .credentialsExpired(user.isPasswordExpired())
                .groupAdmin(isGroupAdmin(userPrincipal))
                .groups(UserMapper.groupsFromUser(user))
                .password(new SaltedPassword(password, null))
                .status(ArtifactoryUserStatus.getByValue(user.getStatus().getName()))
                .mfaStatus(UserMFAStatus.valueOf(user.getMfaStatus().toString()))
                .realm(fromAccessRealm(user.getRealm()))
                .appliedPermissionsGroups(userPrincipal.tokenScopes() != null &&
                        !TokenScopeUtils.scopeToGroups(userPrincipal.tokenScopes().stream().toList()).isEmpty())
                .transientUser(user.isTransientUser())
                .identity(identityToken)
                .mustChangePassword(UserMapper.isMustChangePassword(user));
        user.getCustomData().entrySet().stream()
                .filter(entry -> !UserMapper.MUST_CHANGE_PASSWORD_KEY.equals(entry.getKey()))
                .forEach(entry -> builder.addProp(new UserProperty(entry.getKey(), entry.getValue())));
        return new SimpleUser(builder.build());
    }

    private boolean isAdmin(Set<UsersUtil.BuiltInUserProperty> userProperties, Set<String> scopes,
            ServiceId serviceId, boolean identityToken) {
        if (CollectionUtils.notNullOrEmpty(scopes) && serviceId != null) {
            boolean scopeAdmin = isScopeAdmin(scopes, serviceId);
            if (identityToken) {
                return userProperties.contains(UsersUtil.BuiltInUserProperty.artifactory_admin) || scopeAdmin;
            }
            return scopeAdmin;
        }
        return userProperties.contains(UsersUtil.BuiltInUserProperty.artifactory_admin);
    }

    public boolean isScopeAdmin(Set<String> scopes, ServiceId serviceId) {
        return scopes.stream()
                .filter(ArtifactoryAdminScopeToken::accepts)
                .anyMatch(scope -> ArtifactoryAdminScopeToken.isAdminScopeOnService(scope, serviceId));
    }
}
