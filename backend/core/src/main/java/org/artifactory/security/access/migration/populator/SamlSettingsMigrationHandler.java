package org.artifactory.security.access.migration.populator;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigKeys;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.SamlSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.artifactory.config.AccessConfigKeys.SAML_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.security.access.AccessServiceImpl.SAML_DEFAULT_SETTINGS;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.getParseBooleanFunc;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.populateValueFromMap;

/**
 * @author sergeyv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@SuppressWarnings({"unchecked", "OptionalUsedAsFieldOrParameterType"})
@Slf4j
public class SamlSettingsMigrationHandler implements PatchYamlAccessConfigMigrationHandler {

    private SamlSetting samlSetting;

    private final AccessService accessService;

    @Autowired
    SamlSettingsMigrationHandler(AccessService accessService) {
        this.accessService = accessService;
    }

    public int migrate(Map<String, Object> yamlConfigMap) {
        int movedResources = 0;
        if (yamlConfigMap.containsKey(SECURITY)) {
            Map<Object, Object> security = (Map<Object, Object>) yamlConfigMap.get(SECURITY);
            movedResources = migrateSamlSettings(security);
            if (samlSetting != null && movedResources > 0) {
                accessService.upsertSamlSetting(samlSetting);
            }
        }
        return movedResources;
    }

    private int migrateSamlSettings(Map<Object, Object> security) {
        int movedResources = 0;
        if (security.containsKey(SAML_SETTINGS)) {
            Map<Object, Object> samlSettingsMap = (Map<Object, Object>) security.get(SAML_SETTINGS);
            SamlSetting samlSettings = accessService.getSamlSettings();
            if (attemptToDeleteNonExistingConfig(samlSettingsMap, isEmptyOrDefault(samlSettings))) {
                throw new IllegalArgumentException("Attempted to delete: " + SAML_SETTINGS + " but it does not exist");
            }
            if (samlSettingsMap.isEmpty()) {
                security.remove(SAML_SETTINGS);
                movedResources++;
                samlSetting = SamlSetting.builder().build();
                return movedResources;
            }

            SamlSetting.SamlSettingBuilder builder = samlSettings.toBuilder();
            for (SamlConfigEnum samlConfigEnum : SamlConfigEnum.values()) {
                boolean populated = samlConfigEnum.populate(builder, samlSettingsMap);
                if (populated) {
                    movedResources++;
                }
            }
            samlSetting = builder.build();
            security.remove(SAML_SETTINGS);
        }
        return movedResources;
    }

    private static boolean isEmptyOrDefault(SamlSetting samlSetting) {
        return samlSetting == null || samlSetting.equals(SAML_DEFAULT_SETTINGS);
    }

    private static boolean attemptToDeleteNonExistingConfig(Map<Object, Object> samlSettingsMap,
            boolean configDoesNotExit) {
        return samlSettingsMap.isEmpty() && configDoesNotExit;
    }

    enum SamlConfigEnum {
        ENABLE_INTEGRATION(AccessConfigKeys.SamlConfigKeys.ENABLE_INTEGRATION) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::enableIntegration, getParseBooleanFunc(), configMap);
            }
        },
        LOGIN_URL(AccessConfigKeys.SamlConfigKeys.LOGIN_URL) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::loginUrl, String::valueOf, configMap);
            }
        },
        LOGOUT_URL(AccessConfigKeys.SamlConfigKeys.LOGOUT_URL) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::logoutUrl, String::valueOf, configMap);
            }
        },
        SERVICE_PROVIDER_NAME(AccessConfigKeys.SamlConfigKeys.SERVICE_PROVIDER_NAME) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::serviceProviderName, String::valueOf, configMap);
            }
        },
        CERTIFICATE(AccessConfigKeys.SamlConfigKeys.CERTIFICATE) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::certificate, String::valueOf, configMap);
            }
        },
        USE_ENCRYPTED_ASSERTION(AccessConfigKeys.SamlConfigKeys.USE_ENCRYPTED_ASSERTION) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::useEncryptedAssertion, getParseBooleanFunc(), configMap);
            }
        },
        SYNC_GROUPS(AccessConfigKeys.SamlConfigKeys.SYNC_GROUPS) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::syncGroups, getParseBooleanFunc(), configMap);
            }
        },
        GROUP_ATTRIBUTE(AccessConfigKeys.SamlConfigKeys.GROUP_ATTRIBUTE) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::groupAttribute, String::valueOf,
                        configMap);
            }
        },
        EMAIL_ATTRIBUTE(AccessConfigKeys.SamlConfigKeys.EMAIL_ATTRIBUTE) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::emailAttribute, String::valueOf, configMap);
            }
        },
        NO_AUTO_USER_CREATION(AccessConfigKeys.SamlConfigKeys.NO_AUTO_USER_CREATION) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::autoUserCreation, getParseBooleanFunc(), configMap);
            }
        },
        ALLOW_USER_TO_ACCESS_PROFILE(AccessConfigKeys.SamlConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::allowUserToAccessProfile, getParseBooleanFunc(),
                        configMap);
            }
        },
        AUTO_REDIRECT(AccessConfigKeys.SamlConfigKeys.AUTO_REDIRECT) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::autoRedirect, getParseBooleanFunc(),
                        configMap);
            }
        },
        VERIFY_AUDIENCE_RESTRICTION(AccessConfigKeys.SamlConfigKeys.VERIFY_AUDIENCE_RESTRICTION) {
            @Override
            boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), builder::verifyAudienceRestriction, getParseBooleanFunc(),
                        configMap);
            }
        };

        private final String key;

        SamlConfigEnum(String key) {
            this.key = key;
        }

        abstract boolean populate(SamlSetting.SamlSettingBuilder builder, Map<Object, Object> configMap);

        public String getKey() {
            return key;
        }
    }
}
