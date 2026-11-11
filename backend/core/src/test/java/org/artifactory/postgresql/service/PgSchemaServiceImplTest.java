package org.artifactory.postgresql.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.postgresql.dao.PgSchemaDao;
import org.artifactory.storage.db.postgresql.entity.IndexSizeEntity;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.AolUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.artifactory.common.ConstantValues.pgSchemaMetricsEnabled;
import static org.artifactory.common.ConstantValues.pgSchemaMetricsJobIntervalHours;
import static org.artifactory.common.ConstantValues.pgSchemaMetricsTrackedIndexes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class PgSchemaServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private PgSchemaDao pgSchemaDao;

    @Mock
    private ArtifactoryDbProperties dbProperties;

    @Mock
    private TaskService taskService;

    private MeterRegistry meterRegistry;
    private PgSchemaServiceImpl pgSchemaService;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        pgSchemaService = new PgSchemaServiceImpl(pgSchemaDao, dbProperties, taskService, meterRegistry);
    }

    @Test
    void onContextCreated_shouldScheduleJobAndRegisterGauges_whenValidConfiguration() throws SQLException {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            // Arrange
            aolUtils.when(AolUtils::isAol).thenReturn(true);
            getBound().setProperty(pgSchemaMetricsEnabled.getPropertyName(), "true");
            getBound().setProperty(pgSchemaMetricsTrackedIndexes.getPropertyName(), "idx_nodes_sha256");
            getBound().setProperty(pgSchemaMetricsJobIntervalHours.getPropertyName(), "1");
            when(dbProperties.isPostgres()).thenReturn(true);
            when(pgSchemaDao.getIndexSizes(any()))
                    .thenReturn(Set.of(new IndexSizeEntity("idx_nodes_sha256", 1024000L, "nodes", 50000000L, 1000L)));

            // Act
            pgSchemaService.onContextCreated();

            // Assert
            verify(taskService).startTask(any(Task.class), anyBoolean());
            verify(pgSchemaDao).getIndexSizes(any());
            assertEquals(1024000.0, meterRegistry.get("artifactory.pg.index.size")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "nodes").gauge().value());
            assertEquals(50000000.0, meterRegistry.get("artifactory.pg.table.size")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "nodes").gauge().value());
            assertEquals(1000.0, meterRegistry.get("artifactory.pg.table.rows")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "nodes").gauge().value());
        }
    }

    @ParameterizedTest
    @CsvSource({
            // Feature disabled
            "false, 'idx_nodes_sha256', 1, true, true",
            // No indexes configured
            "true, '', 1, true, true",
            // Interval is zero
            "true, 'idx_nodes_sha256', 0, true, true",
            // Not in AOL environment
            "true, 'idx_nodes_sha256', 1, false, true",
            // Not PostgreSQL database
            "true, 'idx_nodes_sha256', 1, true, false"
    })
    void onContextCreated_shouldNotScheduleJob_whenInvalidConfiguration(
            boolean enabled, String indexes, int intervalHours, boolean isAol, boolean isPostgres) {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            // Arrange
            aolUtils.when(AolUtils::isAol).thenReturn(isAol);
            getBound().setProperty(pgSchemaMetricsEnabled.getPropertyName(), String.valueOf(enabled));
            getBound().setProperty(pgSchemaMetricsTrackedIndexes.getPropertyName(), indexes);
            getBound().setProperty(pgSchemaMetricsJobIntervalHours.getPropertyName(), String.valueOf(intervalHours));
            if (enabled && !indexes.isEmpty() && intervalHours > 0 && isAol) {
                // Only stub isPostgres() if previous conditions pass (to avoid unnecessary stubbing)
                when(dbProperties.isPostgres()).thenReturn(isPostgres);
            }

            // Act
            pgSchemaService.onContextCreated();

            // Assert
            verify(taskService, never()).startTask(any(Task.class), anyBoolean());
        }
    }

    @ParameterizedTest
    @CsvSource({
            // Configuration with whitespace
            "'  idx_nodes_sha256  ,  idx_nodes_node_id  ', 2",
            // Configuration with empty entries
            "'idx_nodes_sha256,,idx_nodes_node_id', 2",
            // Single index
            "'idx_nodes_sha256', 1",
            // Multiple indexes
            "'idx_nodes_sha256,idx_nodes_node_id,idx_builds_build_id', 3"
    })
    void onContextCreated_shouldParseConfigurationCorrectly(String config, int expectedIndexCount)
            throws SQLException {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            // Arrange
            aolUtils.when(AolUtils::isAol).thenReturn(true);
            getBound().setProperty(pgSchemaMetricsEnabled.getPropertyName(), "true");
            getBound().setProperty(pgSchemaMetricsTrackedIndexes.getPropertyName(), config);
            getBound().setProperty(pgSchemaMetricsJobIntervalHours.getPropertyName(), "1");
            when(dbProperties.isPostgres()).thenReturn(true);
            when(pgSchemaDao.getIndexSizes(any())).thenReturn(Set.of());

            // Act
            pgSchemaService.onContextCreated();

            // Assert
            @SuppressWarnings("unchecked")
            AtomicReference<Set<String>> trackedIndexesRef =
                    (AtomicReference<Set<String>>) ReflectionTestUtils.getField(pgSchemaService, "trackedIndexes");
            assertNotNull(trackedIndexesRef);
            Set<String> trackedIndexes = trackedIndexesRef.get();
            assertNotNull(trackedIndexes);
            assertEquals(expectedIndexCount, trackedIndexes.size());
        }
    }

    @Test
    void refreshMetrics_shouldUpdateAllGauges_whenQuerySucceeds() throws SQLException {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            // Arrange
            aolUtils.when(AolUtils::isAol).thenReturn(true);
            getBound().setProperty(pgSchemaMetricsEnabled.getPropertyName(), "true");
            getBound().setProperty(pgSchemaMetricsTrackedIndexes.getPropertyName(), "idx_nodes_sha256");
            getBound().setProperty(pgSchemaMetricsJobIntervalHours.getPropertyName(), "1");
            when(dbProperties.isPostgres()).thenReturn(true);
            Set<IndexSizeEntity> entities =
                    Set.of(new IndexSizeEntity("idx_nodes_sha256", 1024000L, "nodes", 50000000L, 1000L));
            when(pgSchemaDao.getIndexSizes(any())).thenReturn(entities);
            pgSchemaService.onContextCreated();
            Set<IndexSizeEntity> updatedEntities =
                    Set.of(new IndexSizeEntity("idx_nodes_sha256", 2048000L, "nodes", 60000000L, 1200L));
            when(pgSchemaDao.getIndexSizes(any())).thenReturn(updatedEntities);

            // Act
            pgSchemaService.refreshMetrics();

            // Assert
            assertEquals(2048000.0, meterRegistry.get("artifactory.pg.index.size")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "nodes").gauge().value());
            assertEquals(60000000.0, meterRegistry.get("artifactory.pg.table.size")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "nodes").gauge().value());
            assertEquals(1200.0, meterRegistry.get("artifactory.pg.table.rows")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "nodes").gauge().value());
        }
    }

    @Test
    void refreshMetrics_shouldPreserveStaleData_whenQueryFails() throws SQLException {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            // Arrange
            aolUtils.when(AolUtils::isAol).thenReturn(true);
            getBound().setProperty(pgSchemaMetricsEnabled.getPropertyName(), "true");
            getBound().setProperty(pgSchemaMetricsTrackedIndexes.getPropertyName(), "idx_nodes_sha256");
            getBound().setProperty(pgSchemaMetricsJobIntervalHours.getPropertyName(), "1");
            when(dbProperties.isPostgres()).thenReturn(true);
            Set<IndexSizeEntity> initialEntities =
                    Set.of(new IndexSizeEntity("idx_nodes_sha256", 1024000L, "nodes", 50000000L, 1000L));
            when(pgSchemaDao.getIndexSizes(any())).thenReturn(initialEntities);
            pgSchemaService.onContextCreated();
            double initialValue = meterRegistry.get("artifactory.pg.index.size")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "nodes").gauge().value();
            when(pgSchemaDao.getIndexSizes(any())).thenThrow(new SQLException("Connection error"));

            // Act
            pgSchemaService.refreshMetrics();

            // Assert
            assertEquals(initialValue, meterRegistry.get("artifactory.pg.index.size")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "nodes").gauge().value());
        }
    }

    @Test
    void refreshMetrics_shouldReturnZero_whenIndexNotInCache() throws SQLException {
        try (MockedStatic<AolUtils> aolUtils = mockStatic(AolUtils.class)) {
            // Arrange
            aolUtils.when(AolUtils::isAol).thenReturn(true);
            getBound().setProperty(pgSchemaMetricsEnabled.getPropertyName(), "true");
            getBound().setProperty(pgSchemaMetricsTrackedIndexes.getPropertyName(), "idx_nodes_sha256");
            getBound().setProperty(pgSchemaMetricsJobIntervalHours.getPropertyName(), "1");
            when(dbProperties.isPostgres()).thenReturn(true);
            when(pgSchemaDao.getIndexSizes(any())).thenReturn(Set.of());

            // Act
            pgSchemaService.onContextCreated();

            // Assert
            assertEquals(0.0, meterRegistry.get("artifactory.pg.index.size")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "unknown").gauge().value());
            assertEquals(0.0, meterRegistry.get("artifactory.pg.table.size")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "unknown").gauge().value());
            assertEquals(0.0, meterRegistry.get("artifactory.pg.table.rows")
                    .tag("index", "idx_nodes_sha256")
                    .tag("table", "unknown").gauge().value());
        }
    }
}
