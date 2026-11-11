package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.api.BuildGeneralInfo;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetGeneralInfoUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;

    @NonNull
    public GeneralBuildInfo getBuildGeneralInfo(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String date,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        String buildStarted = isNotBlank(date) ? formatBuildTime(Long.parseLong(date)) : null;

        BuildGeneralInfo generalInfo =
                moduleService.getBuildGeneralInfo(buildName, buildNumber, buildStarted, normalisedRepo);
        if (generalInfo == null) {
            throw notFoundException("Failed to locate build %s:%s-%s", buildName, buildNumber,
                    defaultString(buildStarted));
        }
        return toGeneralBuildInfo(generalInfo);
    }

    @NonNull
    private GeneralBuildInfo toGeneralBuildInfo(@NonNull BuildGeneralInfo generalInfo) {
        return GeneralBuildInfo.builder()
                .buildName(generalInfo.getBuildName())
                .buildNumber(generalInfo.getBuildNumber())
                .ciUrl(generalInfo.getCiUrl())
                .releaseStatus(generalInfo.getReleaseStatus())
                .agent(generalInfo.getAgent())
                .buildAgent(generalInfo.getBuildAgent())
                .lastBuildTime(generalInfo.getLastBuildTime())
                .duration(generalInfo.getDuration())
                .principal(generalInfo.getPrincipal())
                .artifactoryPrincipal(generalInfo.getArtifactoryPrincipal())
                .url(generalInfo.getUrl())
                .buildRepo(generalInfo.getBuildRepo())
                .time(generalInfo.getTime())
                .buildStat(generalInfo.getBuildStat())
                .buildFullView(generalInfo.getIsBuildFullView())
                .canManage(generalInfo.getCanManage())
                .canDelete(generalInfo.getCanDelete())
                .sha256(generalInfo.getSha256())
                .vcs(generalInfo.getVcs())
                .build();
    }
}
