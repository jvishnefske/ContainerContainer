package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.ModuleDependencyModel;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.service.BuildDependencyService;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetModuleDependenciesUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";
    private static final String BUILD_STARTED = "1700000000000";
    private static final String MODULE_ID = "org.example:lib";

    private BuildGetModuleDependenciesUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;
    private BuildDependencyService dependencyService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        dependencyService = mock(BuildDependencyService.class);
        command = new BuildGetModuleDependenciesUiCommand(repoService, moduleService, dependencyService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getModuleDependencies_shouldConvertDependenciesAndReportSizeAsTotal() {
        ModuleDependency dependency = dependency("repo-local", "org/dep.jar");
        ModuleDependencyModel model = ModuleDependencyModel.builder().repoKey("repo-local").build();
        when(dependencyService.getModuleDependency(MODULE_ID, BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY)).thenReturn(List.of(dependency));
        when(moduleService.convertToModuleDependencyModel(dependency)).thenReturn(model);

        PagingModel<ModuleDependencyModel> result =
                command.getModuleDependencies(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, BUILDINFO_REPO_KEY);

        verify(repoService).normaliseBuildRepoName(BUILDINFO_REPO_KEY);
        assertEquals("1", result.getTotalItems());
        assertEquals(1, result.getPagingData().size());
        assertSame(model, result.getPagingData().getFirst());
    }

    @Test
    void getModuleDependencies_shouldThrowNotFound_whenServiceReturnsNull() {
        when(dependencyService.getModuleDependency(MODULE_ID, BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY)).thenReturn(null);

        NotFoundException thrown = assertThrows(NotFoundException.class, () ->
                command.getModuleDependencies(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, BUILDINFO_REPO_KEY));

        assertTrue(thrown.getMessage().contains("Failed to fetch module dependencies"));
    }

    @Test
    void getModuleDependencies_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(dependencyService.getModuleDependency(MODULE_ID, BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY)).thenReturn(List.of());

        command.getModuleDependencies(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(dependencyService).getModuleDependency(MODULE_ID, BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY);
    }

    private static ModuleDependency dependency(String repoKey, String path) {
        ModuleDependency dependency = new ModuleDependency(repoKey, path, "dep.jar", "jar", "compile", "sha1");
        dependency.setModule(MODULE_ID);
        return dependency;
    }
}
