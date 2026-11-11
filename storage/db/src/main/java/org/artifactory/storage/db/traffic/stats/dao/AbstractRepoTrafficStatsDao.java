package org.artifactory.storage.db.traffic.stats.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.traffic.stats.entity.RepoTrafficStatsEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base DAO for repo traffic statistics tables.
 * Provides shared CRUD operations for both {@code repo_traffic_hourly}
 * and {@code repo_traffic_daily} tables, which share an identical schema.
 *
 * @author georgyr
 */
@Slf4j
public abstract class AbstractRepoTrafficStatsDao extends BaseDao {

    private static final String SELECT_COLUMNS =
            "id, update_time, action, actor, redirect_target, "
                    + "origin_repo_key, origin_repo_type, origin_package_type, "
                    + "repo_key, repo_type, package_type, "
                    + "request_count, total_transferred_size";

    protected final String tableName;
    private final boolean postgres;

    protected AbstractRepoTrafficStatsDao(
            ArtifactoryJdbcHelper jdbcHelper,
            ArtifactoryDbProperties dbProperties,
            String tableName) {
        super(jdbcHelper);
        this.tableName = tableName;
        this.postgres = dbProperties.isPostgres();
    }

    /**
     * Inserts a new repo traffic stats record.
     *
     * @param entity the stats entity to insert
     */
    public void insert(RepoTrafficStatsEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            String sql = "INSERT INTO " + tableName
                    + " (update_time, action, actor, redirect_target, "
                    + "origin_repo_key, origin_repo_type, origin_package_type, "
                    + "repo_key, repo_type, package_type, "
                    + "request_count, total_transferred_size) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcHelper.executeUpdate(sql,
                    entity.updateTime(),
                    entity.action(),
                    entity.actor(),
                    entity.redirectTarget(),
                    entity.originRepoKey(),
                    entity.originRepoType(),
                    entity.originPackageType(),
                    entity.repoKey(),
                    entity.repoType(),
                    entity.packageType(),
                    entity.requestCount(),
                    entity.totalTransferredSize()
            );
        } catch (SQLException e) {
            throw new StorageException("Failed to insert record into " + tableName, e);
        }
    }

    /**
     * Additive merge for HA safety. On PostgreSQL, uses a single
     * INSERT ... ON CONFLICT ... DO UPDATE to avoid noisy duplicate-key
     * errors in the DB server logs. On other databases, falls back to the
     * try-INSERT / catch / UPDATE pattern.
     *
     * @param entity the stats entity to merge
     */
    public void additiveMerge(RepoTrafficStatsEntity entity) {
        if (entity == null) {
            return;
        }
        if (postgres) {
            postgresUpsert(entity);
        } else {
            fallbackAdditiveMerge(entity);
        }
    }

    private void postgresUpsert(RepoTrafficStatsEntity entity) {
        String sql = "INSERT INTO " + tableName
                + " (update_time, action, actor,"
                + " redirect_target,"
                + " origin_repo_key, origin_repo_type,"
                + " origin_package_type,"
                + " repo_key, repo_type, package_type,"
                + " request_count, total_transferred_size)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (update_time, action, actor,"
                + " redirect_target, origin_repo_key, repo_key)"
                + " DO UPDATE SET"
                + " request_count = " + tableName
                + ".request_count"
                + " + EXCLUDED.request_count,"
                + " total_transferred_size = " + tableName
                + ".total_transferred_size"
                + " + EXCLUDED.total_transferred_size";
        try {
            jdbcHelper.executeUpdate(sql,
                    entity.updateTime(),
                    entity.action(),
                    entity.actor(),
                    entity.redirectTarget(),
                    entity.originRepoKey(),
                    entity.originRepoType(),
                    entity.originPackageType(),
                    entity.repoKey(),
                    entity.repoType(),
                    entity.packageType(),
                    entity.requestCount(),
                    entity.totalTransferredSize()
            );
        } catch (SQLException e) {
            throw new StorageException("Failed to upsert record in " + tableName, e);
        }
    }

    private void fallbackAdditiveMerge(
            RepoTrafficStatsEntity entity) {
        try {
            if (!tryInsert(entity)) {
                additiveUpdate(entity);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to additive-merge record in "
                            + tableName, e);
        }
    }

    private boolean tryInsert(RepoTrafficStatsEntity entity) {
        try {
            insert(entity);
            return true;
        } catch (StorageException e) {
            if (!isUniqueConstraintViolation(e.getCause())) {
                throw e;
            }
            log.debug("Unique constraint violation on insert into {}; falling back to additive update",
                    tableName);
            return false;
        }
    }

    private void additiveUpdate(
            RepoTrafficStatsEntity entity) throws SQLException {
        String sql = "UPDATE " + tableName + " SET "
                + "request_count = request_count + ?, "
                + "total_transferred_size ="
                + " total_transferred_size + ? "
                + "WHERE update_time = ? AND action = ?"
                + " AND actor = ?"
                + " AND redirect_target = ? "
                + "AND origin_repo_key = ?"
                + " AND repo_key = ?";
        int updated = jdbcHelper.executeUpdate(sql,
                entity.requestCount(),
                entity.totalTransferredSize(),
                entity.updateTime(),
                entity.action(),
                entity.actor(),
                entity.redirectTarget(),
                entity.originRepoKey(),
                entity.repoKey()
        );
        if (updated == 0) {
            throw new SQLException("Additive update in " + tableName + " matched 0 rows for"
                    + " origin_repo_key=" + entity.originRepoKey()
                    + " repo_key=" + entity.repoKey()
                    + " update_time=" + entity.updateTime());
        }
    }

    /**
     * Retrieves records for a specific origin repo within a time range.
     *
     * @param originRepoKey the origin repository key
     * @param startTime     start time (inclusive) in epoch ms
     * @param endTime       end time (inclusive) in epoch ms
     * @return list of matching stats entities ordered by update_time descending
     */
    public List<RepoTrafficStatsEntity> getByOriginRepoKeyAndDateRange(
            String originRepoKey, long startTime, long endTime) {
        try {
            String sql = "SELECT " + SELECT_COLUMNS
                    + " FROM " + tableName
                    + " WHERE origin_repo_key = ?"
                    + " AND update_time >= ? AND update_time <= ?"
                    + " ORDER BY update_time DESC";
            try (ResultSet rs = jdbcHelper.executeSelect(sql, originRepoKey, startTime, endTime)) {
                return mapToEntityList(rs);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve records from " + tableName
                            + " for origin_repo_key=" + originRepoKey, e);
        }
    }

    /**
     * Retrieves all records within a time range.
     *
     * @param startTime start time (inclusive) in epoch ms
     * @param endTime   end time (inclusive) in epoch ms
     * @return list of matching stats entities ordered by update_time descending
     */
    public List<RepoTrafficStatsEntity> getByDateRange(long startTime, long endTime) {
        try {
            String sql = "SELECT " + SELECT_COLUMNS
                    + " FROM " + tableName
                    + " WHERE update_time >= ? AND update_time <= ?"
                    + " ORDER BY update_time DESC";
            try (ResultSet rs = jdbcHelper.executeSelect(sql, startTime, endTime)) {
                return mapToEntityList(rs);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve records from " + tableName + " by time range", e);
        }
    }

    /**
     * Retrieves records for a specific actor within a time range.
     *
     * @param actor     the actor type (USER, XRAY, REPLICATOR, etc.)
     * @param startTime start time (inclusive) in epoch ms
     * @param endTime   end time (inclusive) in epoch ms
     * @return list of matching stats entities ordered by update_time descending
     */
    public List<RepoTrafficStatsEntity> getByActorAndDateRange(
            String actor, long startTime, long endTime) {
        try {
            String sql = "SELECT " + SELECT_COLUMNS
                    + " FROM " + tableName
                    + " WHERE actor = ?"
                    + " AND update_time >= ? AND update_time <= ?"
                    + " ORDER BY update_time DESC";
            try (ResultSet rs = jdbcHelper.executeSelect(sql, actor, startTime, endTime)) {
                return mapToEntityList(rs);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve records from " + tableName
                            + " for actor=" + actor, e);
        }
    }

    /**
     * Retrieves records for a specific resolved repo within a time range.
     *
     * @param repoKey   the resolved repository key
     * @param startTime start time (inclusive) in epoch ms
     * @param endTime   end time (inclusive) in epoch ms
     * @return list of matching stats entities ordered by update_time descending
     */
    public List<RepoTrafficStatsEntity> getByResolvedRepoKey(
            String repoKey, long startTime, long endTime) {
        try {
            String sql = "SELECT " + SELECT_COLUMNS
                    + " FROM " + tableName
                    + " WHERE repo_key = ?"
                    + " AND update_time >= ? AND update_time <= ?"
                    + " ORDER BY update_time DESC";
            try (ResultSet rs = jdbcHelper.executeSelect(sql, repoKey, startTime, endTime)) {
                return mapToEntityList(rs);
            }
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve records from " + tableName
                            + " for repo_key=" + repoKey, e);
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

    private List<RepoTrafficStatsEntity> mapToEntityList(ResultSet rs) throws SQLException {
        List<RepoTrafficStatsEntity> results = new ArrayList<>();
        while (rs.next()) {
            results.add(RepoTrafficStatsEntity.builder()
                    .id(rs.getLong("id"))
                    .updateTime(rs.getLong("update_time"))
                    .action(rs.getString("action"))
                    .actor(rs.getString("actor"))
                    .redirectTarget(rs.getString("redirect_target"))
                    .originRepoKey(rs.getString("origin_repo_key"))
                    .originRepoType(rs.getString("origin_repo_type"))
                    .originPackageType(rs.getString("origin_package_type"))
                    .repoKey(rs.getString("repo_key"))
                    .repoType(rs.getString("repo_type"))
                    .packageType(rs.getString("package_type"))
                    .requestCount(rs.getLong("request_count"))
                    .totalTransferredSize(rs.getLong("total_transferred_size"))
                    .build());
        }
        return results;
    }
}
