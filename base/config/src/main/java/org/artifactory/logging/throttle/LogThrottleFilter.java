package org.artifactory.logging.throttle;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Marker;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author Paz Nahmias
 */
public class LogThrottleFilter extends TurboFilter {

    protected final Clock clock;

    protected final AtomicLong throttleTimeFrame = new AtomicLong(0);

    protected Map<String, Long> logThrottleCache = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private long expiryTimeFrameMillis = 3_600_000;
    @Getter
    @Setter
    private int throttleTopLimit = 100;
    @Getter
    @Setter
    private int cacheSize = 500;

    public LogThrottleFilter() {
        this(Clock.systemUTC());
    }

    public LogThrottleFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void start() {
        logThrottleCache = new ConcurrentHashMap<>();
        super.start();
    }

    @Override
    public void stop() {
        logThrottleCache.clear();
        logThrottleCache = null;
        super.stop();
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String s, Object[] objects,
            Throwable throwable) {
        if (!isValidMarkerForThrottling(marker)) {
            return FilterReply.NEUTRAL;
        }

        String throttleKey = logger.getName();
        long currentTime = this.clock.millis();

        if (this.throttleTimeFrame.get() <= currentTime) {
            // we start a new time frame
            this.throttleTimeFrame.set(currentTime + this.expiryTimeFrameMillis);
            this.logThrottleCache.clear();
            this.logThrottleCache.put(throttleKey, 1L);

            return FilterReply.NEUTRAL;
        }

        // we are still in the current time frame and should keep incrementing counter
        long throttleCounter = this.logThrottleCache.compute(throttleKey, (key, old) -> old == null ? 1 : old + 1);
        boolean shouldThrottleLog = throttleCounter > this.throttleTopLimit;

        return shouldThrottleLog ? FilterReply.DENY : FilterReply.NEUTRAL;
    }

    private boolean isValidMarkerForThrottling(Marker marker) {
        return marker != null &&
                (
                        LogThrottleMarker.getInstance().equals(marker) // this is the log throttle marker
                                || marker.contains(
                                LogThrottleMarker.getInstance()) // log throttle marker is a child of this marker
                );
    }
}
