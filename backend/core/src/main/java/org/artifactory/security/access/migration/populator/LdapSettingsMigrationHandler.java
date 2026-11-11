package org.artifactory.security.access.migration.populator;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
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

import static org.artifactory.config.AccessConfigKeys.LdapConfigKeys;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_LDAP_SETTINGS;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.getParseBooleanFunc;
import static org.artifactory.security.access.migration.populator.PatchYamlUtils.populateValueFromMap;

/**
 * @author morm
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@SuppressWarnings({"unchecked", "OptionalUsedAsFieldOrParameterType"})
@Slf4j
class LdapSettingsMigrationHandler implements PatchYamlAccessConfigMigrationHandler {
    private final List<LdapSetting> ldapSettings = Lists.newArrayList();
    private final AccessService accessService;

    @Autowired
    LdapSettingsMigrationHandler(AccessService accessService) {
        this.accessService = accessService;
    }

    @SuppressWarnings("unchecked")
    public int migrate(Map<String, Object> yamlConfigMap) {
        int movedResources = 0;
        if (yamlConfigMap.containsKey(SECURITY)) {
            Map<Object, Object> security = (Map<Object, Object>) yamlConfigMap.get(SECURITY);
            movedResources = migrateLdapSettings(security);
            if (!ldapSettings.isEmpty() && movedResources > 0) {
                accessService.upsertLdapSettings(ldapSettings);
            }
        }
        return movedResources;
    }

    private int migrateLdapSettings(Map<Object, Object> security) {
        int movedResources = 0;
        if (security.containsKey(SECURITY_LDAP_SETTINGS)) {
            List<LdapSetting> existingLdapSettings = accessService.getLdapSettings();
            Map<Object, Object> ldapSettingsMap = (Map<Object, Object>) security.get(SECURITY_LDAP_SETTINGS);

            if (isAttemptToDeleteNonExistingConfig(ldapSettingsMap, existingLdapSettings.isEmpty())) {
                throw new IllegalArgumentException(
                        "Attempted to delete:" + SECURITY_LDAP_SETTINGS + " but it does not exist");
            }

            if (ldapSettingsMap.isEmpty()) {
                security.remove(SECURITY_LDAP_SETTINGS);
                Set<String> ldapSettingKeys =
                        existingLdapSettings.stream().map(LdapSetting::getKey).collect(Collectors.toSet());
                for (String ldapSettingKey : ldapSettingKeys) {
                    accessService.removeLdapSetting(ldapSettingKey);
                    movedResources++;
                }
                return movedResources;
            }

            for (Map.Entry<Object, Object> entry : ldapSettingsMap.entrySet()) {
                String settingKey = (String) entry.getKey();
                Optional<LdapSetting> optionalLdapSetting =
                        existingLdapSettings.stream().filter(ldapSetting -> ldapSetting.getKey().equals(settingKey))
                                .findFirst();

                Map<Object, Object> innerLdapSettingsMap = (Map<Object, Object>) entry.getValue();
                movedResources +=
                        migrateLdapSetting(innerLdapSettingsMap, settingKey, optionalLdapSetting);
            }
            security.remove(SECURITY_LDAP_SETTINGS);
        }
        return movedResources;
    }

    private int migrateLdapSetting(Map<Object, Object> innerLdapSettingsMap, String settingKey,
            Optional<LdapSetting> optionalLdapSetting) {
        int movedResources = 0;
        if (isAttemptToDeleteNonExistingConfig(innerLdapSettingsMap, optionalLdapSetting.isEmpty())) {
            throw new IllegalArgumentException(
                    "Attempted to delete:" + settingKey + " but it does not exist");
        }

        if (innerLdapSettingsMap.isEmpty()) {
            accessService.removeLdapSetting(settingKey);
            movedResources++;
            return movedResources;
        }

        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey(settingKey);

        if (optionalLdapSetting.isPresent()) {
            ldapSetting = new LdapSetting(optionalLdapSetting.get());
        }

        for (LdapSettingsEnum ldapSettingsEnum : LdapSettingsEnum.values()) {
            boolean populated = ldapSettingsEnum.populate(ldapSetting, innerLdapSettingsMap);
            if (populated) {
                movedResources++;
            }
        }

        movedResources += migrateSearchSetting(innerLdapSettingsMap, ldapSetting);
        ldapSettings.add(ldapSetting);

        return movedResources;
    }

    private int migrateSearchSetting(Map<Object, Object> innerLdapSettingsMap, LdapSetting ldapSetting) {
        int movedResources = 0;
        if (!innerLdapSettingsMap.containsKey(LdapConfigKeys.SEARCH)) {
            return movedResources;
        }

        Map<Object, Object> searchMap =
                (Map<Object, Object>) innerLdapSettingsMap.get(LdapConfigKeys.SEARCH);

        SearchPattern search = ldapSetting.getSearch();

        if (searchMap.isEmpty()) {
            ldapSetting.setSearch(new SearchPattern());
            innerLdapSettingsMap.remove(LdapConfigKeys.SEARCH);
            movedResources++;
            return movedResources;

        }

        SearchPattern searchPattern = search != null ? search : new SearchPattern();

        for (LdapSearchEnum ldapSearchEnum : LdapSearchEnum.values()) {
            boolean populated = ldapSearchEnum.populate(searchPattern, searchMap);
            if (populated) {
                movedResources++;
            }
        }

        ldapSetting.setSearch(searchPattern);
        innerLdapSettingsMap.remove(LdapConfigKeys.SEARCH);

        return movedResources;
    }

    enum LdapSettingsEnum {
        ALLOW_USER_TO_ACCESS_PROFILE(LdapConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE) {
            @Override
            boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapSetting::setAllowUserToAccessProfile, getParseBooleanFunc(),
                        configMap);
            }
        },
        EMAIL_ATTR(LdapConfigKeys.EMAIL_ATTR) {
            @Override
            boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapSetting::setEmailAttribute, String::valueOf, configMap);
            }
        },
        POISONING_PROTECTION(LdapConfigKeys.POISONING_PROTECTION) {
            @Override
            boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapSetting::setLdapPoisoningProtection, getParseBooleanFunc(),
                        configMap);
            }
        },
        URL(LdapConfigKeys.URL) {
            @Override
            boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapSetting::setLdapUrl, String::valueOf, configMap);
            }
        },
        USER_DN_PATTERN(LdapConfigKeys.USER_DN_PATTERN) {
            @Override
            boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapSetting::setUserDnPattern, String::valueOf, configMap);
            }
        },
        AUTO_CREATE_USER(LdapConfigKeys.AUTO_CREATE_USER) {
            @Override
            boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapSetting::setAutoCreateUser, getParseBooleanFunc(), configMap);
            }
        },
        PAGING_SUPPORT_ENABLED(LdapConfigKeys.PAGING_SUPPORT_ENABLED) {
            @Override
            boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapSetting::setPagingSupportEnabled, getParseBooleanFunc(),
                        configMap);
            }
        },
        ENABLED(LdapConfigKeys.ENABLED) {
            @Override
            boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), ldapSetting::setEnabled, getParseBooleanFunc(), configMap);
            }
        };

        abstract boolean populate(LdapSetting ldapSetting, Map<Object, Object> configMap);

        private final String key;

        LdapSettingsEnum(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    enum LdapSearchEnum {

        MANAGER_DN(LdapConfigKeys.Search.MANAGER_DN) {
            @Override
            boolean populate(SearchPattern searchPattern, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), searchPattern::setManagerDn, String::valueOf, configMap);
            }
        },
        MANAGER_PASSWORD(LdapConfigKeys.Search.MANAGER_PASSWORD) {
            @Override
            boolean populate(SearchPattern searchPattern, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), searchPattern::setManagerPassword, String::valueOf, configMap);
            }
        },
        SEARCH_BASE(LdapConfigKeys.Search.SEARCH_BASE) {
            @Override
            boolean populate(SearchPattern searchPattern, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), searchPattern::setSearchBase, String::valueOf, configMap);
            }
        },
        SEARCH_FILTER(LdapConfigKeys.Search.SEARCH_FILTER) {
            @Override
            boolean populate(SearchPattern searchPattern, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), searchPattern::setSearchFilter, String::valueOf, configMap);
            }
        },
        SEARCH_SUB_TREE(LdapConfigKeys.Search.SEARCH_SUB_TREE) {
            @Override
            boolean populate(SearchPattern searchPattern, Map<Object, Object> configMap) {
                return populateValueFromMap(getKey(), searchPattern::setSearchSubTree, getParseBooleanFunc(),
                        configMap);
            }
        };

        abstract boolean populate(SearchPattern searchPattern, Map<Object, Object> configMap);

        private final String key;

        LdapSearchEnum(String key) {
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
