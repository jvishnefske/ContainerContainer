package org.artifactory.storage.db.remote.stats.entity;

import lombok.Builder;

/**
 * Entity representing a record in the remote_repo_stats_hourly and remote_repo_stats_daily tables.
 * Both tables share an identical schema; the distinction between hourly and daily aggregation
 * is determined by which table the record is read from or written to.
 *
 * @author husseina
 */
@Builder(toBuilder = true)
public record RemoteRepoStatsEntity(
        long id,
        String repositoryKey,
        String repoPackageType,
        long updateTime,
        long totalRequestCount,
        long totalResponseTimeMs,
        long maxResponseTimeMs,
        long ttfbAvg,
        long totalResponseSize,
        long status2xxCount,
        long status3xxCount,
        long status4xxCount,
        long status5xxCount,
        String httpMethod,
        long transferRate
) {
}
