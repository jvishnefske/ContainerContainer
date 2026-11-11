package org.artifactory.storage.db.monitor;

/**
 * A central monitor, receiving notifications about completion of every DB query.
 * The current implementation of this interface reports per-table DB query metrics.
 *
 * @author Sergey Steinvil
 */
public interface DbQueryMonitor {
    /**
     * Called by the {@link org.artifactory.storage.db.util.ArtifactoryJdbcHelper} when it completes running a query.
     *
     * @param query        - The text of the SQL query (as passed to JDBC helper)
     * @param params       - Parameters fpr the SQL query (if parametrized) - can be used for troubleshooting / logging.
     * @param durationNano - The time it took to execute the query (in nanoseconds)
     * @param error        - The exception that occurred while executing the DB query (if any), null if query succeeded.
     */
    void queryFinished(String query, Object[] params, long durationNano, Exception error);
}
