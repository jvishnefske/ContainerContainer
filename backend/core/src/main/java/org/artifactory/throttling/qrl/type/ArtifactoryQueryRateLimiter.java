package org.artifactory.throttling.qrl.type;

/**
 * @author Andrey Tashlitsky
 */
public sealed interface ArtifactoryQueryRateLimiter permits EnabledQueryRateLimiter, DisabledQueryRateLimiter {

    void acquirePermit();

    void updateExecutionTime(int queryTime);
}
