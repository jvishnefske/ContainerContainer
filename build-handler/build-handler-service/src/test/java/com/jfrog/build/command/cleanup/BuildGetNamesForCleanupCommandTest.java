package com.jfrog.build.command.cleanup;

import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.model.api.cleanup.BuildNameEntry;
import com.jfrog.build.model.api.cleanup.BuildNamesResponse;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildProjectService;
import com.jfrog.build.service.BuildRepoService;
import lombok.NonNull;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.access.client.project.ProjectEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetNamesForCleanupCommandTest {

    private BuildGetNamesForCleanupCommand command;

    private BuildRepoService repoService;
    private BuildProjectService projectService;
    private BuildDatabaseService databaseService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        projectService = mock(BuildProjectService.class);
        databaseService = mock(BuildDatabaseService.class);
        command = new BuildGetNamesForCleanupCommand(repoService, projectService, databaseService);
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    PROJECT_KEY,     BUILD_INFO_REPO_KEY
                    default,         artifactory-build-info
                    default,         artifactory-build-info-2
                    phoenix,         phoenix-build-info
                    another-proj-2,  another-proj-2-build-info
                    """
    )
    void getBuildNames_withValidParams(String projectKey, String repoKey) {
        List<BuildNameAndRepo> buildNamesAndRepos = List.of(
                new BuildNameAndRepo("some-build-name-a", repoKey),
                new BuildNameAndRepo("some-build-name-b", repoKey)
        );
        ProjectEntity projectEntity = mock(ProjectEntity.class);
        when(projectEntity.getName()).thenReturn("Some project display name");
        when(projectService.getProjectsByKeys()).thenReturn(Map.of(projectKey, projectEntity));
        when(repoService.getBuildInfoRepoKeyByProject(projectKey, false)).thenReturn(repoKey);
        when(databaseService.getBuildNames(eq(List.of(repoKey)), isNull(), anyLong(), anyLong()))
                .thenReturn(buildNamesAndRepos);

        BuildNamesResponse response = command.getBuildNames(null, projectKey, 0L, 1000L);

        assertNotNull(response);
        assertEquals(2, response.getBuilds().size());
    }

    @Test
    void getBuildNames_withInvalidBuildName_throwsException() {
        assertThrows(BadRequestException.class,
                () -> command.getBuildNames("*", null, 0L, 1000L));
    }

    @Test
    void verifyProjectAdmin_withNonAdmin_throwsException() {
        ProjectEntity projectEntity = mock(ProjectEntity.class);
        when(projectService.getProjectsByKeys()).thenReturn(Map.of("phoenix", projectEntity));
        doThrow(new ForbiddenException("not admin")).when(projectService).verifyProjectAdmin("phoenix");

        assertThrows(ForbiddenException.class,
                () -> command.getBuildNames("some-build-name", "phoenix", 0L, 1000L));
    }

    @Test
    void verifyProjectsExist_withNonExistentProject_throwsException() {
        when(projectService.getProjectsByKeys()).thenReturn(Map.of());

        assertThrows(NotFoundException.class,
                () -> command.getBuildNames("some-build-name", "nonexistent-project", 0L, 1000L));
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    BUILD_INFO_REPO_KEY,                  EXPECTED_PROJECT_KEY
                    artifactory-build-info,               default
                    phoenix-build-info,                   phoenix
                    artifactory-build-info-1,             default
                    proj-2-build-info,                    proj-2
                    build-info-build-info,                build-info
                    proj-2-build-info-build-info,         proj-2-build-info
                    proj-2-build-info-proj-2-build-info,  proj-2-build-info-proj-2
                    build-info-proj-2-build-info,         build-info-proj-2
                    another-proj-2-build-info,            another-proj-2
                    """
    )
    void convertRepoKeyToProjectKey(@NonNull String repoKey, @NonNull String expectedProjectKey) {
        assertEquals(expectedProjectKey, command.convertRepoKeyToProjectKey(repoKey));
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    BUILD_INFO_REPO_KEY
                    invalid-key
                    invalid-keybuild-info
                    invalid-keyBuild-info
                    invalid-key-Build-info
                    """
    )
    void convertRepoKeyToProjectKey_whenInvalidKey_throwsException(@NonNull String repoKey) {
        assertThrows(BadRequestException.class, () -> command.convertRepoKeyToProjectKey(repoKey));
    }

    @Test
    void getBuildName() {
        ProjectEntity projectEntity = mock(ProjectEntity.class);
        Map<String, ProjectEntity> projectKeyToEntityMap = Map.of("phoenix", projectEntity);
        when(projectEntity.getName()).thenReturn("Phoenix project");
        BuildNameAndRepo buildNameAndRepo = new BuildNameAndRepo("some-build-name", "phoenix-build-info");

        BuildNameEntry buildNameEntry = command.getBuildName(buildNameAndRepo, projectKeyToEntityMap);

        assertNotNull(buildNameEntry);
        assertEquals("some-build-name", buildNameEntry.getBuildName());
        assertEquals("phoenix-build-info", buildNameEntry.getBuildRepo());
        assertEquals("phoenix", buildNameEntry.getProjectKey());
        assertEquals("Phoenix project", buildNameEntry.getProjectName());
    }

    @Test
    void getBuildName_ofDeletedProject() {
        Map<String, ProjectEntity> projectKeyToEntityMap = Map.of();
        BuildNameAndRepo buildNameAndRepo = new BuildNameAndRepo("some-build-name", "phoenix-build-info");

        BuildNameEntry buildNameEntry = command.getBuildName(buildNameAndRepo, projectKeyToEntityMap);

        assertNotNull(buildNameEntry);
        assertEquals("some-build-name", buildNameEntry.getBuildName());
        assertEquals("phoenix-build-info", buildNameEntry.getBuildRepo());
        assertEquals("phoenix", buildNameEntry.getProjectKey());
        assertEquals("phoenix", buildNameEntry.getProjectName());
    }

    @Test
    void getBuildNames_whenProjectsBlank_verifiesAdminForAllProjectsAndQueriesAllRepos() {
        ProjectEntity phoenixProject = mock(ProjectEntity.class);
        when(projectService.getProjectsByKeys()).thenReturn(Map.of("phoenix", phoenixProject));
        when(databaseService.getBuildNames(eq(List.of()), isNull(), eq(0L), eq(10L))).thenReturn(List.of());

        command.getBuildNames(null, "  ", 0L, 10L);

        verify(projectService).verifyProjectAdmin("phoenix");
        verify(databaseService).getBuildNames(eq(List.of()), isNull(), eq(0L), eq(10L));
    }
}
