/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
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

package org.artifactory.rest.resource.search.types;

import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.api.BuildArtifactsRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.rest.constant.BuildRestConstants;
import org.artifactory.api.rest.constant.SearchRestConstants;
import org.artifactory.api.rest.search.result.DownloadRestSearchResult;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.exception.AuthorizationRestException;

import java.io.IOException;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.rest.common.util.RestUtils.buildDownloadUri;

/**
 * Resource for retrieving build artifacts according to input regexp patterns.
 *
 * @author Shay Yaakov
 * @see BuildArtifactsRequest
 */
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
public class BuildArtifactsSearchResource {

    private final RestAddon restAddon;
    private SecurityService securityService;
    private HttpServletRequest request;

    public BuildArtifactsSearchResource(RestAddon restAddon, SecurityService securityService,
            HttpServletRequest request) {
        this.restAddon = restAddon;
        this.securityService = securityService;
        this.request = request;
    }

    @POST
    @Consumes({BuildRestConstants.MT_BUILD_ARTIFACTS_REQUEST, MediaType.APPLICATION_JSON})
    @Produces({SearchRestConstants.MT_BUILD_ARTIFACTS_SEARCH_RESULT, MediaType.APPLICATION_JSON})
    public Response get(BuildArtifactsRequest buildArtifactsRequest) throws IOException {
        if (isBlank(buildArtifactsRequest.getBuildName())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Cannot search without build name.").build();
        }
        boolean buildNumberIsBlank = isBlank(buildArtifactsRequest.getBuildNumber());
        boolean buildStatusIsBlank = isBlank(buildArtifactsRequest.getBuildStatus());
        if (buildNumberIsBlank && buildStatusIsBlank) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                    "Cannot search without build number or build status.").build();
        }
        if (!buildNumberIsBlank && !buildStatusIsBlank) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                    "Cannot search with both build number and build status parameters, " +
                            "please omit build number if your are looking for latest build by status " +
                            "or omit build status to search for specific build version.").build();
        }

        if (!securityService.isAuthenticated()) {
            throw new AuthorizationRestException();
        }

        Map<BuildFileInfo, String> buildArtifacts;
        try {
            buildArtifacts = restAddon.getBuildArtifacts(buildArtifactsRequest);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getLocalizedMessage()).build();
        }
        if (buildArtifacts == null || buildArtifacts.isEmpty()) {
            //In this stage, only one of buildNumberIsBlank or buildNumberIsBlank can be false.
            String noArtifactsFound = String.format("Could not find any build artifacts for build '%s'",
                    buildArtifactsRequest.getBuildName());
            if (!buildNumberIsBlank) {
                noArtifactsFound += String.format(" number '%s'", buildArtifactsRequest.getBuildNumber());
            } else if (!buildStatusIsBlank) {
                noArtifactsFound += String.format(" status '%s'", buildArtifactsRequest.getBuildStatus());
            }
            throw new NotFoundException(noArtifactsFound);
        }

        DownloadRestSearchResult downloadRestSearchResult = new DownloadRestSearchResult();
        for (BuildFileInfo fileInfo : buildArtifacts.keySet()) {
            String downloadUri = buildDownloadUri(request, fileInfo.getRepoKey(), fileInfo.getRelativePath());
            downloadRestSearchResult.results.add(new DownloadRestSearchResult.SearchEntry(downloadUri));
        }

        return Response.ok(downloadRestSearchResult).build();
    }
}
