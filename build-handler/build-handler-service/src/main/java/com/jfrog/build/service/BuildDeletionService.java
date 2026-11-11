package com.jfrog.build.service;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.ArtifactoryBuildArtifact;
import com.jfrog.build.acl.model.BuildCoordinate;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.api.BuildRetentionWorkItem;
import com.jfrog.build.model.BuildDeleteNumberResult;
import com.jfrog.build.tx.BuildTxRunner;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.async.Async;
import org.artifactory.async.WorkQueueConfig;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.Build;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;

import java.util.List;
import java.util.Set;

import static com.jfrog.build.acl.model.BuildAccessLogType.BUILD_DELETE;
import static com.jfrog.build.acl.model.BuildXrayEvent.DELETE_BUILD;
import static com.jfrog.build.acl.util.BuildConstants.BUILD_RETENTION_JOB_WQ_NAME;
import static com.jfrog.build.acl.util.BuildConstants.MARKER_DELETE_BUILD_ARTIFACT;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static com.jfrog.build.model.BuildDeleteNumberResult.Status.FAILURE_ARTIFACTS;
import static com.jfrog.build.model.BuildDeleteNumberResult.ZERO_ARTIFACTS;
import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.artifactory.event.EventHintType.DeleteBuildArtifact;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildDeletionService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildTxRunner txRunner;
    private final BuildAqlService aqlService;
    private final BuildArtifactService artifactService;
    private final BuildDatabaseService databaseService;
    private final BuildPermissionService permissionService;
    private final BuildReadService readService;
    private final BuildSysConfigService sysConfigService;

    public void deleteAllBuildsByName(
            List<BuildCoordinate> buildsCoordinates,
            MutableStatusHolder status,
            boolean deleteArtifacts) {
        for (BuildCoordinate buildCoordinates : buildsCoordinates) {
            String buildName = buildCoordinates.getBuildName();
            String buildRepo = buildCoordinates.getBuildRepo();
            permissionService.assertDeletePermissions(buildName, buildRepo);
            deleteAllBuildsByName(buildName, buildRepo, status, deleteArtifacts);
        }
    }

    public void deleteAllBuildsByName(
            String buildName,
            String buildRepo,
            MutableStatusHolder status,
            boolean deleteArtifacts) {
        readService.searchBuildsByNameInternally(buildName, buildRepo)
                .forEach(build -> deleteBuild(build, status, deleteArtifacts));
    }

    public void deleteBuild(
            BuildRun buildRun,
            MutableStatusHolder status,
            boolean deleteArtifacts) {
        deleteBuild(buildRun, status, deleteArtifacts, false);
    }

    /**
     * {@code idempotentDelete} must only be set by the build retention flow
     * ({@link BuildRetentionService}). Retention can run synchronously inside a concurrent
     * {@code PUT /api/build} (when the uploaded build-info carries a {@code buildRetention} block),
     * so a competing republish (overwrite) of the same build coordinates may have already removed
     * the build-info JSON; in that case the missing JSON must be treated as a no-op success rather
     * than failing the publish with a not-found error. All other callers keep the strict
     * not-found behavior and must not pass {@code true}.
     */
    public void deleteBuild(
            BuildRun buildRun,
            MutableStatusHolder status,
            boolean deleteArtifacts,
            boolean idempotentDelete) {
        if (buildRun == null) {
            status.error("Couldn't find requested build", null, log);
            return;
        }
        String name = buildRun.getName();
        String number = buildRun.getNumber();
        String started = buildRun.getStarted();
        permissionService.assertDeletePermissions(name, number, started, buildRun.getBuildRepo());
        performDeleteBuild(buildRun, status, deleteArtifacts, idempotentDelete);
        log.debug("Deleting build {} : {} : {} from {} repo", name, number, started, buildRun.getBuildRepo());
    }

    public BuildDeleteNumberResult deleteBuild(
            @NonNull BuildRun buildRun,
            @NonNull MutableStatusHolder status,
            boolean dryRun,
            boolean deleteArtifacts,
            boolean skipTrashcan) {
        BuildRepoPath targetBuildJsonPath = generateBuildJsonRepoPath(buildRun);
        if (!bridgeToArtifactory.repoPathExists(targetBuildJsonPath.toString())) {
            throw notFoundException("Build cannot be found in path %s", targetBuildJsonPath);
        }
        BuildDeleteNumberResult result;
        if (deleteArtifacts) {
            result = deleteArtifacts(buildRun, status, dryRun, skipTrashcan);
            if (result.getStatus() == FAILURE_ARTIFACTS) {
                return result;
            }
        } else {
            result = ZERO_ARTIFACTS;
        }
        undeploy(targetBuildJsonPath, status, false, skipTrashcan, dryRun, false);
        StatusEntry lastError = status.getLastError();
        if (nonNull(lastError)) {
            log.error("Failed to delete build {}", targetBuildJsonPath, lastError.getException());
            return result.toBuildInfoFailure();
        }
        return result;
    }

    @NonNull
    public BuildDeleteNumberResult deleteBuild(
            @NonNull String buildRepo,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String started,
            boolean dryRun,
            boolean deleteArtifacts,
            boolean skipTrashcan) {
        BuildRun buildRun = readService.getBuildRun(buildName, buildNumber, started, buildRepo);
        if (isNull(buildRun)) {
            BuildRepoPath targetBuildJsonPath = generateBuildJsonRepoPath(buildName, buildNumber, started, buildRepo);
            throw notFoundException("Build not found: %s", targetBuildJsonPath);
        }
        MutableStatusHolder status = bridgeToArtifactory.newStatusHolder();
        return deleteBuild(buildRun, status, dryRun, deleteArtifacts, skipTrashcan);
    }

    private void performDeleteBuild(
            BuildRun buildRun,
            MutableStatusHolder status,
            boolean deleteArtifacts,
            boolean idempotentDelete) {
        if (deleteArtifacts) {
            removeBuildArtifacts(buildRun, status);
        }
        String name = buildRun.getName();
        String number = buildRun.getNumber();
        String started = buildRun.getStarted();
        BuildRepoPath targetBuildJsonPath = generateBuildJsonRepoPath(name, number, started, buildRun.getBuildRepo());
        if (bridgeToArtifactory.repoPathExists(targetBuildJsonPath.toString())) {
            // when invoked from the retention flow, idempotentDelete is true so a concurrent
            // overwrite that already removed the JSON is tolerated instead of failing with a not-found error.
            bridgeToArtifactory.undeploy(targetBuildJsonPath.toString(), status, false, true, false, false,
                    idempotentDelete);
        } else {
            String message = format("Build %s:%s was not found in path: %s.", name, number, targetBuildJsonPath);
            throw notFoundException(message);
        }
    }

    public void deleteBuildInternal(
            @NonNull Build build,
            @NonNull String buildRepo,
            @NonNull MutableStatusHolder status) {
        String buildName = build.getName();
        String buildNumber = build.getNumber();
        String buildStarted = build.getStarted();
        String buildData = createBuildDataString(buildName, buildNumber, buildRepo);
        status.debug("Starting to remove build '" + buildData, log);
        databaseService.deleteBuild(buildName, buildNumber, buildStarted, buildRepo);
        reportBuildDeletion(buildName, buildNumber, parseBuildTime(buildStarted), buildRepo, status);
    }

    public void deleteBuild(
            @NonNull BuildRun buildRun,
            @NonNull String buildRepo,
            @NonNull MutableStatusHolder status) {
        String buildName = buildRun.getName();
        String buildNumber = buildRun.getNumber();
        String buildData = createBuildDataString(buildName, buildNumber, buildRepo);
        status.debug("Starting to remove build '" + buildData, log);
        databaseService.deleteBuildFromBuildRun(buildRun);
        reportBuildDeletion(buildName, buildNumber, parseBuildTime(buildRun.getStarted()), buildRepo, status);
    }

    private void reportBuildDeletion(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Long buildStarted,
            @NonNull String buildRepo,
            @NonNull MutableStatusHolder status) {
        logBuildDeleteAccessEvent(buildName);
        String buildData = createBuildDataString(buildName, buildNumber, buildRepo);
        status.debug("Finished removing build '" + buildData, log);
        bridgeToArtifactory.runXrayInterceptor(DELETE_BUILD, buildName, buildNumber, buildStarted, buildRepo);
    }

    @NonNull
    private String createBuildDataString(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) {
        return buildName + "' #" + buildNumber + " from " + buildRepo + " repo";
    }

    @Async(workQueue = @WorkQueueConfig(name = BUILD_RETENTION_JOB_WQ_NAME))
    public void deleteBuildAsync(BuildRetentionWorkItem buildRetentionWorkItem) {
        BuildRun buildRun = buildRetentionWorkItem.getBuildId();
        log.info("Async delete of build {} number: {}", buildRun.getName(), buildRun.getNumber());
        boolean deleteArtifacts = buildRetentionWorkItem.isDeleteArtifacts();
        try {
            bridgeToArtifactory.putEventLogHint(DeleteBuildArtifact, MARKER_DELETE_BUILD_ARTIFACT);
            // async retention delete is idempotent on the build-info JSON (see deleteBuild).
            txRunner.runTx(() -> deleteBuild(buildRun, newStatusHolder(), deleteArtifacts, true));
        } catch (Exception e) {
            log.error("Failed to delete build async: {}, exception", e.getMessage(), e);
        } finally {
            bridgeToArtifactory.removeEventLogHint(DeleteBuildArtifact);
        }
    }

    public void removeBuildArtifacts(
            BuildRun buildRun,
            MutableStatusHolder status) {
        permissionService.assertDeletePermissions(
                buildRun.getName(), buildRun.getNumber(), buildRun.getStarted(), buildRun.getBuildRepo());
        BuildWrapper build = readService.getBuildInternally(buildRun);
        String buildName = build.getName();
        String buildNumber = build.getNumber();
        String buildRepo = build.getBuildRepo();
        String buildData = buildName + "' #" + buildNumber + " from " + buildRepo + " repo";
        status.debug("Starting to remove the artifacts of build '" + buildData, log);
        Set<ArtifactoryBuildArtifact> buildArtifactsInfos = sysConfigService.isBuildForcedDeleteArtifacts() ?
                aqlService.getAllBuildArtifactsByAql(build) :
                artifactService.getBuildArtifactsFileInfos(build, null, null, false);
        for (ArtifactoryBuildArtifact artifact : buildArtifactsInfos) {
            if (artifact.getFileInfo() != null) {
                BuildFileInfo fileInfo = artifact.getFileInfo();
                BuildRepoPath repoPath = new BuildRepoPath(fileInfo.getRepoKey(), fileInfo.getRelativePath());
                bridgeToArtifactory.undeploy(repoPath.toString(), status, true, true, false, false, false);
            }
        }
        status.debug("Finished removing the artifacts of build '" + buildData, log);
    }

    @VisibleForTesting
    public BuildDeleteNumberResult deleteArtifacts(
            @NonNull BuildRun buildRun,
            @NonNull MutableStatusHolder status,
            boolean dryRun,
            boolean skipTrashcan) {
        BuildWrapper build;
        try {
            build = readService.getBuildInternally(buildRun);
        } catch (NotFoundException | BinaryNotFoundException e) {
            log.warn("Build info file for build {} {} {} is missing from the filestore. " +
                            "The build artifacts will not be deleted. {}",
                    buildRun.getName(), buildRun.getNumber(), parseBuildTime(buildRun.getStarted()), e.getMessage());
            log.debug("Build info file missing from file store", e);
            return BuildDeleteNumberResult.successArtifacts(0, 0, 0);

        }
        Set<ArtifactoryBuildArtifact> buildArtifacts = artifactService.getBuildArtifactsFileInfos(build);
        int total = buildArtifacts.size();
        String buildPath = generateBuildJsonRepoPath(buildRun).toString();
        log.info("Starting to delete the {} artifacts of build {}", total, buildPath);
        int deletedCount = 0;
        long deletedBytes = 0;
        for (ArtifactoryBuildArtifact artifact : buildArtifacts) {
            BuildFileInfo fileInfo = artifact.getFileInfo();
            BuildRepoPath repoPath = fileInfo.getRepoPath();
            undeploy(repoPath, status, true, skipTrashcan, dryRun, false);
            if (nonNull(status.getLastError())) {
                ExceptionLogUtils.debugOrError(log, "Failed to delete artifact " + repoPath,
                        status.getLastError().getException());
            } else {
                deletedCount++;
                deletedBytes += fileInfo.getSize();
            }
        }
        log.info("Finished successfully to delete {}/{} artifacts of build {}", deletedCount, total, buildPath);
        if (nonNull(status.getLastError())) {
            log.warn("Failed to delete {} artifacts of build {}", total - deletedCount, buildPath);
            return BuildDeleteNumberResult.failedArtifacts(total, deletedCount, deletedBytes);
        }
        return BuildDeleteNumberResult.successArtifacts(total, deletedCount, deletedBytes);
    }

    private void undeploy(
            @NonNull BuildRepoPath buildRepoPath,
            @NonNull MutableStatusHolder status,
            boolean triggerMetadataCalculation,
            boolean skipTrashcan,
            boolean dryRun,
            boolean idempotentDelete) {
        if (dryRun) {
            log.info("Dry run: Would have deleted {}", buildRepoPath);
            return;
        }
        log.info("Deleting {}; skipTrashcan={}", buildRepoPath, skipTrashcan);
        String repoPath = new BuildRepoPath(buildRepoPath.getRepoKey(), buildRepoPath.getRelativePath()).toString();
        bridgeToArtifactory.undeploy(repoPath, status, triggerMetadataCalculation, true, skipTrashcan, false,
                idempotentDelete);
    }

    public void logBuildDeleteAccessEvent(@NonNull String buildName) {
        bridgeToArtifactory.logAccessEvent(BUILD_DELETE, buildName);
    }

    @NonNull
    public MutableStatusHolder newStatusHolder() {
        return bridgeToArtifactory.newStatusHolder();
    }
}
