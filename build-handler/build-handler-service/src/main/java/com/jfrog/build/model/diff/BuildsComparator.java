package com.jfrog.build.model.diff;

import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.model.api.diff.BuildsDiffArtifactModel;
import com.jfrog.build.model.api.diff.BuildsDiffBaseFileModel;
import com.jfrog.build.model.api.diff.BuildsDiffDependencyModel;
import com.jfrog.build.model.api.diff.BuildsDiffPropertyModel;
import com.jfrog.build.model.api.diff.BuildsDiffStatus;
import com.jfrog.build.model.api.diff.BuildsDiffValue;
import com.jfrog.build.service.BuildArtifactService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.jfrog.common.StreamSupportUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import static java.util.Objects.isNull;

/**
 * Compares 2 builds for different modules (artifacts and dependencies) and environment variables.
 * Items are separated into 4 groups: new, deleted, updated or same (same checksum for artifacts and dependencies,
 * same key for env vars).
 *
 * @author Shay Yaakov
 */
@RequiredArgsConstructor
public class BuildsComparator {

    private static final BuildEquivalence BUILD_EQUIVALENCE = new BuildEquivalence();

    private final BuildArtifactService artifactService;

    public List<BuildsDiffPropertyModel> compareProperties(Build firstBuild, Build secondBuild) {
        Properties firstBuildProperties = firstBuild.getProperties();
        if (firstBuildProperties == null) {
            firstBuildProperties = new Properties();
        }

        Properties secondBuildProperties = secondBuild.getProperties();
        if (secondBuildProperties == null) {
            secondBuildProperties = new Properties();
        }

        List<BuildsDiffPropertyModel> buildsDiffList = Lists.newArrayList();

        MapDifference<Object, Object> differences = Maps.difference(firstBuildProperties, secondBuildProperties);

        // Add common values (same key and value on current build and previous build)
        for (Map.Entry<Object, Object> entry : differences.entriesInCommon().entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            addPropertyDiff(buildsDiffList, key, value, value, BuildsDiffStatus.UNCHANGED);
        }

        // Add same keys but different values (value was updated)
        for (Map.Entry<Object, MapDifference.ValueDifference<Object>> entry : differences.entriesDiffering()
                .entrySet()) {
            String key = (String) entry.getKey();
            String currentValue = (String) entry.getValue().leftValue();
            String diffValue = (String) entry.getValue().rightValue();
            addPropertyDiff(buildsDiffList, key, currentValue, diffValue, BuildsDiffStatus.UPDATED);
        }

        // Add new entries (exist on current build but not on previous build)
        for (Map.Entry<Object, Object> entry : differences.entriesOnlyOnLeft().entrySet()) {
            String key = (String) entry.getKey();
            String currentValue = (String) entry.getValue();
            addPropertyDiff(buildsDiffList, key, currentValue, null, BuildsDiffStatus.NEW);
        }

        // Add deleted entries (exist on previous build but not on current build)
        for (Map.Entry<Object, Object> entry : differences.entriesOnlyOnRight().entrySet()) {
            String key = (String) entry.getKey();
            String diffValue = (String) entry.getValue();
            addPropertyDiff(buildsDiffList, key, null, diffValue, BuildsDiffStatus.REMOVED);
        }

        return buildsDiffList;
    }

    private void addPropertyDiff(List<BuildsDiffPropertyModel> buildsDiffList, String key,
            String currentValue, String diffValue, BuildsDiffStatus status) {
        BuildsDiffPropertyModel buildsDiff = new BuildsDiffPropertyModel();
        buildsDiff.setKey(key);
        buildsDiff.setValue(currentValue);
        buildsDiff.setDiffValue(diffValue);
        buildsDiff.setStatus(status);
        buildsDiffList.add(buildsDiff);
    }

    public List<BuildsDiffBaseFileModel> compareArtifacts(Build firstBuild, Build secondBuild) {
        Map<String, BuildItemDiff> firstBuildArtifacts = collectArtifactsFromBuild(firstBuild);
        Map<String, BuildItemDiff> secondBuildArtifacts = collectArtifactsFromBuild(secondBuild);

        return mergeArtifactsDiff(firstBuildArtifacts, secondBuildArtifacts, false);
    }

