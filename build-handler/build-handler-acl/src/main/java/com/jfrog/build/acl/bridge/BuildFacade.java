package com.jfrog.build.acl.bridge;

import com.jfrog.bh.acl.producer.model.BundleInputBuild;
import com.jfrog.build.acl.model.ArtifactoryBuildArtifact;
import com.jfrog.build.acl.model.BuildChecksumType;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildImportSettings;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildSearchCriteria;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.ProducedBy;
import com.jfrog.build.acl.model.UsedBy;
import com.jfrog.build.acl.model.api.BuildArtifactsRequest;
import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.common.status.MutableStatusHolder;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Dependency;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Stream;

public interface BuildFacade {

    @NonNull
    String addBuild(
            @NonNull Build build,
            @NonNull String buildRepo);

    void addBuildInternal(
            @NonNull BuildWrapper buildWrapper);

    void attachReleaseBundle(
            @NonNull BundleInputBuild build,
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull String version);

    void detachReleaseBundle(
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull String version);

    boolean canReadBuild(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo);

    boolean canUploadBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo);

    @NonNull
    BuildPermissionsModel getBuildEffectivePermissions(
            @NonNull String repoKey,
            @NonNull String path);

    @Transactional
    void deleteBuild(
            BuildRun build,
            MutableStatusHolder status,
            boolean deleteArtifacts);

    void deleteBuild(
            @NonNull BuildRun buildRun,
            @NonNull String buildRepo,
            @NonNull MutableStatusHolder status);

    void deleteBuildInternal(
            @NonNull Build build,
            @NonNull String buildRepo,
            @NonNull MutableStatusHolder status);

    Set<BuildRun> findBuildsForChecksum(
            BuildSearchCriteria criteria,
            BuildChecksumType type,
            String checksum,
            @NonNull String buildRepo);

    Set<String> getAllBuildInfoRepos();

    @Nullable
    BuildWrapper getBuild(
            BuildRun buildRun);

    /**
     * Returns build artifacts map according to the param input regexp patterns.
     *
     * @param buildArtifactsRequest A wrapper which contains the necessary parameters
     * @return A map from {@code FileInfo} to their target directories relative paths
     * @see BuildArtifactsRequest
     */
    Map<BuildFileInfo, String> getBuildArtifacts(
            BuildArtifactsRequest buildArtifactsRequest);

    /**
     * Returns an archive file according to the param archive type (zip/tar/tar.gz/tgz) which contains
     * all build artifacts according to the given build name and number (can be latest or latest by status).
     *
     * @param buildArtifactsRequest A wrapper which contains the necessary parameters
     * @return The archived file of build artifacts with their hierarchy rules
     * @see BuildArtifactsRequest
     */
    File getBuildArtifactsArchive(
            BuildArtifactsRequest buildArtifactsRequest) throws IOException;

    Set<ArtifactoryBuildArtifact> getBuildArtifactsFileInfos(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos,
            boolean useFallback);

    String getBuildAsJson(
            BuildRun buildRun);

    Map<Dependency, BuildFileInfo> getBuildDependenciesFileInfos(
            @NonNull BuildWrapper buildWrapper);

    @NonNull
    String getBuildInfoRepoForDefaultProject();

    String getBuildInfoRepoKeyByProject(
            String projectKey,
            boolean checkProjectExists);

    @Nullable
    BuildWrapper getBuildModelFromFile(
            @NonNull BuildRepoPath buildJsonPath);

    List<String> getBuildNames(
            String repoKey);

    BuildRun getBuildRun(
            String buildName,
            String buildNumber,
            String buildStarted,
            @NonNull String buildRepo);

    int getBuildsCountByRepoKey(
            String repoKey);

    List<BuildNameAndRepo> getBuildsInfoInternally(
            String buildRepo);

    @Nullable
    BuildWrapper getLatestBuildByNameAndNumber(
            String buildName,
            String buildNumber,
            String buildRepo);

    @NonNull
    String getPreferredBuildRepo(
            @Nullable String repoKey,
            @Nullable String projectKey);

    int getTotalBuildsCount();

    int getUniqueBuildsCountByRepoKey(
            String repoKey);

    boolean hasBasicReadPermissionForAllBuilds();

    void importBuilds(
            @NonNull BuildImportSettings settings);

    boolean isBuildBasicRead(
            String buildName,
            @NonNull String repoKey);

    boolean isBuildBasicRead(
            String buildName,
            String buildNumber,
            String buildStarted,
            String buildRepo);

    boolean isBuildExistsInDb(
            BuildWrapper build);

    boolean isBuildInfoRepo(
            String repoKey);

    @NonNull
    String normaliseBuildRepoName(
            @Nullable String repoKey);

    void preAddBuild(
            @NonNull Build build,
            @NonNull String buildRepo);

    /**
     * @return list of builds that consume as dependency the artifact matching either md5 or sha1 (can be one or both)
     */
    Stream<UsedBy> retrieveDependantBuilds(
            String sha1,
            String md5);

    /**
     * @return list of builds that produced artifact matching either md5 or sha1 (can be one or both)
     */
    Stream<ProducedBy> retrieveProducedBuilds(
            String sha1,
            String md5);

    SortedSet<BuildRun> searchBuildsByName(
            String buildName,
            @NonNull String buildRepo);

    Set<BuildRun> searchBuildsByNameAndNumber(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo);

    Set<BuildRun> searchBuildsByNameAndNumberInternal(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo);

    Set<BuildRun> searchBuildsByNameInternally(
            @Nullable String buildName);

    void updateBuild(
            @NonNull BuildWrapper buildWrapper);
}
