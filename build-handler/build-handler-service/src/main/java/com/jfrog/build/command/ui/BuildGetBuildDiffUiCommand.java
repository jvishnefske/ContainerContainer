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
import com.jfrog.build.model.api.diff.BuildsDiffValue;
import com.jfrog.build.model.api.BaseArtifact;
import com.jfrog.build.model.api.BuildDiffModel;
import com.jfrog.build.model.api.BuildPropsModel;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.ModuleDependencyModel;
import com.jfrog.build.service.BuildDiffService;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.Build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jfrog.build.model.api.diff.BuildsDiffStatus.REMOVED;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@RequiredArgsConstructor
public class BuildGetBuildDiffUiCommand {

    private static final String NO_PATH = "No path found (externally resolved or deleted/overwritten)";

    private final BuildRepoService repoService;
    private final BuildReadService readService;
    private final BuildDiffService diffService;
    private final BuildPermissionService permissionService;

    @NonNull
    public BuildDiffModel getBuildDiff(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @Nullable String comparedBuildNumber,
            @NonNull String comparedBuildDate,
            @Nullable String buildRepo,
            boolean excludeDependencies) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        String formattedStarted = formatBuildTime(Long.parseLong(buildStarted));
        String formattedComparedDate = formatBuildTime(Long.parseLong(comparedBuildDate));
        permissionService.assertReadPermissions(buildName, buildNumber, formattedStarted, normalisedRepo);
        BuildRun buildRun = readService.getBuildRun(buildName, buildNumber, formattedStarted, normalisedRepo);
        BuildRun comparedBuildRun = readService.getBuildRun(
                buildName, comparedBuildNumber, formattedComparedDate, normalisedRepo
        );
        BuildDiffModel buildDiffModel = new BuildDiffModel();
        Build build = resolveBuild(buildRun);
        Build comparedBuild = resolveBuild(comparedBuildRun);
        if (build == null || comparedBuild == null) {
            // One of the builds could not be located - nothing to compare against, return an empty diff.
            return buildDiffModel;
        }
        if (excludeDependencies) {
            buildDiffModel.setDependencies(diffDependenciesExcludingInternal(build, comparedBuild));
        } else {
            BuildsDiff buildsDiff = diffService.getBuildsDiff(build, comparedBuild);
            buildDiffModel.setArtifacts(toArtifactModels(buildsDiff));
            buildDiffModel.setProps(toPropsModels(buildsDiff));
            buildDiffModel.setDependencies(toDependencyModels(buildsDiff));
        }
        return buildDiffModel;
    }

    @Nullable
    private Build resolveBuild(@Nullable BuildRun buildRun) {
        if (isNull(buildRun)) {
            return null;
        }
        BuildWrapper buildWrapper = readService.getBuild(buildRun);
        return buildWrapper == null ? null : buildWrapper.getBuild();
    }

    @NonNull
    private List<ModuleDependencyModel> diffDependenciesExcludingInternal(
            @NonNull Build build, @NonNull Build comparedBuild) {
        Map<String, BuildsDiffBaseFileModel> artifactBySha1 = new HashMap<>();
        diffService.compareArtifacts(build, comparedBuild)
                .forEach(artifact -> artifactBySha1.put(artifact.getSha1(), artifact));
        List<ModuleDependencyModel> dependencies = new ArrayList<>();
        for (BuildsDiffBaseFileModel dependency : diffService.compareDependencies(build, comparedBuild)) {
            if (artifactBySha1.get(dependency.getSha1()) == null) {
                dependencies.add(toModuleDependencyModel((BuildsDiffDependencyModel) dependency));
            }
        }
        return dependencies;
    }

    @NonNull
    private List<ModuleArtifactModel> toArtifactModels(@NonNull BuildsDiff buildsDiff) {
        List<ModuleArtifactModel> artifacts = new ArrayList<>();
        buildsDiff.artifacts.newItems.forEach(artifact -> artifacts.add(toModuleArtifactModel(artifact)));
        buildsDiff.artifacts.removed.forEach(artifact -> artifacts.add(toModuleArtifactModel(artifact)));
        buildsDiff.artifacts.unchanged.forEach(artifact -> artifacts.add(toModuleArtifactModel(artifact)));
        buildsDiff.artifacts.updated.forEach(artifact -> artifacts.add(toModuleArtifactModel(artifact)));
        return artifacts;
    }

    @NonNull
    private List<ModuleDependencyModel> toDependencyModels(@NonNull BuildsDiff buildsDiff) {
        List<ModuleDependencyModel> dependencies = new ArrayList<>();
        buildsDiff.dependencies.newItems.forEach(dependency -> dependencies.add(toModuleDependencyModel(dependency)));
        buildsDiff.dependencies.removed.forEach(dependency -> dependencies.add(toModuleDependencyModel(dependency)));
        buildsDiff.dependencies.unchanged.forEach(dependency -> dependencies.add(toModuleDependencyModel(dependency)));
        buildsDiff.dependencies.updated.forEach(dependency -> dependencies.add(toModuleDependencyModel(dependency)));
        return dependencies;
    }

    @NonNull
    private List<BuildPropsModel> toPropsModels(@NonNull BuildsDiff buildsDiff) {
        List<BuildPropsModel> props = new ArrayList<>();
        buildsDiff.properties.newItems.forEach(property -> props.add(toBuildPropsModel(property)));
        buildsDiff.properties.removed.forEach(property -> props.add(toBuildPropsModel(property)));
        buildsDiff.properties.unchanged.forEach(property -> props.add(toBuildPropsModel(property)));
        buildsDiff.properties.updated.forEach(property -> props.add(toBuildPropsModel(property)));
        return props;
    }

    @NonNull
    private ModuleArtifactModel toModuleArtifactModel(@NonNull BuildsDiffArtifactModel artifact) {
        BuildRepoPath repoPath = artifact.getRepoPath();
        String repoKey = repoPath != null ? repoPath.getRepoKey() : null;
        String path = repoPath != null ? repoPath.getRelativePath() : null;
        BuildsDiffStatus status = artifact.getStatus();
        String name = artifact.getName();
        ModuleArtifactModel.ModuleArtifactModelBuilder builder = ModuleArtifactModel.builder()
                .repoKey(repoKey)
                .path(path)
                .type(artifact.getType())
                .moduleName(artifact.getModule())
                .status(status.toString())
                .name(resolveName(status, name))
                .prevName(resolvePrevName(status, name, artifact.getDiffName()));
        if (repoPath != null) {
            builder.repoPath(repoKey + ":" + path).actions(readableActions(repoKey, path));
        }
        return builder.build();
    }

    @NonNull
    private ModuleDependencyModel toModuleDependencyModel(@NonNull BuildsDiffDependencyModel dependency) {
        BuildRepoPath repoPath = dependency.getRepoPath();
        String repoKey = repoPath != null ? repoPath.getRepoKey() : null;
        String path = repoPath != null ? repoPath.getRelativePath() : null;
        String name = dependency.getName();
        BuildsDiffStatus status = dependency.getStatus();
        String diffValue = dependency.getBuildsDiffValue().stream()
                .map(BuildsDiffValue::toString)
                .collect(Collectors.joining(","));
        ModuleDependencyModel.ModuleDependencyModelBuilder builder = ModuleDependencyModel.builder()
                .repoKey(repoKey)
                .path(path)
                .type(dependency.getType())
                .moduleName(dependency.getModule())
                .status(status.toString())
                .diffValue(diffValue)
                .name(resolveName(status, name))
                .prevName(resolvePrevName(status, name, dependency.getDiffName()));
        if (repoPath != null) {
            builder.repoPath(repoKey + ":" + path).actions(readableActions(repoKey, path));
        } else {
            builder.path(NO_PATH);
        }
        return builder.build();
    }

    @NonNull
    private BuildPropsModel toBuildPropsModel(@NonNull BuildsDiffPropertyModel property) {
        BuildsDiffStatus status = property.getStatus();
        BuildPropsModel.BuildPropsModelBuilder builder = BuildPropsModel.builder()
                .key(property.getKey())
                .value(property.getValue())
                .status(status.toString());
        switch (status) {
            case NEW -> builder.prevKey("").prevValue("");
            case UNCHANGED -> builder.prevKey(property.getKey()).prevValue(property.getValue());
            case UPDATED -> builder.prevValue(property.getDiffValue()).prevKey(property.getKey());
            case REMOVED -> builder.prevKey(property.getKey()).key("").value("").prevValue(property.getValue());
        }
        return builder.build();
    }

    @Nullable
    private String resolveName(
            @NonNull BuildsDiffStatus status,
            @Nullable String name) {
        return status == REMOVED ? "" : name;
    }

    @Nullable
    private String resolvePrevName(
            @NonNull BuildsDiffStatus status,
            @Nullable String name,
            @Nullable String diffName) {
        return switch (status) {
            case NEW -> "";
            case UPDATED -> isNotBlank(diffName) ? diffName : name;
            case UNCHANGED, REMOVED -> name;
        };
    }

    @NonNull
    private List<BaseArtifact> readableActions(
            @NonNull String repoKey,
            @NonNull String path) {
        List<BaseArtifact> actions = new ArrayList<>();
        permissionService.getArtifactActions(repoKey, path)
                .forEach(action -> actions.add(new BaseArtifact(action)));
        return actions;
    }
}
