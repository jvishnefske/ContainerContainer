package org.artifactory.storage.db.devopsagent.requestlog.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * DAO for the {@code request_logs} table (inbound HTTP requests).
 * FTS: PG {@code search_vector} + {@code to_tsquery}; MySQL/MariaDB FULLTEXT; others {@code LIKE}.
 */
@Repository
public class RequestLogDao extends AbstractRequestLogDao<RequestLogRow> {

    public static final String TABLE_NAME = "request_logs";

    private static final String SELECT_COLUMNS =
            "id, request_time, trace_id, username, http_method, request_path, status_code, duration_ms";

    private static final String INSERT_COLUMN_LIST =
            "(request_time, trace_id, username, http_method, request_path, status_code, duration_ms)";

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE_NAME + " " + INSERT_COLUMN_LIST + " VALUES (?,?,?,?,?,?,?)";

    public RequestLogDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper, dbProperties, TABLE_NAME, SELECT_COLUMNS, INSERT_SQL);
    }

    @Override
    void bindInsertParams(RequestLogRow row, Object[] params, int base) {
        params[base] = row.requestTime();
        params[base + 1] = row.traceId();
        params[base + 2] = row.username();
        params[base + 3] = row.httpMethod();
        params[base + 4] = row.requestPath();
        params[base + 5] = row.statusCode();
        params[base + 6] = row.durationMs();
    }

    @Override
    RequestLogRow mapRow(ResultSet rs) throws SQLException {
        return RequestLogRow.builder()
                .id(rs.getLong("id"))
                .requestTime(rs.getLong("request_time"))
                .traceId(rs.getString("trace_id"))
                .username(rs.getString("username"))
                .httpMethod(rs.getString("http_method"))
                .requestPath(rs.getString("request_path"))
                .statusCode(rs.getInt("status_code"))
                .durationMs(rs.getLong("duration_ms"))
                .build();
    }

    @Override
    void appendSearchClause(WhereClause where, List<Object> params, SearchCriteria criteria) {
        RequestLogDaoSupport.appendInboundSearchClause(dbType, where, params, criteria);
    }
}
