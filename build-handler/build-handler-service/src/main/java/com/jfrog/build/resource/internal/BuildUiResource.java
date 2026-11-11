package com.jfrog.build.resource.internal;

import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import com.jfrog.build.command.ui.BuildDeleteAllUiCommand;
import com.jfrog.build.command.ui.BuildDeleteUiCommand;
import com.jfrog.build.command.ui.BuildGetAllNamesPagedUiCommand;
import com.jfrog.build.command.ui.BuildGetAllNamesUiCommand;
import com.jfrog.build.command.ui.BuildGetAllUiCommand;
import com.jfrog.build.command.ui.BuildGetArtifactDiffUiCommand;
import com.jfrog.build.command.ui.BuildGetArtifactsUiCommand;
import com.jfrog.build.command.ui.BuildGetBuildDiffUiCommand;
import com.jfrog.build.command.ui.BuildGetBuildIssuesUiCommand;
import com.jfrog.build.command.ui.BuildGetContentUiCommand;
import com.jfrog.build.command.ui.BuildGetDependencyDiffUiCommand;
import com.jfrog.build.command.ui.BuildGetEffectivePermissionByGroupUiCommand;
import com.jfrog.build.command.ui.BuildGetEffectivePermissionByUserUiCommand;
import com.jfrog.build.command.ui.BuildGetEffectivePermissionUiCommand;
import com.jfrog.build.command.ui.BuildGetEnvBuildPropsUiCommand;
import com.jfrog.build.command.ui.BuildGetGeneralInfoUiCommand;
import com.jfrog.build.command.ui.BuildGetHistoryUiCommand;
import com.jfrog.build.command.ui.BuildGetJsonUiCommand;
import com.jfrog.build.command.ui.BuildGetModuleArtifactsUiCommand;
import com.jfrog.build.command.ui.BuildGetModuleDependenciesUiCommand;
import com.jfrog.build.command.ui.BuildGetPrevBuildsUiCommand;
import com.jfrog.build.command.ui.BuildGetProjectInfoUiCommand;
import com.jfrog.build.command.ui.BuildGetPublishedModulesUiCommand;
import com.jfrog.build.command.ui.BuildGetReleaseHistoryUiCommand;
import com.jfrog.build.command.ui.BuildGetRepositoryKeyUiCommand;
import com.jfrog.build.command.ui.BuildGetSystemBuildPropsUiCommand;
import com.jfrog.build.command.ui.BuildSearchIdsByNameUiCommand;
import com.jfrog.build.command.ui.BuildSearchVersionsByNameUiCommand;
import com.jfrog.build.model.api.BuildArtifactModel;
import com.jfrog.build.model.api.BuildContentResponse;
import com.jfrog.build.model.api.BuildDiffModel;
import com.jfrog.build.model.api.BuildIdResponse;
import com.jfrog.build.model.api.BuildModule;
import com.jfrog.build.model.api.BuildNumbersResponse;
import com.jfrog.build.model.api.BuildPropsModel;
import com.jfrog.build.model.api.BuildRepositoryKeyModel;
import com.jfrog.build.model.api.DeleteBuildsModel;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.model.api.IssueModel;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.ModuleDependencyModel;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.model.api.ProjectBuildsMetadata;
import com.jfrog.build.model.api.PromotionStatusModel;
import com.jfrog.build.model.api.ViewArtifactModel;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import org.jfrog.commons.continues.ContinueResult;
import org.springframework.stereotype.Controller;

import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.Strings.CS;
import static org.jfrog.security.common.AuthorizationRoles.ROLE_ADMIN;
import static org.jfrog.security.common.AuthorizationRoles.ROLE_USER;

@Controller
@Path("builds")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@RolesAllowed({ROLE_ADMIN, ROLE_USER})
public class BuildUiResource {

