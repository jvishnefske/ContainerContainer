package org.artifactory.storage.db.traffic.stats.entity;

import lombok.Builder;

/**
 * Entity representing a record in the repo_traffic_hourly and repo_traffic_daily tables.
 * Both tables share an identical schema; the distinction between hourly and daily aggregation
 * is determined by which table the record is read from or written to.
 *
 * @author georgyr
 */
@Builder(toBuilder = true)
public record RepoTrafficStatsEntity(
        long id,
        long updateTime,
        String action,
        String actor,
        String redirectTarget,
        String originRepoKey,
        String originRepoType,
        String originPackageType,
        String repoKey,
        String repoType,
        String packageType,
        long requestCount,
        long totalTransferredSize
) {
}
