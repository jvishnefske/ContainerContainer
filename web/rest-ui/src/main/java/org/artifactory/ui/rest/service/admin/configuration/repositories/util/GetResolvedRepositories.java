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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.model.RealRepoConfig;
import org.artifactory.repo.virtual.VirtualRepositoryConfigResolver;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Get all resolved repositories for the current virtual repository
 *
 * @author Aviad Shikloshi
 */
@Component
public class GetResolvedRepositories<T extends VirtualRepositoryConfigModel> implements RestService<T> {

    @Autowired
    private RepoConfigValidator repoValidator;
    @Autowired
    private VirtualRepositoryConfigResolver resolver;


    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        VirtualRepositoryConfigModel virtualModel = request.getImodel();
        VirtualBasicRepositoryConfigModel basic = virtualModel.getBasic();
        try {
            List<VirtualSelectedRepository> selectedRepositories = basic.getSelectedRepositories();
            repoValidator
                    .validateSelectedReposInVirtualExist(selectedRepositories);
            List<RealRepoConfig> orderedRepos = resolver.getOrderedRepos(virtualModel.getGeneral().getRepoKey());
            selectedRepositories =
                    orderedRepos.stream().map(VirtualSelectedRepository::new).collect(Collectors.toList());
            response.iModelList(selectedRepositories);
        } catch (RepoConfigException e) {
            response.error(e.getMessage()).responseCode(e.getStatusCode());
        }
    }
}
