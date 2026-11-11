package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.ContinueBuildDto;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.util.Direction;

import java.util.List;
import java.util.Map;

import static com.jfrog.build.util.BuildContinueTranslator.buildIdToBase64;
import static com.jfrog.build.util.BuildContinueTranslator.toBuildFilter;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;

@Slf4j
@RequiredArgsConstructor
public class BuildGetAllNamesPagedUiCommand {

    private final BuildRepoService repoService;
    private final BuildSearchService searchService;
    private final BuildPermissionService permissionService;

    @NonNull
    public ContinueResult<String> getAllBuildNamesPaged(@NonNull Map<String, String[]> params) {
        permissionService.assertCanViewBuildPermissions(resolveProjectKey(params));
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(params,
                projectKey -> repoService.getBuildInfoRepoKeyByProject(projectKey, true));
        continueBuildDto.setOrderBy(ContinueBuildFilter.OrderBy.BUILD_NAME.name());
        continueBuildDto.setDirection(Direction.ASC);
        ContinueBuildFilter filter = toBuildFilter(continueBuildDto);
        ContinueResult<String> result = createBuildNamesContinueResult(filter);
        result.setTotalRequiredDataAmount(getTotalRequiredBuilds(filter));
        return result;
    }

    @NonNull
    private ContinueResult<String> createBuildNamesContinueResult(@NonNull ContinueBuildFilter filter) {
        List<String> builds = searchService.getDistinctBuildNamesPaging(filter);
        List<GeneralBuildInfo> generalBuildInfoList = builds.stream().map(this::getBuildGeneralInfo).toList();
        String nextContinueState = null;
        if (generalBuildInfoList.size() == filter.getLimit()) {
            GeneralBuildInfo last = generalBuildInfoList.getLast();
            nextContinueState = buildIdToBase64(last);
        }
        List<String> buildNames = generalBuildInfoList.stream().map(GeneralBuildInfo::getBuildName).toList();
        return new ContinueResult<>(nextContinueState, buildNames);
    }

    @Nullable
    private Long getTotalRequiredBuilds(@NonNull ContinueBuildFilter filter) {
        return isNull(filter.getContinueBuildId()) ? (long) searchService.getDistinctBuildNamesCount(filter) : null;
    }

    @NonNull
    private GeneralBuildInfo getBuildGeneralInfo(@NonNull String buildName) {
        return GeneralBuildInfo.builder()
                .buildName(buildName)
                .time(-1L)
                .build();
    }

    @NonNull
    private String resolveProjectKey(@NonNull Map<String, String[]> params) {
        String[] values = params.get("projectKey");
        String projectKey = values != null && values.length > 0 ? values[0] : null;
        return isBlank(projectKey) ? DEFAULT_PROJECT : projectKey;
    }
}
