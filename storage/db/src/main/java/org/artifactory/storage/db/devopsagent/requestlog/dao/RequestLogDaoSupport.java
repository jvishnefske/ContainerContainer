package org.artifactory.storage.db.devopsagent.requestlog.dao;

import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Per-{@link DbType} SQL fragments for request-log DAOs (FTS, pagination, retention).
 */
final class RequestLogDaoSupport {

    private RequestLogDaoSupport() {
    }

    static void appendInboundSearchClause(DbType dbType, AbstractRequestLogDao.WhereClause where,
            List<Object> params, SearchCriteria criteria) {
        if (!hasSearch(criteria)) {
            return;
        }
        switch (dbType) {
            case POSTGRESQL -> appendPostgresFtsClause(where, params, criteria);
            case MYSQL, MARIADB -> {
                where.append("MATCH(request_path, username) AGAINST(? IN NATURAL LANGUAGE MODE)");
                params.add(criteria.rawSearchText());
            }
            case ORACLE -> {
                where.append("LOWER(NVL(request_path, '') || ' ' || NVL(username, '') || ' '"
                        + " || NVL(TO_CHAR(status_code), '')) LIKE ? ESCAPE '\\'");
                params.add(likePattern(criteria.rawSearchText()));
            }
            case MSSQL -> {
                where.append("LOWER(CONCAT(ISNULL(request_path, ''), ' ', ISNULL(username, ''), ' ', "
                        + "ISNULL(CAST(status_code AS VARCHAR(10)), ''))) LIKE ? ESCAPE '\\'");
                params.add(likePattern(criteria.rawSearchText()));
            }
            case DERBY -> {
                where.append("LOWER(COALESCE(request_path, '') || ' ' || COALESCE(username, '') || ' '"
                        + " || COALESCE(CAST(status_code AS VARCHAR(10)), '')) LIKE ? ESCAPE '\\'");
                params.add(likePattern(criteria.rawSearchText()));
            }
            default -> throw new IllegalStateException("Unsupported DB type for request_logs search: " + dbType);
        }
    }

    static void appendOutboundSearchClause(DbType dbType, AbstractRequestLogDao.WhereClause where,
            List<Object> params, SearchCriteria criteria) {
        if (!hasSearch(criteria)) {
            return;
        }
        switch (dbType) {
            case POSTGRESQL -> appendPostgresFtsClause(where, params, criteria);
            case MYSQL, MARIADB -> {
                where.append("MATCH(request_url, repo_key, http_method, trace_id)"
                        + " AGAINST(? IN NATURAL LANGUAGE MODE)");
                params.add(criteria.rawSearchText());
            }
            case ORACLE -> {
                where.append("LOWER(NVL(request_url, '') || ' ' || NVL(repo_key, '')"
                        + " || ' ' || NVL(http_method, '') || ' ' || NVL(trace_id, '') || ' '"
                        + " || NVL(TO_CHAR(status_code), '')) LIKE ? ESCAPE '\\'");
                params.add(likePattern(criteria.rawSearchText()));
            }
            case MSSQL -> {
                where.append("LOWER(CONCAT(ISNULL(request_url, ''), ' ', ISNULL(repo_key, ''), ' ', "
                        + "ISNULL(http_method, ''), ' ', ISNULL(trace_id, ''), ' ', "
                        + "ISNULL(CAST(status_code AS VARCHAR(10)), ''))) LIKE ? ESCAPE '\\'");
                params.add(likePattern(criteria.rawSearchText()));
            }
            case DERBY -> {
                where.append("LOWER(COALESCE(request_url, '') || ' ' || COALESCE(repo_key, '') || ' '"
                        + " || COALESCE(http_method, '') || ' '"
                        + " || COALESCE(trace_id, '') || ' '"
                        + " || COALESCE(CAST(status_code AS VARCHAR(10)), '')) LIKE ? ESCAPE '\\'");
                params.add(likePattern(criteria.rawSearchText()));
            }
            default -> throw new IllegalStateException(
                    "Unsupported DB type for outbound_request_logs search: " + dbType);
        }
    }

    static void appendPagination(DbType dbType, StringBuilder sql, int limit, int offset) {
        if (dbType == DbType.MSSQL) {
            sql.append(" OFFSET ").append(offset).append(" ROWS FETCH NEXT ")
                    .append(limit).append(" ROWS ONLY");
            return;
        }
        if (dbType == DbType.ORACLE || dbType == DbType.DERBY) {
            if (offset > 0) {
                sql.append(" OFFSET ").append(offset).append(" ROWS");
            }
            sql.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");
            return;
        }
        sql.append(" LIMIT ").append(limit);
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }

    static int deleteOlderThanChunked(ArtifactoryJdbcHelper jdbcHelper, DbType dbType,
            String tableName, long cutoffMs, int batchSize, long perBatchSleepMs) throws SQLException {
        String deleteSql = buildDeleteSql(dbType, tableName, batchSize);
        int totalDeleted = 0;
        boolean keepRunning = true;
        while (keepRunning) {
            int deleted = jdbcHelper.executeUpdate(deleteSql, cutoffMs);
            totalDeleted += deleted;
            keepRunning = deleted >= batchSize;
            if (keepRunning && perBatchSleepMs > 0) {
                try {
                    Thread.sleep(perBatchSleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    keepRunning = false;
                }
            }
        }
        return totalDeleted;
    }

    private static String buildDeleteSql(DbType dbType, String tableName, int batchSize) {
        return switch (dbType) {
            case MSSQL -> "DELETE TOP (" + batchSize + ") FROM " + tableName + " WHERE request_time < ?";
            case ORACLE -> "DELETE FROM " + tableName
                    + " WHERE id IN (SELECT id FROM " + tableName
                    + " WHERE request_time < ? AND ROWNUM <= " + batchSize + ")";
            case DERBY -> "DELETE FROM " + tableName
                    + " WHERE id IN (SELECT id FROM " + tableName
                    + " WHERE request_time < ? FETCH FIRST " + batchSize + " ROWS ONLY)";
            case POSTGRESQL -> "DELETE FROM " + tableName
                    + " WHERE id IN (SELECT id FROM " + tableName
                    + " WHERE request_time < ? LIMIT " + batchSize + ")";
            case MYSQL, MARIADB -> "DELETE FROM " + tableName + " WHERE request_time < ? LIMIT " + batchSize;
            default -> throw new IllegalStateException(
                    "Unsupported DB type for request_logs chunked DELETE: " + dbType);
        };
    }

    private static void appendPostgresFtsClause(AbstractRequestLogDao.WhereClause where, List<Object> params,
            SearchCriteria criteria) {
        where.append("search_vector @@ to_tsquery('simple', ?)");
        params.add(criteria.searchQuery());
    }

    static boolean hasSearch(SearchCriteria criteria) {
        if (criteria.searchQuery() != null && !criteria.searchQuery().isBlank()) {
            return true;
        }
        return criteria.rawSearchText() != null && !criteria.rawSearchText().isBlank();
    }

    static void appendPlaceholders(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
    }

    record QueryParts(String sql, List<Object> params) {
    }

    private static String likePattern(String rawSearchText) {
        String escaped = rawSearchText.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
