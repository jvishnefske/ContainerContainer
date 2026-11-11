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

package org.artifactory.ui.rest.service.admin.security.oauth;

import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUIModel;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * @author Gidi Shabat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UpdateOrCreateOAuthSettings implements RestService<OAuthUIModel> {
    private static final Logger log = LoggerFactory.getLogger(UpdateOrCreateOAuthSettings.class);

    @Autowired
    private AccessService accessService;

    @Override
    public void execute(ArtifactoryRestRequest<OAuthUIModel> request, RestResponse response) {
        log.debug("Updating OAuth settings");
        OAuthUIModel imodel = request.getImodel();

        try {
            List<OAuthSetting> oAuthSettingList = accessService.getAllOAuthSettings();
            OAuthGeneralSettings oAuthGeneralSettings = accessService.getOAuthGeneralSettings();
            if (oAuthGeneralSettings == null || oAuthGeneralSettings.isEnable() != imodel.isEnabled() ||
                    oAuthGeneralSettings.getDefaultSetting() == null ||
                    !oAuthGeneralSettings.getDefaultSetting().equals(imodel.getDefaultNpm())) {
                OAuthGeneralSettings newOAuthGeneralSettings =
                        OAuthGeneralSettings.builder().defaultSetting(imodel.getDefaultNpm()).enable(imodel.isEnabled())
                                .build();
                accessService.upsertOAuthGeneralSetting(newOAuthGeneralSettings);
            }
            List<OAuthSetting> providersToUpdate = oAuthSettingList.stream()
                    .filter(o -> o.isAutoUserCreation() != imodel.isPersistUsers() ||
                            o.isAllowUserToAccessProfile() != imodel.isAllowUserToAccessProfile())
                    .map(o -> o.toBuilder().allowUserToAccessProfile(imodel.isAllowUserToAccessProfile())
                            .autoUserCreation(imodel.isPersistUsers())
                            .build())
                    .toList();
            if (!providersToUpdate.isEmpty()) {
                accessService.upsertOAuthSettings(providersToUpdate);
            }
            response.info("Successfully updated OAuth settings");
            log.debug("Successfully Updated OAuth settings");
        }catch (Exception e) {
            String message = "Error occurred while updating OAuth settings";
            log.error("{}. {}", message, e.getMessage());
            response.error(message + ", please review the log");
        }

    }
}
