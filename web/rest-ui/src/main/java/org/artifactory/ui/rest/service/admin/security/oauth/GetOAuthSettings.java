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

import com.google.common.collect.Lists;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthProviderInfo;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthProviderUIModel;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUIModel;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUIProvidersTypeEnum;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Gidi Shabat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetOAuthSettings implements RestService {

    private static final Logger log = LoggerFactory.getLogger(GetOAuthSettings.class);

    @Autowired
    private AccessService accessService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        log.debug("Retrieving OAuth settings ");
        response.iModel(createUIModel(accessService.getAllOAuthSettings(), accessService.getOAuthGeneralSettings()));
    }

    private OAuthUIModel createUIModel(List<OAuthSetting> allOAuthSettings, OAuthGeneralSettings oAuthGeneralSettings) {
        OAuthUIModel oAuthUIModel = new OAuthUIModel();
        oAuthUIModel.setEnabled(oAuthGeneralSettings.isEnable());
        oAuthUIModel.setProviders(getProvidersFromSettings(allOAuthSettings));
        oAuthUIModel.setAvailableTypes(getAvailableProviders());
        oAuthUIModel.setDefaultNpm(oAuthGeneralSettings.getDefaultSetting());
        if (!allOAuthSettings.isEmpty()) {
            oAuthUIModel.setPersistUsers(allOAuthSettings.getFirst().isAutoUserCreation());
            oAuthUIModel.setAllowUserToAccessProfile(allOAuthSettings.getFirst().isAllowUserToAccessProfile());
        }
        return oAuthUIModel;
    }

    private List<OAuthProviderUIModel> getProvidersFromSettings(List<OAuthSetting> allOAuthSettings) {
        return allOAuthSettings.
                stream().filter(Objects::nonNull).map(this::toModel).collect(Collectors.toList());
    }

    private List<OAuthProviderInfo> getAvailableProviders() {
        ArrayList<OAuthUIProvidersTypeEnum> es = Lists.newArrayList(OAuthUIProvidersTypeEnum.values());
        return es.stream().filter(Objects::nonNull).map(OAuthUIProvidersTypeEnum::getProviderInfo)
                .collect(Collectors.toList());
    }

    private OAuthProviderUIModel toModel(OAuthSetting oAuthSetting) {
        OAuthProviderUIModel oAuthProviderUIModel = new OAuthProviderUIModel();
        oAuthProviderUIModel.setId(oAuthSetting.getClientId());
        oAuthProviderUIModel.setSecret(oAuthSetting.getClientSecret());
        oAuthProviderUIModel.setApiUrl(oAuthSetting.getApiUrl());
        oAuthProviderUIModel.setBasicUrl(oAuthSetting.getBasicUrl());
        oAuthProviderUIModel.setAuthUrl(oAuthSetting.getAuthUrl());
        oAuthProviderUIModel.setTokenUrl(oAuthSetting.getTokenUrl());
        oAuthProviderUIModel.setEnabled(oAuthSetting.isEnabled());
        oAuthProviderUIModel.setDomain(oAuthSetting.getDomain());
        oAuthProviderUIModel.setProviderType(oAuthSetting.getProviderType());
        oAuthProviderUIModel.setName(oAuthSetting.getName());
        oAuthProviderUIModel.setPkce(oAuthSetting.isPkce());
        oAuthProviderUIModel.setUseDefaultProxy(oAuthSetting.isUseDefaultProxy());
        return oAuthProviderUIModel;
    }
}