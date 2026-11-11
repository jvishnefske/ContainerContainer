package org.artifactory.storage.db.version.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.jfrog.converter.AbstractDbConverter;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.PostgresqlUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.BiPredicate;

import static org.artifactory.common.ConstantValues.dbPostgresIndexInProgressCheck;
import static org.artifactory.common.ConstantValues.dbPostgresIndexInProgressSamplesTimeMillis;
import static org.artifactory.common.ConstantValues.dbPostgresIndexInProgressWaitMaxTimeMillis;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
public class SafeIndexConcurrentCreator extends AbstractDbConverter {
    private final String indexName;
    private final String tableName;
    private final List<String> columnNames;
    private final String customQuery;

    @SafeVarargs
    public SafeIndexConcurrentCreator(String indexName, String tableName, List<String> columnNames,
            String customQuery, BiPredicate<Connection, DbType>... conditions) {
        super("Creating index: " + indexName + " concurrently.", conditions);
        this.indexName = indexName;
        this.tableName = tableName;
        this.columnNames = columnNames;
        this.customQuery = customQuery;
    }

    @SafeVarargs
    public SafeIndexConcurrentCreator(String indexName, String tableName, List<String> columnsNames,
            BiPredicate<Connection, DbType>... conditions) {
        super("Creating index: " + indexName + " concurrently.", conditions);
        this.indexName = indexName;
        this.tableName = tableName;
        this.columnNames = columnsNames;
        this.customQuery = "";
    }

    boolean indexExists(Connection conn, DbType dbType) throws SQLException {
        for (String columnName : columnNames) {
            if (!DbUtils.indexExists(conn, dbType, tableName, columnName, indexName, false)) {
                return false;
            }
        }
        return true;
    }

    boolean isIndexValid(Connection conn) throws SQLException {
        return PostgresqlUtils.isIndexValid(conn, indexName);
    }

    void createIndexConcurrently(Connection conn) throws SQLException {
        log.info("Index not exists, creating index {} concurrently.", indexName);
        if (StringUtils.isBlank(customQuery)) {
            PostgresqlUtils.createIndexConcurrently(conn, tableName, indexName, columnNames);
        } else {
            PostgresqlUtils.createCustomIndexConcurrently(conn, customQuery);
        }
    }

    boolean checkIndexExistsAndValidBeforeCreation(Connection conn, DbType dbType) throws SQLException {
        if (indexExists(conn, dbType)) {
            log.info("Index {} already exists - Checking if is valid.", indexName);
            if (isIndexValid(conn)) {
                log.info("Index {} is valid - Skipping upgrade.", indexName);
                return true;
            } else {
                log.warn("Index {} invalid - Dropping index.", indexName);
                DbUtils.dropIndex(conn, indexName);
            }
        }
        return false;
    }

    void assertIndexExistsAndValidAfterCreation(Connection conn, DbType dbType)
            throws SQLException, DbConverterExecutionException {
        String msg = "Could not convert DB: CREATE INDEX CONCURRENTLY " + indexName;
        if (indexExists(conn, dbType)) {
            if (!isIndexValid(conn)) {
                log.warn("Index {} invalid after concurrent creation - Dropping index.", indexName);
                DbUtils.dropIndex(conn, indexName);
                log.error("{} due to {}.", msg, "Index invalid after creation.");
                throw new DbConverterExecutionException(msg);
            }
        } else {
            log.error("{} due to {}", msg, "Index not exists after creation.");
            throw new DbConverterExecutionException(msg);
        }
        log.info("Index {} created successfully.", indexName);
    }

    void waitWhileIndexCreationInProgress(ChangeLogContext context) throws DbConverterExecutionException, SQLException {
        if (dbPostgresIndexInProgressCheck.getBoolean()) {
            log.info("Checking if index creation for {} has already started.", indexName);
            long startTime = System.currentTimeMillis();
            long maxDuration = dbPostgresIndexInProgressWaitMaxTimeMillis.getLong();
            while (System.currentTimeMillis() - startTime < maxDuration) {
                try {
                    Connection conn = context.getConnection();
                    if (PostgresqlUtils.isIndexCreationInProgress(conn, indexName)) {
                        log.info("Index creation for {} is in progress...", indexName);
                        Thread.sleep(dbPostgresIndexInProgressSamplesTimeMillis.getLong());
                    } else {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    String msg = "Failure while checking if index creation of {} is still in progress.";
                    throw new DbConverterExecutionException(String.format(msg, indexName), e);
                }
            }
            // Handle the case when the maximum duration is reached.
            String msg = "Timed out waiting for index {} creation to complete, Please check the index status.";
            throw new DbConverterExecutionException(String.format(msg, indexName));
        }
    }

    private void setNetworkTimeout(Connection connection, int timeoutMs) throws SQLException {
        JdbcHelper jdbcHelper = ContextHelper.get().beanForType(JdbcHelper.class);
        connection.setNetworkTimeout(jdbcHelper.getDataSource().getNetworkTimeoutExecutor(), timeoutMs);
    }

    private void setStatementTimeout(Connection connection, int timeoutMs) throws SQLException {
        if (timeoutMs != -1) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("set statement_timeout = " + timeoutMs);
            }
        }
    }

    private void restoreDefaultNetworkTimeout(Connection connection, int originalNetworkTimeout) {
        if (originalNetworkTimeout != -1) {
            try {
                log.info("Restoring connection network timeout.");
                setNetworkTimeout(connection, originalNetworkTimeout);
            } catch (Exception e) {
                log.warn("Failed to restore connection network timeout.");
            }
        }
    }

    private void restoreDefaultStatementTimeout(Connection conn, int originalStatementTimeout) {
        log.info("Restoring the connection statement timeout.");
        try {
            setStatementTimeout(conn, originalStatementTimeout);
        } catch (Exception e) {
            log.warn("Failed to restore connection statement timeout.");
        }
    }

    private int getStatementTimeout(Connection connection) throws SQLException {
        try(ResultSet rs = connection.prepareStatement(
                "select setting from pg_settings where pg_settings.name = 'statement_timeout'").executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    @Override
    protected void executeConverter(ChangeLogContext context) throws DbConverterExecutionException {
        try {
            waitWhileIndexCreationInProgress(context);
        } catch (SQLException e) {
            String msg = "Failure while checking if index creation of {} is still in progress.";
            throw new DbConverterExecutionException(String.format(msg, indexName), e);
        }
        int originalNetworkTimeout = -1;
        int originalStatementTimeout = -1;
        Connection connection = context.getConnection();
        try {
            DbType dbType = context.getDbType();
            if (checkIndexExistsAndValidBeforeCreation(connection, dbType)) {
                return;
            }
            originalNetworkTimeout = connection.getNetworkTimeout();
            originalStatementTimeout = getStatementTimeout(connection);
            log.info("Setting the connection timeout to 10 hours to avoid read timeout while index creating.");
            setNetworkTimeout(connection, 10 * 3600 * 1000);
            log.info("Disabling statement timeout to ensure uninterrupted execution during index creation.");
            setStatementTimeout(connection, 0);
            createIndexConcurrently(connection);
            assertIndexExistsAndValidAfterCreation(connection, dbType);
        } catch (SQLException e) {
            String msg = "Could not convert DB: CREATE INDEX CONCURRENTLY " + indexName;
            log.error("{} due to {}", msg, e.getMessage(), e);
            throw new DbConverterExecutionException(msg, e);
        } finally {
            restoreDefaultStatementTimeout(connection, originalStatementTimeout);
            restoreDefaultNetworkTimeout(connection, originalNetworkTimeout);
        }
    }
}