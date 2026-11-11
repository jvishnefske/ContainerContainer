/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.signature.SignedUrlService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DownloadArtifact;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Returns a signed url for the current user authenticated per the path requested.
 *
 * NOTE: this component is undocumented, and should be restricted in the future - should not be available for all users!
 *
 * @author Yuval Reches
 */
@Slf4j
@Component
public class DownloadArtifactWithSignedUrlService implements RestService<DownloadArtifact> {

    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    SignedUrlService signedUrlService;

    @Override
    public void execute(ArtifactoryRestRequest<DownloadArtifact> request, RestResponse response) {
        DownloadArtifact downloadArtifact = request.getImodel();
        RepoPath repoPath = RepoPathFactory.create(downloadArtifact.getRepoKey(), downloadArtifact.getPath());

        String errorMsg;
        if (!authorizationService.canRead(repoPath)) {
            errorMsg = "Forbidden UI REST call from user: '" + securityService.currentUsername() + "'";
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).error(errorMsg).buildResponse();
            return;
        }

        String contextUrl = HttpUtils.getServletContextUrl(request.getServletRequest());
        RestModel artifactDownloadModel = getArtifactDownloadModel(contextUrl, repoPath);
        response.responseCode(HttpStatus.SC_CREATED);
        response.iModel(artifactDownloadModel);
    }

    /**
     * Build signed url and update download model
     */
    private RestModel getArtifactDownloadModel(String contextUrl, RepoPath repoPath) {
        DownloadArtifact downloadArtifact = new DownloadArtifact();
        String downloadPath = signedUrlService.createSignedUrl(contextUrl, repoPath.toPath(),
                ConstantValues.signedUrlUiDownloadValidForSeconds.getLong(), true);
        downloadArtifact.setDownloadPath(downloadPath);
        return downloadArtifact;
    }

}
