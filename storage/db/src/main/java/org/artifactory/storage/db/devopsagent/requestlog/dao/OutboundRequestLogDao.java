package org.artifactory.storage.db.devopsagent.requestlog.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * DAO for the {@code outbound_request_logs} table (outbound HTTP requests via Remote Repos).
 * FTS: PG {@code search_vector} + {@code to_tsquery}; MySQL/MariaDB FULLTEXT; others {@code LIKE}.
 */
@Repository
public class OutboundRequestLogDao extends AbstractRequestLogDao<OutboundRequestLogRow> {

    public static final String TABLE_NAME = "outbound_request_logs";

    private static final String SELECT_COLUMNS =
            "id, request_time, trace_id, repo_key, http_method, request_url, status_code, duration_ms";

    private static final String INSERT_COLUMN_LIST =
            "(request_time, trace_id, repo_key, http_method, request_url, status_code, duration_ms)";

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE_NAME + " " + INSERT_COLUMN_LIST + " VALUES (?,?,?,?,?,?,?)";

    public OutboundRequestLogDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper, dbProperties, TABLE_NAME, SELECT_COLUMNS, INSERT_SQL);
    }

    @Override
    void bindInsertParams(OutboundRequestLogRow row, Object[] params, int base) {
        params[base] = row.requestTime();
        params[base + 1] = row.traceId();
        params[base + 2] = row.repoKey();
        params[base + 3] = row.httpMethod();
        params[base + 4] = row.requestUrl();
        params[base + 5] = row.statusCode();
        params[base + 6] = row.durationMs();
    }

    @Override
    OutboundRequestLogRow mapRow(ResultSet rs) throws SQLException {
        return OutboundRequestLogRow.builder()
                .id(rs.getLong("id"))
                .requestTime(rs.getLong("request_time"))
                .traceId(rs.getString("trace_id"))
                .repoKey(rs.getString("repo_key"))
                .httpMethod(rs.getString("http_method"))
                .requestUrl(rs.getString("request_url"))
                .statusCode(rs.getInt("status_code"))
                .durationMs(rs.getLong("duration_ms"))
                .build();
    }

    @Override
    void appendSearchClause(WhereClause where, List<Object> params, SearchCriteria criteria) {
        RequestLogDaoSupport.appendOutboundSearchClause(dbType, where, params, criteria);
    }

    @Override
    void appendExtraFilters(WhereClause where, List<Object> params, SearchCriteria criteria) {
        if (criteria.repoNames() != null && !criteria.repoNames().isEmpty()) {
            StringBuilder inClause = new StringBuilder("repo_key IN (");
            RequestLogDaoSupport.appendPlaceholders(inClause, criteria.repoNames().size());
            inClause.append(")");
            where.append(inClause.toString());
            params.addAll(criteria.repoNames());
        }
    }
}
