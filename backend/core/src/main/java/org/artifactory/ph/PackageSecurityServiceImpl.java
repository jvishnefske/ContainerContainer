package org.artifactory.ph;

import com.google.common.collect.Lists;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.model.ArtifactScopeAction;
import com.jfrog.ph.acl.model.ArtifactScopeTarget;
import com.jfrog.ph.acl.model.PackageAuthTokenCacheKey;
import com.jfrog.ph.acl.model.PackageTokenType;
import com.jfrog.ph.acl.model.SimpleUser;
import com.jfrog.ph.acl.service.PackageSecurityService;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.InternalSecurityService;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.access.AccessTokenAuthenticationException;
import org.artifactory.security.props.auth.TokenManager;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OauthErrorModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.security.providermgr.ArtifactoryCacheKey;
import org.artifactory.security.token.ArtifactoryTokenProvider;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.util.date.DateUtils;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.v2.Action;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.artifactory.util.RepoPathUtils.removeDashCache;

@Slf4j
public class PackageSecurityServiceImpl implements PackageSecurityService {

    public static final String FAILED_TO_AUTHENTICATE_LOCALLY = "Failed to authenticate locally.";
    public static final String LOCAL_TOKEN_CREATION_FAILED = "Local token creation failed";
    public static final String SCOPED_TOKEN_CREATION_FAILED = "Repository scoped token creation failed";
    /** Access glob matching every path at any depth under a repository. */
    private static final String RECURSIVE_PATH_WILDCARD = "**";
    private final InternalSecurityService securityService;
    private final AuthorizationService authorizationService;
    private final AccessService accessService;
    private final AuthenticationManager authenticationManager;
    private final UserGroupStoreService userGroupStoreService;
    private final TokenManager tokenManager;
    private final ArtifactoryTokenProvider artifactoryTokenProvider;
    private SecretKey secretKey;

    public PackageSecurityServiceImpl(InternalSecurityService securityService,
            AuthorizationService authorizationService, AccessService accessService,
            AuthenticationManager authenticationManager, UserGroupStoreService userGroupStoreService,
            TokenManager tokenManager, ArtifactoryTokenProvider artifactoryTokenProvider) {
        this.authorizationService = authorizationService;
        this.securityService = securityService;
        this.accessService = accessService;
        this.authenticationManager = authenticationManager;
        this.userGroupStoreService = userGroupStoreService;
        this.tokenManager = tokenManager;
        this.artifactoryTokenProvider = artifactoryTokenProvider;
        setSecretKey();
    }

    private void setSecretKey() {
        String masterKey = ArtifactoryHome.get().getSysConfig().helper().getMasterKey().orElse(null);
        if (masterKey == null) {
            log.info("No master.key was found");
            return;
        }
        secretKey = JFrogCryptoHelper.generatePbeKey(masterKey);
    }

    @Override
    public boolean canRead(String repoKey, String path) {
        return authorizationService.canRead(RepoPathFactory.create(repoKey, path));
    }

    @Override
    public boolean canWrite(String repoKey, String path) {
        return authorizationService.canDeploy(RepoPathFactory.create(repoKey, path));
    }

    @Override
    public boolean canAnnotate(String repoKey, String path) {
        return authorizationService.canAnnotate(RepoPathFactory.create(repoKey, path));
    }

    @Override
    public boolean canDelete(String repoKey, String path) {
        return authorizationService.canDelete(RepoPathFactory.create(repoKey, path));
    }

    @Override
    public boolean canManage(String repoKey, String path) {
        return authorizationService.canManage(RepoPathFactory.create(repoKey, path));
    }

