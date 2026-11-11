package org.artifactory.rest.resource.stats.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for the remote repo stats query API.
 *
 * @author Hussein Alshqerat
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemoteRepoStatsResponse(
        TimeRange timeRange,
        Pagination pagination,
        Summary summary,
        List<RepoStats> repos
) {

    @Builder
    public record TimeRange(String startDate, String endDate) {}

    @Builder
    public record Pagination(int offset, int limit, long total) {}

    @Builder
    public record Summary(
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            double errorRate,
            long avgResponseTimeMs,
            long avgTtfbMs,
            long totalResponseSize,
            int repoCount,
            Map<String, Long> byStatus,
            Map<String, Long> byStatusGroup
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RepoStats(
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
            Map<String, Long> byStatus,
            Map<String, Long> byStatusGroup,
            List<DailyStats> daily
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DailyStats(
            String date,
            long totalRequests,
            long avgResponseTimeMs,
            long avgTtfbMs,
            long totalResponseSize,
            Map<String, Long> byStatus,
            Map<String, Long> byStatusGroup
    ) {}
}
