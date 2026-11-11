package com.jfrog.build.command.ui;

import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetEffectivePermissionByUserUiCommand {

    private final BuildRepoService repoService;
    private final BuildPermissionService permissionService;

    @NonNull
    public List<String> getEffectivePermissionByUser(
            @Nullable String buildName,
            @Nullable String buildNumber,
            @Nullable String buildDate,
            @Nullable String buildRepo,
            @NonNull String username) {
        if (isBlank(buildName) || isBlank(buildNumber) || isBlank(buildDate)) {
            throw badRequestException(
                    "One or more of the following parameters is missing: buildName, buildNumber, buildDate");
        }
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        String buildStarted = formatBuildStarted(buildDate);
        permissionService.assertCanManageBuild(buildName, buildNumber, buildStarted, normalisedRepo);
        return permissionService.getBuildPermissionsByUser(buildName, buildNumber, buildStarted, normalisedRepo,
                username);
    }

    @NonNull
    private String formatBuildStarted(@NonNull String buildDate) {
        try {
            return formatBuildTime(buildDate);
        } catch (NumberFormatException e) {
            throw badRequestException("Invalid buildDate format");
        }
    }
}
