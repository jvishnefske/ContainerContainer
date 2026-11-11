package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.ArtifactoryBuildArtifact;
import com.jfrog.build.acl.model.BuildArtifactMetadata;
import com.jfrog.build.acl.model.BuildRepoDescriptor;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.BuildReleasableModel;
import com.jfrog.build.model.api.BuildSourceModel;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Collects and groups a build's artifacts into {@link BuildReleasableModel} instances for the build-content view.
 * <p>
 * Artifacts are grouped by package identity (packageType + name + version); artifacts without package metadata become
 * standalone releasables. Aggregated (composite) builds - modules with {@code type="build"} referencing a child build
 * by {@code "buildName/buildNumber"} - are resolved recursively.
 */
@Slf4j
@RequiredArgsConstructor
public class BuildReleasableCollector {

    /** Module type for aggregated (composite) builds that reference child builds. */
    private static final String BUILD_MODULE_TYPE = "build";

    /**
     * Container image layer blobs (Docker/OCI) have a packageType but no package name - they are components of an
     * image already represented by the manifest. Skip these to avoid duplicates.
     */
    private static final Set<String> CONTAINER_PACKAGE_TYPES = Set.of("docker", "oci");

    private final BuildBridgeToArtifactory bridgeToArtifactory;
    private final BuildArtifactService artifactService;
    private final BuildReadService readService;

    @NonNull
    public List<BuildReleasableModel> collect(
            @NonNull BuildWrapper buildWrapper,
            @Nullable String buildRepo) {
        Map<String, BuildReleasableModel> releasablesByPackageKey = new LinkedHashMap<>();
        collectRecursive(buildWrapper, buildWrapper.getName(), buildRepo, releasablesByPackageKey, new HashSet<>(),
                false);
        return new ArrayList<>(releasablesByPackageKey.values());
    }

    private void collectRecursive(
            @NonNull BuildWrapper buildWrapper,
            @NonNull String buildName,
            @Nullable String buildRepo,
            @NonNull Map<String, BuildReleasableModel> releasablesByPackageKey,
            @NonNull Set<String> visited,
            boolean sourcedFromBuild) {
        Build build = buildWrapper.getBuild();
        String buildKey = buildName + "/" + build.getNumber();
        if (!visited.add(buildKey)) {
            log.debug("Skipping already visited build: {}", buildKey);
            return;
        }
        if (build.getModules() == null) {
            return;
        }
        Map<Artifact, BuildRepoPath> resolvedPaths = resolveArtifactPaths(buildWrapper);
        for (Module module : build.getModules()) {
            if (BUILD_MODULE_TYPE.equals(module.getType())) {
                resolveChildBuild(module, buildRepo, releasablesByPackageKey, visited);
            } else if (module.getArtifacts() != null) {
                for (Artifact artifact : module.getArtifacts()) {
                    BuildRepoPath repoPath = resolvedPaths.get(artifact);
                    if (repoPath == null) {
                        continue;
                    }
                    processArtifact(artifact, module, build, buildName, buildRepo, releasablesByPackageKey,
                            sourcedFromBuild, repoPath);
                }
            }
        }
    }

    private void resolveChildBuild(
            @NonNull Module module,
            @Nullable String buildRepo,
            @NonNull Map<String, BuildReleasableModel> releasablesByPackageKey,
            @NonNull Set<String> visited) {
        ChildBuild childBuild = parseChildBuild(module);
        if (childBuild == null) {
            return;
        }
        BuildWrapper childWrapper =
                readService.getLatestBuildByNameAndNumber(childBuild.buildName(), childBuild.buildNumber(), buildRepo);
        if (childWrapper == null || !childWrapper.isBuildExist()) {
            log.debug("Child build not found: {}", module.getId());
            return;
        }
        collectRecursive(childWrapper, childBuild.buildName(), buildRepo, releasablesByPackageKey, visited, true);
    }

