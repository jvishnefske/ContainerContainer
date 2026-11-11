package org.artifactory.config;

import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.oauth.OAuthProvidersTypeEnum;
import org.artifactory.api.config.ArtifactorySystemConfig;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.descriptor.security.oauth.OAuthProviderSettings;

import jakarta.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Artifactory system configuration. Stems from the global {@link com.jfrog.sysconf.SysConfig}.
 *
 * @author Uriah Levy
 */
@Slf4j
public class ArtifactorySystemConfigImpl implements ArtifactorySystemConfig {

    private static final String KEY_CENTRAL_OAUTH_ENABLED = "artifactory.security.centralOAuth.enabled";
    private static final String KEY_CENTRAL_OAUTH_REDIRECTURL = "artifactory.security.centralOAuth.redirectUrl";
    private static final String KEY_CENTRAL_OAUTH_PROVIDERS = "artifactory.security.centralOAuth.providers";

    private final SysConfig sysConfig;

    ArtifactorySystemConfigImpl(SysConfig sysConfig) {
        this.sysConfig = sysConfig;
    }

    @Override
    public String getRouterUrl() {
        return sysConfig.helper().getRouterUrl();
    }

    @Override
    public boolean isRouterEnabled() {
        return sysConfig.getBoolean(SysConfigConstants.KEY_ROUTER_ENABLED, true);
    }

    @Override
    public boolean isJFConnectEnabled() {
        return sysConfig.getBoolean(SysConfigConstants.KEY_JFCONNECT_ENABLED, false)
                || sysConfig.getBoolean(SysConfigConstants.KEY_JFLINK_ENABLED, false);
    }

    @Override
    public boolean isJFConnectDisabledExplicitly() {
        return !sysConfig.getBoolean(SysConfigConstants.KEY_JFCONNECT_ENABLED, true) ||
                !sysConfig.getBoolean(SysConfigConstants.KEY_JFLINK_ENABLED, true);
    }

    @Override
    public SysConfig getSysConfig() {
        return sysConfig;
    }

    @Override
    public String getNodeId() {
        return sysConfig.get(SysConfigConstants.KEY_NODE_ID).orElse(null);
    }

    @Override
    public String getCentralOAuthRedirectUrlOrFail() {
        return sysConfig.getOrFail(KEY_CENTRAL_OAUTH_REDIRECTURL);
    }

    @Override
    public CentralOAuthSettings getCentralOAuthDefaultConfig() {
        List<OAuthProviderSettings> providers = Arrays.stream(OAuthProvidersTypeEnum.values())
                .filter(providerType -> StringUtils.isNotEmpty(
                        sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "name"), "")))
                .map(providerType -> {
                    OAuthProviderSettings provider = new OAuthProviderSettings();
                    provider.setProviderType(providerType.name());
                    provider.setCentral(true);
                    provider.setEnabled(
                            sysConfig.getBoolean(getCentralOAuthProviderConfigKey(providerType, "enabled"), false));
                    provider.setName(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "name"), ""));
                    provider.setId(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "id"), ""));
                    provider.setBasicUrl(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "basicUrl"), ""));
                    provider.setApiUrl(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "apiUrl"), ""));
                    provider.setAuthUrl(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "authUrl"), ""));
                    provider.setTokenUrl(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "tokenUrl"), ""));
                    provider.setSecret(sysConfig.get(getCentralOAuthProviderConfigKey(providerType, "secret"), ""));
                    provider.setPkce(sysConfig.getBoolean(getCentralOAuthProviderConfigKey(providerType, "pkce"),
                            false));
                    provider.setUseDefaultProxy(sysConfig.getBoolean(
                            getCentralOAuthProviderConfigKey(providerType, "useDefaultProxy"), false));
                    return provider;
                })
                .filter(this::validateCentralOAuthProviderConfig)
                .collect(Collectors.toList());

        return new CentralOAuthSettings(sysConfig.getBoolean(KEY_CENTRAL_OAUTH_ENABLED, false), providers);
    }

    @Nonnull
    private String getCentralOAuthProviderConfigKey(@Nonnull OAuthProvidersTypeEnum providerType,
            @Nonnull String property) {
        return String.format("%s.%s.%s", KEY_CENTRAL_OAUTH_PROVIDERS, providerType.name(), property);
    }

    private boolean validateCentralOAuthProviderConfig(@Nonnull OAuthProviderSettings provider) {
        if (StringUtils.isEmpty(provider.getName())) {
            log.warn("Skipping Central OAuth provider config for type '{}'. Missing 'name'.", provider.getName());
            return false;
        }
        if (StringUtils.isEmpty(provider.getId())) {
            log.warn("Skipping Central OAuth provider config for type '{}'. Missing 'id'.", provider.getName());
            return false;
        }
        if (StringUtils.isEmpty(provider.getBasicUrl())) {
            log.warn("Skipping Central OAuth provider config for type '{}'. Missing 'basicUrl'.", provider.getName());
            return false;
        }
        if (StringUtils.isEmpty(provider.getApiUrl())) {
            log.warn("Skipping Central OAuth provider config for type '{}'. Missing 'apiUrl'.", provider.getName());
            return false;
        }
        if (StringUtils.isEmpty(provider.getAuthUrl())) {
            log.warn("Skipping Central OAuth provider config for type '{}'. Missing 'authUrl'.", provider.getName());
            return false;
        }
        if (StringUtils.isEmpty(provider.getTokenUrl())) {
            log.warn("Skipping Central OAuth provider config for type '{}'. Missing 'tokenUrl'.", provider.getName());
            return false;
        }
        if (StringUtils.isEmpty(provider.getSecret())) {
            log.warn("Skipping Central OAuth provider config for type '{}'. Missing 'secret'.", provider.getName());
            return false;
        }
        return true;
    }
}