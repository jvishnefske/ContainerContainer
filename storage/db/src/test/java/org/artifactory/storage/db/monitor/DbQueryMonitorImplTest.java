package org.artifactory.storage.db.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbQueryMonitorImplTest {
    @BeforeEach
    void beforeEachTest() {
        MDC.clear(); // Just to be sure no other test dirtied MDC before us
    }

    @AfterEach
    void afterEachTest() {
        MDC.clear();
    }

    @Test
    void extractDatabaseClusterFromPostgresConnectionUrl_shouldExtractClusterName() {
        String actual = DbQueryMonitorImpl.extractDatabaseClusterFromPostgresConnectionUrl(
                "jdbc:postgresql://some-cluster.postgres.db.azure.com:5432/customer_ja?ssl=true");
        assertEquals("some-cluster.postgres.db.azure.com", actual);
    }

    @Test
    void extractDatabaseClusterFromPostgresConnectionUrl_shouldExtractClusterNameWhenPortIsDefault() {
        String actual = DbQueryMonitorImpl.extractDatabaseClusterFromPostgresConnectionUrl(
                "jdbc:postgresql://some-cluster.postgres.db.azure.com/customer_ja?ssl=true");
        assertEquals("some-cluster.postgres.db.azure.com", actual);
    }

    @Test
    void extractDatabaseNameFromPostgresConnectionUrl_shouldGetDbName() {
        String actual = DbQueryMonitorImpl.extractDatabaseNameFromPostgresConnectionUrl(
                "jdbc:postgresql://some-cluster.postgres.db.azure.com:5432/customer_art");
        assertEquals("customer_art", actual);
    }

    @Test
    void extractDatabaseNameFromPostgresConnectionUrl_shouldIgnoreQueryParams() {
        String actual = DbQueryMonitorImpl.extractDatabaseNameFromPostgresConnectionUrl(
                "jdbc:postgresql://some-cluster.postgres.db.azure.com:5432/customer_art?ssl=true");
        assertEquals("customer_art", actual);
    }

    @Test
    void queryFinished_shouldNotReportMetricsIfDisabled() {
        MeterRegistry metrics = new SimpleMeterRegistry();
        DbQueryMonitor monitor = new DbQueryMonitorImpl(metrics, null, false,
                1000, 100, true, false);

        monitor.queryFinished("SELECT 1", null, 1000_000_000L, null);
        Collection<?> meters = metrics.find("db_query_duration").meters();
        assertTrue(meters.isEmpty(), "DbQueryMonitor should not report metrics when disabled");

        // We should not report db_query_by_trigger despite the fact we said metricsByOriginEnabled=true
        meters = metrics.find("db_query_by_trigger").meters();
        assertTrue(meters.isEmpty(), "DbQueryMonitor disabled, should not report db_query_by_trigger metric");
    }

    @Test
    void queryFinished_shouldAllowToDisableMetricsByOrigin() {
        MeterRegistry metrics = new SimpleMeterRegistry();
        DbQueryMonitor monitor = new DbQueryMonitorImpl(metrics, null, true,
                1000, 100, false, false);

        monitor.queryFinished("SELECT 1", null, 1000_000_000L, null);

        Collection<?> meters = metrics.find("db_query_duration").meters();
        assertEquals(1, meters.size(), "DB metrics enabled, should have db_query_duration metric");

        meters = metrics.find("db_query_by_trigger").meters();
        assertTrue(meters.isEmpty(), "Metrics by trigger disabled, should not have db_query_by_trigger metric");
    }

    @Test
    void queryFinished_shouldReportQueryAsFast_whenDurationIsShort() {
        MeterRegistry metrics = new SimpleMeterRegistry();
        DbQueryMonitor monitor = new DbQueryMonitorImpl(metrics, null, true,
                1000, 100, true, false);

        monitor.queryFinished("SELECT * from nodes LIMIT 1", null, 1000L, null);
        Timer actual = metrics.find("db_query_duration").tags(
                "type", "SELECT",
                "tables", "nodes",
                "slow", "n",
                "cluster", "n/a"
        ).timer();
        assertNotNull(actual,
                "Cannot find db_query_duration{type=SELECT,tables=nodes,slow=n,cluster=n/a} metric");
        assertEquals(1, actual.count());
        assertEquals(1000, actual.totalTime(TimeUnit.NANOSECONDS));
    }

    @Test
    void queryFinished_shouldReportQueryAsSlow_whenDurationIsLong() {
        MeterRegistry metrics = new SimpleMeterRegistry();
        DbQueryMonitor monitor = new DbQueryMonitorImpl(metrics, null, true,
                1000, 100, true, false);

        monitor.queryFinished("DELETE from builds", null, 1000000_000000L, null);
        Timer actual = metrics.find("db_query_duration").tags(
                "type", "DELETE",
                "tables", "builds",
                "slow", "y",
                "cluster", "n/a"
        ).timer();
        assertNotNull(actual,
                "Cannot find db_query_duration{type=DELETE,tables=builds,slow=y,cluster=n/a} metric");
        assertEquals(1, actual.count());
        assertEquals(1000.0, actual.totalTime(TimeUnit.SECONDS));
    }

    @Test
    void queryFinished_shouldExtractPostgresDatabaseClusterAndComputeHash() {
        ArtifactoryDbProperties config = mock(ArtifactoryDbProperties.class);
        when(config.getDbType()).thenReturn(DbType.POSTGRESQL);
        when(config.getConnectionUrl()).thenReturn("jdbc:postgresql://localhost:5432/customer_art?ssl=true");

        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        DbQueryMonitor monitor = new DbQueryMonitorImpl(metrics,
                config, true, 1000, 100, true, false);

        monitor.queryFinished("SELECT 1; -- comment", null, 1, null);
        Timer actual = metrics.find("db_query_duration").tags(
                "type", "SELECT",
                "tables", "",
                "slow", "n",
                "cluster", "b8332793"
        ).timer();
        assertNotNull(actual,
                "Cannot find db_query_duration{type=SELECT,tables=,slow=n,cluster=b8332793} metric");

        Gauge clusterInfoGauge = metrics.get("db_cluster").tags("name", "localhost", "hash", "b8332793",
                "db_name", "customer_art").gauge();
        assertNotNull(clusterInfoGauge,
                "Cannot find db_cluster{name=localhost, hash=b8332793, db_name=customer_art} gauge");
    }

    @Test
    void queryFinished_shouldNotReportMetricsByOriginIfDisabled() {
        MeterRegistry metrics = new SimpleMeterRegistry();
        DbQueryMonitor monitor = new DbQueryMonitorImpl(metrics, null, false,
                1000, 100, true, false);

        monitor.queryFinished("SELECT 1", null, 1000_000_000L, null);
        Collection<?> meters = metrics.find("db_query_duration").meters();
        assertTrue(meters.isEmpty(), "DbQueryMonitor should not report metrics when disabled");
    }

    @ParameterizedTest
    @CsvSource({
            ",,,''",
            "http/get,,npm,http/get",
            "http/get,artifact,docker,http/get/artifact"
    })
    void checkMetricsByTrigger(String kind, String group, String packageType, String expectedTrigger) {
        ArtifactoryDbProperties config = mock(ArtifactoryDbProperties.class);
        when(config.getDbType()).thenReturn(DbType.POSTGRESQL);
        when(config.getConnectionUrl()).thenReturn("jdbc:postgresql://localhost:5432/customer_ja");

        MeterRegistry metrics = new SimpleMeterRegistry();
        DbQueryMonitor monitor = new DbQueryMonitorImpl(metrics, config, true,
                1000, 100, true, false);

        ActivityContext activity = CurrentActivity.context();
        activity
                .putKind(kind)
                .putGroup(group)
                .putPackageType(packageType);

        monitor.queryFinished("SELECT * from nodes LIMIT 1", null, 1000L, null);
        Timer actual = metrics.find("db_query_by_trigger").tags(
                "trigger", expectedTrigger,
                "pkg", packageType == null ? "none" : packageType,
                "cluster", "b8332793"
        ).timer();
        assertNotNull(actual,
                "Cannot find db_query_by_trigger metric with correct labels");
        assertEquals(1, actual.count());
        assertEquals(1000, actual.totalTime(TimeUnit.NANOSECONDS));
    }
}