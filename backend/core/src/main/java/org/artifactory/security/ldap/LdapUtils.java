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
package org.artifactory.security.ldap;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LdapGroupAddon;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.ldap.LdapService;
import org.artifactory.api.security.ldap.LdapSettingAndUser;
import org.artifactory.api.security.ldap.LdapUserAttributes;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ldap utility code (moved from {@link ArtifactoryLdapAuthenticationProvider} for usability)
 *
 * @author Nadav Yogev
 * @author Yuval Reches
 */
@UtilityClass
public class LdapUtils {
    private static final Logger log = LoggerFactory.getLogger(LdapUtils.class);
    private static final Set<Integer> DISABLED_ACCOUNT_FLAGS =
            Set.of(66050, 66082, 514, 546, 262658, 262690, 328194, 328226);
    private static final long DIFF_NET_JAVA_FOR_DATES = 11644473600000L; // 100ns intervals since Jan 1, 1601 UTC
    private static final long ONE_HUNDRED_NS_TO_MS_FACTOR = 10000L;

    /**
     * Performs a query to LDAP to search for the {@param username} and get its groups.
     * User is updated locally (saved to db)
     *
     * @return A {@link UserDetails} instance with updated attributes from LDAP server
     * @throws AuthenticationException in case user was not found
     */
    public static SimpleUser refreshUserFromLdap(String username,
            AccessService accessService, LdapService ldapService, AddonsManager addonsManager,
            UserGroupService userGroupService) {
        LdapSettingAndUser settingAndUser = findSettingAndUser(username, accessService, ldapService);
        if (settingAndUser == null) {
            if (ConstantValues.ldapCleanGroupOnFail.getBoolean()) {
                try {
                    removeUserLdapRelatedGroups(userGroupService.findUser(username), userGroupService);
                } catch (UsernameNotFoundException e) {
                    log.trace("LDAP user: '{}' not found internally, skipping synced groups removal", username, e);
                    log.debug("Can't reauthenticate LDAP for user: '{}': user is not found in LDAP", username);
                    throw e;
                }
            }
            String message = "Can't reauthenticate LDAP for user: '" + username +
                    "': user is locked, disabled or does not exist in LDAP";
            log.debug(message);
            throw new InternalAuthenticationServiceException(message);
        }
        LdapGroupAddon ldapGroupAddon = addonsManager.addonByType(LdapGroupAddon.class);
        return createSimpleUser(username, settingAndUser.getSetting(), settingAndUser.getLdapUser(),
                ldapGroupAddon, userGroupService);
    }

    /**
     * remove user ldap related group as user no longer exist in ldap
     *
     * @param userInfo Artifactory User Data
     */
    static void removeUserLdapRelatedGroups(UserInfo userInfo,
            UserGroupService userGroupService) {
        MutableUserInfo mutableUserInfo = InfoFactoryHolder.get().copyUser(userInfo);
        Set<UserGroupInfo> updateUserGroup = new HashSet<>();
        Set<UserGroupInfo> userGroupInfos = new HashSet<>(mutableUserInfo.getGroups());
        for (UserGroupInfo userGroupInfo : userGroupInfos) {
            if (!LdapService.REALM.equals(userGroupInfo.getRealm())) {
                updateUserGroup.add(userGroupInfo);
            }
        }
        mutableUserInfo.setGroups(updateUserGroup);
        if (!userInfo.isTransientUser() && userGroupInfos.size() != updateUserGroup.size()) {
            log.warn("Updating user: '{}' after LDAP login authentication failure", mutableUserInfo.getUsername());
            log.debug("Updating user: '{}' after LDAP login authentication failure, user groups for update are {}",
                    mutableUserInfo.getUsername(), mutableUserInfo.getGroups());
            userGroupService.updateUser(mutableUserInfo, false);
        }
    }

