package org.artifactory.storage.db.policy.dao;

import com.google.common.collect.Sets;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.policy.PolicyType;
import org.artifactory.storage.db.policy.PackageRunSummaryConfig;
import org.artifactory.storage.db.policy.PolicyImpactRow;
import org.artifactory.storage.db.policy.RestoreRunSummaryConfig;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.RunSummaryConfig;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyViewRun;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.SqlDaoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.sql.Statement.RETURN_GENERATED_KEYS;
import static org.artifactory.common.ConstantValues.dbOperationsBulkSize;
import static org.artifactory.storage.db.policy.RetentionRunSummary.Status.COMPLETED;
import static org.artifactory.storage.db.policy.RetentionRunSummary.Status.COMPLETED_WITH_ERRORS;
import static org.artifactory.storage.db.policy.RetentionRunSummary.Status.STOPPED;

/**
 * @author punyap
 */

@Slf4j
@Repository
public class RetentionRunSummaryDao {

    private static final String RETENTION_RUN_SUMMARY_TABLE = "retention_run_summary";
    private static final String POLICY_KEY_COLUMN = "policy_key";
    private static final String RUN_ID_COLUMN = "run_id";
    private static final String RETENTION_RUN_SUMMARY_ID_COLUMN = "id";
    private static final String DRY_RUN_COLUMN = "dry_run";
    private static final String STARTED_DATE_COLUMN = "started_date";
    private static final String FINISHED_DATE_COLUMN = "finished_date";
    private static final String STATUS_COLUMN = "status";
    private static final String RETENTION_ITEM_TYPE_COLUMN = "retention_type";
    private static final String ITEM_TYPE_COLUMN = "item_type";
    private static final String PROCESSED_ITEMS_COUNT_COLUMN = "processed_items_count";
    private static final String FAILED_ITEMS_COUNT_COLUMN = "failed_items_count";
    private static final String PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN = "processed_physical_binary_size";
    private static final String PROCESSED_VIRTUAL_BINARY_SIZE_COLUMN = "processed_virtual_binary_size";
    private static final String TOTAL_BINARY_SIZE_COLUMN = "total_binary_size";
    private static final String TRIGGERED_BY_COLUMN = "triggered_by";
    private static final String TOTAL_ITEMS_VIEW = "totalItems";
    private static final String TOTAL_ITEMS_COUNT_COLUMN = "total_items_count";
    private static final String TEMP_NODES_CLEANED_COLUMN = "temp_nodes_cleaned";
    private static final String STARTED_DATE_COLUM = "started_date";
    private static final String CLEANED_ITEMS_VIEW = "cleanedItems";
    private static final String CLEANED_SIZE_VIEW = "cleanedSize";
    private static final String SCHEDULER_USERNAME = "scheduler";
    private static final String RETENTION_RUN_DEFAULT_PAGINATION_ORDER = " DESC";
    private static final String RUN_CONFIG_COLUMN = "run_config";
    private static final String MAX_RUN_ID_COLUMN = "MAX(run_id)";
    private static final String AND = " AND ";
    private static final String IN_CONDITION = " IN (#) ";
    private static final String RUN_ID_IN_CONDITION = RUN_ID_COLUMN + IN_CONDITION;
    private static final String POLICY_KEY_IN_CONDITION = POLICY_KEY_COLUMN + IN_CONDITION;
    private static final String MAX_RUN_ID_ALIAS = "pr";
    private static final String JOINED_COLUMNS = String.join(",",
            POLICY_KEY_COLUMN,
            RUN_ID_COLUMN,
            DRY_RUN_COLUMN,
            STARTED_DATE_COLUMN,
            FINISHED_DATE_COLUMN,
            RETENTION_ITEM_TYPE_COLUMN,
            STATUS_COLUMN,
            ITEM_TYPE_COLUMN,
            PROCESSED_ITEMS_COUNT_COLUMN,
            FAILED_ITEMS_COUNT_COLUMN,
            TOTAL_ITEMS_COUNT_COLUMN,
            PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN,
            PROCESSED_VIRTUAL_BINARY_SIZE_COLUMN,
            TRIGGERED_BY_COLUMN,
            TOTAL_BINARY_SIZE_COLUMN,
            TEMP_NODES_CLEANED_COLUMN,
            RUN_CONFIG_COLUMN
    );
    private static final String SELECT_JOINED_COLUMNS = String.join(",",
            RETENTION_RUN_SUMMARY_ID_COLUMN, JOINED_COLUMNS);
    private static final String INSERT =
            "INSERT INTO " + RETENTION_RUN_SUMMARY_TABLE +
                    " (" + JOINED_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_ID =
            "SELECT " + SELECT_JOINED_COLUMNS +
                    " FROM " + RETENTION_RUN_SUMMARY_TABLE +
                    " WHERE " +
                    RETENTION_RUN_SUMMARY_ID_COLUMN + "=?";

    private static final String SELECT =
            "SELECT " + SELECT_JOINED_COLUMNS +
                    " FROM " + RETENTION_RUN_SUMMARY_TABLE +
                    " WHERE " +
                    POLICY_KEY_COLUMN + "=? " +
                    "AND " +
                    RUN_ID_COLUMN + "=?";

