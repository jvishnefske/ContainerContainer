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

package org.artifactory.ui.rest.service.admin.security.httpsso;

import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.httpsso.HttpSso;
import org.artifactory.util.AolUtils;
import org.jfrog.access.client.model.HttpSsoSetting;
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
public class UpdateHttpSsoService implements RestService {

    private static final AccessHttpSsoMapper mapper = Mappers.getMapper(AccessHttpSsoMapper.class);

    @Autowired
    private AccessService accessService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        boolean isHttpSsoEnabledAOL = ConstantValues.aolSecurityHttpSsoEnabled.getBoolean();
        if (!isHttpSsoEnabledAOL) {
            AolUtils.assertNotAol("UpdateHttpSso");
        }
        // update sso setting
        HttpSso httpSsoSettings = (HttpSso) request.getImodel();
        HttpSsoSetting httpSsoSetting = mapper.map(httpSsoSettings);
        // save sso setting to descriptor
        saveSsoSetting(httpSsoSetting);
        response.info("Successfully updated HTTP SSO settings");
    }

    /**
     * save sso setting to descriptor
     *
     * @param httpSsoSetting    - http sso setting
     */
    private void saveSsoSetting(HttpSsoSetting httpSsoSetting) {
        accessService.getAccessAuthSettingsClient().upsertHttpSsoSetting(httpSsoSetting);
    }
}
