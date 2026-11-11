package org.artifactory.storage.db.aql.stats;

import lombok.Getter;

/**
 * Per-template aggregation: mutated only by the {@link AqlQueryStatsManager} worker thread,
 * so no synchronization is needed.
 *
 * @author Amir Avidan
 */
@Getter
final class AqlStatsEntry {

    private final String checksum;
    private final String template;
    private final String sampleQuery;
    private long calls;
    private long totalMs;
    private long totalRows;
    private long minMs = Long.MAX_VALUE;
    private long maxMs;

    AqlStatsEntry(String checksum, String template, String sampleQuery) {
        this.checksum = checksum;
        this.template = template;
        this.sampleQuery = sampleQuery;
    }

    void recordObservation(long durationMs, long rowsCount) {
        if (durationMs < minMs) {
            minMs = durationMs;
        }
        if (durationMs > maxMs) {
            maxMs = durationMs;
        }
        calls++;
        totalMs += durationMs;
        totalRows += rowsCount;
    }
}
