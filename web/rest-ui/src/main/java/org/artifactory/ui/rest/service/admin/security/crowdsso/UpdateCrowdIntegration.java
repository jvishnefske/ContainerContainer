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

package org.artifactory.ui.rest.service.admin.security.crowdsso;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.crowdsso.CrowdIntegration;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Setter
@Slf4j
public class UpdateCrowdIntegration implements RestService {
    private static final AccessCrowdMapper mapper = Mappers.getMapper(AccessCrowdMapper.class);
    @Autowired
    private AccessService accessService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        CrowdIntegration crowdIntegration = (CrowdIntegration) request.getImodel();
        // save crowd settings to config descriptor
        saveCrowdSettings(response, crowdIntegration);
    }

    /**
     * save crowd settings to config descriptor
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param crowdIntegration    - crowd settings
     */
    private void saveCrowdSettings(RestResponse artifactoryResponse, CrowdIntegration crowdIntegration) {
        crowdIntegration.toBuilder()
                .password(CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), crowdIntegration.getPassword())).build();
        try {
            accessService.getAccessAuthSettingsClient().upsertCrowdSetting(mapper.map(crowdIntegration));
            artifactoryResponse.info("Successfully updated Atlassian Crowd / Jira settings");
        } catch (Exception e) {
            artifactoryResponse.error("Failed to save new settings");
            log.error("An error occurred while saving new Crowd SSO settings", e);
        }
    }
}
