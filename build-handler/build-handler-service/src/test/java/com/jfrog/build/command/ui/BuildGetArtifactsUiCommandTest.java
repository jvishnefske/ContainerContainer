package com.jfrog.build.command.ui;

import com.jfrog.build.model.BuildNodeRecord;
import com.jfrog.build.model.api.BuildArtifactModel;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetArtifactsUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";

    private BuildGetArtifactsUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        command = new BuildGetArtifactsUiCommand(repoService, moduleService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildArtifacts_shouldMapRecordsToModels_whenNoPublishedEpoch() {
        when(moduleService.getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY))
                .thenReturn(List.of(new BuildNodeRecord("repokey", "node_path", "node_name",
                        "da39a3ee5e6b4b0d3255bfef95601890afd80709", "d41d8cd98f00b204e9800998ecf8427e", 1024L)));

        List<BuildArtifactModel> result =
                command.getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY, null);

        verify(repoService).normaliseBuildRepoName(BUILDINFO_REPO_KEY);
        assertEquals(1, result.size());
        BuildArtifactModel artifact = result.getFirst();
        assertEquals("repokey", artifact.getRepo());
        assertEquals("node_path", artifact.getPath());
        assertEquals("node_name", artifact.getName());
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", artifact.getSha1());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", artifact.getMd5());
        assertEquals(1024L, artifact.getSize());
    }

    @Test
    void getBuildArtifacts_shouldQueryByEpoch_whenPublishedEpochProvided() {
        when(moduleService.getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY, 1700000000000L))
                .thenReturn(List.of(new BuildNodeRecord("repokey", "p", "n", "sha1", "md5", 1L)));

        List<BuildArtifactModel> result =
                command.getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY, "1700000000000");

        assertEquals(1, result.size());
        verify(moduleService).getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY, 1700000000000L);
        verify(moduleService, never()).getBuildArtifacts(eq(BUILD_NAME), eq(BUILD_NUMBER), eq(BUILDINFO_REPO_KEY));
    }

    @Test
    void getBuildArtifacts_shouldThrowBadRequest_whenPublishedEpochNotANumber() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY, "9dddd"));

        assertTrue(thrown.getMessage().contains("Published epoch is not a number"));
    }

    @Test
    void getBuildArtifacts_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(moduleService.getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY))
                .thenReturn(List.of());

        command.getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, null, null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(moduleService).getBuildArtifacts(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY);
    }
}
