package org.artifactory.storage.db.policy.cleanup.dao;

import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.policy.NodeMetadata;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.storage.db.policy.cleanup.CleanedNode;
import org.artifactory.storage.db.policy.cleanup.CleanedPackage;
import org.artifactory.storage.db.policy.cleanup.DeleteArtifactStatus;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.SqlDaoHelper;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.artifactory.common.ConstantValues.dbOperationsBulkSize;
import static org.artifactory.storage.db.util.BaseDao.dotIfNullOrEmpty;

/**
 * @author vovam
 */
@Repository
public class NodeCleanedDao {
    public static final String NODES_CLEANED_TABLE = "nodes_cleaned";
    private static final String POLICY_KEY_COLUMN = "policy_key";
    private static final String POLICY_RUN_ID_COLUMN = "policy_run_id";
    private static final String PACKAGE_NAME_COLUMN = "package_name";
    private static final String PACKAGE_VERSION_COLUMN = "package_version";
    private static final String LEAD_ARTIFACT_COLUMN = "lead_artifact";
    private static final String REPO_COLUMN = "repo";
    private static final String NODE_PATH_COLUMN = "node_path";
    private static final String NODE_NAME_COLUMN = "node_name";
    private static final String BIN_LENGTH_COLUMN = "bin_length";
    private static final String SHA1_COLUMN = "sha1";
    private static final String STATUS_COLUMN = "status";
    private static final String MESSAGE_COLUMN = "message";
    private static final String METADATA_COLUMN = "metadata";
    private static final String JOINED_COLUMNS = String.join(",",
            POLICY_KEY_COLUMN,
            POLICY_RUN_ID_COLUMN,
            PACKAGE_NAME_COLUMN,
            PACKAGE_VERSION_COLUMN,
            LEAD_ARTIFACT_COLUMN,
            REPO_COLUMN,
            NODE_PATH_COLUMN,
            NODE_NAME_COLUMN,
            BIN_LENGTH_COLUMN,
            SHA1_COLUMN,
            STATUS_COLUMN,
            MESSAGE_COLUMN,
            METADATA_COLUMN);
    private static final String INSERT_RECORD_SQL = "INSERT INTO " + NODES_CLEANED_TABLE + " (" + JOINED_COLUMNS +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_RECORD_STATUS_SQL = "UPDATE " + NODES_CLEANED_TABLE + " SET " +
            STATUS_COLUMN + "=? " +
            " WHERE " + POLICY_KEY_COLUMN + " = ? AND " + POLICY_RUN_ID_COLUMN + " = ? AND " + SHA1_COLUMN + " IN (#)";

    private static final String DELETE_BY_POLICY_NAME_AND_RUN =
            "DELETE FROM " + NODES_CLEANED_TABLE + " WHERE " + POLICY_KEY_COLUMN + "=? AND " + POLICY_RUN_ID_COLUMN +
                    "=?";
    private static final String ESTIMATED_CLEANED_BIN_LENGTH_QUERY = """
            select sum(nc.bin_length)
                from (select distinct nc1.sha1, nc1.bin_length
                      from nodes_cleaned nc1
                      where nc1.status = ?
                        and nc1.policy_key = ?
                        and nc1.policy_run_id = ?
                        and not exists(select 1 from nodes n where n.sha1_actual = nc1.sha1 and n.repo != ?)) 
                        nc
            """;

    private static final String ESTIMATED_CLEANED_BIN_LENGTH_QUERY_WITH_STATUS = """
            select sum(nc.bin_length)
                from (select distinct nc1.sha1, nc1.bin_length
                      from nodes_cleaned nc1
                      where nc1.status = ?
                        and nc1.policy_key = ?
                        and nc1.policy_run_id = ?)
                        nc
            """;

    private static final String CLEANUP_RUN_TOTAL_BIN_SIZE_QUERY = """
            SELECT sum(nc.bin_length)
                          FROM nodes_cleaned nc
                          WHERE nc.status = 0
                            AND nc.policy_key = ?
                            AND nc.policy_run_id = ?
            """;

    private static final String RETENTION_RUN_VIRTUAL_BIN_SIZE_QUERY = """
            SELECT sum(nc.bin_length)
                          FROM nodes_cleaned nc
                          WHERE nc.status = ?
                            AND nc.policy_key = ?
                            AND nc.policy_run_id = ?
            """;

