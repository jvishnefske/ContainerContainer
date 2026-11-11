package com.jfrog.build.resource.internal;

import com.jfrog.build.model.BuildDeleteNumberResult;
import com.jfrog.build.model.api.cleanup.BuildNamesResponse;
import com.jfrog.build.model.api.cleanup.BuildNumbersResponse;
import com.jfrog.build.command.cleanup.BuildDeleteForCleanupCommand;
import com.jfrog.build.command.cleanup.BuildGetNamesForCleanupCommand;
import com.jfrog.build.command.cleanup.BuildGetNumbersForCleanupCommand;
import com.jfrog.build.model.api.BuildNumbersModel;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import static com.jfrog.build.model.BuildDeleteNumberResult.Status.SUCCESS;
import static com.jfrog.build.acl.util.BuildConstants.MT_BUILDS;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_PARTIAL_CONTENT;
import static org.jfrog.security.common.AuthorizationRoles.ROLE_ADMIN;
import static org.jfrog.security.common.AuthorizationRoles.ROLE_USER;

/**
 * REST APIs for supporting the build retention/cleanup feature.
 */
@Slf4j
@Controller
@Path("build_cleanup")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@RolesAllowed({ROLE_ADMIN, ROLE_USER})
public class BuildCleanupResource {

    private final BuildDeleteForCleanupCommand deleteForCleanupCommand;
    private final BuildGetNamesForCleanupCommand getNamesForCleanupCommand;
    private final BuildGetNumbersForCleanupCommand getNumbersForCleanupCommand;

    @Inject
    public BuildCleanupResource(
            @NonNull BuildDeleteForCleanupCommand deleteForCleanupCommand,
            @NonNull BuildGetNamesForCleanupCommand getNamesForCleanupCommand,
            @NonNull BuildGetNumbersForCleanupCommand getNumbersForCleanupCommand) {
        this.deleteForCleanupCommand = deleteForCleanupCommand;
        this.getNamesForCleanupCommand = getNamesForCleanupCommand;
        this.getNumbersForCleanupCommand = getNumbersForCleanupCommand;
    }

    @GET
    @Path("names")
    @Produces({MT_BUILDS, APPLICATION_JSON})
    public Response getBuildNames(
            @QueryParam("name") String buildName,
            @QueryParam("projects") String projectKeys,
            @QueryParam("offset") @DefaultValue("0") @Min(0) Long offset,
            @QueryParam("limit") @DefaultValue("1000") @Min(1) @Max(5000) Long limit) {
        BuildNamesResponse buildNames = getNamesForCleanupCommand.getBuildNames(buildName, projectKeys, offset, limit);
        return Response.ok().entity(buildNames).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces({MT_BUILDS, APPLICATION_JSON})
    @Path("/numbers")
    public Response getBuildNumbers(@NonNull BuildNumbersModel numbers) {
        BuildNumbersResponse buildNumbers = getNumbersForCleanupCommand.getBuildNumbers(numbers);
        return Response.ok().entity(buildNumbers).build();
    }

    @DELETE
    @Path("/{buildName: .+}/{buildNumber: .+}")
    public Response deleteBuild(
            @PathParam("buildName") @NonNull String buildName,
            @PathParam("buildNumber") @NonNull String buildNumber,
            @QueryParam("started") @Nullable String started,
            @QueryParam("project") @Nullable String projectKey,
            @QueryParam("dryRun") @DefaultValue("false") @NonNull Boolean dryRun,
            @QueryParam("deleteArtifacts") @DefaultValue("true") @NonNull Boolean deleteArtifacts,
            @QueryParam("skipTrashcan") @DefaultValue("false") @NonNull Boolean skipTrashcan) {
        BuildDeleteNumberResult deleteResult = deleteForCleanupCommand.deleteBuild(
                buildName, buildNumber, started, projectKey, dryRun, deleteArtifacts, skipTrashcan);
        int status = deleteResult.getStatus() == SUCCESS ? SC_OK : SC_PARTIAL_CONTENT;
        return Response.status(status).entity(deleteResult).build();
    }
}