    private final BuildDeleteAllUiCommand deleteAllUiCommand;
    private final BuildDeleteUiCommand deleteUiCommand;
    private final BuildGetAllNamesPagedUiCommand getAllNamesPagedUiCommand;
    private final BuildGetAllNamesUiCommand getAllNamesUiCommand;
    private final BuildGetAllUiCommand getAllUiCommand;
    private final BuildGetArtifactDiffUiCommand getArtifactDiffUiCommand;
    private final BuildGetArtifactsUiCommand getArtifactsUiCommand;
    private final BuildGetBuildDiffUiCommand getBuildDiffUiCommand;
    private final BuildGetBuildIssuesUiCommand getBuildIssuesUiCommand;
    private final BuildGetContentUiCommand getContentUiCommand;
    private final BuildGetDependencyDiffUiCommand getDependencyDiffUiCommand;
    private final BuildGetEffectivePermissionByGroupUiCommand getEffectivePermissionByGroupUiCommand;
    private final BuildGetEffectivePermissionByUserUiCommand getEffectivePermissionByUserUiCommand;
    private final BuildGetEffectivePermissionUiCommand getEffectivePermissionUiCommand;
    private final BuildGetEnvBuildPropsUiCommand getEnvBuildPropsUiCommand;
    private final BuildGetGeneralInfoUiCommand getGeneralInfoUiCommand;
    private final BuildGetHistoryUiCommand getHistoryUiCommand;
    private final BuildGetJsonUiCommand getJsonUiCommand;
    private final BuildGetModuleArtifactsUiCommand getModuleArtifactsUiCommand;
    private final BuildGetModuleDependenciesUiCommand getModuleDependenciesUiCommand;
    private final BuildGetPrevBuildsUiCommand getPrevBuildsUiCommand;
    private final BuildGetProjectInfoUiCommand getProjectInfoUiCommand;
    private final BuildGetPublishedModulesUiCommand getPublishedModulesUiCommand;
    private final BuildGetReleaseHistoryUiCommand getReleaseHistoryUiCommand;
    private final BuildGetRepositoryKeyUiCommand getRepositoryKeyUiCommand;
    private final BuildGetSystemBuildPropsUiCommand getSystemBuildPropsUiCommand;
    private final BuildSearchIdsByNameUiCommand searchIdsByNameUiCommand;
    private final BuildSearchVersionsByNameUiCommand searchVersionsByNameUiCommand;

