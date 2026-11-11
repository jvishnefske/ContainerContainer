package org.artifactory.config;

import lombok.experimental.UtilityClass;
import org.artifactory.descriptor.security.EncryptionPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.artifactory.config.AccessConfigHolder.AUTHENTICATION_BASIC_AUTH_ENABLED;
import static org.artifactory.config.AccessConfigHolder.AUTHENTICATION_DISABLE_REMEMBER_ME;
import static org.artifactory.config.AccessConfigHolder.AUTHENTICATION_MAP;
import static org.artifactory.config.AccessConfigHolder.AUTHENTICATION_PASSWORD_ENCRYPTION;
import static org.artifactory.config.AccessConfigHolder.AUTHENTICATION_USER_PLUGIN;
import static org.artifactory.config.AccessConfigHolder.AUTHENTICATION_VERIFY_PASSWORD_POLICY;
import static org.artifactory.config.AccessConfigHolder.ENABLED_REALMS;
import static org.artifactory.config.AccessConfigHolder.SECURITY_ANONYMOUS_ACCESS_ENABLED;
import static org.artifactory.config.AccessConfigHolder.SECURITY_MAP;
import static org.artifactory.config.AccessConfigHolder.SECURITY_USER_LOCK_POLICY_MAP;
import static org.artifactory.config.AccessConfigHolder.USER_LOCK_POLICY_ATTEMPTS;

/**
 * @author Mor Merhav
 */
@UtilityClass
public class AccessConfigMapUtils {

    public void setEnabledRealms(Set<String> enabledRealms, Map<String, Object> map) {
        Map<String, Object> securityMap = getOrCreate(SECURITY_MAP, map);
        Map<String, Object> authMap = getOrCreate(AUTHENTICATION_MAP, securityMap);
        authMap.put(ENABLED_REALMS, enabledRealms);
    }

    public void addToUserLockPolicy(String key, Object value, Map<String, Object> map) {
        Map<String, Object> securityMap = getOrCreate(SECURITY_MAP, map);
        Map<String, Object> userLockPolicy = getOrCreate(SECURITY_USER_LOCK_POLICY_MAP, securityMap);
        userLockPolicy.put(key, value);
    }

    public static void setClientTempLogin(boolean enabled, Map<String, Object> accessConfigMap) {
        addToUserLockPolicy("max-login-delay-millis", 60000L, accessConfigMap);
        addToUserLockPolicy("client-login-suspension-enabled", enabled, accessConfigMap);
    }

    public void addToAuthentication(String key, Object value, Map<String, Object> map) {
        Map<String, Object> securityMap = getOrCreate(SECURITY_MAP, map);
        Map<String, Object> authMap = getOrCreate(AUTHENTICATION_MAP, securityMap);
        authMap.put(key, value);
    }

    public void addPasswordEncryptionPolicy(EncryptionPolicy encryptionPolicy, Map<String, Object> map) {
        addToAuthentication(AUTHENTICATION_PASSWORD_ENCRYPTION, encryptionPolicy.getType(), map);
    }

    public void addVerifyPasswordPolicy(boolean value, Map<String, Object> map) {
        addToAuthentication(AUTHENTICATION_VERIFY_PASSWORD_POLICY, value, map);
    }

    public void addDisableRememberMe(boolean value, Map<String, Object> map) {
        addToAuthentication(AUTHENTICATION_DISABLE_REMEMBER_ME, value, map);
    }

    public void addBasicAuthenticationEnabled(boolean value, Map<String, Object> map) {
        addToAuthentication(AUTHENTICATION_BASIC_AUTH_ENABLED, value, map);
    }

    public void addUserPlugin(boolean value, Map<String, Object> map) {
        addToAuthentication(AUTHENTICATION_USER_PLUGIN, value, map);
    }

    public void addLoginAttempts(Integer value, Map<String, Object> map) {
        addToUserLockPolicy(USER_LOCK_POLICY_ATTEMPTS, value.longValue(), map);
    }

    public void addAnonAccessEnabled(Boolean anonAccessEnabled, Map<String, Object> map) {
        Map<String, Object> securityMap = getOrCreate(SECURITY_MAP, map);
        securityMap.put(SECURITY_ANONYMOUS_ACCESS_ENABLED, anonAccessEnabled);
    }

    Map<String, Object> getOrCreate(String key, Map<String, Object> map) {
        if (map.containsKey(key)) {
            return (Map<String, Object>) map.get(key);
        }
        map.put(key, new HashMap<String, Object>());
        return (Map<String, Object>) map.get(key);
    }
}
