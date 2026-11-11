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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.xray;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

import static org.artifactory.repo.db.DbLocalRepo.DOWNLOAD_BLOCK_REASON_MESSAGE;

/**
 * Service for checking if Xray is blocking an artifact for the UI Download button
 *
 * @author Yuval Reches
 */
@Slf4j
@Component
public class IsArtifactBlockedService implements RestService {

    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        RepoPath repoPath = RequestUtils.getPathFromRequest(request);

        String errorMsg;
        if (!authorizationService.canRead(repoPath)) {
            errorMsg = "Forbidden UI REST call from user: '" + securityService.currentUsername() + "'";
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).error(errorMsg).buildResponse();
            return;
        }

        // Retrieving info from Xray client
        boolean downloadBlocked = xrayAddon.isDownloadBlocked(repoPath);
        if (downloadBlocked) {
            String message = "The artifact is blocked due to " + DOWNLOAD_BLOCK_REASON_MESSAGE + " for " +
                    repoPath.getRepoKey() + ".";
            if (!xrayAddon.isXrayAliveByHeartbeat()) {
                message += " Xray is unavailable.";
            }
            response.error(message).responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
        }
    }
}
