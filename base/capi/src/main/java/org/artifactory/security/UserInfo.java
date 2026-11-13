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

package org.artifactory.security;

import org.artifactory.common.Info;

import java.util.Optional;
import java.util.Set;

/**
 * @author Fred Simon
 */
public interface UserInfo extends Info {
    String ANONYMOUS = "anonymous";
    String SIGNED_ANONYMOUS = "signed:anonymous";
    String TOKEN_DELIMITER = ":";

    static boolean isAnonymous(String userName) {
        if (userName == null) {
            return false;
        } else {
            userName = userName.toLowerCase();
            int delim = userName.indexOf(TOKEN_DELIMITER);
            if (delim != -1) {
                userName = userName.substring(delim + 1);
            }
        }
        return userName.length() == UserInfo.ANONYMOUS.length() &&
                UserInfo.ANONYMOUS.equals(userName);
    }
    String getUsername();

    String getPassword();

    String getSalt();

    String getEmail();

    String getPrivateKey();

    String getPublicKey();

    String getGenPasswordKey();

    /**
     * @return True in case user is either admin OR part of a group admin.
     */
    boolean isEffectiveAdmin();

    /**
     * USE WITH CAUTION, not recommended to use directly.
     * This method only reflects the admin variable, however, user can be admin via a group admin.
     * Use {@link #isEffectiveAdmin()} instead.
     *
     * @return the value set with admin variable
     */
    boolean isAdmin();

    boolean isPolicyViewer();

    boolean isPolicyManager();

    boolean isWatchManager();

    boolean isReportsManager();

    boolean isGroupAdmin();

    Boolean isGroupAdminVerbatim();

    boolean isEnabled();

    boolean isUpdatableProfile();

    /**
     * @deprecated This field is never used by Artifactory. Account can be locked with
     * {@link UserInfo#getStatus() == UserStatus.LOCKED} ()}
     * Also, not supported by access and has no use in artifactory ui
     */
    @Deprecated
    boolean isAccountNonExpired();

    /**
     * @deprecated Not supported by access and has no use in artifactory ui
     */
    @Deprecated
    boolean isAccountNonLocked();

    boolean isTransientUser();

    String getRealm();

    boolean isExternal();

    /**
     * Indicates whether the user's credentials (password) has expired. Expired credentials prevent
     * authentication.
     *
     * @return <code>false</code> if the user's credentials are valid (ie non-expired), <code>true</code> if no longer
     * valid (ie expired)
     */
    boolean isCredentialsExpired();

    /**
     * @deprecated Not supported by access and has no use in artifactory ui
     */
    @Deprecated
    boolean isCredentialsNonExpired();

    boolean isAnonymous();

    boolean isInGroup(String groupName);

    Set<UserGroupInfo> getGroups();

    Set<UserGroupInfo> getGroupsReference();

    Set<UserPropertyInfo> getUserProperties();

    /**
     * @param key The property key
     * @return Optional property value
     */
    Optional<String> getUserProperty(String key);

    long getLastLoginTimeMillis();

    String getLastLoginClientIp();

    @Deprecated
    long getLastAccessTimeMillis();

    @Deprecated
    String getLastAccessClientIp();

    boolean hasSameAuthorizationContext(UserInfo o);

    boolean isLocked();

    boolean isPasswordDisabled();

    UserMFAStatus getMfaStatus();

    ArtifactoryUserStatus getStatus();

    boolean shouldInvite();

    String getSource();

    boolean isAppliedPermissionsGroups();

    boolean isIdentity();

    boolean isDisabledUiAccess();

    boolean isEmailVerified();

    /**
     * Indicates whether the holder must choose a new password before doing anything else. The flag is set on an
     * account whose current password was not chosen by its holder, for example one set or reset by another
     * administrator. While it is set the account may only call the change-password and logout endpoints, and no
     * token or API key can be created for it.
     *
     * @return <code>true</code> if the account must change its password before it can be used, <code>false</code>
     * otherwise (the default)
     */
    boolean isMustChangePassword();
}
