package org.artifactory.metadata;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.event.work.AbstractNodeEventsErrorsBackOffStrategy;

import static java.util.concurrent.TimeUnit.HOURS;

/**
 * @author Mor Merhav
 */
@Slf4j
public class MetadataBackOffStrategy extends AbstractNodeEventsErrorsBackOffStrategy {

    private static final long MAX_HOURS_TO_WAIT = HOURS.toMillis(30);

    /**
     * backoff time is equal to the retry count * 6, up to a max of 30 hours
     * retryCount   #  0   1   2    3    4    5  ...
     * time (Hours) #  0   6   12   24   30   30 ...
     */
    protected long backoff(int retryCount) {
        long daysToWaitBeforeNextErrorRetry = HOURS.toMillis(retryCount * 6L);
        long backoff = Math.min(daysToWaitBeforeNextErrorRetry, MAX_HOURS_TO_WAIT);
        log.trace("Wait before next error retry: {}, backoff: {}", daysToWaitBeforeNextErrorRetry, backoff);
        return backoff;
    }

}