    @Override
    public <V> V callAsSystem(Callable<V> callable) throws PackageException {
        try {
            return securityService.callAsSystem(callable);
        } catch (PackageException e) {
            throw e;
        } catch (Exception e) {
            throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e.getMessage(), e);
        }
    }

    @Override
    public boolean isAnonymous() {
        return securityService.isAnonymous();
    }

    @Override
    public boolean isAnonAccessEnabled() {
        return securityService.isAnonAccessEnabled();
    }

    @Override
    @NonNull
    public String getCurrentUserName() {
        return securityService.currentUsername();
    }

    @Override
    @NonNull
    public String getSystemUserName() {
        return SecurityService.USER_SYSTEM;
    }

    @Override
    public boolean isAuthenticated() {
        return securityService.isAuthenticated();
    }

    @Override
    @NonNull
    public String loginAndGenerateAccessTokenWithBasicAuth(String userName, String password) throws PackageException {
        Authentication originalAuth = SecurityContextHolder.getContext().getAuthentication();
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userName, password);
        try {
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
                // user came with access token - we should use the same token
                return accessTokenAuthentication.getAccessTokenValue();
            }
            // Adding the authentication so Access will get it for token
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return generateAccessTokenForUser(userName);
        } catch (BadCredentialsException | AccessTokenAuthenticationException e) {
            log.warn(FAILED_TO_AUTHENTICATE_LOCALLY + " for user: '{}'", userName, e);
            throw new PackageException(HttpStatus.SC_UNAUTHORIZED, FAILED_TO_AUTHENTICATE_LOCALLY,
                    FAILED_TO_AUTHENTICATE_LOCALLY);
        } catch (Exception e) {
            log.error(FAILED_TO_AUTHENTICATE_LOCALLY + ":{}", e.getMessage());
            log.debug(FAILED_TO_AUTHENTICATE_LOCALLY + " for user: '{}'", userName, e);
            throw new PackageException(HttpStatus.SC_BAD_REQUEST, FAILED_TO_AUTHENTICATE_LOCALLY,
                    FAILED_TO_AUTHENTICATE_LOCALLY);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(originalAuth);
        }
    }

    @Override
    public void revokeToken(String token) throws PackageException {
        try {
            accessService.revokeToken(token);
        } catch (AccessClientException accessClientException) {
            log.debug(accessClientException.getMessage());
        } catch (Exception e) {
            String msg = "Logout locally failed";
            log.warn(msg + ": error on token revoke.", e.getMessage());
            log.debug(msg + ": error on token revoke of: {}", token, e);
            throw new PackageException(HttpStatus.SC_BAD_REQUEST, msg, msg);
        }
    }

    @Override
    public SimpleUser getCurrentUserDetails() throws PackageException {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if ((authentication == null) || !authentication.isAuthenticated()) {
            String msg = "User is unauthenticated";
            throw new PackageException(HttpStatus.SC_BAD_REQUEST, msg, msg);
        }
        String authUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
        String password = (String) authentication.getCredentials();
        if (StringUtils.isNotBlank(password)) {
            // Check if the user is an Access Token user
            if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
                String username = accessService
                        .extractSubjectUsername(accessTokenAuthentication.getAccessToken());
                String email = "";
                if (accessTokenAuthentication.getPrincipal()
                        instanceof org.artifactory.security.SimpleUser simpleUser) {
                    email = simpleUser.getEmail();
                }
                MutableUserInfo transientUser = new UserInfoBuilder(username).email(email).transientUser().build();
                return new SimpleUser(transientUser.getUsername(), password, transientUser.getEmail());
            }
            UserInfo user = userGroupStoreService.findUser(authUsername);
            if (user != null) {
                if (!CryptoHelper.isApiKey(password)) {
                    password = securityService.createEncryptedPasswordIfNeeded(user, password);
                }
                return new SimpleUser(authUsername, password, user.getEmail());
            }
        }
        String msg = "The user: '" + authentication.getPrincipal() + "' couldn't be found";
        throw new PackageException(HttpStatus.SC_BAD_REQUEST, msg, msg);
    }

    @Nullable
    @Override
    public String getUserNameByAccessTokenValue(@NonNull String accessTokenValue) {
        try {
            final LoginResult loginResult = accessService.getGrpcAuthClient()
                                                    .authenticate(StringUtils.EMPTY, accessTokenValue);
            if (loginResult.isSuccessful() && loginResult.getPrincipal() != null) {
                return loginResult.getPrincipal().getName();
            }
        } catch (AccessClientException e) {
            ExceptionLogUtils.debugOrError(log, "Failed to authenticate user by access token", e);
        }
        return null;
    }

    @Nullable
    @Override
    public SimpleUser findUserByProperty(String key, String val, boolean exactKeyMatch) {
        UserInfo userInfo = userGroupStoreService.findUserByProperty(key, val, exactKeyMatch);
        if (userInfo != null) {
            return new SimpleUser(userInfo.getUsername(), userInfo.getPassword(), userInfo.getEmail());
        }
        return null;
    }

    @Override
    public boolean addUserProperty(String username, String key, String value) {
        return userGroupStoreService.addUserProperty(username, key, value);
    }

    @Override
    public boolean deleteUserProperty(String username, String propKey) {
        return userGroupStoreService.deleteUserProperty(username, propKey);
    }

    @Override
    public String encryptIfNeeded(String password) {
        return CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), password);
    }

    @Override
    public String decryptIfNeeded(String password) {
        return CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), password);
    }

    @Override
    public String createTokenAsSystem(String userName, PackageTokenType packageTokenType) throws Exception {
        TokenType tokenType = mapToTokenType(packageTokenType);
        TokenKeyValue tokenKeyValue =
                securityService.callAsSystem(() -> tokenManager.createToken(new TokenManagerKey(userName, tokenType)));
        OauthModel oauthModel =
                new AuthenticationModel(tokenKeyValue.getToken(), DateUtils.formatBuildDate(System.currentTimeMillis()),
                        tokenKeyValue.getExpiresIn());
        return tokenFromOAuthModel(oauthModel);
    }

    @Override
    public String createRepositoryScopedTokenAsSystem(@NonNull Collection<ArtifactScopeTarget> targets,
            @NonNull String subjectUsername, @NonNull Set<ArtifactScopeAction> actions, long expiresInSeconds)
            throws PackageException {
        if (targets.isEmpty()) {
            throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, SCOPED_TOKEN_CREATION_FAILED,
                    "At least one scope target is required");
        }
        for (ArtifactScopeTarget target : targets) {
            if (target == null || StringUtils.isBlank(target.getRepoKey())) {
                throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, SCOPED_TOKEN_CREATION_FAILED,
                        "Each scope target requires a repository key");
            }
        }
        if (actions.isEmpty()) {
            throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, SCOPED_TOKEN_CREATION_FAILED,
                    "At least one action is required");
        }
        if (StringUtils.isBlank(subjectUsername)) {
            throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, SCOPED_TOKEN_CREATION_FAILED,
                    "A subject username is required");
        }
        List<String> scopes = buildArtifactScopes(targets, actions);
        try {
            return securityService.callAsSystem(() -> {
                TokenRequest tokenRequest = TokenRequest.builder()
                        .subject(buildSubject(subjectUsername))
                        .scopes(scopes)
                        .audience(accessService.getArtifactoryServiceId())
                        .expiresIn(expiresInSeconds)
                        .refreshable(false)
                        .build();
                return accessService.getAccessClient().token().create(tokenRequest).getTokenValue();
            });
        } catch (Exception e) {
            log.warn(SCOPED_TOKEN_CREATION_FAILED + " for scopes '{}': {}", scopes, e.getMessage());
            log.debug(SCOPED_TOKEN_CREATION_FAILED + " for scopes '{}'", scopes, e);
            throw new PackageException(HttpStatus.SC_INTERNAL_SERVER_ERROR, SCOPED_TOKEN_CREATION_FAILED,
                    SCOPED_TOKEN_CREATION_FAILED, e);
        }
    }

    /**
     * Artifactory rejects a subject-less token as a credential, so the token must name someone even though the
     * grant comes from its scope rather than from that user's own permissions. Access expects the fully qualified
     * principal rather than the bare username.
     */
    private String buildSubject(String subjectUsername) {
        return accessService.getArtifactoryServiceId().getFormattedName() + "/users/" + subjectUsername;
    }

    /**
     * A {@link PermissionScope} holds a single action, so one scope is emitted per granted action on each target.
     * Access builds a matcher per scope and asks each whether it covers the required action, which makes this
     * equivalent to listing the action letters together in one scope.
     * <p>
     * Iteration goes through an {@link EnumSet} copy so the scope order follows the enum declaration rather than
     * the caller's set implementation. The copy requires {@code actions} to be non-empty, which the caller
     * validates.
     */
    private List<String> buildArtifactScopes(Collection<ArtifactScopeTarget> targets,
            Set<ArtifactScopeAction> actions) {
        Set<ArtifactScopeAction> orderedActions = EnumSet.copyOf(actions);
        List<String> scopes = new ArrayList<>();
        for (ArtifactScopeTarget target : targets) {
            String repo = removeDashCache(target.getRepoKey());
            String pathPattern = resolvePathPattern(target.getPath());
            for (ArtifactScopeAction action : orderedActions) {
                scopes.add(createPermissionScope(repo, pathPattern, action).toString());
            }
        }
        return scopes;
    }

    /**
     * Blank path means every artifact under the repository. A concrete path is granted recursively under that
     * prefix. The recursive wildcard is required: without it the scope matches only the repository (or path) root,
     * and a single-asterisk one would match only its direct children rather than artifacts nested at any depth.
     */
    private static String resolvePathPattern(@Nullable String path) {
        if (StringUtils.isBlank(path)) {
            return RECURSIVE_PATH_WILDCARD;
        }
        String normalized = StringUtils.strip(path, "/");
        if (RECURSIVE_PATH_WILDCARD.equals(normalized) || normalized.endsWith("/" + RECURSIVE_PATH_WILDCARD)) {
            return normalized;
        }
        return normalized + "/" + RECURSIVE_PATH_WILDCARD;
    }

    private PermissionScope createPermissionScope(String repoKey, String pathPattern, ArtifactScopeAction action) {
        return PermissionScope.create(PermissionScopeResourceType.ARTIFACT, repoKey, pathPattern,
                Action.fromCode(action.getLetter()));
    }

    public String getToken(PackageAuthTokenCacheKey cacheKey) {
        TokenType tokenType = mapToTokenType(cacheKey.getTokenType());
        ArtifactoryCacheKey artifactoryCacheKey = new PackageHandlerCacheKey(
                cacheKey.getUser(), cacheKey.getAuthHeader(), cacheKey.getRepoKey(), tokenType);
        return tokenFromOAuthModel(artifactoryTokenProvider.getToken(artifactoryCacheKey));
    }

    @NonNull
    private TokenType mapToTokenType(PackageTokenType packageTokenType) throws IllegalArgumentException {
        return TokenType.valueOf(packageTokenType.name());
    }

    @NonNull
    private String tokenFromOAuthModel(OauthModel oauthModel) throws WebApplicationException {
        if (oauthModel instanceof AuthenticationModel authenticationModel) {
            return authenticationModel.getToken();
        } else if (oauthModel instanceof OauthErrorModel oauthErrorModel) {
            log.debug("An error occurred while trying to generate token");
            throw new WebApplicationException(errorResponse(oauthErrorModel));
        }
        log.debug("Unexpected OauthModel: {}", oauthModel == null ? null : oauthModel.getClass());
        throw new WebApplicationException(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    private Response errorResponse(OauthErrorModel errorModel) {
        int status = errorModel.getStatusCode();
        String message = errorModel.getMessage();
        return Response.status(status).entity(message).build();
    }

    /**
     * Generating Access token for the user with following specs: Expired in
     * {@link ConstantValues#genericTokensExpirationTimeSecs}, non-Refreshable, no groups' limitation.
     * We set the scope to be "member-of-groups:*" since it will allow the user to obtain groups access according
     * to his group memberships AT THE TIME OF EACH LOGIN.
     */
    private String generateAccessTokenForUser(String username) throws PackageException {
        // Creating the token to be valid + with no specific groups limitation
        try {
            UserTokenSpec tokenSpec = UserTokenSpec.create(username)
                    .expiresIn(ConstantValues.genericTokensExpirationTimeSecs.getLong())
                    .refreshable(false)
                    .scope(Lists.newArrayList("member-of-groups:*"));
            return accessService.createToken(tokenSpec, true, false).getTokenValue();
        } catch (Exception e) {
            log.warn(LOCAL_TOKEN_CREATION_FAILED + ": {}", e.getMessage());
            log.debug("Local token creation failed for user: '{}'", username, e);
            throw new PackageException(HttpStatus.SC_BAD_REQUEST, LOCAL_TOKEN_CREATION_FAILED,
                    LOCAL_TOKEN_CREATION_FAILED);
        }
    }

    @Override
    public boolean shouldHideUnauthorizedResources() {
        return securityService.shouldHideUnauthorizedResources();
    }

    @Override
    public String decryptStringWithPbeKey(String content) {
        if (secretKey == null) {
            log.debug("Secret key is null, returning original content");
            return content;
        }
        return new String(JFrogCryptoHelper.decryptSymmetric(Base64.getDecoder().decode(content), secretKey),
                StandardCharsets.UTF_8);
    }

    public String encryptStringWithPbeKey(String content) {
        if (secretKey == null) {
            return content;
        }
        return Base64.getEncoder().encodeToString(
                JFrogCryptoHelper.encryptSymmetric(content.getBytes(StandardCharsets.UTF_8), secretKey));
    }
}
