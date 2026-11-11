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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.debianview;

import com.jfrog.ph.acl.service.PackageRepoService;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.debian.DebianAddon;
import org.artifactory.addon.debian.DebianMetadataInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.util.PermissionHelper;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.debian.DebianArtifactMetadataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Service for fetching the Debian\Opkg metadata for the Info tab in the UI
 *
 * @author Yuval Reches
 */
@Slf4j
@Component
public class DebianViewService implements RestService<DebianArtifactMetadataInfo> {

    private final AuthorizationService authorizationService;
    private final SecurityService securityService;
    private final PackageRepoService repoService;
    private final PermissionHelper permissionHelper;

    @Autowired
    public DebianViewService(AuthorizationService authorizationService, SecurityService securityService,
            PackageRepoService repoService, PermissionHelper permissionHelper) {
        this.authorizationService = authorizationService;
        this.securityService = securityService;
        this.repoService = repoService;
        this.permissionHelper = permissionHelper;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        fetchDebianMetaData(request, response);
    }

    /**
     * fetch debian metadata
     *
     * @param artifactoryRequest  - encapsulate data relate to request
     * @param artifactoryResponse - encapsulate data require for response
     */
    private void fetchDebianMetaData(ArtifactoryRestRequest<DebianArtifactMetadataInfo> artifactoryRequest,
            RestResponse<DebianArtifactMetadataInfo> artifactoryResponse) {
        DebianArtifactMetadataInfo debianArtifactInfo = artifactoryRequest.getImodel();
        String repoKey = debianArtifactInfo.getRepoKey();
        String path = debianArtifactInfo.getPath();
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
        if (repoService.isVirtual(repoKey)) {
            permissionHelper.assertPermissionOnOneRepo(repoKey, ArtifactoryPermission.READ);
        } else if (!authorizationService.canRead(repoPath)) {
            artifactoryResponse.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        // Get debian addon
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        DebianAddon debianAddon = addonsManager.addonByType(DebianAddon.class);
        if (debianAddon != null) {
            String lowercasePath = path.toLowerCase();

            if (lowercasePath.endsWith(".deb") || lowercasePath.endsWith(".ipk")) {
                // Get debian metadata
                DebianMetadataInfo debianMetadataInfo = debianAddon.getDebianMetadataInfo(repoPath);
                debianArtifactInfo.setDebianInfo(debianMetadataInfo.getDebianInfo());
                debianArtifactInfo.setDebianDependencies(debianMetadataInfo.getDebianDependencies());
                debianArtifactInfo.setIndexFailureReason(debianMetadataInfo.getFailReason());
                debianArtifactInfo.clearRepoData();
                artifactoryResponse.iModel(debianArtifactInfo);
            } else if (lowercasePath.endsWith(".dsc")) {
                DebianMetadataInfo debianMetadataInfo = debianAddon.getDebianSourceMetadataInfo(repoPath, true);
                debianArtifactInfo.setDscInfo(debianMetadataInfo.getDscInfo());
                debianArtifactInfo.clearRepoData();
                artifactoryResponse.iModel(debianArtifactInfo);
            } else {
                log.warn("Unsupported Debian file type: {} for repo: {}", path, repoKey);
            }
        }
    }
}
