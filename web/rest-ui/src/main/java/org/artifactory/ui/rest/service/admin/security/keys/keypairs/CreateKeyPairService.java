/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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

package org.artifactory.ui.rest.service.admin.security.keys.keypairs;

import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Yuval Reches
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CreateKeyPairService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(CreateKeyPairService.class);

    private final KeyPairService keyPairService;

    @Autowired
    public CreateKeyPairService(KeyPairService keyPairService) {
        this.keyPairService = keyPairService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        try {
            KeyPairModel requestModel = (KeyPairModel) request.getImodel();
            log.debug("Create Key Pair request for {}", requestModel.getPairName());
            keyPairService.createKeyPair(requestModel);
            response.info("Successfully added Key Pair " + requestModel.getPairName());
        } catch (Exception e) {
            response.error("Failed to create a Key Pair: " + e.getMessage());
        }
    }
}