package org.artifactory.storage.db.policy.cleanup.dao;

import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.policy.RunSummaryConfig;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupRunConfig;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.jfrog.common.JsonUtils;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.SqlDaoHelper;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.jfrog.storage.wrapper.BlobWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.common.ConstantValues.dbOperationsBulkSize;
@Slf4j
@Repository
public class CleanupRunSummaryDao {

    private static final String CLEANUP_RUN_SUMMARY_TABLE = "cleanup_run_summary";
    private static final String POLICY_KEY_COLUMN = "policy_key";
    private static final String POLICY_RUN_ID_COLUMN = "policy_run_id";
    private static final String DRY_RUN_COLUMN = "dry_run";
    private static final String STARTED_DATE_COLUMN = "started_date";
    private static final String FINISHED_DATE_COLUMN = "finished_date";
    private static final String STATUS_COLUMN = "status";
    private static final String TYPE_COLUMN = "type";
    private static final String TEMP_NODES_CLEANED_COLUMN = "temp_nodes_cleaned";
    private static final String DELETED_PACKAGES_COLUMN = "deleted_packages_count";
    private static final String FAILED_PACKAGES_COLUMN = "failed_packages_count";
    private static final String DELETED_ARTIFACTS_COLUMN = "deleted_artifacts_count";
    private static final String FAILED_ARTIFACTS_COLUMN = "failed_artifacts_count";
    private static final String ORIGINAL_BINARY_SIZE_COLUMN = "original_binary_size";
    private static final String ESTIMATED_DELETED_BINARY_SIZE_COLUMN = "estimated_deleted_binary_size";
    private static final String TRIGGERED_BY_COLUMN = "triggered_by";
    private static final String TOTAL_BINARY_SIZE = "total_binary_size";
    private static final String TOTAL_ITEMS_VIEW_COLUMN = "totalItems";
    private static final String CLEANED_ITEMS_VIEW_COLUMN = "cleanedItems";
    private static final String CLEANED_SIZE_VIEW_COLUMN = "cleanedSize";
    private static final String SCHEDULER_USERNAME = "scheduler";
    private static final String POLICY_RUN_PAGINATION_ORDER = " DESC";
    private static final String RUN_CONFIG_COLUMN = "run_config";

    private enum SortOrder {
        ASC,
        DESC
    }

    @Getter
    private enum SortBy {
        POLICY_RUN_ID("runId", POLICY_RUN_ID_COLUMN),
        POLICY_KEY("policyKey", POLICY_KEY_COLUMN),
        STATUS(STATUS_COLUMN, STATUS_COLUMN),
        ESTIMATED_DELETED_BINARY_SIZE(CLEANED_SIZE_VIEW_COLUMN, CLEANED_SIZE_VIEW_COLUMN),
        TYPE(TYPE_COLUMN, TYPE_COLUMN);

        private final String name;
        private final String column;

        private static final Map<String, SortBy> CODE_TO_ENUM = new HashMap<>();

        static {
            for (SortBy sortBy : SortBy.values()) {
                CODE_TO_ENUM.put(sortBy.getName().toLowerCase(), sortBy);
            }
        }

        SortBy(String name, String column) {
            this.name = name;
            this.column = column;
        }

        private static SortBy fromString(String code) {
            return CODE_TO_ENUM.get(code.toLowerCase());
        }
    }

    private static final String JOINED_COLUMNS = String.join(",",
            POLICY_KEY_COLUMN,
            POLICY_RUN_ID_COLUMN,
            DRY_RUN_COLUMN,
            STARTED_DATE_COLUMN,
            FINISHED_DATE_COLUMN,
            STATUS_COLUMN,
            TYPE_COLUMN,
            TEMP_NODES_CLEANED_COLUMN,
            DELETED_PACKAGES_COLUMN,
            FAILED_PACKAGES_COLUMN,
            DELETED_ARTIFACTS_COLUMN,
            FAILED_ARTIFACTS_COLUMN,
            ORIGINAL_BINARY_SIZE_COLUMN,
            ESTIMATED_DELETED_BINARY_SIZE_COLUMN,
            TRIGGERED_BY_COLUMN,
            TOTAL_BINARY_SIZE,
            RUN_CONFIG_COLUMN
    );
    private static final String INSERT =
            "INSERT INTO " + CLEANUP_RUN_SUMMARY_TABLE +
                    " (" + JOINED_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT =
            "SELECT " + JOINED_COLUMNS +
                    " FROM " + CLEANUP_RUN_SUMMARY_TABLE +
                    " WHERE " +
                    POLICY_KEY_COLUMN + "=? " +
                    "AND " +
                    POLICY_RUN_ID_COLUMN + "=?";

