package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.api.PromotionResult;
import com.jfrog.build.service.BuildPromotionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jfrog.build.api.release.Promotion;

import java.util.Set;

import static com.jfrog.build.util.BuildServiceUtils.shouldDecodeParams;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@RequiredArgsConstructor
public class BuildPromoteCommand {

    private final BuildRepoService repoService;
    private final BuildReadService readService;
    private final BuildPromotionService promotionService;

    @NonNull
    public PromotionResult promote(
            @NonNull HttpServletRequest request,
            @NonNull Promotion promotion,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String buildRepo,
            @Nullable String projectKey) {
        validateInput(buildName, buildNumber);
        if (shouldDecodeParams(request)) {
            buildName = decode(buildName, UTF_8);
            buildNumber = decode(buildNumber, UTF_8);
        }
        buildRepo = repoService.getPreferredBuildRepo(buildRepo, projectKey);
        BuildRun buildRun = getLatestBuildRun(buildName, buildNumber, buildRepo);
        return promotionService.promoteBuild(buildRun, promotion);
    }

    @NonNull
    private BuildRun getLatestBuildRun(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) {
        BuildRun latestBuildRun = null;
        Set<BuildRun> buildRuns = readService.searchBuildsByNameAndNumberInternal(buildName, buildNumber, buildRepo);
        for (BuildRun buildRun : buildRuns) {
            if (isNull(latestBuildRun) || latestBuildRun.getStartedDate().before(buildRun.getStartedDate())) {
                latestBuildRun = buildRun;
            }
        }
        if (isNull(latestBuildRun)) {
            throw notFoundException("Cannot find a build by the name: %s, number: %s, repo: %s",
                    buildName, buildNumber, buildRepo);
        }
        return latestBuildRun;
    }

    private void validateInput(
            @NonNull String buildName,
            @NonNull String buildNumber) {
        if (isBlank(buildName)) {
            throw badRequestException("Build name cannot be blank");
        }
        if (isBlank(buildNumber)) {
            throw badRequestException("Build number cannot be blank");
        }
    }
}
