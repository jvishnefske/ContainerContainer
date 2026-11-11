package org.artifactory.storage.db.policy;

/**
 * Aggregated storage impact for a single retention policy over a time window.
 * Produced by {@link org.artifactory.storage.db.policy.dao.RetentionRunSummaryDao#getStorageImpact}.
 */
public record PolicyImpactRow(
        String policyKey,
        String retentionType,
        long bytesProcessed
) {
}
