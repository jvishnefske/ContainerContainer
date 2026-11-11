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

package org.artifactory.ui.rest.model.admin.security.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.security.UserConfigurationImpl;
import org.artifactory.security.UserInfo;

import java.util.Set;

/**
 * @author Chen Keinan
 */
public class BaseUser extends UserConfigurationImpl implements RestModel {

    private boolean proWithoutLicense;
    private Boolean canDeploy;
    private Boolean canManage;
    private Boolean canCreateReleaseBundle;
    private Boolean canCreateReleaseBundleV2;
    private Boolean isBuildBasicView;
    private String externalRealmLink;
    private Boolean anonAccessEnabled;
    private boolean existsInDB;
    private boolean requireProfileUnlock;
    private boolean requireProfilePassword;
    private Boolean credentialsExpired;
    private Integer currentPasswordValidFor;
    private Integer numberOfGroups;
    @JsonProperty("providerName")
    private String authProviderName;

    public BaseUser() {
    }

    public BaseUser(UserInfo user) {
        this.setLastLoggedInMillis(user.getLastLoginTimeMillis());
        this.setRealm(user.getRealm());
        this.setAdmin(user.isAdmin());
        this.setPolicyViewer(user.isPolicyViewer());
        this.setPolicyManager(user.isPolicyManager());
        this.setWatchManager(user.isWatchManager());
        this.setReportsManager(user.isReportsManager());
        this.setGroupAdmin(user.isGroupAdmin());
        this.setEmail(user.getEmail());
        this.setName(user.getUsername());
        this.setProfileUpdatable(user.isUpdatableProfile());
        this.setCredentialsExpired(user.isCredentialsExpired());
        this.setInternalPasswordDisabled(user.isPasswordDisabled());
        this.setMfaStatus(user.getMfaStatus());
        this.setStatus(user.getStatus());
    }

    public void setProWithoutLicense(boolean proWithoutLicense) {
        this.proWithoutLicense = proWithoutLicense;
    }

    public boolean isProWithoutLicense() {
        return proWithoutLicense;
    }

    public BaseUser(String userName, boolean admin) {
        super.setAdmin(admin);
        super.setName(userName);
    }

    public Boolean isCanDeploy() {
        return canDeploy;
    }

    public void setCanDeploy(Boolean canDeploy) {
        this.canDeploy = canDeploy;
    }

    public Boolean isCanCreateReleaseBundle() {
        return canCreateReleaseBundle;
    }

    public void setCanCreateReleaseBundle(Boolean canCreateReleaseBundle) {
        this.canCreateReleaseBundle = canCreateReleaseBundle;
    }

    public Boolean isCanCreateReleaseBundleV2() {
        return canCreateReleaseBundleV2;
    }

    public void setCanCreateReleaseBundleV2(Boolean canCreateReleaseBundleV2) {
        this.canCreateReleaseBundleV2 = canCreateReleaseBundleV2;
    }

    public Boolean isCanManage() {
        return canManage;
    }

    public void setCanManage(Boolean canManage) {
        this.canManage = canManage;
    }

    public Boolean getBuildBasicView() {
        return isBuildBasicView;
    }

    public void setBuildBasicView(Boolean buildBasicView) {
        isBuildBasicView = buildBasicView;
    }

    public String getExternalRealmLink() {
        return externalRealmLink;
    }

    public void setExternalRealmLink(String externalRealmLink) {
        this.externalRealmLink = externalRealmLink;
    }

    public Boolean getAnonAccessEnabled() {
        return anonAccessEnabled;
    }

    public void setAnonAccessEnabled(Boolean anonAccessEnabled) {
        this.anonAccessEnabled = anonAccessEnabled;
    }

    public boolean isExistsInDB() {
        return existsInDB;
    }

    public void setExistsInDB(boolean existsInDB) {
        this.existsInDB = existsInDB;
    }

    public boolean isRequireProfileUnlock() {
        return requireProfileUnlock;
    }

    public boolean isRequireProfilePassword() {
        return requireProfilePassword;
    }

    public void setRequireProfilePassword(boolean requireProfilePassword) {
        this.requireProfilePassword = requireProfilePassword;
    }

    public void setRequireProfileUnlock(boolean requireProfileUnlock) {
        this.requireProfileUnlock = requireProfileUnlock;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    public Boolean getCredentialsExpired() {
        return credentialsExpired;
    }

    public void setCredentialsExpired(Boolean credentialsExpired) {
        this.credentialsExpired = credentialsExpired;
    }

    /**
     * @return number of days till password should be changed
     */
    public Integer getCurrentPasswordValidFor() {
        return currentPasswordValidFor;
    }

    /**
     * @param currentPasswordValidFor number of days till password should be changed
     */
    public void setCurrentPasswordValidFor(Integer currentPasswordValidFor) {
        this.currentPasswordValidFor = currentPasswordValidFor;
    }

    public Integer getNumberOfGroups() {
        if (numberOfGroups == null) {
            Set<String> groups = getGroups();
            return groups == null ? 0 : groups.size();
        }
        return numberOfGroups;
    }

    public void setNumberOfGroups(Integer numberOfGroups) {
        this.numberOfGroups = numberOfGroups;
    }

    public String getAuthProviderName() {
        return authProviderName;
    }

    public void setAuthProviderName(String oauthProviderName) {
        this.authProviderName = oauthProviderName;
    }
}
