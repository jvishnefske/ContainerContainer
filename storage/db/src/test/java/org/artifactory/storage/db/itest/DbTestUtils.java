/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.itest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.jfrog.sysconf.SysConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.config.db.DbTypePropertyResolver;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.init.DbInitializationManager;
import org.artifactory.storage.db.initializer.TestDBInitializer;
import org.artifactory.storage.db.initializer.TestDbInitializerRegistry;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.YamlUtils;
import org.jfrog.converter.DbSchemaVersionLogDao;
import org.jfrog.security.util.Pair;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.DbStatementUtils;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.PostgresqlUtils;
import org.slf4j.Logger;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.storage.db.itest.spring.DbTestConfigFactory.DERBY;
import static org.jfrog.config.home.JFrogHome.ETC_FOLDER;
import static org.jfrog.config.home.JFrogHome.SYSTEM_YAML_FILENAME;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;
import static org.jfrog.storage.DbType.MARIADB;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.MYSQL;
import static org.jfrog.storage.DbType.ORACLE;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.jfrog.storage.util.DbUtils.doWithConnection;
import static org.jfrog.storage.util.DbUtils.getActiveCatalog;
import static org.jfrog.storage.util.DbUtils.getActiveSchema;
import static org.jfrog.storage.util.DbUtils.indexExists;
import static org.jfrog.storage.util.DbUtils.isSchemaExist;
import static org.jfrog.storage.util.DbUtils.normalizedName;
import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.jfrog.storage.util.PostgresqlUtils.isExtensionExists;

/**
 * A utility class for integration tests to clean and setup the database
 *
 * @author Yossi Shaul
 */
@Slf4j
public class DbTestUtils {
    // BE CAREFUL do not have logger here. Tests not initialized correctly

    private static final String[] DB_META_TABLE_QUALIFIER = new String[] {"TABLE", "PARTITIONED TABLE"};
    private static final String[] DB_META_VIEW_QUALIFIER = new String[] {"VIEW"};

    /**
     * A list of all the tables in the database
     */
    private static final List<String> tables = Collections.unmodifiableList(Lists.newArrayList(
            DbSchemaVersionLogDao.SCHEMA_CHANGE_LOG_TABLE_NAME,
            "repository_stats",
            "db_properties", "artifactory_servers",
            "stats_remote", "stats", "watches", "bundle_blobs", "bundle_files", "artifact_bundles", "node_props",
            "node_meta_infos", "nodes",
            "indexed_archives_entries", "archive_names", "archive_paths", "indexed_archives",
            "binary_blobs", "binaries",
            "aces", "acls", "users_groups", "groups", "user_props", "users",
            "permission_target_repos", "permission_targets",
            "configs", "tasks", "jobs",
            "module_props", "build_props", "build_jsons", "build_promotions",
            "build_dependencies", "build_artifacts", "build_modules", "build_release_bundles", "builds",
            "unique_ids", "distributed_locks", "master_key_status", "trusted_keys",
            "UI_SESSION_ATTRIBUTES", "UI_SESSION", "replication_errors", "node_events_errors",
            "UI_SESSION_V2_ATTRIBUTES", "UI_SESSION_V2",
            "node_events", "node_events_tmp", "node_event_cursor", "node_event_priorities", "migration_status",
            "binaries_tasks", "heavy_query_cache", "repository_config", "storage_cache",
            "mirrors_metadata", "storage_events", "nodes_cleaned", "cleanup_run_summary", "retention_run_summary",
            "quartz_task_history", "file_list_tasks", "storage_multipart_uploads", "version_history", "config_history",
            "policy", "traffic_summary", "distributed_blocking_locks",
            "remote_repo_stats_hourly", "remote_repo_stats_daily", "remote_repo_stats_status",
            "repo_traffic_hourly", "repo_traffic_daily",
            "repo_migrations_history", "repo_migrations",
            "virtual_repo_dangling_refs", "transparent_repo_config",
            "error_catalog", "error_catalog_templates",
            "request_logs",
            "outbound_request_logs",
            "system_states"
    ));

