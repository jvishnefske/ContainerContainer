package org.artifactory.util;

import lombok.extern.slf4j.Slf4j;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class ExponentialBackoff {

    /**
     * retryCount        #  0   1   2   3   4   5   6   7   8   9   10...
     * Min time (minutes)#  0   0  0.5  1   2   4   8   16  32  64  64...
     */
    public static long backoff(int retryCount) {
        long backoffBase = SECONDS.toMillis(30);
        long maxBackoff = MINUTES.toMillis(64);
        if (retryCount <= 1) {
            return 0;
        }
        log.debug("Execution backoff with retrycCount: {}", retryCount);
        long waitingTime = (long) (backoffBase * Math.pow(2, Math.max(0, retryCount - 2)));
        log.debug("Calculated backOff: {}, maxBackoff: {}", waitingTime, maxBackoff);
        return Math.min(waitingTime, maxBackoff);
    }
}