    @Inject
    public BuildUiResource(
            @NonNull BuildDeleteAllUiCommand deleteAllUiCommand,
            @NonNull BuildDeleteUiCommand deleteUiCommand,
            @NonNull BuildGetAllNamesPagedUiCommand getAllNamesPagedUiCommand,
            @NonNull BuildGetAllNamesUiCommand getAllNamesUiCommand,
            @NonNull BuildGetAllUiCommand getAllUiCommand,
            @NonNull BuildGetArtifactDiffUiCommand getArtifactDiffUiCommand,
            @NonNull BuildGetArtifactsUiCommand getArtifactsUiCommand,
            @NonNull BuildGetBuildDiffUiCommand getBuildDiffUiCommand,
            @NonNull BuildGetBuildIssuesUiCommand getBuildIssuesUiCommand,
            @NonNull BuildGetContentUiCommand getContentUiCommand,
            @NonNull BuildGetDependencyDiffUiCommand getDependencyDiffUiCommand,
            @NonNull BuildGetEffectivePermissionByGroupUiCommand getEffectivePermissionByGroupUiCommand,
            @NonNull BuildGetEffectivePermissionByUserUiCommand getEffectivePermissionByUserUiCommand,
            @NonNull BuildGetEffectivePermissionUiCommand getEffectivePermissionUiCommand,
            @NonNull BuildGetEnvBuildPropsUiCommand getEnvBuildPropsUiCommand,
            @NonNull BuildGetGeneralInfoUiCommand getGeneralInfoUiCommand,
            @NonNull BuildGetHistoryUiCommand getHistoryUiCommand,
            @NonNull BuildGetJsonUiCommand getJsonUiCommand,
            @NonNull BuildGetModuleArtifactsUiCommand getModuleArtifactsUiCommand,
            @NonNull BuildGetModuleDependenciesUiCommand getModuleDependenciesUiCommand,
            @NonNull BuildGetPrevBuildsUiCommand getPrevBuildsUiCommand,
            @NonNull BuildGetProjectInfoUiCommand getProjectInfoUiCommand,
            @NonNull BuildGetPublishedModulesUiCommand getPublishedModulesUiCommand,
            @NonNull BuildGetReleaseHistoryUiCommand getReleaseHistoryUiCommand,
            @NonNull BuildGetRepositoryKeyUiCommand getRepositoryKeyUiCommand,
            @NonNull BuildGetSystemBuildPropsUiCommand getSystemBuildPropsUiCommand,
            @NonNull BuildSearchIdsByNameUiCommand searchIdsByNameUiCommand,
            @NonNull BuildSearchVersionsByNameUiCommand searchVersionsByNameUiCommand) {
        this.deleteAllUiCommand = deleteAllUiCommand;
        this.deleteUiCommand = deleteUiCommand;
        this.getAllUiCommand = getAllUiCommand;
        this.getAllNamesUiCommand = getAllNamesUiCommand;
        this.getAllNamesPagedUiCommand = getAllNamesPagedUiCommand;
        this.getArtifactDiffUiCommand = getArtifactDiffUiCommand;
        this.getArtifactsUiCommand = getArtifactsUiCommand;
        this.getBuildDiffUiCommand = getBuildDiffUiCommand;
        this.getBuildIssuesUiCommand = getBuildIssuesUiCommand;
        this.getContentUiCommand = getContentUiCommand;
        this.getDependencyDiffUiCommand = getDependencyDiffUiCommand;
        this.getEffectivePermissionByGroupUiCommand = getEffectivePermissionByGroupUiCommand;
        this.getEffectivePermissionByUserUiCommand = getEffectivePermissionByUserUiCommand;
        this.getEffectivePermissionUiCommand = getEffectivePermissionUiCommand;
        this.getEnvBuildPropsUiCommand = getEnvBuildPropsUiCommand;
        this.getGeneralInfoUiCommand = getGeneralInfoUiCommand;
        this.getHistoryUiCommand = getHistoryUiCommand;
        this.getJsonUiCommand = getJsonUiCommand;
        this.getModuleArtifactsUiCommand = getModuleArtifactsUiCommand;
        this.getModuleDependenciesUiCommand = getModuleDependenciesUiCommand;
        this.getPrevBuildsUiCommand = getPrevBuildsUiCommand;
        this.getProjectInfoUiCommand = getProjectInfoUiCommand;
        this.getPublishedModulesUiCommand = getPublishedModulesUiCommand;
        this.getReleaseHistoryUiCommand = getReleaseHistoryUiCommand;
        this.getRepositoryKeyUiCommand = getRepositoryKeyUiCommand;
        this.getSystemBuildPropsUiCommand = getSystemBuildPropsUiCommand;
        this.searchIdsByNameUiCommand = searchIdsByNameUiCommand;
        this.searchVersionsByNameUiCommand = searchVersionsByNameUiCommand;
    }

    @POST
    @Path("deleteAllBuilds")
    @RolesAllowed(ROLE_ADMIN)
    @Produces(APPLICATION_JSON)
    public Response deleteAllBuilds(
            @NonNull DeleteBuildsModel deleteBuildsModel) {
        String message = deleteAllUiCommand.deleteAllBuilds(deleteBuildsModel.getBuildsCoordinates());
        return Response.ok().entity(message).build();
    }

    /**
     * Deletes specific build (build ID under build name) according to user's permissions.
     */
    @POST
    @Path("buildsDelete")
    @RolesAllowed(ROLE_ADMIN)
    @Produces(APPLICATION_JSON)
    public Response deleteBuild(
            @NonNull DeleteBuildsModel deleteBuildsModel) {
        String message = deleteUiCommand.deleteBuilds(deleteBuildsModel.getBuildsCoordinates());
        return Response.ok().entity(message).build();
    }

    /**
     * Gets all (unique) build names, used by the build permissions modal, in a paged manner.
     */
    @GET
    @Path("namesPaged")
    @Produces(APPLICATION_JSON)
    public Response getAllBuildNamesPaged(
            @NonNull @Context HttpServletRequest request) {
        ContinueResult<String> buildNames = getAllNamesPagedUiCommand.getAllBuildNamesPaged(request.getParameterMap());
        return Response.ok().entity(buildNames).build();
    }

