package org.artifactory.storage.db.policy.dao;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.jfrog.storage.SqlDaoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.common.ConstantValues.dbOperationsBulkSize;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.ALL_COLUMNS;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.CREATED_BY_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.CREATED_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.CRON_EXP_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.DESCRIPTION_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.ENABLED_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.GET_BY_KEY_SQL;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.INSERT_SQL;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.ITEM_TYPE_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.MODIFIED_BY_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.MODIFIED_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.POLICY_BLOB_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.POLICY_KEY_COLUMN;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.POLICY_TYPE_COLUMN;
import static org.artifactory.policy.PolicyType.CLEANUP;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.TABLE_NAME;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.UPDATE_ALL_SQL;
import static org.artifactory.storage.db.policy.dao.PolicyDaoConstants.UPDATE_SQL;

@Repository
public class PolicyDao {
    private final ArtifactoryJdbcHelper jdbcHelper;
    private final SqlDaoHelper<DbPolicy> daoHelper;

    @Autowired
    public PolicyDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.jdbcHelper = jdbcHelper;
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                TABLE_NAME, PolicyDao::getFromResultSet, dbOperationsBulkSize.getInt());
    }

    public boolean insert(DbPolicy dbPolicy) throws SQLException {
        validateInsert(dbPolicy);

        byte[] policyConfig = dbPolicy.config();
        return jdbcHelper.executeUpdate(INSERT_SQL,
                dbPolicy.key(),
                dbPolicy.type(),
                dbPolicy.itemType(),
                dbPolicy.enabled() ? 1 : 0,
                dbPolicy.created(),
                dbPolicy.createdBy(),
                dbPolicy.modified(),
                dbPolicy.modifiedBy(),
                dbPolicy.cronExp(),
                dbPolicy.description(),
                policyConfig != null ? policyConfig : new byte[] {}) > 0;
    }

    public boolean update(DbPolicy dbPolicy) throws SQLException {
        validateUpdate(dbPolicy);

        return jdbcHelper.executeUpdate(UPDATE_SQL,
                dbPolicy.type(),
                dbPolicy.itemType(),
                dbPolicy.enabled() ? 1 : 0,
                dbPolicy.created(),
                dbPolicy.createdBy(),
                dbPolicy.modified(),
                dbPolicy.modifiedBy(),
                dbPolicy.cronExp(),
                dbPolicy.description(),
                dbPolicy.config(),
                dbPolicy.key()) > 0;
    }

    public boolean updateAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig)
            throws SQLException {
        List<Object> params = new ArrayList<>();
        params.add(updateAllPoliciesConfig.enabled());
        params.add(updateAllPoliciesConfig.modified());
        params.add(updateAllPoliciesConfig.modifiedBy());
        params.add(policyKeys);
        return jdbcHelper.executeUpdate(UPDATE_ALL_SQL, params.toArray()) > 0;
    }

    public Optional<DbPolicy> getByKey(String policyKey) throws SQLException {
        try (ResultSet resultSet = daoHelper.executeSelect(GET_BY_KEY_SQL, policyKey)) {
            return resultSet.next() ? Optional.of(getFromResultSet(resultSet)) : Optional.empty();
        }
    }

    public boolean existsByKey(String policyKey) throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select("COUNT(*)")
                .from(TABLE_NAME)
                .where(POLICY_KEY_COLUMN + "=?")
                .build();
        return daoHelper.executeCount(query, policyKey) > 0;
    }

    public boolean deleteByKey(String policyKey) throws SQLException {
        return daoHelper.deleteByProperty(POLICY_KEY_COLUMN, policyKey) > 0;
    }

    public int countAll() throws SQLException {
        return daoHelper.executeCount("SELECT COUNT(*) FROM " + TABLE_NAME);
    }

    public List<DbPolicy> getAll() throws SQLException {
        return daoHelper.findAllByQuery("SELECT " + ALL_COLUMNS + " FROM " + TABLE_NAME);
    }

    public int truncateAll() throws SQLException {
        return jdbcHelper.executeUpdate("TRUNCATE TABLE " + TABLE_NAME);
    }

    public List<DbPolicy> findByPolicyTypeAndItemType(String type, String itemType) throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select(ALL_COLUMNS)
                .from(TABLE_NAME)
                .where(POLICY_TYPE_COLUMN + " = ? AND " +
                        ITEM_TYPE_COLUMN + " = ?")
                .build();
        return daoHelper.findAllByQuery(query, type, itemType);
    }

    public long countByPolicyTypeAndItemType(String type, String itemType) throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select("COUNT(*)")
                .from(TABLE_NAME)
                .where(POLICY_TYPE_COLUMN + " = ? AND " +
                        ITEM_TYPE_COLUMN + " = ?")
                .build();
        return daoHelper.executeCount(query, type, itemType);
    }

    public List<DbPolicy> findByPolicyKeyFilter(String policyKeyFilter) throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select(ALL_COLUMNS)
                .from(TABLE_NAME)
                .where(POLICY_KEY_COLUMN + " LIKE ?")
                .build();
        return daoHelper.findAllByQuery(query, "%" + policyKeyFilter + "%");
    }

    /**
     * Keyset pagination over cleanup policy rows for retention migration export.
     *
     * @param afterPolicyKey exclusive cursor ({@code policy_key}); omit on first page
     */
    public List<DbPolicy> listCleanupPoliciesForMigration(@Nullable String afterPolicyKey, int limit)
            throws SQLException {
        String where = POLICY_TYPE_COLUMN + " = ?";
        List<Object> params = new ArrayList<>();
        params.add(CLEANUP.getCode());
        if (StringUtils.isNotBlank(afterPolicyKey)) {
            where += " AND " + POLICY_KEY_COLUMN + " > ?";
            params.add(afterPolicyKey);
        }
        String query = new ArtifactoryQueryWriter()
                .select(ALL_COLUMNS)
                .from(TABLE_NAME)
                .where(where)
                .orderBy(POLICY_KEY_COLUMN + " ASC")
                .limit((long) limit)
                .build();
        return daoHelper.findAllByQuery(query, params.toArray());
    }

    public List<DbPolicy> findByTypeAndItemTypesAndPolicyKeyFilter(String policyType, Set<String> itemTypes,
            String policyKeyFilter) throws SQLException {
        List<Object> params = Lists.newArrayList(policyType, itemTypes);
        String wherePolicyKeyFilter = StringUtils.EMPTY;
        if (StringUtils.isNotBlank(policyKeyFilter)) {
            params.add("%" + policyKeyFilter + "%");
            wherePolicyKeyFilter = " AND " + POLICY_KEY_COLUMN + " LIKE ?";
        }
        String query = new ArtifactoryQueryWriter()
                .select(ALL_COLUMNS)
                .from(TABLE_NAME)
                .where(POLICY_TYPE_COLUMN + " = ? AND " +
                        ITEM_TYPE_COLUMN + " IN (#)" + wherePolicyKeyFilter)
                .build();
        return daoHelper.findAllByQuery(query ,params.toArray());
    }

    private static DbPolicy getFromResultSet(ResultSet resultSet) throws SQLException {
        return new DbPolicy(
                resultSet.getString(POLICY_KEY_COLUMN),
                resultSet.getString(POLICY_TYPE_COLUMN),
                resultSet.getString(ITEM_TYPE_COLUMN),
                resultSet.getInt(ENABLED_COLUMN) != 0,
                resultSet.getLong(CREATED_COLUMN),
                resultSet.getString(CREATED_BY_COLUMN),
                resultSet.getObject(MODIFIED_COLUMN) == null ? null : resultSet.getLong(MODIFIED_COLUMN),
                resultSet.getString(MODIFIED_BY_COLUMN),
                resultSet.getString(CRON_EXP_COLUMN),
                resultSet.getString(DESCRIPTION_COLUMN),
                resultSet.getBytes(POLICY_BLOB_COLUMN));
    }

    private static void validateInsert(DbPolicy dbPolicy) {
        if (StringUtils.isBlank(dbPolicy.key())) {
            throw new IllegalArgumentException("Policy 'key' field can't be blank");
        }
        if (StringUtils.isBlank(dbPolicy.type())) {
            throw new IllegalArgumentException("Policy 'type' field can't be blank");
        }
        if (StringUtils.isBlank(dbPolicy.itemType())) {
            throw new IllegalArgumentException("Policy 'item type' field can't be blank");
        }
        if (StringUtils.isBlank(dbPolicy.createdBy())) {
            throw new IllegalArgumentException("Policy 'created by' field can't be blank");
        }
        if (dbPolicy.created() <= 0) {
            throw new IllegalArgumentException("Policy 'created' field can't be less or equal 0");
        }
        if (dbPolicy.config() == null) {
            throw new IllegalArgumentException("Policy 'config' field can't be null");
        }
    }

    private static void validateUpdate(DbPolicy dbPolicy) {
        validateInsert(dbPolicy);

        if (StringUtils.isBlank(dbPolicy.modifiedBy())) {
            throw new IllegalArgumentException("Policy 'modified by' field can't be blank");
        }
        if (dbPolicy.modified() == null || dbPolicy.modified() <= 0) {
            throw new IllegalArgumentException("Policy 'modified' field can't be null or less/equal 0");
        }
    }
}