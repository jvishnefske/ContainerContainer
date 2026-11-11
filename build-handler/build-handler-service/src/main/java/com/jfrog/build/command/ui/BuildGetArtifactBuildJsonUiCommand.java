package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;

/**
 * Returns the raw build-info JSON for a build referenced from an artifact (the "View Build JSON" action in the
 * artifact Builds tab). Returns {@code null} when the build cannot be located.
 */
@Slf4j
@RequiredArgsConstructor
public class BuildGetArtifactBuildJsonUiCommand {

    private final BuildReadService readService;
    private final BuildRepoService repoService;

    @Nullable
    public String getArtifactBuildJson(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String startTime,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        String buildStarted = formatBuildTime(startTime);
        BuildRun buildRun = readService.getBuildRun(buildName, buildNumber, buildStarted, normalisedRepo);
        if (buildRun == null) {
            return null;
        }
        return readService.getBuildAsJson(buildRun);
    }
}
