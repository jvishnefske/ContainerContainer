package org.artifactory.storage.db.policy.cleanup.dao;

import org.apache.commons.compress.utils.Lists;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyViewRun;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.artifactory.util.CollectionUtils;
import org.glassfish.jersey.internal.guava.Sets;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class CleanupPolicyViewDao extends BaseDao {
    private static final String CLEANUP_RUN_SUMMARY_TABLE = "cleanup_run_summary";
    private static final String POLICY_RUN_ID_COLUMN = "policy_run_id";
    private static final String MAX_POLICY_RUN_ID_COLUMN = "MAX(policy_run_id)";
    private static final String TYPE_COLUMN = "type";
    private static final String POLICY_KEY_COLUMN = "policy_key";
    private static final String DRY_RUN_COLUMN = "dry_run";
    private static final String STARTED_DATE_COLUM = "started_date";
    private static final String STATUS_COLUMN = "status";
    private static final String IN_CONDITION = " IN (#) ";
    private static final String POLICY_KEY_IN_CONDITION = POLICY_KEY_COLUMN + IN_CONDITION;
    private static final String POLICY_RUN_ID_IN_CONDITION = POLICY_RUN_ID_COLUMN + IN_CONDITION;
    private static final String MAX_POLICY_RUN_ID_ALIAS = "pr";
    private static final String DELETED_PACKAGES_COLUMN = "deleted_packages_count";
    private static final String FAILED_PACKAGES_COLUMN = "failed_packages_count";
    private static final String DELETED_ARTIFACTS_COLUMN = "deleted_artifacts_count";
    private static final String FAILED_ARTIFACTS_COLUMN = "failed_artifacts_count";
    private static final String SELECT_COLUMNS = String.join(",",
            POLICY_RUN_ID_COLUMN, POLICY_KEY_COLUMN, TYPE_COLUMN, STARTED_DATE_COLUM, STATUS_COLUMN, DRY_RUN_COLUMN,
            DELETED_PACKAGES_COLUMN, FAILED_PACKAGES_COLUMN, DELETED_ARTIFACTS_COLUMN, FAILED_ARTIFACTS_COLUMN);

    public CleanupPolicyViewDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    /**
     * Returns policies last run data filtered by policy keys.
     */
    public List<CleanupPolicyViewRun> getPolices(List<String> policyKeys) throws SQLException {
        if (CollectionUtils.isNullOrEmpty(policyKeys)) {
            return Collections.emptyList();
        }
        Set<Integer> runIds = getLastRunIds(policyKeys);
        if (CollectionUtils.isNullOrEmpty(runIds)) {
            return Collections.emptyList();
        }
        String select = new ArtifactoryQueryWriter()
                .select(SELECT_COLUMNS)
                .from(CLEANUP_RUN_SUMMARY_TABLE)
                .where(POLICY_RUN_ID_IN_CONDITION)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(select, runIds)) {
            List<CleanupPolicyViewRun> records = Lists.newArrayList();
            while (resultSet.next()) {
                records.add(toRecord(resultSet));
            }
            return records;
        }
    }

    private Set<Integer> getLastRunIds(List<String> policyKeys) throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select(MAX_POLICY_RUN_ID_COLUMN + " as " + MAX_POLICY_RUN_ID_ALIAS)
                .from(CLEANUP_RUN_SUMMARY_TABLE)
                .where(POLICY_KEY_IN_CONDITION)
                .groupBy(POLICY_KEY_COLUMN)
                .orderBy(MAX_POLICY_RUN_ID_COLUMN)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, policyKeys)) {
            Set<Integer> records = Sets.newHashSet();
            while (resultSet.next()) {
                records.add(resultSet.getInt(MAX_POLICY_RUN_ID_ALIAS));
            }
            return records;
        }
    }

    private CleanupPolicyViewRun toRecord(ResultSet resultSet) throws SQLException {
        long totalItems = resultSet.getLong(DELETED_PACKAGES_COLUMN) + resultSet.getLong(FAILED_PACKAGES_COLUMN);

        return new CleanupPolicyViewRun(resultSet.getInt(POLICY_RUN_ID_COLUMN),
                resultSet.getString(POLICY_KEY_COLUMN),
                resultSet.getString(TYPE_COLUMN),
                resultSet.getLong(STARTED_DATE_COLUM),
                resultSet.getBoolean(DRY_RUN_COLUMN),
                resultSet.getString(STATUS_COLUMN),
                totalItems);
    }
}