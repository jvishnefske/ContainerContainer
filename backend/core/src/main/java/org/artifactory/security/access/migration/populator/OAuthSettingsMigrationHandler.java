package org.artifactory.security.access.migration.populator;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.addon.oauth.OAuthProvidersTypeEnum;
import org.artifactory.aol.AolService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigKeys;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.artifactory.config.AccessConfigKeys.OAUTH_PROVIDER_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.OAUTH_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@SuppressWarnings({"unchecked", "OptionalUsedAsFieldOrParameterType"})
@Slf4j
public class OAuthSettingsMigrationHandler implements PatchYamlAccessConfigMigrationHandler {

    private final AccessService accessService;
    private final AolService aolService;

    @Autowired
    OAuthSettingsMigrationHandler(AccessService accessService, AolService aolService) {
        this.accessService = accessService;
        this.aolService = aolService;
    }

    /**
     * This method migrates OAuth settings from the YAML configuration map to the Access service.
     * It checks for the presence of OAuth settings and processes them accordingly.
     * Sample Request:
     * ```
     * security:
     * oauthSettings:
     * enableIntegration: false
     * allowUserToAccessProfile: false
     * persistUsers: true
     * oauthProvidersSettings:
     * test:
     * enabled: true
     * providerType: github
     * id: wewew
     * secret: JE2e8FAGP5C7UZrvA2zkqyDS5geNQ3nzCdANaT3jKjd8TyC59C
     * apiUrl: https://api.github.com/user
     * authUrl: https://github.com/login/oauth/authorize
     * tokenUrl: https://github.com/login/oauth/access_token
     * basicUrl: https://github.com/
     * domain: wewe1
     * central: false
     * pkce: false
     * useDefaultProxy: false
     * ```
     *
     * @param yamlConfigMap The YAML configuration map containing security settings.
     * @return The number of resources moved during the migration.
     */
    @Override
    public int migrate(Map<String, Object> yamlConfigMap) {
        AtomicInteger movedResources = new AtomicInteger();
        if (yamlConfigMap.containsKey(SECURITY)) {
            Map<Object, Object> security = (Map<Object, Object>) yamlConfigMap.get(SECURITY);
            if (security.containsKey(OAUTH_SETTINGS)) {
                Map<Object, Object> oAuthSettings = (Map<Object, Object>) security.get(OAUTH_SETTINGS);
                migrateOAuthSettings(oAuthSettings, movedResources);
                security.remove(OAUTH_SETTINGS);
            }
        }
        return movedResources.get();
    }

