package com.jfrog.build.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.ArtifactoryBuildArtifact;
import com.jfrog.build.acl.model.BuildArtifactChecksums;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.acl.model.BuildPatternArtifactsRequestWithRepo;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.api.BuildArtifactsRequest;
import com.jfrog.build.mapper.BuildArtifactMapper;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.builder.dependency.PatternArtifactBuilder;
import org.jfrog.build.api.dependency.BuildPatternArtifacts;
import org.jfrog.build.api.dependency.Pattern;
import org.jfrog.build.api.dependency.PatternArtifact;
import org.jfrog.build.api.dependency.PatternResult;
import org.jfrog.build.api.release.PromotionStatus;
import org.jfrog.common.ArchiveUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.common.archive.ArchiveType;
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.filter;
import static com.jfrog.build.service.BuildArtifactService.ArtifactsCollectionResult.ERROR;
import static com.jfrog.build.service.BuildArtifactService.ArtifactsCollectionResult.REPLACED;
import static com.jfrog.build.service.BuildArtifactService.ArtifactsCollectionResult.VALID;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.forbiddenException;
import static org.jfrog.storage.binstore.utils.TmpFileHelper.createTempFile;

@Slf4j
@RequiredArgsConstructor
public class BuildArtifactService {

    private static final String LATEST_BUILD = "LATEST";
    private static final String LAST_RELEASED_BUILD = "LAST_RELEASE";

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildAqlService aqlService;
    private final BuildPermissionService permissionService;
    private final BuildReadService readService;
    private final BuildRepoService repoService;
    private final BuildSysConfigService sysConfigService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    {
        pathMatcher.setTrimTokens(true);
    }

    public enum ArtifactsCollectionResult {
        VALID,
        REPLACED,
        ERROR
    }

    @NonNull
    public Set<ArtifactoryBuildArtifact> getBuildArtifactsFileInfos(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos,
            boolean useFallBack) {
        Build build = buildWrapper.getBuild();
        if (isNull(build) || isNull(build.getModules())) {
            return Set.of();
        }
        Set<ArtifactoryBuildArtifact> buildArtifacts = new LinkedHashSet<>();
        if (collectBuildArtifactFileInfosByManifest(build, sourceRepos, excludedRepos, buildArtifacts)) {
            return buildArtifacts;
        }
        return aqlService.getDistinctBuildArtifactsByAQL(buildWrapper, sourceRepos, excludedRepos, useFallBack);
    }

    @NonNull
    public Optional<BuildArtifactChecksums> getArtifactChecksums(
            @NonNull String repoKey,
            @NonNull String path) {
        return bridgeToArtifactory.getArtifactChecksums(repoKey, path);
    }

    @NonNull
    public Set<ArtifactoryBuildArtifact> getBuildArtifactsFileInfos(@NonNull BuildWrapper buildWrapper) {
        Build build = buildWrapper.getBuild();
        if (isNull(build) || isNull(build.getModules())) {
            return Set.of();
        }
        List<Artifact> artifacts = collectArtifactsFromModules(build);
        if (haveMissingOriginalRepoKeyOrPath(artifacts) || !populateDefaultDeploymentRepoKeys(artifacts)) {
            log.warn("Missing full artifacts path in modules of build {},{},{}. Falling back to fetching all by AQL...",
                    buildWrapper.getName(), build.getNumber(), build.getStarted());
            return aqlService.getAllBuildArtifactsByAql(buildWrapper);
        }
        Set<ArtifactoryBuildArtifact> buildArtifacts = new LinkedHashSet<>();
        for (Artifact artifact : artifacts) {
            String repoPath = artifact.getOriginalDeploymentRepo() + "/" + artifact.getRemotePath();
            try {
                BuildFileInfo fileInfo = bridgeToArtifactory.getFileInfo(repoPath);
                buildArtifacts.add(new ArtifactoryBuildArtifact(artifact, fileInfo));
            } catch (NotFoundException e) {
                log.info("Failed to load build info item: {}. Skipping...", repoPath, e);
            } catch (BadRequestException e) {
                log.warn("Incorrect build artifact reference: {}. Skipping...", repoPath, e);
            }
        }
        return buildArtifacts;
    }