    @GET
    @Path("names")
    @Produces(APPLICATION_JSON)
    public Response getAllBuildNames(
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        List<String> buildNames = getAllNamesUiCommand.getAllBuildNames(buildRepo);
        return Response.ok().entity(buildNames).build();
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getAllBuilds(
            @NonNull @Context HttpServletRequest request) {
        ContinueResult<GeneralBuildInfo> builds = getAllUiCommand.getAllBuilds(request.getParameterMap());
        return Response.ok().entity(builds).build();
    }

    /**
     * Gets the list of build numbers (IDs) for a specific build name.
     */
    @GET
    @Path("history{name:(/[^/]+?)?}")
    @Produces(APPLICATION_JSON)
    public Response getBuildHistory(
            @Nullable @PathParam("name") String name,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @NonNull @Context HttpServletRequest request) {
        // The path template captures the leading slash (e.g. "/my-build"), strip it to get the build name.
        String buildName = CS.removeStart(defaultString(name), "/");
        ContinueResult<GeneralBuildInfo> history =
                getHistoryUiCommand.getBuildHistory(buildName, buildRepo, request.getParameterMap());
        return Response.ok().entity(history).build();
    }

    /**
     * Gets all the previous builds of a build (used for the compare drop-down).
     */
    @GET
    @Path("prevBuild/{name}/{number}/{date}")
    @Produces(APPLICATION_JSON)
    public Response getPrevBuild(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("date") String buildDate,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        List<GeneralBuildInfo> prevBuilds = getPrevBuildsUiCommand.getPrevBuilds(buildName, buildDate, buildRepo);
        return Response.ok().entity(prevBuilds).build();
    }

    /**
     * Specific build page - top bar data (general info, plus whether the tabs and delete option are shown).
     */
    @GET
    @Path("buildInfo/{name}/{number}{date:(/[^/]+?)?}")
    @Produces(APPLICATION_JSON)
    public Response getBuildGeneralInfo(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @Nullable @PathParam("date") String date,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        // The date path template captures the leading slash (e.g. "/1700000000000"), strip it before parsing.
        String buildDate = CS.removeStart(defaultString(date), "/");
        GeneralBuildInfo generalInfo =
                getGeneralInfoUiCommand.getBuildGeneralInfo(buildName, buildNumber, buildDate, buildRepo);
        return Response.ok().entity(generalInfo).build();
    }

    /**
     * Gets the published modules of a specific build (the Published Modules page).
     */
    @GET
    @Path("publishedModules/{name}/{number}/{date}")
    @Produces(APPLICATION_JSON)
    public Response getPublishedModules(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        PagingModel<BuildModule> modules =
                getPublishedModulesUiCommand.getPublishedModules(buildName, buildNumber, buildStarted, buildRepo);
        return Response.ok().entity(modules).build();
    }

    /**
     * Fetches all artifacts of a build given its name, number and build repo.
     */
    @GET
    @Path("buildArtifacts/{name}/{number}/{repo}")
    @Produces(APPLICATION_JSON)
    public Response getBuildArtifacts(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("repo") String buildRepo) {
        List<BuildArtifactModel> artifacts =
                getArtifactsUiCommand.getBuildArtifacts(buildName, buildNumber, buildRepo, null);
        return Response.ok().entity(artifacts).build();
    }

    /**
     * Fetches all artifacts of a build given its name, number, build repo and published epoch.
     */
    @GET
    @Path("buildArtifacts/{name}/{number}/{repo}/{published_epoch}")
    @Produces(APPLICATION_JSON)
    public Response getBuildArtifactsPublishedEpoch(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("repo") String buildRepo,
            @NonNull @PathParam("published_epoch") String publishedEpoch) {
        List<BuildArtifactModel> artifacts =
                getArtifactsUiCommand.getBuildArtifacts(buildName, buildNumber, buildRepo, publishedEpoch);
        return Response.ok().entity(artifacts).build();
    }

    /**
     * Build info page - Diff tab. Compares a build against another build's artifacts, dependencies and properties.
     */
    @GET
    @Path("buildDiff/{name}/{number}/{date}/")
    @Produces(APPLICATION_JSON)
    public Response buildDiff(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @Nullable @QueryParam("otherNumber") String comparedBuildNumber,
            @NonNull @QueryParam("otherDate") String comparedBuildDate,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @QueryParam("exDep") boolean excludeDependencies) {
        BuildDiffModel buildDiff = getBuildDiffUiCommand.getBuildDiff(buildName, buildNumber, buildStarted,
                comparedBuildNumber, comparedBuildDate, buildRepo, excludeDependencies);
        return Response.ok().entity(buildDiff).build();
    }

    /**
     * Build info page - Issues tab.
     */
    @GET
    @Path("buildIssues/{name}/{number}/{date}")
    @Produces(APPLICATION_JSON)
    public Response getBuildIssues(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        List<IssueModel> issues =
                getBuildIssuesUiCommand.getBuildIssues(buildName, buildNumber, buildStarted, buildRepo);
        return Response.ok().entity(issues).build();
    }

    /**
     * Build info page - Release History tab.
     */
    @GET
    @Path("releaseHistory/{name}/{number}/{date}")
    @Produces(APPLICATION_JSON)
    public Response getBuildReleaseHistory(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        List<PromotionStatusModel> releaseHistory =
                getReleaseHistoryUiCommand.getReleaseHistory(buildName, buildNumber, buildStarted, buildRepo);
        return Response.ok().entity(releaseHistory).build();
    }

    /**
     * Build info page - raw build JSON view.
     */
    @GET
    @Path("buildJson/{name}/{number}/{date}")
    @Produces(APPLICATION_JSON)
    public Response getBuildJson(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        ViewArtifactModel buildJson =
                getJsonUiCommand.getBuildJson(buildName, buildNumber, buildStarted, buildRepo);
        return Response.ok().entity(buildJson).build();
    }

    /**
     * Build info page - Effective Permissions tab. Requires manage permission on the build.
     */
    @GET
    @Path("buildEffectivePermission/{name}/{number}/{date}")
    @Produces(APPLICATION_JSON)
    public Response getBuildEffectivePermission(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildDate,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        BuildPermissionsModel permissions =
                getEffectivePermissionUiCommand.getEffectivePermission(buildName, buildNumber, buildDate, buildRepo);
        return Response.ok().entity(permissions).build();
    }

    /**
     * Build info page - Effective Permissions tab: the permission targets granting a specific user access.
     * Build coordinates are passed as query params. Requires manage permission on the build.
     */
    @GET
    @Path("buildEffectivePermissionByEntity/users/{username}")
    @Produces(APPLICATION_JSON)
    public Response getEffectivePermissionByUser(
            @NonNull @PathParam("username") String username,
            @Nullable @QueryParam("buildName") String buildName,
            @Nullable @QueryParam("buildNumber") String buildNumber,
            @Nullable @QueryParam("buildDate") String buildDate,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        List<String> permissions = getEffectivePermissionByUserUiCommand.getEffectivePermissionByUser(
                buildName, buildNumber, buildDate, buildRepo, username);
        return Response.ok().entity(permissions).build();
    }

    /**
     * Build info page - Effective Permissions tab: the permission targets granting a specific group access.
     * Build coordinates are passed as query params. Requires manage permission on the build.
     */
    @GET
    @Path("buildEffectivePermissionByEntity/groups/{groupname}")
    @Produces(APPLICATION_JSON)
    public Response getEffectivePermissionByGroup(
            @NonNull @PathParam("groupname") String groupName,
            @Nullable @QueryParam("buildName") String buildName,
            @Nullable @QueryParam("buildNumber") String buildNumber,
            @Nullable @QueryParam("buildDate") String buildDate,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        List<String> permissions = getEffectivePermissionByGroupUiCommand.getEffectivePermissionByGroup(
                buildName, buildNumber, buildDate, buildRepo, groupName);
        return Response.ok().entity(permissions).build();
    }

    /**
     * Published Modules page - the artifacts of a specific module.
     */
    @GET
    @Path("modulesArtifact/{name}/{number}/{date}/{id}")
    @Produces(APPLICATION_JSON)
    public Response getModulesArtifact(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @NonNull @PathParam("id") String moduleId,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        PagingModel<ModuleArtifactModel> moduleArtifacts =
                getModuleArtifactsUiCommand.getModuleArtifacts(buildName, buildNumber, buildStarted, moduleId,
                        buildRepo);
        return Response.ok().entity(moduleArtifacts).build();
    }

    /**
     * Published Modules page - the dependencies of a specific module.
     */
    @GET
    @Path("modulesDependency/{name}/{number}/{date}/{id}")
    @Produces(APPLICATION_JSON)
    public Response getModulesDependency(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @NonNull @PathParam("id") String moduleId,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        PagingModel<ModuleDependencyModel> moduleDependencies =
                getModuleDependenciesUiCommand.getModuleDependencies(buildName, buildNumber, buildStarted, moduleId,
                        buildRepo);
        return Response.ok().entity(moduleDependencies).build();
    }

    /**
     * Published Modules page - diff of a module's artifacts against another build.
     */
    @GET
    @Path("artifactDiff/{name}/{number}/{date}/{id}")
    @Produces(APPLICATION_JSON)
    public Response artifactDiff(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @NonNull @PathParam("id") String moduleId,
            @Nullable @QueryParam("otherNumber") String comparedBuildNumber,
            @Nullable @QueryParam("otherDate") String comparedBuildDate,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        PagingModel<ModuleArtifactModel> artifactsDiff = getArtifactDiffUiCommand.getArtifactsDiff(
                buildName, buildNumber, buildStarted, moduleId, comparedBuildNumber, comparedBuildDate, buildRepo);
        return Response.ok().entity(artifactsDiff).build();
    }

    /**
     * Published Modules page - diff of a module's dependencies against another build.
     */
    @GET
    @Path("dependencyDiff/{name}/{number}/{date}/{id}")
    @Produces(APPLICATION_JSON)
    public Response dependencyDiff(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @NonNull @PathParam("id") String moduleId,
            @Nullable @QueryParam("otherNumber") String comparedBuildNumber,
            @Nullable @QueryParam("otherDate") String comparedBuildDate,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        PagingModel<ModuleDependencyModel> dependencyDiff = getDependencyDiffUiCommand.getDependencyDiff(
                buildName, buildNumber, buildStarted, moduleId, comparedBuildNumber, comparedBuildDate, buildRepo);
        return Response.ok().entity(dependencyDiff).build();
    }

    /**
     * Environment page - Environment Variables section.
     */
    @GET
    @Path("buildProps/env/{name}/{number}/{date}")
    @Produces(APPLICATION_JSON)
    public Response getEnvBuildProps(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        PagingModel<BuildPropsModel> props =
                getEnvBuildPropsUiCommand.getEnvBuildProps(buildName, buildNumber, buildStarted, buildRepo);
        return Response.ok().entity(props).build();
    }

    /**
     * Environment page - System Variables section.
     */
    @GET
    @Path("buildProps/system/{name}/{number}/{date}")
    @Produces(APPLICATION_JSON)
    public Response getSystemBuildProps(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @NonNull @PathParam("date") String buildStarted,
            @Nullable @QueryParam("buildRepo") String buildRepo) {
        PagingModel<BuildPropsModel> props =
                getSystemBuildPropsUiCommand.getSystemBuildProps(buildName, buildNumber, buildStarted, buildRepo);
        return Response.ok().entity(props).build();
    }

    /**
     * Gets the build-info repository key for a project (defaults to the global build-info repo when no project given).
     */
    @GET
    @Path("repository")
    @Produces(APPLICATION_JSON)
    public Response getBuildInfoRepositoryKey(
            @Nullable @QueryParam("projectKey") String projectKey) {
        BuildRepositoryKeyModel repositoryKey = getRepositoryKeyUiCommand.getBuildInfoRepositoryKey(projectKey);
        return Response.ok().entity(repositoryKey).build();
    }

    /**
     * This API is invoked by the 1st-level screen of "Builds".
     * (the main landing screen with build names and latest versions).
     * <p>
     * Searches build IDs by (case-insensitive) name.
     * Sorted by {@code order_by} or by date by default,
     * with {@code direction} or descending by default.
     */
    @GET
    @Path("search")
    @Produces(APPLICATION_JSON)
    public Response searchBuildsByName(
            @Nullable @QueryParam("name") String buildName,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("projectKey") String projectKey,
            @Nullable @QueryParam("order_by") String orderBy,
            @Nullable @QueryParam("direction") String direction,
            @Nullable @QueryParam("before") String before,
            @Nullable @QueryParam("after") String after,
            @Nullable @QueryParam("num_of_rows") String numOfRows,
            @Nullable @QueryParam("offset") String offset) {
        BuildIdResponse buildIds = searchIdsByNameUiCommand.searchBuildIdsByName(
                buildName,
                buildRepo,
                projectKey,
                orderBy,
                direction,
                before,
                after,
                numOfRows,
                offset
        );
        return Response.ok().entity(buildIds).build();
    }

    /**
     * This API is invoked by the 2nd-level screen of "Builds".
     * (the screen with build versions, which opens after a click from the landing screen).
     * <p>
     * Searches build numbers (versions) for a specific build name.
     * Sorted by {@code order_by} or by date by default,
     * with {@code direction} or descending by default.
     */
    @GET
    @Path("search/{buildName}")
    @Produces(APPLICATION_JSON)
    public Response searchBuildVersions(
            @NonNull @PathParam("buildName") String buildName,
            @Nullable @QueryParam("buildRepo") String buildRepo,
            @Nullable @QueryParam("order_by") String orderBy,
            @Nullable @QueryParam("direction") String direction,
            @Nullable @QueryParam("before") String before,
            @Nullable @QueryParam("after") String after,
            @Nullable @QueryParam("num_of_rows") String numOfRows,
            @Nullable @QueryParam("offset") String offset) {
        BuildNumbersResponse buildNumbers = searchVersionsByNameUiCommand.searchBuildVersionsByName(
                buildName,
                buildRepo,
                orderBy,
                direction,
                before,
                after,
                numOfRows,
                offset
        );
        return Response.ok().entity(buildNumbers).build();
    }

    /**
     * Returns the build-info repository key and the total build count for the given project.
     * Requires project-admin permission. Not supported on Edge for non-default projects.
     */
    @GET
    @Path("projectInfo/{projectKey}")
    @Produces(APPLICATION_JSON)
    public Response getProjectInfo(
            @NonNull @PathParam("projectKey") String projectKey) {
        ProjectBuildsMetadata metadata = getProjectInfoUiCommand.getProjectInfo(projectKey);
        return Response.ok().entity(metadata).build();
    }

    /**
     * Gets build content (packages and standalone artifacts) with pagination, filtering, and sorting.
     */
    @GET
    @Path("content/{name}/{number}")
    @Produces(APPLICATION_JSON)
    public Response getBuildContent(
            @NonNull @PathParam("name") String buildName,
            @NonNull @PathParam("number") String buildNumber,
            @Nullable @QueryParam("buildStarted") String buildStarted,
            @Nullable @QueryParam("repository_key") String repositoryKey,
            @Nullable @QueryParam("projectKey") String projectKey,
            @Nullable @QueryParam("offset") String offset,
            @Nullable @QueryParam("limit") String limit,
            @Nullable @QueryParam("search_term") String searchTerm,
            @Nullable @QueryParam("order_by") String orderBy,
            @Nullable @QueryParam("order_asc") String orderAsc) {
        BuildContentResponse content = getContentUiCommand.getBuildContent(buildName, buildNumber, buildStarted,
                repositoryKey, projectKey, offset, limit, searchTerm, orderBy, !"false".equalsIgnoreCase(orderAsc));
        return Response.ok().entity(content).build();
    }
}
