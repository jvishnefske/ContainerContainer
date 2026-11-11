package org.artifactory.throttling.rrl.strategy;

import org.artifactory.download.RateLimiterException;

import java.util.Map;

/**
 * @author Igor Gumush
 */
public interface InternalRequestRateLimiter {
    void acquirePermit() throws RateLimiterException;

    Map<String, Long> getMetrics();

    boolean shouldCountHeadRequest();
}
