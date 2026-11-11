package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.BuildRepositoryKeyModel;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetRepositoryKeyUiCommandTest {

    private static final String PROJECT_KEY = "my-project";
    private static final String PROJECT_REPO = "my-project-build-info";

    private BuildGetRepositoryKeyUiCommand command;

    private BuildRepoService repoService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        command = new BuildGetRepositoryKeyUiCommand(repoService);
    }

    @Test
    void getBuildInfoRepositoryKey_shouldReturnRepoForProject() {
        when(repoService.getBuildInfoRepoKeyByProject(PROJECT_KEY, true)).thenReturn(PROJECT_REPO);

        BuildRepositoryKeyModel result = command.getBuildInfoRepositoryKey(PROJECT_KEY);

        assertEquals(PROJECT_REPO, result.getKey());
        verify(repoService).getBuildInfoRepoKeyByProject(PROJECT_KEY, true);
    }

    @Test
    void getBuildInfoRepositoryKey_shouldFallBackToDefaultProject_whenProjectKeyBlank() {
        when(repoService.getBuildInfoRepoKeyByProject(DEFAULT_PROJECT, true)).thenReturn(BUILDINFO_REPO_KEY);

        BuildRepositoryKeyModel result = command.getBuildInfoRepositoryKey(" ");

        assertEquals(BUILDINFO_REPO_KEY, result.getKey());
        verify(repoService).getBuildInfoRepoKeyByProject(DEFAULT_PROJECT, true);
    }

    @Test
    void getBuildInfoRepositoryKey_shouldFallBackToDefaultProject_whenProjectKeyNull() {
        when(repoService.getBuildInfoRepoKeyByProject(DEFAULT_PROJECT, true)).thenReturn(BUILDINFO_REPO_KEY);

        BuildRepositoryKeyModel result = command.getBuildInfoRepositoryKey(null);

        assertEquals(BUILDINFO_REPO_KEY, result.getKey());
        verify(repoService).getBuildInfoRepoKeyByProject(DEFAULT_PROJECT, true);
    }

    @Test
    void getBuildInfoRepositoryKey_shouldThrowNotFound_whenProjectMissing() {
        when(repoService.getBuildInfoRepoKeyByProject(PROJECT_KEY, true))
                .thenThrow(new IllegalStateException("No build-info repository for project my-project"));

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> command.getBuildInfoRepositoryKey(PROJECT_KEY));

        assertTrue(thrown.getMessage().contains("No build-info repository for project my-project"));
    }
}
