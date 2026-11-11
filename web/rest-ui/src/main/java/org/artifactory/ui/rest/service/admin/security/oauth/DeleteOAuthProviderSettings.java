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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.OAuthSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.artifactory.security.props.auth.OauthManager.OAUTH_INFO_PROPERTY;

/**
 * @author Gidi Shabat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteOAuthProviderSettings implements RestService<String> {
    private static final Logger log = LoggerFactory.getLogger(DeleteOAuthProviderSettings.class);

    @Autowired
    private AccessService accessService;

    @Autowired
    private UserGroupService userGroupService;

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        String providerName = request.getImodel();
        if (StringUtils.isBlank(providerName)) {
            response.error("Couldn't delete provider, missing provider name");
            return;
        }
        log.debug("Deleting OAuth provider '{}'", providerName);

        Optional<OAuthSetting> oAuthSetting = accessService.getOAuthSetting(providerName);

        if (oAuthSetting.isEmpty()) {
            response.error("Couldn't delete OAuth provider, OAuth settings doesn't exist");
            return;
        }
        try {
            accessService.removeOAuthSetting(providerName);
            userGroupService.deletePropertyFromAllUsers(OAUTH_INFO_PROPERTY + providerName);
            response.info("Successfully deleted OAuth provider " + providerName);
            log.debug("Successfully deleted OAuth provider '{}'", providerName);
        } catch (Exception e) {
            String message = "Error occurred while deleting OAuth settings";
            log.error("{}. {}", message, e.getMessage());
            response.error(message + ", please review the log");
        }
    }
}