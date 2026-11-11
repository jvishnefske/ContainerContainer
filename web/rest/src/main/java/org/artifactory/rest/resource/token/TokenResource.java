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

package org.artifactory.rest.resource.token;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.AuthorizationException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.ProxyTokenResponse;
import org.artifactory.api.security.access.TokenInfo;
import org.artifactory.api.security.access.TokenIssuedByOtherServiceException;
import org.artifactory.api.security.access.TokenNotFoundException;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.token.exception.TokenRequestException;
import org.jfrog.access.common.SubjectFQN;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.exception.TokenScopeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.api.security.AuthorizationService.ROLE_ADMIN;
import static org.artifactory.api.security.AuthorizationService.ROLE_USER;
import static org.artifactory.common.ConstantValues.accessTokenProxyGenerationToAccess;
import static org.artifactory.common.ConstantValues.accessTokenRejectServiceSubject;
import static org.artifactory.rest.resource.token.TokenResource.PATH_ROOT;
import static org.artifactory.security.access.AccessServiceConstants.ARTIFACTORY_SERVICE_ANY_ID_PATTERN;
import static org.artifactory.security.token.TokenResponseErrorCode.InvalidGrant;
import static org.artifactory.security.token.TokenResponseErrorCode.InvalidRepoPath;
import static org.artifactory.security.token.TokenResponseErrorCode.InvalidRequest;
import static org.artifactory.security.token.TokenResponseErrorCode.InvalidScope;
import static org.artifactory.security.token.model.TokenUtils.assertSupportedTokenSpecForCreate;
import static org.artifactory.security.token.model.TokenUtils.audienceToList;
import static org.artifactory.security.token.model.TokenUtils.createTokenSpec;
import static org.artifactory.security.token.model.TokenUtils.expiresInOrDefault;
import static org.artifactory.security.token.model.TokenUtils.requireUsername;
import static org.jfrog.access.common.ScopeContainer.INTERNAL_SCOPE_PREFIX;
import static org.jfrog.access.util.TokenScopeUtils.scopeToList;

