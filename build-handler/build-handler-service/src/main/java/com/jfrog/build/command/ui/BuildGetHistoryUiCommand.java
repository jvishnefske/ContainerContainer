package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.ContinueBuildDto;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.commons.continues.ContinueResult;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.List;
import java.util.Map;

import static com.jfrog.build.util.BuildContinueTranslator.buildIdToBase64;
import static com.jfrog.build.util.BuildContinueTranslator.toBuildFilter;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Slf4j
@RequiredArgsConstructor
public class BuildGetHistoryUiCommand {

    private final BuildRepoService repoService;
    private final BuildSearchService searchService;
    private final BuildSysConfigService sysConfigService;

    @NonNull
    public ContinueResult<GeneralBuildInfo> getBuildHistory(
            @NonNull String buildName,
            @Nullable String buildRepo,
            @NonNull Map<String, String[]> params) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(params,
                projectKey -> repoService.getBuildInfoRepoKeyByProject(projectKey, true));
        ContinueBuildFilter filter = toBuildFilter(continueBuildDto);

        List<GeneralBuild> builds = searchService.getBuildForName(buildName, normalisedRepo, filter);
        List<GeneralBuildInfo> generalBuildInfoList = builds.stream().map(this::toGeneralBuildInfo).toList();

        String nextContinueState = null;
        if (isNotEmpty(generalBuildInfoList) && generalBuildInfoList.size() == filter.getLimit()) {
            GeneralBuildInfo last = generalBuildInfoList.getLast();
            nextContinueState = buildIdToBase64(last);
        }
        return new ContinueResult<>(nextContinueState, generalBuildInfoList);
    }

    @NonNull
    private GeneralBuildInfo toGeneralBuildInfo(@NonNull GeneralBuild build) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(sysConfigService.getDateTimeFormat());
        return GeneralBuildInfo.builder()
                .buildNumber(build.getBuildNumber())
                .lastBuildTime(dateTimeFormatter.print(build.getBuildDate()))
                .releaseStatus(build.getStatus())
                .ciUrl(build.getCiUrl())
                .time(build.getBuildDate())
                .buildStat("Modules-" + build.getNumOfModules() +
                        ", Artifacts-" + build.getNumOfArtifacts() +
                        ", Dependencies-" + build.getNumOfDependencies()
                ).build();
    }
}
