package org.artifactory.storage.db.remote.stats.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.remote.stats.entity.RemoteRepoStatsStatusEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the {@code remote_repo_stats_status} table.
 * Provides operations for per-status-code and HTTP method breakdown records,
 * shared by both hourly and daily aggregation windows.
 *
 * @author husseina
 */
@Slf4j
@Repository
public class RemoteRepoStatsStatusDao extends BaseDao {

    private static final String TABLE_NAME = "remote_repo_stats_status";
    private final ArtifactoryDbProperties dbProperties;

    public RemoteRepoStatsStatusDao(ArtifactoryJdbcHelper jdbcHelper,
                                    ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        this.dbProperties = dbProperties;
    }

    /**
     * Inserts a single status record.
     *
     * @param entity the status entity to insert
     */
    public void insert(RemoteRepoStatsStatusEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            String sql = "INSERT INTO " + TABLE_NAME + " (repository_key, repo_package_type, update_time, " +
                    "aggregation_type, status_code, request_count, http_method) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            jdbcHelper.executeUpdate(sql,
                    entity.repositoryKey(),
                    entity.repoPackageType(),
                    entity.updateTime(),
                    entity.aggregationType(),
                    entity.statusCode(),
                    entity.requestCount(),
                    entity.httpMethod()
            );
        } catch (SQLException e) {
            throw new StorageException("Failed to insert record into " + TABLE_NAME, e);
        }
    }

    /**
     * Additive merge for HA safety. On PostgreSQL, uses a single
     * INSERT ... ON CONFLICT ... DO UPDATE statement. On other DB types,
     * falls back to try-INSERT / catch / UPDATE.
     *
     * @param entity the status entity to merge
     */
    public void additiveMerge(RemoteRepoStatsStatusEntity entity) {
        if (entity == null) {
            return;
        }
        if (dbProperties.isPostgres()) {
            postgresUpsert(entity);
        } else {
            genericAdditiveMerge(entity);
        }
    }

    private void postgresUpsert(RemoteRepoStatsStatusEntity entity) {
        try {
            String sql = """
                    INSERT INTO remote_repo_stats_status
                        (repository_key, repo_package_type, update_time,
                         aggregation_type, status_code, request_count,
                         http_method)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (repository_key, update_time, status_code,
                                 http_method, aggregation_type)
                    DO UPDATE SET
                        repo_package_type = CASE
                            WHEN EXCLUDED.repo_package_type != ''
                            THEN EXCLUDED.repo_package_type
                            ELSE remote_repo_stats_status.repo_package_type END,
                        request_count = remote_repo_stats_status.request_count
                            + EXCLUDED.request_count""";
            jdbcHelper.executeUpdate(sql,
                    entity.repositoryKey(),
                    entity.repoPackageType(),
                    entity.updateTime(),
                    entity.aggregationType(),
                    entity.statusCode(),
                    entity.requestCount(),
                    entity.httpMethod());
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to upsert record in " + TABLE_NAME, e);
        }
    }

    private void genericAdditiveMerge(RemoteRepoStatsStatusEntity entity) {
        try {
            if (!tryInsert(entity)) {
                additiveUpdate(entity);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to additive-merge record in " + TABLE_NAME, e);
        }
    }

    private boolean tryInsert(RemoteRepoStatsStatusEntity entity) {
        try {
            insert(entity);
            return true;
        } catch (StorageException e) {
            if (!(e.getCause() instanceof SQLException)) {
                throw e;
            }
            log.debug("Duplicate key on insert into {};"
                    + " falling back to additive update", TABLE_NAME);
            return false;
        }
    }

    private void additiveUpdate(RemoteRepoStatsStatusEntity entity)
            throws SQLException {
        String sql = """
                UPDATE remote_repo_stats_status SET
                    request_count = request_count + ?
                WHERE repository_key = ? AND update_time = ?
                    AND status_code = ?
                    AND http_method = ? AND aggregation_type = ?""";
        jdbcHelper.executeUpdate(sql,
                entity.requestCount(),
                entity.repositoryKey(),
                entity.updateTime(),
                entity.statusCode(),
                entity.httpMethod(),
                entity.aggregationType());
    }

    /**
     * Batch-inserts a list of status records.
     *
     * @param entities the status entities to insert
     */
    public void insertBatch(List<RemoteRepoStatsStatusEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (RemoteRepoStatsStatusEntity entity : entities) {
            insert(entity);
        }
    }

    /**
     * Retrieves status records for a specific repository within a time range.
     *
     * @param repoKey   the repository key
     * @param startTime start time (inclusive) in epoch ms
     * @param endTime   end time (inclusive) in epoch ms
     * @return list of matching status entities ordered by update_time descending
     */
    public List<RemoteRepoStatsStatusEntity> getByRepositoryKeyAndTimeRange(
            String repoKey, long startTime, long endTime) {
        try {
            String sql = "SELECT id, repository_key, repo_package_type, update_time, " +
                    "aggregation_type, status_code, request_count, http_method " +
                    "FROM " + TABLE_NAME + " " +
                    "WHERE repository_key = ? AND update_time >= ? AND update_time <= ? " +
                    "ORDER BY update_time DESC";
            try (ResultSet rs = jdbcHelper.executeSelect(sql, repoKey, startTime, endTime)) {
                return mapToEntityList(rs);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve records from " + TABLE_NAME + " for key=" + repoKey, e);
        }
    }

    /**
     * Retrieves status records for a specific repository, time range, and aggregation type.
     *
     * @param repoKey         the repository key
     * @param startTime       start time (inclusive) in epoch ms
     * @param endTime         end time (inclusive) in epoch ms
     * @param aggregationType the aggregation window ({@code "hourly"} or {@code "daily"})
     * @return list of matching status entities ordered by update_time descending
     */
    public List<RemoteRepoStatsStatusEntity> getByAggregationType(
            String repoKey, long startTime, long endTime, String aggregationType) {
        try {
            String sql = "SELECT id, repository_key, repo_package_type, update_time, " +
                    "aggregation_type, status_code, request_count, http_method " +
                    "FROM " + TABLE_NAME + " " +
                    "WHERE repository_key = ? AND update_time >= ? AND update_time <= ? " +
                    "AND aggregation_type = ? " +
                    "ORDER BY update_time DESC";
            try (ResultSet rs = jdbcHelper.executeSelect(sql, repoKey, startTime, endTime, aggregationType)) {
                return mapToEntityList(rs);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve records from " + TABLE_NAME + " by aggregation type", e);
        }
    }

    /**
     * Retrieves all status records within a time range for a given aggregation type.
     *
     * @param startTime       start time (inclusive) in epoch ms
     * @param endTime         end time (inclusive) in epoch ms
     * @param aggregationType the aggregation window ({@code "hourly"} or {@code "daily"})
     * @return list of matching status entities ordered by repository_key, update_time
     */
    public List<RemoteRepoStatsStatusEntity> getByTimeRangeAndAggregationType(
            long startTime, long endTime, String aggregationType) {
        return getByTimeRangeAndAggregationType(
                startTime, endTime, aggregationType, List.of(), List.of());
    }

    /**
     * Retrieves status records within a time range for a given aggregation
     * type, with optional repository key and package type filters pushed
     * to the database.
     *
     * @param startTime       start time (inclusive) in epoch ms
     * @param endTime         end time (inclusive) in epoch ms
     * @param aggregationType the aggregation window
     * @param repoKeys        optional list of repository keys to filter by
     * @param packageTypes    optional list of package types to filter by
     * @return list of matching status entities
     */
    public List<RemoteRepoStatsStatusEntity> getByTimeRangeAndAggregationType(
            long startTime, long endTime, String aggregationType,
            List<String> repoKeys, List<String> packageTypes) {
        try {
            boolean hasRepoFilter = repoKeys != null && !repoKeys.isEmpty();
            boolean hasPkgFilter = packageTypes != null
                    && !packageTypes.isEmpty();
            String sql = """
                    SELECT id, repository_key, repo_package_type,
                           update_time, aggregation_type, status_code,
                           request_count, http_method
                    FROM %s
                    WHERE update_time >= ? AND update_time <= ?
                    AND aggregation_type = ?%s%s
                    ORDER BY repository_key, update_time""".formatted(
                    TABLE_NAME,
                    hasRepoFilter ? " AND repository_key IN (#)" : "",
                    hasPkgFilter ? " AND repo_package_type IN (#)" : "");
            List<Object> params = new ArrayList<>();
            params.add(startTime);
            params.add(endTime);
            params.add(aggregationType);
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
                    "Failed to retrieve records from " + TABLE_NAME
                            + " by time range and aggregation type", e);
        }
    }

    /**
     * Deletes status records older than the given threshold time.
     *
     * @param thresholdTime threshold time in epoch ms; records with update_time older than this are deleted
     */
    public void deleteByThresholdTime(long thresholdTime) {
        try {
            String sql = "DELETE FROM " + TABLE_NAME + " WHERE update_time < ?";
            jdbcHelper.executeUpdate(sql, thresholdTime);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to delete records from " + TABLE_NAME + " before threshold time", e);
        }
    }

    private List<RemoteRepoStatsStatusEntity> mapToEntityList(ResultSet rs) throws SQLException {
        List<RemoteRepoStatsStatusEntity> results = new ArrayList<>();
        while (rs.next()) {
            results.add(RemoteRepoStatsStatusEntity.builder()
                    .id(rs.getLong("id"))
                    .repositoryKey(rs.getString("repository_key"))
                    .repoPackageType(rs.getString("repo_package_type"))
                    .updateTime(rs.getLong("update_time"))
                    .aggregationType(rs.getString("aggregation_type"))
                    .statusCode(rs.getInt("status_code"))
                    .requestCount(rs.getLong("request_count"))
                    .httpMethod(rs.getString("http_method"))
                    .build());
        }
        return results;
    }
}