    /**
     * A list of all sequences
     */
    private static final List<String> sequences = Collections.unmodifiableList(
            Lists.newArrayList("node_events_event_id_seq", "storage_events_event_id_seq"));

    /**
     * A list of all views in the database
     */
    private static final List<String> views = List.of("nodes_view", "repos_view");

    private static final List<String> extensions = List.of("pg_trgm");

    //Testing for table existence is not enough, some schema conversions change columns as well.
    //Ordered by most probable table to change
    private static final List<Pair<String, List<String>>> schema = Collections.unmodifiableList(Lists.newArrayList(
            new Pair<>("binaries", Lists.newArrayList("sha1", "md5", "bin_length", "sha256")),
            new Pair<>("nodes",
                    Lists.newArrayList("node_id", "node_type", "repo", "node_path", "node_name", "depth",
                            "created", "created_by", "modified", "modified_by", "updated", "bin_length", "sha1_actual",
                            "sha1_original", "md5_actual", "md5_original", "sha256", "repo_path_checksum")),
            new Pair<>("binary_blobs", Lists.newArrayList("sha1", "data")),
            new Pair<>("aces", Lists.newArrayList("ace_id", "acl_id", "mask", "user_id", "group_id")),
            new Pair<>("acls", Lists.newArrayList("acl_id", "perm_target_id", "modified", "modified_by")),
            new Pair<>("users_groups", Lists.newArrayList("user_id", "group_id", "realm")),
            new Pair<>("groups", Lists.newArrayList("group_id", "group_name", "description", "default_new_user",
                    "realm", "realm_attributes", "admin_privileges")),
            new Pair<>("users",
                    Lists.newArrayList("user_id", "username", "password", "salt", "email", "gen_password_key",
                            "admin", "enabled", "updatable_profile", "realm", "private_key", "public_key",
                            "last_login_time", "last_login_ip",
                            "last_access_time", "last_access_ip", "bintray_auth", "locked", "credentials_expired")),
            new Pair<>("build_dependencies", Lists.newArrayList("dependency_id", "module_id", "dependency_name_id",
                    "dependency_scopes", "dependency_type", "sha1", "md5", "sha256")),
            new Pair<>("build_artifacts", Lists.newArrayList("artifact_id", "module_id", "artifact_name",
                    "artifact_type", "sha1", "md5", "sha256")),
            new Pair<>("build_promotions", Lists.newArrayList("build_id", "created", "created_by", "status", "repo",
                    "promotion_comment", "ci_user")),
            new Pair<>("builds", Lists.newArrayList("build_id", "build_name", "build_number", "build_date",
                    "ci_url", "created", "created_by", "modified", "modified_by")),
            new Pair<>("build_modules", Lists.newArrayList("module_id", "build_id", "module_name_id")),
            new Pair<>("artifactory_servers",
                    Lists.newArrayList("server_id", "start_time", "context_url", "membership_port", "server_state",
                            "server_role", "last_heartbeat", "artifactory_version", "artifactory_revision",
                            "artifactory_release", "artifactory_running_mode", "license_hash")),
            new Pair<>("db_properties",
                    Lists.newArrayList("installation_date", "artifactory_version", "artifactory_revision",
                            "artifactory_release")),
            new Pair<>("stats_remote",
                    Lists.newArrayList("node_id", "origin", "download_count", "last_downloaded", "last_downloaded_by",
                            "path")),
            new Pair<>("stats",
                    Lists.newArrayList("node_id", "download_count", "last_downloaded", "last_downloaded_by")),
            new Pair<>("watches", Lists.newArrayList("watch_id", "node_id", "username", "since")),
            new Pair<>("node_props", Lists.newArrayList("prop_id", "node_id", "prop_key", "prop_value")),
            new Pair<>("node_meta_infos", Lists.newArrayList("node_id", "props_modified", "props_modified_by")),
            new Pair<>("indexed_archives_entries",
                    Lists.newArrayList("indexed_archives_id", "entry_path_id", "entry_name_id")),
            new Pair<>("archive_names", Lists.newArrayList("name_id", "entry_name")),
            new Pair<>("archive_paths", Lists.newArrayList("path_id", "entry_path")),
            new Pair<>("indexed_archives", Lists.newArrayList("archive_sha1", "indexed_archives_id")),
            new Pair<>("user_props", Lists.newArrayList("user_id", "prop_key", "prop_value")),
            new Pair<>("permission_target_repos", Lists.newArrayList("perm_target_id", "repo_key")),
            new Pair<>("permission_targets",
                    Lists.newArrayList("perm_target_id", "perm_target_name", "includes", "excludes")),
            new Pair<>("configs", Lists.newArrayList("config_name", "last_modified", "data")),
            new Pair<>("tasks", Lists.newArrayList("task_type", "task_context", "created")),
            new Pair<>("jobs", Lists.newArrayList("job_id", "job_type", "job_status", "started", "finished",
                    "additional_details")),
            new Pair<>("module_props", Lists.newArrayList("prop_id", "module_id", "prop_key", "prop_value")),
            new Pair<>("build_props", Lists.newArrayList("prop_id", "build_id", "prop_key", "prop_value")),
            new Pair<>("build_jsons", Lists.newArrayList("build_id", "build_info_json")),
            new Pair<>("unique_ids", Lists.newArrayList("index_type", "current_id")),
            new Pair<>("master_key_status", Lists.newArrayList("set_by_node_id", "kid", "expires")),
            new Pair<>("node_events", Lists.newArrayList("event_id", "timestamp", "event_type", "path",
                    "hints")),
            new Pair<>("node_events_tmp", Lists.newArrayList("timestamp", "event_type", "path", "hints")),
            new Pair<>("artifact_bundles", Lists.newArrayList("id", "name", "version", "status", "date_created",
                    "date_distributed", "signature", "type", "storing_repo")),
            new Pair<>("bundle_files", Lists.newArrayList("id", "node_id", "bundle_id", "repo_path")),
            new Pair<>("bundle_blobs", Lists.newArrayList("id", "data", "bundle_id")),
            new Pair<>("trusted_keys",
                    Lists.newArrayList("trusted_key", "fingerprint", "alias", "issued", "issued_by", "expiry")),
            new Pair<>("UI_SESSION_ATTRIBUTES", Lists.newArrayList("SESSION_ID", "ATTRIBUTE_NAME", "ATTRIBUTE_BYTES")),
            new Pair<>("UI_SESSION",
                    Lists.newArrayList("SESSION_ID", "CREATION_TIME", "LAST_ACCESS_TIME", "MAX_INACTIVE_INTERVAL",
                            "PRINCIPAL_NAME")),
            new Pair<>("UI_SESSION_V2_ATTRIBUTES",
                    Lists.newArrayList("SESSION_PRIMARY_ID", "ATTRIBUTE_NAME", "ATTRIBUTE_BYTES")),
            new Pair<>("UI_SESSION_V2",
                    Lists.newArrayList("PRIMARY_ID", "SESSION_ID", "CREATION_TIME", "LAST_ACCESS_TIME",
                            "MAX_INACTIVE_INTERVAL", "EXPIRY_TIME", "PRINCIPAL_NAME")),
            new Pair<>("node_events_errors",
                    Lists.newArrayList("error_id", "operation_key", "event_category", "first_error_time",
                            "last_error_time", "error_count", "error_message", "event_id", "event_time", "event_type",
                            "path")),
            new Pair<>("node_event_cursor", Lists.newArrayList("operator_id", "event_marker")),
            new Pair<>("node_event_priorities",
                    Lists.newArrayList("priority_id", "path", "type", "operator_id", "priority", "timestamp",
                            "retry_count")),
            new Pair<>("migration_status",
                    Lists.newArrayList("identifier", "started", "finished", "migration_info_blob")),
            new Pair<>("binaries_tasks", Stream.of(
                            "id, sha1, time, local_repo_key, remote_url_hash, start_time, count, server_id".split(","))
                    .map(String::trim).collect(Collectors.toList())),
            new Pair<>("quartz_task_history", Lists.newArrayList(
                    "run_id", "external_id", "task_type", "started_date", "last_modified_date", "status",
                    "status_message")
            ),
            new Pair<>("distributed_blocking_locks", Lists.newArrayList("lock_key")),
            new Pair<>("storage_multipart_uploads",
                    Lists.newArrayList("upload_id", "created_date", "temp_path", "status", "modified_date", "data")),
            new Pair<>("repo_traffic_hourly",
                    Lists.newArrayList("id", "update_time", "action", "actor", "redirect_target",
                            "origin_repo_key", "origin_repo_type", "origin_package_type",
                            "repo_key", "repo_type", "package_type", "request_count", "total_transferred_size")),
            new Pair<>("repo_traffic_daily",
                    Lists.newArrayList("id", "update_time", "action", "actor", "redirect_target",
                            "origin_repo_key", "origin_repo_type", "origin_package_type",
                            "repo_key", "repo_type", "package_type", "request_count", "total_transferred_size")),
            new Pair<>("repo_migrations",
                    Lists.newArrayList("repo_name", "start_time", "end_time", "dry_run", "package_type",
                            "migration_status", "target_repo", "job_id", "repository_type", "scheduled_time")),
            new Pair<>("repo_migrations_history",
                    Lists.newArrayList("job_id", "repo_name", "target_repo", "migration_status",
                            "start_time", "end_time")),
            new Pair<>("virtual_repo_dangling_refs",
                    Lists.newArrayList("virtual_repo_key", "child_repo_key", "created", "modified")),
            new Pair<>("transparent_repo_config",
                    Lists.newArrayList("repository_key", "type", "package_type", "revision",
                            "created", "modified", "config_blob")),
            // message_tsv (PG-only generated column) intentionally omitted from this canonical list;
            // its presence is asserted by the PG-gated V273/V274 migration tests instead.
            new Pair<>("error_catalog_templates",
                    Lists.newArrayList("id", "signature_hash", "severity", "logger_class", "exception_class",
                            "template_message", "first_occurrence_at", "last_occurrence_at", "occurrence_count",
                            "created_at", "updated_at")),
            new Pair<>("error_catalog",
                    Lists.newArrayList("id", "template_id", "occurred_at", "raw_message")),
            new Pair<>("request_logs",
                    Lists.newArrayList("id", "request_time", "trace_id", "username", "http_method", "request_path",
                            "status_code", "duration_ms")),
            new Pair<>("outbound_request_logs",
                    Lists.newArrayList("id", "request_time", "trace_id", "repo_key", "http_method",
                            "request_url", "status_code", "duration_ms")),
            new Pair<>("system_states",
                    Lists.newArrayList("use_case_name", "id", "timestamp", "node_id", "actor", "data"))));

