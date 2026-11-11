package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.PagingModel;
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

class BuildGetArtifactDiffUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";
    private static final String BUILD_STARTED = "1700000000000";
    private static final String MODULE_ID = "org.example:lib";
    private static final String COMPARED_NUMBER = "4";
    private static final String COMPARED_DATE = "1600000000000";

    private BuildGetArtifactDiffUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        command = new BuildGetArtifactDiffUiCommand(repoService, moduleService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getArtifactsDiff_shouldBuildParamsAndConvertArtifacts() {
        ModuleArtifact artifact = moduleArtifact("repo-local", "lib/lib.jar");
        ModuleArtifactModel model = ModuleArtifactModel.builder().repoKey("repo-local").build();
        when(moduleService.getModuleArtifactsForDiffWithPaging(any(BuildParams.class)))
                .thenReturn(List.of(artifact));
        when(moduleService.convertToModuleArtifactModel(artifact)).thenReturn(model);

        PagingModel<ModuleArtifactModel> result = command.getArtifactsDiff(
                BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, COMPARED_NUMBER, COMPARED_DATE,
                BUILDINFO_REPO_KEY);

        ArgumentCaptor<BuildParams> paramsCaptor = ArgumentCaptor.forClass(BuildParams.class);
        verify(moduleService).getModuleArtifactsForDiffWithPaging(paramsCaptor.capture());
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
    void getArtifactsDiff_shouldReturnEmptyPagingModel_whenNoArtifacts() {
        when(moduleService.getModuleArtifactsForDiffWithPaging(any(BuildParams.class))).thenReturn(List.of());

        PagingModel<ModuleArtifactModel> result = command.getArtifactsDiff(
                BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, null, null, BUILDINFO_REPO_KEY);

        assertEquals("0", result.getTotalItems());
        assertTrue(result.getPagingData().isEmpty());
    }

    @Test
    void getArtifactsDiff_shouldReturnEmptyPagingModel_whenServiceReturnsNull() {
        when(moduleService.getModuleArtifactsForDiffWithPaging(any(BuildParams.class))).thenReturn(null);

        PagingModel<ModuleArtifactModel> result = command.getArtifactsDiff(
                BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, null, null, BUILDINFO_REPO_KEY);

        assertEquals("0", result.getTotalItems());
        assertTrue(result.getPagingData().isEmpty());
    }

    @Test
    void getArtifactsDiff_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(moduleService.getModuleArtifactsForDiffWithPaging(any(BuildParams.class))).thenReturn(List.of());

        command.getArtifactsDiff(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, MODULE_ID, null, null, null);

        verify(repoService).normaliseBuildRepoName(null);
        ArgumentCaptor<BuildParams> paramsCaptor = ArgumentCaptor.forClass(BuildParams.class);
        verify(moduleService).getModuleArtifactsForDiffWithPaging(paramsCaptor.capture());
        assertEquals(BUILDINFO_REPO_KEY, paramsCaptor.getValue().getBuildRepo());
    }

    private static ModuleArtifact moduleArtifact(String repoKey, String path) {
        ModuleArtifact artifact = new ModuleArtifact(repoKey, ".", "lib.jar", "jar", "sha1");
        artifact.setRepoKey(repoKey);
        artifact.setPath(path);
        artifact.setModule(MODULE_ID);
        return artifact;
    }
}