    public BuildPatternArtifacts getBuildPatternArtifacts(
            @NonNull BuildPatternArtifactsRequestWithRepo buildPatternArtifactsRequest,
            String servletContextUrl) {
        String buildName = buildPatternArtifactsRequest.getBuildName();
        String buildNumber = buildPatternArtifactsRequest.getBuildNumber();
        String buildRepo = repoService.getPreferredBuildRepo(buildPatternArtifactsRequest.getBuildRepo(),
                buildPatternArtifactsRequest.getProject());

        BuildWrapper buildWrapper = getBuildByNameAndNumber(buildName, buildNumber, null, buildRepo);
        if (buildWrapper == null || !buildWrapper.isBuildExist()) {
            return null;
        }
        Build build = buildWrapper.getBuild();
        if (!permissionService.isBuildBasicRead(
                buildName, buildNumber, build.getStarted(), buildWrapper.getBuildRepo())) {
            return null;
        }
        BuildPatternArtifacts buildPatternArtifacts = new BuildPatternArtifacts(buildName, build.getNumber(),
                build.getStarted(), build.getUrl());
        Set<BuildFileInfo> fileInfos = Sets.newHashSet(toFileInfoList(
                getBuildArtifactsFileInfosWithFallback(buildWrapper, null)));
        for (Pattern pattern : buildPatternArtifactsRequest.getPatterns()) {
            final String patternStr = pattern.getPattern().replaceFirst(":", "/");
            buildPatternArtifacts.addPatternResult(getPatternResult(servletContextUrl, fileInfos, patternStr));
        }
        return buildPatternArtifacts;
    }

    public Map<BuildFileInfo, String> getBuildArtifacts(BuildArtifactsRequest request) {
        String buildName = request.getBuildName();
        String buildNumber = request.getBuildNumber();
        String buildStatus = request.getBuildStatus();
        String buildRepo = repoService.getPreferredBuildRepo(request.getBuildRepo(), request.getProject());

        BuildWrapper buildWrapper = null;
        if (isNotBlank(buildNumber)) {
            buildWrapper = getBuildByNameAndNumber(buildName, buildNumber, null, buildRepo);
        } else if (isNotBlank(buildStatus)) {
            buildWrapper = getBuildByNameAndNumber(buildName, buildStatus, buildStatus, buildRepo);
        }

        if (buildWrapper == null || !buildWrapper.isBuildExist()) {
            log.debug("Couldn't find build {} from {} repo", buildName, buildRepo);
            return null;
        }
        Build build = buildWrapper.getBuild();
        if (!permissionService.isBuildBasicRead(build.getName(), build.getNumber(), build.getStarted(),
                buildWrapper.getBuildRepo())) {
            throw forbiddenException("User is not authorized to access build %s:%s artifacts",
                    build.getName(), build.getNumber());
        }
        Set<BuildFileInfo> fileInfos = Sets.newHashSet(toFileInfoList(
                getBuildArtifactsFileInfosWithFallback(buildWrapper, request.getRepos())));
        fileInfos = filterByReadPermissions(fileInfos);
        fileInfos = filterByRepoKeys(fileInfos, request.getRepos());
        return BuildArtifactMapper.resolve(fileInfos, request.getMappings());
    }

