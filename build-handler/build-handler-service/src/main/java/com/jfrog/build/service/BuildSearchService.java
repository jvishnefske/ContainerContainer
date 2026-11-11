package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.model.BuildPaginatedResult;
import com.jfrog.build.model.api.BuildIdSearchResults;
import com.jfrog.build.model.api.BuildVersionSearchResults;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.search.BuildPaginatedAdaptiveSearchService;
import com.jfrog.build.search.BuildPaginatedSearchService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jfrog.common.StreamSupportUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;

/**
 * Paginated search over build IDs and versions.
 */
@Slf4j
public class BuildSearchService {

    private final BuildDatabaseService databaseService;
    private final BuildPermissionService permissionService;

    private final BuildPaginatedSearchService<GeneralBuild> paginatedSearchService;
    private final BuildPaginatedAdaptiveSearchService<BuildId> paginatedAdaptiveSearchService;

    public BuildSearchService(
            @NonNull BuildBridgeToArtifactory bridgeToArtifactory,
            @NonNull BuildPermissionService permissionService,
            @NonNull BuildDatabaseService databaseService,
            @NonNull BuildSysConfigService sysConfigService) {
        this.permissionService = permissionService;
        this.databaseService = databaseService;
        this.paginatedSearchService = new BuildPaginatedSearchService<>(
                databaseService::getBuildNumbersByBuildName,
                build -> bridgeToArtifactory.isAdmin() || filterVersionByPermissions(build)
        );
        this.paginatedAdaptiveSearchService =  new BuildPaginatedAdaptiveSearchService<>(
                databaseService::getLatestBuildIDsByNameCaseInsensitive,
                sysConfigService.getBuildSearchMaxBatchSize(),
                build -> permissionService.hasBasicReadPermissionForAllBuilds() || permissionService.canReadBuild(
                        build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo())
        );
    }

    private boolean filterVersionByPermissions(GeneralBuild buildVersion) {
        return permissionService.isBuildBasicRead(buildVersion.getBuildName(), buildVersion.getBuildNumber(),
                formatBuildTime(buildVersion.getBuildDate()), buildVersion.getBuildRepo());
    }

    public List<BuildId> getLatestBuildIDsPaging(ContinueBuildFilter buildFilter) {
        return iterateFetchFromDb(databaseService::getLatestBuildIDsPaging,
                build -> permissionService.isBuildBasicRead(
                        build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo()),
                buildId -> buildId, buildFilter);
    }

    public List<String> getDistinctBuildNamesPaging(ContinueBuildFilter buildFilter) {
        return iterateFetchFromDb(databaseService::getDistinctBuildNamesPaging,
                build -> true,
                buildName -> new BuildIdImpl(-1L, buildName, null, -1, null),
                buildFilter);
    }

    public int getDistinctBuildNamesCount(ContinueBuildFilter continueBuildFilter) {
        return databaseService.getDistinctBuildNamesCount(continueBuildFilter);
    }

    public BuildIdSearchResults findBuildIDsByNameCaseInsensitive(BuildsSearchFilter buildSearchFilter) {
        BuildPaginatedResult<BuildId> pagedResult = paginatedAdaptiveSearchService.search(buildSearchFilter);
        List<BuildId> builds = pagedResult.data();
        databaseService.populateReleaseBundleNames(builds);
        return BuildIdSearchResults.builder().builds(builds).offset(pagedResult.offset()).build();
    }

    @NonNull
    public BuildVersionSearchResults getBuildVersions(@NonNull BuildsSearchFilter filter) {
        BuildPaginatedResult<GeneralBuild> pagedResult = paginatedSearchService.search(filter);
        populateLatestPromotionStatuses(pagedResult.data());
        return BuildVersionSearchResults.builder()
                .builds(pagedResult.data())
                .offset(pagedResult.offset())
                .build();
    }

    private void populateLatestPromotionStatuses(@NonNull List<GeneralBuild> builds) {
        List<Long> buildIds = builds.stream().map(GeneralBuild::getBuildId).toList();
        Map<Long, String> buildIdsToPromotionStatuses = databaseService.getLatestPromotionStatusForBuildIds(buildIds);
        builds.forEach(build -> build.setStatus(buildIdsToPromotionStatuses.get(build.getBuildId())));
    }

    public List<GeneralBuild> getBuildForName(
            String buildName,
            String buildRepo,
            ContinueBuildFilter buildFilter) {
        return iterateFetchFromDb(continuePageBuildFilter ->
                        databaseService.getBuildForName(buildName, buildRepo, continuePageBuildFilter),
                build -> permissionService.isBuildBasicRead(build.getBuildName(), build.getBuildNumber(),
                        formatBuildTime(build.getBuildDate()), build.getBuildRepo()),
                generalBuild -> new BuildIdImpl(generalBuild.getBuildId(), generalBuild.getBuildName(),
                        generalBuild.getBuildNumber(), generalBuild.getBuildDate(), generalBuild.getBuildRepo()),
                buildFilter);
    }

    private <T> List<T> iterateFetchFromDb(
            Function<ContinueBuildFilter, List<T>> fetchFunction,
            Predicate<T> permissionPredicate,
            Function<T, BuildId> castingNextContinueBuildFunction,
            ContinueBuildFilter continueBuildFilter) {
        BuildId continueBuild = continueBuildFilter.getContinueBuildId();
        boolean needToFetchFromDB = true;
        List<T> result = new ArrayList<>();
        ContinueBuildFilter nextBuildFilter = new ContinueBuildFilter(continueBuildFilter);
        while (needToFetchFromDB) {
            nextBuildFilter.setLimit(continueBuildFilter.getLimit() * 3);
            nextBuildFilter.setContinueBuildId(continueBuild);
            List<T> itemsFromDb = fetchFunction.apply(nextBuildFilter);
            List<T> currentIterationList = StreamSupportUtils.stream(itemsFromDb)
                    .filter(permissionPredicate)
                    .limit(continueBuildFilter.getLimit() - result.size())
                    .toList();
            result.addAll(currentIterationList);
            if (CollectionUtils.isNotEmpty(itemsFromDb) && result.size() < continueBuildFilter.getLimit()) {
                continueBuild = castingNextContinueBuildFunction.apply(itemsFromDb.get(itemsFromDb.size() - 1));
                log.debug("Calling database again to fetch builds after '{}'", continueBuild.getName());
            } else {
                needToFetchFromDB = false;
            }
        }
        return result;
    }
}
