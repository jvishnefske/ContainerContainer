package com.jfrog.build.resource;

import com.jfrog.build.acl.model.BuildPatternArtifactsRequestWithRepo;
import com.jfrog.build.model.api.Builds;
import com.jfrog.build.model.api.BuildsByName;
import com.jfrog.build.model.api.PromotionResult;
import com.jfrog.build.command.BuildAddModulesCommand;
import com.jfrog.build.command.BuildCreateCommand;
import com.jfrog.build.command.BuildDeleteCommand;
import com.jfrog.build.command.BuildGetAllCommand;
import com.jfrog.build.command.BuildGetArtifactsByPatternsCommand;
import com.jfrog.build.command.BuildGetByNameCommand;
import com.jfrog.build.command.BuildGetInfoCommand;
import com.jfrog.build.command.BuildPromoteCommand;
import com.jfrog.build.command.BuildRedirectUploadCommand;
import com.jfrog.build.command.BuildRenameCommand;
import com.jfrog.build.command.BuildRetainCommand;
import com.jfrog.build.model.api.BuildsDeletionModel;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildRetention;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.dependency.BuildPatternArtifacts;
import org.jfrog.build.api.release.Promotion;
import org.jfrog.common.StringList;
import org.springframework.stereotype.Controller;

import java.io.Serializable;
import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.MT_BUILDS;
import static com.jfrog.build.acl.util.BuildConstants.MT_BUILDS_BY_NAME;
import static com.jfrog.build.acl.util.BuildConstants.MT_BUILDS_DIFF;
import static com.jfrog.build.acl.util.BuildConstants.MT_BUILD_INFO;
import static com.jfrog.build.acl.util.BuildConstants.MT_BUILD_INFO_MODULE;
import static com.jfrog.build.acl.util.BuildConstants.MT_BUILD_PATTERN_ARTIFACTS_REQUEST;
import static com.jfrog.build.acl.util.BuildConstants.MT_BUILD_PATTERN_ARTIFACTS_RESULT;
import static com.jfrog.build.acl.util.BuildConstants.MT_LEGACY_ARTIFACTORY_APP;
import static com.jfrog.build.acl.util.BuildConstants.MT_PROMOTION_REQUEST;
import static com.jfrog.build.acl.util.BuildConstants.MT_PROMOTION_RESULT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.jfrog.security.common.AuthorizationRoles.ROLE_ADMIN;
import static org.jfrog.security.common.AuthorizationRoles.ROLE_USER;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CHECKSUM_SHA256;

@Controller
@Path("build")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@RolesAllowed({ROLE_ADMIN, ROLE_USER})
public class BuildPublicResource {

    private final BuildAddModulesCommand addModuleCommand;
    private final BuildCreateCommand createCommand;
    private final BuildDeleteCommand deleteCommand;
    private final BuildGetAllCommand getAllCommand;
    private final BuildGetArtifactsByPatternsCommand getArtifactsByPatternsCommand;
    private final BuildGetByNameCommand getByNameCommand;
    private final BuildGetInfoCommand getInfoCommand;
    private final BuildPromoteCommand promoteCommand;
    private final BuildRedirectUploadCommand redirectUploadCommand;
    private final BuildRenameCommand renameBuildCommand;
    private final BuildRetainCommand retainCommand;

    @Inject
    public BuildPublicResource(
            @NonNull BuildAddModulesCommand addModuleCommand,
            @NonNull BuildCreateCommand createCommand,
            @NonNull BuildDeleteCommand deleteCommand,
            @NonNull BuildGetAllCommand getAllCommand,
            @NonNull BuildGetArtifactsByPatternsCommand getArtifactsByPatternsCommand,
            @NonNull BuildGetByNameCommand getByNameCommand,
            @NonNull BuildGetInfoCommand getInfoCommand,
            @NonNull BuildPromoteCommand promoteCommand,
            @NonNull BuildRedirectUploadCommand redirectUploadCommand,
            @NonNull BuildRenameCommand renameBuildCommand,
            @NonNull BuildRetainCommand retainCommand) {
        this.addModuleCommand = addModuleCommand;
        this.createCommand = createCommand;
        this.deleteCommand = deleteCommand;
        this.getAllCommand = getAllCommand;
        this.getArtifactsByPatternsCommand = getArtifactsByPatternsCommand;
        this.getByNameCommand = getByNameCommand;
        this.getInfoCommand = getInfoCommand;
        this.promoteCommand = promoteCommand;
        this.redirectUploadCommand = redirectUploadCommand;
        this.renameBuildCommand = renameBuildCommand;
        this.retainCommand = retainCommand;
    }

    @PUT
    @Consumes({MT_BUILD_INFO, MT_LEGACY_ARTIFACTORY_APP, APPLICATION_JSON})
    @Produces({APPLICATION_JSON})
    public Response createBuild(
            @NonNull Build build,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey) {
        String buildManifestSha256 = createCommand.createBuild(build, buildRepo, projectKey);
        return Response.noContent().header(CHECKSUM_SHA256.getHeaderName(), buildManifestSha256).build();
    }

    @POST
    @Path("/append/{buildName: .+}/{buildNumber: .+}")
    @Consumes({MT_BUILD_INFO_MODULE, MT_LEGACY_ARTIFACTORY_APP, APPLICATION_JSON})
    public Response addModule(
            @NonNull List<Module> modules,
            @NonNull @PathParam("buildName") String buildName,
            @NonNull @PathParam("buildNumber") String buildNumber,
            @Nullable @QueryParam("started") String buildStarted,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey) {
        addModuleCommand.addModules(modules, buildName, buildNumber, buildStarted, buildRepo, projectKey);
        return Response.noContent().build();
    }