    private void migrateOAuthSettings(Map<Object, Object> oAuthSettings, AtomicInteger movedResources) {
        OAuthGeneralSettings oldGeneral = accessService.getOAuthGeneralSettings();
        List<OAuthSetting> oldProviders = accessService.getAllOAuthSettings();

        List<String> toDelete = new ArrayList<>();
        List<OAuthSetting> toAddOrUpdate = new ArrayList<>();
        Pair<Boolean, OAuthGeneralSettings> newGeneralPair = parseGeneral(oAuthSettings, oldGeneral, movedResources);

        boolean finalAutoCreateUsers = getAutoCreateUsers(oAuthSettings, oldProviders);
        boolean finalAllowAccessToProfile = getAllowAccessToProfile(oAuthSettings, oldProviders);

        if (oAuthSettings != null && oAuthSettings.containsKey(OAUTH_PROVIDER_SETTINGS)) {
            Map<String, Object> providerSettings = (Map<String, Object>) oAuthSettings.get(OAUTH_PROVIDER_SETTINGS);
            if (providerSettings != null && !providerSettings.isEmpty()) {
                for (Map.Entry<String, Object> entry : providerSettings.entrySet()) {
                    String providerName = entry.getKey();
                    Map<Object, Object> providerConfig = (Map<Object, Object>) entry.getValue();
                    parseProviderConfigs(providerConfig, toDelete, providerName, movedResources, oldProviders,
                            finalAutoCreateUsers, finalAllowAccessToProfile, toAddOrUpdate);
                    //Check if we need to update existing based on auto user creation and profile access flags
                    List<OAuthSetting> toUpdateExisting = oldProviders.stream()
                            .filter(s -> s.isAutoUserCreation() != finalAutoCreateUsers ||
                                    s.isAllowUserToAccessProfile() != finalAllowAccessToProfile)
                            .filter(s -> toAddOrUpdate.stream()
                                    .noneMatch(newSetting -> newSetting.getName().equals(s.getName())))
                            .map(s -> s.toBuilder().autoUserCreation(finalAutoCreateUsers)
                                    .allowUserToAccessProfile(finalAllowAccessToProfile).build()).toList();
                    toAddOrUpdate.addAll(toUpdateExisting);
                }

            } else {
                //If key exists but is empty, We need to remove all existing settings
                log.debug("Removing existing OAuth provider settings as the new configuration is empty.");
                oldProviders.forEach(oAuthSetting -> {
                    log.debug("Removing OAuth provider setting: {}", oAuthSetting.getName());
                    toDelete.add(oAuthSetting.getName());
                    movedResources.getAndIncrement();
                });
            }
        } else {
            //If the key does not exist, we need to remove all existing settings
            log.debug("Removing existing OAuth provider settings as the new configuration is missing.");
            oldProviders.forEach(oAuthSetting -> {
                log.debug("Removing OAuth provider setting: {}", oAuthSetting.getName());
                toDelete.add(oAuthSetting.getName());
                movedResources.getAndIncrement();
            });
        }

        validateProviders(toAddOrUpdate);

        updateOAuthSettingsInAccess(movedResources, newGeneralPair, toAddOrUpdate, toDelete);
    }

    private void updateOAuthSettingsInAccess(AtomicInteger movedResources,
            Pair<Boolean, OAuthGeneralSettings> newGeneralPair, List<OAuthSetting> toAddOrUpdate,
            List<String> toDelete) {
        //Update Access with the new or updated OAuth settings
        if (Boolean.TRUE.equals(newGeneralPair.getLeft())) {
            accessService.upsertOAuthGeneralSetting(newGeneralPair.getRight());
            movedResources.getAndIncrement();
            log.debug("Updated OAuth general settings: {}", newGeneralPair.getRight());
        } else {
            log.debug("No update needed for OAuth general settings.");
        }
        if (!toAddOrUpdate.isEmpty()) {
            accessService.upsertOAuthSettings(toAddOrUpdate);
        }
        if (!toDelete.isEmpty()) {
            toDelete.forEach(accessService::removeOAuthSetting);
        }
    }

    private void parseProviderConfigs(Map<Object, Object> providerConfig, List<String> toDelete, String providerName,
            AtomicInteger movedResources, List<OAuthSetting> oldProviders, boolean finalAutoCreateUsers,
            boolean finalAllowAccessToProfile, List<OAuthSetting> toAddOrUpdate) {
        if (providerConfig == null || providerConfig.isEmpty()) {
            toDelete.add(providerName);
            movedResources.incrementAndGet();
        } else {
            OAuthSetting oAuthSetting =
                    parseOAuthSettings(providerName, providerConfig, oldProviders, finalAutoCreateUsers,
                            finalAllowAccessToProfile, movedResources);
            log.debug("Moved OAuth provider setting: {}", providerName);
            toAddOrUpdate.add(oAuthSetting);
            movedResources.getAndIncrement();
        }
    }

    private void validateProviders(List<OAuthSetting> toAddOrUpdate) {
        toAddOrUpdate.forEach(oAuthSetting -> {
            if (aolService.isAol() && oAuthSetting.isUseDefaultProxy()) {
                throw new IllegalArgumentException(
                        "Proxy is not available in Saas for provider: " + oAuthSetting.getName());
            }
            if (StringUtils.isBlank(oAuthSetting.getProviderType())) {
                throw new IllegalArgumentException(
                        "Provider type cannot be empty for provider: " + oAuthSetting.getName());
            }
            if (StringUtils.equalsIgnoreCase(oAuthSetting.getProviderType(), OAuthProvidersTypeEnum.github.name()) &&
                    StringUtils.isBlank(oAuthSetting.getDomain())) {
                throw new IllegalArgumentException(
                        "Organization cannot be empty for github provider: " + oAuthSetting.getName());
            }
        });
    }

