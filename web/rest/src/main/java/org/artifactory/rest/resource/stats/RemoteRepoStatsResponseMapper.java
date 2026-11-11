package org.artifactory.rest.resource.stats;

import org.artifactory.remote.stats.RemoteRepoStatsQueryResult;
import org.artifactory.rest.resource.stats.dto.RemoteRepoStatsResponse;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Maps the domain {@link RemoteRepoStatsQueryResult} into the REST
 * {@link RemoteRepoStatsResponse} DTO. Keeps the service layer free
 * of any presentation/serialization concerns.
 *
 * @author Hussein Alshqerat
 */
public final class RemoteRepoStatsResponseMapper {

    private RemoteRepoStatsResponseMapper() {
    }

    public static RemoteRepoStatsResponse toResponse(RemoteRepoStatsQueryResult result) {
        return RemoteRepoStatsResponse.builder()
                .timeRange(RemoteRepoStatsResponse.TimeRange.builder()
                        .startDate(result.startDate())
                        .endDate(result.endDate())
                        .build())
                .pagination(RemoteRepoStatsResponse.Pagination.builder()
                        .offset(result.offset())
                        .limit(result.limit())
                        .total(result.totalMatchedRepos())
                        .build())
                .summary(mapSummary(result.globalStats()))
                .repos(result.repos().stream()
                        .map(RemoteRepoStatsResponseMapper::mapRepo)
                        .collect(Collectors.toList()))
                .build();
    }

    private static RemoteRepoStatsResponse.Summary mapSummary(
            RemoteRepoStatsQueryResult.GlobalStats gs) {
        return RemoteRepoStatsResponse.Summary.builder()
                .totalRequests(gs.totalRequests())
                .successfulRequests(gs.successfulRequests())
                .failedRequests(gs.failedRequests())
                .errorRate(gs.errorRate())
                .avgResponseTimeMs(gs.avgResponseTimeMs())
                .avgTtfbMs(gs.avgTtfbMs())
                .totalResponseSize(gs.totalResponseSize())
                .repoCount(gs.repoCount())
                .byStatus(toStringKeyMap(gs.byStatus()))
                .byStatusGroup(gs.byStatusGroup())
                .build();
    }

    private static RemoteRepoStatsResponse.RepoStats mapRepo(
            RemoteRepoStatsQueryResult.RepoResult repo) {
        return RemoteRepoStatsResponse.RepoStats.builder()
                .repoKey(repo.repoKey())
                .packageType(repo.packageType())
                .totalRequests(repo.totalRequests())
                .successfulRequests(repo.successfulRequests())
                .failedRequests(repo.failedRequests())
                .errorRate(repo.errorRate())
                .avgResponseTimeMs(repo.avgResponseTimeMs())
                .avgTtfbMs(repo.avgTtfbMs())
                .totalResponseSize(repo.totalResponseSize())
                .avgTransferRate(repo.avgTransferRate())
                .byStatus(toStringKeyMap(repo.byStatus()))
                .byStatusGroup(repo.byStatusGroup())
                .daily(mapDaily(repo.daily()))
                .build();
    }

    private static List<RemoteRepoStatsResponse.DailyStats> mapDaily(
            List<RemoteRepoStatsQueryResult.DayResult> days) {
        if (days == null) {
            return null;
        }
        return days.stream()
                .map(d -> RemoteRepoStatsResponse.DailyStats.builder()
                        .date(d.date())
                        .totalRequests(d.totalRequests())
                        .avgResponseTimeMs(d.avgResponseTimeMs())
                        .avgTtfbMs(d.avgTtfbMs())
                        .totalResponseSize(d.totalResponseSize())
                        .byStatus(toStringKeyMap(d.byStatus()))
                        .byStatusGroup(d.byStatusGroup())
                        .build())
                .collect(Collectors.toList());
    }

    private static Map<String, Long> toStringKeyMap(Map<Integer, Long> intKeyMap) {
        if (intKeyMap == null) {
            return null;
        }
        Map<String, Long> result = new TreeMap<>();
        intKeyMap.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
