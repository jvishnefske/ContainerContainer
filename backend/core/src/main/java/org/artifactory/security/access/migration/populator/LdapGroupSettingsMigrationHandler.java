package org.artifactory.security.access.migration.populator;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.artifactory.security.access.AccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.config.AccessConfigKeys.LdapGroupConfigKeys;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_LDAP_GROUP_SETTINGS;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.getParseBooleanFunc;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.populateValueFromMap;

/**
 * @author morm
 */
@SuppressWarnings("unchecked")
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
class LdapGroupSettingsMigrationHandler implements PatchYamlAccessConfigMigrationHandler {
    private final List<LdapGroupSetting> ldapGroupSettings = Lists.newArrayList();
    private final AccessService accessService;

    @Autowired
    LdapGroupSettingsMigrationHandler(AccessService accessService) {
        this.accessService = accessService;
    }

    public int migrate(Map<String, Object> yamlConfigMap) {
        int movedResources = 0;
        if (yamlConfigMap.containsKey(SECURITY)) {
            Map<Object, Object> security = (Map<Object, Object>) yamlConfigMap.get(SECURITY);
            movedResources = populateLdapGroupSettings(security);
            if (!ldapGroupSettings.isEmpty() && movedResources > 0) {
                accessService.upsertLdapGroupSettings(ldapGroupSettings);
            }
        }
        return movedResources;
    }

    private int populateLdapGroupSettings(Map<Object, Object> security) {
        int movedResources = 0;
        if (security.containsKey(SECURITY_LDAP_GROUP_SETTINGS)) {
            List<LdapGroupSetting> existingLdapGroupSettings = accessService.getLdapGroupSettings();
            Map<Object, Object> ldapGroupSettingsMap = (Map<Object, Object>) security.get(SECURITY_LDAP_GROUP_SETTINGS);

            if (isAttemptToDeleteNonExistingConfig(ldapGroupSettingsMap, existingLdapGroupSettings.isEmpty())) {
                throw new IllegalArgumentException(
                        "Attempted to delete: " + SECURITY_LDAP_GROUP_SETTINGS + " but it does not exist");
            }

            if (ldapGroupSettingsMap.isEmpty()) {
                security.remove(SECURITY_LDAP_GROUP_SETTINGS);
                Set<String> ldapGroupSettingKeys =
                        existingLdapGroupSettings.stream().map(LdapGroupSetting::getName).collect(Collectors.toSet());
                for (String ldapGroupSettingKey : ldapGroupSettingKeys) {
                    accessService.removeLdapGroupOnly(ldapGroupSettingKey);
                    movedResources++;
                }
                return movedResources;
            }

            for (Map.Entry<Object, Object> entry : ldapGroupSettingsMap.entrySet()) {
                String settingKey = (String) entry.getKey();
                Optional<LdapGroupSetting> optionalLdapGroupSetting =
                        existingLdapGroupSettings.stream().filter(setting -> setting.getName().equals(settingKey))
                                .findFirst();

                Map<Object, Object> innerLdapGroupSettingsMap = (Map<Object, Object>) entry.getValue();

                movedResources +=
                        migrateLdapGroupSetting(innerLdapGroupSettingsMap, settingKey, optionalLdapGroupSetting);
            }
            security.remove(SECURITY_LDAP_GROUP_SETTINGS);
        }
        return movedResources;
    }

    private int migrateLdapGroupSetting(Map<Object, Object> innerGroupSettingsMap, String settingKey,
            Optional<LdapGroupSetting> optionalLdapGroupSetting) {
        int movedResources = 0;
        if (isAttemptToDeleteNonExistingConfig(innerGroupSettingsMap, optionalLdapGroupSetting.isEmpty())) {
            throw new IllegalArgumentException(
                    "Attempted to delete:" + settingKey + " but it does not exist");
        }

        if (innerGroupSettingsMap.isEmpty()) {
            accessService.removeLdapGroupOnly(settingKey);
            movedResources++;
            return movedResources;
        }

        LdapGroupSetting ldapGroupSetting = new LdapGroupSetting();
        ldapGroupSetting.setName(settingKey);

        if (optionalLdapGroupSetting.isPresent()) {
            ldapGroupSetting = optionalLdapGroupSetting.get();
        }

        for (LdapGroupSettingsEnum ldapGroupSettingsEnum : LdapGroupSettingsEnum.values()) {
            boolean populated = ldapGroupSettingsEnum.populate(ldapGroupSetting, innerGroupSettingsMap);
            if (populated) {
                movedResources++;
            }
        }
        ldapGroupSettings.add(ldapGroupSetting);
        return movedResources;
    }

    enum LdapGroupSettingsEnum {
        DESC_ATTR(LdapGroupConfigKeys.DESC_ATTR) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setDescriptionAttribute, String::valueOf,
                        configMap);
            }
        },
        ENABLED_LDAP(LdapGroupConfigKeys.ENABLED_LDAP) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setEnabledLdap, String::valueOf, configMap);
            }
        },
        GROUP_BASE_DN(LdapGroupConfigKeys.GROUP_BASE_DN) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setGroupBaseDn, String::valueOf, configMap);
            }

        },
        GROUP_MEMBER_ATTR(LdapGroupConfigKeys.GROUP_MEMBER_ATTR) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setGroupMemberAttribute, String::valueOf,
                        configMap);
            }
        },
        GROUP_NAME_ATTR(LdapGroupConfigKeys.GROUP_NAME_ATTR) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setGroupNameAttribute, String::valueOf,
                        configMap);
            }
        },
        STRATEGY(LdapGroupConfigKeys.STRATEGY) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setStrategy,
                        str -> LdapGroupPopulatorStrategies.valueOf((String) str), configMap);
            }
        },
        SUB_TREE(LdapGroupConfigKeys.SUB_TREE) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setSubTree, getParseBooleanFunc(), configMap);
            }
        },
        FORCE_ATTR_SEARCH(LdapGroupConfigKeys.FORCE_ATTR_SEARCH) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setForceAttributeSearch, getParseBooleanFunc(),
                        configMap);
            }
        },
        FILTER(LdapGroupConfigKeys.FILTER) {
            @Override
            boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapGroupSetting::setFilter, String::valueOf, configMap);
            }
        };

        abstract boolean populate(LdapGroupSetting ldapGroupSetting, Map<Object, Object> configMap);

        private final String key;

        LdapGroupSettingsEnum(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    private boolean isAttemptToDeleteNonExistingConfig(Map<Object, Object> ldapSettingsMap,
            boolean configDoesNotExist) {
        return ldapSettingsMap.isEmpty() && configDoesNotExist;
    }
}