/**
 * @author Yinon Avraham
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(PATH_ROOT)
@RolesAllowed({ROLE_ADMIN, ROLE_USER})
public class TokenResource {

    public static final String PATH_ROOT = "security/token";
    private static final Logger log = LoggerFactory.getLogger(TokenResource.class);
    private static final Set<String> REQUIRED_REFRESH_TOKEN_REQUEST_PARAMS = ImmutableSet
            .of("grant_type", "refresh_token", "access_token");
    private static final Set<String> OPTIONAL_REFRESH_TOKEN_REQUEST_PARAMS = ImmutableSet.of("expires_in");

    @Autowired
    private AccessService accessService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private AddonsManager addonsManager;
    @Context
    private HttpServletRequest request;

    @Deprecated(since = "7.117.x")
    @POST
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Produces(APPLICATION_JSON)
    public Response createOrRefreshToken(
            @FormParam("grant_type") String grantTypeName,
            @FormParam("username") String username,
            @FormParam("refresh_token") String refreshToken,
            @FormParam("access_token") String accessToken,
            @FormParam("scope") String scope,
            @FormParam("expires_in") Long expiresIn,
            @FormParam("refreshable") @DefaultValue("false") boolean refreshable,
            @FormParam("audience") String audience) {
        GrantType grantType = requireGrantType(grantTypeName);
        assertPermittedOperation(grantType);
        assertNotReferenceToken();
        long expiryDefault = getExpiryDefault(accessToken, StringUtils.isNotBlank(refreshToken));
        long targetExpiresIn = expiresInOrDefault(expiresIn, expiryDefault);
        List<String> scopeTokens;
        try {
            scopeTokens = scopeToList(scope);
        } catch (TokenScopeException e) {
            throw new TokenRequestException(InvalidScope, e.getMessage());
        }
        if (scopeTokens.stream().anyMatch(s -> s.startsWith(INTERNAL_SCOPE_PREFIX))) {
            throw new TokenRequestException(InvalidScope, "invalid scope: internal");
        }
        List<String> audienceList = audienceToList(audience);
        try {
            TokenSpec tokenSpec = createTokenSpec(username)
                    .scope(scopeTokens)
                    .expiresIn(targetExpiresIn)
                    .refreshable(refreshable)
                    .audience(audienceList);
            switch (grantType) {
                case ClientCredentials:
                    return createToken(grantTypeName, username, tokenSpec);
                case RefreshToken:
                    return refreshToken(tokenSpec, accessToken, refreshToken);
                default:
                    throw new GrantTypeNotSupportedException(grantType.getSignature());
            }
        } catch (IllegalArgumentException | AuthorizationException e) {
            throw new TokenRequestException(InvalidRequest, e);
        } catch (NotFoundException e) {
            throw new TokenRequestException(InvalidRepoPath, e);
        }
    }

    private Response createToken(String grantTypeName, String username, TokenSpec<?> tokenSpec) {
        assertAllowedSubject(username);
        try {
            Response tokenCreatedByAccess = createTokenByProxyToAccess(tokenSpec, username, grantTypeName);
            if (accessTokenProxyGenerationToAccess.getBoolean()) {
                return tokenCreatedByAccess;
            }
        } catch (Exception e) {
            log.warn("Failed to create token by proxy to Access. Using Artifactory's token instead.", e);
            if (accessTokenProxyGenerationToAccess.getBoolean()) {
                throw e;
            }
        }
        assertSupportedTokenSpecForCreate(tokenSpec, accessService.getArtifactoryServiceId().getFormattedName());
        requireUsername(username);
        return createToken(tokenSpec);
    }

    private long getExpiryDefault(String accessToken, boolean refreshToken) {
        if (refreshToken && StringUtils.isNotBlank(accessToken)) {
            try {
                JwtAccessToken jwtAccessToken = accessService.parseToken(accessToken);
                Long expirationTime = jwtAccessToken.getExpirationTime();
                if (expirationTime != null) {
                    return TimeUnit.MILLISECONDS.toSeconds(expirationTime - jwtAccessToken.getIssuedAt());
                }
            } catch (IllegalArgumentException e) {
                log.debug("Unable to parse token, assuming default expiration.", e);
            }
        }
        return ConstantValues.accessTokenExpiresInDefault.getLong();
    }

    @RolesAllowed({ROLE_ADMIN})
    @GET
    @Produces(APPLICATION_JSON)
    public Response getTokenInfos() {
        List<TokenInfo> tokens = accessService.getTokenInfos();
        List<TokenInfoModel> result = tokens.stream().map(TokenInfoModel::new).collect(Collectors.toList());
        TokensResponseModel responseModel = new TokensResponseModel();
        responseModel.setTokens(result);
        return Response.ok(responseModel).build();
    }

    private void assertPermittedOperation(@Nonnull GrantType grantType) {
        if (!securityService.isAdmin()) {
            switch (grantType) {
                case RefreshToken:
                    if (nonAdminCanRefreshToken()) {
                        return;
                    }
                    break;
                case ClientCredentials:
                    if (nonAdminCanCreateToken()) {
                        return;
                    }
                    break;
                default:
                    throw new GrantTypeNotSupportedException(grantType.getSignature());
            }
            if (!securityService.isAuthenticated() || securityService.isAnonymous()) {
                throw new WebApplicationException(HttpStatus.SC_UNAUTHORIZED);
            }
            throw new AuthorizationRestException();
        }
    }

    private void assertNotReferenceToken() {
        if (!ConstantValues.blockReferenceTokenOnLegacyTokenEndpoint.getBoolean()) {
            return;
        }
        if (AuthenticationHelper.getAuthentication() instanceof AccessTokenAuthentication authentication) {
            String presentingToken = authentication.getAccessTokenValue();
            if (accessService.getAccessTokenType(presentingToken) == AccessTokenType.REFERENCE_TOKEN) {
                log.warn("Rejected token create/refresh request on legacy endpoint: presenting credential was a "
                        + "reference token for user '{}'. Use the Access API instead.", authentication.getName());
                throw new TokenRequestException(InvalidRequest, "This endpoint does not support reference tokens as "
                        + "the bearer credential. Use the Access API to create or refresh tokens.");
            }
        }
    }

    private boolean nonAdminCanCreateToken() {
        return securityService.isAuthenticated() && !securityService.isAnonymous();
    }

    private boolean nonAdminCanRefreshToken() {
        if (isSimpleRefreshTokenRequestParams()) {
            log.debug("Simple refresh token request - no need to authenticate user, does not require admin");
            return true;
        }
        return false;
    }

    private boolean isSimpleRefreshTokenRequestParams() {
        Set<String> paramNames = Sets.newHashSet(Collections.list(request.getParameterNames()));
        if (!paramNames.containsAll(REQUIRED_REFRESH_TOKEN_REQUEST_PARAMS)) {
            // One of the required params is missing
            return false;
        }
        paramNames.removeAll(REQUIRED_REFRESH_TOKEN_REQUEST_PARAMS);
        for (String param : paramNames) {
            if (!OPTIONAL_REFRESH_TOKEN_REQUEST_PARAMS.contains(param)) {
                // Param is not allowed.
                return false;
            }
        }
        return true;
    }

    /**
     * Revoke a token by the access token or refresh token
     * See <a href="https://tools.ietf.org/html/rfc7009">RFC7009 - Section 2 - Token Revocation</a>
     * Another option is to give the token ID. This option and the first option (by token & hint) are mutually
     * exclusive.
     *
     * @param token         the access token or refresh token to revoke
     * @param tokenTypeHint the token type hint (access token or refresh token)
     * @param tokenId       the ID of the token to revoke
     */
    @POST
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Produces({TEXT_PLAIN, APPLICATION_JSON})
    @Path("revoke")
    @RolesAllowed({ROLE_ADMIN})
    public Response revokeToken(
            @FormParam("token") String token,
            @FormParam("token_type_hint") String tokenTypeHint,
            @FormParam("token_id") String tokenId) {
        RevokeTokenOption option = assertValidRevokeTokenRequest(token, tokenTypeHint, tokenId);
        TokenType tokenType = tokenTypeHint == null ? TokenType.AccessToken : TokenType.fromSignature(tokenTypeHint);
        //The token type hint is ignored for now... Just validate it in case it is provided.
        String successMessage = "Token revoked";
        try {
            switch (option) {
                case Token:
                    accessService.revokeToken(token);
                    break;
                case TokenId:
                    accessService.revokeTokenById(tokenId);
                    break;
                default:
                    log.warn("Unexpected revoke token option: {}", option);
                    throw new TokenRequestException(InvalidRequest, "token or token_id are required");
            }
        } catch (TokenIssuedByOtherServiceException e) {
            throw new TokenRequestException(InvalidRequest, e);
        } catch (TokenNotFoundException e) {
            log.warn("Ignoring revoke token request, token not found.");
            log.debug("Ignoring revoke token request, token not found.", e);
            successMessage = "Token not found";
        }
        return Response.ok(successMessage).type(MediaType.TEXT_PLAIN_TYPE).build();
    }

    private RevokeTokenOption assertValidRevokeTokenRequest(String token, String tokenTypeHint, String tokenId) {
        if (isNotBlank(token)) {
            if (isNotBlank(tokenId)) {
                throw new TokenRequestException(InvalidRequest, "token and token_id are mutually exclusive");
            }
            return RevokeTokenOption.Token;
        }
        if (isNotBlank(tokenId)) {
            return RevokeTokenOption.TokenId;
        }
        throw new TokenRequestException(InvalidRequest, "token or token_id are required");
    }

    private enum RevokeTokenOption {
        Token, TokenId
    }

    private Response refreshToken(@Nonnull TokenSpec tokenSpec, String accessToken, String refreshToken) {
        try {
            requireAccessToken(accessToken);
            requireRefreshToken(refreshToken);
            assertAllowedRefreshSubject(accessToken);
            CreatedTokenInfo createdTokenInfo = accessService.refreshToken(tokenSpec, accessToken, refreshToken);
            TokenResponseModel model = toTokenResponseModel(createdTokenInfo);
            return Response.ok(model).build();
        } catch (TokenNotFoundException | AuthorizationException e) {
            throw new TokenRequestException(InvalidGrant, e);
        } catch (TokenIssuedByOtherServiceException e) {
            throw new TokenRequestException(InvalidRequest, e);
        }
    }

    @Nonnull
    private String requireAccessToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            throw new TokenRequestException(InvalidGrant, "access token is required.");
        }
        return accessToken;
    }

    @Nonnull
    private String requireRefreshToken(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new TokenRequestException(InvalidGrant, "refresh token is required.");
        }
        return refreshToken;
    }

    @Nonnull
    private Response createToken(@Nonnull TokenSpec tokenSpec) {
        CreatedTokenInfo createdTokenInfo = accessService.createToken(tokenSpec);
        TokenResponseModel model = toTokenResponseModel(createdTokenInfo);
        return Response.ok(model).build();
    }

    private Response createTokenByProxyToAccess(TokenSpec<?> tokenSpec, String username, String grantTypeSignature) {
        ProxyTokenResponse tokenAsProxy = accessService.createTokenAsProxy(tokenSpec, username, grantTypeSignature);
        TokenResponseModel model = toProxyTokenResponseModel(tokenAsProxy);
        return Response.ok(model).build();
    }

    @Nonnull
    private TokenResponseModel toTokenResponseModel(CreatedTokenInfo createdTokenInfo) {
        TokenResponseModel model = new TokenResponseModel();
        model.setAccessToken(createdTokenInfo.getTokenValue());
        model.setExpiresIn(createdTokenInfo.getExpiresIn());
        model.setScope(createdTokenInfo.getScope());
        model.setTokenType(createdTokenInfo.getTokenType());
        model.setRefreshToken(createdTokenInfo.getRefreshToken());
        return model;
    }

    @Nonnull
    private TokenResponseModel toProxyTokenResponseModel(ProxyTokenResponse proxyTokenResponse) {
        TokenResponseModel model = new TokenResponseModel();
        model.setAccessToken(proxyTokenResponse.getAccessToken());
        model.setExpiresIn(proxyTokenResponse.getExpiresIn());
        model.setScope(proxyTokenResponse.getScope());
        model.setTokenType(proxyTokenResponse.getTokenType());
        model.setRefreshToken(proxyTokenResponse.getRefreshToken());
        return model;
    }

    @Nonnull
    private GrantType requireGrantType(@Nullable String grantTypeName) {
        if (grantTypeName == null) {
            return GrantType.ClientCredentials;
        }
        return GrantType.fromSignature(grantTypeName);
    }

    private void assertAllowedRefreshSubject(String accessToken) {
        JwtAccessToken parsedToken;
        try {
            parsedToken = accessService.parseToken(accessToken);
        } catch (IllegalArgumentException e) {
            log.debug("Unable to parse token, deferring to the refresh flow.", e);
            return;
        }
        assertAllowedSubject(parsedToken.getSubject());
    }

    private void assertAllowedSubject(String username) {
        if (!GenericTokenSpec.accepts(username) || !accessTokenRejectServiceSubject.getBoolean()) {
            return;
        }
        String serviceId = SubjectFQN.fromFullyQualifiedName(username).getServiceId().getFormattedName();
        if (!UserTokenSpec.isUserTokenSubject(username)
                || !ARTIFACTORY_SERVICE_ANY_ID_PATTERN.matcher(serviceId).matches()) {
            throw new TokenRequestException(InvalidRequest, "Invalid username");
        }
    }
}
