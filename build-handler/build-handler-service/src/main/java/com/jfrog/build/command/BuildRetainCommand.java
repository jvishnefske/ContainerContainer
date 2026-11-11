package com.jfrog.build.command;

import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildRetentionService;
import com.jfrog.build.service.BuildSysConfigService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.jfrog.build.api.BuildRetention;

import static java.util.Objects.nonNull;
import static org.artifactory.exception.factory.ExceptionFactory.cancelException;

@Slf4j
@RequiredArgsConstructor
public class BuildRetainCommand {

    private final BuildRepoService repoService;
    private final BuildRetentionService retentionService;
    private final BuildSysConfigService sysConfigService;

    public void retainBuilds(
            @NonNull BuildRetention retention,
            @NonNull String buildName,
            @Nullable String buildRepo,
            @Nullable String projectKey,
            boolean async) {
        buildRepo = repoService.getPreferredBuildRepo(buildRepo, projectKey);
        if (async || sysConfigService.isBuildRetentionAlwaysAsync()) {
            retentionService.discardOldBuilds(buildName, buildRepo, retention, true);
            log.info("Retention policy for build '{}' scheduled to run", buildName);
            return;
        }
        log.info("Starting execution of Retention Policy for build '{}'", buildName);
        MutableStatusHolder status = retentionService.discardOldBuilds(buildName, buildRepo, retention, false);
        StatusEntry errorStatus = status.getMostImportantErrorStatusCode();
        checkForException("Errors have occurred while maintaining build retention.", errorStatus);
        StatusEntry warningStatus = status.getMostImportantWarningsStatusCode();
        checkForException("Warnings encountered while performing build retention.", warningStatus);
        log.info("Retention policy for build '{}' finished successfully", buildName);
    }

    private void checkForException(
            @NonNull String message,
            @Nullable StatusEntry status) {
        if (nonNull(status)) {
            message = message + " Please review the logs for further information.";
            throw cancelException(message, status.getStatusCode());
        }
    }
}
