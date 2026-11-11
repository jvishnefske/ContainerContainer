package com.jfrog.build.resource.internal;

import com.jfrog.build.command.ui.BuildGetArtifactBuildJsonUiCommand;
import com.jfrog.build.command.ui.BuildGetArtifactBuildsUiCommand;
import com.jfrog.build.model.api.ArtifactBuildsModel;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import org.springframework.stereotype.Controller;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jfrog.security.common.AuthorizationRoles.ROLE_ADMIN;
import static org.jfrog.security.common.AuthorizationRoles.ROLE_USER;

@Controller
@Path("artifactbuilds")
@Produces(APPLICATION_JSON)
@RolesAllowed({ROLE_ADMIN, ROLE_USER})
public class ArtifactBuildsUiResource {

    private final BuildGetArtifactBuildJsonUiCommand getArtifactBuildJsonUiCommand;
    private final BuildGetArtifactBuildsUiCommand getArtifactBuildsUiCommand;

    @Inject
    public ArtifactBuildsUiResource(
            @NonNull BuildGetArtifactBuildJsonUiCommand getArtifactBuildJsonUiCommand,
            @NonNull BuildGetArtifactBuildsUiCommand getArtifactBuildsUiCommand) {
        this.getArtifactBuildJsonUiCommand = getArtifactBuildJsonUiCommand;
        this.getArtifactBuildsUiCommand = getArtifactBuildsUiCommand;
    }

    /**
     * Gets the builds related to an artifact (resolved by repo path): the builds that depend on it and the builds
     * that produced it. Filtered by the user's permissions.
     */
    @GET
    @Produces(APPLICATION_JSON)
    public Response getArtifactBuilds(
            @Nullable @QueryParam("repoKey") String repoKey,
            @Nullable @QueryParam("path") String path) {
        ArtifactBuildsModel artifactBuilds = getArtifactBuildsUiCommand.getArtifactBuilds(repoKey, path);
        return Response.ok().entity(artifactBuilds).build();
    }

    /**
     * Returns the raw build-info JSON for a build referenced from an artifact (the "View Build JSON" action).
     */
    @GET
    @Path("json{id:(/[^/]+?)?}")
    @Produces(APPLICATION_JSON)
    public Response getArtifactBuildJson(
            @NonNull @QueryParam("buildName") String buildName,
            @NonNull @QueryParam("buildNumber") String buildNumber,
            @NonNull @QueryParam("startTime") String startTime,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        String buildJson =
                getArtifactBuildJsonUiCommand.getArtifactBuildJson(buildName, buildNumber, startTime, buildRepo);
        return buildJson == null ? Response.ok().build() : Response.ok().entity(buildJson).build();
    }
}
