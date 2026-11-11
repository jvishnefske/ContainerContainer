package org.artifactory.traffic.summary;

import lombok.Builder;
import org.artifactory.storage.db.traffic.summary.entity.TrafficSummaryEntity;

/**
 * @author kunalm
 */
@Builder(toBuilder = true)
public record TrafficSnapshot(
        long timestamp,
        long totalRequestCount,
        long uploadRequestCount,
        long downloadRequestCount,
        long totalBytes,
        long uploadBytes,
        long downloadBytes
) {
    public TrafficSummaryEntity toTrafficSummaryEntity() {
        return TrafficSummaryEntity.builder()
                .time(timestamp)
                .totalRequests(totalRequestCount)
                .uploadRequests(uploadRequestCount)
                .downloadRequests(downloadRequestCount)
                .totalBytes(totalBytes)
                .uploadBytes(uploadBytes)
                .downloadBytes(downloadBytes)
                .build();
    }
}
