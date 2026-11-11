package org.artifactory.security.access.providers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.access.AccessTokenAuthenticationException;
import org.artifactory.security.access.AppliedPermissionsScopeToken;
import org.artifactory.security.access.util.AccessAuthProviderUtils;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.props.auth.BadPropsAuthException;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.ServicePrincipal;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.common.ServiceType;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.api.security.access.UserTokenSpec.TOKEN_PREFIX;

@Slf4j
@Component
public class AccessTokenAuthenticationProviderV2 implements RealmAwareAuthenticationProvider {

    private final AccessService accessService;

    public AccessTokenAuthenticationProviderV2(AccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = getCredentials(authentication);
        log.debug("Token authentication is being performed via Access service.");
        try {
            SimpleUser simpleUser = authenticateUser(username, password, authentication);
            return newSuccessfulAccessTokenAuthentication(simpleUser, password, authentication);
        } catch (AccessClientHttpException e) {
            AccessAuthProviderUtils.handleAccessClientHttpException(authentication, e);
            throw e;
        } catch (AccessClientException e) {
            throw new AccessTokenAuthenticationException("Failed to verify token.", e);
        }
    }

    private String getCredentials(Authentication authentication) {
        return authentication instanceof AccessTokenAuthentication accessTokenAuthentication ?
                accessTokenAuthentication.getAccessTokenValue() : (String) authentication.getCredentials();
    }

    private SimpleUser authenticateUser(String username, String password, Authentication authentication) {
        assertNotTokenWithApiKeyHeader(password, authentication);
        LoginResult loginResult =
                accessService.ensureAuth(() -> accessService.getGrpcAuthClient().authenticate(username, password));
        if (!loginResult.isSuccessful()) {
            throw new AccessTokenAuthenticationException("Token failed verification: " + loginResult.getReason());
        }
        AccessPrincipal accessPrincipal = loginResult.getPrincipal();
        if (accessPrincipal instanceof ServicePrincipal servicePrincipal) {
            return new SimpleUser(new UserInfoBuilder(servicePrincipal.getName()).admin(
                    AccessAuthProviderUtils.isScopeAdmin(new HashSet<>(servicePrincipal.tokenScopes()),
                            accessService.getArtifactoryServiceId())).build());
        }
        if (accessPrincipal instanceof UserPrincipal userPrincipal) {
            assertUsernameMatch(userPrincipal, authentication);
            String newUsername = addPrefixIfNeeded(password, userPrincipal);
            SimpleUser simpleUser = AccessAuthProviderUtils.toArtifactoryUser(userPrincipal, password,
                    accessService.getArtifactoryServiceId());
            MutableUserInfo userInfo = (MutableUserInfo) simpleUser.getDescriptor();
            userInfo.setUsername(newUsername);
            return new SimpleUser(userInfo);
        }
        throw new AuthenticationServiceException("Unknown principal type with name `%s`".formatted(
                Optional.ofNullable(accessPrincipal).map(AccessPrincipal::getName).orElse("")));
    }

    private void assertNotTokenWithApiKeyHeader(String password, Authentication authentication) {
        if (authentication instanceof PropsAuthenticationToken propsAuthenticationToken &&
                ApiKeyManager.API_KEY.equals(propsAuthenticationToken.getPropsKey()) &&
                accessService.getAccessTokenType(password) == AccessTokenType.JWT_ACCESS_TOKEN) {
            throw new BadPropsAuthException("Props Authentication Token not found");
        }
    }

    private void assertUsernameMatch(UserPrincipal userPrincipal, Authentication authentication) {
        String username = authentication.getName();
        if (StringUtils.isNotBlank(username) && !userPrincipal.user().getUsername().equalsIgnoreCase(username)) {
            throw new AuthenticationServiceException("Wrong username was used");
        }
    }

    private String addPrefixIfNeeded(String password, UserPrincipal userPrincipal) {
        if (accessService.getAccessTokenType(password) == AccessTokenType.REFERENCE_TOKEN ||
                accessService.parseToken(password).getIssuer().startsWith(ServiceType.FRONTEND) ||
                isIdentityToken(new ArrayList<>(userPrincipal.tokenScopes()))) {
            return userPrincipal.user().getUsername();
        }
        return TOKEN_PREFIX + userPrincipal.user().getUsername();
    }

    private boolean isIdentityToken(List<String> scope) {
        Set<String> groupNamesFromScope =
                org.artifactory.security.access.AccessTokenAuthenticationProvider.collectGroupNamesFromScope(scope);
        return AppliedPermissionsScopeToken.isIdentityToken(scope) ||
                (groupNamesFromScope.size() == 1 && groupNamesFromScope.contains("*"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return accessService.shouldDelegateTokenOrApiKeyAuthenticationToAccess();
    }

    private Authentication newSuccessfulAccessTokenAuthentication(SimpleUser simpleUser, String password,
            Authentication authentication) {
        JwtAccessToken jwtAccessToken = convertToToken(password);
        AccessTokenAuthentication accessTokenAuth =
                new AccessTokenAuthentication(password, jwtAccessToken, simpleUser, null);
        accessTokenAuth.setDetails(authentication.getDetails());
        return accessTokenAuth;
    }

    private JwtAccessToken convertToToken(String password) {
        return switch (accessService.getAccessTokenType(password)) {
            case REFERENCE_TOKEN -> parseReferenceToken(password);
            case JWT_ACCESS_TOKEN -> JwtAccessTokenImpl.parseTokenValue(password);
            default -> throw new IllegalArgumentException("Could not resolve token");
        };
    }

    private JwtAccessToken parseReferenceToken(String password) {
        TokenVerifyResult tokenVerifyResult = accessService.verifyAndGetResult(password);
        log.debug("Got token verify result: {}", tokenVerifyResult);
        JwtAccessToken accessToken = tokenVerifyResult.getAccessToken();
        if (accessToken == null) {
            throw new IllegalArgumentException("Could not verify reference token");
        }
        return accessToken;
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        //Not relevant
    }

    @Override
    public boolean userExists(String userName) {
        return false;
    }
}
