package com.jfrog.build.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.acl.model.BuildRepoDescriptor;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.diff.BuildParams;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static com.jfrog.build.acl.util.BuildInfoUtils.getProjectKeyFromBuildInfoRepoKey;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getBuildNodePath;
import static com.jfrog.build.util.BuildServiceUtils.isRestrictedRepository;
import static java.lang.Long.parseLong;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.jfrog.access.common.ResourceType.REPO;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildDependencyService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildDatabaseService databaseService;
    private final BuildPermissionService permissionService;

    @Nullable
    public List<ModuleDependency> getModuleDependency(
            @NonNull String moduleId,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepoKey) {
        String buildStartedFormatted = formatBuildTime(buildStarted);
        permissionService.assertBasicReadPermissions(buildName, buildNumber, buildStartedFormatted, buildRepoKey);
        List<ModuleDependency> dependencies = databaseService.getModuleDependency(
                buildName, buildNumber, moduleId, buildStarted, buildRepoKey);
        Optional<String> lastPromotionRepoKey = getLastestPromotionRepoKey(buildName, buildNumber, buildStarted);
        populateDependenciesRepoPathData(dependencies, lastPromotionRepoKey, buildRepoKey);
        return dependencies;
    }

    @NonNull
    public List<ModuleDependency> getModuleDependencyForDiffWithPaging(BuildParams buildParams) {
        permissionService.assertBasicReadPermissions(buildParams.getBuildName(), buildParams.getCurrBuildNum(),
                formatBuildTime(buildParams.getCurrBuildDate()), buildParams.getBuildRepo());
        List<ModuleDependency> dependencies = databaseService.getModuleDependencyForDiffWithPaging(buildParams);
        populateDependenciesRepoPathData(dependencies, empty(), buildParams.getBuildRepo());
        return dependencies;
    }

    @NonNull
    private Optional<String> getLastestPromotionRepoKey(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted) {
        long buildStartedMillis = parseLong(buildStarted);
        BuildRun build = databaseService.getBuildRun(buildName, buildNumber, buildStartedMillis);
        return ofNullable(build).map(BuildRun::getReleaseRepoKey);
    }

    @VisibleForTesting
    void populateDependenciesRepoPathData(
            @NonNull List<ModuleDependency> dependencies,
            @NonNull Optional<String> promotionRepoKey,
            @NonNull String buildRepoKey) {
        if (!dependencies.isEmpty()) {
            List<String> sha1s = dependencies.stream().map(ModuleDependency::getSha1).toList();
            Map<String, List<BuildNode>> nodesBySha1 = databaseService.selectDependencyNodesBySha1(sha1s);
            dependencies.forEach(dependency -> populateDependencyRepoPathData(
                    dependency, nodesBySha1, promotionRepoKey, buildRepoKey));
        }
    }

    private void populateDependencyRepoPathData(
            @NonNull ModuleDependency dependency,
            @NonNull Map<String, List<BuildNode>> candidateNodesBySha1,
            @NonNull Optional<String> promotionRepoKey,
            @NonNull String buildRepoKey) {
        List<BuildNode> candidateNodes = candidateNodesBySha1.get(dependency.getSha1());
        if (nonNull(candidateNodes)) {
            BuildNode node = findPreferredDependencyNode(candidateNodes, dependency, promotionRepoKey, buildRepoKey);
            dependency.setRepoKey(node.getRepo());
            dependency.setPath(getBuildNodePath(node));
        }
    }

    @NonNull
    @VisibleForTesting
    BuildNode findPreferredDependencyNode(
            @NonNull List<BuildNode> candidateNodes,
            @NonNull ModuleDependency dependency,
            @NonNull Optional<String> promotionRepoKey,
            @NonNull String buildRepoKey) {
        int score = 0;
        BuildNode selectedNode = candidateNodes.getFirst();
        for (BuildNode candidateNode : candidateNodes) {
            int newScore = getDependencyNodeCandidateScore(
                    candidateNode, dependency, promotionRepoKey, buildRepoKey);
            if (newScore > score) {
                selectedNode = candidateNode;
                score = newScore;
            }
        }
        return selectedNode;
    }

    /**
     * 1st rank [score: 9] - from the last promotion, in the same project, having the same filename.
     * 2nd rank [score: 7] - from the last promotion, in the same project.
     * 3rd rank [score: 6] - from the last promotion, having the same filename.
     * 4th rank [score: 5] - in the same project, having the same filename.
     * 5th rank [score: 4] - from the last promotion.
     * 6th rank [score: 3] - in the same project.
     * 7th rank [score: 2] - having the same filename.
     * <p>
     * Note that the last check is relevant only for the generic package type.
     * We could explicitly isolate the behavior to generic only, but then it
     * would be necessary to fetch repository config for each node.
     * Skipping this check as this is already a very performance-sensitive area.
     */
    private int getDependencyNodeCandidateScore(
            @NonNull BuildNode node,
            @NonNull ModuleDependency dependency,
            @NonNull Optional<String> promotionRepoKey,
            @NonNull String buildRepoKey) {
        int score = 0;
        if (promotionRepoKey.isPresent() && node.getRepo().equals(promotionRepoKey.get())) {
            score += 4;
        }
        if (isNodeFromBuildProject(node, buildRepoKey)) {
            score += 3;
        }
        if (node.getName().equals(dependency.getName())) {
            score += 2;
        }
        return score;
    }

    private boolean isNodeFromBuildProject(
            @NonNull BuildNode node,
            @NonNull String buildRepoKey) {
        String buildProjectKey = getProjectKeyFromBuildInfoRepoKey(buildRepoKey);
        String nodeProjectKey = bridgeToArtifactory.getProjectForResource(node.getRepo(), REPO);
        return Objects.equals(buildProjectKey, nodeProjectKey);
    }

    @NonNull
    public Map<Dependency, BuildFileInfo> getBuildDependenciesFileInfos(@NonNull BuildWrapper buildWrapper) {
        List<BuildFileInfo> buildDependencies = databaseService.getBuildDependencies(
                buildWrapper.getName(), buildWrapper.getNumber(), buildWrapper.getBuildRepo());
        Multimap<String, Dependency> sha1ToBuildDependency = getBuildDependencies(buildWrapper.getBuild());
        log.debug("This build contains {} dependencies (taken from build info)", sha1ToBuildDependency.size());
        List<String> virtualRepoKeys = bridgeToArtifactory.getVirtualRepoKeys();
        Map<Dependency, BuildFileInfo> matchedDependencies = matchDependenciesToFileInfos(buildDependencies,
                sha1ToBuildDependency, virtualRepoKeys, bridgeToArtifactory, permissionService);

        log.debug("Matched {} build dependencies to actual paths returned by search", matchedDependencies.size());
        //Lastly, populate matchedDependencies with all remaining unmatched dependencies with null values to help users
        //of this function know if all build artifacts were found.
        log.debug("{} dependencies were not matched to actual paths", sha1ToBuildDependency.size());
        for (Dependency dependency : sha1ToBuildDependency.values()) {
            if (!matchedDependencies.containsKey(dependency)) {
                matchedDependencies.put(dependency, null);
            }
        }
        return matchedDependencies;
    }

    /**
     * Map all build dependencies to checksum, held in a multimap for occurrences of duplicate checksum for different
     * dependencies --> although we cannot be 100% positive which dependency took part in the build with the current
     * BuildInfo implementation.
     */
    public Multimap<String, Dependency> getBuildDependencies(Build build) {
        Multimap<String, Dependency> beansMap = HashMultimap.create();
        List<Module> modules = build.getModules();
        if (modules == null) {
            return beansMap;
        }
        for (Module module : modules) {
            if (module.getDependencies() != null) {
                for (Dependency dependency : module.getDependencies()) {
                    if (dependency.getSha1() != null) {
                        beansMap.put(dependency.getSha1(), dependency);
                    } else {
                        log.warn("Dependency: {} is missing SHA1, under build: {}",
                                dependency.getId(), build.getName());
                    }
                }
            }
        }
        return beansMap;
    }

    /**
     * We're making a best effort to guess the relevant dependency according to the id given by the BuildInfo,
     * if indeed more than one dependency is a match for the checksum. if not - the one found is used.
     * Unlike matching the build's artifacts above, a weak match is enough and maybe even recommended here as
     * dependencies might move around or get deleted and we will still find a correct one from another repo.
     */
    private Map<Dependency, BuildFileInfo> matchDependenciesToFileInfos(
            List<BuildFileInfo> buildDependencies,
            Multimap<String, Dependency> checksumToDependencyMap,
            List<String> virtualRepoKeys,
            BuildBridgeToArtifactory bridgeToArtifactory,
            BuildPermissionService permissionService) {
        Map<Dependency, BuildFileInfo> foundResults = Maps.newHashMap();
        buildDependencies.forEach(result -> match(
                checksumToDependencyMap,
                virtualRepoKeys,
                bridgeToArtifactory,
                permissionService,
                result,
                foundResults
        ));
        return foundResults;
    }

    private void match(
            Multimap<String, Dependency> checksumToDependencyMap,
            List<String> virtualRepoKeys,
            BuildBridgeToArtifactory bridgeToArtifactory,
            BuildPermissionService permissionService,
            BuildFileInfo candidate,
            Map<Dependency, BuildFileInfo> foundResults) {
        try {
            //Don't include results from virtual repos
            if (virtualRepoKeys.contains(candidate.getRepoKey())) {
                log.debug("Candidate {} skipped as it from virtual repo", candidate.getRepoPath());
                return;
            }
            Collection<Dependency> dependencies = checksumToDependencyMap.get(candidate.getActualSha1());
            if (CollectionUtils.isEmpty(dependencies)) {
                log.debug("Candidate {} skipped as it has no dependencies to match", candidate.getRepoPath());
                return;
            }
            BuildRepoPath repoPath = candidate.getRepoPath();
            // Do not attempt to match any dependencies from Release Bundle and Trash Can repositories.
            Optional<BuildRepoDescriptor> repoDescriptor = bridgeToArtifactory.getRepoDescriptor(repoPath.getRepoKey());
            if (repoDescriptor.isPresent() && isRestrictedRepository(repoDescriptor.get())) {
                log.debug("Skip dependency match to the path: {} as it from restricted repo", repoPath);
                return;
            }
            //Try matching dependencies exactly by id and add them to the results, overwriting previously
            //found (maybe not exactly matched) dependencies
            if (permissionService.canReadArtifact(repoPath.toString())) {
                tryExactDependencyMatch(foundResults, candidate, dependencies,
                        getDependencyFileInfoSelector(candidate, bridgeToArtifactory, "exact matching"));
                //Add all dependencies that weren't matched yet to make sure we don't leave false unmatched ones
                matchAnyDependencyToFileInfo(foundResults, dependencies,
                        getDependencyFileInfoSelector(candidate, bridgeToArtifactory, "non-exact matching"));
            } else {
                log.debug("Skip path dependency match due to missing read permission: {}", repoPath);
            }
        } catch (Exception e) {
            log.debug("Error creating path from aql result: {} :\n {}", candidate, e.getMessage());
            log.debug("", e);
        }
    }

    /**
     * Tries to match dependencies exactly to a path (file info) based on the dependency's name,
     * and adds matched dependencies to the result map.
     */
    private void tryExactDependencyMatch(
            Map<Dependency, BuildFileInfo> foundResults,
            BuildFileInfo result, Collection<Dependency> dependencies,
            BiFunction<Dependency, BuildFileInfo, BuildFileInfo> fileInfoSelector) {
        dependencies.stream()
                .filter(Objects::nonNull)
                .filter(dependency -> nonNull(dependency.getId()))
                .filter(dependency -> isExactMatch(result, dependency))
                .forEach(dependency -> foundResults.compute(dependency, fileInfoSelector));
    }

    private boolean isExactMatch(BuildFileInfo result, Dependency dependency) {
        return dependency.getId().contains(result.getBuildDependencyName());
    }

    /**
     * Adds each dependency in the collection to the result map, if it was not already matched before.
     */
    private void matchAnyDependencyToFileInfo(
            Map<Dependency, BuildFileInfo> foundResults,
            Collection<Dependency> dependencies,
            BiFunction<Dependency, BuildFileInfo, BuildFileInfo> fileInfoSelector) {
        dependencies.forEach(dependency -> foundResults.compute(dependency, fileInfoSelector));
    }

    /**
     * Selects a FileInfo for the given Dependency.
     * <p>
     * Selection priority rules:
     * <ol>
     *     <li> A candidate file info is from a Local repo,
     *          while the current file info is from a remote cache.
     *     <li> A candidate file info has the same repo path, as the build dependency's path,
     *          while the current file info does not have it.
     *          Some customers populate dependency paths, even though CI agents do not support it.
     *          For instance, JFrog CLI does not as of the time of this comment.
     *     <li> A candidate file info has the same name, as the build dependency's ID.
     *          This is relevant to generic dependencies.
     * </ol>
     */
    @NonNull
    private BiFunction<Dependency, BuildFileInfo, BuildFileInfo> getDependencyFileInfoSelector(
            @NonNull BuildFileInfo candidate,
            @NonNull BuildBridgeToArtifactory bridgeToArtifactory,
            @NonNull String operationDescription) {
        return (dependency, current) -> {
            String dependencyId = dependency.getId();
            String dependencyPath = dependency.getRemotePath();
            if (isNull(current)) {
                logDependencyFileInfoFound(dependency, candidate, operationDescription);
                return candidate;
            }
            if (!isPreferredDependencyRepo(current, bridgeToArtifactory)
                    && isPreferredDependencyRepo(candidate, bridgeToArtifactory)) {
                logDependencyFileInfoReplaced(dependency, current, candidate, operationDescription);
                return candidate;
            }
            if (current.getRepoPath().toString().equals(dependencyPath)) {
                return current;
            }
            if (candidate.getRepoPath().toString().equals(dependencyPath)) {
                logDependencyFileInfoReplaced(dependency, current, candidate, operationDescription);
                return candidate;
            }
            if (current.getName().equals(dependencyId)) {
                return current;
            }
            if (candidate.getName().equals(dependencyId)) {
                logDependencyFileInfoReplaced(dependency, current, candidate, operationDescription);
                return candidate;
            }
            return current;
        };
    }

    private boolean isPreferredDependencyRepo(
            @NonNull BuildFileInfo fileInfo,
            @NonNull BuildBridgeToArtifactory bridgeToArtifactory) {
        String repoKey = fileInfo.getRepoKey();
        Optional<BuildRepoDescriptor> repoDescriptor = bridgeToArtifactory.getRepoDescriptor(repoKey);
        return repoDescriptor.isPresent()
                && repoDescriptor.get().isLocalOrFederated()
                && !repoDescriptor.get().isCache();
    }

    private void logDependencyFileInfoFound(
            @NonNull Dependency dependency,
            @NonNull BuildFileInfo candidate,
            @NonNull String operationDescription) {
        log.debug("Found {} for dependency: {}, sha1: {}, {}",
                operationDescription, dependency.getId(), dependency.getSha1(),
                candidate.getRepoPath());
    }

    private void logDependencyFileInfoReplaced(
            @NonNull Dependency dependency,
            @NonNull BuildFileInfo current,
            @NonNull BuildFileInfo candidate,
            @NonNull String operationDescription) {
        log.info("Replacing {} for dependency: {}, sha1: {}, {} -> {}",
                operationDescription, dependency.getId(), dependency.getSha1(),
                current.getRepoPath(), candidate.getRepoPath());
    }
}
