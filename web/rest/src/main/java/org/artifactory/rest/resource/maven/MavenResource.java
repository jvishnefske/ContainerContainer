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

package org.artifactory.rest.resource.maven;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.maven.MavenMetadataWorkItem;
import org.artifactory.api.maven.MavenService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.MavenRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.RepoPath;
import org.jfrog.common.StringList;
import org.springframework.stereotype.Component;

import static org.artifactory.api.rest.constant.MavenRestConstants.CALCULATE_METADATA;
import static org.artifactory.api.rest.constant.MavenRestConstants.GENERATE_POM;
import static org.artifactory.rest.common.util.RestUtils.calcRepoPathFromRequestPath;

/**
 * A resource for manually running maven indexer
 *
 * @author Shay Yaakov
 */
@Component
@Path(MavenRestConstants.PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(PackageType.MAVEN)
public class MavenResource {

    private final AddonsManager addonsManager;
    private final RepositoryService repositoryService;
    private final MavenMetadataService mavenMetadataService;
    private final MavenService mavenService;
    private final AuthorizationService authorizationService;

    public MavenResource(AddonsManager addonsManager, RepositoryService repositoryService,
            MavenMetadataService mavenMetadataService, MavenService mavenService,
            AuthorizationService authorizationService) {
        this.addonsManager = addonsManager;
        this.repositoryService = repositoryService;
        this.mavenMetadataService = mavenMetadataService;
        this.mavenService = mavenService;
        this.authorizationService = authorizationService;
    }

    @POST
    @Produces({MediaType.TEXT_PLAIN})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response runIndexer(@QueryParam(MavenRestConstants.PARAM_REPOS_TO_INDEX) StringList reposToIndex,
            @QueryParam(MavenRestConstants.PARAM_FORCE) int force) {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return restAddon.runMavenIndexer(reposToIndex, force);
    }

    @POST
    @Produces({MediaType.TEXT_PLAIN})
    @Path(GENERATE_POM + "/{repoKey}/{pathInRepo: .+}")
    public Response generatePom(@PathParam("repoKey") String repoKey, @PathParam("pathInRepo") String pathInRepo) {
        mavenService.generateAndDeployPom(repoKey, pathInRepo);
        return Response.status(Response.Status.OK).build();
    }

    @POST
    @Path(CALCULATE_METADATA + "/{path: .+}")
    public Response calculateMetadata(@PathParam("path") String path,
            @QueryParam(MavenRestConstants.PARAM_RECURSIVE) boolean noneRecursive) {
        RepoPath repoPath = calcRepoPathFromRequestPath(path);
        if (!repositoryService.exists(repoPath)) {
            return Response.status(Response.Status.NOT_FOUND).entity("Could not find path '" + path + "'").build();
        }

        if (!authorizationService.canDeploy(repoPath)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        String repoKey = repoPath.getRepoKey();
        LocalConfig localRepo = repositoryService.localOrFederatedRepoConfigByKey(repoKey);
        if (localRepo == null) {
            return noSuchLocalRepoResponse(repoKey);
        }

        if (!localRepo.getPackageType().isMavenGroup()) {
            return notMavenRepoResponse(repoKey);
        }

        mavenMetadataService.calculateMavenMetadataAsync(
                new MavenMetadataWorkItem(repoPath, !noneRecursive, true, true));
        return Response.ok().build();
    }

    private Response notMavenRepoResponse(String repoKey) {
        return Response.status(Response.Status.BAD_REQUEST).entity(
                "Repository '" + repoKey + "' is not a maven repository.").build();
    }

    private Response noSuchLocalRepoResponse(String repoKey) {
        return Response.status(Response.Status.NOT_FOUND).entity(
                "Unable to find local repository '" + repoKey + "'.")
                .build();
    }
}
