package org.artifactory.rest.resource.release;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.addon.release.bundle.airgap.AirGapReleaseBundleException;
import org.artifactory.addon.release.bundle.airgap.AirGapReleaseBundleValidationException;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.rest.distribution.bundle.models.CloseTransactionStatusResponse;
import org.artifactory.api.rest.release.airgap.AirGapReleaseBundleExportStatusModel;
import org.artifactory.api.rest.release.airgap.AirGapReleaseBundleImportStatusModel;
import org.artifactory.api.rest.release.airgap.AirGapReleaseBundleStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.util.AolUtils;
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
import java.io.File;
import java.io.InputStream;

import static org.artifactory.rest.resource.release.AirGapReleaseBundleResource.PATH_ROOT;

/**
 * @author Yevdo Abramov
 * Created on 20/07/2020
 */
@Slf4j
@Path(PATH_ROOT)
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AirGapReleaseBundleResource {
    public static final String PATH_ROOT = SystemRestConstants.JFROG_RELEASE;

    private final AddonsManager addonsManager;

    @Context
    private HttpServletRequest servletRequest;

    @Inject
    public AirGapReleaseBundleResource(@NonNull AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Path("/import")
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response importReleaseBundleFromStream(@QueryParam("dry_run") boolean dryRun,
            @NonNull InputStream inputStream) throws AirGapReleaseBundleException {
        AolUtils.assertNotAol("Import release bundle");
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        try {
            releaseBundleAddon.airGapReleaseBundleImport(inputStream, dryRun);
            return Response.accepted().build();
        } catch (AirGapReleaseBundleValidationException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Path("/internal/import")
    @RolesAllowed({HaRestConstants.ROLE_HA})
    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    public Response propagatedImportReleaseBundleFromFileSystem(
            @QueryParam("dry_run") boolean dryRun,
            @QueryParam("file_name") @DefaultValue("") @NonNull String importFileName
    )
            throws AirGapReleaseBundleException {
        return importReleaseBundleFromFileSystemInner(dryRun, importFileName, false);
    }

    @Path("/import")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    public Response importReleaseBundleFromFileSystem(
            @QueryParam("dry_run") boolean dryRun,
            @QueryParam("file_name") @DefaultValue("") @NonNull String importFileName
    )
            throws AirGapReleaseBundleException {
        return importReleaseBundleFromFileSystemInner(dryRun, importFileName, true);
    }

    @Path("export/{name}/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @POST
    public Response exportReleaseBundle(
            @PathParam("name") String bundleName,
            @PathParam("version") String bundleVersion)
            throws AirGapReleaseBundleException {
        AolUtils.assertNotAol("Export release bundle");
        try {
            ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
            releaseBundleAddon.triggerAirGapReleaseBundleExport(bundleName, bundleVersion);

            return Response.status(HttpStatus.SC_ACCEPTED).build();
        } catch (AirGapReleaseBundleException e) {
            String errorMessage =
                    String.format("An error occurred while triggering release bundle export for %s/%s: %s", bundleName,
                            bundleVersion, e.getMessage());
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
            @PathParam("name") String bundleName,
            @PathParam("version") String bundleVersion) throws AirGapReleaseBundleException {
        AolUtils.assertNotAol("Delete exported release bundle file");
        try {
            ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
            releaseBundleAddon.deleteReleaseBundleExportedFile(bundleName, bundleVersion);

            return Response.status(HttpStatus.SC_OK).build();

        } catch (AirGapReleaseBundleException e) {
            String errorMessage =
                    String.format("An error occurred while getting release bundle %s/%s export status: %s", bundleName,
                            bundleVersion, e.getMessage());
            log.error(errorMessage);
            log.debug(errorMessage, e);
            throw e;
        }
    }

    @Path("export/{name}/{version}/status")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @GET
    public Response getExportStatus(@PathParam("name") String bundleName,
            @PathParam("version") String bundleVersion) throws AirGapReleaseBundleException {
        AolUtils.assertNotAol("Export release bundle");
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        try {

            String artifactoryUrl = HttpUtils.getServletContextUrl(servletRequest);
            AirGapReleaseBundleExportStatusModel model =
                    releaseBundleAddon.getAirGapExportStatus(bundleName, bundleVersion, artifactoryUrl);

            return Response.ok(model).build();
        } catch (AirGapReleaseBundleException e) {
            String errorMessage =
                    String.format("An error occurred while getting release bundle %s/%s export status: %s", bundleName,
                            bundleVersion, e.getMessage());
            log.error(errorMessage);
            log.debug(errorMessage, e);
            throw e;
        }
    }

    @Path("import/{name}/{version}/status")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @GET
    public Response getImportStatus(@PathParam("name") String bundleName, @PathParam("version") String bundleVersion) {
        AolUtils.assertNotAol("Import release bundle");
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        CloseTransactionStatusResponse closeTransactionStatus =
                releaseBundleAddon.checkCloseTransactionStatus(bundleName, bundleVersion);

        AirGapReleaseBundleImportStatusModel importStatus = new AirGapReleaseBundleImportStatusModel();
        switch (closeTransactionStatus.getStatus()) {
            case INPROGRESS:
            case CLOSE_INPROGRESS:
                importStatus.setStatus(AirGapReleaseBundleStatus.IN_PROGRESS);
                break;
            case COMPLETE:
                importStatus.setStatus(AirGapReleaseBundleStatus.COMPLETED);
                break;
            default:
                importStatus.setStatus(AirGapReleaseBundleStatus.FAILED);
                break;
        }

        return Response.ok(importStatus).build();

    }

    @NonNull
    private Response importReleaseBundleFromFileSystemInner(
            boolean dryRun, @NonNull String importFileName, boolean propagate) throws AirGapReleaseBundleException {

        AolUtils.assertNotAol("Import release bundle");
        File baseImportDir = ContextHelper.get().getArtifactoryHome().getImportDir();
        File importFile = new File(baseImportDir, importFileName);

        try {
            validateImportFromFileSystemRequest(importFileName, baseImportDir, importFile);
        } catch (NotFoundException e) {
            return propagateOrRethrowIfNeeded(importFileName, dryRun, propagate, e);
        }

        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        try {
            releaseBundleAddon.airGapReleaseBundleFileSystemImport(importFile, dryRun);
            return Response.accepted().build();
        } catch (AirGapReleaseBundleValidationException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private Response propagateOrRethrowIfNeeded(
            @NonNull String importFileName,
            boolean dryRun,
            boolean propagate,
            @NonNull NotFoundException e) {

        if (propagate) {
            log.info("File {} not found on current node. will try to propagate the request to other nodes",
                    importFileName);
            boolean successfulPropagation = addonsManager.addonByType(HaCommonAddon.class)
                    .propagateAirGapImportFromFileSystem(importFileName, dryRun);

            if (!successfulPropagation) {
                throw e;
            } else {
                return Response.accepted().build();
            }
        } else {
            throw e;
        }
    }

    private void validateImportFromFileSystemRequest(
            @NonNull String importFileName,
            @NonNull File baseImportDir,
            @NonNull File importFile) {

        if (importFileName.isEmpty()) {
            throw new BadRequestException("Please specify a proper import file name");
        }

        // prevent directory usage
        if (!importFile.getParentFile().equals(baseImportDir)) {
            throw new BadRequestException(
                    "The file: '" + importFileName +
                            "' must be located directly under the Artifactory import directory");
        }

        if (!importFile.exists()) {
            throw new NotFoundException("File: '" + importFileName + "' not found");
        } else if (!importFile.isFile()) {
            throw new BadRequestException(
                    "Cannot import from: '" + importFileName + "'. Please specify a proper import file");
        }
    }
}