    private final ArtifactoryJdbcHelper jdbcHelper;
    private final ArtifactoryDbProperties dbProperties;
    private final SqlDaoHelper<CleanedNode> daoHelper;

    @Autowired
    public NodeCleanedDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.jdbcHelper = jdbcHelper;
        this.dbProperties = dbProperties;
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                NODES_CLEANED_TABLE, this::getFromResultSet, dbOperationsBulkSize.getInt());
    }

    private CleanedNode getFromResultSet(ResultSet resultSet) throws SQLException {
        return new CleanedNode(
                resultSet.getString(POLICY_KEY_COLUMN),
                resultSet.getInt(POLICY_RUN_ID_COLUMN),
                resultSet.getString(PACKAGE_NAME_COLUMN),
                resultSet.getString(PACKAGE_VERSION_COLUMN),
                resultSet.getInt(LEAD_ARTIFACT_COLUMN) == 0 ? false : true,
                resultSet.getString(REPO_COLUMN),
                resultSet.getString(NODE_PATH_COLUMN),
                resultSet.getString(NODE_NAME_COLUMN),
                resultSet.getLong(BIN_LENGTH_COLUMN),
                resultSet.getString(SHA1_COLUMN),
                resultSet.getInt(STATUS_COLUMN),
                resultSet.getString(MESSAGE_COLUMN),
                deserializeMetadata(resultSet)
        );
    }

    public int insertCleanedNode(CleanedNode cleanedNode) throws SQLException {
        return jdbcHelper.executeUpdate(INSERT_RECORD_SQL,
                cleanedNode.policyKey(),
                cleanedNode.policyRunId(),
                cleanedNode.packageName(),
                cleanedNode.packageVersion(),
                cleanedNode.lead() ? 1 : 0,
                cleanedNode.repo(),
                dotIfNullOrEmpty(cleanedNode.nodePath()),
                cleanedNode.nodeName(),
                cleanedNode.binLength(),
                cleanedNode.sha1(),
                cleanedNode.status(),
                cleanedNode.message(),
                serializeMetadata(cleanedNode.metadata()));

    }

    public void updateCleanedNodeBySha(String policyKey, int policyRunId, int status, List<String> sha1List)
            throws SQLException {
        daoHelper.executeUpdate(UPDATE_RECORD_STATUS_SQL,
                status,
                // where
                policyKey,
                policyRunId,
                sha1List);
    }

    public long estimateActualCleanedBinLength(String policyKey, int policyRunId) throws SQLException {
        return jdbcHelper.executeSelectLong(ESTIMATED_CLEANED_BIN_LENGTH_QUERY,
                DeleteArtifactStatus.DELETED.getCode(),
                policyKey,
                policyRunId,
                TrashService.TRASH_KEY);
    }

    public long estimateActualProcessedBinLength(String policyKey, int policyRunId,
            DeleteArtifactStatus processedStatus) throws SQLException {
        return jdbcHelper.executeSelectLong(ESTIMATED_CLEANED_BIN_LENGTH_QUERY,
                processedStatus.getCode(),
                policyKey,
                policyRunId,
                TrashService.TRASH_KEY);
    }

    public long estimateActualProcessedBinLengthByStatus(String policyKey, int policyRunId,
            DeleteArtifactStatus processedStatus) throws SQLException {
        return jdbcHelper.executeSelectLong(ESTIMATED_CLEANED_BIN_LENGTH_QUERY_WITH_STATUS,
                processedStatus.getCode(),
                policyKey,
                policyRunId);
    }

    /**
     * Calculates the total size of all nodes processed by the specified cleanup policy run. This is NOT the actual
     * cleaned binaries size, because same binaries may be referenced by multiple nodes.
     *
     * @param policyKey   policy key
     * @param policyRunId id of the policy run
     * @return a sum of all binaries size (in bytes) that correspond to the nodes processed by the specified run
     * @throws SQLException in case of a DB exception
     */
    public long calculateCleanupRunTotalBinSize(@NonNull String policyKey, @NonNull long policyRunId)
            throws SQLException {
        return jdbcHelper.executeSelectLong(CLEANUP_RUN_TOTAL_BIN_SIZE_QUERY, policyKey, policyRunId);
    }

    /**
     * Calculates the virtual size of all nodes processed by the specified retention policy run. This is NOT the actual
     * cleaned binaries size, because same binaries may be referenced by multiple nodes.
     *
     * @param policyKey       policy key
     * @param policyRunId     id of the policy run
     * @param processedStatus status of the processed nodes
     * @return a sum of all binaries size (in bytes) that correspond to the nodes processed by the specified run
     * @throws SQLException in case of a DB exception
     */
    public long calculateVirtualBinarySize(@NonNull String policyKey, long policyRunId,
            DeleteArtifactStatus processedStatus) throws SQLException {
        return jdbcHelper.executeSelectLong(RETENTION_RUN_VIRTUAL_BIN_SIZE_QUERY, processedStatus.getCode(), policyKey,
                policyRunId);
    }

    public int deleteByPolicyKeyAndRun(String policyKey, int policyRunId) throws SQLException {
        return daoHelper.executeUpdate(DELETE_BY_POLICY_NAME_AND_RUN, policyKey, policyRunId);
    }

    public int truncateAll() throws SQLException {
        return jdbcHelper.executeUpdate("TRUNCATE TABLE " + NODES_CLEANED_TABLE);
    }

    public int countAll() throws SQLException {
        return daoHelper.executeCount("SELECT COUNT(*) FROM " + NODES_CLEANED_TABLE);
    }

    public List<CleanedNode> getAll() throws SQLException {
        return daoHelper.findAllByQuery("SELECT " + JOINED_COLUMNS + " FROM " + NODES_CLEANED_TABLE);
    }

    /**
     * Returns the message field from the first failure-related item in the given policy run, or null if none exists.
     * Checks ERROR (status=2) first, then falls back to SKIPPED (status=1) — lead-artifact failures are recorded as
     * SKIPPED while non-lead failures are recorded as ERROR. Two separate queries avoid ORDER BY on the reserved
     * keyword "status" which causes binding errors on MSSQL when QueryWriter rewrites LIMIT as a subquery.
     */
    public String getFirstErrorMessageForRun(@NotNull String policyKey, int policyRunId) throws SQLException {
        String baseWhere = POLICY_KEY_COLUMN + " = ? AND " + POLICY_RUN_ID_COLUMN + " = ? AND "
                + STATUS_COLUMN + " = ? AND " + MESSAGE_COLUMN + " IS NOT NULL";
        QueryWriter errorQuery = new QueryWriter(dbProperties.getDbType());
        errorQuery.select(MESSAGE_COLUMN).from(NODES_CLEANED_TABLE).where(baseWhere).limit(1L);
        try (ResultSet rs = jdbcHelper.executeSelect(errorQuery.build(), policyKey, policyRunId,
                DeleteArtifactStatus.ERROR.getCode())) {
            if (rs.next()) {
                return rs.getString(MESSAGE_COLUMN);
            }
        }
        QueryWriter skippedQuery = new QueryWriter(dbProperties.getDbType());
        skippedQuery.select(MESSAGE_COLUMN).from(NODES_CLEANED_TABLE).where(baseWhere).limit(1L);
        try (ResultSet rs = jdbcHelper.executeSelect(skippedQuery.build(), policyKey, policyRunId,
                DeleteArtifactStatus.SKIPPED.getCode())) {
            if (rs.next()) {
                return rs.getString(MESSAGE_COLUMN);
            }
        }
        return null;
    }

    @Nonnull
    public List<CleanedPackage> getDeletedNodesRecords(@NotNull String policyKey, int policyRunId) throws SQLException {
        QueryWriter query = new QueryWriter(dbProperties.getDbType());
        String selectedColumns = String.join(",",
                List.of(PACKAGE_NAME_COLUMN, PACKAGE_VERSION_COLUMN, REPO_COLUMN, MESSAGE_COLUMN,
                        "SUM(bin_length) as bin_length", "MAX(status) as status"));
        query.select(selectedColumns)
                .from(NODES_CLEANED_TABLE)
                .where("policy_key = ? AND policy_run_id = ?")
                .groupBy("package_name, package_version, repo, message");

        List<CleanedPackage> cleanedPackages = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query.build(), policyKey, policyRunId)) {
            while (resultSet.next()) {
                CleanedPackage cleanedPackage = new CleanedPackage(
                        resultSet.getString(PACKAGE_NAME_COLUMN),
                        resultSet.getString(PACKAGE_VERSION_COLUMN),
                        resultSet.getString(REPO_COLUMN),
                        resultSet.getLong(BIN_LENGTH_COLUMN),
                        resultSet.getInt(STATUS_COLUMN),
                        resultSet.getString(MESSAGE_COLUMN));
                cleanedPackages.add(cleanedPackage);
            }
            return cleanedPackages;
        }
    }

    @Nonnull
    public List<CleanedNode> getLeadMetadata(@NotNull String policyKey, int policyRunId) throws SQLException {
        String query = "SELECT " + JOINED_COLUMNS +
                " FROM " + NODES_CLEANED_TABLE +
                " WHERE policy_key = ? AND policy_run_id = ? " +
                "   AND lead_artifact = 1 ";

        try (ResultSet resultSet = jdbcHelper.executeSelect(query, policyKey, policyRunId)) {
            return getCleanedNodesRecords(new ArrayList<>(), resultSet);
        }
    }

    @Nonnull
    public List<CleanedNode> getCleanedNodesByRun(@NotNull String policyKey, int policyRunId) throws SQLException {
        QueryWriter query = new QueryWriter(dbProperties.getDbType());
        query.select(JOINED_COLUMNS)
                .from(NODES_CLEANED_TABLE)
                .where(POLICY_KEY_COLUMN + " = ? AND " + POLICY_RUN_ID_COLUMN + " = ?");

        List<CleanedNode> cleanedNodes = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query.build(), policyKey, policyRunId)) {
            return getCleanedNodesRecords(cleanedNodes, resultSet);
        }
    }

    public List<CleanedNode> getCleanedNodesByStatus(List<Integer> statuses, long limit, long offset)
            throws SQLException {
        QueryWriter query = new QueryWriter(dbProperties.getDbType());
        query.select(JOINED_COLUMNS)
                .from(NODES_CLEANED_TABLE)
                .where(STATUS_COLUMN + " IN (#) ")
                .orderBy(POLICY_RUN_ID_COLUMN)
                .limit(limit)
                .offset(offset < 0 ? 0 : offset);

        List<CleanedNode> cleanedNodes = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query.build(), statuses)) {
            return getCleanedNodesRecords(cleanedNodes, resultSet);
        }
    }

    private List<CleanedNode> getCleanedNodesRecords(List<CleanedNode> cleanedNodes, ResultSet resultSet)
            throws SQLException {
        while (resultSet.next()) {
            CleanedNode cleanedNode = CleanedNode.builder()
                    .policyKey(resultSet.getString(POLICY_KEY_COLUMN))
                    .policyRunId(resultSet.getInt(POLICY_RUN_ID_COLUMN))
                    .packageName(resultSet.getString(PACKAGE_NAME_COLUMN))
                    .packageVersion(resultSet.getString(PACKAGE_VERSION_COLUMN))
                    .lead(resultSet.getBoolean(LEAD_ARTIFACT_COLUMN))
                    .repo(resultSet.getString(REPO_COLUMN))
                    .nodePath(resultSet.getString(NODE_PATH_COLUMN))
                    .nodeName(resultSet.getString(NODE_NAME_COLUMN))
                    .binLength(resultSet.getLong(BIN_LENGTH_COLUMN))
                    .sha1(resultSet.getString(SHA1_COLUMN))
                    .status(resultSet.getInt(STATUS_COLUMN))
                    .message(resultSet.getString(MESSAGE_COLUMN))
                    .metadata(deserializeMetadata(resultSet))
                    .build();
            cleanedNodes.add(cleanedNode);
        }
        return cleanedNodes;
    }

    private NodeMetadata deserializeMetadata(ResultSet resultSet) throws SQLException {
        byte[] metadataBytes = resultSet.getBytes(METADATA_COLUMN);
        if (metadataBytes != null && metadataBytes.length > 0) {
            try {
                return org.jfrog.common.JsonUtils.getInstance()
                        .readValue(metadataBytes, NodeMetadata.class);
            } catch (Exception e) {
                throw new SQLException("Failed to deserialize metadata", e);
            }
        }
        return null;

    }

    private byte[] serializeMetadata(NodeMetadata metadata) throws SQLException {
        if (metadata == null) {
            return new byte[0];
        }
        try {
            String metadataJson = org.jfrog.common.JsonUtils.getInstance().valueToString(metadata);
            return metadataJson.getBytes();
        } catch (Exception e) {
            throw new SQLException("Failed to serialize metadata", e);
        }
    }
}
