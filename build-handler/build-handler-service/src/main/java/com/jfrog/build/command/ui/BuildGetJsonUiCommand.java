package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.api.ViewArtifactModel;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetJsonUiCommand {

    private final BuildRepoService repoService;
    private final BuildReadService readService;

    @NonNull
    public ViewArtifactModel getBuildJson(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        String formattedDate = formatBuildTime(Long.parseLong(buildStarted));
        BuildRun buildRun = readService.getBuildRun(buildName, buildNumber, formattedDate, normalisedRepo);
        if (buildRun == null) {
            throw notFoundException("Failed to locate build %s:%s-%s", buildName, buildNumber,
                    defaultString(formattedDate));
        }
        String buildJson = readService.getBuildAsJson(buildRun);
        return ViewArtifactModel.builder()
                .fileContent(buildJson)
                .build();
    }
}
