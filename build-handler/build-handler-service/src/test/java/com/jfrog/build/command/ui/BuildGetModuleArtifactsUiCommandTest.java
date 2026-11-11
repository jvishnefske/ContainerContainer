package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.BadRequestException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BuildGetModuleArtifactsUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";
    private static final String BUILD_STARTED = "1700000000000";
    private static final String MODULE_ID = "org.example:lib";

    private BuildGetModuleArtifactsUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        command = new BuildGetModuleArtifactsUiCommand(repoService, moduleService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getModuleArtifacts_shouldConvertEachArtifactAndWrapInPagingModel() {
        ModuleArtifact artifact = moduleArtifact("repo-local", "lib/lib.jar");
        ModuleArtifactModel model = ModuleArtifactModel.builder().repoKey("repo-local").build();
        when(moduleService.getModuleArtifact(BUILD_NAME, BUILD_NUMBER, MODULE_ID, BUILD_STARTED, BUILDINFO_REPO_KEY))
                .thenReturn(List.of(artifact));
        when(moduleService.convertToModuleArtifactModel(artifact)).thenReturn(model);

        PagingModel<ModuleArtifactModel> result =
                command.getModuleArtifacts(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, BUILDINFO_REPO_KEY);

        verify(repoService).normaliseBuildRepoName(BUILDINFO_REPO_KEY);
        assertEquals("0", result.getTotalItems());
        assertEquals(1, result.getPagingData().size());
        assertSame(model, result.getPagingData().getFirst());
    }

    @Test
    void getModuleArtifacts_shouldThrowNotFound_whenServiceReturnsNull() {
        when(moduleService.getModuleArtifact(BUILD_NAME, BUILD_NUMBER, MODULE_ID, BUILD_STARTED, BUILDINFO_REPO_KEY))
                .thenReturn(null);

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> command.getModuleArtifacts(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, BUILDINFO_REPO_KEY));

        assertTrue(thrown.getMessage().contains("Failed to fetch module artifacts"));
    }

    @Test
    void getModuleArtifacts_shouldThrowBadRequest_whenBuildStartedNotANumber() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.getModuleArtifacts(BUILD_NAME, BUILD_NUMBER, "not-a-number", MODULE_ID,
                        BUILDINFO_REPO_KEY));

        assertTrue(thrown.getMessage().contains("Build started is not a number"));
        verifyNoInteractions(moduleService);
    }

    @Test
    void getModuleArtifacts_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(moduleService.getModuleArtifact(BUILD_NAME, BUILD_NUMBER, MODULE_ID, BUILD_STARTED, BUILDINFO_REPO_KEY))
                .thenReturn(List.of());

        command.getModuleArtifacts(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(moduleService).getModuleArtifact(BUILD_NAME, BUILD_NUMBER, MODULE_ID, BUILD_STARTED, BUILDINFO_REPO_KEY);
    }

    private static ModuleArtifact moduleArtifact(String repoKey, String path) {
        ModuleArtifact artifact = new ModuleArtifact(repoKey, ".", "lib.jar", "jar", "sha1");
        artifact.setRepoKey(repoKey);
        artifact.setPath(path);
        artifact.setModule(MODULE_ID);
        return artifact;
    }
}