    /**
     * A list of all the access server tables inside the artifactory database (since access server is embedded)
     */
    private static final List<String> accessServerTables = Collections.unmodifiableList(Lists.newArrayList(
            "access_tokens", "access_users", "access_servers"
    ));

    static void refreshOrRecreateSchema(Logger log, Connection conn, DbType dbType) throws SQLException {
        try {
            // to improve test speed, re-create the schema only if there's a missing table
            boolean recreateSchema = isSchemaIncomplete(conn, dbType);
            if (recreateSchema) {
                log.info("Recreating test database schema for database: {}", dbType);
                log.info("Dropping objects for connection: {}, database: {}", conn.getMetaData().getURL(), dbType);
                dropExistingObjects(conn, dbType);
                log.info("Creating schema for connection: {}, database: {}", conn.getMetaData().getURL(), dbType);
                createSchema(conn, dbType);
                log.info("Schema created for connection: {}, database: {}", conn.getMetaData().getURL(), dbType);
            } else {
                log.info("Refreshing test database schema for database: {}", dbType);
                deleteFromAllTables(conn, dbType);
                dropAllExistingExtensions(conn, dbType);
                deleteFromAccessServerTables(conn);
                log.info("Schema refreshed for connection: {}, database: {}", conn.getMetaData().getURL(), dbType);
            }
        } catch (SQLException e) {
            log.error("Failed to recreate schema, Test will fail", e);
            throw e;
        }
    }

