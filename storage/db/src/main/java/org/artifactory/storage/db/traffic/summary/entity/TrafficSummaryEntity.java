package org.artifactory.storage.db.traffic.summary.entity;

import lombok.Builder;

/**
 * Entity representing a record in the traffic_summary table
 *
 * @author kunalm
 */
@Builder(toBuilder = true)
public record TrafficSummaryEntity(
        long id,
        long time,
        long totalRequests,
        long uploadRequests,
        long downloadRequests,
        long totalBytes,
        long uploadBytes,
        long downloadBytes
) {
}
