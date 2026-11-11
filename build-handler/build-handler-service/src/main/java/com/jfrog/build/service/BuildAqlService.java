package com.jfrog.build.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.ArtifactoryBuildArtifact;
import com.jfrog.build.acl.model.BuildArtifactAqlRow;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildRepoDescriptor;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildWrapper;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.jfrog.build.util.BuildServiceUtils.isRestrictedRepository;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@RequiredArgsConstructor
public class BuildAqlService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildDatabaseService databaseService;

    @NonNull
    public Set<ArtifactoryBuildArtifact> getAllBuildArtifactsByAql(@NonNull BuildWrapper buildWrapper) {
        List<BuildArtifactAqlRow> aqlRows = bridgeToArtifactory.getBuildArtifactsByNameNumberTimestampByAql(
                buildWrapper, List.of(), List.of()
        );
        if (aqlRows.isEmpty()) {
            aqlRows = getAllBuildArtifactsByAqlFallback(buildWrapper);
        }
        Multimap<String, Artifact> sha1ToArtifacts = mapBuildArtifactsToSha1(buildWrapper.getBuild());
        Set<ArtifactoryBuildArtifact> matchedArtifacts = matchAllArtifactsToFileInfos(aqlRows, sha1ToArtifacts);
        log.debug("Matched {} build artifacts to repo paths", matchedArtifacts.size());
        return matchedArtifacts;
    }

    /**
     * This method is used to get the build artifacts without using the build.timestamp property.
     * It is used as a fallback/workaround when the build artifacts are not found using that property.
     * <p>
     * It can happen when the legacy Jenkins plugin is used, as it has a bug of a mismatch between
     * the build.timestamp property and the build time which is written in the build info manifest.
     * <p>
     * Different builds with the same name and number (and with different timestamps)
     * can be located in the same repo or in different repos (projects).
     * <p>
     * When multiple builds with same name and number exist only in same repo,
     * the method will return a mix of all artifacts of those builds.
     */
    @NonNull
    private List<BuildArtifactAqlRow> getAllBuildArtifactsByAqlFallback(@NonNull BuildWrapper buildWrapper) {
        Build build = buildWrapper.getBuild();
        String buildName = build.getName();
        String buildNumber = build.getNumber();
        String buildRepo = buildWrapper.getBuildRepo();
        log.info("No artifacts found for build {}:{}:{}", buildName, buildNumber, build.getStarted());
        List<String> repoKeys = databaseService.getBuildNumbersNotInRepo(buildName, buildNumber, buildRepo);
        if (repoKeys.isEmpty()) {
            log.info("Builds {}:{} exist only in repo {} - will use the fallback to search for  "
                    + "its artifacts without using build.timestamp", buildName, buildNumber, buildRepo);
            return bridgeToArtifactory.getBuildArtifactsByNameNumberByAql(buildWrapper, List.of(), List.of());
        }
        log.info("Builds {}:{} exist in repo {}, and also in other repos {} - will not use the fallback search for "
                + "its artifacts without using build.timestamp", buildName, buildNumber, buildRepo, repoKeys);
        return List.of();
    }

    @NonNull
    private Set<ArtifactoryBuildArtifact> matchAllArtifactsToFileInfos(
            @NonNull List<BuildArtifactAqlRow> aqlRows,
            @NonNull Multimap<String, Artifact> sha1ToArtifacts) {
        Set<ArtifactoryBuildArtifact> matchedArtifacts = new HashSet<>();
        List<String> virtualRepoKeys = bridgeToArtifactory.getVirtualRepoKeys();
        for (BuildArtifactAqlRow aqlRow : aqlRows) {
            if (isValidBuildArtifact(aqlRow.getRepoKey(), aqlRow.getName(), virtualRepoKeys)) {
                matchArtifactToFileInfo(aqlRow, sha1ToArtifacts, matchedArtifacts);
            }
        }
        return matchedArtifacts;
    }

    private void matchArtifactToFileInfo(
            @NonNull BuildArtifactAqlRow aqlRow,
            @NonNull Multimap<String, Artifact> sha1ToArtifacts,
            @NonNull Set<ArtifactoryBuildArtifact> matchedArtifacts) {
        BuildRepoPath repoPath = buildRepoPathFromAqlRow(aqlRow);
        if (sha1ToArtifacts.containsKey(aqlRow.getActualSha1())) {
            BuildFileInfo fileInfo = convertAqlRowToFileInfo(aqlRow);
            matchedArtifacts.add(new ArtifactoryBuildArtifact(null, fileInfo));
            log.debug("Artifact {} is matched by SHA1", repoPath);
        } else {
            log.warn("Artifact {} is not matched by SHA1", repoPath);
        }
    }

    @NonNull
    public Set<ArtifactoryBuildArtifact> getDistinctBuildArtifactsByAQL(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos,
            boolean useWeakMatchFallBack) {
        List<BuildArtifactAqlRow> aqlRows = bridgeToArtifactory.getBuildArtifactsByNameNumberTimestampByAql(
                buildWrapper, sourceRepos, excludedRepos
        );
        if (aqlRows.isEmpty()) {
            aqlRows = bridgeToArtifactory.getBuildArtifactsByNameNumberByAql(buildWrapper, sourceRepos, excludedRepos);
        }
        List<String> virtualRepoKeys = bridgeToArtifactory.getVirtualRepoKeys();
        Multimap<String, Artifact> sha1ToArtifacts = mapBuildArtifactsToSha1(buildWrapper.getBuild());

        List<BuildFileInfo> fileInfos = convertAqlRowToFileInfos(aqlRows);
        Set<ArtifactoryBuildArtifact> matchedArtifacts = matchDistinctArtifactsToFileInfos(
                fileInfos, sha1ToArtifacts, virtualRepoKeys
        );
        log.debug("Matched {} distinct build artifacts to actual paths returned by AQL", matchedArtifacts.size());
        if (useWeakMatchFallBack && !sha1ToArtifacts.isEmpty()) {
            matchRemainingArtifactsBySha1(
                    buildWrapper, virtualRepoKeys, sha1ToArtifacts, matchedArtifacts, sourceRepos, excludedRepos
            );
        }
        log.debug("Remaining artifacts not matched to actual paths: {}", sha1ToArtifacts.size());
        matchedArtifacts.addAll(collectEmptyRemainingArtifacts(sha1ToArtifacts));
        return matchedArtifacts;
    }

    /**
     * Matches FileInfos to build artifacts(created from an aql query's result) by checksum and by artifact name.
     * If indicated by {@param matchAnyChecksum} matches only by checksum if no exact match by name was found
     * <p>
     * This matching logic is kinda partial as we still can't match exactly sha1 to RepoPath because there's
     * not enough information from the BuildInfo (just sha1 and artifact id). It will not harm promotion \ push to
     * bintray etc. as ALL sha1's are still returned so nothing is skipped, this more of a UI issue that we are
     * currently reluctant to resolve (i.e. by introducing a unique id in the BuildInfo and put it as a property)
     */
    @NonNull
    @VisibleForTesting
    Set<ArtifactoryBuildArtifact> matchDistinctArtifactsToFileInfos(
            @NonNull List<BuildFileInfo> fileInfos,
            @NonNull Multimap<String, Artifact> sha1ToArtifacts,
            @NonNull List<String> virtualRepoKeys) {
        Set<ArtifactoryBuildArtifact> matchedArtifacts = new HashSet<>();
        for (BuildFileInfo fileInfo : fileInfos) {
            if (isValidBuildArtifact(fileInfo.getRepoKey(), fileInfo.getRelativePath(), virtualRepoKeys)) {
                Collection<Artifact> buildArtifacts = sha1ToArtifacts.get(fileInfo.getActualSha1());
                if (isNotEmpty(buildArtifacts)) {
                    matchArtifactToFileInfo(fileInfo, buildArtifacts, matchedArtifacts);
                }
            }
        }
        return matchedArtifacts;
    }

    private void matchArtifactToFileInfo(
            @NonNull BuildFileInfo fileInfo,
            @NonNull Collection<Artifact> buildArtifacts,
            @NonNull Set<ArtifactoryBuildArtifact> matchedArtifacts) {
        if (!matchExactArtifact(fileInfo, buildArtifacts, matchedArtifacts)) {
            log.debug("Exact match for {} not found, trying to match by checksum", fileInfo.getRepoPath().toString());
            matchAnyArtifactToFileInfo(fileInfo, buildArtifacts, matchedArtifacts);
        }
    }

    /**
     * Tries to match an artifact to a result based on its directory path and name.
     * <p>
     * The method first checks if the artifact's directory path (excluding the filename) matches the result's path.
     * If matched, then compares the artifact's name with the result's name.
     * <p>
     * If a match is found, the artifact is added to the result set and removed from the artifact list.
     */
    private boolean matchExactArtifact(
            @NonNull BuildFileInfo fileInfo,
            @NonNull Collection<Artifact> buildArtifacts,
            @NonNull Set<ArtifactoryBuildArtifact> matchedArtifacts) {
        Artifact matchedArtifact = findExactlyMatchedArtifact(fileInfo, buildArtifacts).orElse(null);
        if (nonNull(matchedArtifact)) {
            matchedArtifacts.add(new ArtifactoryBuildArtifact(matchedArtifact, fileInfo));
            log.debug("Matched (exactly) artifact: {} to path: {}", matchedArtifact.getName(), fileInfo.getRepoPath());
            buildArtifacts.remove(matchedArtifact);
        }
        return nonNull(matchedArtifact);
    }

    @NonNull
    private Optional<Artifact> findExactlyMatchedArtifact(
            @NonNull BuildFileInfo fileInfo,
            @NonNull Collection<Artifact> buildArtifacts) {
        return buildArtifacts.stream()
                .filter(Objects::nonNull)
                .filter(artifact -> matchArtifactByRelativePath(fileInfo, artifact))
                .filter(artifact -> matchArtifactByName(fileInfo, artifact))
                .findFirst();
    }

    private boolean matchArtifactByRelativePath(
            @NonNull BuildFileInfo fileInfo,
            @NonNull Artifact artifact) {
        String relativePath = artifact.getRemotePath();
        return nonNull(relativePath) && relativePath.equals(fileInfo.getRelativePath());
    }

    private boolean matchArtifactByName(
            @NonNull BuildFileInfo fileInfo,
            @NonNull Artifact artifact) {
        return nonNull(artifact.getName()) && artifact.getName().equals(fileInfo.getName());
    }

    private void matchAnyArtifactToFileInfo(
            @NonNull BuildFileInfo fileInfo,
            @NonNull Collection<Artifact> buildArtifacts,
            @NonNull Set<ArtifactoryBuildArtifact> matchedArtifacts) {
        Iterator<Artifact> artifactsIterator = buildArtifacts.iterator();
        Artifact matchedArtifact = artifactsIterator.next();
        matchedArtifacts.add(new ArtifactoryBuildArtifact(matchedArtifact, fileInfo));
        log.debug("Matched (by checksum) artifact: {} to path: {}", matchedArtifact.getName(), fileInfo.getRepoPath());
        artifactsIterator.remove();
    }

    /**
     * The "sha1ToArtifacts" contains all remaining artifacts that weren't matched.
     * Match them with the weak search only if indicated and if such remaining
     * unmatched artifacts still exist in the map.
     */
    private void matchRemainingArtifactsBySha1(
            @NonNull BuildWrapper buildWrapper,
            @NonNull List<String> virtualRepoKeys,
            @NonNull Multimap<String, Artifact> sha1ToArtifacts,
            @NonNull Set<ArtifactoryBuildArtifact> matchedArtifacts,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        log.debug("Unmatched artifacts exist and 'use weak match fallback' flag is set - executing weak match");
        List<BuildFileInfo> fileInfos = databaseService.getBuildArtifacts(buildWrapper, sourceRepos, excludedRepos);
        Set<ArtifactoryBuildArtifact> weaklyMatchedArtifacts = matchDistinctArtifactsToFileInfos(
                fileInfos, sha1ToArtifacts, virtualRepoKeys
        );
        log.debug("Weakly matched {} additional artifacts", weaklyMatchedArtifacts.size());
        matchedArtifacts.addAll(weaklyMatchedArtifacts);
    }

    /**
     * SHA1 to build artifacts are held in a multimap for duplicate occurrences
     * of a checksum for different artifacts so that the search result returns them all.
     */
    @NonNull
    private Multimap<String, Artifact> mapBuildArtifactsToSha1(@NonNull Build build) {
        List<Module> modules = build.getModules();
        if (isNull(modules)) {
            return ArrayListMultimap.create();
        }
        Multimap<String, Artifact> sha1ToArtifacts = mapBuildArtifactsToSha1(build, modules);
        log.debug("This build manifest contains {} unique artifacts ", sha1ToArtifacts.size());
        return sha1ToArtifacts;
    }

    @NonNull
    private Multimap<String, Artifact> mapBuildArtifactsToSha1(
            @NonNull Build build,
            @NonNull List<Module> modules) {
        Multimap<String, Artifact> sha1ToArtifacts = ArrayListMultimap.create();
        modules.stream()
                .map(Module::getArtifacts)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .forEach(artifact -> mapBuildArtifactToSha1(build, artifact, sha1ToArtifacts));
        return sha1ToArtifacts;
    }

    private void mapBuildArtifactToSha1(
            @NonNull Build build,
            @NonNull Artifact artifact,
            @NonNull Multimap<String, Artifact> sha1ToArtifacts) {
        if (nonNull(artifact.getSha1())) {
            sha1ToArtifacts.put(artifact.getSha1(), artifact);
        } else {
            String buildId = build.getName() + "/" + build.getNumber();
            log.warn("Missing SHA1 for artifact: {} in build: {}", artifact.getName(), buildId);
        }
    }

    /**
     * Collect remaining unmatched artifacts with {@code null} file-info values
     * to help users of this function know if all build artifacts were found.
     * (see the usage of the parent methods)
     */
    @NonNull
    private List<ArtifactoryBuildArtifact> collectEmptyRemainingArtifacts(
            @NonNull Multimap<String, Artifact> sha1ToArtifacts) {
        return sha1ToArtifacts.values().stream()
                .map(artifact -> new ArtifactoryBuildArtifact(artifact, null))
                .toList();
    }

    private boolean isValidBuildArtifact(
            @Nullable String repoKey,
            @Nullable String relativePath,
            @NonNull List<String> virtualRepoKeys) {
        return isNotBlank(repoKey)
                && isNotBlank(relativePath)
                && !isFromRestrictedRepository(repoKey)
                && !virtualRepoKeys.contains(repoKey);
    }

    private boolean isFromRestrictedRepository(@NonNull String repoKey) {
        Optional<BuildRepoDescriptor> repoDescriptor = bridgeToArtifactory.getRepoDescriptor(repoKey);
        return repoDescriptor.isPresent() && isRestrictedRepository(repoDescriptor.get());
    }

    @NonNull
    private List<BuildFileInfo> convertAqlRowToFileInfos(@NonNull List<BuildArtifactAqlRow> aqlRows) {
        return aqlRows.stream()
                .map(this::convertAqlRowToFileInfo)
                .toList();
    }

    @NonNull
    private BuildFileInfo convertAqlRowToFileInfo(@NonNull BuildArtifactAqlRow aqlRow) {
        return new BuildFileInfo(
                aqlRow.getNodeId(),
                buildRepoPathFromAqlRow(aqlRow),
                aqlRow.getCreatedBy(),
                aqlRow.getModifiedBy(),
                aqlRow.getOriginalMd5(),
                aqlRow.getActualMd5(),
                aqlRow.getOriginalSha1(),
                aqlRow.getActualSha1(),
                aqlRow.getSha2(),
                aqlRow.getSize(),
                ofNullable(aqlRow.getCreated()).map(Date::getTime).orElse(0L),
                ofNullable(aqlRow.getModified()).map(Date::getTime).orElse(0L),
                ofNullable(aqlRow.getUpdated()).map(Date::getTime).orElse(0L),
                null
        );
    }

    @NonNull
    private BuildRepoPath buildRepoPathFromAqlRow(@NonNull BuildArtifactAqlRow aqlRow) {
        return new BuildRepoPath(
                aqlRow.getRepoKey(),
                aqlRow.getDirectoryPath(),
                aqlRow.getName()
        );
    }
}
