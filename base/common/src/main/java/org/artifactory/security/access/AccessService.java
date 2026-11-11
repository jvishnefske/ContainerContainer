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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.config.ImportableExportable;
import org.artifactory.api.security.AuthorizationException;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.ProxyTokenResponse;
import org.artifactory.api.security.access.TokenInfo;
import org.artifactory.api.security.access.TokenNotFoundException;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.SecurityInfo;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.auth.GrpcAuthClient;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.SamlEncryptedAssertion;
import org.jfrog.access.client.model.SamlSetting;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.model.Realm;
import org.jfrog.access.rest.system.MTlsConfigurationModel;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * @author Yinon Avraham
 */
public interface AccessService extends ImportableExportable, ReloadableBean {

    void initAccessService(String oldServiceId);

    /**
     * Create an access token according to the given specification, using the Access client interface.
     *
     * @param tokenSpec the specification for the token to create
     * @return the access token information
     */
    @Nonnull
    CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec);

    /**
     * Create an access token according to the given specification delivered straight to Access,
     * treating to RT as a proxy and sending POST request to Access for the token creation
     * @param tokenSpec the specification for the token to create
     * @param username the user on which the token will be registered
     * @param grantType used to authenticate the request
     * @return the access token information as retrieved from POST API call to Access
     */
    ProxyTokenResponse createTokenAsProxy(@Nonnull TokenSpec<?> tokenSpec, String username, String grantType);

    /**
     * Create an access token according to the given specification for federation (cross platform).
     *
     * @param tokenSpec the specification for the token to create
     * @return the access token information
     */
    @Nonnull
    CreatedTokenInfo createCrossPlatformToken(@Nonnull TokenSpec tokenSpec, @Nonnull String subject);

    @Nonnull
    CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec,
            boolean skipUserCanCreateLongLivedTokenAssertion,
            boolean skipAcceptedScopeAssertion);

    /**
     * Create token with access admin credentials to support creating token for other services
     *
     * @see AccessService#createToken(TokenSpec)
     */
    @Nonnull
    TokenResponse createTokenWithAccessAdminCredentials(@Nonnull String serviceId);

    /**
     * Create a general token without permissions.
     * Used for authenticates generic data not related for user access privileges
     * Ignores the token scope
     *
     * @param tokenSpec the specification for the token to create
     * @param extraData token extra data
     * @return the access token information
     */
    @Nonnull
    CreatedTokenInfo createNoPermissionToken(@Nonnull List<String> scope, @Nonnull TokenSpec tokenSpec,
            @Nullable String extraData);

    /**
     * Refresh an access token according to the given specification.
     *
     * @param tokenSpec    the specification for the token to create
     * @param tokenValue   the access token value which should be refreshed
     * @param refreshToken the refresh token which identifies an existing token
     * @return the new access token information
     * @throws TokenNotFoundException if the either of the given access or refresh tokens were not found
     * @throws AuthorizationException if the access server refused to refresh the token,
     *                                giving a forbidden response.
     */
    @Nonnull
    CreatedTokenInfo refreshToken(@Nonnull TokenSpec tokenSpec, @Nonnull String tokenValue,
            @Nonnull String refreshToken);

    /**
     * Extract the username from the subject of the given access token.
     *
     * @param accessToken the access token from which to extract the subject username
     * @return the username, or <code>null</code> if the subject does not represent a user.
     */
    @Nullable
    String extractSubjectUsername(@Nonnull JwtAccessToken accessToken);

    /**
     * Revoke a token
     *
     * @param tokenValue the token to revoke
     * @throws TokenNotFoundException if the given token was not found and hence could not be revoked
     */
    void revokeToken(@Nonnull String tokenValue);

    /**
     * Revoke a token by ID
     *
     * @param tokenId the ID of the token to revoke
     * @throws TokenNotFoundException if the given token ID was not found and hence could not be revoked
     */
    void revokeTokenById(@Nonnull String tokenId);

    /**
     * Parse the given token value
     *
     * @param tokenValue the token value to parse
     * @return the parsed token
     * @throws IllegalArgumentException if the given value is not a legal token value
     */
    @Nonnull
    JwtAccessToken parseToken(@Nonnull String tokenValue) throws IllegalArgumentException;

    /**
     * Verify (authenticate) the given access token
     *
     * @param accessToken the access token to verify
     * @return {@code true} if the token is verified, {@code false} otherwise (e.g. expired).
     */
    boolean verifyToken(@Nonnull JwtAccessToken accessToken);

    /**
     * Verify (authenticate) the given access token
     *
     * @param accessToken the access token to verify
     * @return {@code true} if the token is verified, {@code false} otherwise (e.g. expired).
     */
    boolean verifyBackOfficeToken(@Nonnull JwtAccessToken accessToken);

    /**
     * Get access token type
     *
     * @param tokenValue the token value to check
     * @return token type
     */
    @Nonnull
    AccessTokenType getAccessTokenType(@Nonnull String tokenValue);

    /**
     * Calling Verify (authenticate) the given access token if serviceId had Changed
     *
     * @param accessToken the access token to verify
     * @return {@code true} if the token is verified, {@code false} otherwise (e.g. expired).
     */
    boolean verifyTokenIfServiceIdChanged(@Nonnull JwtAccessToken accessToken);

    /**
     * Verify (authenticate) the given access token and returns the result. Result might be success of failure.
     * In case of failure to verify, the reason can be extracted from the result.
     *
     * @param accessToken the access token to verify
     * @return the verification result
     */
    TokenVerifyResult verifyAndGetResult(@Nonnull JwtAccessToken accessToken);

    /**
     * Verify (authenticate) the given access token and returns the result. Result might be success of failure.
     * In case of failure to verify, the reason can be extracted from the result.
     *
     * @param accessToken the access token to verify in string
     * @return the verification result
     */
    TokenVerifyResult verifyAndGetResult(@Nonnull String accessToken);

    /**
     * Get artifactory's access service ID (standalone or cluster)
     */
    @Nonnull
    ServiceId getArtifactoryServiceId();

    /**
     * Check whether the given access token applies (provides) the given scope.
     *
     * @param accessToken   the access token to check
     * @param requiredScope the required scope
     */
    boolean isTokenAppliesScope(@Nonnull JwtAccessToken accessToken, @Nonnull String requiredScope);

    /**
     * Register a pattern as an accepted scope pattern
     *
     * @param pattern the pattern to register
     */
    void registerAcceptedScopePattern(@Nonnull Pattern pattern);

    /**
     * Get the information of all the tokens issued by this service (except for internal tokens)
     *
     * @see #getArtifactoryServiceId()
     */
    @Nonnull
    List<TokenInfo> getTokenInfos();

    /**
     * @return The JFrog Access client that connects to the main Access server of Artifactory
     */
    AccessClient getAccessClient();

    GrpcPlatformConfigClient getPlatformConfigClient();

    /**
     * @return The JFrog Access GRPC authentication client
     */
    GrpcAuthClient getGrpcAuthClient();

    AuthSettingsClient getAccessAuthSettingsClient();

    /**
     * Import only security data to access
     *
     * @param securityInfo data to import
     * @param override     whether to override current data or not
     */
    void importSecurityEntities(SecurityInfo securityInfo, boolean override);

    void updateConfigurationModel(Map<String, Object> accessConfiguration) throws AccessClientException;

    int moveYamlConfigResourcesToAccess(Map<String, Object> hashMap);

    UserLockPolicyConfig getUserLockPolicyConfig();

    boolean isAnonymousAccessEnabled();

    boolean isLdapEnabled();

    boolean isLdapSettingsExist(String key);

    List<LdapSetting> getLdapSettings();

    LdapSetting getLdapSettings(String ldapKey);

    void upsertCrowdSettings(CrowdSetting crowdSetting);

    void upsertLdapSettings(List<LdapSetting> ldapSettings);

    void upsertLdapSetting(LdapSetting ldapSetting);

    LdapSetting removeLdapSetting(String ldapKey);

    List<LdapSetting> getEnabledLdapSettings();

    List<LdapGroupSetting> getLdapGroupSettings();

    LdapGroupSetting getLdapGroupSettings(String name);

    List<LdapGroupSetting> getEnabledLdapGroupSettings();

    void upsertLdapGroupSetting(LdapGroupSetting ldapGroupSetting);

    void upsertLdapGroupSettings(List<LdapGroupSetting> ldapGroupSettings);

    /**
     * Remove LDAP groups only (without associated settings)
     *
     * @param ldapGroupName LDAP group name to remove
     * @return the removed LDAP group setting
     */
    LdapGroupSetting removeLdapGroupOnly(String ldapGroupName);

    /**
     * Remove LDAP groups by their associated 'enabledLdap' field
     *
     * @param ldapSettingKey LDAP key remove groups by
     */
    void removeLdapGroupsByEnabledLdapKey(String ldapSettingKey);

    Optional<CrowdSetting> getCrowdSettings();

    Optional<HttpSsoSetting> getHttpSsoSettings();

    void upsertHttpSsoSettings(HttpSsoSetting httpSsoSetting);

    void subscribeToAuthSettingChanges(Runnable run, Realm realm);

    boolean isSamlEnabled();

    boolean isSamlEnabled(boolean checkGlobalSettings);

    @Nonnull
    SamlSetting getSamlSettings();

    void upsertSamlSetting(SamlSetting ldapSetting);

    void upsertOAuthGeneralSetting(OAuthGeneralSettings oAuthGeneralSettings);

    void upsertOAuthSettings(@Nonnull List<OAuthSetting> oAuthSettingsList);

    void removeOAuthSetting(@Nonnull String name);

    /**
     * Pings Access and verifies it is alive
     *
     * @throws AccessClientException in case the ping failed
     */
    void ping();

    void readiness();

    void afterImport(ImportSettings settings);

    /**
     * revoke all tokens for user with provided username and scope
     */
    void revokeAllForUserAndScope(String username, String scope);

    /**
     * Query for a security issue to determine if a access admin using old and unsecured password.
     */
    boolean isAdminUsingOldDefaultPassword();

    /**
     * Invalidate the access admin using old and unsecured password cache.
     */
    void invalidateAdminUsingOldDefaultPasswordCache();

    /**
     * If call fails with 401 AccessClientHttpException, wait for
     * {@link AccessServiceImpl#AUTH_FAILURE_RETRY_INTERVAL} milliseconds and retry iteratively
     * {@link AccessServiceImpl#authFailureMaxRetry()}. times
     */
    <T> T ensureAuth(Callable<T> call);

    /**
     * Unregister from router, registration is done during the bootstrap process
     */
    void unregisterFromRouterIfNeeded();

    /**
     * Subscribe for access client update event
     */
    void subscribeForClientChanges(AccessClientUpdateListener listener);

    void setSubscriptionTypeOnAccess();

    void activateUserWithOAuthProvider(@Nonnull String activationToken, @Nonnull String oauthProviderKey,
            @Nonnull String oauthProviderUserId);

    boolean isMtlsEnabled();

    String extractMtlsUsername(@Nonnull HttpServletRequest request);

    MTlsConfigurationModel getMtlsConfigurationModel();

    boolean isBasicAuthEnabled();

    boolean isEncryptionRequired();

    boolean isEncryptionEnabled();

    EncryptionPolicy getEncryptionPolicy();

    /**
     * Check if the provided realm authentication is enabled by Access
     *
     * @param realm the realm to check if authentication is enabled for
     * @return true if the provided realm authentication is enabled, false otherwise.
     */
    boolean isRealmAuthenticationEnabled(Realm realm);

    default boolean isAnyExternalAuthEnabled() {
        return false;
    }

    boolean shouldDelegateTokenOrApiKeyAuthenticationToAccess();

    default void updateConfigurationToAccess() {
    }

    void upsertSamlEncryptedAssertion(SamlEncryptedAssertion samlEncryptedAssertion);

    boolean isOAuthEnabled();

    OAuthGeneralSettings getOAuthGeneralSettings();

    List<OAuthSetting> getAllOAuthSettings();

    Optional<OAuthSetting> getOAuthSetting(String name);

}
