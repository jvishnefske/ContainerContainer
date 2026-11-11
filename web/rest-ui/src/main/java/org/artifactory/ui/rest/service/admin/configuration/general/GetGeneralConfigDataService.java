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
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.GeneralConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.artifactory.platform.PlatformConfigHolder.PLATFORM_GENERAL_CONFIG_KEY;

/**
 * @author Danny Reiser
 */
@Component
public class GetGeneralConfigDataService implements RestService {

    @Autowired
    private CentralConfigService centralConfigService;
    @Autowired
    private AccessService accessService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        PlatformGeneralConfig platformGeneralConfig = getGeneralPlatformConfig();
        GeneralConfig generalconfig = new GeneralConfig();
        generalconfig.setServerName(descriptor.getServerName());
        generalconfig.setLogoUrl(platformGeneralConfig.getLogo());
        response.iModel(generalconfig);
    }

    private PlatformGeneralConfig getGeneralPlatformConfig() {
        return accessService.getPlatformConfigClient()
                .getConfig(PLATFORM_GENERAL_CONFIG_KEY, PlatformGeneralConfig.class)
                .orElse(PlatformGeneralConfig.builder().build());
    }
}