    private static final String UPDATE =
            "UPDATE " + RETENTION_RUN_SUMMARY_TABLE + " SET " +
                    STARTED_DATE_COLUMN + "=?, " +
                    FINISHED_DATE_COLUMN + "=?, " +
                    RETENTION_ITEM_TYPE_COLUMN + "=?, " +
                    STATUS_COLUMN + "=?, " +
                    ITEM_TYPE_COLUMN + "=?, " +
                    PROCESSED_ITEMS_COUNT_COLUMN + "=?, " +
                    FAILED_ITEMS_COUNT_COLUMN + "=?, " +
                    TOTAL_ITEMS_COUNT_COLUMN + "=?, " +
                    PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN + "=?, " +
                    PROCESSED_VIRTUAL_BINARY_SIZE_COLUMN + "=?, " +
                    TRIGGERED_BY_COLUMN + "=?, " +
                    TOTAL_BINARY_SIZE_COLUMN + "=? " +
                    "WHERE " +
                    POLICY_KEY_COLUMN + "=? " +
                    "AND " +
                    RUN_ID_COLUMN + "=? ";

    private static final String UPDATE_BY_ID =
            "UPDATE " + RETENTION_RUN_SUMMARY_TABLE + " SET " +
                    POLICY_KEY_COLUMN + "=?, " +
                    RUN_ID_COLUMN + "=?, " +
                    STARTED_DATE_COLUMN + "=?, " +
                    FINISHED_DATE_COLUMN + "=?, " +
                    RETENTION_ITEM_TYPE_COLUMN + "=?, " +
                    STATUS_COLUMN + "=?, " +
                    ITEM_TYPE_COLUMN + "=?, " +
                    PROCESSED_ITEMS_COUNT_COLUMN + "=?, " +
                    FAILED_ITEMS_COUNT_COLUMN + "=?, " +
                    TOTAL_ITEMS_COUNT_COLUMN + "=?, " +
                    PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN + "=?, " +
                    PROCESSED_VIRTUAL_BINARY_SIZE_COLUMN + "=?, " +
                    TRIGGERED_BY_COLUMN + "=?, " +
                    TOTAL_BINARY_SIZE_COLUMN + "=? " +
                    "WHERE " +
                    RETENTION_RUN_SUMMARY_ID_COLUMN + "=? ";

    private static final String BYTES_PROCESSED_ALIAS = "bytes_processed";
    private static final String BYTES_PROCESSED_EXPR =
            "SUM(" + PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN + ")";
    private static final String FINISHED_STATUSES_CLAUSE =
            STATUS_COLUMN + " IN ('" + COMPLETED.getName() + "','" + COMPLETED_WITH_ERRORS.getName()
                    + "','" + STOPPED.getName() + "')";

    private static final String SELECT_SMART_ARCHIVE_CONSUMPTION_RUNS =
            "SELECT " + SELECT_JOINED_COLUMNS + " FROM " + RETENTION_RUN_SUMMARY_TABLE +
                    " WHERE " + STARTED_DATE_COLUMN + " >= ? AND " + STARTED_DATE_COLUMN + " <= ? AND " +
                    DRY_RUN_COLUMN + " = 0 AND " +
                    RETENTION_ITEM_TYPE_COLUMN + " IN ('archive','restore') AND " +
                    STATUS_COLUMN + " IN ('completed','stopped') " +
                    "ORDER BY " + STARTED_DATE_COLUMN + " ASC";

    private static final String SELECT_COLUMNS_FOR_POLICY_VIEW_RUN = String.join(",",
            RETENTION_RUN_SUMMARY_ID_COLUMN, POLICY_KEY_COLUMN, ITEM_TYPE_COLUMN, STARTED_DATE_COLUM, STATUS_COLUMN,
            DRY_RUN_COLUMN, PROCESSED_ITEMS_COUNT_COLUMN, FAILED_ITEMS_COUNT_COLUMN, TOTAL_ITEMS_COUNT_COLUMN);

    private final JdbcHelper jdbcHelper;
    private final SqlDaoHelper<RetentionRunSummary> daoHelper;

