package com.jfrog.build.resource.internal;

import com.jfrog.build.command.ui.BuildGetArtifactBuildJsonUiCommand;
import com.jfrog.build.command.ui.BuildGetArtifactBuildsUiCommand;
import com.jfrog.build.model.api.ArtifactBuildsModel;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactBuildsUiResourceTest {

    private BuildGetArtifactBuildJsonUiCommand getArtifactBuildJsonUiCommand;
    private BuildGetArtifactBuildsUiCommand getArtifactBuildsUiCommand;
    private ArtifactBuildsUiResource resource;

    @BeforeEach
    void setUp() {
        getArtifactBuildJsonUiCommand = mock(BuildGetArtifactBuildJsonUiCommand.class);
        getArtifactBuildsUiCommand = mock(BuildGetArtifactBuildsUiCommand.class);
        resource = new ArtifactBuildsUiResource(getArtifactBuildJsonUiCommand, getArtifactBuildsUiCommand);
    }

    @Test
    void getArtifactBuilds_shouldReturnOkAndEntityFromCommand() {
        ArtifactBuildsModel body = ArtifactBuildsModel.builder().usedBy(List.of()).producedBy(List.of()).build();
        when(getArtifactBuildsUiCommand.getArtifactBuilds("repo-local", "lib/lib.jar")).thenReturn(body);

        Response response = resource.getArtifactBuilds("repo-local", "lib/lib.jar");

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
        verify(getArtifactBuildsUiCommand).getArtifactBuilds("repo-local", "lib/lib.jar");
    }

    @Test
    void getArtifactBuildJson_shouldReturnOkAndJsonFromCommand() {
        when(getArtifactBuildJsonUiCommand.getArtifactBuildJson("my-build", "15", "1700000000000", "repo-local"))
                .thenReturn("{\"version\":\"1.0.1\"}");

        Response response = resource.getArtifactBuildJson("my-build", "15", "1700000000000", "repo-local");

        assertEquals(200, response.getStatus());
        assertEquals("{\"version\":\"1.0.1\"}", response.getEntity());
        verify(getArtifactBuildJsonUiCommand).getArtifactBuildJson("my-build", "15", "1700000000000", "repo-local");
    }

    @Test
    void getArtifactBuildJson_shouldReturnOkWithoutEntity_whenCommandReturnsNull() {
        when(getArtifactBuildJsonUiCommand.getArtifactBuildJson("my-build", "15", "1700000000000", "repo-local"))
                .thenReturn(null);

        Response response = resource.getArtifactBuildJson("my-build", "15", "1700000000000", "repo-local");

        assertEquals(200, response.getStatus());
        assertNull(response.getEntity());
    }
}
