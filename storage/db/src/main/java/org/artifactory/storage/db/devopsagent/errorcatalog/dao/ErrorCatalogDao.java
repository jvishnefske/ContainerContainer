package org.artifactory.storage.db.devopsagent.errorcatalog.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.devopsagent.errorcatalog.entity.RawEventRow;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.KeysetCursor;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.RawEventInsertRequest;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.DbType;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DAO for the {@code error_catalog} (raw) table — append-only.
 * Retention is performed uniformly across all supported DB engines via chunked DELETE.
 */
@Repository
@Slf4j
public class ErrorCatalogDao extends BaseDao {

    public static final String TABLE = "error_catalog";

    private static final String SELECT_COLUMNS = "id, template_id, occurred_at, raw_message";

    private final DbType dbType;

    public ErrorCatalogDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        this.dbType = dbProperties.getDbType();
    }

    /**
     * Batched INSERT of raw events. One PreparedStatement, addBatch per event, single executeBatch.
     */
    public int insertBatch(List<RawEventInsertRequest> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO " + TABLE + " (template_id, occurred_at, raw_message) VALUES (?, ?, ?)";
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (RawEventInsertRequest e : events) {
                ps.setLong(1, e.templateId());
                ps.setLong(2, e.occurredAt());
                ps.setString(3, e.rawMessage());
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            int total = 0;
            for (int c : counts) {
                // Batch returns -2 (Statement.SUCCESS_NO_INFO) on some drivers; treat as 1.
                total += (c == Statement.SUCCESS_NO_INFO) ? 1 : Math.max(0, c);
            }
            return total;
        } catch (SQLException e) {
            throw new StorageException("Failed to batch-insert into " + TABLE, e);
        }
    }

    /**
     * Drill-down: all raw events for a template, ordered by {@code occurred_at} descending.
     * Keyset pagination on {@code (occurred_at, id)} keeps depth-stable.
     */
    public List<RawEventRow> findByTemplate(long templateId, KeysetCursor cursor, int limit) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(SELECT_COLUMNS).append(" FROM ").append(TABLE)
                .append(" WHERE template_id = ?");
        params.add(templateId);
        if (cursor != null) {
            sql.append(" AND (occurred_at < ? OR (occurred_at = ? AND id < ?))");
            params.add(cursor.timeValue());
            params.add(cursor.timeValue());
            params.add(cursor.id());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC");
        appendLimit(sql, limit);
        try (ResultSet rs = jdbcHelper.executeSelect(sql.toString(), params.toArray())) {
            List<RawEventRow> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapRow(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new StorageException("Failed to query " + TABLE + " by template_id", e);
        }
    }

    public List<RawEventRow> findByOccurredBetween(long fromMs, long toMsExclusive, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ").append(SELECT_COLUMNS).append(" FROM ").append(TABLE)
                .append(" WHERE occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC, id DESC");
        appendLimit(sql, limit);
        try (ResultSet rs = jdbcHelper.executeSelect(sql.toString(), fromMs, toMsExclusive)) {
            List<RawEventRow> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapRow(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new StorageException("Failed to query " + TABLE + " by occurred_at range", e);
        }
    }

    /**
     * Multi-template drill-down with optional time window. {@code templateIds} MUST be non-empty —
     * the service layer rejects empty input with {@code 400 'template_ids'} before this method is
     * called. Passing an empty list here is undefined behaviour. Walks the existing
     * {@code (template_id, occurred_at DESC)} index for the multi-id IN + range + keyset query
     * (BitmapOr over per-id seeks on PostgreSQL).
     *
     * @param templateIds      non-empty list of template ids, walked as {@code IN (?, ?, ...)}
     * @param fromMs           inclusive lower bound on {@code occurred_at}; {@code null} skips
     * @param toMsExclusive    exclusive upper bound on {@code occurred_at}; {@code null} skips
     * @param cursor           keyset cursor from a prior page; {@code null} for the first page
     * @param limit            maximum rows to return; the service has already clamped this
     * @return rows ordered {@code occurred_at DESC, id DESC}
     */
    public List<RawEventRow> findByTemplatesAndTimeRange(List<Long> templateIds, Long fromMs, Long toMsExclusive,
            KeysetCursor cursor, int limit) {
        List<Object> params = new ArrayList<>(templateIds.size() + 5);
        StringBuilder sql = new StringBuilder("SELECT ").append(SELECT_COLUMNS).append(" FROM ").append(TABLE)
                .append(" WHERE template_id IN (")
                .append(String.join(",", Collections.nCopies(templateIds.size(), "?")))
                .append(")");
        params.addAll(templateIds);
        if (fromMs != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(fromMs);
        }
        if (toMsExclusive != null) {
            sql.append(" AND occurred_at < ?");
            params.add(toMsExclusive);
        }
        if (cursor != null) {
            sql.append(" AND (occurred_at < ? OR (occurred_at = ? AND id < ?))");
            params.add(cursor.timeValue());
            params.add(cursor.timeValue());
            params.add(cursor.id());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC");
        appendLimit(sql, limit);
        try (ResultSet rs = jdbcHelper.executeSelect(sql.toString(), params.toArray())) {
            List<RawEventRow> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapRow(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new StorageException("Failed to query " + TABLE + " by template_ids + range", e);
        }
    }

    /**
     * Chunked DELETE for retention — uniform across all supported DB engines now that the table
     * is non-partitioned. Returns the total number of rows deleted. Honors interruption between batches.
     */
    public int deleteOlderThanChunked(long cutoffMs, int batchSize, long perBatchSleepMs) {
        int totalDeleted = 0;
        String sql = buildChunkedDeleteSql(batchSize);
        while (!Thread.currentThread().isInterrupted()) {
            int deleted;
            try {
                deleted = jdbcHelper.executeUpdate(sql, cutoffMs);
            } catch (SQLException e) {
                throw new StorageException("Chunked DELETE on " + TABLE + " failed at " + totalDeleted + " rows", e);
            }
            if (deleted == 0) {
                break;
            }
            totalDeleted += deleted;
            if (perBatchSleepMs > 0) {
                try {
                    Thread.sleep(perBatchSleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.debug("Chunked DELETE on {} interrupted after {} rows", TABLE, totalDeleted);
                    return totalDeleted;
                }
            }
        }
        return totalDeleted;
    }

    private String buildChunkedDeleteSql(int batchSize) {
        // Each DB has its own way to bound a DELETE.
        return switch (dbType) {
            case POSTGRESQL -> "DELETE FROM " + TABLE
                    + " WHERE id IN (SELECT id FROM " + TABLE + " WHERE occurred_at < ? LIMIT " + batchSize + ")";
            case MYSQL, MARIADB -> "DELETE FROM " + TABLE + " WHERE occurred_at < ? LIMIT " + batchSize;
            case ORACLE -> "DELETE FROM " + TABLE
                    + " WHERE occurred_at < ? AND ROWNUM <= " + batchSize;
            case MSSQL -> "DELETE TOP (" + batchSize + ") FROM " + TABLE + " WHERE occurred_at < ?";
            case DERBY -> "DELETE FROM " + TABLE
                    + " WHERE id IN (SELECT id FROM " + TABLE + " WHERE occurred_at < ? FETCH FIRST "
                    + batchSize + " ROWS ONLY)";
            default -> throw new StorageException("Unsupported DB type for chunked DELETE: " + dbType);
        };
    }

    public int deleteAll() {
        try {
            return jdbcHelper.executeUpdate("DELETE FROM " + TABLE);
        } catch (SQLException e) {
            throw new StorageException("Failed to delete all from " + TABLE, e);
        }
    }

    public long count() {
        try (ResultSet rs = jdbcHelper.executeSelect("SELECT COUNT(*) FROM " + TABLE)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new StorageException("Failed to count " + TABLE, e);
        }
    }

    private void appendLimit(StringBuilder sql, int limit) {
        // T-SQL rejects FETCH without a preceding OFFSET, so emit OFFSET 0 ROWS even for limit-only.
        if (dbType == DbType.MSSQL) {
            sql.append(" OFFSET 0 ROWS FETCH NEXT ").append(limit).append(" ROWS ONLY");
            return;
        }
        if (dbType == DbType.ORACLE || dbType == DbType.DERBY) {
            sql.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");
            return;
        }
        sql.append(" LIMIT ").append(limit);
    }

    private RawEventRow mapRow(ResultSet rs) throws SQLException {
        return new RawEventRow(
                rs.getLong("id"),
                rs.getLong("template_id"),
                rs.getLong("occurred_at"),
                rs.getString("raw_message"));
    }
}