    @Autowired
    public RetentionRunSummaryDao(JdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.jdbcHelper = jdbcHelper;
        // daoHelper is only used in tests
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                RETENTION_RUN_SUMMARY_TABLE, this::toRetentionRunSummary, dbOperationsBulkSize.getInt());
    }

    public int insert(RetentionRunSummary retentionRunSummary) throws SQLException {
        String runConfig = JsonUtils.getInstance().valueToString(retentionRunSummary.runConfig());
        byte[] runConfigBytes = runConfig.getBytes();

        try (Connection connection = jdbcHelper.getDataSource()
                .getConnection(); PreparedStatement preparedStatement = getGenKeysPreparedStmtByDb(connection)) {
            preparedStatement.setString(RetentionRunSummaryColumns.POLICY_KEY.getIndex(),
                    retentionRunSummary.policyKey());
            preparedStatement.setInt(RetentionRunSummaryColumns.RUN_ID.getIndex(), retentionRunSummary.runId());
            preparedStatement.setInt(RetentionRunSummaryColumns.DRY_RUN.getIndex(),
                    retentionRunSummary.dryRun() ? 1 : 0);
            preparedStatement.setLong(RetentionRunSummaryColumns.STARTED_DATE.getIndex(),
                    retentionRunSummary.startedDate());
            preparedStatement.setLong(RetentionRunSummaryColumns.FINISHED_DATE.getIndex(),
                    retentionRunSummary.finishedDate());
            preparedStatement.setString(RetentionRunSummaryColumns.RETENTION_ITEM_TYPE.getIndex(),
                    retentionRunSummary.retentionType().getName());
            preparedStatement.setString(RetentionRunSummaryColumns.STATUS.getIndex(),
                    retentionRunSummary.status().getName());
            preparedStatement.setString(RetentionRunSummaryColumns.ITEM_TYPE.getIndex(),
                    retentionRunSummary.itemType().getName());
            preparedStatement.setLong(RetentionRunSummaryColumns.PROCESSED_ITEMS_COUNT.getIndex(),
                    retentionRunSummary.processedItemsCount());
            preparedStatement.setLong(RetentionRunSummaryColumns.FAILED_ITEMS_COUNT.getIndex(),
                    retentionRunSummary.failedItemsCount());
            preparedStatement.setLong(RetentionRunSummaryColumns.TOTAL_ITEMS_COUNT.getIndex(),
                    retentionRunSummary.totalItemsCount());
            preparedStatement.setLong(RetentionRunSummaryColumns.PROCESSED_PHYSICAL_BINARY_SIZE.getIndex(),
                    retentionRunSummary.processedPhysicalBinarySize());
            preparedStatement.setLong(RetentionRunSummaryColumns.PROCESSED_VIRTUAL_BINARY_SIZE.getIndex(),
                    retentionRunSummary.processedVirtualBinarySize());
            preparedStatement.setString(RetentionRunSummaryColumns.TRIGGERED_BY.getIndex(),
                    retentionRunSummary.triggeredBy());
            preparedStatement.setLong(RetentionRunSummaryColumns.TOTAL_BINARY_SIZE.getIndex(),
                    retentionRunSummary.totalBinarySize());
            preparedStatement.setInt(RetentionRunSummaryColumns.TEMP_NODES_CLEANED.getIndex(),
                    retentionRunSummary.tempNodesCleaned() ? 1 : 0);
            preparedStatement.setBytes(RetentionRunSummaryColumns.RUN_CONFIG.getIndex(), runConfigBytes);

            preparedStatement.executeUpdate();

            try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Inserting retention run summary failed, no ID obtained.");
                }
            }
        }
    }

    private static PreparedStatement getGenKeysPreparedStmtByDb(Connection conn) throws SQLException {
        if (ArtifactoryHome.get().getDBProperties().getDbType() == DbType.ORACLE) {
            return conn.prepareStatement(RetentionRunSummaryDao.INSERT, new String[] {RETENTION_RUN_SUMMARY_ID_COLUMN});
        }
        return conn.prepareStatement(RetentionRunSummaryDao.INSERT, RETURN_GENERATED_KEYS);
    }

    public RetentionRunSummary get(int id) throws SQLException {
        RetentionRunSummary summary = null;
        try (ResultSet resultSet = jdbcHelper.executeSelect(SELECT_BY_ID, id)) {
            if (resultSet.next()) {
                summary = toRetentionRunSummary(resultSet);
            }
            return summary;
        }
    }

    public RetentionRunSummary get(String policyKey, int runId) throws SQLException {
        RetentionRunSummary summary = null;
        try (ResultSet resultSet = jdbcHelper.executeSelect(
                SELECT, policyKey, runId)) {
            if (resultSet.next()) {
                summary = toRetentionRunSummary(resultSet);
            }
            return summary;
        }
    }

    public int update(RetentionRunSummary summary) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE,
                summary.startedDate(),
                summary.finishedDate(),
                summary.retentionType().getName(),
                summary.status().getName(),
                summary.itemType().getName(),
                summary.processedItemsCount(),
                summary.failedItemsCount(),
                summary.totalItemsCount(),
                summary.processedPhysicalBinarySize(),
                summary.processedVirtualBinarySize(),
                summary.triggeredBy(),
                summary.totalBinarySize(),
                // WHERE
                summary.policyKey(),
                summary.runId()
        );
    }

    public int updateById(RetentionRunSummary summary) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE_BY_ID,
                summary.policyKey(),
                summary.runId(),
                summary.startedDate(),
                summary.finishedDate(),
                summary.retentionType().getName(),
                summary.status().getName(),
                summary.itemType().getName(),
                summary.processedItemsCount(),
                summary.failedItemsCount(),
                summary.totalItemsCount(),
                summary.processedPhysicalBinarySize(),
                summary.processedVirtualBinarySize(),
                summary.triggeredBy(),
                summary.totalBinarySize(),
                // WHERE
                Long.parseLong(summary.id())
        );
    }

    // deleteAll is only used in tests
    public int deleteAll() throws SQLException {
        return daoHelper.deleteAll();
    }

    // getAll is only used in tests
    public List<RetentionRunSummary> getAll() throws SQLException {
        return daoHelper.getAll();
    }

    /**
     * Completed archive/restore package policy runs in [{@code fromMillis}, {@code toMillis}] (by started_date),
     * for smart archiving consumption reporting.
     */
    public List<RetentionRunSummary> getConsumptionRunsForDateRange(long fromMillis, long toMillis)
            throws SQLException {
        List<RetentionRunSummary> list = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(
                SELECT_SMART_ARCHIVE_CONSUMPTION_RUNS, fromMillis, toMillis)) {
            while (resultSet.next()) {
                list.add(toRetentionRunSummary(resultSet));
            }
        }
        return list;
    }

    /**
     * Get completed non-dry runs by timestamp and policy type
     *
     * @throws SQLException internal SQL exception
     */
    @Nonnull
    public Set<RetentionRunSummary> getFinishedRunSummaries(@NonNull Set<String> itemTypes, long startTime,
            long endTime, PolicyType policyType)
            throws SQLException {
        String selectQuery = new ArtifactoryQueryWriter()
                .select(SELECT_JOINED_COLUMNS)
                .from(RETENTION_RUN_SUMMARY_TABLE)
                .where(RETENTION_ITEM_TYPE_COLUMN + " = ? AND " +
                        FINISHED_DATE_COLUMN + " > ? AND " +
                        FINISHED_DATE_COLUMN + " != 0 AND " +
                        FINISHED_DATE_COLUMN + " < ? AND " +
                        ITEM_TYPE_COLUMN + " IN (#) AND " +
                        DRY_RUN_COLUMN + " = 0")
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(selectQuery, policyType.getCode(),
                startTime, endTime, itemTypes)) {
            Set<RetentionRunSummary> records = new HashSet<>();
            while (resultSet.next()) {
                records.add(toRetentionRunSummary(resultSet));
            }
            return records;
        }
    }

    /**
     * Get completed non-dry runs for internal retention type by timestamp
     *
     * @throws SQLException internal SQL exception
     */
    public Set<CleanupRunPolicyKeyAndRunId> getRunsForInternalCleanup(long timestamp, PolicyType policyType)
            throws SQLException {
        String selectQuery = new ArtifactoryQueryWriter()
                .select(String.join(",", POLICY_KEY_COLUMN, RUN_ID_COLUMN))
                .from(RETENTION_RUN_SUMMARY_TABLE)
                .where(RETENTION_ITEM_TYPE_COLUMN + " = ? AND " +
                        FINISHED_DATE_COLUMN + " <? AND " +
                        FINISHED_DATE_COLUMN + " !=0 AND " +
                        TEMP_NODES_CLEANED_COLUMN + " =0")
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(selectQuery, policyType.getCode(), timestamp)) {
            Set<CleanupRunPolicyKeyAndRunId> records = Sets.newHashSet();
            while (resultSet.next()) {
                records.add(new CleanupRunPolicyKeyAndRunId(
                        resultSet.getString(POLICY_KEY_COLUMN),
                        resultSet.getInt(RUN_ID_COLUMN)));
            }
            return records;
        }
    }

    public boolean markAsCleanedInternally(String policyKey, int policyRunId,
            RetentionRunSummary.RetentionType retentionType) throws SQLException {
        int successful = jdbcHelper.executeUpdate("UPDATE " + RETENTION_RUN_SUMMARY_TABLE + " SET " +
                TEMP_NODES_CLEANED_COLUMN + "=? " +
                "WHERE " +
                RETENTION_ITEM_TYPE_COLUMN + "=? AND " +
                POLICY_KEY_COLUMN + "=? AND " +
                RUN_ID_COLUMN + "=? ", 1, retentionType.getName(), policyKey, policyRunId);
        return successful > 0;
    }


    public void markAsFinished(String policyKey, int runId, long timestamp) throws SQLException {
        jdbcHelper.executeUpdate("UPDATE " + RETENTION_RUN_SUMMARY_TABLE + " SET " +
                FINISHED_DATE_COLUMN + "=? " +
                "WHERE " +
                POLICY_KEY_COLUMN + "=? " +
                "AND " +
                RUN_ID_COLUMN + "=? ", timestamp, policyKey, runId);
    }

    /**
     * Returns per-policy aggregated storage impact (sum of processed_physical_binary_size) for all
     * non-dry completed/stopped runs whose started_date falls within the given lookback window.
     *
     * @param fromMillis     epoch-millis start of the lookback window (inclusive)
     * @param retentionTypes optional set of retention type codes to include (e.g. "cleanup", "archive");
     *                       {@code null} or empty means all types
     * @param policyKeys     optional set of policy keys to restrict results to (project-scope filter);
     *                       {@code null} or empty means all policies
     * @param minBytes       optional inclusive lower bound on per-policy summed bytes; {@code null} = no bound
     * @param maxBytes       optional inclusive upper bound on per-policy summed bytes; {@code null} = no bound;
     *                       pass {@code 0L} to surface zero-yield policies only
     * @param sortAsc        {@code true} for ascending order, {@code false} for descending
     * @param limit          optional maximum number of rows to return; applied in Java after DB fetch
     * @return list of {@link PolicyImpactRow} ordered by {@code bytes_processed}
     */
    public List<PolicyImpactRow> getStorageImpact(long fromMillis, @Nullable Set<String> retentionTypes,
            @Nullable Set<String> policyKeys, @Nullable Long minBytes, @Nullable Long maxBytes,
            boolean sortAsc, @Nullable Integer limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT " + POLICY_KEY_COLUMN + ", " + RETENTION_ITEM_TYPE_COLUMN + ", "
                        + BYTES_PROCESSED_EXPR + " AS " + BYTES_PROCESSED_ALIAS
                        + " FROM " + RETENTION_RUN_SUMMARY_TABLE
                        + " WHERE " + STARTED_DATE_COLUMN + " >= ?"
                        + AND + DRY_RUN_COLUMN + " = 0"
                        + AND + FINISHED_STATUSES_CLAUSE
        );
        params.add(fromMillis);
        if (retentionTypes != null && !retentionTypes.isEmpty()) {
            sql.append(AND).append(RETENTION_ITEM_TYPE_COLUMN).append(IN_CONDITION);
            params.add(retentionTypes);
        }
        if (policyKeys != null && !policyKeys.isEmpty()) {
            sql.append(AND).append(POLICY_KEY_COLUMN).append(IN_CONDITION);
            params.add(policyKeys);
        }
        sql.append(" GROUP BY ").append(POLICY_KEY_COLUMN).append(", ").append(RETENTION_ITEM_TYPE_COLUMN);
        boolean hasHaving = false;
        if (minBytes != null) {
            sql.append(" HAVING ").append(BYTES_PROCESSED_EXPR).append(" >= ?");
            params.add(minBytes);
            hasHaving = true;
        }
        if (maxBytes != null) {
            sql.append(hasHaving ? AND : " HAVING ").append(BYTES_PROCESSED_EXPR).append(" <= ?");
            params.add(maxBytes);
        }
        sql.append(" ORDER BY ").append(BYTES_PROCESSED_ALIAS)
                .append(sortAsc ? " " + SortOrder.ASC.name() : RETENTION_RUN_DEFAULT_PAGINATION_ORDER);
        List<PolicyImpactRow> rows = new ArrayList<>();
        try (ResultSet rs = jdbcHelper.executeSelect(sql.toString(), params.toArray())) {
            while (rs.next()) {
                rows.add(new PolicyImpactRow(
                        rs.getString(POLICY_KEY_COLUMN),
                        rs.getString(RETENTION_ITEM_TYPE_COLUMN),
                        rs.getLong(BYTES_PROCESSED_ALIAS)
                ));
                if (limit != null && rows.size() >= limit) {
                    break;
                }
            }
        }
        return rows;
    }

    public Set<CleanupPolicyRun> getPolicyRuns(PolicyRunInput policyRunInput, boolean withDate) throws SQLException {

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        String updatedExcludeBlankExecutions = validateExcludeBlankExecutions(policyRunInput.excludeBlankExecutions());

        PolicyRunWhereConditionInputs whereInputs = PolicyRunWhereConditionInputs.builder()
                .itemTypes(policyRunInput.itemTypes())
                .policyKeys(policyRunInput.policyKeys())
                .policyKey(policyRunInput.policyKey())
                .status(policyRunInput.status())
                .excludeBlankExecutions(updatedExcludeBlankExecutions)
                .params(params)
                .where(where)
                .policyType(policyRunInput.policyType())
                .runIds(policyRunInput.runIds())
                .build();

        if (withDate) {
            updateWhereClause(policyRunInput.from(), policyRunInput.to(), whereInputs);
        } else {
            updateWhereClause(whereInputs);
        }


        String updatedSortBy = validateSortBy(policyRunInput.sortBy());
        String updatedSortOrder = validateSortOrder(policyRunInput.sortOrder());

        String select = String.join(",",
                Set.of(RETENTION_RUN_SUMMARY_ID_COLUMN, RUN_ID_COLUMN, ITEM_TYPE_COLUMN, POLICY_KEY_COLUMN,
                        STARTED_DATE_COLUMN, FINISHED_DATE_COLUMN, STATUS_COLUMN, TRIGGERED_BY_COLUMN,
                        DRY_RUN_COLUMN, RUN_CONFIG_COLUMN,
                        RETENTION_ITEM_TYPE_COLUMN)) + "," + getSelectTotalAndCleanedItems() + "," +
                PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN + " AS cleanedSize";

        Long offset = (policyRunInput.pageSize() * policyRunInput.page()) - policyRunInput.pageSize();
        String query = new ArtifactoryQueryWriter()
                .select(select)
                .from(RETENTION_RUN_SUMMARY_TABLE)
                .where(where.toString())
                .limit(policyRunInput.pageSize())
                .offset(offset)
                .orderBy(updatedSortBy + updatedSortOrder)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, params.toArray())) {
            Set<CleanupPolicyRun> records = Sets.newLinkedHashSet();
            while (resultSet.next()) {
                records.add(toRunRecord(resultSet));
            }
            return records;
        }
    }

    public long getPolicyRunsCount(Set<String> itemTypes, long from, long to,
            @Nullable String policyKey, @Nullable Set<String> policyKeys,
            @Nullable String excludeBlankExecutions, @Nullable Set<String> status,
            @NotNull PolicyType policyType) throws SQLException {
        PolicyRunWhereCondition condition = getPolicyRunsWhereCondition(
                itemTypes, from, to, policyKey, policyKeys, excludeBlankExecutions, status, policyType);
        String query = "SELECT COUNT(*) as count from " + RETENTION_RUN_SUMMARY_TABLE + " where " + condition.condition;
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, condition.params.toArray())) {
            resultSet.next();
            return resultSet.getInt("count");
        }
    }

    public long getPolicyRunsCount(Set<String> itemTypes,
            @Nullable String policyKey, @Nullable Set<String> policyKeys,
            @Nullable String excludeBlankExecutions, @Nullable Set<String> status,
            @NotNull PolicyType policyType) throws SQLException {
        PolicyRunWhereCondition condition = getPolicyRunsWhereCondition(
                itemTypes, policyKey, policyKeys, excludeBlankExecutions, status, policyType);
        String query = "SELECT COUNT(*) as count from " + RETENTION_RUN_SUMMARY_TABLE + " where " + condition.condition;
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, condition.params.toArray())) {
            resultSet.next();
            return resultSet.getInt("count");
        }
    }


    public Set<RetentionRunSummary> findByStatuses(@NonNull PolicyType policyType,
            @NonNull Set<String> statuses) throws SQLException {
        String selectQuery = new ArtifactoryQueryWriter()
                .select(SELECT_JOINED_COLUMNS)
                .from(RETENTION_RUN_SUMMARY_TABLE)
                .where(RETENTION_ITEM_TYPE_COLUMN + " = ? AND " + STATUS_COLUMN + " IN (#)")
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(selectQuery, policyType.getCode(), statuses)) {
            Set<RetentionRunSummary> records = Sets.newLinkedHashSet();
            while (resultSet.next()) {
                records.add(toRetentionRunSummary(resultSet));
            }
            return records;
        }
    }

    /**
     * Returns all non-dry runs for the given policy keys and retention type, ordered by policy_key ASC, run_id DESC.
     * Used for consecutive failure detection: caller walks each policy's runs from newest to oldest.
     *
     * @param policyKeys    set of policy keys to include; if empty, returns an empty list
     * @param retentionType the retention type (CLEANUP or ARCHIVE)
     * @return list of run summaries sorted per policy by descending run_id
     */
    public List<RetentionRunSummary> getRunsBatchForPolicies(Set<String> policyKeys,
            RetentionRunSummary.RetentionType retentionType) throws SQLException {
        if (CollectionUtils.isNullOrEmpty(policyKeys)) {
            return Collections.emptyList();
        }
        String query = new ArtifactoryQueryWriter()
                .select(SELECT_JOINED_COLUMNS)
                .from(RETENTION_RUN_SUMMARY_TABLE)
                .where(RETENTION_ITEM_TYPE_COLUMN + " = ? AND " + DRY_RUN_COLUMN + " = 0 AND "
                        + POLICY_KEY_IN_CONDITION)
                .orderBy(POLICY_KEY_COLUMN + " ASC, " + RUN_ID_COLUMN + RETENTION_RUN_DEFAULT_PAGINATION_ORDER)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, retentionType.getName(), policyKeys)) {
            List<RetentionRunSummary> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(toRetentionRunSummary(resultSet));
            }
            return records;
        }
    }

    /**
     * Returns policies last run data filtered by policy keys.
     */
    public List<CleanupPolicyViewRun> getPolices(List<String> policyKeys,
            RetentionRunSummary.RetentionType retentionType) throws SQLException {
        if (CollectionUtils.isNullOrEmpty(policyKeys)) {
            return Collections.emptyList();
        }
        Set<Integer> runIds = getLastRunIds(policyKeys);
        if (CollectionUtils.isNullOrEmpty(runIds)) {
            return Collections.emptyList();
        }
        String select = new ArtifactoryQueryWriter()
                .select(SELECT_COLUMNS_FOR_POLICY_VIEW_RUN)
                .from(RETENTION_RUN_SUMMARY_TABLE)
                .where(RETENTION_ITEM_TYPE_COLUMN + " = ? AND " + RUN_ID_IN_CONDITION)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(select, retentionType.getName(), runIds)) {
            List<CleanupPolicyViewRun> records = Lists.newArrayList();
            while (resultSet.next()) {
                records.add(toRecord(resultSet));
            }
            return records;
        }
    }

    private Set<Integer> getLastRunIds(List<String> policyKeys) throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select(MAX_RUN_ID_COLUMN + " as " + MAX_RUN_ID_ALIAS)
                .from(RETENTION_RUN_SUMMARY_TABLE)
                .where(POLICY_KEY_IN_CONDITION)
                .groupBy(POLICY_KEY_COLUMN)
                .orderBy(MAX_RUN_ID_COLUMN)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, policyKeys)) {
            Set<Integer> records = org.glassfish.jersey.internal.guava.Sets.newHashSet();
            while (resultSet.next()) {
                records.add(resultSet.getInt(MAX_RUN_ID_ALIAS));
            }
            return records;
        }
    }

    private CleanupPolicyViewRun toRecord(ResultSet resultSet) throws SQLException {
        long totalItems = 0;
        if (resultSet.getString(ITEM_TYPE_COLUMN).equals("package")) {
            totalItems = resultSet.getLong(TOTAL_ITEMS_COUNT_COLUMN);
        }
        return new CleanupPolicyViewRun(resultSet.getInt(RETENTION_RUN_SUMMARY_ID_COLUMN),
                resultSet.getString(POLICY_KEY_COLUMN),
                resultSet.getString(ITEM_TYPE_COLUMN),
                resultSet.getLong(STARTED_DATE_COLUM),
                resultSet.getBoolean(DRY_RUN_COLUMN),
                resultSet.getString(STATUS_COLUMN),
                totalItems);
    }

    private PolicyRunWhereCondition getPolicyRunsWhereCondition(Set<String> itemTypes, long from, long to,
            @Nullable String policyKey, @Nullable Set<String> policyKeys, @Nullable String excludeBlankExecutions,
            @Nullable Set<String> status, @NotNull PolicyType policyType) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        excludeBlankExecutions = validateExcludeBlankExecutions(excludeBlankExecutions);
        PolicyRunWhereConditionInputs whereInputs = PolicyRunWhereConditionInputs.builder()
                .itemTypes(itemTypes)
                .policyKeys(policyKeys)
                .policyKey(policyKey)
                .status(status)
                .excludeBlankExecutions(excludeBlankExecutions)
                .params(params)
                .where(where)
                .policyType(policyType)
                .runIds(null)
                .build();
        updateWhereClause(from, to, whereInputs);
        return new PolicyRunWhereCondition(where.toString(), params);
    }

    private PolicyRunWhereCondition getPolicyRunsWhereCondition(Set<String> itemTypes,
            @Nullable String policyKey, @Nullable Set<String> policyKeys, @Nullable String excludeBlankExecutions,
            @Nullable Set<String> status, @NotNull PolicyType policyType) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        excludeBlankExecutions = validateExcludeBlankExecutions(excludeBlankExecutions);
        PolicyRunWhereConditionInputs whereInputs = PolicyRunWhereConditionInputs.builder()
                .itemTypes(itemTypes)
                .policyKeys(policyKeys)
                .policyKey(policyKey)
                .status(status)
                .excludeBlankExecutions(excludeBlankExecutions)
                .params(params)
                .where(where)
                .policyType(policyType)
                .runIds(null)
                .build();
        updateWhereClause(whereInputs);

        return new PolicyRunWhereCondition(where.toString(), params);
    }

    private void updateWhereClause(PolicyRunWhereConditionInputs whereInputs) {

        whereInputs.where.append(RETENTION_ITEM_TYPE_COLUMN + " = ? AND ");
        whereInputs.params.add(whereInputs.policyType.getCode());
        whereInputs.where.append(ITEM_TYPE_COLUMN + " IN (#)")
                .append(whereInputs.excludeBlankExecutions);
        whereInputs.params.add(whereInputs.itemTypes);
        if (whereInputs.policyKeys != null && !whereInputs.policyKeys.isEmpty()) {
            whereInputs.where.append(AND + POLICY_KEY_COLUMN + IN_CONDITION);
            whereInputs.params.add(whereInputs.policyKeys);
        } else if (whereInputs.policyKey != null) {
            whereInputs.where.append(AND + POLICY_KEY_COLUMN + " LIKE ? ");
            whereInputs.params.add("%" + whereInputs.policyKey + "%");
        }
        if (whereInputs.status != null && !whereInputs.status.isEmpty()) {
            whereInputs.where.append(AND + STATUS_COLUMN + IN_CONDITION);
            whereInputs.params.add(whereInputs.status);
        }
        if (whereInputs.runIds != null && !whereInputs.runIds.isEmpty()) {
            whereInputs.where.append(AND + RETENTION_RUN_SUMMARY_ID_COLUMN + IN_CONDITION);
            whereInputs.params.add(whereInputs.runIds);
        }
    }


    private void updateWhereClause(long from, long to, PolicyRunWhereConditionInputs whereInputs) {
        updateWhereClause(whereInputs);
        whereInputs.where.append(AND + STARTED_DATE_COLUMN + " >= ?" + AND + FINISHED_DATE_COLUMN + " <= ? ");
        whereInputs.params.add(from);
        whereInputs.params.add(to);
    }


    private CleanupPolicyRun toRunRecord(ResultSet resultSet) throws SQLException {
        String triggeredBy = resultSet.getString(TRIGGERED_BY_COLUMN);
        boolean manual = !triggeredBy.equals(SCHEDULER_USERNAME);
        return CleanupPolicyRun.builder()
                .runId(resultSet.getInt(RETENTION_RUN_SUMMARY_ID_COLUMN))
                .policyTaskId(resultSet.getInt(RUN_ID_COLUMN))
                .taskType(resultSet.getString(ITEM_TYPE_COLUMN))
                .policyKey(resultSet.getString(POLICY_KEY_COLUMN))
                .startTime(resultSet.getLong(STARTED_DATE_COLUMN))
                .endTime(resultSet.getLong(FINISHED_DATE_COLUMN))
                .totalItems(resultSet.getLong(TOTAL_ITEMS_VIEW))
                .cleanedItems(resultSet.getLong(CLEANED_ITEMS_VIEW))
                .cleanedSize(resultSet.getLong(CLEANED_SIZE_VIEW))
                .status(resultSet.getString(STATUS_COLUMN))
                .triggeredBy(triggeredBy)
                .manual(manual)
                .dryRun(resultSet.getBoolean(DRY_RUN_COLUMN))
                .runSummaryConfig(toRunConfig(resultSet))
                .build();
    }

    private String validateSortBy(String sortBy) {
        if (sortBy == null) {
            return RUN_ID_COLUMN;
        }
        try {
            return SortBy.fromKey(sortBy);
        } catch (Exception e) {
            log.warn("Column: {} is not a valid sort by column", sortBy);
            return RUN_ID_COLUMN;
        }
    }

    private String validateSortOrder(String sortOrder) {
        if (sortOrder == null) {
            return RETENTION_RUN_DEFAULT_PAGINATION_ORDER;
        }
        try {
            SortOrder order = SortOrder.valueOf(sortOrder.toUpperCase());
            return " " + order.name();
        } catch (IllegalArgumentException e) {
            log.warn("Sort order: {} is not a valid sort order", sortOrder);
            return RETENTION_RUN_DEFAULT_PAGINATION_ORDER;
        }
    }

    private RetentionRunSummary toRetentionRunSummary(ResultSet resultSet) throws SQLException {
        return RetentionRunSummary.builder()
                .id(resultSet.getString(RETENTION_RUN_SUMMARY_ID_COLUMN))
                .policyKey(resultSet.getString(POLICY_KEY_COLUMN))
                .runId(resultSet.getInt(RUN_ID_COLUMN))
                .dryRun(resultSet.getInt(DRY_RUN_COLUMN) != 0)
                .startedDate(resultSet.getLong(STARTED_DATE_COLUMN))
                .finishedDate(resultSet.getLong(FINISHED_DATE_COLUMN))
                .retentionType(
                        RetentionRunSummary.RetentionType.fromString(resultSet.getString(RETENTION_ITEM_TYPE_COLUMN)))
                .status(RetentionRunSummary.Status.fromString(resultSet.getString(STATUS_COLUMN)))
                .itemType(RetentionRunSummary.ItemType.fromString(resultSet.getString(ITEM_TYPE_COLUMN)))
                .processedItemsCount(resultSet.getLong(PROCESSED_ITEMS_COUNT_COLUMN))
                .failedItemsCount(resultSet.getLong(FAILED_ITEMS_COUNT_COLUMN))
                .totalItemsCount(resultSet.getLong(TOTAL_ITEMS_COUNT_COLUMN))
                .processedPhysicalBinarySize(resultSet.getLong(PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN))
                .processedVirtualBinarySize(resultSet.getLong(PROCESSED_VIRTUAL_BINARY_SIZE_COLUMN))
                .triggeredBy(resultSet.getString(TRIGGERED_BY_COLUMN))
                .totalBinarySize(resultSet.getLong(TOTAL_BINARY_SIZE_COLUMN))
                .tempNodesCleaned(resultSet.getInt(TEMP_NODES_CLEANED_COLUMN) != 0)
                .runConfig(toRunConfig(resultSet))
                .build();
    }

    public PackageRunSummaryConfig toRunConfig(ResultSet resultSet) throws SQLException {
        byte[] runConfig = resultSet.getBytes(RUN_CONFIG_COLUMN);
        if (runConfig == null || runConfig.length == 0) {
            return null;
        }
        RetentionRunSummary.RetentionType retentionType =
                RetentionRunSummary.RetentionType.fromString(resultSet.getString(RETENTION_ITEM_TYPE_COLUMN));
        if (RetentionRunSummary.RetentionType.RESTORE.equals(retentionType)) {
            return JsonUtils.getInstance().readValue(resultSet.getBytes(RUN_CONFIG_COLUMN),
                    RestoreRunSummaryConfig.class);
        }
        return JsonUtils.getInstance().readValue(resultSet.getBytes(RUN_CONFIG_COLUMN),
                RunSummaryConfig.class);
    }

    private String validateExcludeBlankExecutions(String excludeBlankExecutions) {
        if (excludeBlankExecutions != null && excludeBlankExecutions.equalsIgnoreCase("true")) {
            return AND + PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN + " !=0 ";
        }
        return "";
    }

    private String getSelectTotalAndCleanedItems() {
        return String.format("""
                %s AS totalItems,
                %s AS cleanedItems
                """, TOTAL_ITEMS_COUNT_COLUMN, PROCESSED_ITEMS_COUNT_COLUMN);
    }

    private enum SortOrder {
        ASC,
        DESC
    }

    @Getter
    private enum SortBy {
        POLICY_RUN_ID("runId", RUN_ID_COLUMN),
        POLICY_KEY("policyKey", POLICY_KEY_COLUMN),
        STATUS(STATUS_COLUMN, STATUS_COLUMN),
        PROCESSED_VIRTUAL_BINARY_SIZE(CLEANED_SIZE_VIEW, CLEANED_SIZE_VIEW),
        TYPE(ITEM_TYPE_COLUMN, ITEM_TYPE_COLUMN);


        private final String name;
        private final String column;

        SortBy(String name, String column) {
            this.name = name;
            this.column = column;
        }

        public static String fromKey(String key) {
            for (SortBy sortBy : values()) {
                if (sortBy.getName().equals(key)) {
                    return sortBy.getColumn();
                }
            }
            return RUN_ID_COLUMN;
        }
    }

    private record PolicyRunWhereCondition(
            String condition,
            List<Object> params
    ) {
    }

    @Builder
    private record PolicyRunWhereConditionInputs (
            Set<String> itemTypes,
            String policyKey,
            Set<String> policyKeys,
            Set<String> status,
            String excludeBlankExecutions,
            List<Object> params,
            StringBuilder where,
            PolicyType policyType,
            Set<Integer> runIds
    ) {
    }

    @Getter
    enum RetentionRunSummaryColumns {
        POLICY_KEY(POLICY_KEY_COLUMN, 1),
        RUN_ID(RUN_ID_COLUMN, 2),
        DRY_RUN(DRY_RUN_COLUMN, 3),
        STARTED_DATE(STARTED_DATE_COLUMN, 4),
        FINISHED_DATE(FINISHED_DATE_COLUMN, 5),
        RETENTION_ITEM_TYPE(RETENTION_ITEM_TYPE_COLUMN, 6),
        STATUS(STATUS_COLUMN, 7),
        ITEM_TYPE(ITEM_TYPE_COLUMN, 8),
        PROCESSED_ITEMS_COUNT(PROCESSED_ITEMS_COUNT_COLUMN, 9),
        FAILED_ITEMS_COUNT(FAILED_ITEMS_COUNT_COLUMN, 10),
        TOTAL_ITEMS_COUNT(TOTAL_ITEMS_COUNT_COLUMN, 11),
        PROCESSED_PHYSICAL_BINARY_SIZE(PROCESSED_PHYSICAL_BINARY_SIZE_COLUMN, 12),
        PROCESSED_VIRTUAL_BINARY_SIZE(PROCESSED_VIRTUAL_BINARY_SIZE_COLUMN, 13),
        TRIGGERED_BY(TRIGGERED_BY_COLUMN, 14),
        TOTAL_BINARY_SIZE(TOTAL_BINARY_SIZE_COLUMN, 15),
        TEMP_NODES_CLEANED(TEMP_NODES_CLEANED_COLUMN, 16),
        RUN_CONFIG(RUN_CONFIG_COLUMN, 17);

        private final String name;
        private final int index;

        RetentionRunSummaryColumns(String name, int index) {
            this.name = name;
            this.index = index;
        }

    }

}
