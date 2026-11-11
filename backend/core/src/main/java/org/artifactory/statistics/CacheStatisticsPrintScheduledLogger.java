package org.artifactory.statistics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.common.ClockUtils;

/**
 * @author barh
 */
@Slf4j
public class CacheStatisticsPrintScheduledLogger {
    private final String cacheName;
    private final long interval;
    private static final String LOG_TEMPLATE =
            "{} - {} stats: total requests: {}, total hits: {}, total misses: {}, eviction count: {}, hit rate: {}.";
    private long lastPrintTime;
    private CacheStats previous;

    public CacheStatisticsPrintScheduledLogger(String cacheName, long interval) {
        this.cacheName = cacheName;
        this.interval = interval;
        lastPrintTime = 0;
        previous = CacheStats.empty();
    }

    public void logStats(Cache<String, ?> cache) {
        if (!log.isDebugEnabled()) {
            return;
        }
        long currentTime = ClockUtils.epochMillis();
        if (lastPrintTime + interval < currentTime) {
            CacheStats current = cache.stats();
            CacheStats gap = current.minus(previous);

            this.writeToLog(gap, "gap");
            this.writeToLog(current, "total");

            previous = current;
            lastPrintTime = currentTime;
        }
    }

    private void writeToLog(CacheStats stats, String alias) {
        log.debug(
                LOG_TEMPLATE,
                cacheName,
                alias,
                stats.requestCount(), stats.hitCount(), stats.missCount(), stats.evictionCount(),
                stats.hitRate());
    }
}
