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

package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.repo.Repo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.keys.KeyPairIModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Yuval Reches
 */
@Component
public class GetPublicKeyFilenamePerRepoService implements RestService {

    private final InternalRepositoryService repositoryService;

    @Autowired
    public GetPublicKeyFilenamePerRepoService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoKey = request.getPathParamByKey("repoKey");
        if (repoKey == null) {
            response.error("No repo param found");
            return;
        }
        Repo repo = repositoryService.repositoryByKey(repoKey);
        if (repo == null) {
            response.error("No such repo '" + repoKey + "'");
            return;
        }
        KeyPairDescriptor keyPair = repo.getDescriptor().getPrimaryKeyPairDescriptor();
        if (keyPair == null) {
            response.error("No key pair found for repo '" + repoKey + "'");
            return;
        }
        response.iModel(createKeyPairIModel(keyPair));
    }

    private KeyPairIModel createKeyPairIModel(KeyPairDescriptor keyPair) {
        KeyPairIModel keyPairIModel = new KeyPairIModel();
        keyPairIModel.setAlias(keyPair.getAliasWithExtension());
        return keyPairIModel;
    }

}