    private boolean getAllowAccessToProfile(Map<Object, Object> oAuthSettings,
            List<OAuthSetting> existingOAuthProviderSettings) {
        if (oAuthSettings != null &&
                oAuthSettings.containsKey(AccessConfigKeys.OAuthConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE)) {
            return Boolean.parseBoolean(
                    oAuthSettings.get(AccessConfigKeys.OAuthConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE).toString());
        }
        return !existingOAuthProviderSettings.isEmpty() &&
                existingOAuthProviderSettings.getFirst().isAllowUserToAccessProfile();
    }

    private boolean getAutoCreateUsers(Map<Object, Object> oAuthSettings,
            List<OAuthSetting> existingOAuthProviderSettings) {
        if (oAuthSettings != null && oAuthSettings.containsKey(AccessConfigKeys.OAuthConfigKeys.PERSIST_USERS)) {
            return Boolean.parseBoolean(oAuthSettings.get(AccessConfigKeys.OAuthConfigKeys.PERSIST_USERS).toString());
        }
        return !existingOAuthProviderSettings.isEmpty() &&
                existingOAuthProviderSettings.getFirst().isAutoUserCreation();
    }

    private OAuthSetting parseOAuthSettings(String providerName, Map<Object, Object> providerConfig,
            List<OAuthSetting> existingOAuthProviderSettings, boolean finalAutoCreateUsers,
            boolean finalAllowAccessToProfile, AtomicInteger movedResources) {

        OAuthSetting.OAuthSettingBuilder builder = OAuthSetting.builder();
        builder.name(providerName);

        //Check if the provider already exists
        List<OAuthSetting> existingSettings =
                existingOAuthProviderSettings.stream().filter(s -> s.getName().equals(providerName)).toList();

        if (!existingSettings.isEmpty()) {
            OAuthSetting existingSetting =
                    existingOAuthProviderSettings.stream().filter(s -> s.getName().equals(providerName)).findFirst()
                            .orElseThrow();
            log.debug("Found existing OAuth provider setting: {}", providerName);
            builder = existingSetting.toBuilder();

            if (existingSetting.isAutoUserCreation() != finalAutoCreateUsers) {
                builder.autoUserCreation(finalAutoCreateUsers);
                logFieldUpdate(providerName, AccessConfigKeys.OAuthConfigKeys.PERSIST_USERS, finalAutoCreateUsers);
            }
            if (existingSetting.isAllowUserToAccessProfile() != finalAllowAccessToProfile) {
                builder.allowUserToAccessProfile(finalAllowAccessToProfile);
                logFieldUpdate(providerName, AccessConfigKeys.OAuthConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE,
                        finalAllowAccessToProfile);
            }
        } else {
            //This is new provider setting
            builder.autoUserCreation(finalAutoCreateUsers);
            builder.allowUserToAccessProfile(finalAllowAccessToProfile);
        }

        for (OAuthProviderConfigEnum oAuthProviderConfigEnum : OAuthProviderConfigEnum.values()) {
            boolean populated = oAuthProviderConfigEnum.populate(builder, providerConfig);
            if (populated) {
                logFieldUpdate(providerName, oAuthProviderConfigEnum.getKey(),
                        providerConfig.get(oAuthProviderConfigEnum.getKey()));
                movedResources.getAndIncrement();
            }
        }
        return builder.build();
    }

