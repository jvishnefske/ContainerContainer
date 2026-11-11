package com.jfrog.build.bridge;

import com.jfrog.bh.acl.producer.model.BundleInputBuild;
import com.jfrog.build.acl.bridge.BuildFacade;
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
import com.jfrog.build.service.BuildArtifactService;
import com.jfrog.build.service.BuildCreationService;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildDependencyService;
import com.jfrog.build.service.BuildImportService;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildReleaseBundleService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.common.status.MutableStatusHolder;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Dependency;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class BuildFacadeImpl implements BuildFacade {

    private final BuildArtifactService artifactService;
    private final BuildCreationService creationService;
    private final BuildDatabaseService databaseService;
    private final BuildDeletionService deletionService;
    private final BuildDependencyService dependencyService;
    private final BuildImportService importService;
    private final BuildPermissionService permissionService;
    private final BuildReadService readService;
    private final BuildReleaseBundleService releaseBundleService;
    private final BuildRepoService repoService;

    @Override
    @NonNull
    public String addBuild(
            @NonNull Build build,
            @NonNull String buildRepo) {
        return creationService.createBuild(build, buildRepo);
    }

    @Override
    public void addBuildInternal(
            @NonNull BuildWrapper buildWrapper) {
        creationService.createBuildInternal(buildWrapper);
    }

    @Override
    public void attachReleaseBundle(
            @NonNull BundleInputBuild build,
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull String version) {
        releaseBundleService.attachReleaseBundle(build, repoKey, name, version);
    }

    @Override
    public void detachReleaseBundle(
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull String version) {
        releaseBundleService.detachReleaseBundle(repoKey, name, version);
    }

    @Override
    public boolean canReadBuild(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo) {
        return readService.canReadBuild(buildName, buildNumber, buildRepo);
    }

    @Override
    public boolean canUploadBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) {
        return readService.canUploadBuild(buildName, buildNumber, buildRepo);
    }

    @NonNull
    @Override
    public BuildPermissionsModel getBuildEffectivePermissions(
            @NonNull String repoKey,
            @NonNull String path) {
        return permissionService.getBuildEffectivePermissionsByRepoPath(repoKey, path);
    }

    @Override
    public void deleteBuild(
            BuildRun build,
            MutableStatusHolder status,
            boolean deleteArtifacts) {
        deletionService.deleteBuild(build, status, deleteArtifacts);
    }

    @Override
    public void deleteBuild(
            @NonNull BuildRun buildRun,
            @NonNull String buildRepo,
            @NonNull MutableStatusHolder status) {
        deletionService.deleteBuild(buildRun, buildRepo, status);
    }

    @Override
    public void deleteBuildInternal(
            @NonNull Build build,
            @NonNull String buildRepo,
            @NonNull MutableStatusHolder status) {
        deletionService.deleteBuildInternal(build, buildRepo, status);
    }

    @Override
    public Set<BuildRun> findBuildsForChecksum(
            BuildSearchCriteria criteria,
            BuildChecksumType type,
            String checksum,
            @NonNull String buildRepo) {
        return databaseService.findBuildsForChecksum(criteria, type, checksum, buildRepo);
    }

    @Override
    public Set<String> getAllBuildInfoRepos() {
        return repoService.getAllBuildInfoRepos();
    }

    @Override
    @Nullable
    public BuildWrapper getBuild(
            BuildRun buildRun) {
        return readService.getBuild(buildRun);
    }

    @Override
    public Map<BuildFileInfo, String> getBuildArtifacts(
            BuildArtifactsRequest buildArtifactsRequest) {
        return artifactService.getBuildArtifacts(buildArtifactsRequest);
    }

    @Override
    public File getBuildArtifactsArchive(
            BuildArtifactsRequest buildArtifactsRequest) throws IOException {
        return artifactService.getBuildArtifactsArchive(buildArtifactsRequest);
    }

    @Override
    public Set<ArtifactoryBuildArtifact> getBuildArtifactsFileInfos(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos,
            boolean useFallback) {
        return artifactService.getBuildArtifactsFileInfos(buildWrapper, sourceRepos, excludedRepos, useFallback);
    }

    @Override
    public String getBuildAsJson(
            BuildRun buildRun) {
        return readService.getBuildAsJson(buildRun);
    }

    @Override
    public Map<Dependency, BuildFileInfo> getBuildDependenciesFileInfos(
            @NonNull BuildWrapper buildWrapper) {
        return dependencyService.getBuildDependenciesFileInfos(buildWrapper);
    }

    @Override
    public @NonNull String getBuildInfoRepoForDefaultProject() {
        return repoService.getBuildInfoRepoForDefaultProject();
    }

    @Override
    public String getBuildInfoRepoKeyByProject(
            String projectKey,
            boolean checkProjectExists) {
        return repoService.getBuildInfoRepoKeyByProject(projectKey, checkProjectExists);
    }

    @Override
    @Nullable
    public BuildWrapper getBuildModelFromFile(
            @NonNull BuildRepoPath buildJsonPath) {
        return readService.getBuildModelFromFile(buildJsonPath);
    }

    @Override
    public List<String> getBuildNames(
            String repoKey) {
        return readService.getBuildNames(repoKey);
    }

    @Override
    public BuildRun getBuildRun(
            String buildName,
            String buildNumber,
            String buildStarted,
            @NonNull String buildRepo) {
        return readService.getBuildRun(buildName, buildNumber, buildStarted, buildRepo);
    }

    @Override
    public int getBuildsCountByRepoKey(
            String repoKey) {
        return databaseService.getBuildsCountFromSpecificRepo(repoKey);
    }

    @Override
    public List<BuildNameAndRepo> getBuildsInfoInternally(
            String buildRepo) {
        return readService.getBuildsInfoInternally(buildRepo);
    }

    @Override
    @Nullable
    public BuildWrapper getLatestBuildByNameAndNumber(
            String buildName,
            String buildNumber,
            String buildRepo) {
        return readService.getLatestBuildByNameAndNumber(buildName, buildNumber, buildRepo);
    }

    @NonNull
    @Override
    public String getPreferredBuildRepo(
            @Nullable String repoKey,
            @Nullable String projectKey) {
        return repoService.getPreferredBuildRepo(repoKey, projectKey);
    }

    @Override
    public int getTotalBuildsCount() {
        return databaseService.getTotalBuildsCount();
    }

    @Override
    public int getUniqueBuildsCountByRepoKey(
            String repoKey) {
        return databaseService.getUniqueBuildsCountFromSpecificRepo(repoKey);
    }

    @Override
    public boolean hasBasicReadPermissionForAllBuilds() {
        return permissionService.hasBasicReadPermissionForAllBuilds();
    }

    @Override
    public void importBuilds(
            @NonNull BuildImportSettings settings) {
        importService.importFrom(settings);
    }

    @Override
    public boolean isBuildBasicRead(
            String buildName,
            @NonNull String repoKey) {
        return permissionService.isBuildBasicRead(buildName, repoKey);
    }

    @Override
    public boolean isBuildBasicRead(
            String buildName,
            String buildNumber,
            String buildStarted,
            String buildRepo) {
        return permissionService.isBuildBasicRead(buildName, buildNumber, buildStarted, buildRepo);
    }

    @Override
    public boolean isBuildExistsInDb(
            BuildWrapper build) {
        return readService.isBuildExistsInDb(build);
    }

    @Override
    public boolean isBuildInfoRepo(
            String repoKey) {
        return repoService.isBuildInfoRepo(repoKey);
    }

    @Override
    public @NonNull String normaliseBuildRepoName(
            @Nullable String repoKey) {
        return repoService.normaliseBuildRepoName(repoKey);
    }

    @Override
    public void preAddBuild(
            @NonNull Build build,
            @NonNull String buildRepo) {
        creationService.preCreateBuild(build, buildRepo);
    }

    @Override
    public Stream<UsedBy> retrieveDependantBuilds(
            String sha1,
            String md5) {
        return readService.retrieveDependantBuilds(sha1, md5);
    }

    @Override
    public Stream<ProducedBy> retrieveProducedBuilds(
            String sha1,
            String md5) {
        return readService.retrieveProducedBuilds(sha1, md5);
    }

    @Override
    public SortedSet<BuildRun> searchBuildsByName(
            String buildName,
            @NonNull String buildRepo) {
        return readService.searchBuildsByName(buildName, buildRepo);
    }

    @Override
    public Set<BuildRun> searchBuildsByNameAndNumber(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo) {
        return readService.searchBuildsByNameAndNumber(buildName, buildNumber, buildRepo);
    }

    @Override
    public Set<BuildRun> searchBuildsByNameAndNumberInternal(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo) {
        return readService.searchBuildsByNameAndNumberInternal(buildName, buildNumber, buildRepo);
    }

    @Override
    public Set<BuildRun> searchBuildsByNameInternally(
            @Nullable String buildName) {
        return readService.searchBuildsByNameInternally(buildName);
    }

    @Override
    public void updateBuild(
            @NonNull BuildWrapper buildWrapper) {
        creationService.updateBuild(buildWrapper);
    }
}
