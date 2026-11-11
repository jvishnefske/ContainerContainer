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

package org.artifactory.storage.db.version;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.conversion.version.v227.CreateDbSpecificSequence;
import org.artifactory.storage.db.conversion.version.v228.AlterIndexAfterConcurrentCreation;
import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable;
import org.artifactory.storage.db.version.converter.ArchiveIndexCleanupDBConvertor;
import org.artifactory.storage.db.version.converter.CleanUpTasksTableDBConvertor;
import org.artifactory.storage.db.version.converter.DropIndexConcurrentlyConvertor;
import org.artifactory.storage.db.version.converter.IncreaseColumnStatisticsConvertor;
import org.artifactory.storage.db.version.converter.LockingDBSqlConverter;
import org.artifactory.storage.db.version.converter.SafeIndexConcurrentCreator;
import org.artifactory.storage.db.version.converter.ScriptFileDbConverter;
import org.artifactory.storage.db.version.converter.SkipOnFailsDBConvertor;
import org.artifactory.storage.db.version.converter.SkippableScriptFileDbConverter;
import org.artifactory.storage.db.version.converter.SuspendOnConditionOracleConvertor;
import org.artifactory.storage.db.version.converter.SuspendOnConditionPsqlDbConvertor;
import org.artifactory.storage.db.version.converter.validconversion.ValidOracleConversionVersion;
import org.artifactory.storage.db.version.converter.validconversion.ValidPsqlConversionVersion;
import org.artifactory.util.AolUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.converter.DbConverter;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.converter.predefined.StatementDbConverter;
import org.jfrog.storage.DbType;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.common.config.db.DbVersionUtil.DB_PROPERTIES_TABLE;
import static org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao.ARTIFACT_BUNDLES_TABLE;
import static org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao.BUNDLE_FILES_TABLE;
import static org.artifactory.storage.db.dao.locks.DbDistributeLocksDao.TABLE_DISTRIBUTED_LOCKS;
import static org.artifactory.storage.db.event.dao.EventsDao.EVENTS_TABLE;
import static org.artifactory.storage.db.event.dao.EventsDao.EVENTS_TEMP_TABLE;
import static org.artifactory.storage.db.event.dao.NodeEventPrioritiesDao.EVENT_PRIORITIES_TABLE;
import static org.artifactory.storage.db.event.dao.NodeEventsCursorDao.EVENTS_CURSOR_TABLE;
import static org.artifactory.storage.db.event.error.dao.NodeEventsErrorsDAO.TABLE_NODE_EVENTS_ERRORS;
import static org.artifactory.storage.db.fs.dao.JobsDao.JOBS_TABLE;
import static org.artifactory.storage.db.fs.dao.NodesDao.NODES_TABLE;
import static org.artifactory.storage.db.fs.dao.PropertiesDao.PROPS_TABLE;
import static org.artifactory.storage.db.fs.dao.TasksDao.TASKS_TABLE;
import static org.artifactory.storage.db.keys.dao.TrustedKeysDao.TRUSTED_KEYS_TABLE;
import static org.artifactory.storage.db.servers.dao.ArtifactoryServersDao.SERVERS_TABLE;
import static org.artifactory.storage.db.version.OracleDbVersionPredicates.oracleIndexNotExistsByName;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v600m023;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v610m002;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v6110m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v6120m079;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v6170m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v620;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v640m007;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v650m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v660m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v682m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v690m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7110m033;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7130m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7170m040;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7201m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7230m012;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v731m007;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v732m017;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v734m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7380;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7400;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v740m081;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7460m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v750m145;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v760m001;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v770;
import static org.jfrog.converter.model.ConverterExecutionType.SEQ_ASYNC;
import static org.jfrog.converter.model.ConverterExecutionType.SEQ_SYNC;
import static org.jfrog.converter.util.ColumnDefinition.builder;
import static org.jfrog.converter.util.DbColumnType.BIGINT;
import static org.jfrog.converter.util.DbColumnType.BLOB;
import static org.jfrog.converter.util.DbColumnType.CHAR;
import static org.jfrog.converter.util.DbColumnType.SMALLINT;
import static org.jfrog.converter.util.DbColumnType.VARCHAR;
import static org.jfrog.converter.util.PreDefinedConditions.columnExists;
import static org.jfrog.converter.util.PreDefinedConditions.columnNotExists;
import static org.jfrog.converter.util.PreDefinedConditions.columnSizeNotMatch;
import static org.jfrog.converter.util.PreDefinedConditions.dbSpecific;
import static org.jfrog.converter.util.PreDefinedConditions.enterpriseEdition;
import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;
import static org.jfrog.converter.util.PreDefinedConditions.extensionExists;
import static org.jfrog.converter.util.PreDefinedConditions.extensionNotExists;
import static org.jfrog.converter.util.PreDefinedConditions.foreignKeyExists;
import static org.jfrog.converter.util.PreDefinedConditions.indexExists;
import static org.jfrog.converter.util.PreDefinedConditions.indexExistsAndInValid;
import static org.jfrog.converter.util.PreDefinedConditions.indexNotExists;
import static org.jfrog.converter.util.PreDefinedConditions.indexValid;
import static org.jfrog.converter.util.PreDefinedConditions.primaryKeyExists;
import static org.jfrog.converter.util.PreDefinedConditions.primaryKeyNotExists;
import static org.jfrog.converter.util.PreDefinedConditions.tableExists;
import static org.jfrog.converter.util.PreDefinedConditions.tableNotExists;
import static org.jfrog.converter.util.PreDefinedConditions.viewExists;
import static org.jfrog.converter.util.PreDefinedConditions.viewNotExists;
import static org.jfrog.converter.util.PreDefinedConverters.addColumn;
import static org.jfrog.converter.util.PreDefinedConverters.addColumnDefaultValue;
import static org.jfrog.converter.util.PreDefinedConverters.addConstraintForeignKey;
import static org.jfrog.converter.util.PreDefinedConverters.addConstraintPrimaryKey;
import static org.jfrog.converter.util.PreDefinedConverters.alterColumnNullability;
import static org.jfrog.converter.util.PreDefinedConverters.alterColumnSize;
import static org.jfrog.converter.util.PreDefinedConverters.createIndex;
import static org.jfrog.converter.util.PreDefinedConverters.createUniqueIndex;
import static org.jfrog.converter.util.PreDefinedConverters.dropConstraint;
import static org.jfrog.converter.util.PreDefinedConverters.dropDefault;
import static org.jfrog.converter.util.PreDefinedConverters.dropIndex;
import static org.jfrog.converter.util.PreDefinedConverters.dropTable;
import static org.jfrog.converter.util.PreDefinedConverters.renameColumn;
import static org.jfrog.converter.util.PreDefinedConverters.renameTable;
import static org.jfrog.storage.DbType.DERBY;
import static org.jfrog.storage.DbType.MARIADB;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.MYSQL;
import static org.jfrog.storage.DbType.ORACLE;
import static org.jfrog.storage.DbType.POSTGRESQL;

/**
 * Artifactory specific implementation for DbVersion. Each entry holds an info for one db converter.
 *
 * @author Michael Akushsky
 * @author Volodymyr Shulga
 * @author shaharz
 * @see DbVersion
 */
