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

package org.artifactory.api.security;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.util.Builder;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.ArtifactoryUserStatus;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserMFAStatus;
import org.artifactory.security.UserPropertyInfo;

import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Builder for user info with sensible defaults.
 *
 * @author Yossi Shaul
 */
public class UserInfoBuilder implements Builder<MutableUserInfo> {

    private final String username;

    // TODO [NS] Remove SaltedPassword object, all references should send raw password as the first parameter
    //  and ignore salt
    private SaltedPassword password = new SaltedPassword(null, null);
    private String email = "";
    private boolean admin = false;
    private boolean policyViewer = false;
    private boolean policyManager = false;
    private boolean watchManager = false;
    private boolean reportsManager = false;
    private Boolean groupAdmin = false;
    private boolean enabled = true;
    private boolean updatableProfile = false;
    private boolean transientUser = false;
    private Set<UserGroupInfo> groups = new HashSet<>();
    private String bintrayAuth;
    private Set<UserPropertyInfo> userPropertyInfos;
    private String privateKey;
    private String publicKey;
    private boolean credentialsExpired;
    private long lastLoginTime;
    private String lastLoginIp;
    private String genPasswordKey;
    private String realm;
    private boolean passwordDisabled;
    private UserMFAStatus mfaStatus = UserMFAStatus.NONE;
    private ArtifactoryUserStatus status = ArtifactoryUserStatus.ENABLED;
    private boolean shouldInvite = false;
    private String source;
    private boolean appliedPermissionsGroups;
    private boolean identity;
    private boolean disabledUiAccess;
    private boolean emailVerified;
    private boolean mustChangePassword = false;


    public UserInfoBuilder(String username) {
        this.username = username;
    }

    /**
     * @return The user.
     */
    @Override
    public MutableUserInfo build() {
        if (StringUtils.isBlank(username)) {
            throw new IllegalStateException("User must have a username");
        }

        MutableUserInfo user = InfoFactoryHolder.get().createUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setAdmin(admin);
        user.setPolicyViewer(policyViewer);
        user.setPolicyManager(policyManager);
        user.setWatchManager(watchManager);
        user.setReportsManager(reportsManager);
        user.setEnabled(enabled);
        user.setUpdatableProfile(updatableProfile);
        user.setCredentialsExpired(credentialsExpired);
        user.setCredentialsNonExpired(!credentialsExpired);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setTransientUser(transientUser);
        user.setGroups(groups);
        user.setUserProperties(userPropertyInfos);
        user.setGroupAdmin(groupAdmin);
        user.setPrivateKey(privateKey);
        user.setPublicKey(publicKey);
        user.setLastLoginTimeMillis(lastLoginTime);
        user.setLastLoginClientIp(lastLoginIp);
        user.setGenPasswordKey(genPasswordKey);
        user.setRealm(realm);
        user.setPasswordDisabled(passwordDisabled);
        user.setMfaStatus(mfaStatus);
        user.setStatus(status);
        user.setShouldInvite(shouldInvite);
        user.setSource(source);
        user.setIsAppliedPermissionsGroups(appliedPermissionsGroups);
        user.setIsIdentity(identity);
        user.setIsDisabledUiAccess(disabledUiAccess);
        user.setIsEmailVerified(emailVerified);
        user.setMustChangePassword(mustChangePassword);
        return user;
    }

    public UserInfoBuilder email(String email) {
        this.email = email;
        return this;
    }

    public UserInfoBuilder password(SaltedPassword saltedPassword) {
        this.password = saltedPassword;
        return this;
    }

    public UserInfoBuilder admin(boolean admin) {
        this.admin = admin;
        return this;
    }

    public UserInfoBuilder policyViewer(boolean policyViewer) {
        this.policyViewer = policyViewer;
        return this;
    }

    public UserInfoBuilder policyManager(boolean policyManager) {
        this.policyManager = policyManager;
        return this;
    }

    public UserInfoBuilder watchManager(boolean watchManager) {
        this.watchManager = watchManager;
        return this;
    }

    public UserInfoBuilder reportsManager(boolean reportsManager) {
        this.reportsManager = reportsManager;
        return this;
    }

    public UserInfoBuilder groupAdmin(Boolean groupAdmin) {
        this.groupAdmin = groupAdmin;
        return this;
    }

    public UserInfoBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public UserInfoBuilder updatableProfile(boolean updatableProfile) {
        this.updatableProfile = updatableProfile;
        return this;
    }

    public UserInfoBuilder credentialsExpired(boolean credentialsExpired) {
        this.credentialsExpired = credentialsExpired;
        return this;
    }

    /**
     * @param mustChangePassword whether the holder must choose a new password before using the account (default
     *                           false)
     * @return this builder
     * @see org.artifactory.security.UserInfo#isMustChangePassword()
     */
    public UserInfoBuilder mustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
        return this;
    }

    public UserInfoBuilder lastLogin(long time, String ip) {
        this.lastLoginTime = time;
        this.lastLoginIp = ip;
        return this;
    }

    public UserInfoBuilder genPasswordKey(String genPasswordKey) {
        this.genPasswordKey = genPasswordKey;
        return this;
    }

    public UserInfoBuilder transientUser() {
        return transientUser(true);
    }

    public UserInfoBuilder transientUser(boolean transientUser) {
        this.transientUser = transientUser;
        return this;
    }

    public UserInfoBuilder internalGroups(Set<String> groupNames) {
        if (groupNames != null) {
            groups(InfoFactoryHolder.get().createGroups(groupNames));
        } else {
            groups(null);
        }
        return this;
    }

    public UserInfoBuilder groups(@Nullable Set<UserGroupInfo> groups) {
        if (groups != null) {
            this.groups = groups;
        } else {
            this.groups = Collections.emptySet();
        }
        return this;
    }

    public UserInfoBuilder addProp(UserPropertyInfo prop) {
        if (userPropertyInfos == null) {
            this.userPropertyInfos = Sets.newHashSet();
        }
        userPropertyInfos.add(prop);
        return this;
    }

    public UserInfoBuilder privateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public UserInfoBuilder publicKey(String publicKey) {
        this.publicKey = publicKey;
        return this;
    }

    public UserInfoBuilder realm(String realm) {
        this.realm = realm;
        return this;
    }

    public UserInfoBuilder passwordDisabled(boolean passwordDisabled) {
        this.passwordDisabled = passwordDisabled;
        return this;
    }

    public UserInfoBuilder mfaStatus(UserMFAStatus mfaStatus) {
        this.mfaStatus = mfaStatus;
        return this;
    }

    public UserInfoBuilder status(ArtifactoryUserStatus status) {
        this.status = status == null ? ArtifactoryUserStatus.ENABLED : status;
        return this;
    }

    public UserInfoBuilder shouldInvite(boolean shouldInvite) {
        this.shouldInvite = shouldInvite;
        return this;
    }

    public UserInfoBuilder source(String source) {
        this.source = source;
        return this;
    }

    public UserInfoBuilder appliedPermissionsGroups(boolean appliedPermissionsGroups) {
        this.appliedPermissionsGroups = appliedPermissionsGroups;
        return this;
    }

    public UserInfoBuilder identity(boolean identity) {
        this.identity = identity;
        return this;
    }

    public UserInfoBuilder disabledUiAccess(boolean disabledUiAccess) {
        this.disabledUiAccess = disabledUiAccess;
        return this;
    }

    public UserInfoBuilder emailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
        return this;
    }
}
