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
import org.artifactory.addon.xray.ArtifactXrayInfo;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.model.xray.XrayArtifactInfo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

import static org.artifactory.rest.common.model.xray.ArtifactXrayModelHelper.getXrayInfo;

/**
 * Service for fetching the Xray metadata for the UI
 *
 * @author Yuval Reches
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetXrayTabService implements RestService {

    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private AddonsManager addonsManager;

    private XrayAddon xrayAddon;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        xrayAddon = addonsManager.addonByType(XrayAddon.class);
        RepoPath repoPath = RequestUtils.getPathFromRequest(request);

        String errorMsg;
        if (!authorizationService.canRead(repoPath)) {
            errorMsg = "Forbidden UI REST call from user: '" + securityService.currentUsername() + "'";
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).error(errorMsg).buildResponse();
            return;
        } else {
            if (shouldBlock(repoPath)) {
                errorMsg = "Forbidden UI REST: Xray is not configured on the repo '" + repoPath.getRepoKey() +
                        "' or file '" + repoPath.getPath() + "' is not handled by Xray";
                response.responseCode(HttpServletResponse.SC_BAD_REQUEST).error(errorMsg).buildResponse();
                log.error(errorMsg);
                return;
            }
        }

        // Retrieving info from Xray client
        ArtifactXrayInfo artifactXrayInfo;
        try {
            artifactXrayInfo = xrayAddon.getArtifactXrayInfo(repoPath);
        } catch (IllegalStateException e) {
            response.error(e.getMessage()).buildResponse();
            return;
        }
        if (artifactXrayInfo.getIndexStatus() == null) {
            response.error("Xray is not available").buildResponse();
            return;
        }
        XrayArtifactInfo xrayArtifactInfo = getXrayInfo(artifactXrayInfo);
        updateAllowBlockedArtifacts(xrayArtifactInfo);
        // update response
        response.iModel(xrayArtifactInfo);
    }

    private boolean shouldBlock(RepoPath repoPath) {
        // If using newer Xray version, always return the info. Otherwise, use the old logic.
        String scanVisibilityMinXrayVersion = ConstantValues.treeNodeTabsScanVisibilityMinXrayVersion.getString();
        return !(xrayAddon.isXrayEnabled() && xrayAddon.isXrayOfMinimalRequiredVersion(scanVisibilityMinXrayVersion)) &&
                (!xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(repoPath.getRepoKey()) ||
                        !xrayAddon.isHandledByXray(repoPath));
    }

    private void updateAllowBlockedArtifacts(XrayArtifactInfo xrayArtifactInfo) {
        if (xrayArtifactInfo.getXrayBlocked()) {
            xrayArtifactInfo.setAllowBlockedArtifacts(xrayAddon.isAllowBlockedArtifactsDownload());
        }
    }
}
