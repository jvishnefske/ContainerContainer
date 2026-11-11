package org.artifactory.storage.db.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.metrics.cache.GlobalCacheMetrics;
import org.artifactory.storage.db.util.SqlParseHelper;
import org.jfrog.storage.DbType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DbQueryMonitorImpl implements DbQueryMonitor {

    private final MeterRegistry metrics;
    private final Cache<String, QueryDetails> caffeineQueriesCache;
    private final com.google.common.cache.Cache<String, QueryDetails> guavaQueriesCache;
    private final boolean useCaffeine;
    @Getter @Setter
    private long slowQueryThreshold;
    @Getter @Setter
    private boolean reportMetricsEnabled;
    @Getter @Setter
    private String databaseCluster;
    @Getter @Setter
    private String databaseClusterHash;
    @Getter @Setter
    private String databaseName;
    @Getter @Setter
    private boolean metricsByTriggerEnabled;

    public DbQueryMonitorImpl(MeterRegistry metrics,
            ArtifactoryDbProperties databaseConfig,
            @Value("""
            #{('${artifactory.metrics.enabled:false}' == 'true') &&
            ('${artifactory.query-monitor.metrics.enabled:true}' == 'true')}
            """) boolean reportMetricsEnabled,
            @Value("${artifactory.query-monitor.cache.maxEntries:10000}") long queryCacheMaxEntries,
            @Value("${jfrog.slow.queries.threshold.ms:10000}") long slowQueryThresholdMs,
            @Value("${artifactory.query-monitor.metrics.byTrigger:true}") boolean metricsByTriggerEnabled,
            @Value("${artifactory.cache.caffeine.enabled:false}") boolean useCaffeine) {
        this.metrics = metrics;
        this.useCaffeine = useCaffeine;
        if (useCaffeine) {
            log.info("Using Caffeine cache for DB Query Monitor");
            this.caffeineQueriesCache = Caffeine.newBuilder()
                    .maximumSize(queryCacheMaxEntries)
                    .recordStats()
                    .build();
            this.guavaQueriesCache = null;
            GlobalCacheMetrics.monitorCaffeine(caffeineQueriesCache, "db-query-monitor", this.getClass());
        } else {
            log.info("Using Guava cache for DB Query Monitor");
            this.caffeineQueriesCache = null;
            this.guavaQueriesCache = CacheBuilder.newBuilder()
                    .maximumSize(queryCacheMaxEntries)
                    .recordStats()
                    .build();
            GlobalCacheMetrics.monitorGuava(guavaQueriesCache, "db-query-monitor", this.getClass());
        }

        this.reportMetricsEnabled = reportMetricsEnabled;
        this.metricsByTriggerEnabled = metricsByTriggerEnabled;
        this.slowQueryThreshold = slowQueryThresholdMs * 1000000L;

        // Resolve the database cluster name and then compute its hash to use in metrics
        updateDatabaseCluster(databaseConfig);
        Gauge.builder("db_cluster", () -> 1)
                .description("Database context information")
                .tags(
                        "name", this.databaseCluster,
                        "hash", this.databaseClusterHash,
                        "db_name", this.databaseName
                )
                .register(metrics);
    }

    private void updateDatabaseCluster(ArtifactoryDbProperties databaseConfig) {
        this.databaseCluster = "n/a";
        this.databaseClusterHash = "n/a";
        this.databaseName = "n/a";

        if (databaseConfig != null && databaseConfig.getDbType() == DbType.POSTGRESQL) {
            String cluster = extractDatabaseClusterFromPostgresConnectionUrl(databaseConfig.getConnectionUrl());
            if (!StringUtils.isEmpty(cluster)) {
                this.databaseCluster = cluster.toLowerCase();
                this.databaseClusterHash = Integer.toHexString(this.databaseCluster.hashCode());
            }

            this.databaseName = extractDatabaseNameFromPostgresConnectionUrl(databaseConfig.getConnectionUrl());
        }

        log.info("Database cluster name={}, hash={}, db_name={}",
                this.databaseCluster, this.databaseClusterHash, this.databaseName);
    }

    public static String extractDatabaseClusterFromPostgresConnectionUrl(String url) {
        return StringUtils.substringBefore(
                StringUtils.substringBetween(url, "://", "/"), ":");
    }

    public static String extractDatabaseNameFromPostgresConnectionUrl(String url) {
        return StringUtils.substringBefore(StringUtils.substringAfterLast(url, "/"), "?");
    }

    @Override
    public void queryFinished(String query, Object[] params, long durationNano, Exception error) {
        if (this.reportMetricsEnabled) {
            reportFinishedQueryToMetrics(query, durationNano);
        }
    }

    private void reportFinishedQueryToMetrics(String query, long durationNano) {
        QueryDetails details = getQueryDetails(query);
        boolean queryIsSlow = this.slowQueryThreshold > 0 && durationNano > this.slowQueryThreshold;

        Timer.builder("db_query_duration")
                .description("Time spent executing DB queries")
                .tags(
                        "type", StringUtils.defaultIfEmpty(details.type, ""),
                        "tables", StringUtils.defaultIfEmpty(details.tableNames, ""),
                        "slow", queryIsSlow ? "y" : "n",
                        "cluster", this.databaseClusterHash
                )
                .register(this.metrics)
                .record(durationNano, TimeUnit.NANOSECONDS);

        // Aggregate DB query time by the originating activity:
        if (this.metricsByTriggerEnabled) {
            ActivityContext activity = CurrentActivity.context();

            Timer.builder("db_query_by_trigger")
                    .description("Time spent executing DB queries - aggregated by source activity")
                    .tags(
                            "trigger", formatDbQueryTriggerType(activity),
                            "pkg", activity.getPackageType("none").toLowerCase(),
                            "cluster", this.databaseClusterHash
                    )
                    .register(this.metrics)
                    .record(durationNano, TimeUnit.NANOSECONDS);
        }
    }

    private String formatDbQueryTriggerType(ActivityContext activity) {
        String group = activity.getGroup();
        if (group == null) {
            return activity.getKind("");
        }
        return activity.getKind("") + "/" + activity.getGroup();
    }

    @SneakyThrows
    private QueryDetails getQueryDetails(String query) {
        if (useCaffeine) {
            return caffeineQueriesCache.get(query, this::parseQuery);
        } else {
            return guavaQueriesCache.get(query, () -> parseQuery(query));
        }
    }

    private QueryDetails parseQuery(String query) {
        String normalizedQuery = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces(query);
        String queryType = SqlParseHelper.getSqlStatementType(normalizedQuery);
        Set<String> tableNames = SqlParseHelper.extractTableNames(normalizedQuery, queryType);
        return new QueryDetails(queryType, tableNames.stream().sorted().collect(Collectors.joining(",")));
    }

    private record QueryDetails(String type, String tableNames) {
    }
}
