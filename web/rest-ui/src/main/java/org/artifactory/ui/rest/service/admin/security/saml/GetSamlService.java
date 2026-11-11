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

package org.artifactory.ui.rest.service.admin.security.saml;

import com.google.common.annotations.VisibleForTesting;
import lombok.Setter;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.saml.Saml;
import org.artifactory.ui.rest.model.empty.EmptyModel;
import org.jfrog.access.client.model.SamlSetting;
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
public class GetSamlService implements RestService<Saml> {

    private static final AccessSamlMapper mapper = Mappers.getMapper(AccessSamlMapper.class);

    @Autowired
    private AccessService accessService;

    @Override
    public void execute(ArtifactoryRestRequest<Saml> request, RestResponse response) {
        RestModel restModel = getSamlModel();
        response.iModel(restModel);
    }

    /**
     * get saml setting and populate it data to saml model
     *
     * @return - saml model
     */
    @VisibleForTesting
    protected RestModel getSamlModel() {
        SamlSetting samlSetting = accessService.getSamlSettings();
        if (samlSetting != null) {
            return mapper.map(samlSetting);
        }
        return new EmptyModel();
    }
}
