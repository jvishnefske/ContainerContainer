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

package org.artifactory.rest.resource.artifact;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import static org.artifactory.api.rest.constant.ArtifactRestConstants.PATH_PARAM;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.ZAP_ROOT;
import static org.artifactory.rest.common.util.RestUtils.calcRepoPathFromRequestPath;

/**
 * @author Omri Ziv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(ZAP_ROOT + "/{" + PATH_PARAM + ": .+}")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.ARTIFACT_API)
public class ZapArtifactsResource {

    @Autowired
    private InternalRepositoryService repositoryService;

    @PathParam(PATH_PARAM)
    String path;

    @POST
    public Response zap() {
        RepoPath repoPath = calcRepoPathFromRequestPath(path);
        repositoryService.zap(repoPath);
        return Response.ok("Completed zapping cache in path " + repoPath).build();
    }

}
