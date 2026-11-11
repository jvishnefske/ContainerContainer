package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetEffectivePermissionUiCommand {

    private final BuildRepoService repoService;
    private final BuildPermissionService permissionService;

    @NonNull
    public BuildPermissionsModel getEffectivePermission(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildDate,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        String buildStarted = formatBuildStarted(buildDate);
        permissionService.assertCanManageBuild(buildName, buildNumber, buildStarted, normalisedRepo);
        return permissionService.getBuildEffectivePermissions(buildName, buildNumber, buildStarted, normalisedRepo);
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
