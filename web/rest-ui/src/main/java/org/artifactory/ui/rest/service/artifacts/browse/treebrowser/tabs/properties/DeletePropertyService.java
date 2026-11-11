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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.DeletePropertyModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.PropertyWithPath;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties.BasePropertyService.verifyRepoSupportsPropertyModification;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeletePropertyService<T extends DeletePropertyModel> implements RestService<T> {

    private final InternalRepositoryService repoService;
    private final PropertiesService propsService;
    private final AuthorizationService authorizationService;
    private final SecurityService securityService;
    private final MetadataServiceIndexHandler metadataServiceIndexHandler;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        T model = request.getImodel();
        int errors = 0;
        Set<RepoPath> modifiedPaths = new LinkedHashSet<>();
        for (PropertyWithPath propertyWithPath : model.getProperties()) {
            String name = propertyWithPath.getName();

            RepoPath path = InternalRepoPathFactory.create(propertyWithPath.getRepoKey(), propertyWithPath.getPath());
            boolean recursive = propertyWithPath.isRecursive();
            if (!authorizationService.canAnnotate(path)) {
                response.warn("Unable to remove properties from '" + path +
                        "' - user does not have annotate permissions on given path");
                errors++;
            } else if (deletePropertyAndUpdateResponse(response, name, path, recursive)) {
                if (!recursive) {
                    modifiedPaths.add(path);
                }
            }
        }
        for (RepoPath path : modifiedPaths) {
            metadataServiceIndexHandler.notifyPropertyChange(path, propsService.getProperties(path));
        }
        int numProperties = model.getProperties().size();
        if (errors < numProperties) {
            if (numProperties > 1) {
                response.info("Successfully removed " + (numProperties - errors) + " properties");
            } else if (numProperties == 1) {
                response.info("Successfully removed property '" + model.getProperties().get(0).getName() + "'");
            }
        }
        if (errors > 0) {
            log.warn("Forbidden UI REST call from user: '{}'", securityService.currentUsername());
        }
    }

    /**
     * delete property from DB and update response feedback
     *
     * @param artifactoryResponse - encapsulate data require to response
     * @param name                - property name
     * @param path                - repo path
     * @param recursive           - if true - delete recursively
     * @return true if the property was successfully deleted
     */
    private boolean deletePropertyAndUpdateResponse(RestResponse artifactoryResponse, String name, RepoPath path,
            boolean recursive) {
        verifyRepoSupportsPropertyModification(path, repoService);
        try {
            if (recursive) {
                propsService.deletePropertyRecursively(path, name, true);
            } else {
                propsService.deleteProperty(path, name, true);
            }
            return true;
        } catch (Exception e) {
            log.error("problem with deleting property:" + name);
            artifactoryResponse.error("property " + name + " failed to deleted");
            return false;
        }
    }
}
