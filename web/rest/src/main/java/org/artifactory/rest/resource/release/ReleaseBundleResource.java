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
package org.artifactory.rest.resource.release;

import com.google.common.collect.Maps;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.addon.release.bundle.exception.ReleaseBundleSignatureValidationException;
import org.artifactory.addon.release.bundle.utils.ReleaseBundleAddonJsonUtils;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.rest.distribution.bundle.models.BundleTransactionResponse;
import org.artifactory.api.rest.distribution.bundle.models.BundleVersionsResponse;
import org.artifactory.api.rest.distribution.bundle.models.BundlesResponse;
import org.artifactory.api.rest.distribution.bundle.models.CloseTransactionStatusResponse;
import org.artifactory.api.rest.release.ReleaseBundleArtifactsModel;
import org.artifactory.api.rest.release.ReleaseBundleRequest;
import org.artifactory.api.rest.release.ReleaseBundleResult;
import org.artifactory.api.rest.release.ReleaseBundlesConfigModel;
import org.artifactory.api.rest.release.SourceReleaseBundleRequest;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.bundle.BundleType;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.rest.common.resource.release.bundle.ReleaseBundleResourceBinding;
import org.artifactory.exception.http.BadRequestException;
import org.jfrog.model.api.releasebundle.ReleaseBundleOpenTransactionRequest;
import org.jfrog.security.util.Pair;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;

/**
 * @author Shay Bagants
 */
@Slf4j
@Path(SystemRestConstants.JFROG_RELEASE)
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@ReleaseBundleResourceBinding
public class ReleaseBundleResource {

    private static final String APPLICATION_JOSE = "application/jose";
    private static final int HEAVY_RELEASE_BUNDLE_SIGNATURE_LENGTH = 100_000;
    private final AddonsManager addonsManager;

    @Inject
    SecurityService securityService;

