package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.api.Builds;
import com.jfrog.build.model.api.Builds.Build;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildUrlService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.jfrog.build.service.BuildUrlService.getBuildRelativeHref;
import static com.jfrog.build.service.BuildUrlService.getBuildRepoRelativeHref;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetAllCommand {

    private final BuildUrlService urlService;
    private final BuildRepoService repoService;
    private final BuildDatabaseService databaseService;
    private final BuildPermissionService permissionService;

    @NonNull
    public Builds getAllBuilds(
            @NonNull HttpServletRequest request,
            @Nullable String buildRepoKey,
            @Nullable String projectKey) {
        Builds builds = new Builds();
        buildRepoKey = repoService.getPreferredBuildRepo(buildRepoKey, projectKey);
        builds.slf = urlService.getBaseBuildsHref(request) + getBuildRepoRelativeHref(buildRepoKey);
        populateBuilds(builds, buildRepoKey);
        if (builds.builds.isEmpty()) {
            throw notFoundException("No builds were found");
        }
        return builds;
    }

    private void populateBuilds(
            @NonNull Builds builds,
            @NonNull String buildRepoKey) {
        databaseService.getLatestBuildsByName(buildRepoKey).stream()
                .filter(permissionService::isBuildBasicRead)
                .forEach(buildRun -> createBuild(buildRun, builds));
    }

    private static void createBuild(BuildRun buildRun, Builds builds) {
        String buildHref = getBuildRelativeHref(buildRun.getName());
        Build build = new Build(buildHref, buildRun.getStarted());
        builds.builds.add(build);
    }
}
