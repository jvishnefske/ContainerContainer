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

package org.artifactory.ui.rest.service.admin.security.general;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.curation.CurationInterfaceSettings;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.AccessConfigMapUtils;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.general.CentralOAuthSecurityConfig;
import org.artifactory.ui.rest.model.admin.security.general.SecurityConfig;
import org.artifactory.ui.rest.model.admin.security.password.PasswordSettings;
import org.artifactory.util.SecurityUtils;
import org.jfrog.access.client.AccessClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Chen Keinan
 */
@Component
@Slf4j
public class UpdateSecurityConfigService implements RestService {

    @Autowired
    private CentralConfigService centralConfigService;

    @Autowired
    private AccessService accessService;

    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        MutableCentralConfigDescriptor centralConfig = centralConfigService.getMutableDescriptor();
        SecurityConfig editedDescriptor = (SecurityConfig) request.getImodel();
        SecurityDescriptor securityDescriptor = centralConfig.getSecurity();
        updateSecurityDescriptor(editedDescriptor, securityDescriptor);
        centralConfig.setSecurity(securityDescriptor);
        Map<String, Object> accessMap = new HashMap<>();

        if (editedDescriptor.getUserLockPolicy() != null) {
            AccessConfigMapUtils.addLoginAttempts(editedDescriptor.getUserLockPolicy().getLoginAttempts(), accessMap);
        }
        if (editedDescriptor.getPasswordSettings() != null &&
                editedDescriptor.getPasswordSettings().getEncryptionPolicy() != null) {
            AccessConfigMapUtils.addPasswordEncryptionPolicy(
                    editedDescriptor.getPasswordSettings().getEncryptionPolicy(), accessMap);
        }
        if (editedDescriptor.getBasicAuthEnabled() != null) {
            if (Boolean.FALSE.equals(editedDescriptor.getBasicAuthEnabled()) &&
                    !SecurityUtils.isAnyExternalAuthEnabled()) {
                log.warn("Cannot turn off basic auth when no external authentication providers exist");
            } else {
                AccessConfigMapUtils.addBasicAuthenticationEnabled(editedDescriptor.getBasicAuthEnabled(), accessMap);
            }
        }
        if (editedDescriptor.getAnonAccessEnabled() != null) {
            AccessConfigMapUtils.addAnonAccessEnabled(editedDescriptor.getAnonAccessEnabled(), accessMap);
        }
        try {
            accessService.updateConfigurationModel(accessMap);
        } catch (AccessClientException e) {
            throw new ConfigurationException(e.getMessage());
        }

        updateCurationSettings(editedDescriptor);

        centralConfigService.saveEditedDescriptorAndReload(centralConfig, editedDescriptor);
        response.info("Successfully updated security settings");
    }

    private void updateCurationSettings(SecurityConfig editedDescriptor) {
        if (ConstantValues.curationFailOpenUiEnabled.getBoolean()) {
            try {
                CurationAddon curationAddon = addonsManager.addonByType(CurationAddon.class);
                if (curationAddon.isCurationEntitled()) {
                    curationAddon.updateInterfaceSettings(
                            new CurationInterfaceSettings(editedDescriptor.isAllowDownloadWhenUnavailable()));
                }
            } catch (Exception e) {
                throw new ConfigurationException(e.getMessage());
            }
        }
    }

    /**
     * update security descriptor general data
     *
     * @param editedDescriptor   - changed general data
     * @param securityDescriptor - config descriptor from memory
     */
    private void updateSecurityDescriptor(SecurityConfig editedDescriptor, SecurityDescriptor securityDescriptor) {
        securityDescriptor.setBuildGlobalBasicReadAllowed(editedDescriptor.isBuildGlobalBasicReadAllowed());
        securityDescriptor.setBuildGlobalBasicReadForAnonymous(editedDescriptor.isBuildGlobalBasicReadForAnonymous());
        securityDescriptor.setHideUnauthorizedResources(editedDescriptor.isHideUnauthorizedResources());
        PasswordSettings passwordSettings = editedDescriptor.getPasswordSettings();
        if (passwordSettings != null) {
            securityDescriptor.setPasswordSettings(passwordSettings.toDescriptorPasswordSettings());
        }

        CentralOAuthSecurityConfig centralOAuthConfig = editedDescriptor.getCentralOAuthProviders();
        if (centralOAuthConfig != null) {
            securityDescriptor.setCentralOAuthProviders(centralOAuthConfig.toDescriptor());
        }
    }
}