    public File getBuildArtifactsArchive(BuildArtifactsRequest buildArtifactsRequest) throws IOException {
        Map<BuildFileInfo, String> buildArtifacts = getBuildArtifacts(buildArtifactsRequest);
        if (buildArtifacts == null || buildArtifacts.isEmpty()) {
            log.debug("Skipping build artifacts archive, no results found.");
            return null;
        }
        File workTmpDir = bridgeToArtifactory.getTempWorkDir();
        String archiveDirName = getUniqueBuildArtifactsArchiveDirName(buildArtifactsRequest);
        File archiveTmpDir = createTempFile(workTmpDir, archiveDirName + "_", null, null);
        FileUtils.deleteDirectory(archiveTmpDir);

        log.info("Creating build artifacts temp archive directory: {}", archiveTmpDir.getAbsolutePath());
        FileUtils.forceMkdir(archiveTmpDir);

        try {
            log.info("Creating build artifacts archive...");
            for (Map.Entry<BuildFileInfo, String> entry : buildArtifacts.entrySet()) {
                BuildFileInfo fileInfo = entry.getKey();
                String repoPath = fileInfo.getRepoPath().toString();
                try (InputStream inputStream = bridgeToArtifactory.getResourceStream(repoPath)) {
                    File file = resolveArchiveEntryFile(archiveTmpDir, entry.getValue());
                    FileUtils.copyInputStreamToFile(inputStream, file);
                }
            }

            ArchiveType archiveType = buildArtifactsRequest.getType();
            File archiveFile = createTempFile(workTmpDir, archiveDirName + "_", null, "." + archiveType.value());
            ArchiveUtils.archive(archiveTmpDir, archiveFile, true, archiveType);
            log.info("Finished archiving build artifacts: '{}'", archiveFile.getAbsolutePath());
            return archiveFile;
        } finally {
            //Delete the exploded directory (and anything already copied into it) on every exit path,
            //including when an entry is rejected by resolveArchiveEntryFile.
            log.info("Deleting build artifacts temp archive directory.");
            FileUtils.deleteDirectory(archiveTmpDir);
        }
    }

    /**
     * Resolves a build artifacts mapping output (after placeholder substitution) against the archive temp dir,
     * rejecting any path that canonicalizes outside of it (e.g. via "../" or a "$N" placeholder substituted
     * with a path separator). Prevents planting a file anywhere on disk during archive extraction (RTDEV-93954).
     */
    @VisibleForTesting
    @NonNull
    static File resolveArchiveEntryFile(@NonNull File archiveTmpDir, @NonNull String relativePath)
            throws IOException {
        File baseDir = archiveTmpDir.getCanonicalFile();
        File resolvedFile = new File(baseDir, relativePath).getCanonicalFile();
        String basePathString = baseDir.getPath() + File.separator;
        if (!resolvedFile.getPath().startsWith(basePathString)) {
            throw new BadRequestException("Invalid output path included: " + relativePath);
        }
        return resolvedFile;
    }

    public Map<String, BuildRepoPath> mapSha1ToRepoPathOfFileNodes(List<String> buildFileBeanSha1List) {
        return getFileNodesBySha1(buildFileBeanSha1List);
    }

    public Map<String, BuildRepoPath> getFileNodesBySha1(List<String> sha1List) {
        List<BuildNode> nodes = bridgeToArtifactory.getNodesBySha1(sha1List);
        return StreamSupportUtils.stream(nodes).collect(Collectors.toMap(
                BuildNode::getSha1Actual,
                node -> new BuildRepoPath(node.getRepo(), node.getPath()),
                (repoPathA, repoPathB) -> repoPathA)
        );
    }

    @NonNull
    public static Iterable<BuildFileInfo> toFileInfoList(Set<ArtifactoryBuildArtifact> artifacts) {
        return artifacts.stream()
                .filter(Objects::nonNull)
                .map(ArtifactoryBuildArtifact::getFileInfo)
                .collect(Collectors.toList());
    }

    private Set<ArtifactoryBuildArtifact> getBuildArtifactsFileInfosWithFallback(
            BuildWrapper buildWrapper,
            List<String> reposToSearch) {
        Set<ArtifactoryBuildArtifact> infos = getBuildArtifactsFileInfos(buildWrapper, reposToSearch, null, true);
        if (infos.isEmpty()) {
            return Sets.newHashSet();
        }
        return infos;
    }

