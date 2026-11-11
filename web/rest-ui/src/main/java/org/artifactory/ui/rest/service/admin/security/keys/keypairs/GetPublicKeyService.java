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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * @author Omri Ziv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetPublicKeyService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetPublicKeyService.class);

    private final KeyPairService keyPairService;

    @Autowired
    public GetPublicKeyService(KeyPairService keyPairService) {
        this.keyPairService = keyPairService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String entityKey = request.getPathParamByKey("keyPair");
        log.debug("Get Key Pair request for {}", entityKey);
        KeyPairDescriptor keyPair = keyPairService.getKeyPairByName(entityKey);
        if (Objects.isNull(keyPair)) {
            response.error("No such key pair '" + entityKey + "'");
            return;
        }
        if (StringUtils.isBlank(keyPair.getPublicKey())) {
            response.error("Key " + entityKey + " Does not contains public key");
            return;
        }
        response.responseCode(HttpStatus.SC_OK);
        response.getServletResponse()
                .addHeader("Content-Disposition",
                        "attachment; filename=" + keyPair.getAliasWithExtension());
        response.iModel(keyPair.getPublicKey());
    }
}