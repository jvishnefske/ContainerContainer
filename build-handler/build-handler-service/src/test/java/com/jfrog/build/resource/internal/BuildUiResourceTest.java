package com.jfrog.build.resource.internal;

import com.jfrog.build.acl.model.BuildCoordinate;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.jfrog.commons.continues.ContinueResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildUiResourceTest {

    private BuildUiResource buildUiResource;

    private BuildDeleteAllUiCommand deleteAllUiCommand;
    private BuildDeleteUiCommand deleteUiCommand;
    private BuildGetAllNamesPagedUiCommand getAllNamesPagedUiCommand;
    private BuildGetAllNamesUiCommand getAllNamesUiCommand;
    private BuildGetAllUiCommand getAllUiCommand;
    private BuildGetArtifactDiffUiCommand getArtifactDiffUiCommand;
    private BuildGetArtifactsUiCommand getArtifactsUiCommand;
    private BuildGetBuildDiffUiCommand getBuildDiffUiCommand;
    private BuildGetBuildIssuesUiCommand getBuildIssuesUiCommand;
    private BuildGetContentUiCommand getContentUiCommand;
    private BuildGetDependencyDiffUiCommand getDependencyDiffUiCommand;
    private BuildGetEffectivePermissionByGroupUiCommand getEffectivePermissionByGroupUiCommand;
    private BuildGetEffectivePermissionByUserUiCommand getEffectivePermissionByUserUiCommand;
    private BuildGetEffectivePermissionUiCommand getEffectivePermissionUiCommand;
    private BuildGetEnvBuildPropsUiCommand getEnvBuildPropsUiCommand;
    private BuildGetGeneralInfoUiCommand getGeneralInfoUiCommand;
    private BuildGetHistoryUiCommand getHistoryUiCommand;
    private BuildGetJsonUiCommand getJsonUiCommand;
    private BuildGetModuleArtifactsUiCommand getModuleArtifactsUiCommand;
    private BuildGetModuleDependenciesUiCommand getModuleDependenciesUiCommand;
    private BuildGetPrevBuildsUiCommand getPrevBuildsUiCommand;
    private BuildGetProjectInfoUiCommand getProjectInfoUiCommand;
    private BuildGetPublishedModulesUiCommand getPublishedModulesUiCommand;
    private BuildGetReleaseHistoryUiCommand getReleaseHistoryUiCommand;
    private BuildGetRepositoryKeyUiCommand getRepositoryKeyUiCommand;
    private BuildGetSystemBuildPropsUiCommand getSystemBuildPropsUiCommand;
    private BuildSearchIdsByNameUiCommand searchIdsByNameUiCommand;
    private BuildSearchVersionsByNameUiCommand searchVersionsByNameUiCommand;

    @BeforeEach
    void setUp() {
        deleteAllUiCommand = mock(BuildDeleteAllUiCommand.class);
        deleteUiCommand = mock(BuildDeleteUiCommand.class);
        getAllNamesPagedUiCommand = mock(BuildGetAllNamesPagedUiCommand.class);
        getAllNamesUiCommand = mock(BuildGetAllNamesUiCommand.class);
        getAllUiCommand = mock(BuildGetAllUiCommand.class);
        getArtifactDiffUiCommand = mock(BuildGetArtifactDiffUiCommand.class);
        getArtifactsUiCommand = mock(BuildGetArtifactsUiCommand.class);
        getBuildDiffUiCommand = mock(BuildGetBuildDiffUiCommand.class);
        getBuildIssuesUiCommand = mock(BuildGetBuildIssuesUiCommand.class);
        getContentUiCommand = mock(BuildGetContentUiCommand.class);
        getDependencyDiffUiCommand = mock(BuildGetDependencyDiffUiCommand.class);
        getEffectivePermissionByGroupUiCommand = mock(BuildGetEffectivePermissionByGroupUiCommand.class);
        getEffectivePermissionByUserUiCommand = mock(BuildGetEffectivePermissionByUserUiCommand.class);
        getEffectivePermissionUiCommand = mock(BuildGetEffectivePermissionUiCommand.class);
        getEnvBuildPropsUiCommand = mock(BuildGetEnvBuildPropsUiCommand.class);
        getGeneralInfoUiCommand = mock(BuildGetGeneralInfoUiCommand.class);
        getHistoryUiCommand = mock(BuildGetHistoryUiCommand.class);
        getJsonUiCommand = mock(BuildGetJsonUiCommand.class);
        getModuleArtifactsUiCommand = mock(BuildGetModuleArtifactsUiCommand.class);
        getModuleDependenciesUiCommand = mock(BuildGetModuleDependenciesUiCommand.class);
        getPrevBuildsUiCommand = mock(BuildGetPrevBuildsUiCommand.class);
        getProjectInfoUiCommand = mock(BuildGetProjectInfoUiCommand.class);
        getPublishedModulesUiCommand = mock(BuildGetPublishedModulesUiCommand.class);
        getReleaseHistoryUiCommand = mock(BuildGetReleaseHistoryUiCommand.class);
        getRepositoryKeyUiCommand = mock(BuildGetRepositoryKeyUiCommand.class);
        getSystemBuildPropsUiCommand = mock(BuildGetSystemBuildPropsUiCommand.class);
        searchIdsByNameUiCommand = mock(BuildSearchIdsByNameUiCommand.class);
        searchVersionsByNameUiCommand = mock(BuildSearchVersionsByNameUiCommand.class);
        buildUiResource = new BuildUiResource(
                deleteAllUiCommand, deleteUiCommand, getAllNamesPagedUiCommand, getAllNamesUiCommand, getAllUiCommand,
                getArtifactDiffUiCommand, getArtifactsUiCommand, getBuildDiffUiCommand, getBuildIssuesUiCommand,
                getContentUiCommand,
                getDependencyDiffUiCommand, getEffectivePermissionByGroupUiCommand,
                getEffectivePermissionByUserUiCommand, getEffectivePermissionUiCommand,
                getEnvBuildPropsUiCommand, getGeneralInfoUiCommand, getHistoryUiCommand, getJsonUiCommand,
                getModuleArtifactsUiCommand, getModuleDependenciesUiCommand,
                getPrevBuildsUiCommand, getProjectInfoUiCommand, getPublishedModulesUiCommand,
                getReleaseHistoryUiCommand,
                getRepositoryKeyUiCommand, getSystemBuildPropsUiCommand, searchIdsByNameUiCommand,
                searchVersionsByNameUiCommand);
    }

    @Test
    void deleteAllBuilds_shouldReturnOkAndMessageFromCommand() {
        List<BuildCoordinate> coordinates = List.of(new BuildCoordinate("build-a", BUILDINFO_REPO_KEY));
        DeleteBuildsModel model = new DeleteBuildsModel(coordinates);
        when(deleteAllUiCommand.deleteAllBuilds(coordinates)).thenReturn("Successfully removed build-a build project");

        Response response = buildUiResource.deleteAllBuilds(model);

        assertEquals(200, response.getStatus());
        assertEquals("Successfully removed build-a build project", response.getEntity());
        verify(deleteAllUiCommand).deleteAllBuilds(coordinates);
    }

    @Test
    void deleteBuild_shouldReturnOkAndMessageFromCommand() {
        List<BuildCoordinate> coordinates = List.of(new BuildCoordinate("build-a", "1", 0L, BUILDINFO_REPO_KEY));
        DeleteBuildsModel model = new DeleteBuildsModel(coordinates);
        when(deleteUiCommand.deleteBuilds(coordinates)).thenReturn("Successfully removed build-a #1 build");

        Response response = buildUiResource.deleteBuild(model);

        assertEquals(200, response.getStatus());
        assertEquals("Successfully removed build-a #1 build", response.getEntity());
        verify(deleteUiCommand).deleteBuilds(coordinates);
    }

    @Test
    void getAllBuildNames_shouldReturnOkAndEntityFromCommand() {
        List<String> buildNames = List.of("build-a", "build-b");
        when(getAllNamesUiCommand.getAllBuildNames(BUILDINFO_REPO_KEY)).thenReturn(buildNames);

        Response response = buildUiResource.getAllBuildNames(BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(buildNames, response.getEntity());
        verify(getAllNamesUiCommand).getAllBuildNames(BUILDINFO_REPO_KEY);
    }

    @Test
    void getAllBuildNamesPaged_shouldReturnOkAndEntityFromCommand() {
        Map<String, String[]> params = Map.of("limit", new String[] {"10"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(params);
        ContinueResult<String> body = new ContinueResult<>(null, List.of("build-a", "build-b"));
        when(getAllNamesPagedUiCommand.getAllBuildNamesPaged(same(params))).thenReturn(body);

        Response response = buildUiResource.getAllBuildNamesPaged(request);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getAllNamesPagedUiCommand).getAllBuildNamesPaged(params);
    }

    @Test
    void getAllBuilds_shouldReturnOkAndEntityFromCommand() {
        Map<String, String[]> params = Map.of("limit", new String[] {"10"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(params);
        ContinueResult<GeneralBuildInfo> body = new ContinueResult<>(
                null,
                List.of(GeneralBuildInfo.builder().buildName("some-build-name").build()));
        when(getAllUiCommand.getAllBuilds(same(params))).thenReturn(body);

        Response response = buildUiResource.getAllBuilds(request);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getAllUiCommand).getAllBuilds(params);
    }

    @Test
    void getBuildHistory_shouldStripLeadingSlashFromNameAndReturnEntityFromCommand() {
        Map<String, String[]> params = Map.of("limit", new String[] {"10"});
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(params);
        ContinueResult<GeneralBuildInfo> body = new ContinueResult<>(
                null,
                List.of(GeneralBuildInfo.builder().buildNumber("1").build()));
        when(getHistoryUiCommand.getBuildHistory(eq("my-build"), eq(BUILDINFO_REPO_KEY), same(params)))
                .thenReturn(body);

        // The JAX-RS path template hands the value over with a leading slash.
        Response response = buildUiResource.getBuildHistory("/my-build", BUILDINFO_REPO_KEY, request);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getHistoryUiCommand).getBuildHistory("my-build", BUILDINFO_REPO_KEY, params);
    }

    @Test
    void getBuildHistory_shouldPassEmptyName_whenPathHasNoName() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Map.of());
        when(getHistoryUiCommand.getBuildHistory(eq(""), eq(BUILDINFO_REPO_KEY), same(Map.of())))
                .thenReturn(new ContinueResult<>(null, List.of()));

        Response response = buildUiResource.getBuildHistory(null, BUILDINFO_REPO_KEY, request);

        assertEquals(200, response.getStatus());
        verify(getHistoryUiCommand).getBuildHistory("", BUILDINFO_REPO_KEY, Map.of());
    }

    @Test
    void getPrevBuild_shouldReturnOkAndEntityFromCommand() {
        List<GeneralBuildInfo> body = List.of(GeneralBuildInfo.builder().buildName("my-build").buildNumber("1").build());
        when(getPrevBuildsUiCommand.getPrevBuilds("my-build", "2024-01-01T00:00:00.000+0000", BUILDINFO_REPO_KEY))
                .thenReturn(body);

        Response response =
                buildUiResource.getPrevBuild("my-build", "2024-01-01T00:00:00.000+0000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getPrevBuildsUiCommand).getPrevBuilds("my-build", "2024-01-01T00:00:00.000+0000", BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildGeneralInfo_shouldStripLeadingSlashFromDateAndReturnEntityFromCommand() {
        GeneralBuildInfo body = GeneralBuildInfo.builder().buildName("my-build").buildNumber("1").build();
        when(getGeneralInfoUiCommand.getBuildGeneralInfo("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY))
                .thenReturn(body);

        // The JAX-RS date path template hands the value over with a leading slash.
        Response response = buildUiResource.getBuildGeneralInfo("my-build", "1", "/1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getGeneralInfoUiCommand).getBuildGeneralInfo("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildGeneralInfo_shouldPassEmptyDate_whenNoDateSegment() {
        GeneralBuildInfo body = GeneralBuildInfo.builder().buildName("my-build").buildNumber("1").build();
        when(getGeneralInfoUiCommand.getBuildGeneralInfo("my-build", "1", "", BUILDINFO_REPO_KEY)).thenReturn(body);

        Response response = buildUiResource.getBuildGeneralInfo("my-build", "1", null, BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        verify(getGeneralInfoUiCommand).getBuildGeneralInfo("my-build", "1", "", BUILDINFO_REPO_KEY);
    }

    @Test
    void getPublishedModules_shouldReturnOkAndEntityFromCommand() {
        PagingModel<BuildModule> body = new PagingModel<>(0, List.of());
        when(getPublishedModulesUiCommand.getPublishedModules("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY))
                .thenReturn(body);

        Response response =
                buildUiResource.getPublishedModules("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getPublishedModulesUiCommand).getPublishedModules("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);
    }

    @Test
    void buildDiff_shouldReturnOkAndEntityFromCommand() {
        BuildDiffModel body = new BuildDiffModel();
        when(getBuildDiffUiCommand.getBuildDiff("my-build", "5", "1700000000000", "4", "1600000000000",
                BUILDINFO_REPO_KEY, false)).thenReturn(body);

        Response response = buildUiResource.buildDiff("my-build", "5", "1700000000000", "4", "1600000000000",
                BUILDINFO_REPO_KEY, false);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getBuildDiffUiCommand).getBuildDiff("my-build", "5", "1700000000000", "4", "1600000000000",
                BUILDINFO_REPO_KEY, false);
    }

    @Test
    void buildDiff_shouldForwardExcludeDependenciesFlag() {
        BuildDiffModel body = new BuildDiffModel();
        when(getBuildDiffUiCommand.getBuildDiff("my-build", "5", "1700000000000", "4", "1600000000000",
                BUILDINFO_REPO_KEY, true)).thenReturn(body);

        Response response = buildUiResource.buildDiff("my-build", "5", "1700000000000", "4", "1600000000000",
                BUILDINFO_REPO_KEY, true);

        assertEquals(200, response.getStatus());
        verify(getBuildDiffUiCommand).getBuildDiff("my-build", "5", "1700000000000", "4", "1600000000000",
                BUILDINFO_REPO_KEY, true);
    }

    @Test
    void getBuildIssues_shouldReturnOkAndListFromCommand() {
        List<IssueModel> body = List.of(
                IssueModel.builder().key("PROJ-1").url("http://jira/PROJ-1").summary("bug").build());
        when(getBuildIssuesUiCommand.getBuildIssues("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY))
                .thenReturn(body);

        Response response = buildUiResource.getBuildIssues("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getBuildIssuesUiCommand).getBuildIssues("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildArtifacts_shouldReturnOkAndEntityFromCommandWithNoEpoch() {
        List<BuildArtifactModel> body = List.of();
        when(getArtifactsUiCommand.getBuildArtifacts("my-build", "1", BUILDINFO_REPO_KEY, null)).thenReturn(body);

        Response response = buildUiResource.getBuildArtifacts("my-build", "1", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getArtifactsUiCommand).getBuildArtifacts("my-build", "1", BUILDINFO_REPO_KEY, null);
    }

    @Test
    void getBuildArtifactsPublishedEpoch_shouldReturnOkAndPassEpochToCommand() {
        List<BuildArtifactModel> body = List.of();
        when(getArtifactsUiCommand.getBuildArtifacts("my-build", "1", BUILDINFO_REPO_KEY, "1700000000000"))
                .thenReturn(body);

        Response response =
                buildUiResource.getBuildArtifactsPublishedEpoch("my-build", "1", BUILDINFO_REPO_KEY, "1700000000000");

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getArtifactsUiCommand).getBuildArtifacts("my-build", "1", BUILDINFO_REPO_KEY, "1700000000000");
    }

    @Test
    void getModulesArtifact_shouldReturnOkAndEntityFromCommand() {
        PagingModel<ModuleArtifactModel> body = new PagingModel<>(0, List.of());
        when(getModuleArtifactsUiCommand.getModuleArtifacts("my-build", "1", "1700000000000", "org.example:lib",
                BUILDINFO_REPO_KEY)).thenReturn(body);

        Response response = buildUiResource.getModulesArtifact("my-build", "1", "1700000000000", "org.example:lib",
                BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getModuleArtifactsUiCommand).getModuleArtifacts("my-build", "1", "1700000000000", "org.example:lib",
                BUILDINFO_REPO_KEY);
    }

    @Test
    void getModulesDependency_shouldReturnOkAndEntityFromCommand() {
        PagingModel<ModuleDependencyModel> body = new PagingModel<>(0, List.of());
        when(getModuleDependenciesUiCommand.getModuleDependencies("my-build", "1", "1700000000000", "org.example:lib",
                BUILDINFO_REPO_KEY)).thenReturn(body);

        Response response = buildUiResource.getModulesDependency("my-build", "1", "1700000000000", "org.example:lib",
                BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getModuleDependenciesUiCommand).getModuleDependencies("my-build", "1", "1700000000000",
                "org.example:lib", BUILDINFO_REPO_KEY);
    }

    @Test
    void artifactDiff_shouldReturnOkAndEntityFromCommand() {
        PagingModel<ModuleArtifactModel> body = new PagingModel<>(0, List.of());
        when(getArtifactDiffUiCommand.getArtifactsDiff("my-build", "5", "1700000000000", "org.example:lib",
                "4", "1600000000000", BUILDINFO_REPO_KEY)).thenReturn(body);

        Response response = buildUiResource.artifactDiff("my-build", "5", "1700000000000", "org.example:lib",
                "4", "1600000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getArtifactDiffUiCommand).getArtifactsDiff("my-build", "5", "1700000000000", "org.example:lib",
                "4", "1600000000000", BUILDINFO_REPO_KEY);
    }

    @Test
    void dependencyDiff_shouldReturnOkAndEntityFromCommand() {
        PagingModel<ModuleDependencyModel> body = new PagingModel<>(0, List.of());
        when(getDependencyDiffUiCommand.getDependencyDiff("my-build", "5", "1700000000000", "org.example:lib",
                "4", "1600000000000", BUILDINFO_REPO_KEY)).thenReturn(body);

        Response response = buildUiResource.dependencyDiff("my-build", "5", "1700000000000", "org.example:lib",
                "4", "1600000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getDependencyDiffUiCommand).getDependencyDiff("my-build", "5", "1700000000000", "org.example:lib",
                "4", "1600000000000", BUILDINFO_REPO_KEY);
    }

    @Test
    void getEnvBuildProps_shouldReturnOkAndEntityFromCommand() {
        PagingModel<BuildPropsModel> body = new PagingModel<>(0, List.of());
        when(getEnvBuildPropsUiCommand.getEnvBuildProps("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY))
                .thenReturn(body);

        Response response = buildUiResource.getEnvBuildProps("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getEnvBuildPropsUiCommand).getEnvBuildProps("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildJson_shouldReturnOkAndEntityFromCommand() {
        ViewArtifactModel body = ViewArtifactModel.builder().fileContent("{\"version\":\"1\"}").build();
        when(getJsonUiCommand.getBuildJson("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY)).thenReturn(body);

        Response response = buildUiResource.getBuildJson("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getJsonUiCommand).getBuildJson("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildEffectivePermission_shouldReturnOkAndEntityFromCommand() {
        BuildPermissionsModel body = new BuildPermissionsModel(List.of(), List.of(), List.of());
        when(getEffectivePermissionUiCommand.getEffectivePermission("my-build", "1", "1700000000000",
                BUILDINFO_REPO_KEY)).thenReturn(body);

        Response response =
                buildUiResource.getBuildEffectivePermission("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getEffectivePermissionUiCommand).getEffectivePermission("my-build", "1", "1700000000000",
                BUILDINFO_REPO_KEY);
    }

    @Test
    void getEffectivePermissionByUser_shouldReturnOkAndListFromCommand() {
        List<String> body = List.of("target-a", "target-b");
        when(getEffectivePermissionByUserUiCommand.getEffectivePermissionByUser("my-build", "1", "1700000000000",
                BUILDINFO_REPO_KEY, "alice")).thenReturn(body);

        Response response = buildUiResource.getEffectivePermissionByUser("alice", "my-build", "1", "1700000000000",
                BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getEffectivePermissionByUserUiCommand).getEffectivePermissionByUser("my-build", "1", "1700000000000",
                BUILDINFO_REPO_KEY, "alice");
    }

    @Test
    void getEffectivePermissionByGroup_shouldReturnOkAndListFromCommand() {
        List<String> body = List.of("target-a", "target-b");
        when(getEffectivePermissionByGroupUiCommand.getEffectivePermissionByGroup("my-build", "1", "1700000000000",
                BUILDINFO_REPO_KEY, "developers")).thenReturn(body);

        Response response = buildUiResource.getEffectivePermissionByGroup("developers", "my-build", "1",
                "1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getEffectivePermissionByGroupUiCommand).getEffectivePermissionByGroup("my-build", "1", "1700000000000",
                BUILDINFO_REPO_KEY, "developers");
    }

    @Test
    void getBuildInfoRepositoryKey_shouldReturnOkAndEntityFromCommand() {
        BuildRepositoryKeyModel body = new BuildRepositoryKeyModel("my-project-build-info");
        when(getRepositoryKeyUiCommand.getBuildInfoRepositoryKey("my-project")).thenReturn(body);

        Response response = buildUiResource.getBuildInfoRepositoryKey("my-project");

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getRepositoryKeyUiCommand).getBuildInfoRepositoryKey("my-project");
    }

    @Test
    void searchBuildsByName_shouldReturnOkAndEntityFromCommand() {
        BuildIdResponse body = new BuildIdResponse();
        when(searchIdsByNameUiCommand.searchBuildIdsByName("my-build", "repo-local", "proj", "build_date", "asc",
                "100", "50", "25", "5")).thenReturn(body);

        Response response = buildUiResource.searchBuildsByName("my-build", "repo-local", "proj", "build_date", "asc",
                "100", "50", "25", "5");

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(searchIdsByNameUiCommand).searchBuildIdsByName("my-build", "repo-local", "proj", "build_date", "asc",
                "100", "50", "25", "5");
    }

    @Test
    void searchBuildVersions_shouldReturnOkAndEntityFromCommand() {
        BuildNumbersResponse body = new BuildNumbersResponse();
        when(searchVersionsByNameUiCommand.searchBuildVersionsByName("my-build", "repo-local", "build_date", "asc",
                "100", "50", "25", "5")).thenReturn(body);

        Response response = buildUiResource.searchBuildVersions("my-build", "repo-local", "build_date", "asc",
                "100", "50", "25", "5");

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(searchVersionsByNameUiCommand).searchBuildVersionsByName("my-build", "repo-local", "build_date", "asc",
                "100", "50", "25", "5");
    }

    @Test
    void getBuildContent_shouldReturnOkAndEntityFromCommand() {
        BuildContentResponse body = BuildContentResponse.builder().releasables(List.of()).total(0).build();
        when(getContentUiCommand.getBuildContent("my-build", "1", "1700000000000", "repo-local", "proj", "0", "10",
                "term", "name", true)).thenReturn(body);

        Response response = buildUiResource.getBuildContent("my-build", "1", "1700000000000", "repo-local", "proj",
                "0", "10", "term", "name", "true");

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getContentUiCommand).getBuildContent("my-build", "1", "1700000000000", "repo-local", "proj", "0", "10",
                "term", "name", true);
    }

    @Test
    void getProjectInfo_shouldReturnOkAndEntityFromCommand() {
        ProjectBuildsMetadata body =
                ProjectBuildsMetadata.builder().buildInfoRepo("my-project-build-info").buildsCount(5).build();
        when(getProjectInfoUiCommand.getProjectInfo("my-project")).thenReturn(body);

        Response response = buildUiResource.getProjectInfo("my-project");

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getProjectInfoUiCommand).getProjectInfo("my-project");
    }

    @Test
    void getBuildReleaseHistory_shouldReturnOkAndListFromCommand() {
        List<PromotionStatusModel> body = List.of(
                PromotionStatusModel.builder().status("Released").repository("release-local").build());
        when(getReleaseHistoryUiCommand.getReleaseHistory("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY))
                .thenReturn(body);

        Response response =
                buildUiResource.getBuildReleaseHistory("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getReleaseHistoryUiCommand).getReleaseHistory("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);
    }

    @Test
    void getSystemBuildProps_shouldReturnOkAndEntityFromCommand() {
        PagingModel<BuildPropsModel> body = new PagingModel<>(0, List.of());
        when(getSystemBuildPropsUiCommand.getSystemBuildProps("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY))
                .thenReturn(body);

        Response response =
                buildUiResource.getSystemBuildProps("my-build", "1", "1700000000000", BUILDINFO_REPO_KEY);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getSystemBuildPropsUiCommand).getSystemBuildProps("my-build", "1", "1700000000000",
                BUILDINFO_REPO_KEY);
    }
}
