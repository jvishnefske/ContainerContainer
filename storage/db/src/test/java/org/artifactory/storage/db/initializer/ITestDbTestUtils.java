/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.common.ResourceUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbStatementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author haims
 */
public class ITestDbTestUtils {
    public static final String LOCK_DB_PREFIX = "locking_";

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public class DbCreationParams {
        private String url;
        private String user;
        private String password;
        private String adminUser;
        private String adminPassword;
        private String schema;
        private String dbType;

        public DbCreationParams() {

        }
    }

    void prepareDbAndSchema(Properties dbProps, String testSchema, String dbType) {
        log.info("Going to create schema: {}  for dbtype: {}", testSchema, dbType);
        String password = dbProps.getProperty("password");
        prepareDbAndSchema(dbProps, dbType, testSchema, password);
    }

    void prepareDbAndSchema(Properties dbProps, String dbType, String testSchema, String password) {
        String url = dbProps.getProperty("url");
        String user = dbProps.getProperty("username");
        String root = dbProps.getProperty("root");
        String adminUser = root != null ? root : user;
        String rootPassword = dbProps.getProperty("rootPassword");
        String adminPassword = rootPassword != null ? rootPassword : password;
        DbCreationParams params = new DbCreationParams();
        params.adminPassword = adminPassword;
        params.adminUser = adminUser;
        params.user = user;
        params.password = password;
        params.url = url;
        params.schema = testSchema;
        params.dbType = dbType;

        runUsingDbUtils(params);
    }

    private void runUsingDbUtils(DbCreationParams params) {
        log.info("Going to create schema on DB Url: {} with user: '{}' password {}", params.url, params.adminUser,
                params.adminPassword);
        try {
            runSchemaCreationOnce(params);
        } catch (Exception e) {
            if (isOracleAuthFailure(params, e)) {
                retryOracleSchemaCreation(params, e);
                return;
            }
            throw new RuntimeException(e);
        }
    }

    private void runSchemaCreationOnce(DbCreationParams params) throws Exception {
        try (HikariDataSource ds = new HikariDataSource()) {
            ds.setJdbcUrl(params.url);
            ds.setUsername(params.adminUser);
            ds.setPassword(params.adminPassword);
            try (
                    InputStream schemaCreationScriptIs = getSchemaCreationScriptIs(params.schema, params.dbType,
                            params.user, params.password);
                    Connection con = ds.getConnection()) {
                DbStatementUtils.executeSqlStream(con, schemaCreationScriptIs);
            }
        }
    }

    private void retryOracleSchemaCreation(DbCreationParams params, Exception firstFailure) {
        int maxAttempts = 5;
        Exception last = firstFailure;
        for (int attempt = 2; attempt <= maxAttempts; attempt++) {
            long delayMs = 2_000L * (attempt - 1);
            log.warn("ORA-01017: attempt {}/{} failed, retrying after {} ms", attempt - 1, maxAttempts, delayMs, last);
            sleepUninterruptibly(delayMs);
            try {
                runSchemaCreationOnce(params);
                return;
            } catch (Exception e) {
                last = e;
                if (!isOracleAuthFailure(params, e)) {
                    throw new RuntimeException(e);
                }
            }
        }
        log.warn("ORA-01017: all {} attempts exhausted, giving up", maxAttempts, last);
        throw new RuntimeException(last);
    }

    private static void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }

    private static boolean isOracleAuthFailure(DbCreationParams params, Exception e) {
        if (!"oracle".equalsIgnoreCase(params.dbType)) {
            return false;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException && ((SQLException) t).getErrorCode() == 1017) {
                return true;
            }
        }
        return false;
    }


    private InputStream getSchemaCreationScriptIs(String testSchema, String dbType, String user, String password) {
        String scriptContent;
        try {
            scriptContent = ResourceUtils.getResourceAsString("/db/migration/" + dbType + "/setup.sql");
        } catch (Exception e) {
            scriptContent = ResourceUtils.getResourceAsString("/db/migration/mssql/setup.sql");
        }
        try {
            Map<String, String> valuesMap = new HashMap<>();
            valuesMap.put("schema", testSchema);
            valuesMap.put("password", password);
            valuesMap.put("user", user);
            StrSubstitutor sub = new StrSubstitutor(valuesMap);
            String resolvedString = sub.replace(scriptContent);
            log.info("Schema creation script is: {}\n", resolvedString);
            return IOUtils.toInputStream(resolvedString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteDBSchema(String schemaName, ArtifactoryDbProperties dbProperties) {
        // cleanup the database
        DbType dbType = dbProperties.getDbType();
        String user = dbProperties.getProperty("root", "root");
        String password = dbProperties.getProperty("rootPassword", "password");
        try {
            String connectionUrl = dbProperties.getConnectionUrl();
            log.info("Going to delete schema: {} for db type: {} from DB url: {} with user: {} and password: {}",
                    schemaName, dbType, connectionUrl, user, password);
            Class.forName(dbProperties.getDriverClass());
            try (Connection con = DriverManager.getConnection(connectionUrl, user, password)) {
                deleteSchema(schemaName, con, dbType);
                if (dbProperties.getLockingDbSpecificType().isPresent()) {
                    deleteSchema(LOCK_DB_PREFIX + schemaName, con, dbType);
                }
            }
        } catch (Exception e) {
            log.error("Exception during delete schema: {} for dbType: {} due to error: {}", schemaName, dbType,
                    e.getMessage());
        }
    }

    private void deleteSchema(String schema, Connection con, DbType dbType) {
        String query;
        switch (dbType) {
            case MYSQL:
            case MARIADB:
            case MSSQL:
                query = "DROP SCHEMA " + schema + ";";
                break;
            case DERBY:
                // Do nothing
                return;
            case POSTGRESQL:
                query = "DROP SCHEMA " + schema + " CASCADE;";
                break;
            case ORACLE:
                query = "DROP USER " + schema.toUpperCase() + " CASCADE";
                killLiveOracleSessions(log, con, schema);
                break;
            default:
                throw new RuntimeException("Invalid database type: " + dbType + " expecting one of the following: "
                        + " DERBY, MYSQL, MARIADB, ORACLE, MSSQL, POSTGRESQL");
        }
        try (Statement statement = con.createStatement()) {
            statement.execute(query);
            log.info("Successfully purged schema {}", schema);
        } catch (SQLException e) {
            log.warn("Caught exception while attempting to DROP database schema with name {}. Previous logging may " +
                            "indicate this schema has already been deleted, in which case this error can be ignored."
                    , schema, e);
        }
    }

    private void killLiveOracleSessions(Logger log, Connection connection, String schema) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(getOracleKillSessionsCommand(schema));
        } catch (SQLException e) {
            log.warn("Caught exception while attempting to kill live Oracle sessions for user: '" + schema + "'", e);
        }
    }

    private String getOracleKillSessionsCommand(String testSchema) {
        String killSessionsResourcePath = "/db/migration/oracle/killsessions.sql";
        InputStream resourceStream = ResourceUtils.getResource(killSessionsResourcePath);
        try {
            String scriptContent = IOUtils.toString(resourceStream, "utf-8");
            Map<String, String> valuesMap = new HashMap<>();
            valuesMap.put("schema", testSchema.toUpperCase());
            StrSubstitutor sub = new StrSubstitutor(valuesMap);
            String resolvedString = sub.replace(scriptContent);
            log.debug("Kill sessions command is {}", resolvedString);
            return resolvedString;
        } catch (IOException e) {
            throw new RuntimeException("Unable to compose kill sessions command", e);
        }
    }

}