    private Map<String, BuildItemDiff> collectArtifactsFromBuild(Build build) {
        Map<String, BuildItemDiff> artifactsDiffMap = Maps.newHashMap();
        Map<String, BuildRepoPath> itemRepoPaths = getArtifactsRepoPaths(build);

        List<Module> modules = build.getModules();
        if (modules != null) {
            for (Module module : modules) {
                List<Artifact> artifacts = module.getArtifacts();
                if (artifacts != null) {
                    for (Artifact artifact : artifacts) {
                        BuildItemDiff artifactDiff = new BuildItemDiff
                                .Builder(artifact.getName(), module.getId(), module.getId())
                                .type(artifact.getType()).sha1(artifact.getSha1()).md5(artifact.getMd5())
                                .repoPath(itemRepoPaths.get(artifact.getSha1()))
                                .build();
                        artifactsDiffMap.put(artifact.getSha1(), artifactDiff);
                    }
                }
            }
        }

        return artifactsDiffMap;
    }

    private Map<String, BuildRepoPath> getArtifactsRepoPaths(Build build) {
        List<String> artifactsSha1List = new ArrayList<>();
        List<Module> modules = build.getModules();
        StreamSupportUtils.stream(modules).forEach(module -> {
            List<Artifact> artifacts = module.getArtifacts();
            artifactsSha1List.addAll(StreamSupportUtils.stream(artifacts)
                    .map(Artifact::getSha1).toList());
        });
        return artifactService.mapSha1ToRepoPathOfFileNodes(artifactsSha1List);
    }

    private Map<String, BuildRepoPath> getDependenciesRepoPaths(Build build) {
        List<String> dependenciesSha1List = new ArrayList<>();
        List<Module> modules = build.getModules();
        StreamSupportUtils.stream(modules).forEach(module -> {
            List<Dependency> dependencies = module.getDependencies();
            dependenciesSha1List.addAll(StreamSupportUtils.stream(dependencies)
                    .map(Dependency::getSha1).toList());
        });
        return artifactService.mapSha1ToRepoPathOfFileNodes(dependenciesSha1List);
    }

    public List<BuildsDiffBaseFileModel> compareDependencies(Build firstBuild, Build secondBuild) {
        Map<String, BuildItemDiff> firstBuildDependencies = collectDependenciesFromBuild(firstBuild);
        Map<String, BuildItemDiff> secondBuildDependencies = collectDependenciesFromBuild(secondBuild);

        return mergeArtifactsDiff(firstBuildDependencies, secondBuildDependencies, true);
    }

    private Map<String, BuildItemDiff> collectDependenciesFromBuild(Build build) {
        Map<String, BuildItemDiff> dependenciesDiffMap = Maps.newHashMap();
        Map<String, BuildRepoPath> itemRepoPaths = getDependenciesRepoPaths(build);
        List<Module> modules = build.getModules();
        if (modules != null) {
            for (Module module : modules) {
                List<Dependency> dependencies = module.getDependencies();
                if (dependencies != null) {
                    for (Dependency dependency : dependencies) {
                        BuildItemDiff artifactDiff = new BuildItemDiff
                                .Builder(dependency.getId(), dependency.getId(), module.getId())
                                .type(dependency.getType()).sha1(dependency.getSha1()).md5(dependency.getMd5())
                                .repoPath(itemRepoPaths.get(dependency.getSha1()))
                                .internalDependency(isInternalDependency(dependency, build))
                                .scopes(dependency.getScopes())
                                .build();
                        dependenciesDiffMap.put(dependency.getSha1(), artifactDiff);
                    }
                }
            }
        }

        return dependenciesDiffMap;
    }

