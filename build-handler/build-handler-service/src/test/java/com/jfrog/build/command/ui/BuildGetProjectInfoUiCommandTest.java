package com.jfrog.build.command.ui;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.model.api.ProjectBuildsMetadata;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetProjectInfoUiCommandTest {

    private BuildGetProjectInfoUiCommand command;

    private BuildBridgeToArtifactory bridge;
    private BuildDatabaseService databaseService;
    private BuildRepoService repoService;

    @BeforeEach
    void setUp() {
        bridge = mock(BuildBridgeToArtifactory.class);
        databaseService = mock(BuildDatabaseService.class);
        repoService = mock(BuildRepoService.class);
        command = new BuildGetProjectInfoUiCommand(bridge, databaseService, repoService);
    }

    @Test
    void getProjectInfo_shouldReturnMetadata_whenAuthorizedAndNotEdge() {
        when(bridge.isEdgeInstance()).thenReturn(false);
        when(bridge.isProjectAdmin("my-project")).thenReturn(true);
        when(repoService.getBuildInfoRepoKeyByProject("my-project", true))
                .thenReturn("my-project-build-info");
        when(databaseService.getBuildsCountFromSpecificRepo("my-project-build-info")).thenReturn(42);

        ProjectBuildsMetadata metadata = command.getProjectInfo("my-project");

        assertEquals("my-project-build-info", metadata.getBuildInfoRepo());
        assertEquals(42, metadata.getBuildsCount());
        verify(repoService).getBuildInfoRepoKeyByProject("my-project", true);
        verify(databaseService).getBuildsCountFromSpecificRepo("my-project-build-info");
    }

    @Test
    void getProjectInfo_shouldThrowBadRequest_whenEdgeAndNonDefaultProject() {
        when(bridge.isEdgeInstance()).thenReturn(true);

        assertThrows(BadRequestException.class, () -> command.getProjectInfo("my-project"));
    }

    @Test
    void getProjectInfo_shouldNotThrowBadRequest_whenEdgeAndDefaultProject() {
        // A blank project key is normalised to the default project before the permission/repo lookups.
        when(bridge.isEdgeInstance()).thenReturn(true);
        when(bridge.isProjectAdmin("default")).thenReturn(true);
        when(repoService.getBuildInfoRepoKeyByProject("default", true)).thenReturn("artifactory-build-info");
        when(databaseService.getBuildsCountFromSpecificRepo("artifactory-build-info")).thenReturn(7);

        ProjectBuildsMetadata metadata = command.getProjectInfo(null);

        assertEquals("artifactory-build-info", metadata.getBuildInfoRepo());
        assertEquals(7, metadata.getBuildsCount());
    }

    @Test
    void getProjectInfo_shouldThrowForbidden_whenNotProjectAdmin() {
        when(bridge.isEdgeInstance()).thenReturn(false);
        when(bridge.isProjectAdmin("my-project")).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> command.getProjectInfo("my-project"));
    }
}
