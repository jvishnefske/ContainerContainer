package com.jfrog.build.command;

import com.jfrog.build.service.BuildCreationService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSysConfigService;
import org.artifactory.exception.http.BadRequestException;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildCreateCommandTest {

    private BuildCreateCommand command;

    private BuildRepoService repoService;
    private BuildCreationService creationService;
    private BuildSysConfigService sysConfigService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        creationService = mock(BuildCreationService.class);
        sysConfigService = mock(BuildSysConfigService.class);
        when(repoService.getPreferredBuildRepo(any(), any())).thenReturn("resolved-build-info-repo");
        when(creationService.createBuild(any(), eq("resolved-build-info-repo"))).thenReturn("some-sha");
        command = new BuildCreateCommand(repoService, creationService, sysConfigService);
    }

    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    blockDuplicates, shouldFail
                    false, false
                    true, false
                    """
    )
    @ParameterizedTest
    void createBuild_whenArtifactsDifferByNameInOneModule(boolean blockDuplicates, boolean shouldFail) {
        when(sysConfigService.isBuildBlockDuplicateEntries()).thenReturn(blockDuplicates);
        Build build = newBuild("build-name", "build-number",
                newModule(
                        newArtifact("artifact01-name", "artifact01-sha1"),
                        newArtifact("artifact02-name", "artifact01-sha1")
                )
        );
        assertResult(build, shouldFail);
    }

    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    blockDuplicates, shouldFail
                    false, false
                    true, true
                    """
    )
    @ParameterizedTest
    void createBuild_whenSameArtifactTwiceInOneModule(
            boolean blockDuplicates, boolean shouldFail) {
        when(sysConfigService.isBuildBlockDuplicateEntries()).thenReturn(blockDuplicates);
        Build build = newBuild("build-name", "build-number",
                newModule(
                        newArtifact("artifact01-name", "artifact01-sha1"),
                        newArtifact("artifact01-name", "artifact01-sha1")
                )
        );
        assertResult(build, shouldFail);
    }

    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    blockDuplicates, shouldFail
                    false, false
                    true, true
                    """
    )
    @ParameterizedTest
    void createBuild_whenSameArtifactInMultipleModules(
            boolean blockDuplicates, boolean shouldFail) {
        when(sysConfigService.isBuildBlockDuplicateEntries()).thenReturn(blockDuplicates);
        Build build = newBuild("build-name", "build-number",
                newModule(
                        newArtifact("artifact01-name", "artifact01-sha1"),
                        newArtifact("artifact02-name", "artifact02-sha1")
                ),
                newModule(
                        newArtifact("artifact01-name", "artifact01-sha1"),
                        newArtifact("artifact03-name", "artifact03-sha1")
                )
        );
        assertResult(build, shouldFail);
    }

    @Test
    void createBuild_whenModulesNullAndBlockDuplicatesEnabled() {
        when(sysConfigService.isBuildBlockDuplicateEntries()).thenReturn(true);
        Build build = newBuild("build-name", "build-number");

        assertEquals("some-sha", command.createBuild(build, "", ""));

        verify(creationService).createBuild(eq(build), eq("resolved-build-info-repo"));
        verify(repoService).getPreferredBuildRepo("", "");
    }

    private void assertResult(Build build, boolean fail) {
        if (fail) {
            assertValidationFailed(build);
        } else {
            assertSuccessfulResponse(build);
        }
    }

    private void assertSuccessfulResponse(Build build) {
        assertEquals("some-sha", command.createBuild(build, "", ""));
        verify(creationService).createBuild(eq(build), eq("resolved-build-info-repo"));
        verify(repoService).getPreferredBuildRepo("", "");
    }

    private void assertValidationFailed(Build build) {
        BadRequestException thrown = assertThrows(
                BadRequestException.class,
                () -> command.createBuild(build, "", "")
        );
        assertTrue(thrown.getMessage().contains("Artifact(s) duplicates"));
        verify(creationService, never()).createBuild(any(), anyString());
    }

    private Artifact newArtifact(String name, String sha) {
        Artifact artifact = new Artifact();
        artifact.setName(name);
        artifact.setSha1(sha);
        return artifact;
    }

    private Module newModule(Artifact... artifacts) {
        Module module = new Module();
        module.setArtifacts(List.of(artifacts));
        return module;
    }

    private Build newBuild(String name, String number, Module... modules) {
        Build build = new Build();
        build.setName(name);
        build.setNumber(number);
        List<Module> moduleList = List.of(modules);
        if (!moduleList.isEmpty()) {
            build.setModules(moduleList);
        }
        return build;
    }
}
