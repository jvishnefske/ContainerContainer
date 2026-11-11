package org.artifactory.postgresql.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.postgresql.job.PgSchemaMetricsJob;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ConditionalOnProperty;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.db.postgresql.dao.PgSchemaDao;
import org.artifactory.storage.db.postgresql.entity.IndexSizeEntity;
import org.artifactory.util.AolUtils;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.artifactory.common.ConstantValues.pgSchemaMetricsEnabled;
import static org.artifactory.common.ConstantValues.pgSchemaMetricsJobIntervalHours;
import static org.artifactory.common.ConstantValues.pgSchemaMetricsTrackedIndexes;

/**
 * Implementation of {@link PgSchemaService} that collects PostgreSQL schema metrics and exposes them via Micrometer.
 * <p>
 * Registers gauges for index size, table size, and table rows. All metrics include both index and table name as tags.
 * Metrics are refreshed periodically by a scheduled job.
 * <p>
 * <b>Availability:</b> This feature is only available in AOL (SaaS) environments with PostgreSQL.
 * <p>
 * <b>Thread Safety:</b> Uses {@link AtomicReference} for both {@code indexDataCache} and {@code trackedIndexes} to
 * ensure visibility and atomic updates across threads. Gauges always see consistent snapshots (complete old or new
 * state, never partial).
 * <p>
 * <b>Error Handling:</b> On refresh failure, preserves stale data rather than clearing cache. Designed for external
 * log monitoring to detect repeated failures.
 * <p>
 * <b>Performance:</b> Queries PostgreSQL catalog views using {@code pg_relation_size()}. Suitable for tens of
 * indexes, not thousands.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "artifactory.metrics.enabled", havingValue = "true")
@ConditionalOnProperty(value = "artifactory.pg.schema.metrics.enabled", havingValue = "true")
public class PgSchemaServiceImpl implements PgSchemaService, ContextCreationListener {

    private final PgSchemaDao pgSchemaDao;
    private final ArtifactoryDbProperties dbProperties;
    private final TaskService taskService;
    private final MeterRegistry meterRegistry;
    private final AtomicReference<Map<String, IndexSizeEntity>> indexDataCache = new AtomicReference<>(Map.of());
    private final AtomicReference<Set<String>> trackedIndexes = new AtomicReference<>(Set.of());

    /**
     * Initializes the service: parses configuration, registers gauges, and schedules the refresh job.
     * Job is only scheduled if running in AOL (SaaS), using PostgreSQL, and valid configuration exists.
     */
    @Override
    public void onContextCreated() {
        Set<String> parsedIndexes = Arrays.stream(pgSchemaMetricsTrackedIndexes.getString().split(","))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toUnmodifiableSet());
        int intervalHours = pgSchemaMetricsJobIntervalHours.getInt();
        if (shouldScheduleJob(parsedIndexes, intervalHours)) {
            trackedIndexes.set(parsedIndexes);
            registerIndexMetrics();
            long intervalMillis = HOURS.toMillis(intervalHours);
            Task t = TaskUtils.createRepeatingTask(PgSchemaMetricsJob.class, intervalMillis, 0L);
            taskService.startTask(t, false);
            log.debug("Scheduled PgSchemaMetricsJob to run with interval: {} hours", intervalHours);
        }
    }

    private boolean shouldScheduleJob(Set<String> trackedIndexes, int intervalHours) {
        return pgSchemaMetricsEnabled.getBoolean() &&
                !trackedIndexes.isEmpty() && intervalHours > 0 && AolUtils.isAol() && dbProperties.isPostgres();
    }

    /**
     * Registers Micrometer gauges for all tracked indexes. Gauges query the cache on each evaluation.
     */
    private void registerIndexMetrics() {
        refreshMetrics();
        trackedIndexes.get().forEach(indexName -> {
            String tableName = getTableName(indexName);
            meterRegistry.gauge("artifactory.pg.index.size",
                    Tags.of("index", indexName, "table", tableName), this, service -> service.getIndexSize(indexName));
            meterRegistry.gauge("artifactory.pg.table.size",
                    Tags.of("index", indexName, "table", tableName), this, service -> service.getTableSize(indexName));
            meterRegistry.gauge("artifactory.pg.table.rows",
                    Tags.of("index", indexName, "table", tableName), this, service -> service.getTableRows(indexName));
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Atomically replaces entire cache with fresh data. On failure, preserves stale data.
     */
    @Override
    public void refreshMetrics() {
        try {
            Set<IndexSizeEntity> indexSizes = pgSchemaDao.getIndexSizes(trackedIndexes.get());
            Map<String, IndexSizeEntity> newCache = indexSizes.stream()
                    .collect(Collectors.toUnmodifiableMap(IndexSizeEntity::indexName, Function.identity()));
            indexDataCache.set(newCache);
            log.debug("Successfully refreshed index metrics data");
        } catch (SQLException e) {
            log.warn("Failed to refresh index data from database", e);
        }
    }

    private <T> T getEntityAttribute(String indexName, Function<IndexSizeEntity, T> extractor, T defaultValue) {
        return Optional.ofNullable(indexDataCache.get().get(indexName))
                .map(extractor)
                .orElse(defaultValue);
    }

    private long getIndexSize(String indexName) {
        return getEntityAttribute(indexName, IndexSizeEntity::indexSize, 0L);
    }

    private long getTableSize(String indexName) {
        return getEntityAttribute(indexName, IndexSizeEntity::tableSize, 0L);
    }

    private long getTableRows(String indexName) {
        return getEntityAttribute(indexName, IndexSizeEntity::tableRows, 0L);
    }

    private String getTableName(String indexName) {
        return getEntityAttribute(indexName, IndexSizeEntity::tableName, "unknown");
    }
}
