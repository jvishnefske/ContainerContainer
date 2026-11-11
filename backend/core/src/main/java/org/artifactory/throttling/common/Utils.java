package org.artifactory.throttling.common;

import com.google.common.util.concurrent.RateLimiter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.throtlling.common.enums.IRateLimiterType;

@Slf4j
@UtilityClass
public class Utils {

    public static final int MONITORING_UNLIMITED_PERMITS = Integer.MAX_VALUE;
    public static final int MONITORING_TIMEFRAME_MILLIS = 60_000;

    /**
     * Converting the values to permits per second for the rate limiter.
     * See {@link RateLimiter#create(double)} for more information.
     */
    public static int calculatePermitsPerSecond(IRateLimiterType rlType, int permitsPerTimeFrame, int timeFrameMillis) {
        // Converting to long in order to avoid overflow in case permitsPerTimeFrame is Integer.Max_VALUE :)
        int permitsPerSecond = (int) (1000 * (long) permitsPerTimeFrame / Math.max(1, timeFrameMillis));
        log.debug("{} query rate limiter: Converting {}/{} to {} permits per second",
                rlType, permitsPerTimeFrame, timeFrameMillis, permitsPerSecond);

        return permitsPerSecond;
    }

    public static int calculatePermitsPerSecond(int permitsPerTimeFrame, int timeFrameMillis) {
        return (int) (1000 * (long) permitsPerTimeFrame / Math.max(1, timeFrameMillis));
    }
}