    @POST
    @Path("/rename/{buildName: .+}")
    @Produces({TEXT_PLAIN, APPLICATION_JSON})
    @Consumes({TEXT_PLAIN, APPLICATION_JSON})
    public Response renameBuilds(
            @NonNull @Context HttpServletRequest request,
            @NonNull @PathParam("buildName") String buildName,
            @NonNull @QueryParam("to") String buildNameNew,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey) {
        renameBuildCommand.renameBuilds(request, buildName, buildNameNew, buildRepo, projectKey);
        String message = format("Build renaming of '%s' to '%s' was successfully started", buildName, buildNameNew);
        return Response.ok().entity(message).build();
    }

    @PUT
    @Path("/buildUploadRedirect/{originalServletPath: .+}")
    @Consumes({APPLICATION_OCTET_STREAM})
    public void redirectBuildUpload(
            @NonNull @Context HttpServletRequest request,
            @NonNull @Context HttpServletResponse response,
            @NonNull @PathParam("originalServletPath") String originalServletPath) {
        redirectUploadCommand.redirectBuildUpload(request, response, originalServletPath);
    }

    @POST
    @Path("/promote/{buildName: .+}/{buildNumber: .+}")
    @Consumes({MT_PROMOTION_REQUEST, APPLICATION_JSON})
    @Produces({MT_PROMOTION_RESULT, APPLICATION_JSON})
    public Response promote(
            @NonNull Promotion promotion,
            @NonNull @Context HttpServletRequest request,
            @NonNull @PathParam("buildName") String buildName,
            @NonNull @PathParam("buildNumber") String buildNumber,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey) {
        PromotionResult result = promoteCommand.promote(request, promotion, buildName, buildNumber, buildRepo, projectKey);
        int status = promotion.isFailFast() && result.errorsOrWarningHaveOccurred() ? SC_BAD_REQUEST : SC_OK;
        return Response.status(status).entity(result).build();
    }

    @GET
    @Produces({MT_BUILDS, APPLICATION_JSON})
    public Response getBuilds(
            @NonNull @Context HttpServletRequest request,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey) {
        Builds builds = getAllCommand.getAllBuilds(request, buildRepo, projectKey);
        return Response.ok().entity(builds).build();
    }

    @GET
    @Path("/{buildName: .+}")
    @Produces({MT_BUILDS_BY_NAME, APPLICATION_JSON})
    public Response getBuildsByName(
            @NonNull @Context HttpServletRequest request,
            @NonNull @PathParam("buildName") String buildName,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey) {
        BuildsByName buildsByName = getByNameCommand.getBuildsByName(
                request,
                buildName,
                buildRepo,
                projectKey
        );
        return Response.ok().entity(buildsByName).build();
    }

    @GET
    @Path("/{buildName: .+}/{buildNumber: .+}")
    @Produces({MT_BUILD_INFO, MT_BUILDS_DIFF, APPLICATION_JSON})
    public Response getBuildInfo(
            @NonNull @Context HttpServletRequest request,
            @NonNull @PathParam("buildName") String buildName,
            @NonNull @PathParam("buildNumber") String buildNumber,
            @Nullable @QueryParam("started") String buildStarted,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey,
            @Nullable @QueryParam("diff") String diffNumber,
            @QueryParam("slim") boolean slim) {
        Serializable entity = getInfoCommand.getBuildInfo(
                request,
                buildName,
                buildNumber,
                buildStarted,
                buildRepo,
                projectKey,
                diffNumber,
                slim
        );
        return Response.ok(entity).build();
    }

    @POST
    @Path("/patternArtifacts")
    @Consumes({MT_BUILD_PATTERN_ARTIFACTS_REQUEST, MT_LEGACY_ARTIFACTORY_APP, APPLICATION_JSON})
    @Produces({MT_BUILD_PATTERN_ARTIFACTS_RESULT, APPLICATION_JSON})
    public Response getArtifactsByPatterns(
            @NonNull @Context HttpServletRequest request,
            @NonNull List<BuildPatternArtifactsRequestWithRepo> patterns) {
        List<BuildPatternArtifacts> buildArtifacts =
                getArtifactsByPatternsCommand.getArtifactsByPatterns(request, patterns);
        return Response.ok().entity(buildArtifacts).build();
    }

    @POST
    @Path("/retention/{buildName: .+}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response retainBuilds(
            @Nullable BuildRetention retention,
            @NonNull @PathParam("buildName") String buildName,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey,
            @QueryParam("async") boolean async) {
        if (isNull(retention) || retention.getCount() == 0) {
            return Response.status(BAD_REQUEST).entity("Max count retention needs to be a positive number").build();
        }
        retainCommand.retainBuilds(retention, buildName, buildRepo, projectKey, async);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{buildName: .+}")
    public Response deleteBuilds(
            @NonNull @Context HttpServletRequest request,
            @NonNull @PathParam("buildName") String buildName,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("project") String projectKey,
            @Nullable @QueryParam("buildNumbers") StringList buildNumbers,
            @QueryParam("artifacts") int artifacts,
            @QueryParam("deleteAll") int deleteAll) {
        String message = deleteCommand.deleteBuilds(
                request,
                buildName,
                buildRepo,
                projectKey,
                buildNumbers,
                artifacts == 1,
                deleteAll == 1
        );
        return Response.ok().entity(message).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Path("/delete")
    public Response bulkDeleteBuilds(
            @NonNull @Context HttpServletRequest request,
            @NonNull BuildsDeletionModel build) {
        String message = deleteCommand.deleteBuilds(
                request,
                build.getBuildName(),
                build.getBuildRepo(),
                build.getProject(),
                build.getBuildNumbers(),
                build.isDeleteArtifacts(),
                build.isDeleteAll()
        );
        return Response.ok().entity(message).build();
    }
}
