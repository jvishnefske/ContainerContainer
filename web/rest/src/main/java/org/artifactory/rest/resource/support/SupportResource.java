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

package org.artifactory.rest.resource.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.support.SupportAddon;
import org.artifactory.api.request.JerseyArtifactoryResponse;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.download.FolderDownloadException;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.artifactory.support.config.bundle.BundleConfigurationImpl;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.api.rest.constant.ArtifactRestConstants.SUPPORT_BUNDLES_PATH;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.SUPPORT_ROOT;

/**
 * Legacy resource, not to be used.
 *
 * Provides support content generation services
 *
 * @author Michael Pasternak
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(SUPPORT_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class SupportResource {

    @Autowired
    private SecurityService securityService;
    @Autowired
    private AddonsManager addonsManager;
    @Context
    private HttpServletRequest httpServletRequest;

    @POST
    @Path(SUPPORT_BUNDLES_PATH)
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateBundle(BundleConfigurationImpl bundleConfiguration) {
        log.debug("Producing support bundle according to configuration: {}", bundleConfiguration);
        if (securityService.isAnonymous() || !securityService.isAdmin()) {
            return unauthorized();
        }
        String response = addonsManager.addonByType(SupportAddon.class)
                .generate(bundleConfiguration, HttpUtils.getServletContextUrl(httpServletRequest));
        if (StringUtils.isNotEmpty(response)) {
            return Response.ok().entity(response).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Support content collection has failed, see 'support.log' for more details")
                .build();
    }

    @GET
    @RolesAllowed({HaRestConstants.ROLE_HA, AuthorizationService.ROLE_ADMIN})
    @Path(SUPPORT_BUNDLES_PATH + "/{archive: .+}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadBundle(@PathParam(value = "archive") String archive,
            @QueryParam("node") String handlingNode) {
        if (securityService.isAnonymous() || !securityService.isAdmin()) {
            return unauthorized();
        }

        log.debug("Downloading bundle: {}", archive);
        JerseyArtifactoryResponse res = new JerseyArtifactoryResponse();
        res.setStatus(HttpStatus.SC_OK);
        Response response;
        try {
            SupportAddon supportAddon = addonsManager.addonByType(SupportAddon.class);
            res.sendStreamWithDelegation(supportAddon.downloadByBundleName(archive));
            response = res.build();
        } catch (FolderDownloadException fde) {
            log.debug("Failed to send stream to client: ", fde);
            response = Response.status(fde.getCode()).entity("Failed to download bundle: " + fde.getMessage()).build();
        } catch (Exception e) {
            log.debug("Failed to send stream to client: ", e);
            response = Response.status(HttpStatus.SC_BAD_REQUEST).entity("Failed to download bundle: " + e.getMessage())
                    .build();
        }
        return response;
    }

    @GET
    @Path(SUPPORT_BUNDLES_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    public Response listBundles() {
        if (securityService.isAnonymous() || !securityService.isAdmin()) {
            return unauthorized();
        }
        log.debug("Listing all bundles");
        SupportAddon supportAddon = addonsManager.addonByType(SupportAddon.class);
        try {
            return Response.ok().entity(supportAddon.listBundles()).build();
        } catch (UnsupportedOperationException e) {
            throw new MissingRestAddonException();
        }
    }

    @DELETE
    @Path(SUPPORT_BUNDLES_PATH + "/{archive: .+}")
    @RolesAllowed({HaRestConstants.ROLE_HA, AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteBundle(@PathParam("archive") String archive, @QueryParam("node") String handlingNode) {
        if (securityService.isAnonymous() || !securityService.isAdmin()) {
            return unauthorized();
        }

        log.debug("Deleting bundle: {}", archive);
        SupportAddon supportAddon = addonsManager.addonByType(SupportAddon.class);
        boolean deleted = supportAddon.deleteBundleByName(archive);
        try {
            return Response.status(deleted ? Response.Status.ACCEPTED : Response.Status.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
}
