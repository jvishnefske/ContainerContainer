/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.MirrorMemberConfigModel;
import org.artifactory.util.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;

/**
 * Validates connectivity and repository names of remote mirror belonging to a federated repository
 *
 * @author Andreik
 */
@Component
public class ValidateFederatedRepoMirrorsService implements RestService<FederationConfigModel> {

    static final String SUCCESS = "Validated repository mirror successfully";
    static final String FAILURE = "Validation of federated repository failed: ";

    @Override
    public void execute(ArtifactoryRestRequest<FederationConfigModel> request, RestResponse response) {
        FederationConfigModel imodel = request.getImodel();
        FederatedRepoService federatedRepoService = ContextHelper.get().beanForType(FederatedRepoService.class);
        try {
            List<MirrorMemberConfigModel> mirrors = imodel.getMirrors();
            String sourceRepoKey = imodel.getSourceRepo();
            String sourceRepoType = imodel.getSourceRepoType();

            if (CollectionUtils.isNullOrEmpty(mirrors)) {
                throw new BadRequestException("No mirrors were provided");
            }
            federatedRepoService.validateRemoteServer(mirrors.getFirst().getUrl(),
                    imodel.getProxy(),
                    sourceRepoType,
                    sourceRepoKey
            );
            response.info(SUCCESS).responseCode(SC_OK);
        } catch (BadRequestException e) {
            response.error(e.getMessage()).responseCode(SC_BAD_REQUEST);
        } catch (ForbiddenException e) {
            response.error(e.getMessage()).responseCode(SC_FORBIDDEN);
        }
    }
}
