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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddonsImpl;
import org.artifactory.addon.LdapGroupAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.security.ldap.LdapService;
import org.artifactory.api.security.ldap.LdapUser;
import org.artifactory.api.security.ldap.LdapUserAttributes;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.util.AolUtils;
import org.artifactory.util.StopWatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.SearchScope;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import javax.naming.directory.Attributes;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.ldap.query.LdapQueryBuilder.query;


/**
 * This class tests an ldap connection given a ldap settings.
 *
 * @author Yossi Shaul
 * @author Tomer Cohen
 */
@Service
public class LdapServiceImpl extends AbstractLdapService implements LdapService {
    static final int MAX_CONCURRENT_TESTS_ALLOWED = 5;
    private static final Logger log = LoggerFactory.getLogger(LdapServiceImpl.class);
    private static final String LOCKOUT_DURATION = "lockoutDuration";
    private final Cache<String, String> userToSettings = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS).build();
    private final Semaphore concurrentTestConnectionSemaphore = new Semaphore(MAX_CONCURRENT_TESTS_ALLOWED);

    private final LoadingCache<LdapSetting, Long> accountLockoutDurationCache = CacheBuilder.newBuilder()
            .initialCapacity(5)
            .maximumSize(ConstantValues.activePrincipalTokenCacheMaxSize.getInt())
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build(new LdapSettingLongCacheLoader());

    private final LoadingCache<Pair<String, LdapSetting>, LdapUserAttributes> ldapUserAttributesLoadingCache =
            CacheBuilder.newBuilder()
                    .initialCapacity(ConstantValues.ldapUserAttributesCacheInitCapacity.getInt())
                    .maximumSize(ConstantValues.ldapUserAttributesCacheMaxSize.getInt())
                    .expireAfterWrite(ConstantValues.ldapUserAttributesCacheExpiryMinutes.getInt(), TimeUnit.MINUTES)
                    .build(new LdapUserWithAttributesCacheLoader());


    @Override
    public BasicStatusHolder testLdapConnection(LdapSetting ldapSetting, String username, String password) {
        BasicStatusHolder status = new BasicStatusHolder();
        StopWatch connectionTimeStopWatch = new StopWatch();
        boolean acquiredSemaphore = false;
        try {
            acquiredSemaphore = concurrentTestConnectionSemaphore.tryAcquire();
            if (!acquiredSemaphore) {
                status.error(String.format("Too many concurrent connection tests. Max allowed: %d",
                        MAX_CONCURRENT_TESTS_ALLOWED), log);
                return status;
            }
            boolean aol = AolUtils.isAol();
            LdapContextSource securityContext =
                    ArtifactoryLdapAuthenticator.createSecurityContext(ldapSetting, ArtifactoryHome.get());
            InternalArtifactoryContext context = InternalContextHelper.get();
            AddonsManager addonsManager = context.beanForType(AddonsManager.class);
            ArtifactoryBindAuthenticator authenticator = new ArtifactoryBindAuthenticator(
                    securityContext, ldapSetting, aol, addonsManager.addonByType(LdapGroupAddon.class));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, password);
            StopWatchUtil.measureTime(connectionTimeStopWatch, () -> authenticator.authenticate(authentication));
            status.status("Successfully connected and authenticated the test user", log);
            LdapTemplate ldapTemplate = createLdapTemplate(ldapSetting);
            LdapUser ldapUser = getUserFromLdapSearch(ldapTemplate, username, ldapSetting);
            if (ldapUser == null) {
                status.warn("LDAP user search failed, LDAP queries " +
                        "concerning users and groups may not be available.", log);
            }
        } catch (Exception e) {
            log.debug("Error while testing LDAP authentication: " + e.getMessage(), e);
            SearchPattern pattern = ldapSetting.getSearch();
            long authenticateTimeMillis = connectionTimeStopWatch.getTotalTimeMillis();
            if ((pattern != null && StringUtils.isNotBlank(pattern.getSearchFilter())) &&
                    StringUtils.isNotBlank(ldapSetting.getUserDnPattern())) {
                handleException(e, status, username, true, authenticateTimeMillis);
            } else {
                handleException(e, status, username, false, authenticateTimeMillis);
            }
        } finally {
            if (acquiredSemaphore) {
                concurrentTestConnectionSemaphore.release();
            }
        }
        return status;
    }

    @Override
    public LdapUserAttributes getUserWithAttributesFromDomain(LdapSetting ldapSetting, String userName) {
        try {
            if (!validateLdapSettings(ldapSetting)) {
                log.warn("Cannot find user: '{}', LDAP settings with key: '{}' are not valid", userName,
                        ldapSetting.getKey());
                return null;
            }
            return ldapUserAttributesLoadingCache.get(Pair.of(userName, ldapSetting));
        } catch (ExecutionException e) {
            log.warn(e.getMessage());
            return null;
        }
    }

    private LdapGroupAddon getLdapGroupAddon() {
        InternalArtifactoryContext context = InternalContextHelper.get();
        if (context != null) {
            AddonsManager addonsManager = context.beanForType(AddonsManager.class);
            return addonsManager.addonByType(LdapGroupAddon.class);
        } else {
            return new CoreAddonsImpl();
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public LdapUser getDnFromUserName(LdapSetting ldapSetting, String userName) {
        if (!validateLdapSettings(ldapSetting)) {
            log.warn("Cannot find user: '{}', LDAP settings not valid ", userName);
            return null;
        }
        LdapTemplate ldapTemplate = createLdapTemplate(ldapSetting);
        return getUserFromLdapSearch(ldapTemplate, userName, ldapSetting);
    }

    @Override
    public void addUserLdapSetting(String username, @Nonnull String ldapSetting) {
        userToSettings.put(username, ldapSetting);
    }

    @Override
    public @Nullable
    String getUserLdapSetting(String username) {
        return userToSettings.getIfPresent(username);
    }

    @Override
    public @Nullable
    Long getAccountLockoutDuration(@Nullable LdapSetting ldapSetting) {
        try {
            if (ldapSetting == null) {
                return null;
            }
            return accountLockoutDurationCache.get(ldapSetting);
        } catch (ExecutionException e) {
            log.warn(e.getMessage());
            return null;
        }
    }

    public DirContextOperations searchUserInLdap(LdapTemplate ldapTemplate, String userName, LdapSetting settings) {
        if (settings.getSearch() == null) {
            return null;
        }
        DirContextOperations contextOperations = null;
        try {
            log.debug("Searching for user: '{}'", userName);
            boolean aol = AolUtils.isAol();
            List<LdapUserSearch> ldapUserSearches = getLdapGroupAddon().getLdapUserSearches(
                    ldapTemplate.getContextSource(), settings, aol);
            for (LdapUserSearch ldapUserSearch : ldapUserSearches) {
                try {
                    contextOperations = ldapUserSearch.searchForUser(userName);
                } catch (org.springframework.security.core.AuthenticationException e) {
                    log.debug("Failed to retrieve groups user: '{}' via LDAP: {}", userName, e.getMessage());
                }
                if (contextOperations != null) {
                    break;
                }
            }
            if (contextOperations != null) {
                // Only DirContextAdapter can be used since the LDAP connection need to be released and we still need
                // read access to this LDAP context.
                if (!(contextOperations instanceof DirContextAdapter)) {
                    throw new ClassCastException(
                            "Cannot use LDAP DirContext class " + contextOperations.getClass().getName() +
                                    " it should be " + DirContextAdapter.class.getName());
                }
                log.debug("Found user: '{}', has DN: {}", userName, contextOperations.getNameInNamespace());
            }
        } catch (CommunicationException ce) {
            String message =
                    String.format("Failed to retrieve groups for user: '%s' via LDAP: communication error.", userName);
            log.warn(message);
        } catch (Exception e) {
            String message = "Unexpected exception in LDAP query:";
            log.debug(message, e);
            log.warn("{} for user: '{}' vid LDAP: {}", message, userName, e.getMessage());
        }
        return contextOperations;
    }


    private LdapUser getUserFromLdapSearch(LdapTemplate ldapTemplate, String userName, LdapSetting settings) {
        DirContextOperations contextOperations = searchUserInLdap(ldapTemplate, userName, settings);
        if (contextOperations == null) {
            return null;
        }
        return new LdapUser(userName, contextOperations.getNameInNamespace());
    }

    private boolean validateLdapSettings(LdapSetting ldapSetting) {
        if (ldapSetting == null) {
            log.warn("No LDAP settings defined.");
            return false;
        }
        if (!ldapSetting.isEnabled()) {
            log.warn("LDAP settings not enabled.");
            return false;
        }
        if (ldapSetting.getSearch() == null || isBlank(ldapSetting.getSearch().getSearchFilter())) {
            log.warn("No search filter defined.");
            return false;
        }
        return true;
    }

    Long loadLockoutDurationAttributeValue(LdapSetting setting) {
        LdapTemplate ldapTemplate = createLdapTemplateForDomain(setting);
        LdapQuery query = query()
                .attributes(LOCKOUT_DURATION)
                .searchScope(SearchScope.OBJECT)
                .where("objectclass")
                .is("domain");
        List<Long> values = ldapTemplate.search(query, this::getLockoutDurationAttributeValue);
        Long accountLockoutDuration = values.isEmpty() ? null : values.get(0);
        return accountLockoutDuration;
    }

    private Long getLockoutDurationAttributeValue(Attributes attrs) {
        try {
            String lockoutDuration = (String) (attrs.get(LOCKOUT_DURATION).get(0));
            return Long.valueOf(lockoutDuration);
        } catch (Exception e) {
            return null;
        }
    }

    private class LdapSettingLongCacheLoader extends CacheLoader<LdapSetting, Long> {

        @Override
        public Long load(@Nonnull LdapSetting setting) throws ExecutionException {
            Long accountLockoutDuration = loadLockoutDurationAttributeValue(setting);
            if (accountLockoutDuration == null) {
                throw new ExecutionException(
                        String.format(
                                "Cant load account lockout duration settings for %s", setting.getKey()), null);
            }
            return accountLockoutDuration;
        }

    }

    private class LdapUserWithAttributesCacheLoader extends CacheLoader<Pair<String, LdapSetting>, LdapUserAttributes> {
        @Override
        public LdapUserAttributes load(@Nonnull Pair<String, LdapSetting> usernameAndLdapSettingsPair)
                throws ExecutionException {
            String username = usernameAndLdapSettingsPair.getKey();
            LdapSetting ldapSetting = usernameAndLdapSettingsPair.getValue();
            log.debug("Initiating load of LDAP user: '{}' attributes cache with ldap settings key: '{}'",
                    username, ldapSetting);
            LdapUserAttributes userWithAttributes = getUserWithAttributesFromDomain(ldapSetting, username);

            if (userWithAttributes == null) {
                throw new ExecutionException(String.format(
                        "Can't load user with attributes for username: '%s' with LDAP settings key: '%s'",
                        username, ldapSetting), null);
            }
            log.debug("Finished load of LDAP user: '{}' arrtibutes cache", username);
            return userWithAttributes;
        }

        private LdapUserAttributes getUserWithAttributesFromDomain(LdapSetting ldapSetting, String userName) {
            LdapTemplate ldapTemplate = createLdapTemplate(ldapSetting);
            DirContextOperations dirContextOperations = searchUserInLdap(ldapTemplate, userName, ldapSetting);
            if (dirContextOperations == null) {
                log.info("Couldn't find user: '{}' in {}", userName, ldapSetting.getKey());
                return null;
            }
            LdapUser ldapUser = new LdapUser(userName, dirContextOperations.getNameInNamespace());
            Attributes attributes = dirContextOperations.getAttributes();
            if (attributes == null) {
                log.info(
                        "Cannot extract user attributes for user: '{}'," +
                                " some attributes will not be imported to the user profile", userName);
            }
            return new LdapUserAttributes(ldapUser, attributes);
        }
    }
}
