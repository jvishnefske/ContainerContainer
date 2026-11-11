package com.jfrog.build.command.cleanup;

import com.jfrog.build.model.BuildDeleteNumberResult;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildProjectService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.exception.CancelException;

import static com.jfrog.build.model.BuildDeleteNumberResult.Status.FAILURE_ARTIFACTS;
import static com.jfrog.build.model.BuildDeleteNumberResult.Status.FAILURE_BUILD_INFO;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;

@Slf4j
@RequiredArgsConstructor
public class BuildDeleteForCleanupCommand {

    private final BuildRepoService repoService;
    private final BuildProjectService projectService;
    private final BuildDeletionService deletionService;

    @NonNull
    public BuildDeleteNumberResult deleteBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String started,
            @Nullable String projectKey,
            @NonNull Boolean dryRun,
            @NonNull Boolean deleteArtifacts,
            @NonNull Boolean skipTrashcan) {
        started = verifyDeleteParams(started, projectKey);
        log.info("Received Delete build request with name={}, number={}, project={}, "
                        + "started={}, dryRun={}, deleteArtifacts={}, skipTrashcan={}",
                buildName, buildNumber, projectKey, started, dryRun, deleteArtifacts, skipTrashcan);
        String repoKey = repoService.getBuildInfoRepoKeyByProject(projectKey, false);
        try {
            BuildDeleteNumberResult result = deletionService.deleteBuild(
                    repoKey, buildName, buildNumber, started, dryRun, deleteArtifacts, skipTrashcan);
            processDeletionErrors(result);
            return result;
        } catch (CancelException e) {
            log.warn("Failed to delete build {}:{}:{}", buildName, buildNumber, started, e);
            throw e;
        } catch (Exception e) {
            log.info("Failed to delete build {}:{}:{}: {}", buildName, buildNumber, started, e.getMessage());
            log.debug("Failed to delete build", e);
            throw e;
        }
    }

    @NonNull
    private String verifyDeleteParams(
            @Nullable String started,
            @Nullable String projectKey) {
        if (isBlank(projectKey)) {
            throw badRequestException("Project is required");
        }
        if (isBlank(started)) {
            throw badRequestException("Started is required");
        }
        projectService.verifyProjectAdmin(projectKey);
        return getStartedFormatted(started);
    }

    @NonNull
    private String getStartedFormatted(@NonNull String started) {
        try {
            return formatBuildTime(started);
        } catch (Exception e) {
            throw badRequestException("Invalid started time: %s", e, started);
        }
    }

    private void processDeletionErrors(@NonNull BuildDeleteNumberResult result) {
        int deletedCount = result.getDeletedArtifactsCount();
        if (result.getStatus() == FAILURE_BUILD_INFO && deletedCount == 0) {
            throw internalServerException("Failed to delete the build");
        }
        if (result.getStatus() == FAILURE_ARTIFACTS && deletedCount == 0) {
            throw internalServerException(
                    "Failed to delete any of the remaining %d build artifacts", result.getTotalArtifactsCount()
            );
        }
    }
}
