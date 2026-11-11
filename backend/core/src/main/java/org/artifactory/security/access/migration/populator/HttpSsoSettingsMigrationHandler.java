package org.artifactory.security.access.migration.populator;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static org.artifactory.config.AccessConfigKeys.HTTP_SSO_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.HttpSsoConfigKeys;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.getParseBooleanFunc;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.populateValueFromMap;

/**
 * @author morm
 */
@SuppressWarnings("unchecked")
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
class HttpSsoSettingsMigrationHandler implements PatchYamlAccessConfigMigrationHandler {

    private HttpSsoSetting httpSsoSetting;

    private final AccessService accessService;

    @Autowired
    HttpSsoSettingsMigrationHandler(AccessService accessService) {
        this.accessService = accessService;
    }

    public int migrate(Map<String, Object> yamlConfigMap) {
        httpSsoSetting = HttpSsoSetting.builder().build();
        int movedResources = 0;
        if (yamlConfigMap.containsKey(SECURITY)) {
            Map<Object, Object> security = (Map<Object, Object>) yamlConfigMap.get(SECURITY);
            movedResources = migrateHttpSsoSettings(security);
        }
        if (httpSsoSetting != null && movedResources > 0) {
            accessService.upsertHttpSsoSettings(httpSsoSetting);
        }
        return movedResources;
    }

    private int migrateHttpSsoSettings(Map<Object, Object> security) {
        int movedResources = 0;
        if (security.containsKey(HTTP_SSO_SETTINGS)) {
            Map<Object, Object> httpSsoSettingsMap = (Map<Object, Object>) security.get(HTTP_SSO_SETTINGS);
            Optional<HttpSsoSetting> ssoSettingOptional = accessService.getHttpSsoSettings();

            if (isAttemptToDeleteNonExistingConfig(httpSsoSettingsMap, isEmptyOrDefault(ssoSettingOptional))) {
                throw new IllegalArgumentException(
                        "Attempted to delete: " + HTTP_SSO_SETTINGS + " but it does not exist");
            }

            if (httpSsoSettingsMap.isEmpty()) {
                security.remove(HTTP_SSO_SETTINGS);
                movedResources++;
                httpSsoSetting = HttpSsoSetting.builder().build();
                return movedResources;
            }

            httpSsoSetting = ssoSettingOptional.orElseGet(() -> HttpSsoSetting.builder().build());
            HttpSsoSetting.HttpSsoSettingBuilder builder = httpSsoSetting.toBuilder();

            for (HttpSSoConfigEnum httpSSoConfigEnum : HttpSSoConfigEnum.values()) {
                boolean populated = httpSSoConfigEnum.populate(builder, httpSsoSettingsMap);
                if (populated) {
                    movedResources++;
                }
            }
            httpSsoSetting = builder.build();
            security.remove(HTTP_SSO_SETTINGS);
        }
        return movedResources;
    }

    private static boolean isEmptyOrDefault(Optional<HttpSsoSetting> ssoSettingOptional) {
        return ssoSettingOptional.isEmpty() || ssoSettingOptional.get().equals(HttpSsoSetting.builder().build());
    }

    private boolean isAttemptToDeleteNonExistingConfig(Map<Object, Object> httpSsoSettingsMap,
            boolean configDoesNotExist) {
        return httpSsoSettingsMap.isEmpty() && configDoesNotExist;
    }

    enum HttpSSoConfigEnum {
        HTTP_SSO_PROXIED(HttpSsoConfigKeys.HTTP_SSO_PROXIED) {
            @Override
            boolean populate(HttpSsoSetting.HttpSsoSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::httpSsoProxied, getParseBooleanFunc(), configMap);
            }
        },
        REMOTE_USER_REQUEST_VARIABLE(HttpSsoConfigKeys.REMOTE_USER_REQUEST_VARIABLE) {
            @Override
            boolean populate(HttpSsoSetting.HttpSsoSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::remoteUserRequestVariable, String::valueOf, configMap);
            }
        },
        ALLOW_USER_TO_ACCESS_PROFILE(HttpSsoConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE) {
            @Override
            boolean populate(HttpSsoSetting.HttpSsoSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::allowUserToAccessProfile, getParseBooleanFunc(),
                        configMap);
            }
        },
        NO_AUTO_CREATION(HttpSsoConfigKeys.NO_AUTO_CREATION) {
            @Override
            boolean populate(HttpSsoSetting.HttpSsoSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::autoCreateUser, getParseBooleanFunc(), configMap);
            }
        },
        SYNC_LDAP_GROUPS(HttpSsoConfigKeys.SYNC_LDAP_GROUPS) {
            @Override
            boolean populate(HttpSsoSetting.HttpSsoSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::syncLdapGroups, getParseBooleanFunc(), configMap);
            }
        };

        private final String key;

        HttpSSoConfigEnum(String key) {
            this.key = key;
        }

        abstract boolean populate(HttpSsoSetting.HttpSsoSettingBuilder builder, Map<Object, Object> configMap);

        public String getKey() {
            return key;
        }
    }
}
