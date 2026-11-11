package org.artifactory.queue.error;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.event.work.AbstractNodeEventsErrorsBackOffStrategy;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.jfrog.common.ArgUtils.requireNonNegative;
import static org.jfrog.common.ArgUtils.requirePositive;

/**
 * @author avishayh
 */
@Slf4j
public class PersistentQueueBackoffStrategy extends AbstractNodeEventsErrorsBackOffStrategy {

    private final int factor;
    private final long maxTimeToWait;

    public PersistentQueueBackoffStrategy(int factor, int maxHoursToWait) {
        requirePositive(factor, "factor must be positive");
        requireNonNegative(maxHoursToWait, "maxHoursToWait must be non-negative");
        this.factor = factor;
        this.maxTimeToWait = HOURS.toMillis(maxHoursToWait);
    }

    /**
     * calc by minimum (factor(minutes)^retry or maxTime)
     *
     * for multipleFactor = 4 minutes and maxHoursToWait = 30H
     * retryCount    #  1    2    3         4            5            6    7   ...
     * time(Minutes) #  4m   16m  64m(~1h)  256m(~4.5h)  1024m(~17h)  30h  30h ...
     * @return backoff time in ms return
     */
    public long backoff(int retryCount) {
        long waitBeforeNextErrorRetry = MINUTES.toMillis((long) Math.pow(factor, retryCount));
        long backoff = Math.min(waitBeforeNextErrorRetry, maxTimeToWait);
        log.trace("Wait before next error retry: {}, backoff: {}", waitBeforeNextErrorRetry, backoff);
        return backoff;
    }

}
