package org.artifactory.event.work;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.event.error.model.ErrorStatistics;

/**
 * @author Mor Merhav
 * @author avishayh
 */
@Slf4j
public abstract class AbstractNodeEventsErrorsBackOffStrategy implements NodeEventsErrorsBackOffStrategy {

    /**
     * Indicate whether error is being retried too frequently and should have backoff delay before next
     * retry attempt. Days elapsed since the error initial creation is compared to a time defined by the backoff
     * strategy.
     * Backoff strategy:
     * First attempt (retryCount == 0) is always allowed. From the 2nd attempt onward we use backoff() to calculate
     */
    public final boolean isBackoffDelayRequired(ErrorStatistics error, long currentTime) {
        int errorCount = error.getErrorCount();
        if (errorCount == 0) {
            log.trace("Backoff delay is not required since error is 0");
            return false;
        }
        long errorCreatedTime = error.getFirstErrorTime();
        long millisSinceErrorCreated = currentTime - errorCreatedTime;
        log.trace("Error created time: {}, current: {}, age: {}", errorCreatedTime, currentTime,
                millisSinceErrorCreated);
        return millisSinceErrorCreated < backoff(errorCount);
    }

    protected abstract long backoff(int errorCount);
}