@Slf4j
@SuppressWarnings("squid:S1192")
public enum ArtifactoryDBVersion implements DbVersion {
    v10(SEQ_SYNC, createIndex(BUNDLE_FILES_TABLE, "repo_path_bundle_files_idx", List.of("repo_path"),
            Map.of(DbType.MYSQL, List.of("repo_path(255)"),
                    DbType.MARIADB, List.of("repo_path(255)"))), v600m023, true),
    v11(SEQ_SYNC, new StatementDbConverter(
            "update trusted_keys set alias = kid where alias is null;",
            "Set all alias to kid in trusted_keys if trusted_keys equals NULL",
            executeUnconditional()), v610m002, true),
    v12(SEQ_SYNC, alterColumnNullability(TRUSTED_KEYS_TABLE, "alias", "VARCHAR(255)", false),
            v610m002, true),
    v13(SEQ_SYNC, createUniqueIndex(TRUSTED_KEYS_TABLE, "trusted_keys_alias", List.of("alias")), v610m002, true),
    v14(SEQ_SYNC, dropConstraint(SERVERS_TABLE, "artifactory_servers_pk", dbSpecific(DbType.MSSQL)), v620, true),
    v15(SEQ_SYNC, alterColumnSize(SERVERS_TABLE, "server_id", 128), v620, true),
    v16(SEQ_SYNC, alterColumnNullability(SERVERS_TABLE, "server_id", "VARCHAR(128)", false), v620, true),
    v17(SEQ_SYNC, addConstraintPrimaryKey(SERVERS_TABLE, "artifactory_servers_pk", "server_id",
            dbSpecific(DbType.MSSQL)), v620, true),
    v18(SEQ_SYNC, new ScriptFileDbConverter("v18_ui_session", tableNotExists("UI_SESSION")), v620, true),
    v19(SEQ_SYNC, createIndex("UI_SESSION", "UI_SESSION_IX1", List.of("LAST_ACCESS_TIME")), v620, true),
    v20(SEQ_SYNC, new ScriptFileDbConverter("v20_ui_session_attributes",
            tableNotExists("UI_SESSION_ATTRIBUTES")), v620, true),
    v21(SEQ_SYNC, createIndex("UI_SESSION_ATTRIBUTES", "UI_SESSION_ATTRIBUTES_IX1", List.of("SESSION_ID")),
            v620, true),
    v22(SEQ_SYNC, addColumn(builder().tableName(ARTIFACT_BUNDLES_TABLE).columnName("type").type(VARCHAR).size(32)
            .notNull(true).defaultValue("TARGET").build()), v640m007, true),
    v23(SEQ_SYNC, addColumn(builder().tableName(ARTIFACT_BUNDLES_TABLE).columnName("storing_repo")
            .type(VARCHAR).size(64).build()), v640m007, true),
    v24(SEQ_SYNC, dropIndex(ARTIFACT_BUNDLES_TABLE, "name_version_idx", List.of("name")), v640m007, true),
    v25(SEQ_SYNC, createUniqueIndex(ARTIFACT_BUNDLES_TABLE, "name_version_idx",
            List.of("name", "version", "type")), v640m007, true),
    v26(SEQ_SYNC, new ScriptFileDbConverter("v26_node_props_index", dbSpecific(POSTGRESQL)), v650m001, true),
    v27(SEQ_SYNC, new ScriptFileDbConverter("v27_replication_errors", tableNotExists("replication_errors")),
            v660m001, true),
    v28(SEQ_SYNC, createIndex("replication_errors", "replication_errors_rep_key_idx",
            List.of("replication_key")), v660m001, true),
    v29(SEQ_SYNC, dropIndex(NODES_TABLE, "nodes_repo_path_checksum", List.of("repo_path_checksum"),
            dbSpecific(DbType.MYSQL, DbType.MARIADB)), v660m001, true),
    v30(SEQ_SYNC, createIndex(NODES_TABLE, "nodes_repo_path_checksum", List.of("repo_path_checksum"),
            dbSpecific(DbType.MYSQL, DbType.MARIADB)), v660m001, true),
    v31(SEQ_SYNC, addColumn(builder().tableName(BUNDLE_FILES_TABLE)
            .columnName("original_component_details").type(VARCHAR).size(1000).defaultValue(null).build()),
            v682m001, true),
    v32(SEQ_SYNC, new ScriptFileDbConverter("v32_node_event_cursor_table",
            tableNotExists("node_event_cursor")), v690m001, true),
    v33(SEQ_SYNC, new ScriptFileDbConverter("v33_node_event_priorities_table",
            tableNotExists(EVENT_PRIORITIES_TABLE)), v690m001, true),
    v34(SEQ_SYNC, new ScriptFileDbConverter("v34_migration_status_table",
            tableNotExists("migration_status")), v6110m001, true),
    v35(SEQ_SYNC, new ScriptFileDbConverter("v35_create_jobs_table", tableNotExists(JOBS_TABLE)), v6110m001, true),
    v36(SEQ_SYNC, createIndex(JOBS_TABLE, "jobs_index",
            List.of("started", "finished", "job_status", "job_type")), v6110m001, true),
    v37(SEQ_SYNC, new StatementDbConverter(
            "delete from migration_status where identifier = 'metadata-service-migration';",
            "Delete all from 'migration_status' where identifier equals 'metadata-service-migration'",
            executeUnconditional()), v6110m001, true),
    v38(SEQ_SYNC, new StatementDbConverter(
            "update artifact_bundles set storing_repo = 'release-bundles' where type = 'TARGET';",
            "Set all storing_repo to 'release-bundles' in artifact_bundles if type equals 'TARGET'",
            executeUnconditional()), v6120m079, true),
    v39(SEQ_SYNC, dropIndex(ARTIFACT_BUNDLES_TABLE, "name_version_status_idx",
            List.of("name", "version", "status")), v6120m079, true),
    v40(SEQ_SYNC, createIndex(ARTIFACT_BUNDLES_TABLE, "name_ver_sta_date_typ_repo_idx",
            List.of("name", "version", "status", "date_created", "type", "storing_repo")), v6120m079, true),
    v41(SEQ_SYNC, createIndex(BUNDLE_FILES_TABLE, "bundle_id_bundle_files_idx", List.of("bundle_id")),
            v6120m079, true),
    v42(SEQ_SYNC, addColumn(builder().tableName(EVENTS_CURSOR_TABLE).columnName("type").type(VARCHAR)
            .size(255).notNull(true).defaultValue("UNDEFINED").build()), v6170m001, true),
    v43(SEQ_SYNC, new StatementDbConverter(
            "UPDATE node_event_cursor SET operator_id = 'metadata-operator-events', type = 'METADATA_EVENTS' " +
                    "WHERE operator_id = 'metadata-operator';",
            "Set `METADATA_EVENT` type for node_event_cursor records with `metadata-operator` id.",
            executeUnconditional()), v6170m001, true),
    v44(SEQ_SYNC, alterColumnSize(TASKS_TABLE, "task_context", 2048), v732m017, true),
    v45(SEQ_SYNC, new StatementDbConverter(
            "CREATE INDEX node_props_prop_key_value_idx ON node_props (prop_key) INCLUDE(prop_value);",
            "Create specific MSSQL index 'node_props_prop_key_value_idx' for 'prop_key' include 'prop_value' on " +
                    "'node_props'",
            dbSpecific(DbType.MSSQL),
            indexNotExists(PROPS_TABLE, "node_props_prop_key_value_idx", List.of("prop_key"))), v740m081, true),
    v46(SEQ_SYNC, new StatementDbConverter(
            "CREATE INDEX node_props_prop_value_key_idx ON node_props (prop_value) INCLUDE(prop_key);",
            "Create specific MSSQL index 'node_props_prop_value_key_idx' for 'prop_value' include 'prop_key' on " +
                    "'node_props'",
            dbSpecific(DbType.MSSQL),
            indexNotExists(PROPS_TABLE, "node_props_prop_value_key_idx", List.of("prop_value"))), v740m081, true),
    v47(SEQ_SYNC, new StatementDbConverter(
            "CREATE INDEX node_props_node_prop_value_idx ON node_props(node_id) INCLUDE(prop_key, prop_value);",
            "Create specific MSSQL index 'node_props_node_prop_value_idx' for 'node_id' include 'prop_key' and " +
                    "'prop_value' on 'node_props'",
            dbSpecific(DbType.MSSQL),
            indexNotExists(PROPS_TABLE, "node_props_node_prop_value_idx", List.of("node_id"))), v740m081, true),
    v48(SEQ_SYNC, dropIndex(PROPS_TABLE, "node_props_node_id_idx", List.of("node_id"),
            dbSpecific(DbType.MSSQL)), v740m081, true),
    v49(SEQ_SYNC, dropIndex(PROPS_TABLE, "node_props_prop_key_idx", List.of("prop_key"),
            dbSpecific(DbType.MSSQL)), v740m081, true),
    v50(SEQ_SYNC,
            dropIndex(PROPS_TABLE, "node_props_prop_value_idx", List.of("prop_value"),
                    dbSpecific(DbType.MSSQL)), v740m081, true),
    v51(SEQ_SYNC, alterColumnSize(ARTIFACT_BUNDLES_TABLE, "version", 32), v750m145, true),
    v52(SEQ_SYNC, alterColumnSize(SERVERS_TABLE, "artifactory_version", 128), v760m001, true),
    v53(SEQ_SYNC, alterColumnNullability(SERVERS_TABLE, "artifactory_version", "VARCHAR(128)", false,
            dbSpecific(DbType.MYSQL, DbType.MARIADB, DbType.MSSQL)), v760m001, true),
    v54(SEQ_SYNC, alterColumnSize(DB_PROPERTIES_TABLE, "artifactory_version", 128), v760m001, true),
    v55(SEQ_SYNC, alterColumnNullability(DB_PROPERTIES_TABLE, "artifactory_version", "VARCHAR(128)", false,
            dbSpecific(DbType.MYSQL, DbType.MARIADB, DbType.MSSQL)), v760m001, true),
    v56(SEQ_SYNC, renameColumn("replication_errors", "replication_key", "operation_key", "VARCHAR(255)"),
            v770, true),
    v57(SEQ_SYNC, renameColumn("replication_errors", "task_time", "event_time", "BIGINT NOT NULL"),
            v770, true),
    v58(SEQ_SYNC, renameColumn("replication_errors", "task_type", "event_type", "SMALLINT NOT NULL"),
            v770, true),
    v59(SEQ_SYNC, renameColumn("replication_errors", "task_path", "path", "VARCHAR(1344) NOT NULL"),
            v770, true),
    v60(SEQ_SYNC, addColumn(builder().tableName("replication_errors").columnName("event_category")
            .type(VARCHAR).size(225).build(), tableExists("replication_errors")), v770, true),
    v61(SEQ_SYNC, alterColumnNullability("replication_errors", "operation_key", "VARCHAR(255)", true,
            tableExists("replication_errors")), v770, true),
    v62(SEQ_SYNC, renameTable("replication_errors", "node_events_errors"), v770, true),
    v63(SEQ_SYNC, new StatementDbConverter(
            "UPDATE node_events_errors SET event_category = 'REPLICATION' WHERE event_category IS NULL;",
            "Set all event_category to 'REPLICATION' in node_events_errors if event_category equals NULL",
            tableExists(TABLE_NODE_EVENTS_ERRORS)), v770, true),
    v64(SEQ_SYNC, alterColumnNullability("node_events_errors", "event_category", "VARCHAR(255)", false,
            indexNotExists(TABLE_NODE_EVENTS_ERRORS, "node_events_errors_idx",
                    List.of("operation_key", "event_category", "event_id", "event_time"))), v770, true),
    // failed skipped not changed to skipped??
    v65(SEQ_SYNC, new StatementDbConverter(
            "UPDATE node_events_errors SET event_type = 1 WHERE event_type = 2;",
            "Set all event_type to '1' in node_events_errors if event_type equals '2'",
            columnExists(TABLE_NODE_EVENTS_ERRORS, "event_type")), v770, true),
    v66(SEQ_SYNC, dropIndex(TABLE_NODE_EVENTS_ERRORS, "replication_errors_rep_key_idx", List.of("operation_key")),
            v770, true),
    v67(SEQ_SYNC, createIndex(TABLE_NODE_EVENTS_ERRORS, "node_events_errors_op_key_idx", List.of("operation_key")),
            v770, true),
    v68(SEQ_SYNC, alterColumnSize(SERVERS_TABLE, "server_role", 255), v770, true),
    v69(SEQ_SYNC, alterColumnNullability(SERVERS_TABLE, "server_role", "VARCHAR(255)", false,
            dbSpecific(DbType.MYSQL, DbType.MARIADB, DbType.MSSQL)), v770, true),
    v70(SEQ_SYNC, dropTable("aces"), v770, true),
    v71(SEQ_SYNC, dropTable("acls"), v770, true),
    v72(SEQ_SYNC, dropTable("permission_target_repos"), v770, true),
    v73(SEQ_SYNC, dropTable("permission_targets"), v770, true),
    v74(SEQ_SYNC, dropTable("users_groups"), v770, true),
    v75(SEQ_SYNC, dropTable("groups"), v770, true),
    v76(SEQ_SYNC, dropTable("user_props"), v770, true),
    v77(SEQ_SYNC, dropTable("users"), v770, true),
    v78(SEQ_ASYNC, new ScriptFileDbConverter("v78_change_nodes_node_name_idx",
            dbSpecific(POSTGRESQL), indexNotExists(NODES_TABLE, "nodes_node_name_idx",
            List.of("node_name varchar_pattern_ops"))), v7110m033, true),
    v79(SEQ_ASYNC, new ScriptFileDbConverter("v79_change_nodes_node_path_idx", dbSpecific(POSTGRESQL),
            indexNotExists(NODES_TABLE, "nodes_node_path_idx", List.of("node_path varchar_pattern_ops"))),
            v7110m033, true),
    v80(SEQ_ASYNC, new ScriptFileDbConverter("v80_change_nodes_node_repo_path_idx", dbSpecific(POSTGRESQL),
            indexNotExists(NODES_TABLE, "nodes_repo_path_name_idx", List.of("repo varchar_pattern_ops",
                    "node_path varchar_pattern_ops", "node_name varchar_pattern_ops"))),
            v7110m033, true),
    v81(SEQ_ASYNC, createIndex("bundle_files", "bundle_files_node_id_idx", List.of("node_id")),
            v7110m033, true),
    v82(SEQ_SYNC, new LockingDBSqlConverter("v82_alter_distributed_locks_set_unlogged",
            dbSpecific(POSTGRESQL)), v7110m033, true),
    v83(SEQ_SYNC, addColumn(builder().tableName("builds").columnName("repo").type(VARCHAR)
            .size(64).notNull(true).defaultValue("artifactory-build-info").build()), v7130m001, true),
    v84(SEQ_SYNC, dropIndex("builds", "builds_name_number_date_idx", List.of("build_name")),
            v7130m001, true),
    v85(SEQ_SYNC, createUniqueIndex("builds", "builds_name_num_date_repo_idx",
            List.of("build_name", "build_number", "build_date", "repo")), v7130m001, true),
    v86(SEQ_SYNC, new CreateDbSpecificSequence(), v7170m040, true),
    v87(SEQ_SYNC,
            new ScriptFileDbConverter("v87_node_events_tmp_table", columnNotExists(EVENTS_TEMP_TABLE, "tmp_event_id")),
            v7170m040, true),
    v88(SEQ_SYNC, createIndex(EVENTS_TEMP_TABLE, "node_events_tmp_idx",
            List.of("timestamp", "tmp_event_id")), v7170m040, true),
    v89(SEQ_SYNC, addColumn(builder().tableName(EVENTS_TABLE).columnName("hints").type(VARCHAR)
            .size(4000).build()), v7170m040, true),
    v90(SEQ_SYNC, addColumn(builder().tableName(TABLE_NODE_EVENTS_ERRORS).columnName("event_id")
            .type(BIGINT).build()), v7170m040, true),
    v91(SEQ_SYNC,
            addConstraintForeignKey(TABLE_NODE_EVENTS_ERRORS, "node_events_errors_event_id_fk", "event_id",
                    EVENTS_TABLE, "event_id"), v7170m040, true),
    v92(SEQ_SYNC, dropIndex(TABLE_NODE_EVENTS_ERRORS, "node_events_errors_op_key_idx", List.of("operation_key")),
            v7170m040, true),
    v93(SEQ_SYNC, createIndex(TABLE_NODE_EVENTS_ERRORS, "node_events_errors_idx",
            List.of("operation_key", "event_category", "event_id", "event_time")), v7170m040, true),
    v94(SEQ_SYNC, addColumn(builder().tableName("node_meta_infos").columnName("props_md5").type(CHAR).size(32)
            .build()), v7170m040, true),
    v95(SEQ_ASYNC, createIndex(EVENTS_TEMP_TABLE, "node_events_tmp_event_id_idx", List.of("tmp_event_id"),
            primaryKeyNotExists(EVENTS_TEMP_TABLE, "node_events_tmp_pk")),
            v7201m001, true),
    v96(SEQ_SYNC, dropIndex(TABLE_NODE_EVENTS_ERRORS, "node_events_errors_idx",
            List.of("operation_key", "event_category", "event_id", "event_time"),
            dbSpecific(DbType.MSSQL)), v7201m001, true),
    v97(SEQ_SYNC, new StatementDbConverter(
            "UPDATE node_events_errors SET event_category = 'REPLICATION' WHERE event_category IS NULL;",
            "Set all events in `node_events_errors` missing category to `REPLICATION`",
            dbSpecific(DbType.MSSQL)), v7201m001, true),
    v98(SEQ_SYNC, alterColumnNullability(TABLE_NODE_EVENTS_ERRORS, "event_category", "VARCHAR(255)", false,
            columnSizeNotMatch(TABLE_NODE_EVENTS_ERRORS, "event_category", 255),
            dbSpecific(DbType.MSSQL)), v7201m001, true),
    v99(SEQ_SYNC, createIndex(TABLE_NODE_EVENTS_ERRORS, "node_events_errors_idx",
            List.of("operation_key", "event_category", "event_id", "event_time"), dbSpecific(DbType.MSSQL)),
            v7201m001, true),
    v100(SEQ_SYNC, addColumn(builder().tableName("build_modules").columnName("type").type(VARCHAR)
            .size(64).defaultValue(null).build()), v7230m012, true),
    v101(SEQ_SYNC, alterColumnSize("watches", "username", 255), true),
    v102(SEQ_SYNC, addColumn(builder().tableName("build_modules").columnName("type").type(VARCHAR).size(64)
            .defaultValue(null).build()), true),
    v103(SEQ_SYNC, new ScriptFileDbConverter("v103_binaries_tasks", tableNotExists("binaries_tasks")), true),
    v104(SEQ_SYNC, createIndex("binaries_tasks", "binaries_tasks_time_idx", List.of("time")), true),
    v105(SEQ_SYNC, createIndex("binaries_tasks", "binaries_tasks_start_time_idx", List.of("start_time")), true),
    v106(SEQ_SYNC, createIndex("binaries_tasks", "binaries_tasks_server_id_idx", List.of("server_id")), true),
    v107(SEQ_SYNC, createIndex("binaries_tasks", "binaries_tasks_count_idx", List.of("count")), true),
    v108(SEQ_SYNC, alterColumnSize("node_props", "prop_value", 4000), true),
    v109(SEQ_SYNC,
            new ScriptFileDbConverter("v109_add_distributed_locks_lock_key_idx", tableExists(TABLE_DISTRIBUTED_LOCKS),
                    indexNotExists(TABLE_DISTRIBUTED_LOCKS, "lock_key_idx", List.of("lock_key"))), true),
    v110(SEQ_SYNC,
            addColumn(builder().tableName(ARTIFACT_BUNDLES_TABLE).columnName("date_distributed").type(BIGINT).build()),
            true),
    v111(SEQ_SYNC, new ScriptFileDbConverter("v111_prepare_change_node_props_node_prop_value_idx",
            dbSpecific(POSTGRESQL)), v731m007, true),
    v112(SEQ_SYNC, new ScriptFileDbConverter("v112_alter_node_props_node_prop_value_idx",
            dbSpecific(POSTGRESQL)), v731m007, true),
    v113(SEQ_SYNC, new ScriptFileDbConverter("v113_prepare_change_node_props_prop_key_value_idx",
            dbSpecific(POSTGRESQL)), v731m007, true),
    v114(SEQ_SYNC, new ScriptFileDbConverter("v114_alter_node_props_prop_key_value_idx",
            dbSpecific(POSTGRESQL)), v731m007, true),
    v115(SEQ_SYNC, new ScriptFileDbConverter("v115_prepare_change_node_props_prop_value_key_idx",
            dbSpecific(POSTGRESQL)), v731m007, true),
    v116(SEQ_SYNC, new ScriptFileDbConverter("v116_alter_node_props_prop_value_key_idx",
            dbSpecific(POSTGRESQL)), v731m007, true),
    v117(SEQ_SYNC, addColumnDefaultValue(ARTIFACT_BUNDLES_TABLE, "type", "'TARGET'"), true),
    v118(SEQ_SYNC,
            addColumn(builder().tableName(EVENTS_CURSOR_TABLE).columnName("status")
                    .type(VARCHAR).size(255).defaultValue("SYNC").build()), true),
    v119(SEQ_SYNC,
            addColumn(builder().tableName(EVENTS_CURSOR_TABLE).columnName("last_updated")
                    .type(BIGINT).defaultValue(System.currentTimeMillis()).build()), true),
    v120(SEQ_ASYNC, new SkippableScriptFileDbConverter(
            new ScriptFileDbConverter("v120_prepare_change_node_props_node_prop_value_idx",
                    dbSpecific(POSTGRESQL),
                    indexNotExists("node_props", "pattern_node_props_node_prop_value_idx",
                            List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops")),
                    indexNotExists("node_props", "node_props_node_prop_value_idx",
                            List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops")))),
            v734m001, true),
    v121(SEQ_ASYNC,
            new SkippableScriptFileDbConverter(new AlterIndexAfterConcurrentCreation("node_props_node_prop_value_idx",
                    "pattern_node_props_node_prop_value_idx", "v121_alter_node_props_node_prop_value_idx",
                    dbSpecific(POSTGRESQL), indexNotExists("node_props", "node_props_node_prop_value_idx",
                    List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"))))
            , v734m001, true),
    v122(SEQ_ASYNC, new SkippableScriptFileDbConverter(
            new ScriptFileDbConverter("v122_prepare_change_node_props_prop_key_value_idx",
                    dbSpecific(POSTGRESQL),
                    indexNotExists("node_props", "pattern_node_props_prop_key_value_idx",
                            List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops")),
                    indexNotExists("node_props", "node_props_prop_key_value_idx",
                            List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"))))
            , v734m001, true),
    v123(SEQ_ASYNC,
            new SkippableScriptFileDbConverter(new AlterIndexAfterConcurrentCreation("node_props_prop_key_value_idx",
                    "pattern_node_props_prop_key_value_idx", "v123_alter_node_props_prop_key_value_idx",
                    dbSpecific(POSTGRESQL), indexNotExists("node_props", "node_props_prop_key_value_idx",
                    List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops")))), v734m001, true),
    v124(SEQ_ASYNC, new SkippableScriptFileDbConverter(
            new ScriptFileDbConverter("v124_prepare_change_node_props_prop_value_key_idx",
                    dbSpecific(POSTGRESQL), indexNotExists("node_props", "pattern_node_props_prop_value_key_idx",
                    List.of("prop_value varchar_pattern_ops", "prop_key varchar_pattern_ops")),
                    indexNotExists("node_props", "node_props_prop_value_key_idx",
                            List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops")))),
            v734m001, true),
    v125(SEQ_ASYNC,
            new SkippableScriptFileDbConverter(new AlterIndexAfterConcurrentCreation("node_props_prop_value_key_idx",
                    "pattern_node_props_prop_value_key_idx", "v125_alter_node_props_prop_value_key_idx",
                    dbSpecific(POSTGRESQL), indexNotExists("node_props", "node_props_prop_value_key_idx",
                    List.of("prop_value varchar_pattern_ops", "prop_key varchar_pattern_ops")))), v734m001, true),
    v126(SEQ_SYNC, dropDefault(EVENTS_CURSOR_TABLE, "status"), v7380, true),
    v127(SEQ_SYNC, dropDefault(EVENTS_CURSOR_TABLE, "last_updated"), v7380, true),
    v128(SEQ_SYNC, alterColumnSize("distributed_locks", "category", 128,
            dbSpecific(DbType.DERBY, DbType.MARIADB, DbType.MYSQL, POSTGRESQL,
                    DbType.ORACLE)), v7400, true),
    v129(SEQ_SYNC, new ScriptFileDbConverter("v129_alter_category_size_column",
            dbSpecific(DbType.MSSQL)), v7400, true),
    v130(SEQ_SYNC, addColumn(builder().tableName(ARTIFACT_BUNDLES_TABLE).columnName("keep").type(SMALLINT).build()),
            v7460m001, true),
    v131(SEQ_SYNC, new ScriptFileDbConverter("v131_heavy_query_cache", tableNotExists("heavy_query_cache")), true),
    v132(SEQ_SYNC, createIndex("heavy_query_cache", "hqc_key_idx", List.of("cache_key")), true),
    v133(SEQ_SYNC, createIndex("heavy_query_cache", "hqc_expiry_time_idx", List.of("expiry_time")), true),
    v134(SEQ_SYNC, new ScriptFileDbConverter("skip_version"), true),
    v135(SEQ_SYNC, new ScriptFileDbConverter("skip_version"), true),
    v136(SEQ_SYNC, new ScriptFileDbConverter("skip_version"), true),
    v137(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v138(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v139(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v140(SEQ_SYNC, new ScriptFileDbConverter("skip_version"), true),
    v141(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v142(SEQ_SYNC, new ScriptFileDbConverter("v142_repository_config_table",
            tableNotExists("repository_config")), true),
    v143(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v144(SEQ_ASYNC, new ScriptFileDbConverter("v144_update_artifact_bundles_date_distributed"), true),
    v145(SEQ_SYNC, createIndex("binaries_tasks", "binaries_tasks_repo_key_idx", List.of("local_repo_key"),
            dbSpecific(DbType.DERBY), indexNotExists("binaries_tasks", "binaries_tasks_repo_key_idx",
                    List.of("local_repo_key"))), true),
    v146(SEQ_ASYNC, new ScriptFileDbConverter("v146_binaries_tasks_add_index_repo", dbSpecific(POSTGRESQL,
            DbType.ORACLE, DbType.MARIADB, DbType.MYSQL, DbType.MSSQL),
            indexNotExists("binaries_tasks", "binaries_tasks_repo_key_idx",
                    List.of("local_repo_key"))), true),
    v150(SEQ_SYNC, new ScriptFileDbConverter("v150_mirrors_metadata_table",
            tableNotExists("mirrors_metadata")), true),
    v151(SEQ_ASYNC, new SafeIndexConcurrentCreator("nodes_repo_path_name_idx", NODES_TABLE,
            List.of("repo varchar_pattern_ops", "node_path varchar_pattern_ops", "node_name varchar_pattern_ops"),
            "CREATE UNIQUE INDEX CONCURRENTLY nodes_repo_path_name_idx ON nodes " +
                    "(repo varchar_pattern_ops, node_path varchar_pattern_ops, node_name varchar_pattern_ops);",
            dbSpecific(POSTGRESQL), indexExistsAndInValid("nodes_repo_path_name_idx", NODES_TABLE,
            List.of("repo varchar_pattern_ops", "node_path varchar_pattern_ops",
                    "node_name varchar_pattern_ops"))), true),
    v152(SEQ_ASYNC, new SafeIndexConcurrentCreator("nodes_node_name_idx", NODES_TABLE,
            List.of("node_name varchar_pattern_ops"), dbSpecific(POSTGRESQL),
            indexExistsAndInValid("nodes_node_name_idx", NODES_TABLE,
                    List.of("node_name varchar_pattern_ops"))), true),
    v153(SEQ_ASYNC, new SafeIndexConcurrentCreator("nodes_node_path_idx",
            NODES_TABLE, List.of("node_path varchar_pattern_ops"), dbSpecific(POSTGRESQL),
            indexExistsAndInValid("nodes_node_path_idx", NODES_TABLE, List.of("node_path varchar_pattern_ops"))),
            true),
    v154(SEQ_ASYNC, new SafeIndexConcurrentCreator("node_props_prop_value_key_idx", "node_props",
            List.of("prop_value varchar_pattern_ops", "prop_key varchar_pattern_ops"),
            dbSpecific(POSTGRESQL), indexExistsAndInValid("node_props_prop_value_key_idx", "node_props",
            List.of("prop_value varchar_pattern_ops", "prop_key varchar_pattern_ops"))), true),
    v155(SEQ_ASYNC, new SafeIndexConcurrentCreator("node_props_prop_key_value_idx", "node_props",
            List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"),
            dbSpecific(POSTGRESQL), indexExistsAndInValid("node_props_prop_key_value_idx", "node_props",
            List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"))), true),
    v156(SEQ_ASYNC, new SafeIndexConcurrentCreator("node_props_node_prop_value_idx", "node_props",
            List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"),
            dbSpecific(POSTGRESQL), indexExistsAndInValid("node_props_node_prop_value_idx", "node_props",
            List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"))), true),
    v157(SEQ_ASYNC, new SkipOnFailsDBConvertor("v157_create_pg_trgm_extension", true, dbSpecific(POSTGRESQL),
            extensionNotExists("pg_trgm")), true),
    v158(SEQ_ASYNC, new SafeIndexConcurrentCreator("nodes_lower_node_name_gin_idx", "nodes", List.of("node_name"),
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS nodes_lower_node_name_gin_idx ON nodes " +
                    "USING gin(LOWER (node_name) gin_trgm_ops) WHERE node_type = 1;",
            dbSpecific(POSTGRESQL), extensionExists("pg_trgm")), true),
    v159(SEQ_ASYNC, dropIndex("node_props", "pattern_node_props_node_prop_value_idx",
            List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"),
            dbSpecific(POSTGRESQL), indexExists("node_props", "node_props_node_prop_value_idx",
                    List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops")),
            indexValid("node_props_node_prop_value_idx")), true),
    v160(SEQ_ASYNC, dropIndex("node_props", "pattern_node_props_prop_key_value_idx",
            List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"),
            dbSpecific(POSTGRESQL), indexExists("node_props", "node_props_prop_key_value_idx",
                    List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops")),
            indexValid("node_props_prop_key_value_idx")), true),
    v161(SEQ_ASYNC, dropIndex("node_props", "pattern_node_props_prop_value_key_idx",
            List.of("prop_value varchar_pattern_ops", "prop_key varchar_pattern_ops"), dbSpecific(POSTGRESQL),
            indexExists("node_props", "node_props_prop_value_key_idx",
                    List.of("prop_value varchar_pattern_ops", "prop_key varchar_pattern_ops")),
            indexValid("node_props_prop_value_key_idx")), true),
    v162(SEQ_ASYNC,
            new SafeIndexConcurrentCreator("pattern_bundle_files_repo_path_idx", "bundle_files",
                    List.of("repo_path varchar_pattern_ops"),
                    dbSpecific(DbType.POSTGRESQL)), true),
    v163(SEQ_ASYNC, dropIndex("bundle_files", "repo_path_bundle_files_idx", List.of("repo_path"),
            dbSpecific(DbType.POSTGRESQL),
            indexExists("bundle_files", "pattern_bundle_files_repo_path_idx", List.of("repo_path varchar_pattern_ops")),
            indexExists("bundle_files", "repo_path_bundle_files_idx", List.of("repo_path"))), true),
    v164(SEQ_SYNC, addConstraintPrimaryKey(EVENTS_TEMP_TABLE, "node_events_tmp_pk", "tmp_event_id",
            primaryKeyNotExists(EVENTS_TEMP_TABLE, "node_events_tmp_pk"),
            tableNotExists("node_events_tmp_p0_5")), true),
    v165(SEQ_ASYNC, dropIndex(EVENTS_TEMP_TABLE, "node_events_tmp_event_id_idx", List.of("tmp_event_id"),
            dbSpecific(POSTGRESQL, DERBY, MSSQL, MYSQL, MARIADB),
            primaryKeyExists(EVENTS_TEMP_TABLE, "node_events_tmp_pk"),
            indexExists(EVENTS_TEMP_TABLE, "node_events_tmp_event_id_idx", List.of("tmp_event_id"))), true),
    v166(SEQ_ASYNC, new ScriptFileDbConverter("v166_set_repository_created_date"), true),
    v167(SEQ_ASYNC, new ScriptFileDbConverter("v167_rename_retention_restore_prefix"), true),
    v168(SEQ_SYNC, new ScriptFileDbConverter("v168_storage_events_table", tableNotExists("storage_events")), true),
    v169(SEQ_SYNC, createIndex("storage_events", "storage_events_sha1_type_idx",
            List.of("sha1", "event_type")), true),
    v170(SEQ_SYNC,
            createIndex("storage_events", "storage_events_bin_size_idx", List.of("bin_length")), true),
    v171(SEQ_SYNC, new ScriptFileDbConverter("v171_rename_retention_archive_prefix"), true),
    v172(SEQ_SYNC, addColumn(
            builder().tableName(DB_PROPERTIES_TABLE).columnName("artifactory_product_version").type(VARCHAR)
                    .size(128).build()), true),
    v173(SEQ_ASYNC, new CleanUpTasksTableDBConvertor("v173_cleanup_tasks_table", tableExists(TASKS_TABLE),
            columnNotExists("tasks", "task_id")), true),
    v174(SEQ_ASYNC, new ScriptFileDbConverter("v174_add_new_column_on_tasks_with_pk",
            columnNotExists("tasks", "task_id")), true),
    v175(SEQ_ASYNC, new SkipOnFailsDBConvertor("v175_create_primary_key_on_build_promotion", true,
            indexExists("build_promotions", "build_promotions_created_idx", List.of("build_id", "created"))), true),
    v176(SEQ_ASYNC, dropIndex("build_promotions", "build_promotions_created_idx", List.of("build_id", "created"),
            dbSpecific(DERBY, MSSQL, MYSQL, MARIADB),
            primaryKeyExists("build_promotions", "build_promotions_builds_pk")), true),
    v177(SEQ_ASYNC,
            new SafeIndexConcurrentCreator("nodes_name_path_idx", NODES_TABLE,
                    List.of("node_name varchar_pattern_ops", "node_path varchar_pattern_ops"),
                    dbSpecific(DbType.POSTGRESQL)), true),
    v178(SEQ_ASYNC, new DropIndexConcurrentlyConvertor("nodes_node_name_idx", NODES_TABLE,
            List.of("node_name varchar_pattern_ops"), indexExists(NODES_TABLE, "nodes_name_path_idx",
            List.of("node_name varchar_pattern_ops", "node_path varchar_pattern_ops")),
            indexValid("nodes_name_path_idx"),
            dbSpecific(POSTGRESQL)), true),
    v179(SEQ_ASYNC, createIndex(NODES_TABLE, "nodes_name_path_idx",
            List.of("node_name", "node_path"),
            Map.of(DbType.MYSQL, List.of("node_name", "node_path(255)"),
                    DbType.MARIADB, List.of("node_name", "node_path(255)"),
                    DbType.DERBY, List.of("node_name", "node_path"),
                    DbType.MSSQL, List.of("node_name", "node_path"),
                    DbType.ORACLE, List.of("node_name", "node_path")),
            dbSpecific(MYSQL, MARIADB, ORACLE, DERBY, MSSQL)), true),
    v180(SEQ_ASYNC, dropIndex(NODES_TABLE, "nodes_node_name_idx", List.of("node_name"),
            indexExists(NODES_TABLE, "nodes_name_path_idx", List.of("node_name", "node_path")),
            dbSpecific(DbType.DERBY, MSSQL, MYSQL, ORACLE, MARIADB)), true),
    v181(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v182(SEQ_ASYNC, new SuspendOnConditionPsqlDbConvertor("v182_create_partitions_for_node_events_tmp",
            ValidPsqlConversionVersion.PSQL_10,
            dbSpecific(POSTGRESQL), tableNotExists("node_events_tmp_p0_5")), true),
    v183(SEQ_ASYNC, new SuspendOnConditionPsqlDbConvertor("v183_adjust_partitions_for_replications",
            ValidPsqlConversionVersion.PSQL_10,
            dbSpecific(POSTGRESQL), tableExists("node_events_tmp_p0_5"),
            indexNotExists("node_events_tmp_p0_5", "node_events_tmp_p0_5_idx_time_id",
                    List.of("timestamp", "tmp_event_id")),
            primaryKeyNotExists("node_events_tmp_p0_5", "node_events_tmp_partition_0_5_pk")), true),
    v184(SEQ_SYNC, new ScriptFileDbConverter("v184_quartz_task_history_table",
            tableNotExists(QuartzTaskHistoryTable.TABLE_NAME)), true),
    v185(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v185_alter_prop_value_column",
            "prop_value", dbSpecific(POSTGRESQL), tableExists("node_props"),
            columnExists("node_props", "prop_value")), true),
    v186(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v186_alter_prop_key_column",
            "prop_key", dbSpecific(POSTGRESQL), tableExists("node_props"),
            columnExists("node_props", "prop_key")), true),
    v187(SEQ_ASYNC, new SuspendOnConditionOracleConvertor("v187_alter_node_events_sequence",
            ValidOracleConversionVersion.RAC, dbSpecific(ORACLE)), true),
    v188(SEQ_SYNC, new ScriptFileDbConverter("v188_file_list_tasks_table",
            tableNotExists("file_list_tasks")), true),
    v189(SEQ_SYNC, new ScriptFileDbConverter("v189_add_service_id_column_to_artifact_bundles",
            tableExists("artifact_bundles"),
            columnNotExists("artifact_bundles", "source_service_id")), true),
    v190(SEQ_SYNC, addColumn(
            builder().tableName("storage_events").columnName("data").type(VARCHAR).size(1024).build()), true),
    v191(SEQ_SYNC, new ScriptFileDbConverter("v191_create_cleanup_run_summary_table",
            tableNotExists("cleanup_run_summary")), true),
    v192(SEQ_SYNC, new ScriptFileDbConverter("v192_create_nodes_cleaned_table", tableNotExists("nodes_cleaned")),
            true),
    v193(SEQ_SYNC, new ScriptFileDbConverter("v193_recreate_quartz_task_history_table",
            columnExists(QuartzTaskHistoryTable.TABLE_NAME, "execution_id")), true),
    v194(SEQ_SYNC, new ScriptFileDbConverter("v194_storage_multipart_uploads_table",
            tableNotExists("storage_multipart_uploads")), true),
    v195(SEQ_SYNC, new ScriptFileDbConverter("v195_update_node_event_cursor_status"), true),
    v196(SEQ_ASYNC, new SafeIndexConcurrentCreator("node_prop_nuget_partial_idx", "node_props",
            List.of("prop_value"),
            "CREATE INDEX CONCURRENTLY node_prop_nuget_partial_idx ON node_props ((lower(prop_value))) WHERE lower" +
                    "(prop_key)='nuget.id';", dbSpecific(POSTGRESQL),
            indexNotExists("node_props", "node_prop_nuget_partial_idx", List.of("prop_value"))), true),
    v197(SEQ_SYNC, new ScriptFileDbConverter("v197_alter_cleanup_run_summary_table",
            columnExists("cleanup_run_summary", "estimated_packages_count")), true),
    v198(SEQ_SYNC, new ScriptFileDbConverter("v198_create_policies_table", tableNotExists("policy")), true),
    v199(SEQ_SYNC, addColumn(
            builder().tableName("cleanup_run_summary").columnName("total_binary_size").type(BIGINT).defaultValue(0)
                    .notNull(true).build()), true),
    v200(SEQ_SYNC, alterColumnSize(EVENT_PRIORITIES_TABLE, "path", 1344), true),
    v201(SEQ_SYNC, new ScriptFileDbConverter("v201_create_config_history_table", tableNotExists("config_history")),
            false),
    v202(SEQ_SYNC, new ScriptFileDbConverter("v202_create_version_history_table", tableNotExists("version_history")),
            false),
    v203(SEQ_SYNC, new ScriptFileDbConverter("v203_alter_version_history_table",
            columnNotExists("version_history", "active")), true),
    v204(SEQ_SYNC, new ScriptFileDbConverter("v204_metadata_service_rollback_blocker"), false),
    v205(SEQ_SYNC, new ScriptFileDbConverter("v205_alter_trusted_keys_table",
            columnNotExists("trusted_keys", "key_type")), true),
    v206(SEQ_ASYNC, new ScriptFileDbConverter("v206_nodes_add_index_repo_created",
            dbSpecific(ORACLE, MARIADB, MYSQL, DERBY),
            indexNotExists(NODES_TABLE, "nodes_repo_created_idx", List.of("repo", "created"))), true),
    v207(SEQ_ASYNC, new SafeIndexConcurrentCreator("nodes_repo_created_idx", NODES_TABLE, List.of("repo", "created"),
            dbSpecific(POSTGRESQL)), true),
    v226(SEQ_ASYNC, new ScriptFileDbConverter("v226_nodes_add_index_repo_created_for_mssql_enterprise",
            dbSpecific(MSSQL),
            indexNotExists(NODES_TABLE, "nodes_repo_created_idx", List.of("repo", "created")),
            enterpriseEdition()), true),
    v227(SEQ_ASYNC, new ScriptFileDbConverter("v227_nodes_add_index_repo_created_for_mssql_non_enterprise",
            dbSpecific(MSSQL),
            indexNotExists(NODES_TABLE, "nodes_repo_created_idx", List.of("repo", "created")),
            enterpriseEdition().negate()), true),
    v208(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v209(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v210(SEQ_ASYNC, new ScriptFileDbConverter("v210_alter_nodes_cleaned_table",
            foreignKeyExists("nodes_cleaned", "nodes_cleaned_cleanup_run_summary_fk")), true),
    v211(SEQ_ASYNC, new ScriptFileDbConverter("v211_alter_nodes_cleaned_table",
            dbSpecific(ORACLE, MYSQL, MARIADB, DERBY),
            indexNotExists("nodes_cleaned", "nodes_cleaned_policy_key_policy_run_id_idx",
                    List.of("policy_key", "policy_run_id"))), true),
    v212(SEQ_ASYNC, new SafeIndexConcurrentCreator("nodes_cleaned_policy_key_policy_run_id_idx", "nodes_cleaned",
            List.of("policy_key", "policy_run_id"), dbSpecific(POSTGRESQL)), true),
    v228(SEQ_ASYNC, new ScriptFileDbConverter("v228_alter_nodes_cleaned_table_for_mssql_enterprise",
            dbSpecific(MSSQL),
            indexNotExists("nodes_cleaned", "nodes_cleaned_policy_key_policy_run_id_idx",
                    List.of("policy_key", "policy_run_id")),
            enterpriseEdition()), true),
    v229(SEQ_ASYNC, new ScriptFileDbConverter("v229_alter_nodes_cleaned_table_for_mssql_non_enterprise",
            dbSpecific(MSSQL),
            indexNotExists("nodes_cleaned", "nodes_cleaned_policy_key_policy_run_id_idx",
                    List.of("policy_key", "policy_run_id")),
            enterpriseEdition().negate()), true),
    v213(SEQ_SYNC, new ScriptFileDbConverter("v213_create_retention_run_summary_table",
            tableNotExists("retention_run_summary")), true),
    v214(SEQ_SYNC, new ScriptFileDbConverter("v214_create_ui_session_v2_table", tableNotExists("UI_SESSION_V2")),
            true),
    v215(SEQ_SYNC, addColumn(
            builder().tableName("cleanup_run_summary").columnName("run_config").type(BLOB)
                    .notNull(false).build()), true),
    v216(SEQ_ASYNC, new ScriptFileDbConverter("skip_version"), true),
    v217(SEQ_SYNC, new ScriptFileDbConverter("v204_metadata_service_rollback_blocker"), false),
    v218(SEQ_SYNC, addColumn(
            builder().tableName("retention_run_summary").columnName("run_config").type(BLOB)
                    .notNull(false).build()), true),
    v219(SEQ_ASYNC, dropIndex(NODES_TABLE, "nodes_repo_path_checksum", List.of("repo_path_checksum"),
            indexExists(NODES_TABLE, "nodes_repo_path_checksum", List.of("repo_path_checksum"))), true),
    v220(SEQ_SYNC, new ScriptFileDbConverter("v220_create_build_release_bundles_table",
            tableNotExists("build_release_bundles")), true),
    v221(SEQ_SYNC, alterColumnSize("build_dependencies", "dependency_scopes", 4000), true),
    v222(SEQ_ASYNC, new ArchiveIndexCleanupDBConvertor("v222_cleanup_archive_index_tables",
            tableExists("indexed_archives_entries"), tableExists("indexed_archives"),
            tableExists("archive_paths"), tableExists("archive_names")), true),
    v223(SEQ_SYNC, addColumn(
            builder().tableName("builds").columnName("immutable").type(SMALLINT).defaultValue(null)
                    .build()), true),
    v224(SEQ_SYNC, new ScriptFileDbConverter("v204_metadata_service_rollback_blocker"), false),
    v225(SEQ_SYNC, alterColumnSize("artifact_bundles", "version", 255), true),
    v230(SEQ_SYNC, new ScriptFileDbConverter("v230_create_repository_stats_table",
            tableNotExists("repository_stats")), true),
    v231(SEQ_SYNC, dropTable("repository_stats"), true),
    v232(SEQ_SYNC, new ScriptFileDbConverter("v232_create_repository_stats_table",
            tableNotExists("repository_stats")), true),
    v233(SEQ_SYNC, new ScriptFileDbConverter("v233_create_traffic_summary_table",
            tableNotExists("traffic_summary")), true),
    v234(SEQ_ASYNC, new SafeIndexConcurrentCreator("repo_build_name_date_idx", "builds",
            List.of("repo", "build_name", "build_date"),
            dbSpecific(DbType.POSTGRESQL),
            indexNotExists("builds", "repo_build_name_date_idx",
                    List.of("repo", "build_name", "build_date"))), true),
    v235(SEQ_ASYNC, new ScriptFileDbConverter("v235_builds_add_index_build_name_repo_date",
            dbSpecific(DbType.ORACLE, DbType.MARIADB, DbType.MYSQL, DbType.MSSQL),
            indexNotExists("builds", "repo_build_name_date_idx",
                    List.of("repo", "build_name", "build_date"))), true),
    v236(SEQ_ASYNC, createIndex("builds", "repo_build_name_date_idx",
            List.of("repo", "build_name", "build_date"),
            dbSpecific(DbType.DERBY), indexNotExists("builds", "repo_build_name_date_idx",
                    List.of("repo", "build_name", "build_date"))), true),
    v237(SEQ_SYNC, new ScriptFileDbConverter("v237_create_distributed_blocking_locks_table",
            tableNotExists("distributed_blocking_locks")), true),
    v238(SEQ_SYNC, addColumn(
            builder().tableName("nodes_cleaned").columnName("metadata").type(BLOB)
                    .notNull(false).build()), true),
    v239(SEQ_SYNC, new ScriptFileDbConverter("v239_create_grid_topology_table",
            tableNotExists("grid_topology")), true),
    v240(SEQ_SYNC, new ScriptFileDbConverter("v240_create_project_storage_cache_table",
            tableNotExists("storage_cache")), true),
    v241(SEQ_ASYNC, new SafeIndexConcurrentCreator("nodes_pom_files_idx", "nodes",
            List.of("repo", "depth", "node_path varchar_pattern_ops"),
            "CREATE INDEX CONCURRENTLY nodes_pom_files_idx ON nodes (repo, depth, node_path varchar_pattern_ops)" +
                    " WHERE node_name LIKE '%.pom' ;", dbSpecific(POSTGRESQL),
            indexNotExists("nodes", "nodes_pom_files_idx", List.of("repo", "depth", "node_path varchar_pattern_ops"))),
            true),
    v242(SEQ_SYNC, new ScriptFileDbConverter("v242_create_nodes_view",
            dbSpecific(POSTGRESQL), viewNotExists("nodes_view")), true),
    v243(SEQ_SYNC, new ScriptFileDbConverter("v243_create_repos_view",
            dbSpecific(POSTGRESQL), viewNotExists("repos_view")), true),
    v244(SEQ_ASYNC, new SafeIndexConcurrentCreator("node_events_timestamp_event_id_type_idx", EVENTS_TABLE,
            List.of("timestamp", "event_id", "event_type"), dbSpecific(DbType.POSTGRESQL),
            indexNotExists(EVENTS_TABLE, "node_events_timestamp_event_id_type_idx",
                    List.of("timestamp", "event_id", "event_type"))), true),
    v245(SEQ_ASYNC, new DropIndexConcurrentlyConvertor("node_events_full_idx", EVENTS_TABLE,
            List.of("timestamp", "event_id", "event_type", "path"), indexExists(EVENTS_TABLE, "node_events_full_idx",
            List.of("timestamp", "event_id", "event_type", "path")), dbSpecific(POSTGRESQL)), true),
    v246(SEQ_ASYNC, new ScriptFileDbConverter("v246_create_optimized_node_events_index",
            indexNotExists(EVENTS_TABLE, "node_events_timestamp_event_id_type_idx",
                    List.of("timestamp", "event_id", "event_type")),
            dbSpecific(MYSQL, MARIADB, ORACLE, DERBY, MSSQL)), true),
    v247(SEQ_ASYNC, dropIndex(EVENTS_TABLE, "node_events_full_idx",
            List.of("timestamp", "event_id", "event_type", "path"), indexExists(EVENTS_TABLE,
                    "node_events_full_idx", List.of("timestamp", "event_id", "event_type", "path")),
            dbSpecific(MYSQL, MARIADB, ORACLE, DERBY, MSSQL)), true),
    v248(SEQ_SYNC,
            new StatementDbConverter("DROP VIEW nodes_view;",
                    "removes old view before recreating a fixed one in the next converter",
                    dbSpecific(POSTGRESQL), viewExists("nodes_view")), true),
    v249(SEQ_SYNC, new ScriptFileDbConverter("v249_fix_nodes_view_dot_path",
            dbSpecific(POSTGRESQL), viewNotExists("nodes_view")), true),
    v250(SEQ_SYNC, new ScriptFileDbConverter("v250_create_remote_repo_stats_hourly_table",
            tableNotExists("remote_repo_stats_hourly")), true),
    v251(SEQ_SYNC, new ScriptFileDbConverter("v251_create_remote_repo_stats_daily_table",
            tableNotExists("remote_repo_stats_daily")), true),
    v252(SEQ_SYNC, new ScriptFileDbConverter("v252_create_remote_repo_stats_status_table",
            tableNotExists("remote_repo_stats_status")), true),
    v253(SEQ_SYNC, new ScriptFileDbConverter("v253_create_repo_traffic_hourly_table",
            tableNotExists("repo_traffic_hourly")), true),
    v254(SEQ_SYNC, new ScriptFileDbConverter("v254_create_repo_traffic_daily_table",
            tableNotExists("repo_traffic_daily")), true),
    v255(SEQ_SYNC, new ScriptFileDbConverter("v255_create_repo_migrations_table",
            tableNotExists("repo_migrations")), true),
    v256(SEQ_SYNC, new ScriptFileDbConverter("v256_create_repo_migrations_history_table",
            tableNotExists("repo_migrations_history")), true),
    v257(SEQ_ASYNC, new SafeIndexConcurrentCreator(
            "nodes_type_modified_id_idx",
            "nodes",
            List.of("node_type", "modified", "node_id"),
            """
                         create index concurrently nodes_type_modified_id_idx
                          on nodes (node_type, modified desc, node_id desc)
                          where repo <> 'auto-trashcan';
               """,
            aolOnly(),
            dbSpecific(POSTGRESQL),
            indexNotExists("nodes",
                    "nodes_type_modified_id_idx",
                    List.of("node_type", "modified", "node_id"))),
            true
    ),
    v258(SEQ_ASYNC, dropIndex(TABLE_DISTRIBUTED_LOCKS, "distributed_locks_owner_thread",
            List.of("owner_thread"), indexExists(TABLE_DISTRIBUTED_LOCKS,
                    "distributed_locks_owner_thread", List.of("owner_thread")),
            dbSpecific(MYSQL, MARIADB, ORACLE, DERBY, MSSQL)), true),
    v259(SEQ_ASYNC, new DropIndexConcurrentlyConvertor("distributed_locks_owner_thread",
            TABLE_DISTRIBUTED_LOCKS, List.of("owner_thread"),
            dbSpecific(POSTGRESQL)), true),
    v260(SEQ_ASYNC, dropIndex(TABLE_DISTRIBUTED_LOCKS, "lock_key_idx",
            List.of("lock_key"), indexExists(TABLE_DISTRIBUTED_LOCKS, "lock_key_idx", List.of("lock_key")),
            dbSpecific(MYSQL, MARIADB, ORACLE, DERBY, MSSQL)), true),
    v261(SEQ_ASYNC, dropIndex(TABLE_DISTRIBUTED_LOCKS, "distributed_locks_owner",
            List.of("owner"), indexExists(TABLE_DISTRIBUTED_LOCKS, "distributed_locks_owner", List.of("owner")),
            dbSpecific(MYSQL, MARIADB, ORACLE, DERBY, MSSQL)), true),
    v262(SEQ_ASYNC, new SafeIndexConcurrentCreator(
            "nodes_ts_artifacts_idx",
            "nodes",
            List.of("node_path", "node_name"),
            """
                create index concurrently nodes_ts_artifacts_idx
                    on nodes using gin (
                    to_tsvector('simple', translate(
                    (((repo || ' ') || node_path) || ' ') || node_name, '/-_', '   ')))
                where (((repo) <> 'auto-trashcan') AND (node_type = 1));
               """,
            aolOnly(),
            dbSpecific(POSTGRESQL),
            indexNotExists("nodes",
                    "nodes_ts_artifacts_idx",
                    List.of("node_path", "node_name"))),
            true
    ),
    v263(SEQ_ASYNC, new SafeIndexConcurrentCreator(
            "nodes_ts_folders_idx",
            "nodes",
            List.of("node_path", "node_name"),
            """
                create index concurrently nodes_ts_folders_idx
                    on nodes using gin (
                    to_tsvector('simple', translate(repo || ' ' || node_path || ' ' || node_name, '/-_', '   ')))
                where (repo <> 'auto-trashcan' AND (node_type = 0) AND (depth > 0));
               """,
            aolOnly(),
            dbSpecific(POSTGRESQL),
            indexNotExists("nodes",
                    "nodes_ts_folders_idx",
                    List.of("node_path", "node_name"))),
            true
    ),
    v264(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v264_alter_nodes_node_path_column",
            NODES_TABLE, "node_path", dbSpecific(POSTGRESQL), tableExists(NODES_TABLE),
            columnExists(NODES_TABLE, "node_path")), true),
    v265(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v265_alter_nodes_sha256_column",
            NODES_TABLE, "sha256", dbSpecific(POSTGRESQL), tableExists(NODES_TABLE),
            columnExists(NODES_TABLE, "sha256")), true),
    v266(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v266_alter_nodes_sha1_actual_column",
            NODES_TABLE, "sha1_actual", dbSpecific(POSTGRESQL), tableExists(NODES_TABLE),
            columnExists(NODES_TABLE, "sha1_actual")), true),
    v267(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v267_alter_nodes_repo_column",
            NODES_TABLE, "repo", dbSpecific(POSTGRESQL), tableExists(NODES_TABLE),
            columnExists(NODES_TABLE, "repo")), true),
    v268(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v268_alter_nodes_node_name_column",
            NODES_TABLE, "node_name", dbSpecific(POSTGRESQL), tableExists(NODES_TABLE),
            columnExists(NODES_TABLE, "node_name")), true),
    v269(SEQ_SYNC, new ScriptFileDbConverter(
                    "v269_create_virtual_repo_deferred_dangling_ref_cleanup_table",
            tableNotExists("virtual_repo_dangling_refs")), true),
    v270(SEQ_ASYNC, new ScriptFileDbConverter("v270_builds_add_index_build_name_repo_date_desc",
            dbSpecific(POSTGRESQL),
            indexNotExists("builds", "repo_build_name_date_desc_idx",
                    List.of("repo", "build_name", "build_date", "build_id", "build_number"))), true),
    v271(SEQ_ASYNC, new DropIndexConcurrentlyConvertor("repo_build_name_date_idx", "builds",
            List.of("repo", "build_name", "build_date"), indexExists("builds", "repo_build_name_date_idx",
            List.of("repo", "build_name", "build_date")), dbSpecific(POSTGRESQL)), true),
    v272(SEQ_SYNC, new ScriptFileDbConverter("v272_create_transparent_repo_config_table",
            tableNotExists("transparent_repo_config")), true),
    v273(SEQ_ASYNC, new ScriptFileDbConverter("v273_adjust_node_events_tmp_partition_pk",
            dbSpecific(POSTGRESQL), tableExists("node_events_tmp_p0_5")), true),
    v274(SEQ_SYNC, new ScriptFileDbConverter(
            "v274_create_error_catalog_tables",
            tableNotExists("error_catalog_templates")), true),
    v275(SEQ_ASYNC, new SafeIndexConcurrentCreator(
            "error_catalog_templates_msg_fts_idx",
            "error_catalog_templates",
            List.of("message_tsv"),
            """
                create index concurrently error_catalog_templates_msg_fts_idx
                    on error_catalog_templates using gin (message_tsv);
               """,
            dbSpecific(POSTGRESQL),
            indexNotExists("error_catalog_templates",
                    "error_catalog_templates_msg_fts_idx",
                    List.of("message_tsv"))),
            true
    ),
    v276(SEQ_SYNC, new ScriptFileDbConverter("v276_create_request_logs_table",
            tableNotExists("request_logs")), true),
    v277(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v277_alter_request_logs_username_column",
            "request_logs", "username", dbSpecific(POSTGRESQL), tableExists("request_logs"),
            columnExists("request_logs", "username")), true),
    v278(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor("v278_alter_request_logs_request_path_column",
            "request_logs", "request_path", dbSpecific(POSTGRESQL), tableExists("request_logs"),
            columnExists("request_logs", "request_path")), true),
    v279(SEQ_SYNC, new ScriptFileDbConverter("v279_create_outbound_request_logs_table",
            tableNotExists("outbound_request_logs")), true),
    v280(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor(
            "v280_alter_outbound_request_logs_request_url_column",
            "outbound_request_logs", "request_url", dbSpecific(POSTGRESQL),
            tableExists("outbound_request_logs"),
            columnExists("outbound_request_logs", "request_url")), true),
    v281(SEQ_ASYNC, new IncreaseColumnStatisticsConvertor(
            "v281_alter_outbound_request_logs_repo_key_column",
            "outbound_request_logs", "repo_key", dbSpecific(POSTGRESQL),
            tableExists("outbound_request_logs"),
            columnExists("outbound_request_logs", "repo_key")), true),
    v282(SEQ_ASYNC, new SafeIndexConcurrentCreator(
            "request_logs_search_vector_idx",
            "request_logs",
            List.of("search_vector"),
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS request_logs_search_vector_idx "
                    + "ON request_logs USING GIN (search_vector)",
            dbSpecific(POSTGRESQL),
            tableExists("request_logs")), true),
    v283(SEQ_ASYNC, new SafeIndexConcurrentCreator(
            "outbound_rl_search_vector_idx",
            "outbound_request_logs",
            List.of("search_vector"),
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS outbound_rl_search_vector_idx "
                    + "ON outbound_request_logs USING GIN (search_vector)",
            dbSpecific(POSTGRESQL),
            tableExists("outbound_request_logs")), true),
    v284(SEQ_SYNC, new ScriptFileDbConverter("v284_add_repo_type_v2_column",
            tableExists("repository_config"), columnNotExists("repository_config", "repo_type_v2")), true),
    v285(SEQ_SYNC, new ScriptFileDbConverter("v285_create_system_states_table",
            tableNotExists("system_states")), true),
    // Oracle-only function-based index on LOWER(prop_key), LOWER(prop_value) for NuGet case-insensitive props.
    v286(SEQ_ASYNC, new ScriptFileDbConverter("v286_node_props_add_nuget_lower_idx",
            dbSpecific(ORACLE),
            oracleIndexNotExistsByName("node_prop_nuget_lower_idx")), true)
    ;

    @VisibleForTesting
    final ArtifactoryServiceVersionProvider applyFromVersion;
    private final ConverterExecutionType type;
    private final DbConverter converter;
    @Getter
    private final boolean backwardCompatible;

    /**
     * Constructor that should be used for all newly introduced DB converters (starting from v101)
     *
     * @param type               - converter type. Used to filter out and further process converters of a same type.
     * @param converter          - conditional converter containing actual work.
     * @param backwardCompatible - false if there are any breaking changes in converter
     *                           (deleting table, deleting column, etc.), true otherwise
     */
    ArtifactoryDBVersion(ConverterExecutionType type, DbConverter converter, boolean backwardCompatible) {
        this(type, converter, null, backwardCompatible);
    }

    /**
     * Constructor used for converters that were executed by the moment of new DB converters execution model
     * introducing (prior v101).
     *
     * @param type               - converter type. Used to filter out and further process converters of a same type.
     * @param converter          - conditional converter containing actual work.
     * @param artifactoryVersion - version of artifactory for which this converter is actual. If this prop is less than
     *                           current artifactory version, this converter will be skipped.
     * @param backwardCompatible - false if there are any breaking changes in converter
     *                           (deleting table, deleting column, etc.), ture otherwise
     */
    ArtifactoryDBVersion(ConverterExecutionType type, DbConverter converter,
            ArtifactoryServiceVersionProvider artifactoryVersion, boolean backwardCompatible) {
        this.type = type;
        this.converter = converter;
        this.applyFromVersion = artifactoryVersion;
        this.backwardCompatible = backwardCompatible;
    }

    public static DbVersion[] get(ConverterExecutionType... execTypes) {
        Set<ConverterExecutionType> filter = execTypes.length > 0 ?
                Stream.of(execTypes).collect(Collectors.toSet()) :
                Stream.of(ConverterExecutionType.values()).collect(Collectors.toSet());

        return Stream.of(ArtifactoryDBVersion.values())
                .filter(version -> filter.contains(version.type()))
                .sorted(Comparator.comparing(ArtifactoryDBVersion::ordinal))
                .toArray(DbVersion[]::new);
    }

    public static DbVersion[] getAlreadyApplied(CompoundVersionDetails sourceArtifactory,
            ConverterExecutionType... execTypes) {
        Set<ConverterExecutionType> filter = execTypes.length > 0 ?
                Stream.of(execTypes).collect(Collectors.toSet()) :
                Stream.of(ConverterExecutionType.values()).collect(Collectors.toSet());

        return Stream.of(ArtifactoryDBVersion.values())
                .filter(version -> filter.contains(version.type()))
                .filter(version -> version.isAlreadyApplied(sourceArtifactory))
                .sorted(Comparator.comparing(ArtifactoryDBVersion::ordinal))
                .toArray(DbVersion[]::new);
    }

    public String version() {
        return this.name();
    }

    public String description() {
        return converter.description();
    }

    public ConverterExecutionType type() {
        return this.type;
    }

    public DbConverter converter() {
        return this.converter;
    }

    public boolean isAlreadyApplied(CompoundVersionDetails sourceArtifactory) {
        return applyFromVersion != null && sourceArtifactory.getVersion().after(applyFromVersion.get());
    }

    public String systemVersion() {
        return Optional.ofNullable(applyFromVersion)
                .map(ArtifactoryServiceVersionProvider::get)
                .map(ArtifactoryServiceVersion::getVersion)
                .orElse("");
    }

    public static List<ArtifactoryDBVersion> getByVersions(List<String> versions) {
        return Arrays.stream(ArtifactoryDBVersion.values())
                .filter(dbVersion -> versions.contains(dbVersion.version()))
                .toList();
    }

    /**
     * Condition that runs the converter only when running on Artifactory Online (SaaS).
     * Use with SEQ_ASYNC converters only; sync converters run before context is up so AolUtils.isAol() is not available
     * Returns false if context is not ready or any exception occurs (e.g. NPE when used from SEQ_SYNC).
     */
    @VisibleForTesting
    static BiPredicate<Connection, DbType> aolOnly() {
        return (connection, dbType) -> {
            try {
                boolean aol = AolUtils.isAol();
                log.info("aolOnly condition evaluated as {}", aol);
                return aol;
            } catch (Exception e) {
                log.info("aolOnly condition failed, treating as false", e);
                return false;
            }
        };
    }

}