package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRenameService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Set;

import static com.jfrog.build.util.BuildServiceUtils.shouldDecodeParams;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.forbiddenException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@RequiredArgsConstructor
public class BuildRenameCommand {

    private final BuildRepoService repoService;
    private final BuildReadService readService;
    private final BuildRenameService renameService;
    private final BuildPermissionService permissionService;

    public void renameBuilds(
            @NonNull HttpServletRequest request,
            @NonNull String buildName,
            @NonNull String buildNameNew,
            @Nullable String buildRepo,
            @Nullable String projectKey) {
        if (shouldDecodeParams(request)) {
            buildName = decode(buildName, UTF_8);
        }
        buildRepo = repoService.getPreferredBuildRepo(buildRepo, projectKey);
        validateInput(buildName, buildNameNew, buildRepo);
        Set<BuildRun> buildsToRename = readService.searchBuildsByName(buildName, buildRepo);
        if (buildsToRename.isEmpty()) {
            throw notFoundException("Unable to find Build name: '%s', repo: '%s' ", buildName, buildRepo);
        }
        buildsToRename.forEach(build -> validatePermissions(build, buildNameNew));
        renameService.renameBuildsAsync(buildName, buildNameNew, buildRepo);
    }

    private void validateInput(
            @NonNull String buildName,
            @NonNull String buildNameNew,
            @NonNull String buildRepo) {
        if (isBlank(buildName)) {
            throw badRequestException("Please state the name of the build to be renamed");
        }
        if (isBlank(buildNameNew)) {
            throw badRequestException("Please state a new name for the selected builds");
        }
        if (buildName.equals(buildNameNew)) {
            throw badRequestException("Same name specified as 'from' and 'to'. Can't rename a build to the same name");
        }
        if (!permissionService.canDeleteBuildName(buildName, buildRepo)) {
            throw forbiddenException("User is not authorized to delete build info. Build delete permission is needed");
        }
    }

    private void validatePermissions(
            @NonNull BuildRun build,
            @NonNull String buildNameNew) {
        String name = build.getName();
        String number = build.getNumber();
        String started = build.getStarted();
        String repo = build.getBuildRepo();
        permissionService.assertDeletePermissions(name, number, started, repo);
        permissionService.assertUploadPermissions(buildNameNew, number, started, repo);
        readService.assertMutable(name, number, started);
    }
}
