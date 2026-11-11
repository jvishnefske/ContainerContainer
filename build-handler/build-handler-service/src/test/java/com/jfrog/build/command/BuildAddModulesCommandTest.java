package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.service.BuildCreationService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildAddModulesCommandTest {

    private BuildAddModulesCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;
    private BuildCreationService creationService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        creationService = mock(BuildCreationService.class);
        command = new BuildAddModulesCommand(repoService, readService, creationService);
    }

    @Test
    void addModules_appendsModulesAndUpdatesBuild() {
        when(repoService.getPreferredBuildRepo(eq(null), eq(null))).thenReturn("artifactory-build-info");
        Build build = new Build();
        build.setName("some-build-name");
        build.setNumber("some-build-number");
        build.setModules(List.of(new Module()));
        BuildWrapper wrapper = new BuildWrapper(build, "artifactory-build-info");
        when(readService.getBuild(
                "some-build-name", "some-build-number", null, "artifactory-build-info")).thenReturn(wrapper);
        Module moduleToAppend = new Module();

        command.addModules(
                List.of(moduleToAppend),
                "some-build-name",
                "some-build-number",
                null,
                null,
                null);

        assertEquals(2, build.getModules().size());
        verify(readService).getBuild("some-build-name", "some-build-number", null, "artifactory-build-info");
        verify(creationService).updateBuild(wrapper);
    }

    @Test
    void addModules_whenBuildMissing_throwsNotFound() {
        when(repoService.getPreferredBuildRepo(eq(null), eq("phoenix"))).thenReturn("phoenix-build-info");
        when(readService.getBuild(
                "some-build-name", "some-build-number", null, "phoenix-build-info")).thenReturn(null);

        assertThrows(
                NotFoundException.class,
                () -> command.addModules(
                        List.of(new Module()),
                        "some-build-name",
                        "some-build-number",
                        null,
                        null,
                        "phoenix"));
    }

    @Test
    void addModules_withStarted_passesStartedToReadService() {
        when(repoService.getPreferredBuildRepo(eq("explicit-build-repo-query"), eq("phoenix")))
                .thenReturn("phoenix-build-info");
        Build build = new Build();
        build.setName("some-build-name");
        build.setNumber("some-build-number");
        BuildWrapper wrapper = new BuildWrapper(build, "phoenix-build-info");
        when(readService.getBuild(
                "some-build-name", "some-build-number", "2024-01-01T00:00:00.000+0000", "phoenix-build-info"))
                .thenReturn(wrapper);

        command.addModules(
                List.of(new Module()),
                "some-build-name",
                "some-build-number",
                "2024-01-01T00:00:00.000+0000",
                "explicit-build-repo-query",
                "phoenix");

        verify(readService).getBuild(
                "some-build-name", "some-build-number", "2024-01-01T00:00:00.000+0000", "phoenix-build-info");
        verify(creationService).updateBuild(wrapper);
    }
}

