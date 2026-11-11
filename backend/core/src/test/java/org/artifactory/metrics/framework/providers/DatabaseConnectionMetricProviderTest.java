package org.artifactory.metrics.framework.providers;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.metrics.model.Metric;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.metric.MetricsDataSource;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DatabaseConnectionMetricProviderTest {

    @Test
    public void testGetName() {
        DatabaseConnectionMetricProvider dbProvider =
                new DatabaseConnectionMetricProvider(null);
        assertEquals("jfrt_db_connections", dbProvider.getName());
    }

    @Test
    public void testGetDescription() {
        DatabaseConnectionMetricProvider dbProvider =
                new DatabaseConnectionMetricProvider(null);
        assertEquals("Artifactory Database Connection Metrics Provider",
                dbProvider.getDescription());
    }

    @Test
    public void testGetRefreshIntervalMillis() {
        DatabaseConnectionMetricProvider dbProvider =
                new DatabaseConnectionMetricProvider(null);
        assertEquals(60000, dbProvider.getRefreshIntervalMillis());
    }

    @Test
    public void testCollectDBMetric() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);

        ArtifactoryDataSource ds = mock(ArtifactoryDataSource.class);
        when(ds.isWrapperFor(ArtifactoryDataSource.class)).thenReturn(true);
        when(ds.unwrap(ArtifactoryDataSource.class)).thenReturn(ds);
        when(jdbcHelper.getDataSource()).thenReturn(ds);
        when(ds.getActiveConnectionsCount()).thenReturn(10);
        when(ds.getIdleConnectionsCount()).thenReturn(10);
        when(ds.getMaxActive()).thenReturn(100);
        when(ds.getMaxIdle()).thenReturn(10);
        when(ds.getMinIdle()).thenReturn(10);
        DatabaseConnectionMetricProvider dbProvider =
                new DatabaseConnectionMetricProvider(jdbcHelper);
        List<Metric> metrics = dbProvider.collect();

        assertEquals(metrics.get(0).getName(), "active");
        assertEquals(metrics.get(0).getValue().doubleValue(), 10.0, 0.1);
        assertEquals(metrics.get(1).getName(), "idle");
        assertEquals(metrics.get(1).getValue().doubleValue(), 10.0, 0.1);
        assertEquals(metrics.get(2).getName(), "max_active");
        assertEquals(metrics.get(2).getValue().doubleValue(), 100.0, 0.1);
        assertEquals(metrics.get(3).getName(), "min_idle");
        assertEquals(metrics.get(3).getValue().doubleValue(), 10.0, 0.1);

    }
    @Test
    public void testCollectDBMetricMetricsDataSource() throws SQLException {
        String[] expectedMetrics = new String[] {"active", "idle", "max_active", "min_idle"};
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        JFrogDataSource jFrogDataSource = mock(JFrogDataSource.class);
        MetricsDataSource metricsDataSource = createMockDataSource();
        when(jFrogDataSource.isWrapperFor(MetricsDataSource.class)).thenReturn(true);
        when(jFrogDataSource.unwrap(MetricsDataSource.class)).thenReturn(metricsDataSource);
        when(jdbcHelper.getDataSource()).thenReturn(jFrogDataSource);

        ArtifactoryDataSource artifactoryDataSource = mock(ArtifactoryDataSource.class);
        when(metricsDataSource.isWrapperFor(ArtifactoryDataSource.class)).thenReturn(true);
        when(metricsDataSource.unwrap(ArtifactoryDataSource.class)).thenReturn(artifactoryDataSource);
        when(artifactoryDataSource.getActiveConnectionsCount()).thenReturn(10);
        when(artifactoryDataSource.getIdleConnectionsCount()).thenReturn(10);
        when(artifactoryDataSource.getMaxActive()).thenReturn(100);
        when(artifactoryDataSource.getMaxIdle()).thenReturn(10);
        when(artifactoryDataSource.getMinIdle()).thenReturn(10);
        DatabaseConnectionMetricProvider dbProvider =
                new DatabaseConnectionMetricProvider(jdbcHelper);
        List<Metric> metrics = dbProvider.collect();
        assertTrue(metrics.size() != 0);
        assertEquals(metrics.get(0).getName(),"active");
        List<String> metricsNames = metrics.stream().map(Metric::getName).collect(Collectors.toList());
        CollectionUtils.isEqualCollection(metricsNames, Arrays.asList(expectedMetrics));
    }
    @NotNull
    private MetricsDataSource createMockDataSource() throws SQLException {
        DataSource mockDataSource = Mockito.mock(DataSource.class);
        Connection mockConnection = Mockito.mock(Connection.class);
        PreparedStatement mockStatement = Mockito.mock(PreparedStatement.class);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(any())).thenReturn(mockStatement);
        return new MetricsDataSource(mockDataSource);
    }



}
