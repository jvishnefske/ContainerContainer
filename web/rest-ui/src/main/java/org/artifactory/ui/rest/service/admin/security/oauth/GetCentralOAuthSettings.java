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

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.sso.CentralOAuthSSOAddon;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.descriptor.security.oauth.OAuthProviderSettings;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.oauth.CentralOAuthUIModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetCentralOAuthSettings implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetCentralOAuthSettings.class);

    private final AddonsManager addonsManager;

    @Autowired
    public GetCentralOAuthSettings(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        log.debug("Retrieving Central OAuth settings");
        CentralOAuthSSOAddon centralOAuthAddon = addonsManager.addonByType(CentralOAuthSSOAddon.class);
        CentralOAuthSettings centralOAuthSettings = centralOAuthAddon.getConfiguration();
        CentralOAuthUIModel model = new CentralOAuthUIModel(getAvailableProviders(centralOAuthSettings));
        response.iModel(model);
    }

    private List<String> getAvailableProviders(CentralOAuthSettings oauthSettings) {
        return oauthSettings.getProviders().stream()
                .map(OAuthProviderSettings::getName)
                .collect(Collectors.toList());
    }
}