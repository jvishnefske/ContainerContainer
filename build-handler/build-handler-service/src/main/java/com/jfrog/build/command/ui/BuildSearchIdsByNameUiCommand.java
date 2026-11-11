package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.model.BuildReleaseBundle;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.model.api.BuildIdInfo;
import com.jfrog.build.model.api.BuildIdReleaseBundle;
import com.jfrog.build.model.api.BuildIdResponse;
import com.jfrog.build.model.api.BuildIdSearchResults;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Date;
import java.util.Objects;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildSearchIdsByNameUiCommand {

    private final BuildRepoService repoService;
    private final BuildSearchService searchService;
    private final BuildPermissionService permissionService;
    private final BuildSysConfigService sysConfigService;

    @NonNull
    public BuildIdResponse searchBuildIdsByName(
            @Nullable String buildName,
            @Nullable String buildRepo,
            @Nullable String projectKey,
            @Nullable String orderBy,
            @Nullable String direction,
            @Nullable String before,
            @Nullable String after,
            @Nullable String numOfRows,
            @Nullable String offset) {
        BuildsSearchFilter filter = BuildsSearchFilter.builder()
                .name(buildName)
                .buildRepo(repoService.getBuildRepoForSearch(buildRepo, projectKey))
                .projectKey(projectKey)
                .before(parseEpoch(before))
                .after(parseEpoch(after))
                .limit(resolveLimit(numOfRows))
                .offset(resolveOffset(offset))
                .orderBy(orderBy)
                .sortOrder(direction)
                .build();
        BuildIdSearchResults searchResults = searchService.findBuildIDsByNameCaseInsensitive(filter);
        return toBuildIdResponse(searchResults);
    }

    @NonNull
    private BuildIdResponse toBuildIdResponse(@NonNull BuildIdSearchResults searchResults) {
        BuildIdResponse response = new BuildIdResponse();
        searchResults.getBuilds().stream().map(this::toBuildIdInfo).forEach(response::add);
        response.setOffset(searchResults.getOffset());
        return response;
    }

    @NonNull
    private BuildIdInfo toBuildIdInfo(@NonNull BuildId build) {
        Date startDate = build.getStartedDate();
        long startTime = startDate != null ? startDate.getTime() : 0;
        Set<BuildReleaseBundle> releaseBundles =
                build instanceof BuildIdImpl id ? id.getReleaseBundles() : Set.of();
        return BuildIdInfo.builder()
                .buildName(build.getName())
                .lastBuildTime(ISODateTimeFormat.dateTime().print(startTime))
                .buildRepo(build.getBuildRepo())
                .time(startTime)
                .lastBuildNumber(build.getNumber())
                .releaseBundles(releaseBundles.stream()
                        .map(bundle -> new BuildIdReleaseBundle(bundle.repoKey(), bundle.name()))
                        .toList())
                .canDelete(canDelete(build))
                .build();
    }

    private boolean canDelete(@NonNull BuildId build) {
        return sysConfigService.isBuildUiSkipDeletePermissionCheck()
                || permissionService.canDeleteBuild(build.getName(), build.getNumber(), build.getStarted(),
                build.getBuildRepo());
    }

    private long parseEpoch(@Nullable String value) {
        return isNotBlank(value) ? Long.parseLong(value) : 0;
    }

    /**
     * Mirrors the legacy {@code SearchBuildApiValidator#getLimitForSearch}: defaults to an unbounded request capped at
     * the configured user-query limit; negative values collapse to that limit too.
     */
    private long resolveLimit(@Nullable String numOfRows) {
        long defaultLimit = sysConfigService.getSearchUserQueryLimit();
        long limit;
        try {
            limit = isNotBlank(numOfRows) ? Long.parseLong(numOfRows) : Long.MAX_VALUE;
        } catch (NumberFormatException e) {
            numOfRows = Objects.toString(numOfRows);
            throw badRequestException("The num_of_rows value must be a number between %d and %d, but received: '%s'. "
                    + "Please provide a valid number within the specified range", 0, defaultLimit, numOfRows);
        }
        return limit < 0 ? Math.max(limit, defaultLimit) : Math.min(limit, defaultLimit);
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
