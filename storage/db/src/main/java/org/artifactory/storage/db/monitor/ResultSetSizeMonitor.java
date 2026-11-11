package org.artifactory.storage.db.monitor;

/**
 * An interface for monitoring the size of result sets returned by database queries.
 * It allows for tracking the number of rows fetched by queries and reporting large (or empty)
 * result sets.
 *
 * <p>
 *     This interface is intended to be used internally by the ArtifactoryJdbcHelper and
 *     not meant for direct use in the application code.
 * </p>
 *
 * @author Sergey Steinvil
 */
public interface ResultSetSizeMonitor {
    /**
     * Decides whether to monitor the number of rows returned by the query in order
     * to detect activities, fetching large amount of rows.
     */
    boolean shouldMonitor(String query, boolean isStreaming, String hint);

    /**
     * Creates a new {@link DbQueryContext} for the given database query.
     * It is important to capture the context of the caller function - the current activity name, etc.,
     * because reporting of the result set size may happen in a completely different thread.
     *
     * @param query The SQL query string.
     * @param isStreaming Whether the query is a streaming query.
     * @param hint An optional hint for the query context.
     * @return A new instance of {@link DbQueryContext}.
     */
    default DbQueryContext createContext(String query, boolean isStreaming, String hint) {
        return new DbQueryContext(query, isStreaming, hint);
    }

    /**
     * Returns the current time in nanoseconds. This method is used to measure duration
     * of fetching records from the result set. It can be overridden for testing purposes.
     *
     * @return The current time in nanoseconds.
     */
    default long getNanoTime() {
        return System.nanoTime();
    }

    /**
     * Called when the query fetch is completed.
     * @param queryContext The details and the context of the query that was executed.
     * @param rowsCount The number of rows fetched by the query.
     * @param fetchDurationNanos The time it took to fetch rows from the result set (in nanoseconds).
     */
    void onQueryFetchCompleted(DbQueryContext queryContext, long rowsCount, long fetchDurationNanos);
}
