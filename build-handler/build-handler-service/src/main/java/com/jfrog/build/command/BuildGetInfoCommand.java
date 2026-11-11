package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.BuildInfo;
import com.jfrog.build.model.api.diff.BuildsDiff;
import com.jfrog.build.service.BuildDiffService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildUrlService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jfrog.build.api.Build;

import java.io.Serializable;
import java.util.List;

import static com.jfrog.build.model.BuildRunComparators.getBuildStartDateComparator;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@RequiredArgsConstructor
public class BuildGetInfoCommand {

    private final BuildUrlService urlService;
    private final BuildRepoService repoService;
    private final BuildReadService readService;
    private final BuildDiffService diffService;

    @NonNull
    public Serializable getBuildInfo(
            @NonNull HttpServletRequest request,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String buildStarted,
            @Nullable String buildRepo,
            @Nullable String projectKey,
            @Nullable String diffNumber,
            boolean slim) {
        buildRepo = repoService.getPreferredBuildRepo(buildRepo, projectKey);
        BuildWrapper build = readService.getBuild(buildName, buildNumber, buildStarted, buildRepo);
        if (isNull(build) || !build.isBuildExist()) {
            throw notFoundException("No build was found for build name: %s, build number: %s %s",
                    buildName, buildNumber, isNotBlank(buildStarted) ? ", build started: " + buildStarted : "");
        }
        if (isNotBlank(diffNumber)) {
            return handleDiffRequest(build, diffNumber);
        }
        return prepareGetBuildResponse(request, build, slim);
    }

    @NonNull
    private BuildsDiff handleDiffRequest(
            @NonNull BuildWrapper buildWrapperA,
            @NonNull String diffNumber) {
        if ("null".equals(diffNumber)) {
            throw badRequestException("Parameter 'diff' must contain a value, if specified");
        }
        String buildName = buildWrapperA.getName();
        String buildRepo = buildWrapperA.getBuildRepo();
        BuildWrapper buildWrapperB = readService.getLatestBuildByNameAndNumber(buildName, diffNumber, buildRepo);
        if (isNull(buildWrapperB) || !buildWrapperB.isBuildExist()) {
            throw notFoundException("No build was found for build name: %s , build number: %s ", buildName, diffNumber);
        }
        Build buildA = buildWrapperA.getBuild();
        Build buildB = buildWrapperB.getBuild();
        BuildRun buildRunA = readService.getBuildRunInternally(buildName, buildA.getNumber(), buildA.getStarted());
        BuildRun buildRunB = readService.getBuildRunInternally(buildName, buildB.getNumber(), buildB.getStarted());
        if (getBuildStartDateComparator().compare(buildRunA, buildRunB) < 0) {
            throw badRequestException("Build number should be greater than the build number to compare against");
        }
        return diffService.getBuildsDiff(buildA, buildB);
    }

    @NonNull
    private BuildInfo prepareGetBuildResponse(
            @NonNull HttpServletRequest request,
            @NonNull BuildWrapper buildWrapper,
            boolean slim) {
        Build build = buildWrapper.getBuild();
        if (slim) {
            build.setModules(List.of());
            build.setProperties(null);
        }
        BuildInfo buildInfo = new BuildInfo();
        String buildRepo = buildWrapper.getBuildRepo();
        buildInfo.slf = urlService.getBuildInfoHref(request, build.getName(), build.getNumber(), buildRepo);
        buildInfo.buildInfo = build;
        return buildInfo;
    }
}
