package org.artifactory.throttling.qrl.type;

import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;

import jakarta.annotation.Nonnull;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * @author Andrey Tashlitsky
 */
public final class SimulationQueryRateLimiter extends EnabledQueryRateLimiter {

    private long simModeFailedAcquireCountTime = 0;
    private long simModeFailedQuotaAcquireTime = 0;

    SimulationQueryRateLimiter(@Nonnull QueryRateLimiterType qrlType, int permitsPerTimeFrame,
            int timeFrameMillis, int timeQuota) {
        super(qrlType, permitsPerTimeFrame, timeFrameMillis, timeQuota);
    }

    @Override
    protected synchronized void acquireCountPermitInternal() {
        boolean acquired = queryCountRateLimiter.tryAcquire(permitsPerTimeFrame, 1, NANOSECONDS);
        if (acquired) {
            if (simModeFailedAcquireCountTime != 0) {
                long acquireTime = System.currentTimeMillis() - simModeFailedAcquireCountTime;
                qrlMetrics.addTotalSlowedDownMillis(acquireTime);
                simModeFailedAcquireCountTime = 0; // stopping stopwatch
            }
        } else {
            if (simModeFailedAcquireCountTime == 0) {
                simModeFailedAcquireCountTime = System.currentTimeMillis(); // starting stopwatch
            }
        }
    }

    @Override
    protected synchronized void acquireTimePermitInternal() {
        boolean acquired = queryTimeRateLimiter.tryAcquire(timeQuota, 1, NANOSECONDS);
        if (acquired) {
            if (simModeFailedQuotaAcquireTime != 0) {
                long acquireTime = System.currentTimeMillis() - simModeFailedQuotaAcquireTime;
                qrlMetrics.addTotalSlowedDownByTimeMillis(acquireTime);
                simModeFailedQuotaAcquireTime = 0; // stopping stopwatch
            }
        } else {
            if (simModeFailedQuotaAcquireTime == 0) {
                simModeFailedQuotaAcquireTime = System.currentTimeMillis(); // starting stopwatch
            }
        }
    }
}
