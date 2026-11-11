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

package org.artifactory.rest.resource.versions;

import org.apache.http.HttpStatus;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * @author Gidi Shabat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(VersionsResources.PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
@ActivityDetails(group = RestApiGroup.SEARCH_API)
public class VersionsResources {

    public static final String PATH_ROOT = "versions";

    @Context
    private HttpServletRequest request;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private CentralConfigService centralConfig;
    @Autowired
    private SecurityService securityService;

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("{repoKey: _any}/{path: (?!_any).+}")
    public Response getLatestVersionByPath(@PathParam("repoKey") String repoKey, @PathParam("path") String path) {
        return getLatestVersionInternal(null, path);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("{repoKey: (?!_any)[^/]+}/{path: _any}")
    public Response getLatestVersionByRepo(@PathParam("repoKey") String repoKey, @PathParam("path") String path) {
        return getLatestVersionInternal(repoKey, null);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("{repoKey: _any}/{path: _any}")
    public Response getLatestVersion(@PathParam("repoKey") String repoKey, @PathParam("path") String path) {
        return getLatestVersionInternal(null, null);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("{repoKey: (?!_any)[^/]+}/{path: (?!_any).+}")
    public Response getLatestVersionByRepoAndPath(@PathParam("repoKey") String repoKey,
            @PathParam("path") String path) {
        return getLatestVersionInternal(repoKey, path);
    }

    private Response getLatestVersionInternal(String repoKey, String path) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (securityService.isAuthenticated() && !securityService.isAnonymous()) {
            return addonsManager.addonByType(RestAddon.class).getLatestVersionByProperties(repoKey, path, parameterMap,
                    request);
        } else {
            if (centralConfig.getDescriptor().getSecurity().isHideUnauthorizedResources()) {
                return Response.status(HttpStatus.SC_NOT_FOUND).build();
            }
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }
}
