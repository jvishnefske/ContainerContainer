package org.artifactory.throttling.qrl.type;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * This implementation applies no rate limiting.
 * Used for system critical operations such as heartbeat.
 *
 * @author Andrey Tashlitsky
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public final class DisabledQueryRateLimiter implements ArtifactoryQueryRateLimiter {

    @Override
    public void acquirePermit() {
        /*
         * No rate limiting is applied.
         */
    }

    @Override
    public void updateExecutionTime(int queryTime) {
        /*
         * No rate limiting is applied.
         */
    }
}
