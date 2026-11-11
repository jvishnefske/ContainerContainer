package org.artifactory.throttling.rrl.strategy;

import org.artifactory.download.RateLimiterException;
import org.artifactory.throtlling.rrl.enums.RequestRateLimiterType;

import java.util.Map;

/**
 * @author Igor Gumush
 */
public interface RequestRateLimiterService {
    void acquirePermit(RequestRateLimiterType rlType) throws RateLimiterException;

    boolean isFeatureDisabled();

    Map<RequestRateLimiterType, Map<String, Long>> getMetrics();
}