    private static final String UPDATE =
            "UPDATE " + CLEANUP_RUN_SUMMARY_TABLE + " SET " +
                    STARTED_DATE_COLUMN + "=?, " +
                    FINISHED_DATE_COLUMN + "=?, " +
                    STATUS_COLUMN + "=?, " +
                    TYPE_COLUMN + "=?, " +
                    DELETED_PACKAGES_COLUMN + "=?, " +
                    FAILED_PACKAGES_COLUMN + "=?, " +
                    DELETED_ARTIFACTS_COLUMN + "=?, " +
                    FAILED_ARTIFACTS_COLUMN + "=?, " +
                    ORIGINAL_BINARY_SIZE_COLUMN + "=?, " +
                    ESTIMATED_DELETED_BINARY_SIZE_COLUMN + "=? ," +
                    TRIGGERED_BY_COLUMN + "=? ," +
                    TOTAL_BINARY_SIZE + "=? " +
                    "WHERE " +
                    POLICY_KEY_COLUMN + "=? " +
                    "AND " +
                    POLICY_RUN_ID_COLUMN + "=? ";

    private final JdbcHelper jdbcHelper;
    private final SqlDaoHelper<CleanupRunSummary> daoHelper;

    @Autowired
    public CleanupRunSummaryDao(JdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.jdbcHelper = jdbcHelper;
        // daoHelper is only used in tests
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                CLEANUP_RUN_SUMMARY_TABLE, this::toCleanupRunSummary, dbOperationsBulkSize.getInt());
    }

    public void insert(CleanupRunSummary cleanupRunSummary) throws SQLException {
        String runConfig = JsonUtils.getInstance().valueToString(cleanupRunSummary.runConfig());
        BlobWrapper runConfigBlob = new BlobWrapper(runConfig);
        jdbcHelper.executeUpdate(INSERT,
                cleanupRunSummary.policyKey(),
                cleanupRunSummary.policyRunId(),
                cleanupRunSummary.dryRun() ? 1 : 0,
                cleanupRunSummary.startedDate(),
                cleanupRunSummary.finishedDate(),
                cleanupRunSummary.status().getName(),
                cleanupRunSummary.type().getName(),
                cleanupRunSummary.tempNodesCleaned() ? 1 : 0,
                cleanupRunSummary.deletedPackagesCount(),
                cleanupRunSummary.failedPackagesCount(),
                cleanupRunSummary.deletedArtifactsCount(),
                cleanupRunSummary.failedArtifactsCount(),
                cleanupRunSummary.originalBinarySize(),
                cleanupRunSummary.estimatedDeletedBinarySize(),
                cleanupRunSummary.triggeredBy(),
                cleanupRunSummary.totalBinarySize(),
                runConfigBlob
        );
    }

    public CleanupRunSummary get(String policyKey, int policyRunId) throws SQLException {
        CleanupRunSummary summary = null;
        try (ResultSet resultSet = jdbcHelper.executeSelect(
                SELECT, policyKey, policyRunId)) {
            if (resultSet.next()) {
                summary = toCleanupRunSummary(resultSet);
            }
            return summary;
        }
    }

    public int update(CleanupRunSummary summary) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE,
                summary.startedDate(),
                summary.finishedDate(),
                summary.status().getName(),
                summary.type().getName(),
                summary.deletedPackagesCount(),
                summary.failedPackagesCount(),
                summary.deletedArtifactsCount(),
                summary.failedArtifactsCount(),
                summary.originalBinarySize(),
                summary.estimatedDeletedBinarySize(),
                summary.triggeredBy(),
                summary.totalBinarySize(),
                // WHERE
                summary.policyKey(),
                summary.policyRunId()
        );
    }

    // deleteAll is only used in tests
    public int deleteAll() throws SQLException {
        return daoHelper.deleteAll();
    }

    // getAll is only used in tests
    public List<CleanupRunSummary> getAll() throws SQLException {
        return daoHelper.getAll();
    }

    private CleanupRunSummary toCleanupRunSummary(ResultSet resultSet) throws SQLException {
        return CleanupRunSummary.builder()
                .policyKey(resultSet.getString(POLICY_KEY_COLUMN))
                .policyRunId(resultSet.getInt(POLICY_RUN_ID_COLUMN))
                .dryRun(resultSet.getInt(DRY_RUN_COLUMN) != 0)
                .startedDate(resultSet.getLong(STARTED_DATE_COLUMN))
                .finishedDate(resultSet.getLong(FINISHED_DATE_COLUMN))
                .status(CleanupRunSummary.Status.fromString(resultSet.getString(STATUS_COLUMN)))
                .type(CleanupRunSummary.Type.fromString(resultSet.getString(TYPE_COLUMN)))
                .tempNodesCleaned(resultSet.getInt(TEMP_NODES_CLEANED_COLUMN) != 0)
                .deletedPackagesCount(resultSet.getLong(DELETED_PACKAGES_COLUMN))
                .failedPackagesCount(resultSet.getLong(FAILED_PACKAGES_COLUMN))
                .deletedArtifactsCount(resultSet.getLong(DELETED_ARTIFACTS_COLUMN))
                .failedArtifactsCount(resultSet.getLong(FAILED_ARTIFACTS_COLUMN))
                .originalBinarySize(resultSet.getLong(ORIGINAL_BINARY_SIZE_COLUMN))
                .estimatedDeletedBinarySize(resultSet.getLong(ESTIMATED_DELETED_BINARY_SIZE_COLUMN))
                .triggeredBy(resultSet.getString(TRIGGERED_BY_COLUMN))
                .totalBinarySize(resultSet.getLong(TOTAL_BINARY_SIZE))
                .runConfig(toCleanupRunConfig(resultSet))
                .build();
    }

    /**
     * Get completed non-dry runs by timestamp and policy type
     *
     * @throws SQLException internal SQL exception
     */
    @Nonnull
    public Set<CleanupRunSummary> getFinishedRunSummaries(@NonNull Set<String> itemTypes, long startTime, long endTime)
            throws SQLException {
        String selectQuery = new ArtifactoryQueryWriter()
                .select(JOINED_COLUMNS)
                .from(CLEANUP_RUN_SUMMARY_TABLE)
                .where(FINISHED_DATE_COLUMN + " > ? AND " +
                        FINISHED_DATE_COLUMN + " != 0 AND " +
                        FINISHED_DATE_COLUMN + " < ? AND " +
                        TYPE_COLUMN + " IN (#) AND " +
                        DRY_RUN_COLUMN + " = 0")
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(selectQuery, startTime, endTime, itemTypes)) {
            Set<CleanupRunSummary> records = new HashSet<>();
            while (resultSet.next()) {
                records.add(toCleanupRunSummary(resultSet));
            }
            return records;
        }
    }

    /**
     * Get completed non-dry runs for internal cleanup by timestamp
     *
     * @throws SQLException internal SQL exception
     */
    public Set<CleanupRunPolicyKeyAndRunId> getRunsForInternalCleanup(long timestamp) throws SQLException {
        String selectQuery = new ArtifactoryQueryWriter()
                .select(String.join(",", POLICY_KEY_COLUMN, POLICY_RUN_ID_COLUMN))
                .from(CLEANUP_RUN_SUMMARY_TABLE)
                .where(FINISHED_DATE_COLUMN + " <? AND " +
                        FINISHED_DATE_COLUMN + " !=0 AND " +
                        TEMP_NODES_CLEANED_COLUMN + " =0")
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(selectQuery, timestamp)) {
            Set<CleanupRunPolicyKeyAndRunId> records = Sets.newHashSet();
            while (resultSet.next()) {
                records.add(new CleanupRunPolicyKeyAndRunId(
                        resultSet.getString(POLICY_KEY_COLUMN),
                        resultSet.getInt(POLICY_RUN_ID_COLUMN)));
            }
            return records;
        }
    }

    public boolean markAsCleanedInternally(String policyKey, int policyRunId) throws SQLException {
        int successful = jdbcHelper.executeUpdate("UPDATE " + CLEANUP_RUN_SUMMARY_TABLE + " SET " +
                TEMP_NODES_CLEANED_COLUMN + "=? " +
                "WHERE " +
                POLICY_KEY_COLUMN + "=? " +
                "AND " +
                POLICY_RUN_ID_COLUMN + "=? ", 1, policyKey, policyRunId);
        return successful > 0;
    }

    public boolean markAsFinished(String policyKey, int policyRunId, long timestamp) throws SQLException {
        int successful = jdbcHelper.executeUpdate("UPDATE " + CLEANUP_RUN_SUMMARY_TABLE + " SET " +
                FINISHED_DATE_COLUMN + "=? " +
                "WHERE " +
                POLICY_KEY_COLUMN + "=? " +
                "AND " +
                POLICY_RUN_ID_COLUMN + "=? ", timestamp, policyKey, policyRunId);
        return successful > 0;
    }

    public Set<CleanupPolicyRun> getPolicyRuns(Set<String> itemTypes, long from, long to, @Nullable String policyKey,
            @Nullable Set<String> policyKeys, long page, long pageSize, String sortBy, String sortOrder,
            String excludeBlankExecutions, Set<String> status)
            throws SQLException {

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        excludeBlankExecutions = validateExcludeBlankExecutions(excludeBlankExecutions);
        updateWhereClause(itemTypes, from, to, policyKeys, policyKey, status, excludeBlankExecutions, params, where);

        return fetchPolicyRuns(page, pageSize, sortBy, sortOrder, params,where);
    }

    public Set<CleanupPolicyRun> getPolicyRuns(Set<String> itemTypes, @Nullable String policyKey,
            @Nullable Set<String> policyKeys, long page, long pageSize, String sortBy, String sortOrder,
            String excludeBlankExecutions, Set<String> status)
            throws SQLException {

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        excludeBlankExecutions = validateExcludeBlankExecutions(excludeBlankExecutions);
        updateWhereClause(itemTypes, policyKeys, policyKey, status, excludeBlankExecutions, params, where);
        return fetchPolicyRuns(page, pageSize, sortBy, sortOrder, params,where);

    }

    private Set<CleanupPolicyRun> fetchPolicyRuns(long page, long pageSize, String sortBy, String sortOrder,
            List<Object> params, StringBuilder where)
            throws SQLException {
        sortBy = validateSortBy(sortBy);
        sortOrder = validateSortOrder(sortOrder);

        String select = String.join(",",
                Set.of(POLICY_RUN_ID_COLUMN, TYPE_COLUMN, POLICY_KEY_COLUMN, STARTED_DATE_COLUMN, FINISHED_DATE_COLUMN,
                        STATUS_COLUMN, TRIGGERED_BY_COLUMN, DRY_RUN_COLUMN, RUN_CONFIG_COLUMN)) + "," +
                getSelectTotalAndCleanedItems() + "," + ESTIMATED_DELETED_BINARY_SIZE_COLUMN + " AS cleanedSize";

        Long offset = (pageSize * page) - pageSize;
        String query = new ArtifactoryQueryWriter()
                .select(select)
                .from(CLEANUP_RUN_SUMMARY_TABLE)
                .where(where.toString())
                .limit(pageSize)
                .offset(offset)
                .orderBy(sortBy + sortOrder)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, params.toArray())) {
            Set<CleanupPolicyRun> records = Sets.newLinkedHashSet();
            while (resultSet.next()) {
                records.add(toRunRecord(resultSet));
            }
            return records;
        }
    }

    private String validateExcludeBlankExecutions(String excludeBlankExecutions) {
        if (excludeBlankExecutions != null && excludeBlankExecutions.equalsIgnoreCase("true")) {
            return " AND " + ESTIMATED_DELETED_BINARY_SIZE_COLUMN + " !=0 ";
        }
        return "";
    }

    public long getPolicyRunsCount(Set<String> itemTypes, long from, long to, @Nullable String policyKey,
            @Nullable Set<String> policyKeys, @Nullable String excludeBlankExecutions, @Nullable Set<String> status)
            throws SQLException {
        PolicyRunWhereCondition condition =
                getPolicyRunsWhereCondition(itemTypes, from, to, policyKey, policyKeys, excludeBlankExecutions, status);
        String query = "SELECT COUNT(*) as count from " + CLEANUP_RUN_SUMMARY_TABLE + " where " + condition.condition;
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, condition.params.toArray())) {
            resultSet.next();
            return resultSet.getInt("count");
        }
    }

    public long getPolicyRunsCount(Set<String> itemTypes, @Nullable String policyKey,
            @Nullable Set<String> policyKeys, @Nullable String excludeBlankExecutions, @Nullable Set<String> status)
            throws SQLException {
        PolicyRunWhereCondition condition =
                getPolicyRunsWhereCondition(itemTypes, policyKey, policyKeys, excludeBlankExecutions, status);
        String query = "SELECT COUNT(*) as count from " + CLEANUP_RUN_SUMMARY_TABLE + " where " + condition.condition;
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, condition.params.toArray())) {
            resultSet.next();
            return resultSet.getInt("count");
        }
    }

    /**
     * Keyset pagination over cleanup run summaries for retention migration export.
     *
     * @param afterPolicyRunId exclusive cursor ({@code policy_run_id}); omit on first page
     */
    @Nonnull
    public List<CleanupRunSummaryMigrationRow> listCleanupRunSummariesForMigration(
            @Nullable Long afterPolicyRunId, int limit) throws SQLException {
        if (limit <= 0) {
            return List.of();
        }
        return fetchMigrationBatch(afterPolicyRunId, limit);
    }

    private List<CleanupRunSummaryMigrationRow> fetchMigrationBatch(@Nullable Long afterPolicyRunId, int limit)
            throws SQLException {
        List<Object> params = new ArrayList<>();
        QueryWriter queryWriter = new ArtifactoryQueryWriter()
                .select(JOINED_COLUMNS)
                .from(CLEANUP_RUN_SUMMARY_TABLE);
        if (afterPolicyRunId != null) {
            params.add(afterPolicyRunId);
            queryWriter.where(POLICY_RUN_ID_COLUMN + " > ?");
        }
        String query = queryWriter
                .orderBy(POLICY_RUN_ID_COLUMN + " ASC")
                .limit((long) limit)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, params.toArray())) {
            List<CleanupRunSummaryMigrationRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(toMigrationRow(resultSet));
            }
            return rows;
        }
    }

    private CleanupRunSummaryMigrationRow toMigrationRow(ResultSet resultSet) throws SQLException {
        byte[] runConfigBytes = resultSet.getBytes(RUN_CONFIG_COLUMN);
        return new CleanupRunSummaryMigrationRow(toCleanupRunSummary(resultSet), runConfigBytes);
    }

    public Set<CleanupRunSummary> findByStatuses(@NonNull Set<String> statuses) throws SQLException {
        String selectQuery = new ArtifactoryQueryWriter()
                .select(JOINED_COLUMNS)
                .from(CLEANUP_RUN_SUMMARY_TABLE)
                .where(STATUS_COLUMN + " IN (#)")
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(selectQuery, statuses)) {
            Set<CleanupRunSummary> records = Sets.newLinkedHashSet();
            while (resultSet.next()) {
                records.add(toCleanupRunSummary(resultSet));
            }
            return records;
        }
    }

    private PolicyRunWhereCondition getPolicyRunsWhereCondition(Set<String> itemTypes, long from, long to,
            @Nullable String policyKey, @Nullable Set<String> policyKeys, @Nullable String excludeBlankExecutions,
            @Nullable Set<String> status) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        excludeBlankExecutions = validateExcludeBlankExecutions(excludeBlankExecutions);
        updateWhereClause(itemTypes, from, to, policyKeys, policyKey, status, excludeBlankExecutions, params, where);

        return new PolicyRunWhereCondition(where.toString(), params);
    }

    private PolicyRunWhereCondition getPolicyRunsWhereCondition(Set<String> itemTypes,
            @Nullable String policyKey, @Nullable Set<String> policyKeys, @Nullable String excludeBlankExecutions,
            @Nullable Set<String> status) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        excludeBlankExecutions = validateExcludeBlankExecutions(excludeBlankExecutions);
        updateWhereClause(itemTypes, policyKeys, policyKey, status, excludeBlankExecutions, params, where);

        return new PolicyRunWhereCondition(where.toString(), params);
    }

    private void updateWhereClause(Set<String> itemTypes, long from, long to, Set<String> policyKeys, String policyKey,
            Set<String> status, String excludeBlankExecutions, List<Object> params, StringBuilder where) {
        updateWhereClause(itemTypes, policyKeys, policyKey, status, excludeBlankExecutions, params, where);
        where.append(" AND " + STARTED_DATE_COLUMN + " >= ? AND " + FINISHED_DATE_COLUMN + " <= ? ");
        params.add(from);
        params.add(to);
    }


    private void updateWhereClause(Set<String> itemTypes, Set<String> policyKeys, String policyKey,
            Set<String> status, String excludeBlankExecutions, List<Object> params, StringBuilder where) {
        where.append(TYPE_COLUMN + " IN (#) ")
                .append(excludeBlankExecutions);
        params.add(itemTypes);
        if (policyKeys != null && !policyKeys.isEmpty()) {
            where.append(" AND " + POLICY_KEY_COLUMN + " IN (#) ");
            params.add(policyKeys);
        } else if (policyKey != null) {
            where.append(" AND " + POLICY_KEY_COLUMN + " LIKE ? ");
            params.add("%" + policyKey + "%");
        }
        if (status != null && !status.isEmpty()) {
            where.append(" AND " + STATUS_COLUMN + " IN (#) ");
            params.add(status);
        }
    }

    private CleanupPolicyRun toRunRecord(ResultSet resultSet) throws SQLException {
        String triggeredBy = resultSet.getString(TRIGGERED_BY_COLUMN);
        boolean manual = !triggeredBy.equals(SCHEDULER_USERNAME);
        return CleanupPolicyRun.builder()
                .runId(resultSet.getInt(POLICY_RUN_ID_COLUMN))
                .taskType(resultSet.getString(TYPE_COLUMN))
                .policyKey(resultSet.getString(POLICY_KEY_COLUMN))
                .startTime(resultSet.getLong(STARTED_DATE_COLUMN))
                .endTime(resultSet.getLong(FINISHED_DATE_COLUMN))
                .totalItems(resultSet.getLong(TOTAL_ITEMS_VIEW_COLUMN))
                .cleanedItems(resultSet.getLong(CLEANED_ITEMS_VIEW_COLUMN))
                .cleanedSize(resultSet.getLong(CLEANED_SIZE_VIEW_COLUMN))
                .status(resultSet.getString(STATUS_COLUMN))
                .triggeredBy(triggeredBy)
                .manual(manual)
                .dryRun(resultSet.getBoolean(DRY_RUN_COLUMN))
                .runSummaryConfig(toRetentionRunConfig(resultSet))
                .build();
    }

    String validateSortBy(String sortBy) {
        if (sortBy == null) {
            return POLICY_RUN_ID_COLUMN;
        }
        SortBy sort = SortBy.fromString(sortBy);
        if (sort == null) {
            log.warn("Column: {} is not a valid sort by column", sortBy);
            return POLICY_RUN_ID_COLUMN;
        }
        return sort.getColumn();
    }

    String validateSortOrder(String sortOrder) {
        if (sortOrder == null) {
            return POLICY_RUN_PAGINATION_ORDER;
        }
        try {
            SortOrder order = SortOrder.valueOf(sortOrder.toUpperCase());
            return " " + order.name();
        } catch (IllegalArgumentException e) {
            log.warn("Sort order: {} is not a valid sort order", sortOrder);
            return POLICY_RUN_PAGINATION_ORDER;
        }
    }

    public CleanupRunConfig toCleanupRunConfig(ResultSet resultSet) throws SQLException {
        return readRunConfig(resultSet, CleanupRunConfig.class);
    }

    public RunSummaryConfig toRetentionRunConfig(ResultSet resultSet) throws SQLException {
        return readRunConfig(resultSet, RunSummaryConfig.class);
    }

    private String getSelectTotalAndCleanedItems() {
        return String.format("""
                %s + %s AS totalItems,
                %s AS cleanedItems
                """, DELETED_PACKAGES_COLUMN, FAILED_PACKAGES_COLUMN, DELETED_PACKAGES_COLUMN);
    }


    private <T> T readRunConfig(ResultSet resultSet, Class<T> valueType) throws SQLException {
        byte[] runConfig = resultSet.getBytes(RUN_CONFIG_COLUMN);
        if (runConfig == null || runConfig.length == 0) {
            return null;
        }
        return JsonUtils.getInstance().readValue(runConfig, valueType);
    }

    private record PolicyRunWhereCondition(
            String condition,
            List<Object> params
    ) {
    }
}
