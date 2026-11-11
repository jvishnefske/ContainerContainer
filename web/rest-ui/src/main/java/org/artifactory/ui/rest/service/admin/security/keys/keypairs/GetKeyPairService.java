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
import org.artifactory.api.jackson.JacksonWriter;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.keys.KeyPairsUtil;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Yuval Reches
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetKeyPairService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetKeyPairService.class);

    private final KeyPairService keyPairService;

    @Autowired
    public GetKeyPairService(KeyPairService keyPairService) {
        this.keyPairService = keyPairService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String entityKey = request.getPathParamByKey("keyPair");
        log.debug("Get Key Pair request for {}", entityKey);
        KeyPairDescriptor keyPair = keyPairService.getKeyPairByName(entityKey);
        if (keyPair == null) {
            response.error("No such key pair '" + entityKey + "'");
            return;
        }
        try {
            response.iModel(JacksonWriter.serialize(KeyPairsUtil.toResponse(keyPair)));
        } catch (IOException e) {
            log.error("Error occurred while getting Key Pair {}: {}", entityKey, e.getMessage());
            log.debug("Error occurred while getting Key Pair {}", entityKey, e);
            response.error("Error occurred while getting Key Pair " + entityKey);
        }
    }

}