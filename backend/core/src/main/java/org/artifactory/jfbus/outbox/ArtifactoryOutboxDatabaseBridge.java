package org.artifactory.jfbus.outbox;

import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.eventing.jfbus.outbox.bridge.DatabaseType;
import org.jfrog.eventing.jfbus.outbox.bridge.OutboxDatabaseBridge;
import org.jfrog.eventing.jfbus.outbox.bridge.ResultSetExtractor;
import org.jfrog.storage.DbType;
import org.springframework.jdbc.datasource.DataSourceUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Implements {@link OutboxDatabaseBridge} backed by Artifactory's {@link ArtifactoryJdbcHelper}.
 * <p>
 * Participates in Spring-managed transactions via {@link DataSourceUtils}.
 *
 * @author IgorG
 */
public class ArtifactoryOutboxDatabaseBridge implements OutboxDatabaseBridge {

    private final ArtifactoryJdbcHelper jdbcHelper;
    private final DatabaseType databaseType;

    public ArtifactoryOutboxDatabaseBridge(ArtifactoryJdbcHelper jdbcHelper, DbType dbType) {
        this.jdbcHelper = jdbcHelper;
        this.databaseType = mapDatabaseType(dbType);
    }

    @Override
    public <T> T executeSelect(ResultSetExtractor<T> extractor, String sql, Object... params) throws SQLException {
        try (ResultSet rs = jdbcHelper.executeSelectForSmallResults(sql, params)) {
            return extractor.extract(rs);
        }
    }

    @Override
    public int executeUpdate(String sql, Object... params) throws SQLException {
        return jdbcHelper.executeUpdate(sql, params);
    }

    @Override
    public int executeUpdateWithTimeout(int timeoutSec, String sql, Object... params) throws SQLException {
        var dataSource = jdbcHelper.getDataSource();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(timeoutSec);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    @Override
    public void executeBatch(String sql, List<Object[]> batchParams) throws SQLException {
        jdbcHelper.executeBulkUpdate(sql, batchParams);
    }

    @Override
    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    private static DatabaseType mapDatabaseType(DbType dbType) {
        if (dbType == null) {
            return DatabaseType.GENERIC;
        }
        return switch (dbType) {
            case POSTGRESQL -> DatabaseType.POSTGRESQL;
            case MYSQL -> DatabaseType.MYSQL;
            case MARIADB -> DatabaseType.MARIADB;
            case ORACLE -> DatabaseType.ORACLE;
            case MSSQL -> DatabaseType.MSSQL;
            case DERBY -> DatabaseType.DERBY;
            default -> DatabaseType.GENERIC;
        };
    }
}
