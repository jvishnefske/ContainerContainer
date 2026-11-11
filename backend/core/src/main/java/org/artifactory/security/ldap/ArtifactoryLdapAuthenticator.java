/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.security.ldap;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.ObjectUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LdapGroupAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.concurrent.ArtifactoryRunnable;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.EntitlementsEventListener;
import org.artifactory.spring.Reloadable;
import org.jfrog.access.model.Realm;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.config.diff.DataDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Wrapper for the LDAP bind authenticator. Used to authenticate users against ldap and as a factory for the security
 * context and actual authenticator.
 *
 * @author Yossi Shaul
 */
@Reloadable(beanClass = InternalLdapAuthenticator.class,
        initAfter = {InternalCentralConfigService.class, AccessService.class},
        listenOn = {CentralConfigKey.security, CentralConfigKey.proxies})
@Component
public class ArtifactoryLdapAuthenticator implements InternalLdapAuthenticator, EntitlementsEventListener,
        ContextCreationListener {
    public static final String LDAP_SERVICE_MISCONFIGURED = "LDAP service misconfigured";
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryLdapAuthenticator.class);
    private static final String NO_LDAP_SERVICE_CONFIGURED = "No LDAP service configured";
    public static final String LDAP_CONNECT_TIMEOUT_DEFAULT_MILLIS = "10000";

    @Autowired
    private CentralConfigService centralConfig;
    @Autowired
    private AccessService accessService;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private AolService aolService;
    @Autowired
    private ArtifactoryApplicationContext applicationContext;

    private Map<String, BindAuthenticator> authenticators;
    private ArtifactoryHome artifactoryHome = ArtifactoryHome.get();

    static LdapContextSource createSecurityContext(LdapSetting ldapSetting, ArtifactoryHome artifactoryHome) {
        return createSecurityContextWithBaseFilter(ldapSetting, s -> s, artifactoryHome);
    }

    static LdapContextSource createSecurityContextForDomain(LdapSetting ldapSetting, ArtifactoryHome artifactoryHome) {
        return createSecurityContextWithBaseFilter(ldapSetting, ArtifactoryLdapAuthenticator::filterDomainComponents,
                artifactoryHome);
    }

    private static LdapContextSource createSecurityContextWithBaseFilter(LdapSetting ldapSetting,
            UnaryOperator<String> filter, ArtifactoryHome artifactoryHome) {
        String url = ldapSetting.getLdapUrl();
        String scheme = getLdapScheme(url);
        String baseUrl = getLdapBaseUrl(scheme, url);
        DefaultSpringSecurityContextSource contextSource =
                new DefaultSpringSecurityContextSource(scheme + baseUrl);
        String base = url.substring((scheme + baseUrl).length());
        String filteredBase = filter.apply(base);
        contextSource.setBase(adjustBase(filteredBase));

        // set default connection timeout, read timeout and referral strategy.
        Map<String, Object> env = new HashMap<>();
        String connectTimeout = artifactoryHome.getArtifactoryProperties().getProperty(
                "artifactory.security.ldap.connect.timeoutMillis", LDAP_CONNECT_TIMEOUT_DEFAULT_MILLIS);
        env.put("com.sun.jndi.ldap.connect.timeout", connectTimeout);
        String readTimeout = artifactoryHome.getArtifactoryProperties().getProperty(
                "artifactory.security.ldap.socket.timeoutMillis", "15000");
        env.put("com.sun.jndi.ldap.read.timeout", readTimeout);
        String referralStrategy = artifactoryHome.getArtifactoryProperties().getProperty(
                "artifactory.security.ldap.referralStrategy", "follow");
        env.put(Context.REFERRAL, referralStrategy);
        String poolIdleTimeout = artifactoryHome.getArtifactoryProperties().getProperty(
                "artifactory.security.ldap.pool.timeoutMillis", null);
        if (poolIdleTimeout != null) {
            env.put("com.sun.jndi.ldap.connect.pool.timeout", poolIdleTimeout);
        }

        contextSource.setBaseEnvironmentProperties(env);
        SearchPattern searchPattern = ldapSetting.getSearch();
        if (searchPattern != null) {
            if (PathUtils.hasText(searchPattern.getManagerDn())) {
                contextSource.setUserDn(searchPattern.getManagerDn());
                contextSource.setPassword(
                        CryptoHelper.decryptIfNeeded(artifactoryHome, searchPattern.getManagerPassword()));
            } else {
                contextSource.setAnonymousReadOnly(true);
            }
        }

        try {
            contextSource.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return contextSource;
    }

    static String filterDomainComponents(String base) {
        if (base.isEmpty()) {
            return "";
        }
        String string = base.startsWith("/") ? base.substring(1) : base;
        return Arrays.stream(string.split(",\\s?"))
                .map(s -> s.split("="))
                .filter(strings -> strings[0].equalsIgnoreCase("dc"))
                .map(strings -> strings[0] + "=" + strings[1])
                .collect(Collectors.joining(","));
    }

    private static String getLdapBaseUrl(String scheme, String url) {
        url = url.substring(scheme.length());
        int index = url.indexOf('/');
        if (index != -1) {
            return url.substring(0, index);
        } else {
            return url;
        }
    }

    private static String getLdapScheme(String url) {
        String scheme = url.substring(0, url.indexOf("//") + 2);
        return scheme.substring(0, url.indexOf('/')) + "//";
    }

    /**
     * Adjust the base {@link org.springframework.ldap.core.DistinguishedName} of the {@link LdapContextSource} which is
     * space escaped
     *
     * @param url The entire LDAP url
     * @return The escaped base DN
     */
    private static String adjustBase(String url) {
        url = url.replace(" ", "%20");
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not parse LDAP URL.", e);
        }
        String base = uri.getPath();
        if (base.indexOf('/') == 0) {
            return base.substring(1);
        } else {
            return base;
        }
    }

    @Override
    public void init() {
        log.trace("ArtifactoryLdapAuthenticator initialization started");
        try {
            authenticators = createBindAuthenticators();
            if (authenticators.isEmpty()) {
                authenticators = null;
            }
        } catch (Exception e) {
            log.error("Failed to create LDAP authenticator. Please verify and fix your LDAP settings.", e);
        }
        if (authenticators == null) {
            log.debug("LDAP service is disabled");
        }
        log.trace("ArtifactoryLdapAuthenticator initialization finished");
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        log.trace("ArtifactoryLdapAuthenticator reload started");
        artifactoryHome = ArtifactoryHome.get();
        boolean descriptorNotEquals = !centralConfig.getDescriptor().getSecurity().equals(oldDescriptor.getSecurity());
        log.trace("descriptorNotEquals {}", descriptorNotEquals);
        boolean proxiesNotEquals = !ObjectUtils
                .equals(centralConfig.getDescriptor().getDefaultProxy(), oldDescriptor.getDefaultProxy());
        log.trace("proxiesNotEquals {}", proxiesNotEquals);
        boolean authenticatorsIsNull = authenticators == null;
        log.trace("authenticatorsIsNull {}", authenticatorsIsNull);
        if (descriptorNotEquals
                || proxiesNotEquals
                || authenticatorsIsNull) {
            authenticators = null;
            init();
        }
        accessService.subscribeToAuthSettingChanges(this::onLdapSettingChange, Realm.LDAP);
        log.trace("ArtifactoryLdapAuthenticator reload finished");
    }

    @Override
    public Map<String, BindAuthenticator> getAuthenticators() {
        return authenticators;
    }

    private Map<String, BindAuthenticator> createBindAuthenticators() {
        Map<String, BindAuthenticator> result = Maps.newLinkedHashMap();
        LdapGroupAddon groupAddon = addonsManager.addonByType(LdapGroupAddon.class);
        boolean aol = aolService.isAol();
        List<LdapSetting> ldapSettings = accessService.getEnabledLdapSettings();
        log.trace("Ldap settings count {}", ldapSettings.size());
        for (LdapSetting ldapSetting : ldapSettings) {
            log.trace("Before creating authenticator for LdapSetting key {} url {} enabled {}",
                    ldapSetting.getKey(), ldapSetting.getLdapUrl(), ldapSetting.isEnabled());
            LdapContextSource contextSource = createSecurityContext(ldapSetting, artifactoryHome);
            ArtifactoryBindAuthenticator bindAuthenticator =
                    new ArtifactoryBindAuthenticator(contextSource, ldapSetting, aol, groupAddon);
            result.put(ldapSetting.getKey(), bindAuthenticator);
            log.trace("After creating authenticator for LdapSetting key {}",
                    ldapSetting.getKey());
        }
        log.trace("Authenticator count {}", result.size());
        return result;
    }

    @Override
    public DirContextOperations authenticate(Authentication authentication) {
        //Spring expects an exception on failed authentication
        if (authenticators != null && accessService.isLdapEnabled()) {
            RuntimeException authenticationException = null;
            for (BindAuthenticator authenticator : authenticators.values()) {
                DirContextOperations user = null;
                try {
                    user = authenticator.authenticate(authentication);
                } catch (RuntimeException e) {
                    authenticationException = e;
                }
                if (user != null) {
                    return user;
                }
            }
            if (authenticationException != null) {
                throw authenticationException;
            }
            throw new AuthenticationServiceException(LDAP_SERVICE_MISCONFIGURED);
        } else {
            throw new AuthenticationServiceException(NO_LDAP_SERVICE_CONFIGURED);
        }
    }

    @Override
    public void onLicenseLoaded() {
        log.trace("Licence loaded - starting initialization");
        init();
        log.trace("Licence loaded - initialization completed");
    }

    public void onLdapSettingChange() {
        log.debug("Ldap Setting changed - initialize authenticators");
        new ArtifactoryRunnable(this::init, applicationContext, AuthenticationHelper.getAuthentication()).run();
    }


    @Override
    public void onContextCreated() {
        try {
            accessService.subscribeToAuthSettingChanges(this::onLdapSettingChange, Realm.LDAP);
        } catch (Exception e) {
            log.debug("try to subscribe to ldap setting change ldap is not up yet");
        }
    }
}
