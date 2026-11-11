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

package org.artifactory.storage.db.itest;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.ProxyTokenResponse;
import org.artifactory.api.security.access.TokenInfo;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.access.AccessClientUpdateListener;
import org.artifactory.security.access.AccessService;
import org.artifactory.version.CompoundVersionDetails;
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
import org.jfrog.common.config.diff.DataDiff;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * @author Yinon Avraham.
 */
public class DummyAccessService implements AccessService {

    @Override
    public void initAccessService(String oldServiceId) {
        throw unexpectedMethodCallException();
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec) {
        throw unexpectedMethodCallException();
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createCrossPlatformToken(
            @Nonnull TokenSpec tokenSpec, @Nonnull String serviceId) {
        throw unexpectedMethodCallException();
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec,
            boolean skipUserCanCreateLongLivedTokenAssertion,
            boolean skipAcceptedScopeAssertion) {
        throw unexpectedMethodCallException();
    }

    @Override
    public ProxyTokenResponse createTokenAsProxy(TokenSpec<?> tokenSpec, String username, String grantType) {
        return null;
    }

    @Nonnull
    @Override
    public TokenResponse createTokenWithAccessAdminCredentials(@Nonnull String serviceId) {
        return null;
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createNoPermissionToken(@Nonnull List<String> scope,
            @Nonnull TokenSpec tokenSpec, @Nullable String extraData) {
        throw unexpectedMethodCallException();
    }

    @Nonnull
    @Override
    public CreatedTokenInfo refreshToken(@Nonnull TokenSpec tokenSpec, @Nonnull String tokenValue,
            @Nonnull String refreshToken) {
        throw unexpectedMethodCallException();
    }

    @Nullable
    @Override
    public String extractSubjectUsername(@Nonnull JwtAccessToken accessToken) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void revokeToken(@Nonnull String tokenValue) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void revokeTokenById(@Nonnull String tokenId) {
        throw unexpectedMethodCallException();
    }

    @Nonnull
    @Override
    public JwtAccessToken parseToken(@Nonnull String tokenValue) throws IllegalArgumentException {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean verifyToken(@Nonnull JwtAccessToken accessToken) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean verifyBackOfficeToken(@Nonnull JwtAccessToken accessToken) {
        throw unexpectedMethodCallException();
    }

    @Nonnull
    @Override
    public AccessTokenType getAccessTokenType(@Nonnull String tokenValue) {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean verifyTokenIfServiceIdChanged(@Nonnull JwtAccessToken accessToken) {
        return false;
    }

    @Override
    public TokenVerifyResult verifyAndGetResult(@Nonnull JwtAccessToken accessToken) {
        throw unexpectedMethodCallException();
    }

    @Override
    public TokenVerifyResult verifyAndGetResult(@Nonnull String accessToken) {
        throw unexpectedMethodCallException();
    }


    @Nonnull
    @Override
    public ServiceId getArtifactoryServiceId() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isTokenAppliesScope(@Nonnull JwtAccessToken accessToken, @Nonnull String requiredScope) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void registerAcceptedScopePattern(@Nonnull Pattern pattern) {
        throw unexpectedMethodCallException();
    }

    @Nonnull
    @Override
    public List<TokenInfo> getTokenInfos() {
        throw unexpectedMethodCallException();
    }

    @Override
    public AccessClient getAccessClient() {
        throw unexpectedMethodCallException();
    }

    @Override
    public AuthSettingsClient getAccessAuthSettingsClient() {
        throw unexpectedMethodCallException();
    }

    @Override
    public GrpcAuthClient getGrpcAuthClient() {
        throw unexpectedMethodCallException();
    }

    @Override
    public GrpcPlatformConfigClient getPlatformConfigClient() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void importSecurityEntities(SecurityInfo securityInfo, boolean override) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void updateConfigurationModel(Map<String, Object> accessConfiguration) throws AccessClientException {
        throw unexpectedMethodCallException();
    }

    @Override
    public int moveYamlConfigResourcesToAccess(Map<String, Object> hashMap) {
        throw unexpectedMethodCallException();
    }

    @Override
    public UserLockPolicyConfig getUserLockPolicyConfig() {
        throw unexpectedMethodCallException();
    }

    @Override
    public boolean isAnonymousAccessEnabled() {
        return false;
    }

    @Override
    public void ping() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void readiness() {
        throw unexpectedMethodCallException();
    }

    @Override
    public void afterImport(ImportSettings settings) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void revokeAllForUserAndScope(String username, String scope) {

    }

    @Override
    public boolean isAdminUsingOldDefaultPassword() {
        return false;
    }

    @Override
    public void invalidateAdminUsingOldDefaultPasswordCache() {
    }

    @Override
    public <T> T ensureAuth(Callable<T> call) {
        return null;
    }

    @Override
    public void exportTo(ExportSettings settings) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void importFrom(ImportSettings settings) {
        throw unexpectedMethodCallException();
    }

    @Override
    public void unregisterFromRouterIfNeeded() {
        throw unexpectedMethodCallException();
    }

    private RuntimeException unexpectedMethodCallException() {
        return new UnsupportedOperationException(
                "This is a dummy implementation, none of its methods should be called!");
    }

    @Override
    public void init() {

    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {

    }

    @Override
    public void destroy() {

    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {

    }

    @Override
    public void subscribeForClientChanges(AccessClientUpdateListener listener) {

    }

    @Override
    public void setSubscriptionTypeOnAccess() {

    }

    @Override
    public void activateUserWithOAuthProvider(@Nonnull String activationToken, @Nonnull String oauthProviderKey,
            @Nonnull String oauthProviderUserId) {

    }

    @Override
    public boolean isMtlsEnabled() {
        return false;
    }

    @Override
    public MTlsConfigurationModel getMtlsConfigurationModel() {
        return null;
    }


    @Override
    public boolean isBasicAuthEnabled() {
        return false;
    }

    @Override
    public boolean isEncryptionRequired() {
        return false;
    }

    @Override
    public boolean isEncryptionEnabled() {
        return false;
    }

    @Override
    public EncryptionPolicy getEncryptionPolicy() {
        return null;
    }

    @Override
    public boolean isRealmAuthenticationEnabled(Realm realm) {
        return true;
    }

    @Override
    public String extractMtlsUsername(@Nonnull HttpServletRequest request) {
        return null;
    }

    @Override
    public boolean isLdapEnabled() {
        return false;
    }

    @Override
    public List<LdapSetting> getLdapSettings() {
        return List.of();
    }

    @Override
    public boolean isLdapSettingsExist(String key) {
        return false;
    }

    @Override
    public LdapSetting getLdapSettings(String ldapKey) {
        return new LdapSetting();
    }

    @Override
    public void upsertCrowdSettings(CrowdSetting crowdSetting) {

    }

    @Override
    public void upsertLdapSettings(List<LdapSetting> ldapSettings) {
    }

    @Override
    public void upsertLdapSetting(LdapSetting ldapSetting) {
    }

    @Override
    public LdapSetting removeLdapSetting(String ldapKey) {
        return new LdapSetting();
    }

    @Override
    public List<LdapSetting> getEnabledLdapSettings() {
        return List.of();
    }

    @Override
    public List<LdapGroupSetting> getLdapGroupSettings() {
        return List.of();
    }

    @Override
    public LdapGroupSetting getLdapGroupSettings(String name) {
        return new LdapGroupSetting();
    }

    @Override
    public List<LdapGroupSetting> getEnabledLdapGroupSettings() {
        return List.of();
    }

    @Override
    public void upsertLdapGroupSetting(LdapGroupSetting ldapGroupSetting) {
    }

    @Override
    public void upsertLdapGroupSettings(List<LdapGroupSetting> ldapGroupSettings) {
    }

    @Override
    public LdapGroupSetting removeLdapGroupOnly(String ldapGroupName) {
        return new LdapGroupSetting();
    }

    @Override
    public void removeLdapGroupsByEnabledLdapKey(String ldapSettingKey) {
    }

    @Override
    public Optional<CrowdSetting> getCrowdSettings() {
        return Optional.empty();
    }

    @Override
    public Optional<HttpSsoSetting> getHttpSsoSettings() {
        return Optional.empty();
    }

    @Override
    public void upsertHttpSsoSettings(HttpSsoSetting httpSsoSetting) {

    }

    @Override
    public void subscribeToAuthSettingChanges(Runnable run, Realm realm) {

    }

    @Override
    public boolean isSamlEnabled() {
        return false;
    }

    @Override
    public boolean isSamlEnabled(boolean checkGlobalSettings) {
        return false;
    }

    @Nonnull
    @Override
    public SamlSetting getSamlSettings() {
        return null;
    }

    @Override
    public void upsertSamlSetting(SamlSetting ldapSetting) {

    }

    @Override
    public boolean shouldDelegateTokenOrApiKeyAuthenticationToAccess() {
        return false;
    }

    @Override
    public void upsertSamlEncryptedAssertion(SamlEncryptedAssertion samlEncryptedAssertion) {

    }

    @Override
    public OAuthGeneralSettings getOAuthGeneralSettings() {
        return null;
    }

    @Override
    public List<OAuthSetting> getAllOAuthSettings() {
        return List.of();
    }

    @Override
    public Optional<OAuthSetting> getOAuthSetting(String name) {
        return Optional.empty();
    }

    @Override
    public boolean isOAuthEnabled() {
        return false;
    }

    @Override
    public void upsertOAuthGeneralSetting(OAuthGeneralSettings oAuthGeneralSettings) {

    }

    @Override
    public void upsertOAuthSettings(@Nonnull List<OAuthSetting> oAuthSettingsList) {

    }

    @Override
    public void removeOAuthSetting(@Nonnull String name) {

    }
}
