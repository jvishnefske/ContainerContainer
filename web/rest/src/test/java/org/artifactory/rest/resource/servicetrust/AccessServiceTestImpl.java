package org.artifactory.rest.resource.servicetrust;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.ProxyTokenResponse;
import org.artifactory.api.security.access.TokenInfo;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.access.AccessClientUpdateListener;
import org.artifactory.security.access.AccessService;
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

public class AccessServiceTestImpl implements AccessService {
    @Override
    public void exportTo(ExportSettings settings) {

    }

    @Override
    public void importFrom(ImportSettings settings) {

    }

    @Override
    public void initAccessService(String oldServiceId) {

    }

    @Nonnull
    @Override
    public CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec) {
        return new CreatedTokenInfo() {
            @Nonnull
            @Override
            public String getTokenValue() {
                return tokenSpec.getExtension();
            }

            @Nonnull
            @Override
            public String getTokenType() {
                return null;
            }

            @Nullable
            @Override
            public String getRefreshToken() {
                return null;
            }

            @Nonnull
            @Override
            public String getScope() {
                return null;
            }

            @Nullable
            @Override
            public Long getExpiresIn() {
                return null;
            }

            @Nullable
            @Override
            public String getReferenceToken() {
                return null;
            }
        };
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createCrossPlatformToken(@Nonnull TokenSpec tokenSpec, @Nonnull String subject) {
        return null;
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec, boolean skipUserCanCreateLongLivedTokenAssertion,
            boolean skipAcceptedScopeAssertion) {
        return null;
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
    public CreatedTokenInfo createNoPermissionToken(@Nonnull List<String> scope, @Nonnull TokenSpec tokenSpec,
            @Nullable String extraData) {
        return null;
    }

    @Nonnull
    @Override
    public CreatedTokenInfo refreshToken(@Nonnull TokenSpec tokenSpec, @Nonnull String tokenValue,
            @Nonnull String refreshToken) {
        return null;
    }

    @Nullable
    @Override
    public String extractSubjectUsername(@Nonnull JwtAccessToken accessToken) {
        return null;
    }

    @Override
    public void revokeToken(@Nonnull String tokenValue) {

    }

    @Override
    public void revokeTokenById(@Nonnull String tokenId) {

    }

    @Nonnull
    @Override
    public JwtAccessToken parseToken(@Nonnull String tokenValue) throws IllegalArgumentException {
        return null;
    }

    @Override
    public boolean verifyToken(@Nonnull JwtAccessToken accessToken) {
        return false;
    }

    @Override
    public boolean verifyBackOfficeToken(@Nonnull JwtAccessToken accessToken) {
        return false;
    }

    @Nonnull
    @Override
    public AccessTokenType getAccessTokenType(@Nonnull String tokenValue) {
        return null;
    }

    @Override
    public boolean verifyTokenIfServiceIdChanged(@Nonnull JwtAccessToken accessToken) {
        return false;
    }

    @Override
    public TokenVerifyResult verifyAndGetResult(@Nonnull JwtAccessToken accessToken) {
        return null;
    }

    @Override
    public TokenVerifyResult verifyAndGetResult(@Nonnull String accessToken) {
        return null;
    }

    @Nonnull
    @Override
    public ServiceId getArtifactoryServiceId() {
        return null;
    }

    @Override
    public boolean isTokenAppliesScope(@Nonnull JwtAccessToken accessToken, @Nonnull String requiredScope) {
        return false;
    }

    @Override
    public void registerAcceptedScopePattern(@Nonnull Pattern pattern) {

    }

    @Nonnull
    @Override
    public List<TokenInfo> getTokenInfos() {
        return null;
    }

    @Override
    public AccessClient getAccessClient() {
        return null;
    }

    @Override
    public AuthSettingsClient getAccessAuthSettingsClient() {
        return null;
    }

    @Override
    public GrpcAuthClient getGrpcAuthClient() {
        return null;
    }

    @Override
    public GrpcPlatformConfigClient getPlatformConfigClient() {
        return null;
    }

    @Override
    public void importSecurityEntities(SecurityInfo securityInfo, boolean override) {
    }

    @Override
    public void updateConfigurationModel(Map<String, Object> accessConfiguration) throws AccessClientException {

    }

    @Override
    public int moveYamlConfigResourcesToAccess(Map<String, Object> hashMap) {
        return 0;
    }

    @Override
    public UserLockPolicyConfig getUserLockPolicyConfig() {
        return null;
    }

    @Override
    public boolean isAnonymousAccessEnabled() {
        return false;
    }

    @Override
    public void ping() {

    }

    @Override
    public void readiness() {

    }

    @Override
    public void afterImport(ImportSettings settings) {

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
    public void unregisterFromRouterIfNeeded() {

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
    public String extractMtlsUsername(@Nonnull HttpServletRequest request) {
        return null;
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
        return false;
    }

    @Override
    public boolean isLdapEnabled() {
        return false;
    }

    @Override
    public List<LdapSetting> getLdapSettings() {
        return null;
    }

    @Override
    public boolean isLdapSettingsExist(String key) {
        return false;
    }

    @Override
    public LdapSetting getLdapSettings(String ldapKey) {
        return null;
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
        return null;
    }

    @Override
    public List<LdapSetting> getEnabledLdapSettings() {
        return null;
    }

    @Override
    public List<LdapGroupSetting> getLdapGroupSettings() {
        return null;
    }

    @Override
    public LdapGroupSetting getLdapGroupSettings(String name) {
        return null;
    }

    @Override
    public List<LdapGroupSetting> getEnabledLdapGroupSettings() {
        return null;
    }

    @Override
    public void upsertLdapGroupSetting(LdapGroupSetting ldapGroupSetting) {
    }

    @Override
    public void upsertLdapGroupSettings(List<LdapGroupSetting> ldapGroupSettings) {
    }

    @Override
    public void upsertSamlEncryptedAssertion(SamlEncryptedAssertion samlEncryptedAssertion) {

    }

    @Override
    public boolean isOAuthEnabled() {
        return false;
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
    public LdapGroupSetting removeLdapGroupOnly(String ldapGroupName) {
        return null;
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
    public void upsertOAuthGeneralSetting(OAuthGeneralSettings oAuthGeneralSettings) {

    }

    @Override
    public void upsertOAuthSettings(@Nonnull List<OAuthSetting> oAuthSettingsList) {

    }

    @Override
    public void removeOAuthSetting(@Nonnull String name) {

    }

    @Override
    public boolean shouldDelegateTokenOrApiKeyAuthenticationToAccess() {
        return false;
    }
}
