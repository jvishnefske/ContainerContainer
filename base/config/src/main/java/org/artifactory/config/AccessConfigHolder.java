package org.artifactory.config;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.SamlEncryptedAssertion;
import org.jfrog.access.client.model.SamlSetting;
import org.jfrog.access.rest.system.LdapConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class AccessConfigHolder {

    public static final String SECURITY_MAP = "security";
    public static final String AUTHENTICATION_MAP = "authentication";
    public static final String ENABLED_REALMS = "enabled-realms";
    public static final String AUTHENTICATION_PASSWORD_ENCRYPTION = "password-encryption";
    public static final String AUTHENTICATION_DISABLE_REMEMBER_ME = "disable-remember-me";
    public static final String AUTHENTICATION_BASIC_AUTH_ENABLED = "basic-authentication-enabled";
    public static final String AUTHENTICATION_USER_PLUGIN = "user-plugin";
    public static final String AUTHENTICATION_VERIFY_PASSWORD_POLICY = "verify-password-policy";
    public static final String SECURITY_USER_LOCK_POLICY_MAP = "user-lock-policy";
    public static final String SECURITY_ANONYMOUS_ACCESS_ENABLED = "anonymous-access-enabled";
    public static final String USER_LOCK_POLICY_ATTEMPTS = "attempts";
    public static final String USER_LOCK_POLICY_PASSWORD_EXPIRY_DAYS = "password-expiry-days";
    public static final String LOGIN_BLOCK_DELAY_MILLIS = "base-login-delay-millis";
    public static final String LOGIN_MAX_BLOCK_DELAY_MILLIS = "max-login-delay-millis";
    public static final String MAX_LOGIN_DELAY_INCORRECT_ATTEMPTS = "max-login-delay-incorrect-attempts";
    private final Map<String, Object> configMap = new HashMap<>();
    private final Object lockObject = new Object();
    private final List<LdapGroupSetting> ldapGroupSettingList = new ArrayList<>();
    private final List<LdapSetting> ldapSettingList = new ArrayList<>();

    private final Object httpSsoLockObject = new Object();
    private HttpSsoSetting httpSsoSetting;
    private final Object ldapLockObject = new Object();
    private LdapConfig ldapConfig;
    private final Object crowdLockObject = new Object();
    private CrowdSetting crowdSetting;
    private final List<OAuthSetting> oAuthSettingList = new ArrayList<>();
    private OAuthGeneralSettings oAuthGeneralSettings;
    private final Object oAuthLockObject = new Object();
    private final Object samlLockObject = new Object();
    private SamlSetting samlSetting;
    private ProxyDescriptor proxySettings = null;
    private List<ProxyDescriptor> allProxies = null;
    private SamlEncryptedAssertion samlEncryptedAssertion;

    @VisibleForTesting
    AccessConfigHolder() {
    }

    public static AccessConfigHolder getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public Map<String, Object> drainConfig() {
        synchronized (lockObject) {
            Map<String, Object> mapCopy = new HashMap<>(configMap);
            configMap.clear();
            return mapCopy;
        }
    }

    public ProxyDescriptor drainProxySettings() {
        synchronized (lockObject) {
            ProxyDescriptor proxy = proxySettings;
            proxySettings = null;
            return proxy;
        }
    }

    public void setProxySettings(ProxyDescriptor proxyDescriptor) {
        synchronized (lockObject) {
            this.proxySettings = proxyDescriptor;
        }
    }

    /**
     * Drain the staged list of all proxies for mirroring to Access under
     * {@code proxy.settings.all}. Returns {@code null} if nothing was staged
     * (no descriptor save in this cycle); an empty list means the descriptor
     * has no proxies and Access should be cleared.
     */
    public List<ProxyDescriptor> drainAllProxies() {
        synchronized (lockObject) {
            List<ProxyDescriptor> copy = allProxies;
            allProxies = null;
            return copy;
        }
    }

    public void setAllProxies(List<ProxyDescriptor> proxies) {
        synchronized (lockObject) {
            this.allProxies = proxies == null ? List.of() : new ArrayList<>(proxies);
        }
    }

    public List<LdapSetting> drainLdapSetting() {
        synchronized (lockObject) {
            List<LdapSetting> listCopy = new ArrayList<>(ldapSettingList);
            ldapSettingList.clear();
            return listCopy;
        }
    }

    public List<LdapGroupSetting> drainLdapGroupSetting() {
        List<LdapGroupSetting> listCopy = new ArrayList<>(ldapGroupSettingList);
        ldapGroupSettingList.clear();
        return listCopy;
    }

    public void addPasswordEncryptionPolicy(EncryptionPolicy encryptionPolicy) {
        AccessConfigMapUtils.addPasswordEncryptionPolicy(encryptionPolicy, configMap);
    }

    public void addDisableRememberMe(boolean value) {
        addToAuthentication(AUTHENTICATION_DISABLE_REMEMBER_ME, value);
    }

    public void addBasicAuthenticationEnabled(boolean value) {
        addToAuthentication(AUTHENTICATION_BASIC_AUTH_ENABLED, value);
    }

    public void addLoginAttempts(int loginAttempts) {
        addToUserLockPolicy(USER_LOCK_POLICY_ATTEMPTS, loginAttempts);
    }

    public LdapConfig drainLdapConfig() {
        synchronized (ldapLockObject) {
            LdapConfig copy = ldapConfig;
            ldapConfig = null;
            return copy;
        }
    }

    public void setLdapConfig(LdapConfig ldapConfig) {
        synchronized (ldapLockObject) {
            this.ldapConfig = ldapConfig;
        }
    }

    public HttpSsoSetting drainHttpSsoSetting() {
        synchronized (httpSsoLockObject) {
            HttpSsoSetting copy = httpSsoSetting;
            httpSsoSetting = null;
            return copy;
        }
    }

    public void setHttpSsoSetting(HttpSsoSetting httpSsoSetting) {
        synchronized (httpSsoLockObject) {
            this.httpSsoSetting = httpSsoSetting;
        }
    }

    public CrowdSetting drainCrowdSetting() {
        synchronized (crowdLockObject) {
            CrowdSetting copy = crowdSetting;
            crowdSetting = null;
            return copy;
        }
    }

    public void setCrowdSetting(CrowdSetting crowdSetting) {
        synchronized (crowdLockObject) {
            this.crowdSetting = crowdSetting;
        }
    }

    public SamlSetting drainSamlSetting() {
        synchronized (samlLockObject) {
            SamlSetting copy = samlSetting;
            samlSetting = null;
            return copy;
        }
    }

    public void setSamlSetting(SamlSetting samlSetting) {
        synchronized (samlLockObject) {
            this.samlSetting = samlSetting;
        }
    }

    public void setSamlEncryption(SamlEncryptedAssertion samlEncryptedAssertion) {
        synchronized (lockObject) {
            this.samlEncryptedAssertion = samlEncryptedAssertion;
        }
    }

    public boolean isNullSamlEncryption() {
        return samlEncryptedAssertion == null;
    }

    public SamlEncryptedAssertion drainSamlEncryptedAssertion() {
        synchronized (lockObject) {
            SamlEncryptedAssertion copy = samlEncryptedAssertion;
            samlEncryptedAssertion = null;
            return copy;
        }
    }

    private static final class InstanceHolder {
        private static final AccessConfigHolder INSTANCE = new AccessConfigHolder();
    }

    public boolean isEmpty() {
        return configMap.isEmpty();
    }

    public boolean isNullLdapConfig() {
        return ldapConfig == null;
    }

    public boolean isNullHttpSsoSetting() {
        return httpSsoSetting == null;
    }

    public boolean isNullCrowdSetting() {
        return crowdSetting == null;
    }

    public boolean isNullSamlSetting() {
        return samlSetting == null;
    }

    public void addPasswordExpiryDays(int passwordExpiryDays) {
        addToUserLockPolicy(USER_LOCK_POLICY_PASSWORD_EXPIRY_DAYS, passwordExpiryDays);
    }

    public void addLoginBlockDelayMillis(long loginBlockDelayMillis) {
        addToUserLockPolicy(LOGIN_BLOCK_DELAY_MILLIS, loginBlockDelayMillis);
    }

    public void addLoginMaxBlockDelayMillis(long maxLoginBlockDelayMillis) {
        addToUserLockPolicy(LOGIN_MAX_BLOCK_DELAY_MILLIS, maxLoginBlockDelayMillis);
    }

    public void addMaxIncorrectLoginAttempts(long maxIncorrectLoginAttempts) {
        addToUserLockPolicy(MAX_LOGIN_DELAY_INCORRECT_ATTEMPTS, maxIncorrectLoginAttempts);
    }

    public void addAnonAccessEnabled(Boolean anonAccessEnabled) {
        synchronized (lockObject) {
            Map<String, Object> securityMap = AccessConfigMapUtils.getOrCreate(SECURITY_MAP, configMap);
            securityMap.put(SECURITY_ANONYMOUS_ACCESS_ENABLED, anonAccessEnabled);
        }
    }

    private void addToAuthentication(String key, Object value) {
        synchronized (lockObject) {
            AccessConfigMapUtils.addToAuthentication(key, value, configMap);
        }
    }

    private void addToUserLockPolicy(String key, long value) {
        synchronized (lockObject) {
            AccessConfigMapUtils.addToUserLockPolicy(key, value, configMap);
        }
    }

    public void addLdapSetting(List<LdapSetting> settingList) {
        synchronized (lockObject) {
            ldapSettingList.addAll(settingList);
        }
    }

    public void addLdapGroupSetting(List<LdapGroupSetting> groupSettings) {
        synchronized (lockObject) {
            ldapGroupSettingList.addAll(groupSettings);
        }
    }

    public void addOAuthSetting(List<OAuthSetting> oAuthSettings) {
        synchronized (oAuthLockObject) {
            oAuthSettingList.addAll(oAuthSettings);
        }
    }

    public void addOAuthGeneralSettings(OAuthGeneralSettings oAuthGeneralSettings) {
        synchronized (oAuthLockObject) {
            this.oAuthGeneralSettings = oAuthGeneralSettings;
        }
    }

    public List<OAuthSetting> drainOAuthSetting() {
        synchronized (oAuthLockObject) {
            List<OAuthSetting> listCopy = new ArrayList<>(oAuthSettingList);
            oAuthSettingList.clear();
            return listCopy;
        }
    }

    public OAuthGeneralSettings drainOAuthGeneralSettings() {
        synchronized (oAuthLockObject) {
            OAuthGeneralSettings copy = oAuthGeneralSettings;
            oAuthGeneralSettings = null;
            return copy;
        }
    }

    public boolean isNullOAuthGeneralSetting() {
        return oAuthGeneralSettings == null;
    }

    public boolean isNullOAuthSettings() {
        return oAuthSettingList.isEmpty();
    }
}