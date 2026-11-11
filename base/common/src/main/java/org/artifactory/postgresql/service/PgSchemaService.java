package org.artifactory.postgresql.service;

/**
 * Service for collecting and exposing PostgreSQL schema metrics through Micrometer gauges.
 * <p>
 * Monitors configured indexes and tables, exposing metrics such as size and row counts. Metrics are refreshed
 * periodically by a scheduled job and cached to minimize database load.
 * <p>
 * <b>Design:</b> Configuration is static (requires restart). On query failure, stale data is preserved rather than
 * cleared. All configured index names must exist in the PostgreSQL database.
 *
 * @author Andrey Tashlitsky
 */
public interface PgSchemaService {

    /**
     * Refreshes cached metrics from the database. Called periodically by
     * {@link org.artifactory.postgresql.job.PgSchemaMetricsJob}.
     * <p>
     * Thread-safe with atomic cache updates. On failure, preserves stale data and logs error.
     */
    void refreshMetrics();
}
