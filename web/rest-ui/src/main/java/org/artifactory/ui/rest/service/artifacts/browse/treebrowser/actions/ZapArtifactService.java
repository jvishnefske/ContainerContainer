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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.ZapArtifact;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZapArtifactService implements RestService<ZapArtifact> {

    private final RepositoryService repositoryService;
    private final AuthorizationService authorizationService;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest<ZapArtifact> request, RestResponse response) {
        ZapArtifact zapArtifact = request.getImodel();
        String repoKey = zapArtifact.getRepoKey();
        String path = zapArtifact.getPath();
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
        try {
            repositoryService.zap(repoPath);
        } catch (ForbiddenException e) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        response.info("Completed zapping cache in path " + repoPath);
    }
}
