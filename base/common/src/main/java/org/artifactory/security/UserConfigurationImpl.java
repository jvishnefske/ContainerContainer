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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.artifactory.addon.oauth.OAuthProvidersTypeEnum;

import java.util.Map;
import java.util.Set;

/**
 * @author Noam Y. Tenne
 */
public class UserConfigurationImpl {

    private String name;
    private String email;
    private String password;
    private boolean admin;
    private boolean policyViewer;
    private boolean policyManager;
    private boolean watchManager;
    private boolean reportsManager;
    private Boolean groupAdmin;
    private boolean profileUpdatable = true;
    private boolean internalPasswordDisabled = false;
    private Set<String> groups;
    private String lastLoggedIn;
    private long lastLoggedInMillis;
    private String realm;
    private boolean offlineMode;
    private boolean disableUIAccess;
    private UserMFAStatus mfaStatus;
    private ArtifactoryUserStatus status;
    private Map<OAuthProvidersTypeEnum, String> centralOAuthProviderUserIds;
    private boolean shouldInvite = false;
    private String source;

    /**
     * @return The username
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAdmin() {
        return admin;
    }

    @JsonIgnore
    public boolean isEffectiveAdmin() {
        return admin || (groupAdmin != null && groupAdmin);
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public Boolean isGroupAdmin() {
        return groupAdmin;
    }

    public void setGroupAdmin(boolean groupAdmin) {
        this.groupAdmin = groupAdmin;
    }

    public boolean isPolicyViewer() {
        return policyViewer;
    }

    public void setPolicyViewer(boolean policyViewer) {
        this.policyViewer = policyViewer;
    }

    public boolean isPolicyManager() {
        return policyManager;
    }

    public void setPolicyManager(boolean policyManager) {
        this.policyManager = policyManager;
    }

    public boolean isWatchManager() {
        return watchManager;
    }

    public void setWatchManager(boolean watchManager) {
        this.watchManager = watchManager;
    }

    public boolean isReportsManager() {
        return reportsManager;
    }

    public void setReportsManager(boolean reportsManager) {
        this.reportsManager = reportsManager;
    }

    public boolean isProfileUpdatable() {
        return profileUpdatable;
    }

    public void setProfileUpdatable(boolean profileUpdatable) {
        this.profileUpdatable = profileUpdatable;
    }

    public boolean isInternalPasswordDisabled() {
        return internalPasswordDisabled;
    }

    public void setInternalPasswordDisabled(boolean internalPasswordDisabled) {
        this.internalPasswordDisabled = internalPasswordDisabled;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    public String getLastLoggedIn() {
        return lastLoggedIn;
    }

    public void setLastLoggedIn(String lastLoggedIn) {
        this.lastLoggedIn = lastLoggedIn;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public boolean isOfflineMode() {
        return offlineMode;
    }

    public void setOfflineMode(boolean offlineMode) {
        this.offlineMode = offlineMode;
    }

    public long getLastLoggedInMillis() {
        return lastLoggedInMillis;
    }

    public void setLastLoggedInMillis(long lastLoggedInMillis) {
        this.lastLoggedInMillis = lastLoggedInMillis;
    }

    public boolean isdisableUIAccess() {
        return disableUIAccess;
    }

    public void setdisableUIAccess(boolean disableUIAccess) {
        this.disableUIAccess = disableUIAccess;
    }

    public UserMFAStatus getMfaStatus() {
        return mfaStatus;
    }

    public void setMfaStatus(UserMFAStatus mfaStatus) {
        this.mfaStatus = mfaStatus;
    }

    public ArtifactoryUserStatus getStatus() {
        return status;
    }

    public void setStatus(ArtifactoryUserStatus status) {
        this.status = status;
    }

    public Map<OAuthProvidersTypeEnum, String> getCentralOAuthProviderUserIds() {
        return centralOAuthProviderUserIds;
    }

    public void setCentralOAuthProviderUserIds(Map<OAuthProvidersTypeEnum, String> centralOAuthProviderUserIds) {
        this.centralOAuthProviderUserIds = centralOAuthProviderUserIds;
    }

    public boolean isShouldInvite() {
        return shouldInvite;
    }

    public void setShouldInvite(boolean shouldInvite) {
        this.shouldInvite = shouldInvite;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserConfigurationImpl)) {
            return false;
        }

        UserConfigurationImpl that = (UserConfigurationImpl) o;

        if (admin != that.admin) {
            return false;
        }
        if (internalPasswordDisabled != that.internalPasswordDisabled) {
            return false;
        }
        if (profileUpdatable != that.profileUpdatable) {
            return false;
        }
        if (email != null ? !email.equals(that.email) : that.email != null) {
            return false;
        }
        if (groups != null ? !groups.equals(that.groups) : that.groups != null) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        return realm != null ? realm.equals(that.realm) : that.realm == null;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + email.hashCode();
        result = 31 * result + (admin ? 1 : 0);
        result = 31 * result + (profileUpdatable ? 1 : 0);
        result = 31 * result + (internalPasswordDisabled ? 1 : 0);
        result = 31 * result + (groups != null ? groups.hashCode() : 0);
        result = 31 * result + (realm != null ? realm.hashCode() : 0);
        return result;
    }
}
