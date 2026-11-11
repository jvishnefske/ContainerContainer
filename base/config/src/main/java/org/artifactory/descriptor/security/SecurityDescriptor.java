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

package org.artifactory.descriptor.security;

import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.oauth.CentralOAuthProviders;
import org.artifactory.descriptor.security.signingkeys.SigningKeysSettings;
import org.artifactory.descriptor.security.sshserver.SshServerSettings;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Yossi Shaul
 *
 * Don't forget to override equals/hashCode with adding new fields.
 * Otherwise {@link org.artifactory.security.SecurityServiceImpl#reload(CentralConfigDescriptor, List)} won't
 * reload auth cache.
 */
@XmlRootElement
@XmlType(name = "SecurityType", propOrder = {"hideUnauthorizedResources", "passwordSettings",
        "centralOAuthProviders", "signingKeysSettings", "sshServerSettings", "buildGlobalBasicReadAllowed",
        "buildGlobalBasicReadForAnonymous"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
public class SecurityDescriptor implements Descriptor {

    /**
     * This flag controls whether we reveal the cause when user requests a resource she is not authorize to view.
     */
    @XmlElement(defaultValue = "false", required = false)
    private boolean hideUnauthorizedResources = false;

    @XmlElement(name = "passwordSettings", required = false)
    private PasswordSettings passwordSettings = new PasswordSettings();

    @XmlElement(name = "signingKeysSettings", required = false)
    @Valid
    private SigningKeysSettings signingKeysSettings;

    @XmlElement(name = "centralOAuthProviders", required = false)
    @Valid
    private CentralOAuthProviders centralOAuthProviders;

    @XmlElement(name = "sshServerSettings", required = false)
    private SshServerSettings sshServerSettings;

    @XmlElement(defaultValue = "false")
    private boolean buildGlobalBasicReadAllowed;

    @XmlElement(defaultValue = "false")
    private boolean buildGlobalBasicReadForAnonymous;

    /**
     * This flag controls whether we reveal the cause when user requests a resource she is not authorize to view.
     */
    public boolean isHideUnauthorizedResources() {
        return hideUnauthorizedResources;
    }

    /**
     * This flag controls whether we reveal the cause when user requests a resource she is not authorize to view.
     *
     * @param hideUnauthorizedResources If true hide security reason (return 404)
     */
    public void setHideUnauthorizedResources(boolean hideUnauthorizedResources) {
        this.hideUnauthorizedResources = hideUnauthorizedResources;
    }

    boolean isCentralOAuthEnabled() {
        return centralOAuthProviders != null && centralOAuthProviders.getEnabled();
    }

    /**
     * Calculates if any external authentication provider is configured.
     *
     * @return true if any auth provider is configured.
     */
    public boolean isAnyExternalAuthEnabled() {
        return isCentralOAuthEnabled();
    }

    @Nonnull
    public PasswordSettings getPasswordSettings() {
        return passwordSettings;
    }

    public void setPasswordSettings(PasswordSettings passwordSettings) {
        this.passwordSettings = Optional.ofNullable(passwordSettings).orElseGet(PasswordSettings::new);
    }

    public CentralOAuthProviders getCentralOAuthProviders() {
        return centralOAuthProviders;
    }

    public void setCentralOAuthProviders(CentralOAuthProviders centralOAuthProviders) {
        this.centralOAuthProviders = centralOAuthProviders;
    }

    public SigningKeysSettings getSigningKeysSettings() {
        return signingKeysSettings;
    }

    public void setSigningKeysSettings(SigningKeysSettings signingKeysSettings) {
        this.signingKeysSettings = signingKeysSettings;
    }

    public SshServerSettings getSshServerSettings() {
        return sshServerSettings;
    }

    public void setSshServerSettings(SshServerSettings sshServerSettings) {
        this.sshServerSettings = sshServerSettings;
    }

    public boolean isBuildGlobalBasicReadAllowed() {
        return buildGlobalBasicReadAllowed;
    }

    public void setBuildGlobalBasicReadAllowed(boolean buildGlobalBasicReadAllowed) {
        this.buildGlobalBasicReadAllowed = buildGlobalBasicReadAllowed;
    }

    public boolean isBuildGlobalBasicReadForAnonymous() {
        return buildGlobalBasicReadForAnonymous;
    }

    public void setBuildGlobalBasicReadForAnonymous(boolean buildGlobalBasicReadForAnonymous) {
        this.buildGlobalBasicReadForAnonymous = buildGlobalBasicReadForAnonymous;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SecurityDescriptor that = (SecurityDescriptor) o;

        if (hideUnauthorizedResources != that.hideUnauthorizedResources) {
            return false;
        }
        if (!Objects.equals(passwordSettings, that.passwordSettings)) {
            return false;
        }
        if (!Objects.equals(signingKeysSettings, that.signingKeysSettings)) {
            return false;
        }
        if (!Objects.equals(sshServerSettings, that.sshServerSettings)) {
            return false;
        }
        if (buildGlobalBasicReadAllowed != that.buildGlobalBasicReadAllowed) {
            return false;
        }
        if (buildGlobalBasicReadForAnonymous != that.buildGlobalBasicReadForAnonymous) {
            return false;
        }
        return Objects.equals(centralOAuthProviders, that.centralOAuthProviders);
    }

    @Override
    public int hashCode() {
        int result = (hideUnauthorizedResources ? 1 : 0);
        result = 31 * result + (passwordSettings != null ? passwordSettings.hashCode() : 0);
        result = 31 * result + (signingKeysSettings != null ? signingKeysSettings.hashCode() : 0);
        result = 31 * result + (sshServerSettings != null ? sshServerSettings.hashCode() : 0);
        result = 31 * result + (buildGlobalBasicReadAllowed ? 1 : 0);
        result = 31 * result + (buildGlobalBasicReadForAnonymous ? 1 : 0);
        result = 31 * result + (centralOAuthProviders != null ? centralOAuthProviders.hashCode() : 0);
        return result;
    }
}