    private Pair<Boolean, OAuthGeneralSettings> parseGeneral(Map<Object, Object> oAuthSettings,
            OAuthGeneralSettings oldGeneral, AtomicInteger movedResources) {
        boolean needsUpdate = false;
        OAuthGeneralSettings.OAuthGeneralSettingsBuilder builder = oldGeneral.toBuilder();
        if (oAuthSettings != null && oAuthSettings.containsKey(AccessConfigKeys.OAuthConfigKeys.ENABLE_INTEGRATION)) {
            boolean enableIntegration = Boolean.parseBoolean(
                    oAuthSettings.get(AccessConfigKeys.OAuthConfigKeys.ENABLE_INTEGRATION).toString());
            if (oldGeneral.isEnable() != enableIntegration) {
                builder.enable(enableIntegration);
                needsUpdate = true;
                movedResources.getAndIncrement();
                log.debug("OAuth general settings need update: enableIntegration changed to {}", enableIntegration);
            }
        }
        if (oAuthSettings != null && oAuthSettings.containsKey(AccessConfigKeys.OAuthConfigKeys.DEFAULT)) {
            String defaultNpm = oAuthSettings.get(AccessConfigKeys.OAuthConfigKeys.DEFAULT).toString();
            if (!defaultNpm.equals(oldGeneral.getDefaultSetting())) {
                builder.defaultSetting(defaultNpm);
                needsUpdate = true;
                movedResources.getAndIncrement();
                log.debug("OAuth general settings need update: defaultNpm changed to {}", defaultNpm);
            }
        }
        return Pair.of(needsUpdate, builder.build());
    }

    private void logFieldUpdate(String providerName, String fieldName, Object newValue) {
        log.debug("OAuth provider setting '{}' {}: {}", providerName, fieldName, newValue);
    }

    @Getter
    enum OAuthProviderConfigEnum {

        ENABLED(AccessConfigKeys.OAuthConfigKeys.ENABLED) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::enabled,
                        PatchYamlUtils.getParseBooleanFunc(), configMap);
            }
        }, PROVIDER_TYPE(AccessConfigKeys.OAuthConfigKeys.PROVIDER_TYPE) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::providerType, String::valueOf, configMap);
            }
        }, CLIENT_SECRET(AccessConfigKeys.OAuthConfigKeys.SECRET) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(),
                        builder::clientSecret,
                        s -> CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), String.valueOf(s)), configMap);
            }
        }, CLIENT_ID(AccessConfigKeys.OAuthConfigKeys.ID) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::clientId, String::valueOf, configMap);
            }
        }, API_URL(AccessConfigKeys.OAuthConfigKeys.API_URL) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::apiUrl, String::valueOf, configMap);
            }
        }, AUTH_URL(AccessConfigKeys.OAuthConfigKeys.AUTH_URL) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::authUrl, String::valueOf, configMap);
            }
        }, TOKEN_URL(AccessConfigKeys.OAuthConfigKeys.TOKEN_URL) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::tokenUrl, String::valueOf, configMap);
            }
        }, BASIC_URL(AccessConfigKeys.OAuthConfigKeys.BASIC_URL) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::basicUrl, String::valueOf, configMap);
            }
        }, DOMAIN(AccessConfigKeys.OAuthConfigKeys.DOMAIN) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::domain, String::valueOf, configMap);
            }
        }, PKCE(AccessConfigKeys.OAuthConfigKeys.PKCE) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::pkce,
                        PatchYamlUtils.getParseBooleanFunc(), configMap);
            }
        }, USE_DEFAULT_PROXY(AccessConfigKeys.OAuthConfigKeys.USE_DEFAULT_PROXY) {
            @Override
            boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap) {
                return PatchYamlUtils.populateValueFromMap(getKey(), builder::useDefaultProxy,
                        PatchYamlUtils.getParseBooleanFunc(), configMap);
            }
        };

        private final String key;

        OAuthProviderConfigEnum(String key) {
            this.key = key;
        }

        abstract boolean populate(OAuthSetting.OAuthSettingBuilder builder, Map<Object, Object> configMap);

    }
}
