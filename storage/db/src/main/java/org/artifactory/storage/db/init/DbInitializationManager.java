package org.artifactory.storage.db.init;

import com.google.common.util.concurrent.MoreExecutors;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.ResourceUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.DbStatementUtils;
import org.jfrog.storage.util.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.artifactory.storage.db.dao.locks.DbDistributeLocksDao.TABLE_DISTRIBUTED_LOCKS;
import static org.artifactory.storage.db.fs.dao.BlockingLockDao.TABLE_DISTRIBUTED_BLOCKING_LOCKS;
import static org.jfrog.storage.util.DbUtils.tableExists;

/**
 * Entry-point for initializing the Artifactory database schema.
 *
 * @author Uriah Levy
 * @author Shay Bagants
 */
public class DbInitializationManager {
    private static final Logger log = LoggerFactory.getLogger(DbInitializationManager.class);
    private final ArtifactoryDbProperties dbProperties;
    private final JdbcHelper jdbcHelper;
    private final DataSource locksDataSource;
    private static final String PATH_DELIMITER = "/";

    public DbInitializationManager(ArtifactoryDbProperties dbProperties, JdbcHelper jdbcHelper,
            DataSource locksDataSource) {
        this.dbProperties = dbProperties;
        this.jdbcHelper = jdbcHelper;
        this.locksDataSource = locksDataSource;
    }

    public boolean init() {
        log.info("Initializing DB Schema initialization manager");
        try {
            boolean schemaCreatedByMe = initDatabaseSchema();
            initDbLockSchemaIfNeeded();
            log.info("DB Schema initialization manager initialized");
            return schemaCreatedByMe;
        } catch (Exception e) {
            log.error("DB Schema initialization manager failed to init.", e);
            throw new IllegalStateException("DB Schema initialization manager failed to init.", e);
        }
    }

