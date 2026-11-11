package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.model.api.ModuleDependencyModel;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.service.BuildDependencyService;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetDependencyDiffUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";
    private static final String BUILD_STARTED = "1700000000000";
    private static final String MODULE_ID = "org.example:lib";
    private static final String COMPARED_NUMBER = "4";
    private static final String COMPARED_DATE = "1600000000000";

    private BuildGetDependencyDiffUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;
    private BuildDependencyService dependencyService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        dependencyService = mock(BuildDependencyService.class);
        command = new BuildGetDependencyDiffUiCommand(repoService, moduleService, dependencyService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getDependencyDiff_shouldBuildParamsAndConvertDependencies() {
        ModuleDependency dependency = moduleDependency("repo-local", "lib/lib.jar");
        ModuleDependencyModel model = ModuleDependencyModel.builder().repoKey("repo-local").build();
        when(dependencyService.getModuleDependencyForDiffWithPaging(any(BuildParams.class)))
                .thenReturn(List.of(dependency));
        when(moduleService.convertToModuleDependencyModel(dependency)).thenReturn(model);

        PagingModel<ModuleDependencyModel> result = command.getDependencyDiff(
                BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, COMPARED_NUMBER, COMPARED_DATE,
                BUILDINFO_REPO_KEY);

        ArgumentCaptor<BuildParams> paramsCaptor = ArgumentCaptor.forClass(BuildParams.class);
        verify(dependencyService).getModuleDependencyForDiffWithPaging(paramsCaptor.capture());
        BuildParams params = paramsCaptor.getValue();
        assertEquals(BUILD_NAME, params.getBuildName());
        assertEquals(BUILD_NUMBER, params.getCurrBuildNum());
        assertEquals(Long.parseLong(BUILD_STARTED), params.getCurrBuildDate());
        assertEquals(MODULE_ID, params.getBuildModuleId());
        assertEquals(COMPARED_NUMBER, params.getComperedBuildNum());
        assertEquals(Long.parseLong(COMPARED_DATE), params.getComperedBuildDate());
        assertEquals(BUILDINFO_REPO_KEY, params.getBuildRepo());

        assertEquals("0", result.getTotalItems());
        assertEquals(1, result.getPagingData().size());
        assertSame(model, result.getPagingData().getFirst());
    }

    @Test
    void getDependencyDiff_shouldReturnEmptyPagingModel_whenNoDependencies() {
        when(dependencyService.getModuleDependencyForDiffWithPaging(any(BuildParams.class))).thenReturn(List.of());

        PagingModel<ModuleDependencyModel> result = command.getDependencyDiff(
                BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, null, null, BUILDINFO_REPO_KEY);

        assertEquals("0", result.getTotalItems());
        assertTrue(result.getPagingData().isEmpty());
    }

    @Test
    void getDependencyDiff_shouldReturnEmptyPagingModel_whenServiceReturnsNull() {
        when(dependencyService.getModuleDependencyForDiffWithPaging(any(BuildParams.class))).thenReturn(null);

        PagingModel<ModuleDependencyModel> result = command.getDependencyDiff(
                BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, null, null, BUILDINFO_REPO_KEY);

        assertEquals("0", result.getTotalItems());
        assertTrue(result.getPagingData().isEmpty());
    }

    @Test
    void getDependencyDiff_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(dependencyService.getModuleDependencyForDiffWithPaging(any(BuildParams.class))).thenReturn(List.of());

        command.getDependencyDiff(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, null, null, null);

        verify(repoService).normaliseBuildRepoName(null);
        ArgumentCaptor<BuildParams> paramsCaptor = ArgumentCaptor.forClass(BuildParams.class);
        verify(dependencyService).getModuleDependencyForDiffWithPaging(paramsCaptor.capture());
        assertEquals(BUILDINFO_REPO_KEY, paramsCaptor.getValue().getBuildRepo());
    }

    private static ModuleDependency moduleDependency(String repoKey, String path) {
        ModuleDependency dep = new ModuleDependency(repoKey, ".", "lib.jar", "jar", "compile", "sha1");
        dep.setRepoKey(repoKey);
        dep.setPath(path);
        dep.setModule(MODULE_ID);
        return dep;
    }
}
