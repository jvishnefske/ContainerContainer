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

package org.artifactory.ui.rest.service.admin.configuration.general;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.PlatformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Yoaz Menda
 */
@Component
public class UpdatePlatformConfigService implements RestService {

    private final CentralConfigService centralConfigService;
    private final PlatformConfigService platformConfigService;

    @Autowired
    public UpdatePlatformConfigService(CentralConfigService centralConfigService,
            PlatformConfigService platformConfigService) {
        this.centralConfigService = centralConfigService;
        this.platformConfigService = platformConfigService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        PlatformConfig platformConfig = (PlatformConfig) request.getImodel();
        updateDescriptorAndSave(platformConfig);
        response.info("Successfully updated settings");
    }

    /**
     * update config descriptor with general config setting and save
     *
     * @param platformConfig - platform setting sent from client
     */
    private void updateDescriptorAndSave(PlatformConfig platformConfig) {
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        mutableDescriptor.setServerName(platformConfig.getServerName());
        mutableDescriptor.setUrlBase(platformConfig.getCustomUrlBase());
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor);

        platformConfigService.updatePlatformGeneralConfig(platformConfig.getDateFormat(),
                platformConfig.isHelpLinksEnabled(), platformConfig.getLogoUrl());
        platformConfigService.updatePlatformSystemMessageConfig(platformConfig.isSystemMessageEnabled(),
                platformConfig.getSystemMessageTitle(), platformConfig.getSystemMessageTitleColor(),
                platformConfig.getSystemMessage());
    }
}
