package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.ArtifactoryBuildArtifact;
import com.jfrog.build.acl.model.BuildArtifactMetadata;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildRepoDescriptor;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.BuildReleasableModel;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildReleasableCollectorTest {

    private static final String REPO_KEY = "repo-local";
    private static final String ARTIFACT_PATH = "lib/lib.jar";
    private static final String DOWNLOAD_PATH = REPO_KEY + "/" + ARTIFACT_PATH;

    private BuildBridgeToArtifactory bridge;
    private BuildArtifactService artifactService;
    private BuildReadService readService;
    private BuildReleasableCollector collector;

    private BuildWrapper buildWrapper;

    @BeforeEach
    void setUp() {
        bridge = mock(BuildBridgeToArtifactory.class);
        artifactService = mock(BuildArtifactService.class);
        readService = mock(BuildReadService.class);
        collector = new BuildReleasableCollector(bridge, artifactService, readService);

        Artifact artifact = new Artifact();
        artifact.setName("lib.jar");
        Module module = new Module();
        module.setId("org.example:lib");
        module.setType("maven");
        module.setArtifacts(List.of(artifact));
        Build build = new Build();
        build.setName("my-build");
        build.setNumber("1");
        build.setStarted("2024-01-01T00:00:00.000+0000");
        build.setModules(List.of(module));
        buildWrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);

        BuildFileInfo fileInfo = BuildFileInfo.builder().repoPath(new BuildRepoPath(REPO_KEY, ARTIFACT_PATH)).build();
        when(artifactService.getBuildArtifactsFileInfos(buildWrapper))
                .thenReturn(Set.of(new ArtifactoryBuildArtifact(artifact, fileInfo)));
    }

    @Test
    void collect_shouldGroupArtifactIntoPackage_whenMetadataHasName() {
        when(bridge.getArtifactMetadata(DOWNLOAD_PATH)).thenReturn(of(BuildArtifactMetadata.builder()
                .packageType("maven")
                .name("org.example:lib")
                .version("1.0")
                .packageId("pkg-id")
                .build()));

        List<BuildReleasableModel> releasables = collector.collect(buildWrapper, BUILDINFO_REPO_KEY);

        assertEquals(1, releasables.size());
        BuildReleasableModel releasable = releasables.getFirst();
        assertEquals("maven", releasable.getPackageType());
        assertEquals("org.example:lib", releasable.getName());
        assertEquals("1.0", releasable.getVersion());
        assertEquals("pkg-id", releasable.getPackageId());
        // Packages represent a group and carry no download path.
        assertNull(releasable.getDownloadPath());
        assertEquals("1", releasable.getSource().getBuildNumber());
        assertEquals("org.example:lib", releasable.getSource().getModuleName());
    }

    @Test
    void collect_shouldProduceStandaloneReleasable_whenNoMetadata() {
        when(bridge.getArtifactMetadata(DOWNLOAD_PATH)).thenReturn(empty());
        when(bridge.getRepoDescriptor(REPO_KEY)).thenReturn(of(BuildRepoDescriptor.builder()
                .type("generic")
                .build()));

        List<BuildReleasableModel> releasables = collector.collect(buildWrapper, BUILDINFO_REPO_KEY);

        assertEquals(1, releasables.size());
        BuildReleasableModel releasable = releasables.getFirst();
        assertEquals("generic", releasable.getPackageType());
        assertEquals("lib.jar", releasable.getName());
        assertEquals(DOWNLOAD_PATH, releasable.getDownloadPath());
    }

    @Test
    void collect_shouldSkipContainerLayerBlob_whenMetadataHasNoNameAndContainerType() {
        when(bridge.getArtifactMetadata(DOWNLOAD_PATH)).thenReturn(of(BuildArtifactMetadata.builder()
                .packageType("docker")
                .name("")
                .build()));

        List<BuildReleasableModel> releasables = collector.collect(buildWrapper, BUILDINFO_REPO_KEY);

        assertTrue(releasables.isEmpty());
    }

    @Test
    void collect_shouldReturnEmpty_whenArtifactHasNoResolvedPath() {
        when(artifactService.getBuildArtifactsFileInfos(buildWrapper)).thenReturn(Set.of());

        List<BuildReleasableModel> releasables = collector.collect(buildWrapper, BUILDINFO_REPO_KEY);

        assertTrue(releasables.isEmpty());
        // No path resolved -> the artifact is skipped, so no metadata lookups happen.
    }

    @Test
    void collect_shouldReturnEmpty_whenBuildHasNoModules() {
        Build build = new Build();
        build.setName("my-build");
        build.setNumber("1");
        BuildWrapper emptyWrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);
        when(artifactService.getBuildArtifactsFileInfos(any(BuildWrapper.class))).thenReturn(Set.of());

        assertTrue(collector.collect(emptyWrapper, BUILDINFO_REPO_KEY).isEmpty());
    }

    @Test
    void collect_shouldUseEmptyPackageType_whenNoRepoDescriptor() {
        when(bridge.getArtifactMetadata(DOWNLOAD_PATH)).thenReturn(empty());
        when(bridge.getRepoDescriptor(REPO_KEY)).thenReturn(empty());

        List<BuildReleasableModel> releasables = collector.collect(buildWrapper, BUILDINFO_REPO_KEY);

        assertEquals(1, releasables.size());
        assertEquals("", releasables.getFirst().getPackageType());
        assertEquals(DOWNLOAD_PATH, releasables.getFirst().getDownloadPath());
    }

    @Test
    void collect_shouldResolveChildBuild_forAggregatedBuildModule() {
        // Parent build has no direct artifacts, only an aggregated "build" module referencing child "child/2".
        Module buildModule = new Module();
        buildModule.setId("child/2");
        buildModule.setType("build");
        Build parent = new Build();
        parent.setName("agg");
        parent.setNumber("1");
        parent.setModules(List.of(buildModule));
        BuildWrapper parentWrapper = new BuildWrapper(parent, BUILDINFO_REPO_KEY);
        when(artifactService.getBuildArtifactsFileInfos(parentWrapper)).thenReturn(Set.of());

        // Child build carries the real artifact.
        Artifact childArtifact = new Artifact();
        childArtifact.setName("child.jar");
        Module childModule = new Module();
        childModule.setId("org.example:child");
        childModule.setType("maven");
        childModule.setArtifacts(List.of(childArtifact));
        Build child = new Build();
        child.setName("child");
        child.setNumber("2");
        child.setStarted("2024-02-02T00:00:00.000+0000");
        child.setModules(List.of(childModule));
        BuildWrapper childWrapper = new BuildWrapper(child, BUILDINFO_REPO_KEY);

        String childPath = "child/child.jar";
        String childDownloadPath = REPO_KEY + "/" + childPath;
        BuildFileInfo childFileInfo =
                BuildFileInfo.builder().repoPath(new BuildRepoPath(REPO_KEY, childPath)).build();
        when(readService.getLatestBuildByNameAndNumber("child", "2", BUILDINFO_REPO_KEY)).thenReturn(childWrapper);
        when(artifactService.getBuildArtifactsFileInfos(childWrapper))
                .thenReturn(Set.of(new ArtifactoryBuildArtifact(childArtifact, childFileInfo)));
        when(bridge.getArtifactMetadata(childDownloadPath)).thenReturn(of(BuildArtifactMetadata.builder()
                .packageType("maven")
                .name("org.example:child")
                .version("2.0")
                .build()));

        List<BuildReleasableModel> releasables = collector.collect(parentWrapper, BUILDINFO_REPO_KEY);

        assertEquals(1, releasables.size());
        BuildReleasableModel releasable = releasables.getFirst();
        assertEquals("org.example:child", releasable.getName());
        // Artifacts sourced from a child build are tagged as "BUILDS".
        assertEquals("BUILDS", releasable.getSource().getSourceType());
        assertEquals("child", releasable.getSource().getBuildName());
    }

    @Test
    void collect_shouldSkipAggregatedModule_whenChildBuildNotFound() {
        Module buildModule = new Module();
        buildModule.setId("child/2");
        buildModule.setType("build");
        Build parent = new Build();
        parent.setName("agg");
        parent.setNumber("1");
        parent.setModules(List.of(buildModule));
        BuildWrapper parentWrapper = new BuildWrapper(parent, BUILDINFO_REPO_KEY);
        when(artifactService.getBuildArtifactsFileInfos(parentWrapper)).thenReturn(Set.of());
        when(readService.getLatestBuildByNameAndNumber("child", "2", BUILDINFO_REPO_KEY)).thenReturn(null);

        assertTrue(collector.collect(parentWrapper, BUILDINFO_REPO_KEY).isEmpty());
    }

    @Test
    void collect_shouldSkipAggregatedModule_whenModuleIdHasNoSlash() {
        Module buildModule = new Module();
        buildModule.setId("invalid-id-without-slash");
        buildModule.setType("build");
        Build parent = new Build();
        parent.setName("agg");
        parent.setNumber("1");
        parent.setModules(List.of(buildModule));
        BuildWrapper parentWrapper = new BuildWrapper(parent, BUILDINFO_REPO_KEY);
        when(artifactService.getBuildArtifactsFileInfos(parentWrapper)).thenReturn(Set.of());

        assertTrue(collector.collect(parentWrapper, BUILDINFO_REPO_KEY).isEmpty());
    }
}
