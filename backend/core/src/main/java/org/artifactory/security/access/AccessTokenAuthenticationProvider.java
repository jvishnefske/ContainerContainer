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

package org.artifactory.security.access;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.api.security.ldap.LdapService;
import org.artifactory.common.ConstantValues;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.ServiceType;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.artifactory.security.access.AccessUserPassAuthenticationProvider.ACCESS_REALM;
import static org.artifactory.security.ldap.LdapUtils.refreshUserFromLdap;
import static org.jfrog.access.model.Realm.LDAP;
import static org.jfrog.access.token.TokenType.isFrontendImpersonationToken;

/**
 * @author Yinon Avraham
 */
@Component
public class AccessTokenAuthenticationProvider implements RealmAwareAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenAuthenticationProvider.class);
    private static final String TOKEN_PREFIX = "token:";
    /**
     * Max length of a username. This is the limitation due to the username holding columns in the database
     * (e.g. 'created_by', 'last_downloaded_by', etc.)
     */
    private static final int MAX_USERNAME_LENGTH = 64;

    AccessService accessService;
    private UserGroupStoreService userGroupStore;
    private UserGroupService userGroupService;
    private LdapService ldapService;
    private AddonsManager addonsManager;

    @Override
    public Authentication authenticate(Authentication authentication) {
        Assert.isInstanceOf(AccessTokenAuthentication.class, authentication,
                "Only instances of AccessTokenAuthentication are expected");
        AccessTokenAuthentication accessTokenAuth = (AccessTokenAuthentication) authentication;
        String accessTokenValue = accessTokenAuth.getAccessTokenValue();
        JwtAccessToken accessToken = verifyToken(accessTokenValue);
        //TODO [YA] Currently, we match the token to a real user. Once we rely on the scope the token provides, the
        //user principal is unnecessary.
        UserDetails principal = findPrincipalByToken(accessToken);
        verifyMatchingPrincipal(accessTokenAuth, accessToken);
        return newSuccessfulAccessTokenAuthentication(accessToken, principal, accessTokenAuth.getDetails(),
                accessTokenAuth.getAuthorities());
    }

    /**
     * Triggers a LDAP sync for {@param principal} in case of LDAP access token.
     *
     * @return the updated user model after retrieving from LDAP server (or the original one in case of apply
     * headless validation is set to 'false' or not LDAP realm)
     * @throws AuthenticationException in case user was not found in LDAP
     */
    private UserDetails refreshLdapUserIfNeeded(UserDetails principal) {
        if (accessService.isRealmAuthenticationEnabled(LDAP)) {
            return principal;
        }

        String username = principal.getUsername().replace(TOKEN_PREFIX, "");
        String realm = getRealm(principal);
        if (!LDAP.getName().equals(realm)) {
            log.debug("The user: '{}' request for realm {}, won't trigger LDAP sync", username, realm);
            return principal;
        }
        if (!ConstantValues.ldapApplyHeadlessRealmValidation.getBoolean()) {
            log.debug("Headless validation is off, skipping LDAP sync for user: '{}'", username);
            return principal;
        }
        log.debug("Triggering LDAP sync for user {}", username);
        return refreshUserFromLdap(username, accessService, ldapService, addonsManager, userGroupService);
    }

    private Authentication newSuccessfulAccessTokenAuthentication(JwtAccessToken accessToken,
            UserDetails principal, Object details, Collection<GrantedAuthority> authorities) {
        AccessTokenAuthentication accessTokenAuth = new AccessTokenAuthentication(accessToken,
                principal, authorities);
        accessTokenAuth.setAuthenticated(true);
        accessTokenAuth.setDetails(details);
        return accessTokenAuth;
    }

    public boolean isAccessToken(@Nonnull String value) {
        return accessService.getAccessTokenType(value) != AccessTokenType.NOT_VALID_TOKEN;
    }

    private JwtAccessToken verifyToken(String accessToken) {
        TokenVerifyResult result;
        try {
            result = accessService.verifyAndGetResult(accessToken);
        } catch (Exception e) {
            log.error("Failed to verify token: {}", e.toString());
            log.debug("Failed to verify token", e);
            throw new AccessTokenAuthenticationException("Failed to verify token.", e);
        }
        if (!result.isSuccessful()) {
            throw new AccessTokenAuthenticationException("Token failed verification: " + result.getReason());
        }

        return result.getAccessToken();
    }

    @Nonnull
    private UserDetails findPrincipalByToken(JwtAccessToken accessToken) {
        String username = extractUsername(accessToken);
        UserInfo realUser = userGroupService.findUser(username, false);
        String principalName = getPrincipalName(accessToken);
        MutableUserInfo user = new UserInfoBuilder(principalName).transientUser().build();
        if (realUser == null && log.isDebugEnabled()) {
            log.debug("The user: '{}' not found by username, extracted from access token subject: {}",
                    username, accessToken.getSubject());
        }
        user.setRealm(realUser != null ? realUser.getRealm() : null);
        if (shouldSkipRefreshGroupsFromLdap(accessToken)) {
            user = populateGroups(user, realUser, accessToken);
            user = setAdminPrivileges(user, realUser, accessToken);
            return new SimpleUser(user);
        }
        if (realUser != null && isIdentityToken(accessToken.getScope())) {
            user = InfoFactoryHolder.get().copyUser(realUser);
        }
        UserDetails userDetailsFreshFromLdap = refreshLdapUserIfNeeded(new SimpleUser(user));
        UserInfo userInfoFreshFromLdap = ((SimpleUser) userDetailsFreshFromLdap).getDescriptor();
        user = populateGroups(user, userInfoFreshFromLdap, accessToken);
        user = setAdminPrivileges(user, userInfoFreshFromLdap, accessToken);
        user.setIsAppliedPermissionsGroups(isAppliedGroupPermissionToken(accessToken.getScope()));
        user.setIsIdentity(isIdentityToken(accessToken.getScope()));
        return new SimpleUser(user);
    }

    MutableUserInfo setAdminPrivileges(MutableUserInfo user, @Nullable UserInfo realUser, JwtAccessToken accessToken) {
        ServiceId artifactoryServiceId = accessService.getArtifactoryServiceId();
        if (accessToken.getScope().stream()
                .filter(ArtifactoryAdminScopeToken::accepts)
                .anyMatch(scope -> ArtifactoryAdminScopeToken.isAdminScopeOnService(scope, artifactoryServiceId))) {
            user.setAdmin(true);
        }
        if (realUser != null && realUser.isAdmin() && isIdentityToken(accessToken.getScope())) {
            user.setAdmin(true);
        }
        return user;
    }

    /**
     * Update the user with groups according to the real user (if exists, otherwise <code>null</code>) and the
     * token. The user will have only the groups that came from the token.
     * In case the token has only "*" in the groups, we use all the groups assigned to the real user (if exists).
     */
    MutableUserInfo populateGroups(MutableUserInfo user, UserInfo realUser, JwtAccessToken accessToken) {
        if (realUser != null && isIdentityToken(accessToken.getScope())) {
            // Identity token (applied-permissions/user or member-of-groups:*) - use the user from the storage
            user = InfoFactoryHolder.get().copyUser(realUser);
        }
        // The token may still point to group membership that isn't persisted (i.e SAML login) - add them

        Set<String> groups = new HashSet<>(collectGroupNamesFromScope(accessToken.getScope()));
        Set<String> existingGroups = ConstantValues.loginFetchGroupsNyNames.getBoolean() ?
                getGroupsByNames(groups) : getAllGroups();
        groups.retainAll(existingGroups);
        groups.forEach(user::addGroup);
        return user;
    }

    private Set<String> getAllGroups() {
        return userGroupService.getAllGroups().stream()
                .map(GroupInfo::getGroupName)
                .collect(Collectors.toSet());
    }

    private Set<String> getGroupsByNames(Set<String> groups) {
        return userGroupService.getAllGroupsByGroupNames(groups.stream().toList()).keySet();
    }

    public static Set<String> collectGroupNamesFromScope(List<String> scope) {
        if (CollectionUtils.isEmpty(scope)) {
            return Collections.emptySet();
        }
        Set<String> groups = new HashSet<>();
        if (scope.stream().anyMatch(AppliedPermissionsScopeToken::accepts)) {
            groups.addAll(appliedPermissionsGroupsToGroupsSet(scope));
        }
        if (scope.stream().anyMatch(MemberOfGroupsScopeToken::accepts)) {
            groups.addAll(memberOfGroupsTokenToGroupsSet(scope));
        }
        return groups;
    }

    private boolean shouldSkipRefreshGroupsFromLdap(JwtAccessToken token) {
        long maxTimeAllowedToSkipRefreshGroupsFromLdap = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(
                ConstantValues.tokenExpiryLimitToSkipRefreshGroupsSecs.getInt());
        return isFrontendImpersonationToken(token.getIssuer(), token.getScope()) &&
                (token.getExpirationTime() != null &&
                        (token.getExpirationTime() < maxTimeAllowedToSkipRefreshGroupsFromLdap));
    }

    private static Set<String> memberOfGroupsTokenToGroupsSet(List<String> scope) {
        return scope.stream()
                .filter(MemberOfGroupsScopeToken::accepts)
                .flatMap(scopeToken -> MemberOfGroupsScopeToken.parse(scopeToken).getGroupNames().stream())
                .collect(Collectors.toSet());
    }

    private static Set<String> appliedPermissionsGroupsToGroupsSet(List<String> scope) {
        return scope.stream()
                .filter(AppliedPermissionsScopeToken::accepts)
                .flatMap(scopeToken -> AppliedPermissionsScopeToken.parse(scopeToken).getGroupNames().stream())
                .collect(Collectors.toSet());
    }

    public static boolean isIdentityToken(List<String> tokenScopes) {
        Set<String> groups = new HashSet<>(collectGroupNamesFromScope(tokenScopes));
        return (groups.size() == 1 && groups.contains("*")) ||
                (AppliedPermissionsScopeToken.isIdentityToken(tokenScopes));
    }

    private boolean isAppliedGroupPermissionToken(List<String> tokenScopes) {
        return !collectGroupNamesFromScope(tokenScopes).isEmpty();
    }

    private void verifyMatchingPrincipal(AccessTokenAuthentication accessTokenAuth, JwtAccessToken accessToken) {
        if (accessTokenAuth.getPrincipal() != null) {
            String principalAsString = accessTokenAuth.getPrincipal().toString();
            String subject = accessToken.getSubject();
            String username = UserTokenSpec.isUserTokenSubject(subject) ?
                    UserTokenSpec.extractUsername(subject) :
                    subject;
            if (!StringUtils.equalsIgnoreCase(principalAsString, username)) {
                log.warn("Token principle mismatch.");
                log.debug("Token principal mismatch. (existing='{}', expected='{}')", principalAsString, username);
                throw new AccessTokenAuthenticationException("Token principal mismatch.");
            }
        }
    }

    @Nonnull
    private String extractUsername(@Nonnull JwtAccessToken accessToken) {
        String username = accessService.extractSubjectUsername(accessToken);
        if (username != null) {
            return username;
        }
        return accessToken.getSubject();
    }

    @Nonnull
    private String getPrincipalName(JwtAccessToken accessToken) {
        String extractedUsername = extractUsername(accessToken);
        String username = extractedUsername;
        int lastSlashIndex = extractedUsername.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            username = extractedUsername.substring(lastSlashIndex + 1);
        }
        // Only prepend "token:" to non-UI issued tokens - the UI uses tokens for login sessions
        String principalName =
                accessToken.getIssuer().startsWith(ServiceType.FRONTEND) ? username : TOKEN_PREFIX + username;
        if (principalName.length() > MAX_USERNAME_LENGTH) {
            String trimmedUsername = principalName.substring(0, MAX_USERNAME_LENGTH);
            log.debug("Principal username extracted from access token subject is too long and got trimmed. " +
                    "subject = '{}', principalName = '{}'", accessToken.getSubject(), principalName);
            principalName = trimmedUsername;
        }
        return principalName;
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        //Not relevant
    }

    @Override
    public boolean userExists(String username) {
        return userGroupStore.userExists(username);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return !accessService.shouldDelegateTokenOrApiKeyAuthenticationToAccess();
    }

    private String getRealm(UserDetails principal) {
        if (principal instanceof SimpleUser) {
            return ((SimpleUser) principal).getDescriptor().getRealm();
        }
        return ACCESS_REALM;
    }

    @Autowired
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    public void setUserGroupStore(UserGroupStoreService userGroupStore) {
        this.userGroupStore = userGroupStore;
    }

    @Autowired
    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @Autowired
    public void setLdapService(LdapService ldapService) {
        this.ldapService = ldapService;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    public boolean isReferenceToken(String token) {
        return accessService.getAccessTokenType(token) == AccessTokenType.REFERENCE_TOKEN;
    }
}
