package org.artifactory.remote.stats;

import java.util.List;
import java.util.Map;

/**
 * Domain object returned by the service layer for remote repo stats queries.
 * Decoupled from any REST/presentation concerns — the REST layer maps this
 * to the appropriate response DTO.
 *
 * @author Hussein Alshqerat
 */
public record RemoteRepoStatsQueryResult(
        String startDate,
        String endDate,
        int offset,
        int limit,
        long totalMatchedRepos,
        GlobalStats globalStats,
        List<RepoResult> repos
) {

    public record GlobalStats(
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            double errorRate,
            long avgResponseTimeMs,
            long avgTtfbMs,
            long totalResponseSize,
            int repoCount,
            Map<Integer, Long> byStatus,
            Map<String, Long> byStatusGroup
    ) {}

    public record RepoResult(
            String repoKey,
            String packageType,
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            double errorRate,
            long avgResponseTimeMs,
            long avgTtfbMs,
            long totalResponseSize,
            long avgTransferRate,
            Map<Integer, Long> byStatus,
            Map<String, Long> byStatusGroup,
            List<DayResult> daily
    ) {}

    public record DayResult(
            String date,
            long totalRequests,
            long avgResponseTimeMs,
            long avgTtfbMs,
            long totalResponseSize,
            Map<Integer, Long> byStatus,
            Map<String, Long> byStatusGroup
    ) {}
}
