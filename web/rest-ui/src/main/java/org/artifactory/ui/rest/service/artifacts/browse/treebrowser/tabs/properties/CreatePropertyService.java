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

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.exception.CancelException;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.exception.ValidationException;
import org.artifactory.properties.validation.PropertyNameValidator;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.storage.EmojiNotAllowedInDbException;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.PropertiesArtifactInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Chen Keinan
 */
@Component
@Slf4j
public class CreatePropertyService extends BasePropertyService<PropertiesArtifactInfo> {

    @Autowired
    public CreatePropertyService(InternalRepositoryService repoService, PropertiesService propsService,
            AuthorizationService authorizationService, SecurityService securityService,
            MetadataServiceIndexHandler metadataServiceIndexHandler) {
        super(repoService, authorizationService, securityService, propsService, metadataServiceIndexHandler);
    }

    @Override
    protected void doExecute(ArtifactoryRestRequest<PropertiesArtifactInfo> request, RestResponse response,
            RepoPath repoPath) {
        PropertiesArtifactInfo propertiesTab = request.getImodel();
        if (propertiesTab.getProperty() == null) {
            response.error("Cannot create an empty property");
            return;
        }
        try {
            PropertyNameValidator.validate(propertiesTab.getProperty().getName());
        } catch (ValidationException e) {
            throw new BadRequestException(e.getMessage());
        }
        boolean recursive = Boolean.parseBoolean(request.getQueryParamByKey("recursive"));
        savePropertyAndUpdateResponse(response, propertiesTab, repoPath, recursive);
    }

    /**
     * save property and update response
     *
     * @param artifactoryResponse - encapsulate artifactory response data
     * @param propertiesTab       - properties tab data
     * @param repoPath            - repo path
     */
    void savePropertyAndUpdateResponse(RestResponse<?> artifactoryResponse, PropertiesArtifactInfo propertiesTab,
            RepoPath repoPath, boolean recursive) {
        try {
            if (recursive) {
                propsService.addPropertyRecursively(repoPath, propertiesTab.getParent(), propertiesTab.getProperty(),
                        true, propertiesTab.getSelectedValues());
            } else {
                propsService.addProperty(repoPath, propertiesTab.getParent(), propertiesTab.getProperty(),
                        true, propertiesTab.getSelectedValues());
            }
            metadataServiceIndexHandler.notifyPropertyChange(repoPath, propsService.getProperties(repoPath));
            artifactoryResponse.info("Successfully created property '" + propertiesTab.getProperty().getName() + "'");
            artifactoryResponse.responseCode(HttpServletResponse.SC_CREATED);
        } catch (CancelException e) {
            log.error("Failed to create property:" + propertiesTab.getProperty().getName());
            artifactoryResponse.error("Failed to created property '" + propertiesTab.getProperty().getName() + "'");
        } catch (EmojiNotAllowedInDbException e) {
            log.warn("", e);
            artifactoryResponse.error(e.getMessage());
            artifactoryResponse.responseCode(422);
        }
    }
}
