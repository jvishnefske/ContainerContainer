package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.PublishedModule;
import com.jfrog.build.model.api.BuildModule;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetPublishedModulesUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "1";
    private static final String BUILD_STARTED = "1700000000000";

    private BuildGetPublishedModulesUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        command = new BuildGetPublishedModulesUiCommand(repoService, moduleService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getPublishedModules_shouldMapModulesToPagingModel() {
        // Note the (buildNumber, buildStarted, buildName, buildRepo) positional contract of the service.
        when(moduleService.getPublishedModules(BUILD_NUMBER, BUILD_STARTED, BUILD_NAME, BUILDINFO_REPO_KEY))
                .thenReturn(List.of(
                        publishedModule("module-a", "2", "3", "jar", "build-1", "1"),
                        publishedModule("module-b", "0", "1", "docker", "build-2", "2")));

        PagingModel<BuildModule> result =
                command.getPublishedModules(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);

        verify(repoService).normaliseBuildRepoName(BUILDINFO_REPO_KEY);
        assertEquals("0", result.getTotalItems());
        assertEquals(2, result.getPagingData().size());
        BuildModule first = result.getPagingData().getFirst();
        assertEquals("module-a", first.getModuleId());
        assertEquals("2", first.getNumOfArtifacts());
        assertEquals("3", first.getNumOfDependencies());
        assertEquals("jar", first.getType());
        assertEquals("build-1", first.getBuildId());
        assertEquals("1", first.getBuildNumber());
    }

    @Test
    void getPublishedModules_shouldReturnEmptyPagingModel_whenNoModules() {
        when(moduleService.getPublishedModules(BUILD_NUMBER, BUILD_STARTED, BUILD_NAME, BUILDINFO_REPO_KEY))
                .thenReturn(List.of());

        PagingModel<BuildModule> result =
                command.getPublishedModules(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);

        assertEquals("0", result.getTotalItems());
        assertTrue(result.getPagingData().isEmpty());
    }

    @Test
    void getPublishedModules_shouldReturnNull_whenServiceReturnsNull() {
        when(moduleService.getPublishedModules(BUILD_NUMBER, BUILD_STARTED, BUILD_NAME, BUILDINFO_REPO_KEY))
                .thenReturn(null);

        PagingModel<BuildModule> result =
                command.getPublishedModules(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);

        assertNull(result);
    }

    @Test
    void getPublishedModules_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(moduleService.getPublishedModules(BUILD_NUMBER, BUILD_STARTED, BUILD_NAME, BUILDINFO_REPO_KEY))
                .thenReturn(List.of());

        command.getPublishedModules(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(moduleService).getPublishedModules(BUILD_NUMBER, BUILD_STARTED, BUILD_NAME, BUILDINFO_REPO_KEY);
    }

    private static PublishedModule publishedModule(
            String id, String numOfArtifact, String numOfDependencies, String type, String buildId,
            String buildNumber) {
        return PublishedModule.builder()
                .id(id)
                .numOfArtifact(numOfArtifact)
                .numOfDependencies(numOfDependencies)
                .type(type)
                .buildId(buildId)
                .buildNumber(buildNumber)
                .build();
    }
}
