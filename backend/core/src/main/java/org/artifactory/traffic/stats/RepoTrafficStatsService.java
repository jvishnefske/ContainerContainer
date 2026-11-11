package org.artifactory.traffic.stats;

import org.artifactory.traffic.TrafficEntry;

/**
 * Service for collecting and persisting per-repo traffic statistics.
 * Each Artifactory node accumulates stats in memory and periodically flushes
 * to the database using additive merge for HA safety.
 *
 * @author georgyr
 */
public interface RepoTrafficStatsService {

    /**
     * Records a single traffic event. Thread-safe and non-blocking;
     * data is accumulated in memory until the next scheduled flush.
     *
     * @param entry the enriched traffic entry (must already have repo metadata set)
     */
    void recordTrafficEntry(TrafficEntry entry);

    /**
     * Flushes in-memory accumulated stats to the database using additive merge.
     * Safe to call from multiple HA nodes concurrently.
     */
    void flushStats();
}
