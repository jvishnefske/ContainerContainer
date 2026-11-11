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
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.general.CentralOAuthSecurityConfig;
import org.artifactory.ui.rest.model.admin.security.general.SecurityConfig;
import org.artifactory.ui.rest.model.admin.security.password.PasswordSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
public class GetSecurityConfigService implements RestService {

    @Autowired
    private CentralConfigService centralConfigService;
    @Autowired
    private AccessService accessService;
    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        CentralConfigDescriptor centralConfig = centralConfigService.getDescriptor();
        SecurityDescriptor securityDescriptor = centralConfig.getSecurity();
        UserLockPolicy userLockPolicy = new UserLockPolicy();
        int attempts = accessService.getUserLockPolicyConfig().getAttempts();
        userLockPolicy.setEnabled(attempts > 0);
        userLockPolicy.setLoginAttempts(attempts);
        PasswordSettings passwordSettings =
                PasswordSettings.fromDescriptorPasswordSettings(securityDescriptor.getPasswordSettings());
        passwordSettings.setEncryptionPolicy(accessService.getEncryptionPolicy());
        passwordSettings.setEncryptionEnabled(accessService.isEncryptionEnabled());
        passwordSettings.setEncryptionRequired(accessService.isEncryptionRequired());

        boolean curationEntitled = false;
        boolean allowDownloadWhenUnavailable = false;
        if (ConstantValues.curationFailOpenUiEnabled.getBoolean()) {
            try {
                CurationAddon curationAddon = addonsManager.addonByType(CurationAddon.class);
                curationEntitled = curationAddon.isCurationEntitled();
                allowDownloadWhenUnavailable = curationAddon.isAllowDownloadWhenUnavailable();
            } catch (Exception e) {
                log.warn("Failed to retrieve curation settings, defaulting to false", e);
            }
        }

        SecurityConfig securityConfig = new SecurityConfig(accessService.isAnonymousAccessEnabled(),
                securityDescriptor.isBuildGlobalBasicReadAllowed(),
                securityDescriptor.isBuildGlobalBasicReadForAnonymous(),
                securityDescriptor.isHideUnauthorizedResources(),
                passwordSettings,
                userLockPolicy,
                accessService.isBasicAuthEnabled(),
                new CentralOAuthSecurityConfig(
                        centralConfigService.getSystemConfiguration().getCentralOAuthDefaultConfig(),
                        securityDescriptor.getCentralOAuthProviders()),
                curationEntitled,
                allowDownloadWhenUnavailable);

        response.iModel(securityConfig);
    }
}