    private boolean initDatabaseSchema() throws SQLException, IOException {
        try (Connection con = DbConnectionUtils.retryGetConnection(jdbcHelper)) {
            printConnectionInfo(con, dbProperties.getDbType());
            // check if db tables exist and initialize if not
            if (!isSchemaExist(con)) {
                // if using MySQL, check version compatibility
                if (dbProperties.getDbType() == DbType.MYSQL) {
                    DbConnectionUtils.checkMySqlMinVersion(con);
                }
                DbConnectionUtils.checkDefaultDbProperties(this.dbProperties);
                log.info("***Creating database schema***");
                try {
                    DbStatementUtils.executeSqlStream(con, getDbSchemaSql());
                    log.info("***Database schema created***");
                    return true;
                } catch (SQLException e) {
                    if (ArtifactoryHome.get().isHaConfigured() && isSchemaExist(con)) {
                        // Fail gracefully if the schema already exists
                        log.info("Database schema already exists.");
                    } else {
                        log.error("", e);
                        throw e;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check whether the schema exists with retries, allowing another node in the cluster to create it.
     *
     * @return true if the schema has been created by another node
     */
    private boolean isSchemaExist(Connection con) {
        log.debug("Checking for database schema existence");
        String errorMsg = "Unable to check for existence of the unique_ids table";
        CompletableFuture<Boolean> tableExists = ExecutionUtils
                .retry(() -> {
                    try {
                        return DbUtils.isSchemaExist(con, dbProperties.getDbType());
                    } catch (SQLException e) {
                        log.error(errorMsg, e);
                    }
                    return false;
                }, getRetryOptions(), MoreExecutors.newDirectExecutorService());
        try {
            return tableExists.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(errorMsg, e);
        } catch (ExecutionException e) {
            log.error(errorMsg, e);
        }
        return false;
    }

    private ExecutionUtils.RetryOptions getRetryOptions() {
        return ExecutionUtils.RetryOptions.builder()
                .backoffMaxDelay(6000)
                .exponentialBackoffMultiplier(3)
                .numberOfRetries(10)
                .timeout(1500)
                .build();
    }

    private InputStream getDbSchemaSql() throws IOException {
        String dbTypeName = DbUtils.getDbTypeNameForSqlResources(dbProperties.getDbType());
        String resourcePath = PATH_DELIMITER + dbTypeName + PATH_DELIMITER + dbTypeName + ".sql";
        InputStream resource = ResourceUtils.getResource(resourcePath);
        if (resource == null) {
            throw new IOException("Database DDL resource not found at: '" + resourcePath + "'");
        }
        return resource;
    }

    private void initDbLockSchemaIfNeeded() throws SQLException, IOException {
        log.debug("Initializing DB lock schema if needed");
        try (Connection con = DbConnectionUtils.retryGetConnection(jdbcHelper)) {
            DbType dbType = dbProperties.getDbType();
            if (!isTableExists(con.getMetaData(), dbType, TABLE_DISTRIBUTED_BLOCKING_LOCKS)) {
                log.info("*** Creating DB blocking lock schema ***");
                DbStatementUtils.executeSqlStream(con, getDbBlockingLockSchemaSql(dbType));
            }
        }
        log.debug("Checking if additional database was provided for DB locking");
        if (dbProperties.getLockingDbSpecificType().isPresent()) {
            log.info("Additional database details found for DB locking");
            Optional<DbType> lockingDbSpecificType = dbProperties.getLockingDbSpecificType();
            if (lockingDbSpecificType.isPresent()) {
                try (Connection con = locksDataSource.getConnection()) {
                    printConnectionInfo(con, lockingDbSpecificType.get());
                    if (!isAnotherDbLockSchemaExist(con, lockingDbSpecificType.get())) {
                        log.info("***Creating DB lock database schema ***");
                        DbStatementUtils.executeSqlStream(con, getDbLockSchemaSql());
                    }
                    if (!isTableExists(con.getMetaData(), lockingDbSpecificType.get(),
                            TABLE_DISTRIBUTED_BLOCKING_LOCKS)) {
                        log.info("*** Creating distributed blocking locks table in locking database ***");
                        DbStatementUtils.executeSqlStream(con, getDbBlockingLockSchemaSql(lockingDbSpecificType.get()));
                    }
                }
            }
        }
    }

    /**
     * Check whether the table exists with retries
     *
     * @return true if the table exists, false otherwise
     */
    private boolean isTableExists(DatabaseMetaData metaData, DbType dbType, String tableName) {
        log.debug("Checking for database table: '{}' existence", tableName);
        String errorMsg = "Unable to check for existence of '%s' table".formatted(tableName);
        try {
            return DbUtils.tableExists(metaData, dbType, tableName);
        } catch (SQLException e) {
            log.error(errorMsg, e);
        }
        return false;
    }

    private InputStream getDbLockSchemaSql() throws IOException {
        Optional<DbType> lockingSpecificDbType = dbProperties.getLockingDbSpecificType();
        if (lockingSpecificDbType.isPresent()) {
            String dbTypeName = DbUtils.getDbTypeNameForSqlResources(lockingSpecificDbType.get());
            // run the converter that creates the db locking table
            String resourcePath = "/conversion/" + dbTypeName + PATH_DELIMITER + dbTypeName + "_v550c.sql";
            InputStream resource = ResourceUtils.getResource(resourcePath);
            if (resource == null) {
                throw new IOException("Database DDL resource not found at: '" + resourcePath + "'");
            }
            return resource;
        } else {
            throw new IllegalStateException("Another DB was configured for locking but no db type found");
        }
    }

    private InputStream getDbBlockingLockSchemaSql(DbType dbType) throws IOException {
        String dbTypeName = DbUtils.getDbTypeNameForSqlResources(dbType);
        String resourcePath = "/conversion/" + dbTypeName + PATH_DELIMITER + dbTypeName +
                "_v237_create_distributed_blocking_locks_table.sql";
        InputStream resource = ResourceUtils.getResource(resourcePath);
        if (resource == null) {
            throw new IOException("Database DDL resource not found at: '" + resourcePath + "'");
        }
        return resource;
    }

    private boolean isAnotherDbLockSchemaExist(Connection con, DbType dbType) throws SQLException {
        log.debug("Checking for specific DB lock database schema existence");
        return tableExists(con.getMetaData(), dbType, TABLE_DISTRIBUTED_LOCKS);
    }

    private void printConnectionInfo(Connection con, DbType dbType) {
        try {
            DatabaseMetaData meta = con.getMetaData();
            log.info("Database: {} {}. Driver: {} {} Pool: {}", meta.getDatabaseProductName(),
                    meta.getDatabaseProductVersion(),
                    meta.getDriverName(), meta.getDriverVersion(), dbType);
        } catch (SQLException e) {
            log.warn("Can not retrieve database and driver name / version", e);
        }
        if (dbType == DbType.POSTGRESQL && log.isDebugEnabled()) {
            printPgSettings(con);
        }
    }

    private void printPgSettings(Connection con) {
        try (Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT name, setting, source FROM pg_settings WHERE source NOT IN ('default', 'client')")) {
            log.debug("pg_settings (non-default values):");
            while (rs.next()) {
                log.debug("  {}: {} (source: {})",
                        rs.getString("name"), rs.getString("setting"), rs.getString("source"));
            }
        } catch (Exception e) {
            log.warn("Could not retrieve pg_settings", e);
        }
    }
}