    /**
     * Find a user in LDAP if (1)not locked, (2)not disabled, (3)exist in LDAP, (4)LDAP has been configured.
     * returns a pair of the (1)settings the user was found in, (2) user attributes.
     */
    @Nullable
    static LdapSettingAndUser findSettingAndUser(@NonNull String username,
            @NonNull AccessService accessService, @NonNull LdapService ldapService) {
        List<LdapSetting> settings = accessService.getLdapSettings();
        if (settings == null || settings.isEmpty()) {
            log.debug("No LDAP settings defined");
            return null;
        }
        for (LdapSetting setting : settings) {
            if (!setting.isEnabled()) {
                continue;
            }
            log.debug("Trying to find user: '{}' with LDAP settings '{}'", username, setting);
            LdapUserAttributes ldapUser = ldapService.getUserWithAttributesFromDomain(setting, username);
            if (ldapUser == null) {
                continue;
            }
            log.debug("Found user: '{}' with LDAP settings '{}'", username, setting);
            Attribute pwdAccountLockedTime = ldapUser.getAttributes().get("pwdAccountLockedTime");
            Attribute lockoutTime = ldapUser.getAttributes().get("lockoutTime");
            Attribute userAccountControlAttr = ldapUser.getAttributes().get("userAccountControl");
            if (pwdAccountLockedTime == null // no LDAP account locked
                    && !isActiveDirectoryAccountDisabled(userAccountControlAttr) // AD account disabled
                    && !isActiveDirectoryLockout(lockoutTime, setting, ldapService)) { // AD account locked
                return new LdapSettingAndUser(setting, ldapUser);
            }
        }
        return null;
    }

    /**
     * @param userAccountControlAttr - User account control attribute
     * @return True if the UserAccountControl attribute contains a value of disabled account
     */
    private static boolean isActiveDirectoryAccountDisabled(@Nullable Attribute userAccountControlAttr) {
        if (userAccountControlAttr == null) {
            return false;
        }
        try {
            int accountControl = Integer.parseUnsignedInt((String) userAccountControlAttr.get(0));
            return DISABLED_ACCOUNT_FLAGS.contains(accountControl);
        } catch (NamingException | NumberFormatException e) {
            log.error(e.getMessage());
        }
        return false;
    }

    /**
     * @param lockoutTime attribute
     * @return true if an Active Directory user has an active lockout time
     */
    private static boolean isActiveDirectoryLockout(@Nullable Attribute lockoutTime, @Nullable LdapSetting setting,
            @NonNull LdapService ldapService) {
        try {
            if (lockoutTime == null || lockoutTime.size() == 0 || "0".equals(lockoutTime.get(0))) {
                return false;
            }
            Long lockoutDuration = ldapService.getAccountLockoutDuration(setting);
            if (lockoutDuration == null) {
                return false;
            }
            lockoutDuration = (lockoutDuration / ONE_HUNDRED_NS_TO_MS_FACTOR);
            long lockout = Long.parseLong((String) lockoutTime.get(0));
            lockout = (lockout / ONE_HUNDRED_NS_TO_MS_FACTOR) - DIFF_NET_JAVA_FOR_DATES;
            return System.currentTimeMillis() + lockoutDuration - lockout < 0;
        } catch (NamingException | NumberFormatException e) {
            log.error(e.getMessage());
        }
        return false;
    }

    static SimpleUser createSimpleUser(String userName, LdapSetting usedLdapSetting, LdapUserAttributes user,
            LdapGroupAddon ldapGroupAddon, UserGroupService userGroupService) {
        MutableUserInfo userInfo = InfoFactoryHolder.get()
                .copyUser(userGroupService
                        .findOrCreateExternalAuthUser(userName, !usedLdapSetting.isAutoCreateUser(),
                                usedLdapSetting.isAllowUserToAccessProfile()));
        userInfo.setRealm(LdapService.REALM);
        String emailAttribute = usedLdapSetting.getEmailAttribute();
        if (StringUtils.isNotBlank(emailAttribute)) {
            Attribute userEmailAttribute = user.getAttributes().get(emailAttribute);
            if (userEmailAttribute != null) {
                try {
                    String email = userEmailAttribute.get().toString();
                    if (StringUtils.isNotBlank(email) && !userInfo.isTransientUser()) {
                        log.debug("The user: '{}' has email address '{}'", userName, email);
                        if (!email.equals(userInfo.getEmail())) {
                            userInfo.setEmail(email);
                            userGroupService.updateUser(userInfo, false);
                        }
                    }
                } catch (NamingException | NullPointerException e) {
                    log.warn("Failed to extract user: '{}' email, due to: {}", user.getLdapUser(), e.getMessage());
                    log.debug("Failed to extract user: '{}' email, due to: {}", user.getLdapUser(), e.getMessage(), e);
                }
            }
        }
        log.debug("Loading LDAP groups");
        ldapGroupAddon.populateGroups(user.getLdapUser().getDn(), userInfo);
        log.debug("Finished Loading LDAP groups");
        return new SimpleUser(userInfo);
    }
}