    @Nullable
    private ChildBuild parseChildBuild(@NonNull Module module) {
        String moduleId = module.getId();
        if (isBlank(moduleId) || !moduleId.contains("/")) {
            log.debug("Invalid build module id: {}", moduleId);
            return null;
        }
        int index = moduleId.lastIndexOf("/");
        return new ChildBuild(moduleId.substring(0, index), moduleId.substring(index + 1));
    }

    private void processArtifact(
            @NonNull Artifact artifact,
            @NonNull Module module,
            @NonNull Build build,
            @NonNull String buildName,
            @Nullable String buildRepo,
            @NonNull Map<String, BuildReleasableModel> releasablesByPackageKey,
            boolean sourcedFromBuild,
            @NonNull BuildRepoPath repoPath) {
        String downloadPath = repoPath.toString();
        BuildSourceModel source = BuildSourceModel.builder()
                .buildName(buildName)
                .buildNumber(build.getNumber())
                .buildRepository(buildRepo)
                .buildStarted(build.getStarted())
                .moduleName(module.getId())
                .moduleType(module.getType())
                .sourceType(sourcedFromBuild ? "BUILDS" : null)
                .build();

        // First artifact in a group becomes the lead. Packages (metadata with a valid name) represent a group and get
        // no downloadPath. Container image layer blobs (Docker/OCI) with no package name are skipped - they are
        // components of an image already represented by the manifest. Other artifacts are standalone.
        Optional<BuildArtifactMetadata> metadata = bridgeToArtifactory.getArtifactMetadata(downloadPath);
        if (metadata.isPresent() && isNotBlank(metadata.get().getName())) {
            BuildArtifactMetadata bom = metadata.get();
            releasablesByPackageKey.putIfAbsent(
                    bom.getPackageType() + ":" + bom.getName() + ":" + bom.getVersion(),
                    buildPackageReleasableModel(bom, source));
        } else if (metadata.isEmpty() || !CONTAINER_PACKAGE_TYPES.contains(metadata.get().getPackageType())) {
            releasablesByPackageKey.putIfAbsent(
                    "standalone:" + downloadPath,
                    buildStandaloneReleasableModel(artifact, repoPath, downloadPath, source));
        }
    }

    @NonNull
    private BuildReleasableModel buildPackageReleasableModel(
            @NonNull BuildArtifactMetadata metadata,
            @NonNull BuildSourceModel source) {
        return BuildReleasableModel.builder()
                .packageType(metadata.getPackageType())
                .packageId(metadata.getPackageId())
                .name(metadata.getName())
                .version(metadata.getVersion())
                .source(source)
                .build();
    }

    @NonNull
    private BuildReleasableModel buildStandaloneReleasableModel(
            @NonNull Artifact artifact,
            @NonNull BuildRepoPath repoPath,
            @NonNull String downloadPath,
            @NonNull BuildSourceModel source) {
        return BuildReleasableModel.builder()
                .packageType(getPackageType(repoPath))
                .name(artifact.getName())
                .downloadPath(downloadPath)
                .source(source)
                .build();
    }

    @NonNull
    private String getPackageType(@NonNull BuildRepoPath repoPath) {
        return bridgeToArtifactory.getRepoDescriptor(repoPath.getRepoKey())
                .map(BuildRepoDescriptor::getType)
                .orElse(EMPTY);
    }

    @NonNull
    private Map<Artifact, BuildRepoPath> resolveArtifactPaths(@NonNull BuildWrapper buildWrapper) {
        Set<ArtifactoryBuildArtifact> resolved = artifactService.getBuildArtifactsFileInfos(buildWrapper);
        IdentityHashMap<Artifact, BuildRepoPath> map = new IdentityHashMap<>();
        for (ArtifactoryBuildArtifact buildArtifact : resolved) {
            if (buildArtifact.getFileInfo() != null) {
                map.put(buildArtifact.getArtifact(), buildArtifact.getFileInfo().getRepoPath());
            }
        }
        return map;
    }

    private record ChildBuild(String buildName, String buildNumber) {
    }
}
