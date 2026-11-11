package org.artifactory.throttling.qrl.type;

import com.google.common.util.concurrent.RateLimiter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;
import org.artifactory.throtlling.qrl.model.QueryRateLimiterMetrics;

import jakarta.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The basic query rate limiting mechanism implementation.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
public sealed class EnabledQueryRateLimiter implements ArtifactoryQueryRateLimiter permits SimulationQueryRateLimiter {

    private final QueryRateLimiterType qrlType;
    @Getter
    protected final int permitsPerTimeFrame;
    @Getter
    protected final int timeFrameMillis;
    @Getter
    protected final int timeQuota;
    protected final RateLimiter queryCountRateLimiter;
    protected final RateLimiter queryTimeRateLimiter;
    protected final QueryRateLimiterMetrics qrlMetrics = new QueryRateLimiterMetrics();
    protected int availablePermits;
    protected final AtomicInteger availableTimeQuota = new AtomicInteger(0);

    protected EnabledQueryRateLimiter(@Nonnull QueryRateLimiterType qrlType, int permitsPerTimeFrame,
            int timeFrameMillis, int timeQuota) {
        this.qrlType = qrlType;
        this.permitsPerTimeFrame = permitsPerTimeFrame;
        this.timeFrameMillis = timeFrameMillis;
        this.timeQuota = timeQuota;
        this.queryCountRateLimiter =
                RateLimiter.create(calculatePermitsPerSecond(permitsPerTimeFrame, timeFrameMillis));
        this.queryTimeRateLimiter = RateLimiter.create(calculatePermitsPerSecond(timeQuota, timeFrameMillis));
    }

    /**
     * Converting the values to permits per second for the rate limiter.
     * See {@link RateLimiter#create(double)} for more information.
     */
    private int calculatePermitsPerSecond(int permitsPerTimeFrame, int timeFrameMillis) {
        // Converting to long in order to avoid overflow in case permitsPerTimeFrame is Integer.MAX_VALUE :)
        int permitsPerSecond = (int) (1000 * (long) permitsPerTimeFrame / Math.max(1, timeFrameMillis));
        log.debug("{} query rate limiter: Converting {}/{} to {} permits per second",
                qrlType, permitsPerTimeFrame, timeFrameMillis, permitsPerSecond);
        return permitsPerSecond;
    }

    /**
     * Check if there is an available permit. If not, try to acquire the permits from the rate limiter object.
     */
    @Override
    public synchronized void acquirePermit() {
        if (availablePermits > 0) {
            availablePermits--;
        } else {
            acquireCountPermitInternal();
            availablePermits = permitsPerTimeFrame - 1;
        }
        if (availableTimeQuota.get() <= 0) {
            acquireTimePermitInternal();
            availableTimeQuota.set(timeQuota);
        }
        qrlMetrics.incrementTotalQueries();
    }

    @Override
    public void updateExecutionTime(int queryTime) {
        final int executionTime = queryTime < 0 ? timeFrameMillis : queryTime;
        int chargingTime = Math.min(executionTime, timeFrameMillis);
        availableTimeQuota.addAndGet(chargingTime * -1);
        qrlMetrics.addTotalChargedQueryTime(chargingTime);
    }

    protected void acquireCountPermitInternal() {
        double acquireTime = queryCountRateLimiter.acquire(permitsPerTimeFrame);
        qrlMetrics.addTotalSlowedDownMillis((long) (1000 * acquireTime));
    }

    protected void acquireTimePermitInternal() {
        double acquireTime = queryTimeRateLimiter.acquire(timeQuota);
        qrlMetrics.addTotalSlowedDownByTimeMillis((long) (1000 * acquireTime));
    }

    public QueryRateLimiterMetrics getMetrics() {
        return qrlMetrics;
    }
}
