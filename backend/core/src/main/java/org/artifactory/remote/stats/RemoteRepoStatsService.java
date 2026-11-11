package org.artifactory.remote.stats;

/**
 * Service for collecting and persisting remote repository request statistics.
 * Each Artifactory node accumulates stats in memory and periodically flushes
 * to the database using additive merge for HA safety.
 *
 * @author husseina
 */
public interface RemoteRepoStatsService {

    /**
     * Records a single remote repository request. This method is thread-safe and non-blocking;
     * data is accumulated in memory until the next scheduled flush.
     *
     * @param repositoryKey   the remote repository key
     * @param repoPackageType the package type of the repository (e.g., "maven", "npm")
     * @param httpMethod      the HTTP method used (e.g., "GET", "HEAD")
     * @param statusCode      the HTTP response status code
     * @param responseTimeMs  the total response time in milliseconds
     * @param ttfbMs          time to first byte in milliseconds
     * @param responseSize    the response body size in bytes
     */
    void recordRequest(String repositoryKey, String repoPackageType, String httpMethod,
                       int statusCode, long responseTimeMs, long ttfbMs, long responseSize);

    /**
     * Flushes in-memory accumulated stats to the database using additive merge.
     * Safe to call from multiple HA nodes concurrently.
     */
    void flushStats();

    /**
     * Queries remote repository statistics from the database with filtering,
     * sorting, and pagination.
     *
     * @param request the query parameters
     * @return domain result with global stats and per-repo data
     */
    RemoteRepoStatsQueryResult queryStats(RemoteRepoStatsRequest request);
}
