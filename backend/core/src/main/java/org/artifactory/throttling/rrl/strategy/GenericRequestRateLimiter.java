package org.artifactory.throttling.rrl.strategy;

import com.google.common.util.concurrent.RateLimiter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.download.RateLimiterException;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.artifactory.common.ConstantValues.rrlGenericIsBlockingModeEnabled;
import static org.artifactory.throttling.common.Utils.MONITORING_UNLIMITED_PERMITS;
import static org.artifactory.throttling.common.Utils.calculatePermitsPerSecond;

/**
 * @author Igor Gumush
 * @author Omri Cohen
 */
@Slf4j
public class GenericRequestRateLimiter implements InternalRequestRateLimiter {
    final RateLimiter rateLimiter;
    final String domainSuffix;
    final String name;
    final int permitsPerTimeFrame;
    final boolean isTrackingOnly;
    int availablePermits;
    boolean includeHead;
    final AtomicLong rateLimitedRequestsCounter = new AtomicLong(0);
    final AtomicLong totalUnblockedOutgoingRequestsCounter = new AtomicLong(0);

    GenericRequestRateLimiter(String name, String domainSuffix, int permitsPerTimeFrame, int timeFrameMillis,
            boolean includeHead) {
        this.name = name;
        this.domainSuffix = domainSuffix;
        this.permitsPerTimeFrame = permitsPerTimeFrame;
        this.includeHead = includeHead;
        this.isTrackingOnly = permitsPerTimeFrame >= MONITORING_UNLIMITED_PERMITS;
        this.rateLimiter = isTrackingOnly ? null
                : RateLimiter.create(Math.max(calculatePermitsPerSecond(permitsPerTimeFrame, timeFrameMillis), 1));
    }

    public static GenericRequestRateLimiter create(@NonNull String name, @NonNull String domainSuffix,
            int permitsPerTimeFrame,
            int timeFrameMillis, boolean includeHead) {
        return new GenericRequestRateLimiter(name, domainSuffix, permitsPerTimeFrame, timeFrameMillis, includeHead);
    }

    /**
     * Check if there is an available permit.
     * If not, try to acquire the permits from the rate limiter object.
     */
    @Override
    public synchronized void acquirePermit() throws RateLimiterException {
        if (!isTrackingOnly) {
            if (availablePermits > 0) {
                availablePermits--;
                log.trace("Counter for {} is: {}", this.name, availablePermits);
            } else {
                log.trace("Updating counter for: {}", this.name);
                boolean acquired = acquirePermitInternal();
                if (acquired) {
                    log.trace("Counter updated for: {}", this.name);
                    availablePermits = permitsPerTimeFrame - 1;
                }
            }
        }
        /*
          If acquirePermitInternal() throws RateLimiterException we never reach here.
          For tracking-only domains the rate limiting logic is skipped entirely.
         */
        totalUnblockedOutgoingRequestsCounter.incrementAndGet();
    }

    private boolean acquirePermitInternal() throws RateLimiterException {
        boolean acquired = rateLimiter.tryAcquire(permitsPerTimeFrame, 1, NANOSECONDS);
        if (acquired) {
            return true;
        }

        rateLimitedRequestsCounter.incrementAndGet();
        if (rrlGenericIsBlockingModeEnabled.getBoolean()) {
            throw new RateLimiterException("Request rate limiter time exceeded");
        }
        return false;
    }

    @Override
    public Map<String, Long> getMetrics() {
        return Map.of("Drop Count", rateLimitedRequestsCounter.getAndSet(0),
                "Total Count", totalUnblockedOutgoingRequestsCounter.getAndSet(0));
    }

    @Override
    public boolean shouldCountHeadRequest() {
        return includeHead;
    }
}
