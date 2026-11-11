package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.ContinueBuildDto;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
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
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@RequiredArgsConstructor
public class BuildGetAllUiCommand {

    private final BuildRepoService repoService;
    private final BuildSearchService searchService;
    private final BuildSysConfigService sysConfigService;
    private final BuildPermissionService permissionService;

    @NonNull
    public ContinueResult<GeneralBuildInfo> getAllBuilds(@NonNull Map<String, String[]> params) {
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(params,
                projectKey -> repoService.getBuildInfoRepoKeyByProject(projectKey, true));
        ContinueBuildFilter filter = toBuildFilter(continueBuildDto);
        logBuildFilter(filter);
        String nextContinueState = null;
        List<BuildId> buildIds = searchService.getLatestBuildIDsPaging(filter);
        List<GeneralBuildInfo> generalBuildInfoList = buildIds.stream().map(this::getBuildGeneralInfo).toList();
        if (isNotEmpty(generalBuildInfoList) && generalBuildInfoList.size() == filter.getLimit()) {
            GeneralBuildInfo last = generalBuildInfoList.getLast();
            nextContinueState = buildIdToBase64(last);
        }
        return new ContinueResult<>(nextContinueState, generalBuildInfoList);
    }

    @NonNull
    private GeneralBuildInfo getBuildGeneralInfo(@NonNull BuildId build) {
        long buildStartedMillis = build.getStartedDate().getTime();
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(sysConfigService.getDateTimeFormat());
        String lastBuildTime = dateTimeFormatter.print(buildStartedMillis);
        return GeneralBuildInfo.builder()
                .buildName(build.getName())
                .lastBuildTime(lastBuildTime)
                .buildNumber(build.getNumber())
                .time(buildStartedMillis)
                .canDelete(canDeleteBuild(build))
                .buildRepo(build.getBuildRepo())
                .build();
    }

    private boolean canDeleteBuild(@NonNull BuildId build) {
        return sysConfigService.isBuildUiSkipDeletePermissionCheck() || permissionService.canDeleteBuild(
                build.getName(),
                build.getNumber(),
                build.getStarted(),
                build.getBuildRepo()
        );
    }

    private void logBuildFilter(@NonNull ContinueBuildFilter filter) {
        String orderDirection = filter.getDirection().name();
        log.debug("Fetching builds order by {} {}", filter.getOrderByStr(), orderDirection);
        if (isNotBlank(filter.getSearchStr())) {
            log.debug("Fetching builds only matches {}", filter.getSearchStr());
        }
        if (filter.getLimit() != null) {
            log.debug("Fetching {} builds", filter.getLimit());
        }
        if (filter.getContinueBuildId() != null) {
            log.debug("Fetching builds after {}", filter.getContinueBuildId().getName());
        }
    }
}
