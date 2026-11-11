package org.artifactory.storage.db.remote.stats.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.remote.stats.entity.RemoteRepoStatsEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base DAO for remote repository statistics tables.
 * Provides shared CRUD operations for both {@code remote_repo_stats_hourly}
 * and {@code remote_repo_stats_daily} tables, which share an identical schema.
 *
 * @author husseina
 */
@Slf4j
public abstract class AbstractRemoteRepoStatsDao extends BaseDao {

    protected final String tableName;
    protected final ArtifactoryDbProperties dbProperties;

    protected AbstractRemoteRepoStatsDao(ArtifactoryJdbcHelper jdbcHelper,
                                         ArtifactoryDbProperties dbProperties,
                                         String tableName) {
        super(jdbcHelper);
        this.dbProperties = dbProperties;
        this.tableName = tableName;
    }

    /**
     * Inserts a new remote repo stats record.
     *
     * @param entity the stats entity to insert
     */
    public void insert(RemoteRepoStatsEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            String sql = "INSERT INTO " + tableName
                    + " (repository_key, repo_package_type, update_time, "
                    + "total_request_count, total_response_time_ms, "
                    + "max_response_time_ms, ttfb_avg, total_response_size, "
                    + "status_2xx_count, status_3xx_count, "
                    + "status_4xx_count, status_5xx_count, "
                    + "http_method, transfer_rate) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcHelper.executeUpdate(sql,
                    entity.repositoryKey(),
                    entity.repoPackageType(),
                    entity.updateTime(),
                    entity.totalRequestCount(),
                    entity.totalResponseTimeMs(),
                    entity.maxResponseTimeMs(),
                    entity.ttfbAvg(),
                    entity.totalResponseSize(),
                    entity.status2xxCount(),
                    entity.status3xxCount(),
                    entity.status4xxCount(),
                    entity.status5xxCount(),
                    entity.httpMethod(),
                    entity.transferRate()
            );
        } catch (SQLException e) {
            throw new StorageException("Failed to insert record into " + tableName, e);
        }
    }

    /**
     * Updates an existing record identified by its composite PK
     * (repository_key, update_time, http_method).
     *
     * @param entity the stats entity with updated values
     */
    public void update(RemoteRepoStatsEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            String sql = "UPDATE " + tableName + " SET "
                    + "repo_package_type = ?, "
                    + "total_request_count = ?, "
                    + "total_response_time_ms = ?, "
                    + "max_response_time_ms = ?, "
                    + "ttfb_avg = ?, "
                    + "total_response_size = ?, "
                    + "status_2xx_count = ?, "
                    + "status_3xx_count = ?, "
                    + "status_4xx_count = ?, "
                    + "status_5xx_count = ?, "
                    + "transfer_rate = ? "
                    + "WHERE repository_key = ? "
                    + "AND update_time = ? AND http_method = ?";
            jdbcHelper.executeUpdate(sql,
                    entity.repoPackageType(),
                    entity.totalRequestCount(),
                    entity.totalResponseTimeMs(),
                    entity.maxResponseTimeMs(),
                    entity.ttfbAvg(),
                    entity.totalResponseSize(),
                    entity.status2xxCount(),
                    entity.status3xxCount(),
                    entity.status4xxCount(),
                    entity.status5xxCount(),
                    entity.transferRate(),
                    entity.repositoryKey(),
                    entity.updateTime(),
                    entity.httpMethod()
            );
        } catch (SQLException e) {
            throw new StorageException("Failed to update record in " + tableName, e);
        }
    }

    /**
     * Checks whether a record exists for the given composite PK.
     *
     * @param repositoryKey the repository key
     * @param updateTime    the update time bucket
     * @param httpMethod    the HTTP method
     * @return true if a matching record exists
     */
    public boolean exists(String repositoryKey, long updateTime,
                          String httpMethod) {
        try {
            String sql = "SELECT 1 FROM " + tableName
                    + " WHERE repository_key = ? AND update_time = ?"
                    + " AND http_method = ?";
            try (ResultSet rs = jdbcHelper.executeSelect(
                    sql, repositoryKey, updateTime, httpMethod)) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to check existence in " + tableName
                            + " for key=" + repositoryKey, e);
        }
    }

    /**
     * Inserts a new record or updates an existing one based on the composite PK.
     *
     * @param entity the stats entity to upsert
     */
    public void upsert(RemoteRepoStatsEntity entity) {
        if (exists(entity.repositoryKey(), entity.updateTime(),
                entity.httpMethod())) {
            update(entity);
        } else {
            insert(entity);
        }
    }

    /**
     * Additive merge for HA safety. On PostgreSQL, uses a single
     * INSERT ... ON CONFLICT ... DO UPDATE statement. On other DB types,
     * falls back to try-INSERT / catch / UPDATE.
     *
     * @param entity the stats entity to merge
     */
    public void additiveMerge(RemoteRepoStatsEntity entity) {
        if (entity == null) {
            return;
        }
        if (dbProperties.isPostgres()) {
            postgresUpsert(entity);
        } else {
            genericAdditiveMerge(entity);
        }
    }

    private void postgresUpsert(RemoteRepoStatsEntity entity) {
        try {
            String sql = """
                    INSERT INTO %s (repository_key, repo_package_type,
                        update_time, total_request_count,
                        total_response_time_ms, max_response_time_ms,
                        ttfb_avg, total_response_size,
                        status_2xx_count, status_3xx_count,
                        status_4xx_count, status_5xx_count,
                        http_method, transfer_rate)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (repository_key, update_time, http_method)
                    DO UPDATE SET
                        repo_package_type = CASE
                            WHEN EXCLUDED.repo_package_type != ''
                            THEN EXCLUDED.repo_package_type
                            ELSE %1$s.repo_package_type END,
                        total_request_count = %1$s.total_request_count
                            + EXCLUDED.total_request_count,
                        total_response_time_ms = %1$s.total_response_time_ms
                            + EXCLUDED.total_response_time_ms,
                        max_response_time_ms = GREATEST(
                            %1$s.max_response_time_ms,
                            EXCLUDED.max_response_time_ms),
                        ttfb_avg = CASE
                            WHEN %1$s.total_request_count
                                + EXCLUDED.total_request_count > 0
                            THEN ((%1$s.ttfb_avg * %1$s.total_request_count)
                                + (EXCLUDED.ttfb_avg
                                    * EXCLUDED.total_request_count))
                                / (%1$s.total_request_count
                                    + EXCLUDED.total_request_count)
                            ELSE %1$s.ttfb_avg END,
                        total_response_size = %1$s.total_response_size
                            + EXCLUDED.total_response_size,
                        status_2xx_count = %1$s.status_2xx_count
                            + EXCLUDED.status_2xx_count,
                        status_3xx_count = %1$s.status_3xx_count
                            + EXCLUDED.status_3xx_count,
                        status_4xx_count = %1$s.status_4xx_count
                            + EXCLUDED.status_4xx_count,
                        status_5xx_count = %1$s.status_5xx_count
                            + EXCLUDED.status_5xx_count,
                        transfer_rate = CASE
                            WHEN (%1$s.total_response_time_ms
                                + EXCLUDED.total_response_time_ms) > 0
                            THEN ((%1$s.total_response_size
                                + EXCLUDED.total_response_size) * 1000)
                                / (%1$s.total_response_time_ms
                                    + EXCLUDED.total_response_time_ms)
                            ELSE 0 END""".formatted(tableName);
            jdbcHelper.executeUpdate(sql,
                    entity.repositoryKey(),
                    entity.repoPackageType(),
                    entity.updateTime(),
                    entity.totalRequestCount(),
                    entity.totalResponseTimeMs(),
                    entity.maxResponseTimeMs(),
                    entity.ttfbAvg(),
                    entity.totalResponseSize(),
                    entity.status2xxCount(),
                    entity.status3xxCount(),
                    entity.status4xxCount(),
                    entity.status5xxCount(),
                    entity.httpMethod(),
                    entity.transferRate());
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to upsert record in " + tableName, e);
        }
    }

    private void genericAdditiveMerge(RemoteRepoStatsEntity entity) {
        try {
            if (!tryInsert(entity)) {
                additiveUpdate(entity);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to additive-merge record in " + tableName, e);
        }
    }

    private boolean tryInsert(RemoteRepoStatsEntity entity) {
        try {
            insert(entity);
            return true;
        } catch (StorageException e) {
            if (!isUniqueConstraintViolation(e.getCause())) {
                throw e;
            }
            log.debug("Unique constraint violation on insert into {};"
                    + " falling back to additive update",
                    tableName);
            return false;
        }
    }

    private void additiveUpdate(RemoteRepoStatsEntity entity)
            throws SQLException {
        String sql = """
                UPDATE %s SET
                    total_request_count = total_request_count + ?,
                    total_response_time_ms = total_response_time_ms + ?,
                    max_response_time_ms = CASE
                        WHEN ? > max_response_time_ms THEN ?
                        ELSE max_response_time_ms END,
                    ttfb_avg = CASE
                        WHEN total_request_count + ? > 0
                        THEN ((ttfb_avg * total_request_count) + ?)
                            / (total_request_count + ?)
                        ELSE ttfb_avg END,
                    total_response_size = total_response_size + ?,
                    status_2xx_count = status_2xx_count + ?,
                    status_3xx_count = status_3xx_count + ?,
                    status_4xx_count = status_4xx_count + ?,
                    status_5xx_count = status_5xx_count + ?,
                    transfer_rate = CASE
                        WHEN (total_response_time_ms + ?) > 0
                        THEN ((total_response_size + ?) * 1000)
                            / (total_response_time_ms + ?)
                        ELSE 0 END
                WHERE repository_key = ?
                    AND update_time = ? AND http_method = ?"""
                .formatted(tableName);
        int updated = jdbcHelper.executeUpdate(sql,
                entity.totalRequestCount(),
                entity.totalResponseTimeMs(),
                entity.maxResponseTimeMs(),
                entity.maxResponseTimeMs(),
                entity.totalRequestCount(),
                entity.ttfbAvg() * entity.totalRequestCount(),
                entity.totalRequestCount(),
                entity.totalResponseSize(),
                entity.status2xxCount(),
                entity.status3xxCount(),
                entity.status4xxCount(),
                entity.status5xxCount(),
                entity.totalResponseTimeMs(),
                entity.totalResponseSize(),
                entity.totalResponseTimeMs(),
                entity.repositoryKey(),
                entity.updateTime(),
                entity.httpMethod());
        if (updated == 0) {
            throw new SQLException(
                    "Additive update in " + tableName + " matched 0 rows for"
                    + " repository_key=" + entity.repositoryKey()
                    + " update_time=" + entity.updateTime()
                    + " http_method=" + entity.httpMethod());
        }
    }

    /**
     * Retrieves records for a specific repository within a time range.
     *
     * @param repoKey   the repository key
     * @param startTime start time (inclusive) in epoch ms
     * @param endTime   end time (inclusive) in epoch ms
     * @return list of matching stats entities ordered by update_time descending
     */
    public List<RemoteRepoStatsEntity> getByRepositoryKeyAndTimeRange(
            String repoKey, long startTime, long endTime) {
        try {
            String sql = "SELECT id, repository_key, repo_package_type, "
                    + "update_time, total_request_count, "
                    + "total_response_time_ms, max_response_time_ms, "
                    + "ttfb_avg, total_response_size, "
                    + "status_2xx_count, status_3xx_count, "
                    + "status_4xx_count, status_5xx_count, "
                    + "http_method, transfer_rate "
                    + "FROM " + tableName + " "
                    + "WHERE repository_key = ? "
                    + "AND update_time >= ? AND update_time <= ? "
                    + "ORDER BY update_time DESC";
            try (ResultSet rs = jdbcHelper.executeSelect(sql, repoKey, startTime, endTime)) {
                return mapToEntityList(rs);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve records from " + tableName
                            + " for key=" + repoKey, e);
        }
    }

    /**
     * Retrieves all records within a time range.
     *
     * @param startTime start time (inclusive) in epoch ms
     * @param endTime   end time (inclusive) in epoch ms
     * @return list of matching stats entities ordered by update_time descending
     */
    public List<RemoteRepoStatsEntity> getByTimeRange(long startTime,
                                                      long endTime) {
        return getByTimeRange(startTime, endTime, List.of(), List.of());
    }

    /**
     * Retrieves records within a time range with optional repository key
     * and package type filters pushed to the database.
     *
     * @param startTime    start time (inclusive) in epoch ms
     * @param endTime      end time (inclusive) in epoch ms
     * @param repoKeys     optional list of repository keys to filter by
     * @param packageTypes optional list of package types to filter by
     * @return list of matching stats entities ordered by update_time descending
     */
    public List<RemoteRepoStatsEntity> getByTimeRange(
            long startTime, long endTime,
            List<String> repoKeys, List<String> packageTypes) {
        try {
            boolean hasRepoFilter = repoKeys != null && !repoKeys.isEmpty();
            boolean hasPkgFilter = packageTypes != null
                    && !packageTypes.isEmpty();
            String sql = """
                    SELECT id, repository_key, repo_package_type,
                           update_time, total_request_count,
                           total_response_time_ms, max_response_time_ms,
                           ttfb_avg, total_response_size,
                           status_2xx_count, status_3xx_count,
                           status_4xx_count, status_5xx_count,
                           http_method, transfer_rate
                    FROM %s
                    WHERE update_time >= ? AND update_time <= ?%s%s
                    ORDER BY update_time DESC""".formatted(
                    tableName,
                    hasRepoFilter ? " AND repository_key IN (#)" : "",
                    hasPkgFilter ? " AND repo_package_type IN (#)" : "");
            List<Object> params = new ArrayList<>();
            params.add(startTime);
            params.add(endTime);
            if (hasRepoFilter) {
                params.add(repoKeys);
            }
            if (hasPkgFilter) {
                params.add(packageTypes);
            }
            try (ResultSet rs = jdbcHelper.executeSelect(
                    sql, params.toArray())) {
                return mapToEntityList(rs);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve records from " + tableName
                            + " by time range", e);
        }
    }

    /**
     * Deletes records older than the given threshold time.
     *
     * @param thresholdTime threshold time in epoch ms; records with update_time older than this are deleted
     */
    public void deleteByThresholdTime(long thresholdTime) {
        try {
            String sql = "DELETE FROM " + tableName + " WHERE update_time < ?";
            jdbcHelper.executeUpdate(sql, thresholdTime);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to delete records from " + tableName + " before threshold time", e);
        }
    }

    private List<RemoteRepoStatsEntity> mapToEntityList(ResultSet rs)
            throws SQLException {
        List<RemoteRepoStatsEntity> results = new ArrayList<>();
        while (rs.next()) {
            results.add(RemoteRepoStatsEntity.builder()
                    .id(rs.getLong("id"))
                    .repositoryKey(rs.getString("repository_key"))
                    .repoPackageType(rs.getString("repo_package_type"))
                    .updateTime(rs.getLong("update_time"))
                    .totalRequestCount(rs.getLong("total_request_count"))
                    .totalResponseTimeMs(rs.getLong("total_response_time_ms"))
                    .maxResponseTimeMs(rs.getLong("max_response_time_ms"))
                    .ttfbAvg(rs.getLong("ttfb_avg"))
                    .totalResponseSize(rs.getLong("total_response_size"))
                    .status2xxCount(rs.getLong("status_2xx_count"))
                    .status3xxCount(rs.getLong("status_3xx_count"))
                    .status4xxCount(rs.getLong("status_4xx_count"))
                    .status5xxCount(rs.getLong("status_5xx_count"))
                    .httpMethod(rs.getString("http_method"))
                    .transferRate(rs.getLong("transfer_rate"))
                    .build());
        }
        return results;
    }
}
