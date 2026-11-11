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

package org.artifactory.ui.rest.service.utils.repoPropertySet;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.PropertiesArtifactInfo;
import org.artifactory.ui.utils.PropertySetsConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
public class GetRepoPropertySetService implements RestService {

    @Autowired
    private RepositoryService repoService;
    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String path = request.getQueryParamByKey("path");
        String repoKey = request.getQueryParamByKey("repoKey");
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
        if (repoService.isVirtualRepoExist(repoKey)) {
            repoPath = repoService.getVirtualItemInfo(repoPath).getRepoPath();
        }
        if (!authorizationService.canRead(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        List<? extends RestModel> propertyItemList = createPropertyItemMap(repoPath);
        response.iModelList(propertyItemList);
    }

    /**
     * Prepares a list of Property Items to add to the property drop-down component
     *
     * @return list of properties artifact info
     */
    private List<PropertiesArtifactInfo> createPropertyItemMap(RepoPath repoPath) {
        LocalRepoDescriptor descriptor = repoService
                .localFederatedCachedRepoDescriptorByKey(repoPath.getRepoKey());
        if (descriptor == null) {
            log.debug("No local repository descriptor found for repo key: {}", repoPath.getRepoKey());
            throw new NotFoundException("Repository not found: " + repoPath.getRepoKey());
        }
        List<PropertySet> propertySets = new ArrayList<>(descriptor.getPropertySets());
        return PropertySetsConverter.toPropertiesArtifactInfoList(propertySets);
    }
}