    private BuildWrapper getBuildByNameAndNumber(
            String buildName,
            String buildNumber,
            String buildStatus,
            String buildRepo) {
        BuildWrapper build;
        if (buildNumber.equals(LATEST_BUILD)) {
            build = readService.getLatestBuildByNameAndStatus(buildName, buildRepo, buildNumber);
        } else if (buildNumber.equals(LAST_RELEASED_BUILD)) {
            build = readService.getLatestBuildByNameAndStatus(buildName, buildRepo, PromotionStatus.RELEASED);
        } else if (isNotBlank(buildStatus)) {
            build = readService.getLatestBuildByNameAndStatus(buildName, buildRepo, buildStatus);
        } else {
            build = readService.getLatestBuildByNameAndNumberInternally(buildName, buildNumber, buildRepo);
        }
        if (build == null) {
            log.warn(
                    "Build [{}], number [{}], repo [{}] - no results found, check correctness of build name and build" +
                            " number.",
                    buildName, buildNumber, buildRepo);
            return null;
        }

        return build;
    }

    private PatternResult getPatternResult(
            String servletContextUrl,
            Set<BuildFileInfo> fileInfos,
            String patternStr) {
        return new PatternResult(
                fileInfos.stream()
                        .filter(Objects::nonNull)
                        .filter(fileInfo -> pathMatcher.match(patternStr, fileInfo.getRepoPath().toString()))
                        .map(fileInfo -> toPatternArtifact(servletContextUrl, fileInfo))
                        .collect(Collectors.toList()));
    }

    private PatternArtifact toPatternArtifact(
            String servletContextUrl,
            BuildFileInfo fileInfo) {
        return new PatternArtifactBuilder()
                .artifactoryUrl(servletContextUrl)
                .uri(fileInfo.getRepoPath().toString())
                .sha1(fileInfo.getActualSha1())
                .size(fileInfo.getSize())
                .lastModifiedDate(new Date(fileInfo.getModified()))
                .build();
    }

    private String getUniqueBuildArtifactsArchiveDirName(BuildArtifactsRequest buildArtifactsRequest) {
        StringBuilder builder = new StringBuilder(buildArtifactsRequest.getBuildName()).append("-");
        String buildNumber = buildArtifactsRequest.getBuildNumber();
        if (StringUtils.isBlank(buildNumber)) {
            builder.append(buildArtifactsRequest.getBuildStatus());
        } else {
            builder.append(buildNumber);
        }

        String buildName = builder.toString();
        return buildName.replaceAll("\\W+", "_");
    }

    private Set<BuildFileInfo> filterByReadPermissions(Set<BuildFileInfo> fileInfos) {
        return Sets.newHashSet(filter(fileInfos, input ->
                input != null && permissionService.canReadArtifact(input.getRepoPath().toString())));
    }

    private Set<BuildFileInfo> filterByRepoKeys(
            Set<BuildFileInfo> fileInfos,
            List<String> reposToSearch) {
        if (reposToSearch == null || reposToSearch.isEmpty()) {
            return fileInfos;
        }
        return Sets.newHashSet(filter(fileInfos, input -> input != null && reposToSearch.contains(input.getRepoKey())));
    }

    private boolean collectBuildArtifactFileInfosByManifest(
            @NonNull Build build,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos,
            @NonNull Set<ArtifactoryBuildArtifact> buildArtifacts) {
        if (CollectionUtils.isNotEmpty(sourceRepos) || CollectionUtils.isNotEmpty(excludedRepos)) {
            return false;
        }
        List<Artifact> artifacts = collectArtifactsFromModules(build);
        if (haveMissingOriginalRepoKeyOrPath(artifacts) || !populateDefaultDeploymentRepoKeys(artifacts)) {
            return false;
        }
        if (sysConfigService.isBuildArtifactsQueryDeduplicationEnabled()) {
            log.info("Collecting build artifacts with deduplication by repository path");
            return collectDeduplicatedBuildArtifactFileInfosByManifest(artifacts, buildArtifacts);
        }
        for (Artifact artifact : artifacts) {
            if (collectBuildArtifactFileInfoByManifest(artifact, buildArtifacts) != VALID) {
                return false;
            }
        }
        return true;
    }

