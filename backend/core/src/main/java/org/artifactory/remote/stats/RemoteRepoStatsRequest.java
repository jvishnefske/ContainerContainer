package org.artifactory.remote.stats;

import java.util.List;

/**
 * Domain request object for querying remote repo statistics.
 * Contains only the query parameters — no REST/parsing concerns.
 *
 * @author Hussein Alshqerat
 */
public record RemoteRepoStatsRequest(
        String startDate,
        String endDate,
        List<String> repoKeys,
        List<String> packageTypes,
        Float minErrorRate,
        String statusPattern,
        boolean includeStatusBreakdown,
        boolean includeDailyBreakdown,
        String sortBy,
        String order,
        int offset,
        int limit
) {
}
