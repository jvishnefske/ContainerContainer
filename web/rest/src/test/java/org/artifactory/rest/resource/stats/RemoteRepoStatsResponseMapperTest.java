package org.artifactory.rest.resource.stats;

import org.artifactory.remote.stats.RemoteRepoStatsQueryResult;
import org.artifactory.rest.resource.stats.dto.RemoteRepoStatsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link RemoteRepoStatsResponseMapper}.
 *
 * @author Hussein Alshqerat
 */
class RemoteRepoStatsResponseMapperTest {

    @Test
    void toResponse_shouldMapTimeRangeAndPagination() {
        RemoteRepoStatsQueryResult result = new RemoteRepoStatsQueryResult(
                "2026-02-01T00:00:00Z", "2026-02-22T00:00:00Z",
                5, 25, 100,
                emptyGlobalStats(),
                List.of());

        RemoteRepoStatsResponse resp =
                RemoteRepoStatsResponseMapper.toResponse(result);

        assertEquals("2026-02-01T00:00:00Z", resp.timeRange().startDate());
        assertEquals("2026-02-22T00:00:00Z", resp.timeRange().endDate());
        assertEquals(5, resp.pagination().offset());
        assertEquals(25, resp.pagination().limit());
        assertEquals(100, resp.pagination().total());
    }

    @Test
    void toResponse_shouldMapGlobalStats() {
        RemoteRepoStatsQueryResult.GlobalStats gs =
                new RemoteRepoStatsQueryResult.GlobalStats(
                        1000, 800, 200, 0.2,
                        150, 100, 50000, 3,
                        Map.of(200, 700L, 404, 200L),
                        Map.of("2xx", 700L, "4xx", 200L));

        RemoteRepoStatsQueryResult result = new RemoteRepoStatsQueryResult(
                "2026-02-01T00:00:00Z", "2026-02-22T00:00:00Z",
                0, 50, 3, gs, List.of());

        RemoteRepoStatsResponse resp =
                RemoteRepoStatsResponseMapper.toResponse(result);

        RemoteRepoStatsResponse.Summary s = resp.summary();
        assertEquals(1000, s.totalRequests());
        assertEquals(800, s.successfulRequests());
        assertEquals(200, s.failedRequests());
        assertEquals(0.2, s.errorRate());
        assertEquals(3, s.repoCount());
        assertNotNull(s.byStatus().get("200"));
        assertEquals(700L, s.byStatus().get("200"));
        assertEquals(200L, s.byStatus().get("404"));
        assertEquals(700L, s.byStatusGroup().get("2xx"));
    }

    @Test
    void toResponse_shouldConvertIntStatusKeysToStrings() {
        RemoteRepoStatsQueryResult.RepoResult repo =
                new RemoteRepoStatsQueryResult.RepoResult(
                        "repo-a", "maven", 100, 80, 20, 0.2,
                        150, 100, 50000, 333,
                        Map.of(200, 80L, 404, 20L),
                        Map.of("2xx", 80L, "4xx", 20L),
                        null);

        RemoteRepoStatsQueryResult result = new RemoteRepoStatsQueryResult(
                "2026-02-01T00:00:00Z", "2026-02-22T00:00:00Z",
                0, 50, 1, emptyGlobalStats(), List.of(repo));

        RemoteRepoStatsResponse resp =
                RemoteRepoStatsResponseMapper.toResponse(result);

        RemoteRepoStatsResponse.RepoStats r = resp.repos().get(0);
        assertNotNull(r.byStatus());
        assertEquals(80L, r.byStatus().get("200"));
        assertEquals(20L, r.byStatus().get("404"));
    }

    @Test
    void toResponse_shouldMapDailyBreakdown_whenPresent() {
        RemoteRepoStatsQueryResult.DayResult day =
                new RemoteRepoStatsQueryResult.DayResult(
                        "2026-02-10", 50, 120, 90, 25000,
                        Map.of(200, 40L, 404, 10L),
                        Map.of("2xx", 40L, "4xx", 10L));

        RemoteRepoStatsQueryResult.RepoResult repo =
                new RemoteRepoStatsQueryResult.RepoResult(
                        "repo-a", "npm", 50, 40, 10, 0.2,
                        120, 90, 25000, 208,
                        null, null, List.of(day));

        RemoteRepoStatsQueryResult result = new RemoteRepoStatsQueryResult(
                "2026-02-01T00:00:00Z", "2026-02-22T00:00:00Z",
                0, 50, 1, emptyGlobalStats(), List.of(repo));

        RemoteRepoStatsResponse resp =
                RemoteRepoStatsResponseMapper.toResponse(result);

        RemoteRepoStatsResponse.RepoStats r = resp.repos().get(0);
        assertNotNull(r.daily());
        assertEquals(1, r.daily().size());
        assertEquals("2026-02-10", r.daily().get(0).date());
        assertEquals(50, r.daily().get(0).totalRequests());
        assertEquals(40L, r.daily().get(0).byStatus().get("200"));
    }

    @Test
    void toResponse_shouldReturnNullDaily_whenNotPresent() {
        RemoteRepoStatsQueryResult.RepoResult repo =
                new RemoteRepoStatsQueryResult.RepoResult(
                        "repo-a", "npm", 50, 40, 10, 0.2,
                        120, 90, 25000, 208,
                        null, null, null);

        RemoteRepoStatsQueryResult result = new RemoteRepoStatsQueryResult(
                "2026-02-01T00:00:00Z", "2026-02-22T00:00:00Z",
                0, 50, 1, emptyGlobalStats(), List.of(repo));

        RemoteRepoStatsResponse resp =
                RemoteRepoStatsResponseMapper.toResponse(result);

        assertNull(resp.repos().get(0).daily());
        assertNull(resp.repos().get(0).byStatus());
    }

    private RemoteRepoStatsQueryResult.GlobalStats emptyGlobalStats() {
        return new RemoteRepoStatsQueryResult.GlobalStats(
                0, 0, 0, 0.0, 0, 0, 0, 0,
                Map.of(), Map.of());
    }
}
