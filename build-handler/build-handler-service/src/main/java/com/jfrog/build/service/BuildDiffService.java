package com.jfrog.build.service;

import com.jfrog.build.model.api.diff.BuildsDiff;
import com.jfrog.build.model.api.diff.BuildsDiffArtifactModel;
import com.jfrog.build.model.api.diff.BuildsDiffBaseFileModel;
import com.jfrog.build.model.api.diff.BuildsDiffDependencyModel;
import com.jfrog.build.model.api.diff.BuildsDiffPropertyModel;
import com.jfrog.build.model.api.diff.BuildsDiffStatus;
import com.jfrog.build.model.diff.BuildsComparator;
import lombok.RequiredArgsConstructor;
import org.jfrog.build.api.Build;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class BuildDiffService {

    private final BuildArtifactService artifactService;

    public BuildsDiff getBuildsDiff(Build firstBuild, Build secondBuild) {
        BuildsComparator buildsComparator = new BuildsComparator(artifactService);
        BuildsDiff buildsDiff = new BuildsDiff();
        addArtifacts(buildsDiff, buildsComparator, firstBuild, secondBuild);
        addDependencies(buildsDiff, buildsComparator, firstBuild, secondBuild);
        addProperties(buildsDiff, buildsComparator, firstBuild, secondBuild);
        return buildsDiff;
    }

    public List<BuildsDiffBaseFileModel> compareArtifacts(Build build, Build secondBuild) {
        return new BuildsComparator(artifactService).compareArtifacts(build, secondBuild);
    }

    public List<BuildsDiffBaseFileModel> compareDependencies(Build build, Build secondBuild) {
        return new BuildsComparator(artifactService).compareDependencies(build, secondBuild);
    }

    private void addArtifacts(
            BuildsDiff buildsDiff,
            BuildsComparator buildsComparator,
            Build firstBuild,
            Build secondBuild) {
        List<BuildsDiffBaseFileModel> buildsDiffModels = buildsComparator.compareArtifacts(firstBuild, secondBuild);
        filterAndGetArtifacts(buildsDiff.artifacts.newItems, buildsDiffModels, BuildsDiffStatus.NEW);
        filterAndGetArtifacts(buildsDiff.artifacts.updated, buildsDiffModels, BuildsDiffStatus.UPDATED);
        filterAndGetArtifacts(buildsDiff.artifacts.unchanged, buildsDiffModels, BuildsDiffStatus.UNCHANGED);
        filterAndGetArtifacts(buildsDiff.artifacts.removed, buildsDiffModels, BuildsDiffStatus.REMOVED);
    }

    private void filterAndGetArtifacts(
            List<BuildsDiffArtifactModel> artifacts,
            List<BuildsDiffBaseFileModel> buildsDiffModels,
            BuildsDiffStatus status) {
        Iterable<BuildsDiffBaseFileModel> newFilter = buildsDiffModels.stream()
                .filter(input -> status.equals(input.getStatus())).collect(Collectors.toList());
        for (BuildsDiffBaseFileModel buildsDiffModel : newFilter) {
            artifacts.add((BuildsDiffArtifactModel) buildsDiffModel);
        }
    }

    private void addDependencies(
            BuildsDiff buildsDiff,
            BuildsComparator buildsComparator,
            Build firstBuild,
            Build secondBuild) {
        List<BuildsDiffBaseFileModel> buildsDiffModels = buildsComparator.compareDependencies(firstBuild, secondBuild);
        filterAndGetDependencies(buildsDiff.dependencies.newItems, buildsDiffModels, BuildsDiffStatus.NEW);
        filterAndGetDependencies(buildsDiff.dependencies.updated, buildsDiffModels, BuildsDiffStatus.UPDATED);
        filterAndGetDependencies(buildsDiff.dependencies.unchanged, buildsDiffModels, BuildsDiffStatus.UNCHANGED);
        filterAndGetDependencies(buildsDiff.dependencies.removed, buildsDiffModels, BuildsDiffStatus.REMOVED);
    }

    private void filterAndGetDependencies(
            List<BuildsDiffDependencyModel> dependencies,
            List<BuildsDiffBaseFileModel> buildsDiffModels,
            BuildsDiffStatus status) {
        Iterable<BuildsDiffBaseFileModel> newFilter = buildsDiffModels.stream()
                .filter(input -> status.equals(input.getStatus())).collect(Collectors.toList());
        for (BuildsDiffBaseFileModel buildsDiffModel : newFilter) {
            dependencies.add((BuildsDiffDependencyModel) buildsDiffModel);
        }
    }

    private void addProperties(
            BuildsDiff buildsDiff,
            BuildsComparator buildsComparator,
            Build firstBuild,
            Build secondBuild) {
        List<BuildsDiffPropertyModel> buildsDiffModels = buildsComparator.compareProperties(firstBuild, secondBuild);
        filterAndGetProperties(buildsDiff.properties.newItems, buildsDiffModels, BuildsDiffStatus.NEW);
        filterAndGetProperties(buildsDiff.properties.updated, buildsDiffModels, BuildsDiffStatus.UPDATED);
        filterAndGetProperties(buildsDiff.properties.unchanged, buildsDiffModels, BuildsDiffStatus.UNCHANGED);
        filterAndGetProperties(buildsDiff.properties.removed, buildsDiffModels, BuildsDiffStatus.REMOVED);
    }

    private void filterAndGetProperties(
            List<BuildsDiffPropertyModel> dependencies,
            List<BuildsDiffPropertyModel> buildsDiffModels,
            BuildsDiffStatus status) {
        Iterable<BuildsDiffPropertyModel> newFilter = buildsDiffModels.stream()
                .filter(input -> status.equals(input.getStatus())).collect(Collectors.toList());

        for (BuildsDiffPropertyModel buildsDiffModel : newFilter) {
            dependencies.add(buildsDiffModel);
        }
    }
}
