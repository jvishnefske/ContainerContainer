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

package org.artifactory.ui.rest.service.utils.repositories;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.service.utils.repositories.repodata.GetRepositoriesDataHandler;
import org.artifactory.ui.rest.service.utils.repositories.repodata.RepositoriesDataHandlerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@AllArgsConstructor
@Slf4j
@Component
public class GetAllRepositoriesService<T> implements RestService<T> {
    private RepositoriesDataHandlerFactory<T> repositoriesDataHandlerFactory;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        String queryParam = getRequestQueryParam(request);
        GetRepositoriesDataHandler<T> getRepositoriesDataHandler =
                repositoriesDataHandlerFactory.getHandler(queryParam);
        getRepositoriesDataHandler.handle(request, response);
    }

    public String getRequestQueryParam(ArtifactoryRestRequest<T> request) {
        Set<String> validQueryParams = repositoriesDataHandlerFactory.getHandledQueryParams();
        return request.getUriInfo().getQueryParameters().keySet().stream()
                .filter(validQueryParams::contains)
                .findFirst()
                .orElse("");
    }
}
