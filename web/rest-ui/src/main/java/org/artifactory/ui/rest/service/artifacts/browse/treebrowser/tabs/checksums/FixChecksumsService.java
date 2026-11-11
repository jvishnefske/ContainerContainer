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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.checksums;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Dan Feldman
 */
@Slf4j
@Component
public class FixChecksumsService implements RestService {

    @Autowired
    private RepositoryService repoService;
    @Autowired
    private AuthorizationService authService;
    @Autowired
    private SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        BaseArtifactInfo artifactInfo = (BaseArtifactInfo) request.getImodel();
        RepoPath path = RepoPathFactory.create(artifactInfo.getRepoKey(), artifactInfo.getPath());
        if (repoService.isVirtualRepoExist(path.getRepoKey())) {
            path = repoService.getVirtualFileInfo(path).getRepoPath();
        }
        if (!authService.canDeploy(path) || securityService.isAnonymous()) {
            log.debug("The user: '{}' attempting to fix checksums on path {} has insufficient rights",
                    securityService.currentUsername(), path);
            response.error("Insufficient rights for operation").responseCode(HttpStatus.SC_UNAUTHORIZED);
        } else {
            try {
                log.debug("Fixing checksums for {}", path);
                repoService.fixChecksums(path);
                response.info("Successfully fixed checksum inconsistency");
            } catch (Exception e) {
                response.error("Could not fix checksum inconsistency");
                log.debug("", e);
            }
        }
    }
}
