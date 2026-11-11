package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.BuildNumberInfo;
import com.jfrog.build.model.api.BuildNumbersResponse;
import com.jfrog.build.model.api.BuildVersionSearchResults;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Objects;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildSearchVersionsByNameUiCommand {

    private final BuildRepoService repoService;
    private final BuildSearchService searchService;
    private final BuildPermissionService permissionService;
    private final BuildSysConfigService sysConfigService;

    @NonNull
    public BuildNumbersResponse searchBuildVersionsByName(
            @NonNull String buildName,
            @Nullable String buildRepo,
            @Nullable String orderBy,
            @Nullable String direction,
            @Nullable String before,
            @Nullable String after,
            @Nullable String numOfRows,
            @Nullable String offset) {
        BuildsSearchFilter filter = BuildsSearchFilter.builder()
                .name(buildName)
                .buildRepo(repoService.normaliseBuildRepoName(buildRepo))
                .before(parseEpoch(before))
                .after(parseEpoch(after))
                .limit(resolveLimit(numOfRows))
                .offset(resolveOffset(offset))
                .orderBy(orderBy)
                .sortOrder(direction)
                .build();
        BuildVersionSearchResults searchResults = searchService.getBuildVersions(filter);
        return toBuildNumbersResponse(searchResults);
    }

    @NonNull
    private BuildNumbersResponse toBuildNumbersResponse(@NonNull BuildVersionSearchResults searchResults) {
        BuildNumbersResponse response = new BuildNumbersResponse();
        searchResults.getBuilds().stream().map(this::toBuildNumberInfo).forEach(response::add);
        response.setOffset(searchResults.getOffset());
        return response;
    }

    @NonNull
    private BuildNumberInfo toBuildNumberInfo(@NonNull GeneralBuild build) {
        return BuildNumberInfo.builder()
                .buildRepo(build.getBuildRepo())
                .buildNumber(build.getBuildNumber())
                .ciUrl(build.getCiUrl())
                .buildTime(ISODateTimeFormat.dateTime().print(build.getBuildDate()))
                .buildStat(build.getStatus())
                .time(build.getBuildDate())
                .canDelete(canDelete(build))
                .build();
    }

    private boolean canDelete(@NonNull GeneralBuild build) {
        return sysConfigService.isBuildUiSkipDeletePermissionCheck()
                || permissionService.canDeleteBuild(build.getBuildName(), build.getBuildNumber(),
                formatBuildTime(build.getBuildDate()), build.getBuildRepo());
    }

    private long parseEpoch(@Nullable String value) {
        return isNotBlank(value) ? Long.parseLong(value) : 0;
    }

    /**
     * Mirrors the legacy versions-search limit handling: defaults to the configured user-query limit and caps any
     * explicit value at that limit.
     */
    private long resolveLimit(@Nullable String numOfRows) {
        int defaultLimit = sysConfigService.getSearchUserQueryLimit();
        int limit;
        try {
            limit = isNotBlank(numOfRows) ? Integer.parseInt(numOfRows) : defaultLimit;
        } catch (NumberFormatException e) {
            numOfRows = Objects.toString(numOfRows);
            throw badRequestException("The num_of_rows value must be a valid number, but received: '%s'", numOfRows);
        }
        return Math.min(limit, defaultLimit);
    }

    private long resolveOffset(@Nullable String offset) {
        if (isBlank(offset)) {
            return 0;
        }
        long parsedOffset;
        try {
            parsedOffset = Long.parseLong(offset);
        } catch (NumberFormatException e) {
            throw badRequestException("The offset value must be greater than or equal to 0 but received: '%s'", offset);
        }
        if (parsedOffset < 0) {
            throw badRequestException("The offset value must be greater than or equal to 0 but received: '%s'", offset);
        }
        return parsedOffset;
    }
}
