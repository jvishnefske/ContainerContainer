package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.api.BuildsByName;
import com.jfrog.build.model.api.BuildsByName.Build;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildUrlService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Set;
import java.util.SortedSet;

import static com.jfrog.build.service.BuildUrlService.getBuildNumberRelativeHref;
import static com.jfrog.build.service.BuildUrlService.getBuildRelativeHref;
import static com.jfrog.build.service.BuildUrlService.getBuildRepoRelativeHref;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@RequiredArgsConstructor
public class BuildGetByNameCommand {

    private final BuildUrlService urlService;
    private final BuildRepoService repoService;
    private final BuildReadService readService;

    @NonNull
    public BuildsByName getBuildsByName(
            @NonNull HttpServletRequest request,
            @NonNull String buildName,
            @Nullable String buildRepoKey,
            @Nullable String projectKey) {
        BuildsByName builds = new BuildsByName();
        buildRepoKey = repoService.getPreferredBuildRepo(buildRepoKey, projectKey);
        SortedSet<BuildRun> buildsByName = readService.searchBuildsByName(buildName, buildRepoKey);
        if (buildsByName.isEmpty()) {
            throw notFoundException("No build was found for build name: %s", buildName);
        }
        String baseBuildUrl = urlService.getBaseBuildsHref(request);
        builds.slf = baseBuildUrl + getBuildRelativeHref(buildName) + getBuildRepoRelativeHref(buildRepoKey);
        populateBuilds(buildsByName, builds);
        return builds;
    }

    private void populateBuilds(
            @NonNull Set<BuildRun> buildRuns,
            @NonNull BuildsByName builds) {
        for (BuildRun buildRun : buildRuns) {
            String versionHref = getBuildNumberRelativeHref(buildRun.getNumber());
            Build build = new Build(versionHref, buildRun.getStarted());
            builds.buildsNumbers.add(build);
        }
    }
}
