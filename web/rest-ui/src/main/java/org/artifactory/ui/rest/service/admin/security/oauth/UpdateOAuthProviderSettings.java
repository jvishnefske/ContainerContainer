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
import org.artifactory.aol.AolService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthProviderUIModel;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUIProvidersTypeEnum;
import org.jfrog.access.client.model.OAuthSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * @author Gidi Shabat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UpdateOAuthProviderSettings implements RestService<OAuthProviderUIModel> {
    private static final Logger log = LoggerFactory.getLogger(UpdateOAuthProviderSettings.class);

    @Autowired
    private AccessService accessService;
    @Autowired
    private AolService aolService;

    @Override
    public void execute(ArtifactoryRestRequest<OAuthProviderUIModel> request, RestResponse response) {
        OAuthProviderUIModel imodel = request.getImodel();

        if (StringUtils.isBlank(imodel.getName())) {
            response.error("Missing provider name");
            return;
        }

        Optional<OAuthSetting> existingOAuthSetting = accessService.getOAuthSetting(imodel.getName());
        if (existingOAuthSetting.isEmpty()) {
            response.error("Couldn't update provider, not exist.");
            return;
        }

        if (aolService.isAol() && imodel.isUseDefaultProxy()) {
            response.error("Proxy is not available in Saas");
            return;
        }
        if (StringUtils.isBlank(imodel.getProviderType())) {
            response.error("Missing provider Type");
            return;
        }
        if (!OAuthUIProvidersTypeEnum.isSupportedProviderType(imodel.getProviderType())) {
            response.error("Invalid provider Type");
            return;
        }
        if (StringUtils.equalsIgnoreCase(imodel.getProviderType(),
                OAuthUIProvidersTypeEnum.github.getProviderType().name()) &&
                (StringUtils.isNotBlank(existingOAuthSetting.get().getDomain()) &&
                        StringUtils.isBlank(imodel.getDomain()))) {
            response.error("Organization cannot be empty for github provider settings");
            return;
        }

        OAuthSetting oAuthSetting = OAuthSetting.builder()
                .enabled(imodel.isEnabled())
                .name(imodel.getName())
                .providerType(imodel.getProviderType())
                .clientId(imodel.getId())
                .clientSecret(CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), imodel.getSecret()))
                .apiUrl(imodel.getApiUrl())
                .basicUrl(imodel.getBasicUrl())
                .authUrl(imodel.getAuthUrl())
                .domain(imodel.getDomain())
                .tokenUrl(imodel.getTokenUrl())
                .pkce(imodel.isPkce())
                .useDefaultProxy(imodel.isUseDefaultProxy())
                .autoUserCreation(existingOAuthSetting.get().isAutoUserCreation())
                .allowUserToAccessProfile(existingOAuthSetting.get().isAllowUserToAccessProfile())
                .central(false)
                .build();

        log.debug("Updating OAuth provider '{}'", imodel.getName());

        try {
            accessService.upsertOAuthSettings(List.of(oAuthSetting));
            response.info("Successfully updates OAuth provider " + imodel.getName());
            log.debug("Successfully updated OAuth provider '{}'", imodel.getName());
        } catch (Exception e) {
            String message = "Error occurred while updating OAuth settings";
            log.error("{}. {}", message, e.getMessage());
            response.error(message + ", please review the log");
        }
    }
}