    private boolean collectDeduplicatedBuildArtifactFileInfosByManifest(
            @NonNull List<Artifact> artifacts,
            @NonNull Set<ArtifactoryBuildArtifact> buildArtifacts) {
        Map<String, List<Artifact>> artifactsByRepoPath = groupDuplicatedArtifacts(artifacts);
        for (Artifact artifact : artifacts) {
            ArtifactsCollectionResult result = collectBuildArtifactFileInfoByManifest(artifact, buildArtifacts);
            if (result == VALID) {
                artifactsByRepoPath.remove(getRepoPath(artifact));
            }
            if (result == ERROR) {
                return false;
            }
        }
        return artifactsByRepoPath.isEmpty();
    }

    @NonNull
    private Map<String, List<Artifact>> groupDuplicatedArtifacts(@NonNull List<Artifact> artifacts) {
        Map<String, List<Artifact>> groupedArtifacts = new LinkedHashMap<>();
        for (Artifact artifact : artifacts) {
            groupedArtifacts.computeIfAbsent(getRepoPath(artifact), key -> new LinkedList<>()).add(artifact);
        }
        return groupedArtifacts;
    }

    @NonNull
    private String getRepoPath(@NonNull Artifact artifact) {
        return artifact.getOriginalDeploymentRepo() + ":" + artifact.getRemotePath();
    }

    @NonNull
    private ArtifactsCollectionResult collectBuildArtifactFileInfoByManifest(
            @NonNull Artifact artifact,
            @NonNull Set<ArtifactoryBuildArtifact> buildArtifacts) {
        String repoPath = artifact.getOriginalDeploymentRepo() + "/" + artifact.getRemotePath();
        try {
            BuildFileInfo fileInfo = bridgeToArtifactory.getFileInfo(repoPath);
            if (!Objects.equals(fileInfo.getActualSha1(), artifact.getSha1())) {
                log.info("Found replaced build info item: {}. Falling back to fetching by AQL", repoPath);
                return REPLACED;
            }
            buildArtifacts.add(new ArtifactoryBuildArtifact(artifact, fileInfo));
        } catch (NotFoundException e) {
            log.info("Not found build info item: {}. Falling back to fetching by AQL", repoPath);
            return ERROR;
        } catch (Exception e) {
            log.warn("Failed to load build info item: {}. Falling back to fetching by AQL", repoPath, e);
            return ERROR;
        }
        return VALID;
    }

    @NonNull
    private List<Artifact> collectArtifactsFromModules(@NonNull Build build) {
        return build.getModules().stream()
                .map(Module::getArtifacts)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .toList();
    }

    private boolean haveMissingOriginalRepoKeyOrPath(@NonNull List<Artifact> artifacts) {
        return artifacts.stream().anyMatch(artifact ->
                isBlank(artifact.getOriginalDeploymentRepo()) || isBlank(artifact.getRemotePath()));
    }

    private boolean populateDefaultDeploymentRepoKeys(@NonNull List<Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            String originalRepoKey = artifact.getOriginalDeploymentRepo();
            try {
                Optional<String> repoKey = bridgeToArtifactory.getDefaultDeploymentRepoKey(originalRepoKey);
                if (repoKey.isEmpty()) {
                    log.warn("Missing default deployment repo key for the build artifact: repoKey: {}, path: {}",
                            artifact.getOriginalDeploymentRepo(), artifact.getRemotePath());
                    return false;
                }
                artifact.setOriginalDeploymentRepo(repoKey.get());
            } catch (Exception e) {
                log.warn("Failed get default deployment repo key for build artifact: repoKey: {}, path: {}",
                        artifact.getOriginalDeploymentRepo(), artifact.getRemotePath(), e);
                return false;
            }
        }
        return true;
    }
}