    @Inject
    public ReleaseBundleResource(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Path("bundle")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER, HaRestConstants.ROLE_HA})
    @POST
    public Response assembleBundleFromAql(ReleaseBundleRequest bundleRequest,
            @QueryParam("includeMetaData") boolean includeMetaData,
            @QueryParam(ProjectsService.PROJECT_KEY) String projectKey) {
        if (securityService.isAnonymous()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        projectKey = ProjectsUtils.getNormalizedProjectKey(projectKey);
        ReleaseBundleResult releaseBundleResult =
                releaseBundleAddon.executeReleaseBundleRequest(bundleRequest, includeMetaData, projectKey);
        return Response.ok().entity(releaseBundleResult).build();
    }

    @Path("fat_manifest_content/{path: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @GET
    public Response getFatManifestContent(
            @PathParam("path") String path,
            @QueryParam(ProjectsService.PROJECT_KEY) String projectKey) {

        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        projectKey = ProjectsUtils.getNormalizedProjectKey(projectKey);
        String fatManifestContent = releaseBundleAddon.getFatManifestContent(path, projectKey);
        return Response.ok().entity(fatManifestContent).build();
    }

    @Path(SystemRestConstants.BUNDLE_TRANSACTION)
    @Consumes(APPLICATION_JOSE)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @POST
    public Response initiateBundleTransaction(String signedJwsBundle) throws IOException {
        String openTransactionRequestString =
                ReleaseBundleAddonJsonUtils.writeJsonCamelCase(
                        new ReleaseBundleOpenTransactionRequest(signedJwsBundle));
        return openBundleTransaction(openTransactionRequestString);
    }

    @Path(SystemRestConstants.BUNDLE_OPEN_TRANSACTION_V2)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @POST
    public Response openBundleTransaction(@NonNull String openTransactionRequestString)
            throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("Store release bundle requested. Store request size: {} bytes",
                    openTransactionRequestString.getBytes().length);
            if (openTransactionRequestString.length() < HEAVY_RELEASE_BUNDLE_SIGNATURE_LENGTH) {
                log.debug("Release bundle open transaction request body: {}", openTransactionRequestString);
            }
        }

        ReleaseBundleOpenTransactionRequest openTransactionRequest =
                ReleaseBundleAddonJsonUtils.readCamelCase(openTransactionRequestString,
                        ReleaseBundleOpenTransactionRequest.class);

        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        BundleTransactionResponse response;
        try {
            response = releaseBundleAddon.createBundleTransaction(openTransactionRequest);
        } catch (ReleaseBundleSignatureValidationException e) {
            log.error("Release bundle signature validation error: {}", e.getMessage());
            throw new BadRequestException("Failed validating release bundle signature", e);
        } catch (ParseException e) {
            log.error("Failed to parse JWS", e);
            throw new BadRequestException("Failed to parse JWS. " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Caught exception while trying to start transaction ", e);
            throw new BadRequestException(e.getMessage());
        }
        return Response.status(HttpStatus.SC_CREATED).entity(response).build();
    }


    @OPTIONS
    @Path("store")
    @Produces("text/plain")
    public Response storeOptions() {
        return Response.status(HttpStatus.SC_OK)
                .header("Allow", "OPTIONS,PUT")
                .entity("OPTIONS, PUT")
                .build();
    }

    @Path("store")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @PUT
    public Response storeBundle(String bundleRequestString,
            @QueryParam(ProjectsService.PROJECT_KEY) String projectKey)
            throws IOException, ParseException, SQLException {
        if (log.isDebugEnabled()) {
            log.debug("Store release bundle requested. Store request size: {} bytes",
                    bundleRequestString.getBytes().length);
            if (bundleRequestString.length() < HEAVY_RELEASE_BUNDLE_SIGNATURE_LENGTH) {
                log.debug("Release bundle store request body: {}", bundleRequestString);
            }
        }

        SourceReleaseBundleRequest bundleRequest =
                ReleaseBundleAddonJsonUtils.readCamelCase(bundleRequestString, SourceReleaseBundleRequest.class);
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        Pair<String, Integer> pathAndStatus = null;
        try {
            projectKey = ProjectsUtils.getNormalizedProjectKey(projectKey);
            pathAndStatus = releaseBundleAddon.storeBundle(bundleRequest, projectKey);
        } catch (ReleaseBundleSignatureValidationException e) {
            log.error("Release bundle signature validation error: {}", e.getMessage());
            throw new BadRequestException("Failed validating release bundle signature", e);
        }
        HashMap<Object, Object> entity = Maps.newHashMap();
        entity.put("bundle_path", pathAndStatus.getFirst());
        return Response.status(pathAndStatus.getSecond()).type(MediaType.APPLICATION_JSON).entity(entity).build();
    }

    @Path(SystemRestConstants.BUNDLE_TRANSACTION_CLOSE + "/{transaction_path: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @POST
    public Response closeBundleTransaction(@PathParam("transaction_path") String transactionPath) {
        log.debug("Close bundle transaction for {}", transactionPath);
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        releaseBundleAddon.closeBundleTransaction(transactionPath);
        return Response.ok(Collections.emptyMap()).build();
    }

    @Path(SystemRestConstants.BUNDLE_TRANSACTION_CLOSE_ASYNC + "/{transaction_path: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @POST
    public Response closeBundleTransactionAsync(@PathParam("transaction_path") String transactionPath,
            @QueryParam("sync_wait_time_secs") Integer syncWaitTimeSecs) {

        log.debug("Close bundle transaction async for {} with syncWaitTimeSecs of {}",
                transactionPath, syncWaitTimeSecs);
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);

        CloseTransactionStatusResponse statusResponse = releaseBundleAddon
                .closeBundleTransactionAsync(transactionPath, syncWaitTimeSecs, Collections.emptyList(), false);
        return Response.ok(statusResponse).build();
    }

    @Path(SystemRestConstants.BUNDLE_CHECK_CLOSE_STATUS + "/{transaction_path: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @GET
    public Response checkCloseBundleStatus(@PathParam("transaction_path") String transactionPath) {
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        CloseTransactionStatusResponse statusResponse = releaseBundleAddon.checkCloseTransactionStatus(transactionPath);
        return Response.ok(statusResponse).build();
    }

    @Path("bundles")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @GET
    public Response getBundles(@QueryParam("type") String type) {
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        BundlesResponse allBundles = releaseBundleAddon.getAllBundles(BundleType.fromType(type));
        return Response.ok().entity(allBundles).build();
    }

    @Path("bundles/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @GET
    public Response getBundles(@PathParam("name") String bundleName, @QueryParam("type") String type) {
        BundleType bundleType = BundleType.fromType(type);
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        BundleVersionsResponse bundleVersionsResponse = releaseBundleAddon.getBundleVersions(bundleName, bundleType);
        return Response.ok().entity(bundleVersionsResponse).build();
    }

    @Path("bundles/{name}/{version}")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @HEAD
    public Response getSourceReleaseBundleChecksum(@PathParam("name") String bundleName,
            @PathParam("version") String bundleVersion) {
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        String bundleJson = releaseBundleAddon.getBundleJson(bundleName, bundleVersion, BundleType.SOURCE);
        if(StringUtils.isEmpty(bundleJson)) {
            return Response.status(HttpStatus.SC_NOT_FOUND).build();
        }

        String sha256 = DigestUtils.sha256Hex(bundleJson);
        return Response.ok()
                .header(ArtifactoryHeaders.CHECKSUM_SHA256, sha256)
                .build();
    }

    @Path("bundles/{name}/{version}")
    @Produces({MediaType.APPLICATION_JSON, APPLICATION_JOSE})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @GET
    public Response getBundles(@PathParam("name") String bundleName, @PathParam("version") String bundleVersion,
            @QueryParam("format") String format, @QueryParam("type") String type) {
        BundleType bundleType = BundleType.fromType(type);
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        if (StringUtils.isNotBlank(format) && "jws".equalsIgnoreCase(format)) {
            String signedJwsBundle = releaseBundleAddon.getBundleSignedJws(bundleName, bundleVersion, bundleType);
            return Response.ok().entity(signedJwsBundle).type(APPLICATION_JOSE).build();
        }
        String bundleJson = releaseBundleAddon.getBundleJson(bundleName, bundleVersion, bundleType);
        return Response.ok().entity(bundleJson).build();
    }

    @Path("bundles/{name}/{version}/status")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @GET
    public Response getBundleStatus(@PathParam("name") String bundleName, @PathParam("version") String bundleVersion,
            @QueryParam("type") String type) {
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        String bundleStatus = releaseBundleAddon.getBundleStatus(bundleName, bundleVersion, BundleType.fromType(type));
        return Response.ok().entity(bundleStatus).build();
    }

    @Path("bundles/{name}/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @DELETE
    public Response deleteBundle(@PathParam("name") String bundleName, @PathParam("version") String bundleVersion) {
        log.info("Delete target bundle request {}/{}", bundleName, bundleVersion);
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        releaseBundleAddon.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET);
        return Response.ok().entity(Collections.emptyMap()).build();
    }

    @Path("bundles/source/{name}/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @DELETE
    public Response deleteSourceBundle(@PathParam("name") String bundleName,
            @PathParam("version") String bundleVersion) {
        log.info("Delete source bundle request {}/{}", bundleName, bundleVersion);
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        releaseBundleAddon.deleteReleaseBundle(bundleName, bundleVersion, BundleType.SOURCE);
        return Response.ok().entity(Collections.emptyMap()).build();
    }

    @Path("bundles/config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @PUT
    public Response setReleaseBundlesConfig(ReleaseBundlesConfigModel releaseBundlesConfig) {
        log.debug("bundle config request {}", releaseBundlesConfig);
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        releaseBundleAddon.setReleaseBundlesConfig(releaseBundlesConfig);
        return Response.status(HttpStatus.SC_ACCEPTED).entity("Successfully updated release bundles config").build();
    }

    @Path("bundles/config")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @GET
    public Response getReleaseBundlesConfig() {
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        ReleaseBundlesConfigModel releaseBundlesConfig = releaseBundleAddon.getReleaseBundlesConfig();
        return Response.status(HttpStatus.SC_OK).entity(releaseBundlesConfig).build();
    }

    @Path("bundles/{name}/{version}/artifacts")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @GET
    public Response getArtifactsToScan(@PathParam("name") String bundleName,
            @PathParam("version") String bundleVersion) {
        ReleaseBundleAddon releaseBundleAddon =
                addonsManager.addonByType(ReleaseBundleAddon.class);
        ReleaseBundleArtifactsModel releaseBundleArtifacts =
                releaseBundleAddon.getListOfBundleNodes(bundleName, bundleVersion);
        return Response.status(HttpStatus.SC_OK).entity(releaseBundleArtifacts).build();
    }
}
