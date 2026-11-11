package org.artifactory.config;

import lombok.experimental.UtilityClass;

/**
 * Holds the keys of the access config as it comes from the patch yaml
 *
 * @author Mor Merhav
 * @see
 * <a href="https://www.jfrog.com/confluence/display/JFROG/Artifactory+YAML+Configuration">Artifactory YAML Configuration</a>
 * @see org.artifactory.rest.resource.system.ConfigResource#patchConfig
 */
@UtilityClass
public class AccessConfigKeys {

    public static final String ACCESS_ADOPTION_CONFIG = "adoption.config";
    public static final String ACCESS_ADOPTION_CONFIG_DISABLED = "disabled";

    public static final String SECURITY = "security";

    // Directly under security
    public static final String SECURITY_ANON_ACCESS_ENABLED = "anonAccessEnabled";
    public static final String SECURITY_USER_LOCK_POLICY = "userLockPolicy";
    public static final String SECURITY_PASSWORD_SETTINGS = "passwordSettings";
    public static final String SECURITY_BASIC_AUTH_ENABLED = "basicAuthEnabled";
    public static final String SECURITY_LDAP_GROUP_SETTINGS = "ldapGroupSettings";
    public static final String SECURITY_LDAP_SETTINGS = "ldapSettings";
    public static final String CROWD_SETTINGS = "crowdSettings";
    public static final String HTTP_SSO_SETTINGS = "httpSsoSettings";
    public static final String SAML_SETTINGS = "samlSettings";
    public static final String OAUTH_SETTINGS = "oauthSettings";
    public static final String OAUTH_PROVIDER_SETTINGS = "oauthProvidersSettings";

    // Directly under password settings
    public static final String PASSWORD_SETTINGS_ENCRYPTION_POLICY = "encryptionPolicy";


    // Directly under user lock policy
    public static final String USER_LOCK_POLICY_LOGIN_ATTEMPTS = "loginAttempts";
    public static final String USER_LOCK_POLICY_ENABLED = "enabled";


    public static final String REMOTE_USER = "REMOTE_USER";

    public static final class HttpSsoConfigKeys {
        public static final String ALLOW_USER_TO_ACCESS_PROFILE = "allowUserToAccessProfile";
        public static final String REMOTE_USER_REQUEST_VARIABLE = "remoteUserRequestVariable";
        public static final String HTTP_SSO_PROXIED = "httpSsoProxied";
        public static final String SYNC_LDAP_GROUPS = "syncLdapGroups";
        public static final String NO_AUTO_CREATION = "noAutoUserCreation";

    }

    public static final class LdapConfigKeys {
        public static final String LDAP_SETTING = "ldapSetting";
        public static final String ALLOW_USER_TO_ACCESS_PROFILE = "allowUserToAccessProfile";
        public static final String AUTO_CREATE_USER = "autoCreateUser";
        public static final String URL = "ldapUrl";
        public static final String ENABLED = "enabled";
        public static final String EMAIL_ATTR = "emailAttribute";
        public static final String USER_DN_PATTERN = "userDnPattern";
        public static final String POISONING_PROTECTION = "ldapPoisoningProtection";
        public static final String PAGING_SUPPORT_ENABLED = "pagingSupportEnabled";
        public static final String SEARCH = "search";

        public static final class Search {
            public static final String SEARCH_BASE = "searchBase";
            public static final String SEARCH_FILTER = "searchFilter";
            public static final String SEARCH_SUB_TREE = "searchSubTree";
            public static final String MANAGER_DN = "managerDn";
            public static final String MANAGER_PASSWORD = "managerPassword";
        }
    }

    public static final class LdapGroupConfigKeys {
        public static final String SECURITY_LDAP_GROUP_SETTING = "ldapGroupSetting";
        public static final String LDAP_GROUP_NAME = "name";
        public static final String GROUP_BASE_DN = "groupBaseDn";
        public static final String GROUP_NAME_ATTR = "groupNameAttribute";
        public static final String GROUP_MEMBER_ATTR = "groupMemberAttribute";
        public static final String SUB_TREE = "subTree";
        public static final String FILTER = "filter";
        public static final String DESC_ATTR = "descriptionAttribute";
        public static final String STRATEGY = "strategy";
        public static final String FORCE_ATTR_SEARCH = "forceAttributeSearch";
        public static final String ENABLED_LDAP = "enabledLdap";

    }

    public static final class CrowdConfigKeys {
        public static final String ALLOW_USER_TO_ACCESS_PROFILE = "allowUserToAccessProfile";
        public static final String APPLICATION_NAME = "applicationName";
        public static final String PASSWORD = "password";
        public static final String SERVER_URL = "serverUrl";
        public static final String SESSION_VALIDATION_INTERVAL = "sessionValidationInterval";
        public static final String ENABLE_INTEGRATION = "enableIntegration";
        public static final String NO_AUTO_CREATION = "noAutoUserCreation";
        public static final String CUSTOM_COOKIE_TOKEN_KEY = "customCookieTokenKey";
        public static final String DIRECT_AUTHENTICATION = "directAuthentication";
        public static final String OVERRIDE_ALL_GROUPS_UPON_LOGIN = "overrideAllGroupsUponLogin";
        public static final String USE_DEFAULT_PROXY = "useDefaultProxy";

    }

    @UtilityClass
    public static final class SamlConfigKeys {
        public static final String LOGIN_URL = "loginUrl";
        public static final String LOGOUT_URL = "logoutUrl";
        public static final String SERVICE_PROVIDER_NAME = "serviceProviderName";
        public static final String CERTIFICATE = "certificate";
        public static final String USE_ENCRYPTED_ASSERTION = "useEncryptedAssertion";
        public static final String SYNC_GROUPS = "syncGroups";
        public static final String GROUP_ATTRIBUTE = "groupAttribute";
        public static final String EMAIL_ATTRIBUTE = "emailAttribute";
        public static final String NO_AUTO_USER_CREATION = "noAutoUserCreation";
        public static final String ALLOW_USER_TO_ACCESS_PROFILE = "allowUserToAccessProfile";
        public static final String AUTO_REDIRECT = "autoRedirect";
        public static final String VERIFY_AUDIENCE_RESTRICTION = "verifyAudienceRestriction";
        public static final String ENABLE_INTEGRATION = "enableIntegration";
    }

    @UtilityClass
    public static final class OAuthConfigKeys {
        public static final String ENABLED = "enabled";
        public static final String PROVIDER_TYPE = "providerType";
        public static final String SECRET = "secret";
        public static final String ID = "id";
        public static final String API_URL = "apiUrl";
        public static final String AUTH_URL = "authUrl";
        public static final String TOKEN_URL = "tokenUrl";
        public static final String BASIC_URL = "basicUrl";
        public static final String DOMAIN = "domain";
        public static final String PKCE = "pkce";
        public static final String USE_DEFAULT_PROXY = "useDefaultProxy";
        //General OAuth settings
        public static final String ENABLE_INTEGRATION = "enableIntegration";
        public static final String DEFAULT = "defaultNpm";
        public static final String ALLOW_USER_TO_ACCESS_PROFILE = "allowUserToAccessProfile";
        public static final String PERSIST_USERS = "persistUsers";
    }
}