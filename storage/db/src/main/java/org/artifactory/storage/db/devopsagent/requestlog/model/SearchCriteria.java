package org.artifactory.storage.db.devopsagent.requestlog.model;

import lombok.Builder;

import java.util.List;

/**
 * Filters + pagination for an inbound search call. Built by
 * {@link org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogService} after parsing and
 * validating the HTTP query parameters; consumed by the DAOs.
 * <p>
 * Each list-typed filter is empty (not null) when absent. Cursor takes precedence over offset
 * when both are supplied.
 */
@Builder
public record SearchCriteria(
        Long timeFrom,
        Long timeTo,
        long minDurationMs,
        List<String> repoNames,        // empty list = no filter
        String searchQuery,            // PG tsquery string (joined with '|' or '&'); null when absent
        String rawSearchText,          // original search param for MySQL FULLTEXT / LIKE fallbacks
        int limit,                     // already clamped to [1, 2000]
        int offset,                    // already clamped to [0, 10000]
        Cursor cursor) {               // nullable
}
