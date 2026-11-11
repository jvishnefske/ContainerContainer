/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.security.db.apikey;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.ldap.LdapService;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.BadPropsAuthException;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.Assert;

import java.util.Set;

import static org.artifactory.security.access.AccessUserPassAuthenticationProvider.ACCESS_REALM;
import static org.artifactory.security.ldap.LdapUtils.refreshUserFromLdap;
import static org.artifactory.security.props.auth.ApiKeyManager.API_KEY;
import static org.jfrog.access.model.Realm.LDAP;

/**
 * @author Chen Keinan
 */
public class PropsAuthenticationProvider implements RealmAwareAuthenticationProvider {
    private static final Logger log = LoggerFactory.getLogger(PropsAuthenticationProvider.class);

    private UserGroupStoreService userGroupStore;
    private UserGroupService userGroupService;
    private LdapService ldapService;
    private AccessService accessService;
    private AddonsManager addonsManager;
    private SecurityService securityService;

    @Autowired
    public PropsAuthenticationProvider(UserGroupStoreService userGroupStore,
            UserGroupService userGroupService,
            LdapService ldapService, AccessService accessService, AddonsManager addonsManager,
            SecurityService securityService) {
        this.userGroupStore = userGroupStore;
        this.userGroupService = userGroupService;
        this.ldapService = ldapService;
        this.addonsManager = addonsManager;
        this.securityService = securityService;
        this.accessService = accessService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Assert.isInstanceOf(PropsAuthenticationToken.class,
                authentication, "Only Props Authentication Token is supported");
        PropsAuthenticationToken authToken = (PropsAuthenticationToken) authentication;
        // Determine props key and value
        String tokenKey = (String) authToken.getPropsKey();
        String tokenValue = (String) authentication.getCredentials();
        TokenKeyValue tokenKeyValue = new TokenKeyValue(tokenKey, tokenValue);
        UserDetails principal = findPrincipalByToken(tokenKeyValue);
        if (principal == null) {
            log.debug("token not found");
            throw new BadPropsAuthException("Props Authentication Token not found");
        } else {
            log.debug("Trying to authenticate user: '{}' via {}.", principal.getUsername(), tokenKey);
            if (authToken.getPrincipal() != null && StringUtils.isNotBlank(authToken.getPrincipal().toString()) &&
                    !authToken.getPrincipal().toString().equalsIgnoreCase(principal.getUsername())) {
                log.debug("Bad authentication key.");
                throw new BadPropsAuthException("Bad authentication Key");
            }
        }
        if (ConstantValues.ldapApplyHeadlessRealmValidation.getBoolean()) {
            principal = retrieveLdapUserDetailsInCaseOfApiKey(tokenKey, principal);
        }
        securityService.ensureUserIsNotLocked(principal.getUsername());
        securityService.ensureUserIsNotDisabled(principal.getUsername());
        return createSuccessAuthentication(authToken, principal);
    }

    /**
     * Triggers an LDAP sync for {@param principal} in case:
     *  - LDAP realm on Access is turned off (otherwise this is done on Access side)
     *  - The request is of API KEY
     *  - The user's realm is LDAP
     *
     * @return the updated user model after retrieving from LDAP server (or the original one in case not LDAP realm)
     * @throws AuthenticationException in case user was not found in LDAP
     */
    UserDetails retrieveLdapUserDetailsInCaseOfApiKey(String tokenKey, UserDetails principal) {
        if (accessService.isRealmAuthenticationEnabled(LDAP)) {
            return principal;
        }

        String username = principal.getUsername();
        String realm = getRealm(principal);
        if (!API_KEY.equals(tokenKey) || !LDAP.getName().equals(realm)) {
            log.debug("The user: '{}' request is of type {} for realm {}, won't trigger LDAP sync", username, tokenKey,
                    realm);
            return principal;
        }
        return refreshUserFromLdap(username, accessService, ldapService, addonsManager, userGroupService);
    }

    private String getRealm(UserDetails principal) {
        if (principal instanceof SimpleUser) {
            return ((SimpleUser) principal).getDescriptor().getRealm();
        }
        return ACCESS_REALM;
    }

    private UserDetails findPrincipalByToken(TokenKeyValue tokenKeyValue) {
        //Matches exact token value, so we're safe - any user returned has this token for sure
        //(but perhaps from a different PropsTokenManager - which is how this flaky feature was built in the first
        //place)
        UserInfo user = userGroupStore.findUserByProperty(tokenKeyValue.getKey(), tokenKeyValue.getToken(), false);
        if (user != null) {
            return new SimpleUser(user);
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return !accessService.shouldDelegateTokenOrApiKeyAuthenticationToAccess();
    }

    protected Authentication createSuccessAuthentication(PropsAuthenticationToken authentication,
            UserDetails user) {
        PropsAuthenticationToken result = new PropsAuthenticationToken(user, authentication.getPropsKey(),
                authentication.getCredentials(), new NullAuthoritiesMapper().mapAuthorities(user.getAuthorities()));

        result.setDetails(authentication.getDetails());
        result.setAuthenticated(true);
        return result;
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        // not require
    }

    @Override
    public boolean userExists(String username) {
        return userGroupStore.userExists(username);
    }

    @Override
    public String getRealm() {
        return null;
    }
}