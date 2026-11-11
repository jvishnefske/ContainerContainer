package org.artifactory.metrics.framework.providers;

import jakarta.annotation.Nonnull;
import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.metric.MetricsDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SlowQueriesMetricsProviderTest {

    SlowQueriesMetricsProvider slowQueryMetricsProvider;

    @Test
    void getName() {
        slowQueryMetricsProvider = new SlowQueriesMetricsProvider(null);
        assertEquals("jfrt_slow_queries", slowQueryMetricsProvider.getName());
    }

    @Test
    void getDescription() {
        slowQueryMetricsProvider = new SlowQueriesMetricsProvider(null);
        assertEquals("Artifactory Slow Queries", slowQueryMetricsProvider.getDescription());
    }

    @Test
    void getRefreshIntervalMillis() {
        slowQueryMetricsProvider = new SlowQueriesMetricsProvider(null);
        assertEquals(0L, slowQueryMetricsProvider.getRefreshIntervalMillis());
    }

    @Test
    void collectNonMetricsDataSource() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        slowQueryMetricsProvider = new SlowQueriesMetricsProvider(jdbcHelper);
        ArtifactoryDataSource ds = mock(ArtifactoryDataSource.class);
        when(ds.isWrapperFor(ArtifactoryDataSource.class)).thenReturn(true);
        when(ds.unwrap(ArtifactoryDataSource.class)).thenReturn(ds);
        when(jdbcHelper.getDataSource()).thenReturn(ds);

        List<Metric> metricList = slowQueryMetricsProvider.collect();
        assertEquals(0, metricList.size());
    }

    @Test
    void collectMetricsDataSourceDB() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        slowQueryMetricsProvider = new SlowQueriesMetricsProvider(jdbcHelper);
        JFrogDataSource ds = mock(JFrogDataSource.class);
        MetricsDataSource metricsDataSource = createMockDataSource();
        when(ds.isWrapperFor(MetricsDataSource.class)).thenReturn(true);
        when(ds.unwrap(MetricsDataSource.class)).thenReturn(metricsDataSource);
        when(jdbcHelper.getDataSource()).thenReturn(ds);

        metricsDataSource.getConnection().prepareStatement("select 1").execute();
        metricsDataSource.getConnection().prepareStatement("select 1").execute();
        metricsDataSource.getConnection().prepareStatement("select 2").execute();

        List<Metric> metricList = slowQueryMetricsProvider.collect();
        assertEquals(4, metricList.size());
    }

    @Nonnull
    private MetricsDataSource createMockDataSource() throws SQLException {
        DataSource mockDataSource = Mockito.mock(DataSource.class);
        Connection mockConnection = Mockito.mock(Connection.class);
        PreparedStatement mockStatement = Mockito.mock(PreparedStatement.class);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(any())).thenReturn(mockStatement);
        return new MetricsDataSource(mockDataSource);
    }

    @Test
    void getReportType() {
        slowQueryMetricsProvider = new SlowQueriesMetricsProvider(null);
        assertEquals(MetricProvider.ReportType.CONTINUOUS, slowQueryMetricsProvider.getReportType());
    }

    @Test
    void getLastUpdatedMillis() {
        slowQueryMetricsProvider = new SlowQueriesMetricsProvider(null);
        assertEquals(0L, slowQueryMetricsProvider.getLastUpdatedMillis());
    }
}