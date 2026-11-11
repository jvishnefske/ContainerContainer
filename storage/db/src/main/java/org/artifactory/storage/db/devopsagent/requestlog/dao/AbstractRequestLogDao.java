package org.artifactory.storage.db.devopsagent.requestlog.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogIdentifiable;
import org.artifactory.storage.db.devopsagent.requestlog.model.Cursor;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Package-private abstract base for request-log DAOs.
 * Holds all shared SQL logic; subclasses supply table-specific hooks.
 *
 * @param <R> the concrete row type (must implement {@link RequestLogIdentifiable})
 */
abstract class AbstractRequestLogDao<R extends RequestLogIdentifiable> extends BaseDao {

    protected final DbType dbType;
    protected final String tableName;
    private final String selectColumns;
    private final String insertSql;

    AbstractRequestLogDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties,
            String tableName, String selectColumns, String insertSql) {
        super(jdbcHelper);
        this.dbType = dbProperties.getDbType();
        this.tableName = tableName;
        this.selectColumns = selectColumns;
        this.insertSql = insertSql;
    }

    // -------------------------------------------------------------------------
    // Abstract hooks — each subclass provides these
    // -------------------------------------------------------------------------

    /**
     * Fills exactly 7 consecutive entries in {@code params} starting at {@code base}
     * with the row's non-id fields in the same order as the INSERT column list.
     */
    abstract void bindInsertParams(R row, Object[] params, int base);

    /** Maps the current {@link ResultSet} row to a concrete row object. */
    abstract R mapRow(ResultSet rs) throws SQLException;

    /**
     * Appends the FTS / full-text search WHERE clause for this table type.
     * Called after time/duration filters and {@link #appendExtraFilters}.
     */
    abstract void appendSearchClause(WhereClause where, List<Object> params, SearchCriteria criteria);

    /**
     * Hook for table-specific extra WHERE filters (e.g. {@code repo_key IN (…)}).
     * Default implementation is a no-op; subclasses override as needed.
     */
    void appendExtraFilters(WhereClause where, List<Object> params, SearchCriteria criteria) {
    }

    // -------------------------------------------------------------------------
    // Concrete shared methods
    // -------------------------------------------------------------------------

    public int insertBatch(List<R> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement(insertSql)) {
            Object[] rowParams = new Object[7];
            for (R row : rows) {
                bindInsertParams(row, rowParams, 0);
                for (int j = 0; j < 7; j++) {
                    ps.setObject(j + 1, rowParams[j]);
                }
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            int total = 0;
            for (int c : counts) {
                total += (c == Statement.SUCCESS_NO_INFO) ? 1 : Math.max(0, c);
            }
            return total;
        }
    }

    public List<R> search(SearchCriteria criteria) throws SQLException {
        RequestLogDaoSupport.QueryParts parts = buildSearchQuery(criteria);
        try (ResultSet rs = jdbcHelper.executeSelect(parts.sql(), parts.params().toArray())) {
            return readRows(rs);
        }
    }

    public List<R> findByTraceId(String traceId, long timeFrom, long timeTo, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder(256);
        List<Object> params = new ArrayList<>();
        sql.append("SELECT ").append(selectColumns).append(" FROM ").append(tableName)
                .append(" WHERE trace_id = ? AND request_time BETWEEN ? AND ?")
                .append(" ORDER BY request_time ASC, id ASC");
        params.add(traceId);
        params.add(timeFrom);
        params.add(timeTo);
        RequestLogDaoSupport.appendPagination(dbType, sql, limit, 0);
        try (ResultSet rs = jdbcHelper.executeSelect(sql.toString(), params.toArray())) {
            return readRows(rs);
        }
    }

    public int deleteOlderThanChunked(long cutoffMs, int batchSize, long perBatchSleepMs) throws SQLException {
        return RequestLogDaoSupport.deleteOlderThanChunked(
                jdbcHelper, dbType, tableName, cutoffMs, batchSize, perBatchSleepMs);
    }

    RequestLogDaoSupport.QueryParts buildSearchQuery(SearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(512);
        List<Object> params = new ArrayList<>();
        sql.append("SELECT ").append(selectColumns).append(" FROM ").append(tableName);

        WhereClause where = new WhereClause(sql);
        if (criteria.timeFrom() != null) {
            where.append("request_time >= ?");
            params.add(criteria.timeFrom());
        }
        if (criteria.timeTo() != null) {
            where.append("request_time <= ?");
            params.add(criteria.timeTo());
        }
        if (criteria.minDurationMs() > 0) {
            where.append("duration_ms >= ?");
            params.add(criteria.minDurationMs());
        }
        appendExtraFilters(where, params, criteria);
        appendSearchClause(where, params, criteria);
        if (criteria.cursor() != null) {
            Cursor cursor = criteria.cursor();
            where.append("(request_time < ? OR (request_time = ? AND id < ?))");
            params.add(cursor.requestTime());
            params.add(cursor.requestTime());
            params.add(cursor.id());
        }
        sql.append(" ORDER BY request_time DESC, id DESC");
        int offset = criteria.cursor() == null ? criteria.offset() : 0;
        RequestLogDaoSupport.appendPagination(dbType, sql, criteria.limit() + 1, offset);
        return new RequestLogDaoSupport.QueryParts(sql.toString(), params);
    }

    static final class WhereClause {
        private final StringBuilder sql;
        private boolean first = true;

        WhereClause(StringBuilder sql) {
            this.sql = sql;
        }

        void append(String condition) {
            sql.append(first ? " WHERE " : " AND ").append(condition);
            first = false;
        }
    }

    List<R> readRows(ResultSet rs) throws SQLException {
        List<R> out = new ArrayList<>();
        while (rs.next()) {
            out.add(mapRow(rs));
        }
        return out;
    }
}
