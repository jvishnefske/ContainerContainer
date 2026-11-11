package org.artifactory.storage.db.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.spring.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Slf4j
@Service
@ConditionalOnProperty(name = "artifactory.query-monitor.large-results.enabled", havingValue = "true")
public class LargeResultSetsMonitor implements ResultSetSizeMonitor {
    private final Meter.MeterProvider<Counter> largeResultSetsCounter;
    @Getter
    @Setter(onMethod_ = {@Value("${artifactory.query-monitor.large-results.rows-threshold:100000}")})
    private long minRowsCountToReport = 100_000;
    @Getter
    @Setter(onMethod_ = {@Value("${artifactory.query-monitor.large-results.min-log-interval:300000}")})
    private long minLogIntervalMsec = 300_000; // Log at most one query in 5 minutes
    @Getter
    @Setter
    LongSupplier timeSupplier = System::currentTimeMillis;
    protected final ConcurrentMap<String, Long> lastReportTimePerAction = new ConcurrentHashMap<>();

    public LargeResultSetsMonitor(MeterRegistry metrics) {
        this.largeResultSetsCounter = Counter.builder("db_query_large")
                .description("Number of large result sets per source activity")
                .withRegistry(metrics);
        // Note: we may also consider using a MultiGauge to report the largest result set per activity
    }

    @Override
    public boolean shouldMonitor(String query, boolean isStreaming, String hint) {
        // In the future we may want to exclude queries from monitoring based on
        // the current activity (e.g. 'do not monitor full GC queries') or based
        // on the SQL text, like ('monitor only SELECT queries').
        return !isStreaming;
    }

    @Override
    public void onQueryFetchCompleted(DbQueryContext queryContext, long rowsCount, long fetchDurationNanos) {
        if (rowsCount >= this.minRowsCountToReport) {
            reportLargeResultSet(queryContext, rowsCount, fetchDurationNanos);
        }
        // Note: in the future we may want to report SELECT queries that returned no rows
    }

    protected void reportLargeResultSet(DbQueryContext queryContext, long rowsCount, long fetchDurationNanos) {
        String actionName = StringUtils.defaultIfEmpty(queryContext.getActionName(), "");

        // Report metrics
        this.largeResultSetsCounter.withTags(
                        "action", actionName,
                        "group", StringUtils.defaultIfEmpty(queryContext.getActionGroup(), ""),
                        "package", StringUtils.defaultIfEmpty(queryContext.getActionPackage(), "")
                )
                .increment();

        // Log the large result set, throttling the logging to avoid flooding the logs
        long timeNow = this.timeSupplier.getAsLong();
        if (this.lastReportTimePerAction.merge(actionName, timeNow, this::throttleLogging) == timeNow) {
            // Only log if the throttling condition is met
            log.warn("Large result set: query='{}', rows={}, action={} ({}/{}, package={}, actor={}), hint={}, " +
                     "fetch-ms={}",
                    queryContext.getQuery(), rowsCount,
                    queryContext.getActionName(),
                    queryContext.getActionKind(), queryContext.getActionGroup(),
                    queryContext.getActionPackage(), queryContext.getActionActor(),
                    queryContext.getHint(),
                    TimeUnit.NANOSECONDS.toMillis(fetchDurationNanos)
            );
        }
    }

    protected Long throttleLogging(long previousLogTime, long timeNow) {
        return timeNow - previousLogTime < this.minLogIntervalMsec ? previousLogTime : timeNow;
    }
}
