package org.artifactory.storage.db.remote.stats.entity;

import lombok.Builder;

/**
 * Entity representing a record in the remote_repo_stats_status table.
 * Stores per-status-code and HTTP method breakdown for both hourly and daily
 * aggregation windows, distinguished by the {@code aggregationType} field.
 *
 * @author husseina
 */
@Builder(toBuilder = true)
public record RemoteRepoStatsStatusEntity(
        long id,
        String repositoryKey,
        String repoPackageType,
        long updateTime,
        String aggregationType,
        int statusCode,
        long requestCount,
        String httpMethod
) {
}
