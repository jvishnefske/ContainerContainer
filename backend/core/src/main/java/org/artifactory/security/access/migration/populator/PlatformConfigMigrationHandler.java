package org.artifactory.security.access.migration.populator;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigMapUtils;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.artifactory.security.access.AccessService;
import org.artifactory.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.artifactory.config.AccessConfigKeys.PASSWORD_SETTINGS_ENCRYPTION_POLICY;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_ANON_ACCESS_ENABLED;
import static org.artifactory.config.AccessConfigKeys.SECURITY_BASIC_AUTH_ENABLED;
import static org.artifactory.config.AccessConfigKeys.SECURITY_PASSWORD_SETTINGS;
import static org.artifactory.config.AccessConfigKeys.SECURITY_USER_LOCK_POLICY;
import static org.artifactory.config.AccessConfigKeys.USER_LOCK_POLICY_ENABLED;
import static org.artifactory.config.AccessConfigKeys.USER_LOCK_POLICY_LOGIN_ATTEMPTS;

/**
 * @author morm
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
class PlatformConfigMigrationHandler implements PatchYamlAccessConfigMigrationHandler {
    private final Map<String, Object> accessMap = new HashMap<>();

    private final AccessService accessService;

    @Autowired
    PlatformConfigMigrationHandler(AccessService accessService) {
        this.accessService = accessService;
    }

    @SuppressWarnings("unchecked")
    public int migrate(Map<String, Object> yamlConfigMap) {
        int movedResources = 0;
        if (yamlConfigMap.containsKey(SECURITY)) {
            Map<Object, Object> security = (Map<Object, Object>) yamlConfigMap.get(SECURITY);
            if (security.containsKey(SECURITY_BASIC_AUTH_ENABLED)) {
                boolean basicAuthEnabled = (Boolean) security.get(SECURITY_BASIC_AUTH_ENABLED);
                if (Boolean.FALSE.equals(basicAuthEnabled) && !SecurityUtils.isAnyExternalAuthEnabled()) {
                    log.warn("Cannot turn off basic auth when no external authentication providers exist");
                } else {
                    AccessConfigMapUtils.addBasicAuthenticationEnabled(basicAuthEnabled, accessMap);
                    movedResources++;
                }
                security.remove(SECURITY_BASIC_AUTH_ENABLED);
            }
            if (security.containsKey(SECURITY_ANON_ACCESS_ENABLED)) {
                AccessConfigMapUtils.addAnonAccessEnabled((Boolean) security.get(SECURITY_ANON_ACCESS_ENABLED),
                        accessMap);
                movedResources++;
                security.remove(SECURITY_ANON_ACCESS_ENABLED);
            }
            if (security.containsKey(SECURITY_USER_LOCK_POLICY)) {
                Map<Object, Object> userLockPolicy = (Map<Object, Object>) security.get(SECURITY_USER_LOCK_POLICY);
                movedResources = extractUserLockPolicy(movedResources, userLockPolicy, security);
            }
            if (security.containsKey(SECURITY_PASSWORD_SETTINGS)) {
                Map<Object, Object> passwordSettings = (Map<Object, Object>) security.get(SECURITY_PASSWORD_SETTINGS);
                if (passwordSettings.containsKey(PASSWORD_SETTINGS_ENCRYPTION_POLICY)) {
                    EncryptionPolicy encryptionPolicy = EncryptionPolicy.valueOf(
                            passwordSettings.get(PASSWORD_SETTINGS_ENCRYPTION_POLICY).toString().toUpperCase(
                                    Locale.ROOT));
                    AccessConfigMapUtils.addPasswordEncryptionPolicy(encryptionPolicy, accessMap);
                    movedResources++;
                }
                security.remove(SECURITY_PASSWORD_SETTINGS);
            }
        }
        if (movedResources > 0) {
            accessService.updateConfigurationModel(accessMap);
        }
        return movedResources;
    }

    private int extractUserLockPolicy(int movedResources, Map<Object, Object> userLockPolicy,
            Map<Object, Object> security) {
        if (userLockPolicy.containsKey(USER_LOCK_POLICY_LOGIN_ATTEMPTS) &&
                userLockPolicy.containsKey(USER_LOCK_POLICY_ENABLED)) {
            AccessConfigMapUtils.addLoginAttempts(
                    (boolean) userLockPolicy.get(USER_LOCK_POLICY_ENABLED) ?
                            (int) userLockPolicy.get(USER_LOCK_POLICY_LOGIN_ATTEMPTS) : 0, accessMap);
            ++movedResources;
        }
        if ((userLockPolicy.containsKey(USER_LOCK_POLICY_ENABLED) &&
                !(boolean) userLockPolicy.get(USER_LOCK_POLICY_ENABLED)) ||
                (userLockPolicy.containsKey(USER_LOCK_POLICY_LOGIN_ATTEMPTS) &&
                        ((int) userLockPolicy.get(USER_LOCK_POLICY_LOGIN_ATTEMPTS) == 0))) {
            AccessConfigMapUtils.addLoginAttempts(0, accessMap);
            ++movedResources;
        }
        security.remove(SECURITY_USER_LOCK_POLICY);
        return movedResources;
    }
}