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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.ViewArtifact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
public class ViewArtifactService implements RestService {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        ViewArtifact viewArtifact = (ViewArtifact) request.getImodel();
        RepoPath repoPath = RepoPathFactory.create(viewArtifact.getRepoKey(), viewArtifact.getPath());
        if (repositoryService.isVirtualRepoExist(viewArtifact.getRepoKey())) {
            repoPath = repositoryService.getVirtualFileInfo(repoPath).getRepoPath();
        }
        // read permission checks
        if (!authorizationService.canRead(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }

        // get file info
        ItemInfo itemInfo = getItemInfo(repoPath);

        try {
            /// get file content
            String fileContent = getFileContent((FileInfo) itemInfo);
            // update response with file content
            updateResponse(response, fileContent);
        } catch (ClassCastException e) {
            log.warn("Cannot view content of a folder: {}", itemInfo);
            response.responseCode(HttpServletResponse.SC_BAD_REQUEST)
                    .error("Cannot view content of a folder. Please select a file.");
        } catch (Exception e) {
            response.error("Failed to get file content: " + e.getMessage())
                    .responseCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * get item to be reviewed info
     *
     * @return - item info instance
     */
    private ItemInfo getItemInfo(RepoPath repoPath) {
        return repositoryService.getItemInfo(repoPath);
    }

    /**
     * update file content response
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param fileContent         - file content
     */
    private void updateResponse(RestResponse artifactoryResponse, String fileContent) {
        RestModel artifact = new ViewArtifact();
        ((ViewArtifact) artifact).setFileContent(fileContent);
        artifactoryResponse.iModel(artifact);
    }

    /**
     * get file content by file type
     *
     * @param fileInfo - file item to be reviewed
     * @return - file content
     */
    private String getFileContent(org.artifactory.fs.FileInfo fileInfo) {
        return repositoryService.getStringContent(fileInfo);
    }
}
