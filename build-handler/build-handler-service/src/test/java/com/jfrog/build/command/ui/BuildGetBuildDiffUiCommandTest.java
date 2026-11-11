package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.diff.BuildsDiff;
import com.jfrog.build.model.api.diff.BuildsDiffArtifactModel;
import com.jfrog.build.model.api.diff.BuildsDiffBaseFileModel;
import com.jfrog.build.model.api.diff.BuildsDiffDependencyModel;
import com.jfrog.build.model.api.diff.BuildsDiffPropertyModel;
import com.jfrog.build.model.api.diff.BuildsDiffStatus;
import com.jfrog.build.model.api.BuildDiffModel;
import com.jfrog.build.model.api.BuildPropsModel;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.ModuleDependencyModel;
import com.jfrog.build.service.BuildDiffService;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import org.jfrog.build.api.Build;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetBuildDiffUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";
    private static final String BUILD_STARTED_EPOCH = "1700000000000";
    private static final String COMPARED_NUMBER = "4";
    private static final String COMPARED_DATE_EPOCH = "1600000000000";
    private static final String STARTED = formatBuildTime(Long.parseLong(BUILD_STARTED_EPOCH));
    private static final String COMPARED = formatBuildTime(Long.parseLong(COMPARED_DATE_EPOCH));

    private BuildGetBuildDiffUiCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;
    private BuildDiffService diffService;
    private BuildPermissionService permissionService;

    private final Build build = new Build();
    private final Build comparedBuild = new Build();

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        diffService = mock(BuildDiffService.class);
        permissionService = mock(BuildPermissionService.class);
        command = new BuildGetBuildDiffUiCommand(repoService, readService, diffService, permissionService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    private void wireBothBuilds() {
        BuildRun run = mock(BuildRun.class);
        BuildRun comparedRun = mock(BuildRun.class);
        when(readService.getBuildRun(BUILD_NAME, BUILD_NUMBER, STARTED, BUILDINFO_REPO_KEY)).thenReturn(run);
        when(readService.getBuildRun(BUILD_NAME, COMPARED_NUMBER, COMPARED, BUILDINFO_REPO_KEY)).thenReturn(comparedRun);
        when(readService.getBuild(run)).thenReturn(new BuildWrapper(build, BUILDINFO_REPO_KEY));
        when(readService.getBuild(comparedRun)).thenReturn(new BuildWrapper(comparedBuild, BUILDINFO_REPO_KEY));
    }

    @Test
    void getBuildDiff_shouldAssertPermissionsAndMapFullDiff() {
        wireBothBuilds();
        BuildsDiff buildsDiff = new BuildsDiff();
        buildsDiff.artifacts.updated.add(artifact("repo-local", "lib/a.jar", "a.jar", "old-a.jar",
                BuildsDiffStatus.UPDATED));
        buildsDiff.artifacts.removed.add(artifact(null, null, "gone.jar", null, BuildsDiffStatus.REMOVED));
        buildsDiff.dependencies.newItems.add(dependency("repo-local", "dep/d.jar", "d.jar", BuildsDiffStatus.NEW));
        buildsDiff.properties.updated.add(property("os", "linux", "windows", BuildsDiffStatus.UPDATED));
        when(diffService.getBuildsDiff(build, comparedBuild)).thenReturn(buildsDiff);
        when(permissionService.getArtifactActions("repo-local", "lib/a.jar"))
                .thenReturn(List.of("Download", "ShowInTree"));

        BuildDiffModel result = command.getBuildDiff(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH, COMPARED_NUMBER,
                COMPARED_DATE_EPOCH, BUILDINFO_REPO_KEY, false);

        verify(permissionService).assertReadPermissions(BUILD_NAME, BUILD_NUMBER, STARTED, BUILDINFO_REPO_KEY);

        // Artifacts are emitted in newItems, removed, unchanged, updated order.
        assertEquals(2, result.getArtifacts().size());
        ModuleArtifactModel removed = result.getArtifacts().getFirst();
        assertEquals("", removed.getName());
        assertEquals("gone.jar", removed.getPrevName());
        assertNull(removed.getRepoPath());
        ModuleArtifactModel updated = result.getArtifacts().get(1);
        assertEquals("a.jar", updated.getName());
        assertEquals("old-a.jar", updated.getPrevName());
        assertEquals("repo-local:lib/a.jar", updated.getRepoPath());
        assertEquals(List.of("Download", "ShowInTree"),
                updated.getActions().stream().map(a -> a.getName()).toList());

        assertEquals(1, result.getDependencies().size());
        ModuleDependencyModel dependency = result.getDependencies().getFirst();
        assertEquals("d.jar", dependency.getName());
        assertEquals("repo-local:dep/d.jar", dependency.getRepoPath());

        assertEquals(1, result.getProps().size());
        BuildPropsModel prop = result.getProps().getFirst();
        assertEquals("os", prop.getKey());
        assertEquals("linux", prop.getValue());
        assertEquals("windows", prop.getPrevValue());
        assertEquals("os", prop.getPrevKey());
    }

    @Test
    void getBuildDiff_shouldReturnEmptyModel_whenComparedBuildMissing() {
        BuildRun run = mock(BuildRun.class);
        when(readService.getBuildRun(BUILD_NAME, BUILD_NUMBER, STARTED, BUILDINFO_REPO_KEY)).thenReturn(run);
        when(readService.getBuild(run)).thenReturn(new BuildWrapper(build, BUILDINFO_REPO_KEY));
        when(readService.getBuildRun(BUILD_NAME, COMPARED_NUMBER, COMPARED, BUILDINFO_REPO_KEY)).thenReturn(null);

        BuildDiffModel result = command.getBuildDiff(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH, COMPARED_NUMBER,
                COMPARED_DATE_EPOCH, BUILDINFO_REPO_KEY, false);

        assertNull(result.getArtifacts());
        assertNull(result.getDependencies());
        assertNull(result.getProps());
    }

    @Test
    void getBuildDiff_shouldExcludeInternalDependencies_whenExcludeDependenciesSet() {
        wireBothBuilds();
        BuildsDiffBaseFileModel sharedArtifact = artifact("repo-local", "lib/shared.jar", "shared.jar", null,
                BuildsDiffStatus.UNCHANGED);
        sharedArtifact.setSha1("shared-sha1");
        BuildsDiffDependencyModel internalDep = dependency(null, null, "shared.jar", BuildsDiffStatus.UNCHANGED);
        internalDep.setSha1("shared-sha1");
        BuildsDiffDependencyModel externalDep = dependency("repo-local", "dep/ext.jar", "ext.jar",
                BuildsDiffStatus.NEW);
        externalDep.setSha1("ext-sha1");
        when(diffService.compareArtifacts(build, comparedBuild)).thenReturn(List.of(sharedArtifact));
        when(diffService.compareDependencies(build, comparedBuild)).thenReturn(List.of(internalDep, externalDep));

        BuildDiffModel result = command.getBuildDiff(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH, COMPARED_NUMBER,
                COMPARED_DATE_EPOCH, BUILDINFO_REPO_KEY, true);

        assertNull(result.getArtifacts());
        assertNull(result.getProps());
        assertEquals(1, result.getDependencies().size());
        ModuleDependencyModel dependency = result.getDependencies().getFirst();
        assertEquals("ext.jar", dependency.getName());
        assertEquals("repo-local:dep/ext.jar", dependency.getRepoPath());
    }

    @Test
    void getBuildDiff_shouldReplaceDependencyPath_whenRepoPathMissing() {
        wireBothBuilds();
        BuildsDiff buildsDiff = new BuildsDiff();
        buildsDiff.dependencies.removed.add(dependency(null, null, "gone-dep.jar", BuildsDiffStatus.REMOVED));
        when(diffService.getBuildsDiff(build, comparedBuild)).thenReturn(buildsDiff);

        BuildDiffModel result = command.getBuildDiff(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH, COMPARED_NUMBER,
                COMPARED_DATE_EPOCH, BUILDINFO_REPO_KEY, false);

        ModuleDependencyModel dependency = result.getDependencies().getFirst();
        assertEquals("No path found (externally resolved or deleted/overwritten)", dependency.getPath());
        assertNull(dependency.getRepoPath());
        assertTrue(dependency.getActions().isEmpty());
    }

    private static BuildsDiffArtifactModel artifact(String repoKey, String relativePath, String name, String diffName,
            BuildsDiffStatus status) {
        BuildsDiffArtifactModel artifact = new BuildsDiffArtifactModel();
        artifact.setName(name);
        artifact.setDiffName(diffName);
        artifact.setType("jar");
        artifact.setModule("org.example:lib");
        artifact.setStatus(status);
        if (repoKey != null) {
            artifact.setRepoPath(new BuildRepoPath(repoKey, relativePath));
        }
        return artifact;
    }

    private static BuildsDiffDependencyModel dependency(String repoKey, String relativePath, String name,
            BuildsDiffStatus status) {
        BuildsDiffDependencyModel dependency = new BuildsDiffDependencyModel();
        dependency.setName(name);
        dependency.setType("jar");
        dependency.setModule("org.example:lib");
        dependency.setStatus(status);
        if (repoKey != null) {
            dependency.setRepoPath(new BuildRepoPath(repoKey, relativePath));
        }
        return dependency;
    }

    private static BuildsDiffPropertyModel property(String key, String value, String diffValue,
            BuildsDiffStatus status) {
        BuildsDiffPropertyModel property = new BuildsDiffPropertyModel();
        property.setKey(key);
        property.setValue(value);
        property.setDiffValue(diffValue);
        property.setStatus(status);
        return property;
    }
}