    public static void dropAllExistingSequences(Connection con, DbType dbType) throws SQLException {
        if (MYSQL != dbType && MARIADB != dbType) {
            for (String sequence : sequences) {
                if (sequenceExists(con, dbType, sequence)) {
                    try (Statement stmt = con.createStatement()) {
                        stmt.execute("DROP SEQUENCE " + sequence + (DbType.DERBY == dbType ? " RESTRICT" : ""));
                    }
                }
            }
        }
    }

    public static void dropAllExistingExtensions(Connection connection, DbType dbType) throws SQLException {
        if (POSTGRESQL == dbType) {
            for (String extension : extensions) {
                if (isExtensionExists(connection, extension)) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("DROP EXTENSION " + extension + " CASCADE;");
                    }
                }
            }
        }
    }

    public static void dropExistingObjects(Connection con, DbType dbType) throws SQLException {
        dropAllExistingViews(con, dbType);
        dropAllExistingTables(con, dbType);
        dropAllExistingSequences(con, dbType);
        dropAllExistingExtensions(con, dbType);
    }

    public static void dropAllExistingTables(Connection con, DbType dbType) throws SQLException {
        dropTablesIfExist(con, dbType, tables);
    }

    private static void dropTablesIfExist(Connection con, DbType dbType, List<String> tableNames)
            throws SQLException {
        for (String table : tableNames) {
            if (tableExists(con, dbType, table)) {
                try (Statement statement = con.createStatement()) {
                    statement.execute(
                            "DROP TABLE " + table + (ORACLE == dbType ? " CASCADE CONSTRAINTS" : ""));
                }
            }
        }
    }

    public static void dropAllExistingViews(Connection con, DbType dbType) throws SQLException {
        for (String view : views) {
            if (viewExists(con, dbType, view)) {
                try (Statement statement = con.createStatement()) {
                    statement.execute("DROP VIEW " + view + (ORACLE == dbType ? " CASCADE CONSTRAINTS" : ""));
                }
            }
        }
    }

    public static void deleteFromAllTables(Connection con, DbType dbType) throws SQLException {
        for (String table : tables) {
            try (Statement statement = con.createStatement()) {
                statement.execute("DELETE FROM " + table);
            }
        }
    }

    private static void deleteFromAccessServerTables(Connection con) {
        for (String table : accessServerTables) {
            try (Statement statement = con.createStatement()) {
                statement.execute("DELETE FROM " + table);
            } catch (SQLException e) {
                log.debug("Failed to delete from table: {}", table, e);
            }
        }
    }

    public static boolean tableExists(Connection con, DbType dbType, String tableName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        tableName = normalizedName(tableName, metaData);
        try (ResultSet rs = metaData.getTables(getActiveCatalog(con, dbType), getActiveSchema(con, dbType), tableName,
                DB_META_TABLE_QUALIFIER)) {
            return rs.next();
        }
    }

    public static boolean viewExists(Connection con, DbType dbType, String viewName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        viewName = normalizedName(viewName, metaData);
        try (ResultSet rs = metaData.getTables(getActiveCatalog(con, dbType), getActiveSchema(con, dbType), viewName,
                DB_META_VIEW_QUALIFIER)) {
            return rs.next();
        }
    }

    public static boolean tableEmpty(Connection connection, String tableName) throws SQLException {
        String query = "SELECT COUNT(*) FROM " + tableName;
        try (PreparedStatement preparedStatement = connection.prepareStatement(query);
                ResultSet resultSet = preparedStatement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1) == 0;
            }
        }
        return false;
    }

    public static boolean sequenceExists(Connection con, DbType dbType, String sequenceName) throws SQLException {
        try (Statement stmt = con.createStatement()) {
            String query = seqExistsQuery(DbUtils.getActiveSchema(con, dbType), sequenceName, dbType);
            try (ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    return rs.getInt(1) == 1;
                }
            }
        }
        return false;
    }

    private static String seqExistsQuery(String schema, String name, DbType dbType) {
        if (dbType == ORACLE) {
            return "SELECT COUNT(1) FROM user_sequences " +
                    "WHERE LOWER(sequence_name) = '" + name + "'";
        } else if (dbType == POSTGRESQL) {
            return "SELECT COUNT(1) " +
                    "FROM pg_class AS c, pg_namespace AS ns " +
                    "WHERE c.relnamespace = ns.oid " +
                    "AND c.relkind = 'S' " +
                    "AND ns.nspname = '" + schema + "' " +
                    "AND c.relname = '" + name + "'";
        } else if (dbType == MSSQL) {
            return "SELECT COUNT(1) FROM sys.objects " +
                    "WHERE name = '" + name + "' AND type = 'SO'";
        } else if (dbType == DbType.DERBY) {
            return "SELECT COUNT(1) from SYS.SYSSEQUENCES " +
                    "WHERE UPPER(sequencename) = UPPER('" + name + "')";
        } else {
            throw new RuntimeException("Invalid dbType: " + dbType);
        }
    }

    public static boolean isTableMissing(Connection con, DbType dbType) throws SQLException {
        for (String table : tables) {
            if (!tableExists(con, dbType, table)) {
                return true;
            }
        }
        return false;
    }

    public static boolean indexExistsForView(JdbcHelper jdbcHelper, String indexName, String columnName)
            throws SQLException {
        return DbUtils.withConnection(jdbcHelper,
                conn -> {
                    String activeSchema = getActiveSchema(conn, DbType.POSTGRESQL);
                    String indexDef = PostgresqlUtils.getPostgresqlIndexDefInfo(conn, activeSchema, indexName);
                    return indexDef.contains(columnName);
                });
    }

    public static boolean indexNotExists(JdbcHelper jdbcHelper, String tableName, String columnName, String indexName,
            DbType dbType) throws SQLException {
        return withConnection(jdbcHelper,
                conn -> !indexExists(conn, dbType, tableName, columnName, indexName, true));
    }

    /**
     * Tests the db schema for missing columns, enough that once is missing to return true.
     */
    private static boolean isSchemaIncomplete(Connection con, DbType dbType) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        return isSchemaColumnsIncomplete(metaData, dbType, schema);
    }

    private static boolean isSchemaColumnsIncomplete(DatabaseMetaData metaData, DbType dbType,
            List<Pair<String, List<String>>> tableColumnsList) throws SQLException {
        for (Pair<String, List<String>> tableColumns : tableColumnsList) {
            String table = tableColumns.getFirst();
            for (String column : tableColumns.getSecond()) {
                if (!DbUtils.columnExists(metaData, dbType, table, column)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean columnExists(Connection con, DbType dbType, String table, String column) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        String tableName = normalizedName(table, metaData);
        String columnName = normalizedName(column, metaData);
        try (ResultSet rs = metaData
                .getColumns(getActiveCatalog(con, dbType), getActiveSchema(con, dbType), tableName, columnName)) {
            return rs.next();
        }
    }

    public static int getColumnSize(Connection con, DbType dbType, String tableName, String columnName)
            throws SQLException {
        return DbUtils.getColumnSize(con.getMetaData(), dbType, tableName, columnName);
    }

    public static String getDbName() {
        return DbTypePropertyResolver.resolveDbNameOrDefault(DERBY);
    }

    public static DbType getDbType() {
        return DbTypePropertyResolver.resolveOrDefault(DbType.DERBY);
    }

    private static void createSchema(Connection con, DbType dbType) throws SQLException {
        String dbConfigDir = DbUtils.getDbTypeNameForSqlResources(dbType);
        try (InputStream schema = ResourceUtils.getResource("/" + dbConfigDir + "/" + dbConfigDir + ".sql")) {
            DbStatementUtils.executeSqlStream(con, schema);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ArtifactoryJdbcHelper createJdbcHelper() throws IOException {
        ArtifactoryDbProperties dbProperties = getOrCreateDbProperties();
        return new ArtifactoryJdbcHelper(
                CommonDataSourceFactory
                        .createDataSource(dbProperties,
                                true));
    }

    public static void initAndCreateUniqueDbNamespaceIfNeeded() throws IOException, SQLException {
        // Init DB schema
        initUniqueDbNamespaceIfNeeded();
        // After Setup (Schema created) we need dbInitializationManager to the unique schema
        try (ArtifactoryJdbcHelper rawJdbcHelper = createJdbcHelper()) {
            ArtifactoryDbProperties dbProperties = ArtifactoryHome.get().getDBProperties();
            log.info("Connecting to {}", dbProperties.getConnectionUrl());
            DbInitializationManager dbInitializingInstallationManager = new DbInitializationManager(
                    dbProperties, rawJdbcHelper,
                    CommonDataSourceFactory.createDifferentDatasourceForDbLock(dbProperties, true)
            );
            dbInitializingInstallationManager.init();
        }
    }

    private static void initUniqueDbNamespaceIfNeeded() throws
            SQLException, IOException {
        if (getDbType() != DbType.DERBY) {
            try (ArtifactoryJdbcHelper rawJdbcHelper = createJdbcHelper()) {
                ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
                ArtifactoryDbProperties artifactoryDbProperties = artifactoryHome.getDBProperties();
                if (propertiesContainsUnique(artifactoryDbProperties)) {
                    AtomicBoolean schemaExists = new AtomicBoolean(false);
                    doWithConnection(rawJdbcHelper, conn ->
                            schemaExists.set(isSchemaExist(conn, artifactoryDbProperties.getDbType())));
                    if (schemaExists.get()) {
                        return;
                    }
                }
                setupDb();
                artifactoryHome.initDBProperties();
            }
        }
    }

    private static boolean propertiesContainsUnique(ArtifactoryDbProperties artifactoryDbProperties) {
        return StringUtils.contains(artifactoryDbProperties.getConnectionUrl(),
                UniqueJvmForkTestIdentifier.get()) ||
                StringUtils.contains(artifactoryDbProperties.getUsername(),
                        UniqueJvmForkTestIdentifier.get());
    }

    private static void setupDb() {
        TestDBInitializer dbInitializer = TestDbInitializerRegistry.getDbInitializer(getDbName());
        Properties dbProps = dbInitializer.setup(UniqueJvmForkTestIdentifier.get());
        writeUrlAndUserNameToSystemYaml(dbProps.getProperty("url"), dbProps.getProperty("username"));

        ReflectionTestUtils.setField(ArtifactoryHome.get(), "sysConfig",
                new SysConfig.Builder().withHome(ArtifactoryHome.get().getHomeDir().getPath()).build());
    }

    private static void writeUrlAndUserNameToSystemYaml(String url, String userName) {
        File systemYamlFile = new File(
                Paths.get(ArtifactoryHome.get().getHomeDir().getPath(), VAR_FOLDER,
                        ETC_FOLDER, SYSTEM_YAML_FILENAME).toUri());
        if (systemYamlFile.exists()) {
            JsonNode systemYaml = YamlUtils.getInstance().readTree(systemYamlFile);
            ObjectNode databaseNode = ((ObjectNode) systemYaml.get("artifactory").get("database"));
            databaseNode.put("url", url);
            databaseNode.put("username", userName);
            YamlUtils.getInstance().writeValue(systemYamlFile, systemYaml);
        }
    }

    private static ArtifactoryDbProperties getOrCreateDbProperties() throws IOException {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        ArtifactoryDbProperties artifactoryDbProperties = artifactoryHome.getDBProperties();
        String dbConfigName = getDbName();
        if (artifactoryDbProperties.getDbType() != DbType.DERBY &&
                propertiesContainsUnique(artifactoryDbProperties)) {
            return artifactoryDbProperties;
        }

        File dbPropsFile;
        String generatedDbConfigLocation = getGeneratedDbConfigLocation();
        if (generatedDbConfigLocation != null) {
            dbPropsFile = new File(generatedDbConfigLocation);
        } else {
            if (isOracleTwentyThreeVersion(dbConfigName)) {
                dbPropsFile = ResourceUtils.getResourceAsFile("/db/oracle23/" + dbConfigName + ".yaml");
            } else {
                dbPropsFile = ResourceUtils.getResourceAsFile("/db/" + dbConfigName + ".yaml");
            }
        }
        File systemYamlFile = Paths
                .get(artifactoryHome.getSysLayout().getProductEtc().getAbsolutePath(), SYSTEM_YAML_FILENAME)
                .toFile();
        FileUtils.copyFile(dbPropsFile, systemYamlFile);
        ReflectionTestUtils.setField(artifactoryHome, "sysConfig",
                new SysConfig.Builder().withHome(artifactoryHome.getHomeDir().getPath()).build());
        JsonNode jsonNode = YamlUtils.getInstance().readTree(systemYamlFile);
        JsonNode dbType = jsonNode.get("artifactory").get("database").get("type");
        if (DERBY.equals(dbType.asText())) {
            System.setProperty("derby.stream.error.file",
                    new File(artifactoryHome.getLogDir(), "derby.log").getAbsolutePath());
        }
        artifactoryHome.initDBProperties();
        return new ArtifactoryDbProperties(artifactoryHome);
    }

    public static boolean isOracleTwentyThreeVersion(String dbConfig) {
        String dbVersion = System.getProperty("artifactory.db.version");
        return dbConfig.contains(DbType.ORACLE.toString()) && dbVersion != null && dbVersion.contains("23");
    }

    private static String getGeneratedDbConfigLocation() {
        String generatedConfig = System.getProperty("artifactory.db.generated.config");
        // reset the value to make sure it's not propagated to other tests
        System.clearProperty("artifactory.db.generated.config");
        return generatedConfig;
    }

    public static SimpleJdbcInsert createSimpleJdbcInsert(DataSource dataSource) {
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(dataSource);
        if (ArtifactoryHome.get().getDBProperties().getDbType() == POSTGRESQL) {
            jdbcInsert.setSchemaName(UniqueJvmForkTestIdentifier.get());
        } else if (ArtifactoryHome.get().getDBProperties().getDbType() == MYSQL ||
                ArtifactoryHome.get().getDBProperties().getDbType() == MARIADB) {
            jdbcInsert.setCatalogName(UniqueJvmForkTestIdentifier.get());
        }
        return jdbcInsert;
    }

    public static void withRetry(int retries, long interval, RunnableWithException runnable)
            throws SQLException, IOException {
        for (int i = 1; i <= retries; i++) {
            try {
                log.info("Try #{}", i);
                runnable.run();
                log.info("Succeeded");
                return;
            } catch (IOException | SQLException e) {
                log.info("Caught exception: {}, will retry up to {} times", e.getMessage(), retries);
                if (i == retries) {
                    log.info("All retries have failed, throwing the exception");
                    throw e;
                }
                sleep(interval);
            }
        }
    }

    private static void sleep(long interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void run() throws IOException, SQLException;
    }
}