    private boolean isInternalDependency(Dependency dependency, Build build) {
        List<Module> modules = build.getModules();
        if (modules != null) {
            for (Module module : modules) {
                List<Artifact> artifacts = module.getArtifacts();
                if (CollectionUtils.isNotEmpty(artifacts)) {
                    for (Artifact artifact : artifacts) {
                        if (StringUtils.isNotBlank(artifact.getSha1())) {
                            if (artifact.getSha1().equals(dependency.getSha1())) {
                                return true;
                            }
                        } else if (StringUtils.isNotBlank(artifact.getMd5())) {
                            if (artifact.getMd5().equals(dependency.getMd5())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private List<BuildsDiffBaseFileModel> mergeArtifactsDiff(Map<String, BuildItemDiff> firstBuildArtifacts,
            Map<String, BuildItemDiff> secondBuildArtifacts, boolean isDependencies) {

        List<BuildsDiffBaseFileModel> buildsDiffList = Lists.newArrayList();
        MapDifference<String, BuildItemDiff> checksumDifferences =
                Maps.difference(firstBuildArtifacts, secondBuildArtifacts, BUILD_EQUIVALENCE);

        // Get all the common entries (artifacts with same SHA-1 and MD5 checksum)
        Map<String, BuildItemDiff> sameChecksumArtifacts = checksumDifferences.entriesInCommon();
        for (Map.Entry<String, BuildItemDiff> artifactDiffEntry : sameChecksumArtifacts.entrySet()) {
            BuildsDiffBaseFileModel buildsDiff;
            if (isDependencies) {
                buildsDiff = new BuildsDiffDependencyModel();
                BuildsDiffDependencyModel model = (BuildsDiffDependencyModel) buildsDiff;
                model.setInternalDependency(artifactDiffEntry.getValue().isInternalDependency());
                model.setScopes(artifactDiffEntry.getValue().getScopes());
                model.addBuildsDiffValue(BuildsDiffValue.UNCHANGED);
            } else {
                buildsDiff = new BuildsDiffArtifactModel();
            }
            BuildRepoPath repoPath = artifactDiffEntry.getValue().getRepoPath();
            BuildRepoPath buildRepoPath = getBuildRepoPath(repoPath);
            buildsDiff.setName(artifactDiffEntry.getValue().getId());
            buildsDiff.setDiffName(secondBuildArtifacts.get(artifactDiffEntry.getKey()).getId());
            buildsDiff.setModule(artifactDiffEntry.getValue().getModuleId());
            buildsDiff.setStatus(BuildsDiffStatus.UNCHANGED);
            buildsDiff.setRepoPath(buildRepoPath);
            buildsDiff.setType(artifactDiffEntry.getValue().getType());
            buildsDiff.setSha1(artifactDiffEntry.getValue().getSha1());
            buildsDiff.setMd5(artifactDiffEntry.getValue().getMd5());
            buildsDiffList.add(buildsDiff);
        }

        // Next, get entries from first build
        Map<String, BuildItemDiff> artifactsOnlyFromFirstBuild = checksumDifferences.entriesOnlyOnLeft();
        Map<String, BuildItemDiff> artifactsOnlyFromSecondBuild = checksumDifferences.entriesOnlyOnRight();
        Map<String, MapDifference.ValueDifference<BuildItemDiff>> artifactsDiffSameHash =
                checksumDifferences.entriesDiffering();
        buildsDiffList.addAll(
                getDiffsFromFirstBuild(artifactsOnlyFromFirstBuild, artifactsOnlyFromSecondBuild, isDependencies));

        // Last get entries from second build
        addDiffsFromSecondBuild(artifactsOnlyFromSecondBuild, buildsDiffList, isDependencies);
        addDiffsSameHash(artifactsDiffSameHash, buildsDiffList, isDependencies);

        return buildsDiffList;
    }

    private List<BuildsDiffBaseFileModel> getDiffsFromFirstBuild(Map<String, BuildItemDiff> artifactsOnlyFromFirstBuild,
            Map<String, BuildItemDiff> secondBuildArtifacts, boolean isDependencies) {
        List<BuildsDiffBaseFileModel> buildsDiffList = Lists.newArrayList();
        for (Map.Entry<String, BuildItemDiff> diffEntry : artifactsOnlyFromFirstBuild.entrySet()) {
            final BuildItemDiff firstArtifactDiff = diffEntry.getValue();

            boolean alreadyAdded = false;
            for (Map.Entry<String, BuildItemDiff> artifactDiffEntry : secondBuildArtifacts.entrySet()) {
                BuildItemDiff secondArtifactDiff = artifactDiffEntry.getValue();
                if (artifactDiffAreTheSame(firstArtifactDiff, secondArtifactDiff)) {
                    BuildsDiffBaseFileModel buildsDiff;
                    if (isDependencies) {
                        buildsDiff = new BuildsDiffDependencyModel();
                        BuildsDiffDependencyModel model = (BuildsDiffDependencyModel) buildsDiff;
                        model.setInternalDependency(firstArtifactDiff.isInternalDependency());
                        model.setScopes(firstArtifactDiff.getScopes());
                        model.addBuildsDiffValue(BuildsDiffValue.CHECKSUM);
                    } else {
                        buildsDiff = new BuildsDiffArtifactModel();
                    }
                    BuildRepoPath repoPath = firstArtifactDiff.getRepoPath();
                    BuildRepoPath buildRepoPath = getBuildRepoPath(repoPath);
                    buildsDiff.setName(firstArtifactDiff.getId());
                    buildsDiff.setDiffName(secondArtifactDiff.getId());
                    buildsDiff.setModule(firstArtifactDiff.getModuleId());
                    buildsDiff.setStatus(BuildsDiffStatus.UPDATED);
                    buildsDiff.setRepoPath(buildRepoPath);
                    buildsDiff.setType(firstArtifactDiff.getType());
                    buildsDiff.setSha1(firstArtifactDiff.getSha1());
                    buildsDiff.setMd5(firstArtifactDiff.getMd5());
                    buildsDiffList.add(buildsDiff);
                    alreadyAdded = true;

                    // Break here since we don't want to add multiple changes, for example if prev build had a-1.0,
                    //a-2.0
                    // and current build only has a-3.0 => we only add one change, the second will be marked as deleted!
                    break;
                }
            }

            if (!alreadyAdded) {
                BuildsDiffBaseFileModel buildsDiff;
                if (isDependencies) {
                    buildsDiff = new BuildsDiffDependencyModel();
                    BuildsDiffDependencyModel model = (BuildsDiffDependencyModel) buildsDiff;
                    model.setInternalDependency(firstArtifactDiff.isInternalDependency());
                    model.setScopes(firstArtifactDiff.getScopes());
                    model.addBuildsDiffValue(BuildsDiffValue.UNCHANGED);
                } else {
                    buildsDiff = new BuildsDiffArtifactModel();
                }
                BuildRepoPath repoPath = firstArtifactDiff.getRepoPath();
                BuildRepoPath buildRepoPath = getBuildRepoPath(repoPath);
                buildsDiff.setName(firstArtifactDiff.getId());
                buildsDiff.setModule(firstArtifactDiff.getModuleId());
                buildsDiff.setStatus(BuildsDiffStatus.NEW);
                buildsDiff.setRepoPath(buildRepoPath);
                buildsDiff.setType(firstArtifactDiff.getType());
                buildsDiff.setSha1(firstArtifactDiff.getSha1());
                buildsDiff.setMd5(firstArtifactDiff.getMd5());
                buildsDiffList.add(buildsDiff);
            }
        }

        return buildsDiffList;
    }

    private void addDiffsFromSecondBuild(Map<String, BuildItemDiff> artifactsOnlyFromSecondBuild,
            List<BuildsDiffBaseFileModel> buildsDiffList, boolean isDependencies) {

        for (Map.Entry<String, BuildItemDiff> diffEntry : artifactsOnlyFromSecondBuild.entrySet()) {
            String id = diffEntry.getValue().getId();

            boolean foundExisting = false;
            for (BuildsDiffBaseFileModel buildsDiff : buildsDiffList) {
                if (StringUtils.equals(id, buildsDiff.getName()) ||
                        StringUtils.equals(id, buildsDiff.getDiffName())) {
                    if (isDependencies) {
                        BuildsDiffDependencyModel buildsDiffDependencyModel = (BuildsDiffDependencyModel) buildsDiff;
                        if (!Objects.equals(diffEntry.getValue().getScopes(), buildsDiffDependencyModel.getScopes())) {
                            buildsDiffDependencyModel.addBuildsDiffValue(BuildsDiffValue.SCOPE);
                        }
                    }
                    foundExisting = true;
                    break;
                }
            }

            if (!foundExisting) {
                BuildsDiffBaseFileModel buildsDiff;
                if (isDependencies) {
                    buildsDiff = new BuildsDiffDependencyModel();
                    BuildsDiffDependencyModel model = (BuildsDiffDependencyModel) buildsDiff;
                    model.setInternalDependency(diffEntry.getValue().isInternalDependency());
                    model.setScopes(diffEntry.getValue().getScopes());
                    model.addBuildsDiffValue(BuildsDiffValue.UNCHANGED);
                } else {
                    buildsDiff = new BuildsDiffArtifactModel();
                }
                buildsDiff.setDiffName(id);
                buildsDiff.setModule(diffEntry.getValue().getModuleId());
                buildsDiff.setStatus(BuildsDiffStatus.REMOVED);
                buildsDiffList.add(buildsDiff);
            }
        }
    }

    private void addDiffsSameHash(Map<String, MapDifference.ValueDifference<BuildItemDiff>> artifactsDiffSameHash,
            List<BuildsDiffBaseFileModel> buildsDiffList, boolean isDependencies) {
        for (Map.Entry<String, MapDifference.ValueDifference<BuildItemDiff>> diffEntry :
                artifactsDiffSameHash.entrySet()) {
            BuildsDiffBaseFileModel buildsDiff;
            if (isDependencies) {
                buildsDiff = new BuildsDiffDependencyModel();
                BuildsDiffDependencyModel model = (BuildsDiffDependencyModel) buildsDiff;
                model.setInternalDependency(diffEntry.getValue().rightValue().isInternalDependency());
                model.setScopes(diffEntry.getValue().rightValue().getScopes());
                checkDependencyDiffFields(artifactsDiffSameHash, model);
            } else {
                buildsDiff = new BuildsDiffArtifactModel();
            }
            BuildRepoPath repoPath = diffEntry.getValue().rightValue().getRepoPath();
            BuildRepoPath buildRepoPath = getBuildRepoPath(repoPath);
            buildsDiff.setName(diffEntry.getValue().leftValue().getId());
            buildsDiff.setDiffName(diffEntry.getValue().rightValue().getId());
            buildsDiff.setModule(diffEntry.getValue().rightValue().getModuleId());
            buildsDiff.setStatus(BuildsDiffStatus.UPDATED);
            buildsDiff.setRepoPath(buildRepoPath);
            buildsDiff.setType(diffEntry.getValue().rightValue().getType());
            buildsDiff.setSha1(diffEntry.getValue().rightValue().getSha1());
            buildsDiff.setMd5(diffEntry.getValue().rightValue().getMd5());
            buildsDiffList.add(buildsDiff);
        }
    }

    private void checkDependencyDiffFields(Map<String, MapDifference.ValueDifference<BuildItemDiff>> diffSameHash,
            BuildsDiffDependencyModel model) {
        for (Map.Entry<String, MapDifference.ValueDifference<BuildItemDiff>> diffEntry : diffSameHash.entrySet()) {
            Set<String> rightScopes = diffEntry.getValue().rightValue().getScopes();
            Set<String> leftScopes = diffEntry.getValue().leftValue().getScopes();
            if (!Objects.equals(rightScopes, leftScopes)) {
                model.addBuildsDiffValue(BuildsDiffValue.SCOPE);
            }
        }
    }

    private boolean artifactDiffAreTheSame(BuildItemDiff firstArtifactDiff, BuildItemDiff secondArtifactDiff) {
        return StringUtils.equals(firstArtifactDiff.getBaseId(), secondArtifactDiff.getBaseId())
                && StringUtils.equals(firstArtifactDiff.getType(), secondArtifactDiff.getType());
    }

    @Nullable
    private static BuildRepoPath getBuildRepoPath(@Nullable BuildRepoPath repoPath) {
        return isNull(repoPath) ? null : new BuildRepoPath(repoPath.getRepoKey(), repoPath.getRelativePath());
    }
}
