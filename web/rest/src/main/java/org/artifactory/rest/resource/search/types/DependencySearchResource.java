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

package org.artifactory.rest.resource.search.types;

import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.SearchRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.exception.http.BadRequestException;

import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;

import static org.artifactory.projects.ProjectsService.PROJECT;

/**
 * @author Noam Y. Tenne
 */
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
public class DependencySearchResource {

    private static final String BUILD_REPO = "buildRepo";

    private RestAddon restAddon;
    private HttpServletRequest request;

    public DependencySearchResource(RestAddon restAddon, HttpServletRequest request) {
        this.restAddon = restAddon;
        this.request = request;
    }

    @GET
    @Produces({SearchRestConstants.MT_DEPENDENCY_BUILDS, MediaType.APPLICATION_JSON})
    public Response get(
            @QueryParam(SearchRestConstants.PARAM_SHA1_CHECKSUM) String sha1,
            @QueryParam(SearchRestConstants.PARAM_SHA2_CHECKSUM) String sha2,
            @QueryParam(BUILD_REPO) @Nullable String buildRepo,
            @QueryParam(PROJECT) @Nullable String projectKey) throws IOException {
        try {
            BuildFacade buildFacade =
                    ContextHelper.get().beanForType(BuildFacade.class);
            return restAddon.searchDependencyBuilds(request, sha1, sha2,
                    buildFacade.getPreferredBuildRepo(buildRepo, projectKey));
        } catch (IllegalArgumentException iae) {
            throw new BadRequestException(iae.getMessage());
        }
    }
}
