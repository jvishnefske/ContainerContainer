package org.artifactory.security.access.migration.populator;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.CrowdSetting;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static org.artifactory.config.AccessConfigKeys.CROWD_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.CrowdConfigKeys;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.getParseBooleanFunc;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.populateValueFromMap;

/**
 * @author morm
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@SuppressWarnings("unchecked")
@Slf4j
class CrowdSettingsMigrationHandler implements PatchYamlAccessConfigMigrationHandler {

    private CrowdSetting crowdSetting;
    private final AccessService accessService;

    CrowdSettingsMigrationHandler(AccessService accessService) {
        this.accessService = accessService;
    }

    public int migrate(Map<String, Object> yamlConfigMap) {
        int movedResources = 0;
        if (yamlConfigMap.containsKey(SECURITY)) {
            Map<Object, Object> security = (Map<Object, Object>) yamlConfigMap.get(SECURITY);
            movedResources = migrateCrowdSettings(security);
        }
        if (crowdSetting != null && movedResources > 0) {
            accessService.upsertCrowdSettings(crowdSetting);
        }
        return movedResources;
    }

    private int migrateCrowdSettings(Map<Object, Object> security) {
        int movedResources = 0;
        if (security.containsKey(CROWD_SETTINGS)) {
            Map<Object, Object> crowdSettingsMap = (Map<Object, Object>) security.get(CROWD_SETTINGS);
            Optional<CrowdSetting> crowdSettingOptional = accessService.getCrowdSettings();

            if (attemptToDeleteNonExistingConfig(crowdSettingsMap, isEmptyOrDefault(crowdSettingOptional))) {
                throw new IllegalArgumentException("Attempted to delete: " + CROWD_SETTINGS + " but it does not exist");
            }
            if (crowdSettingsMap.isEmpty()) {
                security.remove(CROWD_SETTINGS);
                movedResources++;
                crowdSetting = CrowdSetting.builder().build();
                return movedResources;
            }

            CrowdSetting.CrowdSettingBuilder builder =
                    crowdSettingOptional.orElseGet(() -> CrowdSetting.builder().build()).toBuilder();
            for (CrowdConfigEnum crowdConfigEnum : CrowdConfigEnum.values()) {
                boolean populated = crowdConfigEnum.populate(builder, crowdSettingsMap);
                if (populated) {
                    movedResources++;
                }
            }
            crowdSetting = builder.build();
            security.remove(CROWD_SETTINGS);
        }
        return movedResources;
    }

    private static boolean isEmptyOrDefault(Optional<CrowdSetting> crowdSettingOptional) {
        return crowdSettingOptional.isEmpty() || crowdSettingOptional.get().equals(CrowdSetting.builder().build());
    }

    private static boolean attemptToDeleteNonExistingConfig(Map<Object, Object> crowdSettingsMap,
            boolean configDoesNotExit) {
        return crowdSettingsMap.isEmpty() && configDoesNotExit;
    }

    enum CrowdConfigEnum {
        ENABLE_INTEGRATION(CrowdConfigKeys.ENABLE_INTEGRATION) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::enable, getParseBooleanFunc(), configMap);
            }
        },
        APPLICATION_NAME(CrowdConfigKeys.APPLICATION_NAME) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::applicationName, String::valueOf, configMap);
            }
        },
        PASSWORD(CrowdConfigKeys.PASSWORD) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::password, String::valueOf, configMap);
            }
        },
        SERVER_URL(CrowdConfigKeys.SERVER_URL) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::serverUrl, String::valueOf, configMap);
            }
        },
        SESSION_VALIDATION_INTERVAL(CrowdConfigKeys.SESSION_VALIDATION_INTERVAL) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::sessionValidationInterval,
                        val -> val instanceof String ? Integer.parseInt((String) val) : ((Integer) val).longValue(),
                        configMap);
            }
        },
        NO_AUTO_CREATION(CrowdConfigKeys.NO_AUTO_CREATION) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::autoUserCreation, getParseBooleanFunc(), configMap);
            }
        },
        USE_DEFAULT_PROXY(CrowdConfigKeys.USE_DEFAULT_PROXY) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::useDefaultProxy, getParseBooleanFunc(), configMap);
            }
        },
        ALLOW_USER_TO_ACCESS_PROFILE(CrowdConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::allowUserToAccessProfile, getParseBooleanFunc(),
                        configMap);
            }
        },
        CUSTOM_COOKIE_TOKEN_KEY(CrowdConfigKeys.CUSTOM_COOKIE_TOKEN_KEY) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::customCookieTokenKey, String::valueOf, configMap);
            }
        },
        DIRECT_AUTHENTICATION(CrowdConfigKeys.DIRECT_AUTHENTICATION) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::directAuthentication, getParseBooleanFunc(), configMap);
            }
        },
        OVERRIDE_ALL_GROUPS_UPON_LOGIN(CrowdConfigKeys.OVERRIDE_ALL_GROUPS_UPON_LOGIN) {
            @Override
            boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::overrideAllGroupsUponLogin, getParseBooleanFunc(),
                        configMap);
            }
        };

        private final String key;

        CrowdConfigEnum(String key) {
            this.key = key;
        }

        abstract boolean populate(CrowdSetting.CrowdSettingBuilder builder, Map<Object, Object> configMap);

        public String getKey() {
            return key;
        }
    }
}
