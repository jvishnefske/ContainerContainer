package org.artifactory.rest.resource.release;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.addon.release.bundle.airgap.AirGapReleaseBundleException;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.rest.release.airgap.AirGapReleaseBundleExportStatusModel;
import org.artifactory.api.rest.release.airgap.ArtifactoryAirGapV2RequestModel;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.rest.resource.release.AirGapReleaseBundleV2Resource.PATH_ROOT;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;


/**
 * @author Eli Skoran
 * Created on 23/05/2023
 */
@Slf4j
@Path(PATH_ROOT)
@Component
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AirGapReleaseBundleV2Resource {
    public static final String PATH_ROOT = "v2/" + SystemRestConstants.JFROG_RELEASE;

    private final AddonsManager addonsManager;

    @Context
    private HttpServletRequest servletRequest;

    @Inject
    public AirGapReleaseBundleV2Resource(@NonNull AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Path("export/{name}/{version}")
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @POST
    public Response exportReleaseBundleV2(
            @PathParam("name") String rbV2Name,
            @PathParam("version") String rbV2Version,
            @QueryParam("repository_key") @DefaultValue(DEFAULT_RBV2_REPO) String storingRepository,
            @QueryParam("project") @DefaultValue(DEFAULT_PROJECT) String projectKey,
            @NonNull ArtifactoryAirGapV2RequestModel airGapV2RequestModel)
            throws AirGapReleaseBundleException {
        try {
            ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
            releaseBundleAddon.triggerAirGapReleaseBundleV2Export(
                    rbV2Name, rbV2Version, storingRepository, projectKey, airGapV2RequestModel);

            return Response.status(HttpStatus.SC_ACCEPTED).build();
        } catch (AirGapReleaseBundleException e) {
            String errorMessage =
                    String.format("An error occurred while triggering release bundle v2 export for %s/%s: %s", rbV2Name,
                            rbV2Version, e.getMessage());
            log.error(errorMessage);
            log.debug(errorMessage, e);
            throw e;
        }
    }

    @Path("export/{name}/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @DELETE
    public Response deleteExportedReleaseBundleFile(
            @PathParam("name") String rbV2Name,
            @PathParam("version") String rbV2Version,
            @QueryParam("repository_key") @DefaultValue(DEFAULT_RBV2_REPO) String storingRepository,
            @QueryParam("project") @DefaultValue(DEFAULT_PROJECT) String projectKey)
            throws AirGapReleaseBundleException {
        try {
            ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
            releaseBundleAddon.deleteReleaseBundleV2ExportedFile(rbV2Name, rbV2Version, storingRepository, projectKey);

            return Response.status(HttpStatus.SC_OK).build();

        } catch (AirGapReleaseBundleException e) {
            String errorMessage =
                    String.format("An error occurred while getting release bundle %s/%s export status: %s", rbV2Name,
                            rbV2Version, e.getMessage());
            log.error(errorMessage);
            log.debug(errorMessage, e);
            throw e;
        }
    }

    @Path("export/{name}/{version}/status")
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @GET
    public Response getExportStatus(
            @PathParam("name") String rbV2Name,
            @PathParam("version") String rbV2Version,
            @QueryParam("repository_key") @DefaultValue(DEFAULT_RBV2_REPO) String storingRepository,
            @QueryParam("project") @DefaultValue(DEFAULT_PROJECT) String projectKey)
            throws AirGapReleaseBundleException {
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        try {
            //nonnull the vars
            String artifactoryUrl = HttpUtils.getServletContextUrl(servletRequest);
            AirGapReleaseBundleExportStatusModel model = releaseBundleAddon.getAirGapV2ExportStatus(
                    rbV2Name, rbV2Version, storingRepository, projectKey, artifactoryUrl
            );

            return Response.ok(model).build();
        } catch (AirGapReleaseBundleException e) {
            String errorMessage =
                    String.format("An error occurred while getting release bundle v2 %s/%s export status: %s", rbV2Name,
                            rbV2Version, e.getMessage());
            log.error(errorMessage);
            log.debug(errorMessage, e);
            throw e;
        }
    }
}
