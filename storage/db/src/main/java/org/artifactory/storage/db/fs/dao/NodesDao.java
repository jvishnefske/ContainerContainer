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

package org.artifactory.storage.db.fs.dao;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.RepoStorageSummary;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.entity.FolderSummaryNodeInfo;
import org.artifactory.storage.db.fs.entity.FolderSummaryNodeInfoImpl;
import org.jfrog.rtfs.dto.sync.FileListEntry;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.db.fs.entity.NodeProperty;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.artifactory.storage.fs.VfsException;
import org.jfrog.common.ResultSetStream;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.SqlDaoHelper;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.nodesStreamingDbFetchPageSize;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.storage.db.util.ArtifactoryDbUtils.isSupportsStreaming;
import static org.artifactory.utils.PathUtils.isPathContainsSpecialChars;
import static org.artifactory.utils.PathUtils.replaceSpecialChars;

/**
 * A data access object for node table access.
 *
 * @author Yossi Shaul
 */
@Slf4j
@Repository
public class NodesDao extends BaseDao {

    public static final String NODES_TABLE = "nodes";
    public static final String REPO_COLUMN_NAME = "repo";
    public static final String NODE_ID_COLUMN = "node_id";
    public static final String NODE_PATH_COLUMN = "node_path";
    public static final String UPDATE_QUERY = "UPDATE nodes " +
            "SET repo = ?,  node_path = ?, node_name = ?, " +
            "depth = ?, created = ?, created_by = ?, " +
            "modified = ?, modified_by = ?, updated = ?, bin_length = ?, " +
            "sha1_actual = ?, sha1_original = ?, md5_actual = ?, md5_original = ?, sha256 = ?, " +
            "repo_path_checksum = ? " +
            "WHERE node_id = ?";
    private static final String NODE_COLUMNS =
            "(node_id, node_type, repo, node_path, node_name, "
                    + "depth, created, created_by, "
                    + "modified, modified_by, updated, "
                    + "bin_length, sha1_actual, sha1_original, "
                    + "md5_actual, md5_original, sha256, "
                    + "repo_path_checksum)";

    private static final String NODE_VALUES =
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String INSERT_QUERY =
            "INSERT INTO nodes " + NODE_COLUMNS + " " + NODE_VALUES;

    static final String POSTGRES_CONDITIONAL_INSERT =
            INSERT_QUERY + " ON CONFLICT (repo varchar_pattern_ops,"
                    + " node_path varchar_pattern_ops,"
                    + " node_name varchar_pattern_ops) DO NOTHING";

    static final String ORACLE_CONDITIONAL_INSERT =
            "INSERT /*+ IGNORE_ROW_ON_DUPKEY_INDEX(nodes (repo, node_path, node_name)) */ "
                    + "INTO nodes " + NODE_COLUMNS + " " + NODE_VALUES;

    static final String MSSQL_CONDITIONAL_INSERT =
            "BEGIN TRY "
                    + INSERT_QUERY + "; "
                    + "END TRY "
                    + "BEGIN CATCH "
                    + "IF ERROR_NUMBER() NOT IN (2601, 2627) THROW; "
                    + "END CATCH";

    private static final String SELECT_NODE_QUERY = "SELECT * FROM nodes ";
    private static final String SELECT_NODE_FOR_UPDATE = "SELECT 1 FROM nodes WHERE node_id=? FOR UPDATE";

    private static final String SELECT_NODES_SIZE_TEMPLATE = """
            SELECT SUM(bin_length) AS total_size
            FROM (
                SELECT MAX(bin_length) AS bin_length
                FROM nodes
                WHERE node_type = 1
                  AND sha1_actual IS NOT NULL
                  AND repo IN (#)
                GROUP BY sha1_actual
            )%s unique_nodes""";
    // Retrieve total size of unique nodes (by sha1_actual) in the given repos (for most of DBs)
    private static final String SELECT_NODES_SIZE = SELECT_NODES_SIZE_TEMPLATE.formatted("");
    // Retrieve total size of unique nodes (by sha1_actual) in the given repos (specially for Derby)
    private static final String SELECT_NODES_SIZE_DERBY = SELECT_NODES_SIZE_TEMPLATE.formatted(" AS");

    // The HOLDLOCK hint politely asks SQL Server to hold the lock until you commit the transaction.
    // The ROWLOCK hint politely asks SQL Server to lock only this row rather than issuing a page or table lock.
    private static final String SELECT_NODE_FOR_UPDATE_MSSQL =
            "SELECT 1 FROM nodes WITH (HOLDLOCK, ROWLOCK) WHERE node_id=?";

    private final ArtifactoryDbProperties dbProperties;
    private String nodeByPathQuery;
    private String nodeIdByPathQuery;
    private String nodesByPropertyQuery;
    private String nodesExistsQuery;
    private String nodesGetNodeTypeQuery;
    private String nodeHasChildren;
    private String nodeGetChildren;
    private String nodeGetDescendants;

    @Autowired
    public NodesDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        initExternalQueries();
        this.dbProperties = dbProperties;
    }

    /**
     * these are queries that can be altered through the artifactory.system.properties for adding hints {@see RTFACT
     * -15325}
     */
    private void initExternalQueries() {
        nodeByPathQuery = ConstantValues.nodesDaoSqlGetNodeByPath.isSet() ?
                ConstantValues.nodesDaoSqlGetNodeByPath.getString() :
                "SELECT * FROM nodes WHERE repo = ? AND node_path = ? AND node_name = ?";
        nodeIdByPathQuery = ConstantValues.nodesDaoSqlGetNodeIdByPath.isSet() ?
                ConstantValues.nodesDaoSqlGetNodeIdByPath.getString() :
                "SELECT node_id FROM nodes WHERE repo = ? AND node_path = ? AND node_name = ?";
        nodesByPropertyQuery = ConstantValues.nodesDaoSqlSearchFilesByProperty.isSet() ?
                ConstantValues.nodesDaoSqlSearchFilesByProperty.getString() :
                "SELECT n.* FROM nodes n JOIN node_props p ON n.node_id = p.node_id WHERE repo = ? AND p.prop_key = ?";
        nodesExistsQuery = ConstantValues.nodesDaoSqlNodeExists.isSet() ?
                ConstantValues.nodesDaoSqlNodeExists.getString() :
                "SELECT count(*) FROM nodes WHERE repo = ? AND node_path = ? AND node_name = ?";
        nodesGetNodeTypeQuery = ConstantValues.nodesDaoSqlGetItemType.isSet() ?
                ConstantValues.nodesDaoSqlGetItemType.getString() :
                "SELECT node_type FROM nodes WHERE repo = ? AND node_path = ? AND node_name = ?";
        nodeHasChildren = ConstantValues.nodesDaoSqlNodeHasChildren.isSet() ?
                ConstantValues.nodesDaoSqlNodeHasChildren.getString() :
                "SELECT COUNT(1) FROM nodes WHERE repo = ? AND node_path = ? AND depth = ?";
        nodeGetChildren = ConstantValues.nodesDaoSqlNodeGetChildren.isSet() ?
                ConstantValues.nodesDaoSqlNodeGetChildren.getString() :
                SELECT_NODE_QUERY + "WHERE repo = ? AND node_path = ? AND depth = ?";
        nodeGetDescendants = SELECT_NODE_QUERY + "WHERE repo = ? AND (node_path = ? OR node_path LIKE ?)";
    }

    @Nullable
    public Node get(NodePath path) throws SQLException {
        Node node = null;
        try (ResultSet resultSet = jdbcHelper.executeSelectForSmallResults(nodeByPathQuery, path.getRepo(),
                dotIfNullOrEmpty(path.getPath()), dotIfNullOrEmpty(path.getName()))) {
            if (resultSet.next()) {
                node = nodeFromResultSet(resultSet);
            }
            return node;
        }
    }

    @Nullable
    public Node get(long id) throws SQLException {
        ResultSet resultSet = null;
        Node node = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM nodes WHERE node_id = ?", id);
            if (resultSet.next()) {
                node = nodeFromResultSet(resultSet);
            }
            return node;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    @NonNull
    public Map<NodePath, Node> get(@NonNull List<NodePath> paths) throws SQLException {
        Map<NodePath, Node> nodesMap = new HashMap<>();
        for (NodePath path : paths) {
            Node node = get(path);
            if (node != null) {
                nodesMap.put(path, node);
            }
        }
        return nodesMap;

    }

    @NonNull
    public Map<NodePath, Long> getNodesIds(@NonNull List<NodePath> paths) throws SQLException {
        Map<NodePath, Long> nodesMap = new HashMap<>();
        for (NodePath path : paths) {
            long id = getNodeId(path);
            if (DbService.NO_DB_ID != id) {
                nodesMap.put(path, id);
            }
        }
        return nodesMap;
    }

    public long getNodeId(NodePath path) throws SQLException {
        try (ResultSet resultSet = jdbcHelper.executeSelectForSmallResults(nodeIdByPathQuery, path.getRepo(),
                dotIfNullOrEmpty(path.getPath()), dotIfNullOrEmpty(path.getName()))) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            } else {
                return DbService.NO_DB_ID;
            }
        }
    }

    public int getFileCount(String repoKey, String fileName) throws SQLException {
        return jdbcHelper
                .executeSelectCount("SELECT COUNT(*) FROM nodes WHERE node_type=1 and node_name = ? and repo = ?",
                        fileName, repoKey);
    }

    public Integer getItemType(NodePath path) throws SQLException {
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelectForSmallResults(nodesGetNodeTypeQuery,
                    path.getRepo(), dotIfNullOrEmpty(path.getPath()), dotIfNullOrEmpty(path.getName()));
            if (resultSet.next()) {
                return resultSet.getInt("node_type");
            }
            return null;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public String getNodeSha1(NodePath path) throws SQLException {
        ResultSet resultSet = null;
        String sha1 = null;
        try {
            resultSet = jdbcHelper.executeSelect("SELECT sha1_actual FROM nodes " +
                            "WHERE repo = ? AND node_path = ? AND node_name = ?",
                    path.getRepo(), dotIfNullOrEmpty(path.getPath()), dotIfNullOrEmpty(path.getName()));
            if (resultSet.next()) {
                sha1 = resultSet.getString(1);
            }
            return sha1;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public List<Node> getFileNodesBySha1(List<String> sha1List) throws SQLException {
        List<List<String>> sha1Bulks = Lists.partition(sha1List, ConstantValues.dbOperationsBulkSize.getInt());
        List<Node> results = new ArrayList<>();

        log.trace("executing DB retrieval of {} sha1 bulks:", sha1Bulks.size());
        for (List<String> sha1Bulk : sha1Bulks) {
            log.trace("Retrieving bulk #{} containing {} sha1's",
                    sha1Bulks.indexOf(sha1Bulk) + 1,
                    sha1Bulk.size());
            results.addAll(retrieveSha1Bulk(sha1Bulk));
        }

        return results;
    }

    private List<Node> retrieveSha1Bulk(@NonNull List<String> sha1Bulk) throws SQLException {
        List<Node> results = new ArrayList<>();
        ResultSet resultSet = null;
        try {
            resultSet = jdbcHelper.executeSelect(SELECT_NODE_QUERY +
                            "WHERE node_type = 1 AND " +
                            "repo != 'auto-trashcan' AND repo != 'jfrog-support-bundle' AND sha1_actual IN (#)",
                    sha1Bulk);
            while (resultSet.next()) {
                results.add(nodeFromResultSet(resultSet));
            }
        } finally {
            DbUtils.close(resultSet);
        }

        return results;
    }

    public void createBulk(@NonNull List<Node> nodes) throws SQLException {
        List<List<Node>> nodeBulks = Lists.partition(nodes, ConstantValues.dbOperationsBulkSize.getInt());
        for (List<Node> nodeBulk : nodeBulks) {
            List<Object[]> listOfParams = new ArrayList<>();
            for (Node node : nodeBulk) {
                Object[] params = nodeToInsertParams(node);
                listOfParams.add(params);
            }
            jdbcHelper.executeBulkUpdate(INSERT_QUERY, listOfParams);
        }
    }

    public int create(Node node) throws SQLException {
        log.trace("About to insert node {}", node.toStringWithChecksums());
        return jdbcHelper.executeUpdate(INSERT_QUERY, nodeToInsertParams(node));
    }

    public int createIfNotExists(Node node) throws SQLException {
        log.trace("About to conditionally insert node {}", node.toStringWithChecksums());
        DbType dbType = dbProperties.getDbType();
        switch (dbType) {
            case POSTGRESQL:
                return jdbcHelper.executeUpdate(
                        POSTGRES_CONDITIONAL_INSERT, nodeToInsertParams(node));
            case ORACLE:
                return jdbcHelper.executeUpdate(
                        ORACLE_CONDITIONAL_INSERT, nodeToInsertParams(node));
            case MSSQL:
                return jdbcHelper.executeUpdate(
                        MSSQL_CONDITIONAL_INSERT, nodeToInsertParams(node));
            case MYSQL:
            case MARIADB:
                return jdbcHelper.executeUpdate(
                        INSERT_QUERY, nodeToInsertParams(node));
            default:
                try {
                    return jdbcHelper.executeUpdate(
                            INSERT_QUERY, nodeToInsertParams(node));
                } catch (SQLException e) {
                    if (!isUniqueConstraintViolation(e)) {
                        throw e;
                    }
                    log.debug("Node already exists, skipping: {}", node);
                    return 0;
                }
        }
    }


    public int update(Node node) throws SQLException {
        // node id and type are not updatable
        int res = jdbcHelper.executeUpdate(UPDATE_QUERY,
                node.getRepo(),
                dotIfNullOrEmpty(node.getPath()),
                dotIfNullOrEmpty(node.getName()),
                node.getDepth(),
                node.getCreated(),
                node.getCreatedBy(),
                node.getModified(),
                node.getModifiedBy(),
                node.getUpdated(),
                node.getLength(),
                node.getSha1Actual(),
                node.getSha1Original(),
                node.getMd5Actual(),
                node.getMd5Original(),
                node.getSha2(),
                calcUniqueKey(node),
                node.getNodeId()
        );
        log.trace("Node was {} updated. {}", res > 0 ? "" : "not", node.toStringWithChecksums());
        return res;
    }

    public boolean exists(NodePath path) throws SQLException {

        try (ResultSet resultSet = jdbcHelper.executeSelectForSmallResults(nodesExistsQuery, path.getRepo(),
                dotIfNullOrEmpty(path.getPath()), dotIfNullOrEmpty(path.getName()))) {
            int count = 0;
            if (resultSet.next()) {
                count = resultSet.getInt(1);
                if (count > 1) {
                    if (log.isDebugEnabled()) {
                        StorageException bigWarning = new StorageException(
                                "Unexpected node count for absolute path: '" + path + "' - " + count);
                        log.warn(bigWarning.getMessage(), bigWarning);
                    } else {
                        log.warn("Unexpected node count for absolute path: '{}' - {}", path, count);
                    }
                }
            }
            return count > 0;
        }
    }

    public boolean exists(long nodeId) throws SQLException {
        try (ResultSet resultSet = jdbcHelper.executeSelect("SELECT 1 FROM nodes WHERE node_id = ?", nodeId)) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    @NonNull
    public List<RepoPath> getExistingItems(@NonNull List<NodePath> paths) throws SQLException {
        Map<NodePath, Node> existingNodes = get(paths);

        List<RepoPath> existingRepoPaths = new ArrayList<>();
        for (Node existingNode : existingNodes.values()) {
            existingRepoPaths.add(new RepoPathImpl(existingNode.getRepo(),
                    existingNode.getPath() + "/" + existingNode.getName()
                            + (existingNode.isFile() ? "" : "/")));
        }
        return existingRepoPaths;
    }

    public boolean delete(long id) throws SQLException {
        int deleted = jdbcHelper.executeUpdate("DELETE FROM nodes WHERE node_id = ?", id);
        if (deleted > 0) {
            log.trace("Node {} deleted", id);
        }
        return deleted > 0;
    }

    public void deleteBulk(@NonNull List<Long> ids) throws SQLException {
        List<List<Long>> deleteBulks = Lists.partition(ids, ConstantValues.dbOperationsBulkSize.getInt());
        for (List<Long> deleteBulk : deleteBulks) {
            int deleted = jdbcHelper.executeUpdate("DELETE FROM nodes WHERE node_id in (#)", deleteBulk);
            if (deleted > 0) {
                if (deleted == deleteBulk.size()) {
                    log.trace("Nodes {} deleted", deleteBulk);
                } else {
                    log.trace("Some nodes of {} were not deleted. Expected {}, actual {}",
                            deleteBulk, deleteBulk.size(), deleted);
                }
            }
        }
    }

    /**
     * Returns non-root nodes (files and folders) for the given repository, up to the specified limit.
     * The root folder (depth = 0) is excluded since it is always present for an existing repository.
     */
    public List<Node> getNodesByRepo(String repoKey, int limit) throws SQLException {
        ResultSet resultSet = null;
        List<Node> results = new ArrayList<>();
        String query = new QueryWriter(dbProperties.getDbType())
                .select("*")
                .from(NODES_TABLE)
                .where("repo = ? AND depth > 0")
                .limit((long) limit)
                .build();
        try {
            resultSet = jdbcHelper.executeSelect(query, repoKey);
            while (resultSet.next()) {
                results.add(nodeFromResultSet(resultSet));
            }
            return results;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public List<Node> getChildren(NodePath path) throws SQLException {
        return getChildren(path, -1);
    }

    /**
     * Returns child nodes for the given path, with an optional limit.
     *
     * @param path  node path to retrieve children for
     * @param limit maximum number of children, or -1/0 for no limit
     * @return list of child nodes
     * @throws SQLException on database error
     */
    public List<Node> getChildren(NodePath path, long limit) throws SQLException {
        ResultSet resultSet = null;
        List<Node> results = Lists.newArrayList();
        try {
            // the child path must be the path+name of the parent
            String childPath = path.getPathName();

            String query;
            if (limit > 0) {
                query = new QueryWriter(dbProperties.getDbType())
                        .select("*")
                        .from(NODES_TABLE)
                        .where("repo = ? AND node_path = ? AND depth = ?")
                        .limit(limit)
                        .build();
            } else {
                query = nodeGetChildren;
            }

            resultSet = jdbcHelper.executeSelect(query,
                    path.getRepo(), dotIfNullOrEmpty(childPath), path.getDepth() + 1);
            while (resultSet.next()) {
                results.add(nodeFromResultSet(resultSet));
            }
            return results;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    @Nonnull
    public Stream<Node> getChildrenStream(NodePath path) throws SQLException {
        ResultSet rs = jdbcHelper.executeSelectStreamed(getQueryControls(), nodeGetChildren,
                path.getRepo(), dotIfNullOrEmpty(path.getPathName()), path.getDepth() + 1);
        CompletableFuture<SQLException> onException = futureBounceSqlExceptionToStorageException();
        return ResultSetStream.asStream(rs, this::nodeFromResultSet, onException);
    }

    @Nonnull
    public Stream<Node> getNodesStreamIgnoringRepo(NodePath nodePath) throws SQLException {
        String query = new QueryWriter(dbProperties.getDbType())
                .select()
                .from(NODES_TABLE)
                .where("node_path = ?")
                .build();
        ResultSet rs =
                jdbcHelper.executeSelectStreamed(getQueryControls(), query, dotIfNullOrEmpty(nodePath.getPathName()));
        CompletableFuture<SQLException> onException = futureBounceSqlExceptionToStorageException();
        return ResultSetStream.asStream(rs, this::nodeFromResultSet, onException)
                .filter(node -> !node.getName().isEmpty());
    }

    private QueryControls getQueryControls() {
        QueryControls.QueryControlsBuilder builder = QueryControls.builder();
        if (isSupportsStreaming(dbProperties.getDbType())) {
            builder.fetchSize(nodesStreamingDbFetchPageSize.getInt());
        }
        return builder.build();
    }

    private CompletableFuture<SQLException> futureBounceSqlExceptionToStorageException() {
        CompletableFuture<SQLException> onException = new CompletableFuture<>();
        onException.thenAccept(e -> {
            throw new StorageException("Failed to retrieve nodes", e);
        });
        return onException;
    }

    /**
     * Returns folder nodes within a node_id range for batch processing.
     * Used for systematic empty folder scanning.
     *
     * @param startNodeId  the starting node_id for the scan range
     * @param intervalSize the size of the interval to scan
     * @param limit        maximum number of nodes to return
     * @return list of folder nodes (node_type = 0) within the specified range
     * @throws SQLException on database error
     */
    public List<Node> getTrashFoldersByNodeIdRange(long startNodeId, long intervalSize, long limit)
            throws SQLException {
        ResultSet resultSet = null;
        List<Node> results = Lists.newArrayList();
        try {
            String query = new QueryWriter(dbProperties.getDbType())
                    .select("*")
                    .from(NODES_TABLE)
                    .where("node_type = 0 AND node_id >= ? AND node_id < ? AND repo = '"
                            + TRASH_REPO_KEY + "'")
                    .orderBy("node_id ASC")
                    .limit(limit)
                    .build();
            resultSet = jdbcHelper.executeSelect(query, startNodeId, startNodeId + intervalSize);
            while (resultSet.next()) {
                results.add(nodeFromResultSet(resultSet));
            }
            return results;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public List<Node> getDescendants(NodePath nodePath) throws SQLException {
        ResultSet resultSet = null;
        List<Node> results = Lists.newArrayList();
        try {
            String rootPath = nodePath.getPathName();
            if (isPathContainsSpecialChars(rootPath)) {
                log.debug("Escaping underscore in path '{}'", rootPath);
                String path = replaceSpecialChars(rootPath);
                resultSet = jdbcHelper.executeSelect(nodeGetDescendants.replace("LIKE ?", "LIKE ? ESCAPE '^'"),
                        nodePath.getRepo(), rootPath, path + "/%");
            } else {
                resultSet = jdbcHelper.executeSelect(nodeGetDescendants, nodePath.getRepo(), rootPath, rootPath + "/%");
            }
            while (resultSet.next()) {
                results.add(nodeFromResultSet(resultSet));
            }
            return results;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public boolean hasChildren(NodePath path) throws SQLException {
        ResultSet resultSet = null;
        try {
            // the child path must be the path+name of the parent
            String childPath = path.getPathName();
            resultSet = jdbcHelper.executeSelectForSmallResults(nodeHasChildren,
                    path.getRepo(), dotIfNullOrEmpty(childPath), path.getDepth() + 1);
            if (resultSet.next()) {
                int childrenCount = resultSet.getInt(1);
                log.trace("Children count of '{}': {}", path, childrenCount);
                return childrenCount > 0;
            }
            return false;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public List<? extends Node> getAllNodes() throws SQLException {
        if (!ConstantValues.dev.getBoolean()) {
            return Lists.newArrayList();
        }

        ResultSet resultSet = null;
        List<Node> results = Lists.newArrayList();
        try {
            resultSet = jdbcHelper.executeSelect(SELECT_NODE_QUERY);
            while (resultSet.next()) {
                results.add(nodeFromResultSet(resultSet));
            }
            return results;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public int getFilesCount() throws SQLException {
        if (ConstantValues.totalSizeUseChunks.getBoolean() && DbType.POSTGRESQL == dbProperties.getDbType()) {
            int totalSizeChunkSize = ConstantValues.totalSizeQueryChunkSize.getInt();
            long totalSizeChunkDelay = ConstantValues.totalSizeDelayMillis.getInt();
            int total = 0;
            long nodeIdUpTo = 0L;
            boolean goOn = true;
            while (goOn) {
                ResultSet resultSet = jdbcHelper.executeSelect("""
                        select count(*), max(node_id)
                        from (
                            select node_id
                            from nodes
                            where node_type = 1 and node_id > ?
                            order by node_id
                            limit %d) n
                        """.formatted(totalSizeChunkSize), nodeIdUpTo);
                resultSet.next();
                nodeIdUpTo = resultSet.getLong(2); // max(node_id)
                goOn = nodeIdUpTo != 0;
                if (goOn) {
                    total += resultSet.getLong(1); // count(*)
                    try {
                        TimeUnit.MILLISECONDS.sleep(totalSizeChunkDelay);
                    } catch (InterruptedException ignored) {
                        // We don't expect this thread to be interrupted but if it happens, it's good practice
                        // to restore interrupted state.
                        Thread.currentThread().interrupt();
                        // and to go on with the query execution.
                    }
                }
                DbUtils.close(resultSet);
            }
            return total;
        } else if (DbType.MYSQL == dbProperties.getDbType()
                && ConstantValues.totalSizeMySqlNoIndex.getBoolean()) {
            return jdbcHelper.executeSelectCount("SELECT COUNT(*) FROM nodes USE INDEX () WHERE node_type = 1");
        } else {
            return jdbcHelper.executeSelectCount("SELECT COUNT(*) FROM nodes WHERE node_type = 1");
        }
    }

    public int getFilesCount(String repoKey) throws SQLException {
        return jdbcHelper.executeSelectCount("SELECT COUNT(*) FROM nodes WHERE node_type=1 and repo = ?", repoKey);
    }

    public boolean isRepoContainsFiles(String repoKey) throws SQLException {
        ResultSet resultSet = null;
        String query = new QueryWriter(dbProperties.getDbType())
                .select("1 as count")
                .from(NODES_TABLE)
                .where("node_type=1 AND repo = ?")
                .limit(1L)
                .build();
        try {
            log.debug("Query '{}', repoKey='{}'", query, repoKey);
            resultSet = jdbcHelper.executeSelect(query, repoKey);
            return resultSet.next();
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public int getFilesCount(NodePath nodePath) throws SQLException {
        ResultSet resultSet = null;
        int result = 0;
        try {
            resultSet = jdbcHelper.executeSelect(
                    "SELECT COUNT(*) FROM nodes WHERE node_type=1 and repo = ? and depth > ? and " +
                            "(node_path = ? or node_path like ?)",
                    nodePath.getRepo(), nodePath.getDepth(), nodePath.getPathName(), nodePath.getPathName() + "/%");
            if (resultSet.next()) {
                result = resultSet.getInt(1);
            }
        } finally {
            DbUtils.close(resultSet);
        }
        return result;
    }

    public FolderSummaryNodeInfo getFilesCountAndSize(@Nonnull NodePath nodePath) throws SQLException {
        try (ResultSet resultSet = executeCountAndSizeResultSet(nodePath)) {
            if (resultSet.next()) {
                return new FolderSummaryNodeInfoImpl(resultSet.getLong(1), resultSet.getLong(2));
            } else {
                log.warn("Could not calculate artifact count and size for folder '{}' - query return no results",
                        nodePath);
                return new FolderSummaryNodeInfoImpl(0, 0);
            }
        }
    }

    // used by the sha2 migration, can probably remove in V6.0 :(
    public int getMissingSha2ArtifactCount() throws SQLException {
        return jdbcHelper.executeSelectCount("SELECT COUNT(*) FROM nodes WHERE sha256 IS NULL and node_type = 1");
    }

    // Used by the Conan V2 migration
    public int getConanV1LayoutArtifactCount() throws SQLException {
        return jdbcHelper.executeSelectCount(
                "SELECT COUNT(*) FROM nodes WHERE depth = 6 and node_type = 1 and node_name = 'conanfile.py'");
    }

    private ResultSet executeCountAndSizeResultSet(NodePath nodePath) throws SQLException {
        if (nodePath.isRoot()) {
            return jdbcHelper.executeSelect(
                    "SELECT COUNT(*) AS file_count, SUM(bin_length) AS total_size FROM nodes WHERE node_type=1 and " +
                            "repo = ?",
                    nodePath.getRepo());
        } else {
            return jdbcHelper.executeSelect(
                    "SELECT COUNT(*) AS file_count, SUM(bin_length) AS total_size FROM nodes WHERE node_type=1 and " +
                            "repo = ? and depth > ? and " +
                            "(node_path = ? or node_path like ?)",
                    nodePath.getRepo(), nodePath.getDepth(), nodePath.getPathName(), nodePath.getPathName() + "/%");
        }
    }

    public long getFilesTotalSize(String repoKey) throws SQLException {
        return jdbcHelper.executeSelectLong("SELECT SUM(bin_length) FROM nodes WHERE node_type=1 and repo = ?",
                repoKey);
    }

    public long getFilesTotalSize(NodePath nodePath) throws SQLException {
        return jdbcHelper.executeSelectLong(
                "SELECT SUM(bin_length) FROM nodes WHERE node_type=1 and repo = ? and depth > ? and " +
                        "(node_path = ? or node_path like ?)",
                nodePath.getRepo(), nodePath.getDepth(), nodePath.getPathName(), nodePath.getPathName() + "/%");
    }

    public int getNodesCount(String repoKey) throws SQLException {
        return jdbcHelper.executeSelectCount(
                "SELECT COUNT(*) FROM nodes WHERE repo = ?", repoKey);
    }

    public int getNodesCount(NodePath nodePath) throws SQLException {
        return jdbcHelper.executeSelectCount(
                "SELECT COUNT(*) FROM nodes WHERE repo = ? and depth > ? and (node_path = ? or node_path like ?)",
                nodePath.getRepo(), nodePath.getDepth(), nodePath.getPathName(), nodePath.getPathName() + "/%");
    }

    public List<Node> searchByChecksum(ChecksumType type, String checksum) throws SQLException {
        if (!type.isValid(checksum)) {
            throw new IllegalArgumentException(
                    "Cannot search for invalid " + type.name() + " checksum value '" + checksum + "'");
        }
        //until we rename to sha1 and md5....
        String fieldName = type.equals(ChecksumType.sha256) ? "sha256" : type.name() + "_actual";
        List<Node> results = Lists.newArrayList();
        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(SELECT_NODE_QUERY +
                    "WHERE " + fieldName + " = ?", checksum);
            while (rs.next()) {
                results.add(nodeFromResultSet(rs));
            }
        } finally {
            DbUtils.close(rs);
        }
        return results;
    }

    public List<Node> searchBadChecksums(ChecksumType type, Set<String> repos) {
        long totalLimit = ConstantValues.badChecksumsSearchLimit.getLong();
        List<Node> results = new ArrayList<>();
        List<List<String>> repoPartitions =
                Lists.partition(new ArrayList<>(repos), ConstantValues.dbOperationsBulkSize.getInt());

        repoPartitions.stream()
                .takeWhile(repoPartition -> results.size() < totalLimit)
                .forEach(repoPartition -> {
                    String query = new QueryWriter(dbProperties.getDbType())
                            .select()
                            .from(NodesDao.NODES_TABLE)
                            .where(createWhereClauseForBadChecksums(type, new HashSet<>(repoPartition)))
                            .limit(totalLimit - results.size())
                            .build();
                    try (ResultSet rs = type.equals(ChecksumType.sha256)
                            ? jdbcHelper.executeSelect(query)
                            : jdbcHelper.executeSelect(query, ChecksumInfo.TRUSTED_FILE_MARKER)) {
                        while (rs.next() && results.size() < totalLimit) {
                            results.add(nodeFromResultSet(rs));
                        }
                    } catch (SQLException e) {
                        throw new VfsException("Search files with bad checksum failed: " + e.getMessage(), e);
                    }
                });
        return results;
    }

    private String createWhereClauseForBadChecksums(ChecksumType type, Set<String> repos) {
        String where = "node_type = 1 AND repo IN (" +
                repos.stream().map(repo -> "'" + repo + "'").collect(Collectors.joining(", ")) + ") AND ";
        if (ChecksumType.sha256 == type) {
            where += "sha256 IS NULL";
        } else {
            String checksumOriginal = type.name() + "_original";
            String checksumActual = type.name() + "_actual";
            where += "(" + checksumOriginal + " IS NULL" +
                    " OR " +
                    checksumActual + " IS NULL " +
                    "OR " +
                    checksumOriginal + " != ? AND " +
                    checksumOriginal + " != " + checksumActual +
                    ")";
        }
        return where;
    }

    //TODO: [by YS] this is a just a temp naive search for maven plugin metadata
    public List<Node> searchNodesByProperty(String repo, String propKey) throws SQLException {
        List<Node> results = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(nodesByPropertyQuery, repo, propKey)) {
            while (resultSet.next()) {
                results.add(nodeFromResultSet(resultSet));
            }
        }
        return results;
    }

    public List<Node> getOrphanNodes(NodePath path) throws SQLException {
        ResultSet resultSet = null;
        List<Node> results = new ArrayList<>();
        try {
            resultSet = jdbcHelper.executeSelect("SELECT * FROM nodes n1" +
                            " WHERE n1.repo = ?" +
                            " AND n1.node_path like ?" +
                            " AND n1.node_name NOT IN" +
                            " (SELECT n2.node_name FROM nodes n2, nodes n3" +
                            " WHERE (n2.node_path like '%/%' AND n2.node_path like CONCAT('%/', n3.node_name))" +
                            " OR (n2.node_path not like '%/%' AND n2.node_path like CONCAT('%', n3.node_name)))",
                    path.getRepo(), emptyIfNullOrDot(path.getPath()) + "%");
            while (resultSet.next()) {
                results.add(nodeFromResultSet(resultSet));
            }
        } finally {
            DbUtils.close(resultSet);
        }
        return results;
    }

    public Set<RepoStorageSummary> getRepositoriesStorageSummary() throws SQLException {
        Map<String, RepoStorageSummary> results = Maps.newHashMap();
        if (DbType.POSTGRESQL == dbProperties.getDbType() && ConstantValues.totalSizeUseChunks.getBoolean()) {
            return getRepositoriesStorageSummaryPostgres();
        } else {
            String query;
            // RTDEV-39116 - Mysql Uses Inefficient index for above query
            // So we want to force it to do a sequential scan
            if (DbType.MYSQL == dbProperties.getDbType()
                    && ConstantValues.totalSizeMySqlNoIndex.getBoolean()) {
                query = buildStorageSummaryQuery(true);
                log.info("Using MySQL getRepositoriesStorageSummary query with no index");
            } else {
                query = buildStorageSummaryQuery(false);
            }
            try (ResultSet resultSet = jdbcHelper.executeSelect(query)) {
                while (resultSet.next()) {
                    RepoStorageSummary repoStorageSummary = repoSummaryPairFromResultSet(resultSet);
                    results.put(repoStorageSummary.getRepoKey(), repoStorageSummary);
                }
            }
        }

        return Sets.newHashSet(results.values());
    }

    private String buildStorageSummaryQuery(boolean skipIndex) {
        if (skipIndex) {
            return """
                    SELECT repo,
                    SUM(CASE WHEN node_type = 0 THEN 1 ELSE 0 END) as folders,
                    SUM(CASE WHEN node_type = 1 THEN 1 ELSE 0 END) as files,
                    SUM(bin_length)
                    FROM nodes USE INDEX() GROUP BY repo""";
        } else {
            return """
                    SELECT repo,
                    SUM(CASE WHEN node_type = 0 THEN 1 ELSE 0 END) as folders,
                    SUM(CASE WHEN node_type = 1 THEN 1 ELSE 0 END) as files,
                    SUM(bin_length)
                    FROM nodes GROUP BY repo""";
        }
    }

    private Set<RepoStorageSummary> getRepositoriesStorageSummaryPostgres() throws SQLException {
        int totalSizeChunkSize = ConstantValues.totalSizeQueryChunkSize.getInt();
        long totalSizeChunkDelay = ConstantValues.totalSizeDelayMillis.getInt();
        Map<String, RepoStorageSummary> results = Maps.newHashMap();
        long nodeIdUpTo = 0L;
        boolean goOn = true;
        while (goOn) {
            try (ResultSet resultSet = jdbcHelper.executeSelect("""
                    select repo,
                        sum(case when node_type = 0 then 1 else 0 end) as folders,
                        sum(case when node_type = 1 then 1 else 0 end) as files,
                        sum(bin_length), max(node_id)
                    from (
                        select *
                        from nodes
                        where node_id > ?
                        order by node_id
                        limit %d) n
                    group by repo
                    """.formatted(totalSizeChunkSize), nodeIdUpTo)) {

                goOn = resultSet.next();
                if (goOn) {
                    do {
                        RepoStorageSummary repoStorageSummary = repoSummaryPairFromResultSet(resultSet);
                        results.merge(repoStorageSummary.getRepoKey(), repoStorageSummary, RepoStorageSummary::merge);
                        nodeIdUpTo = Math.max(nodeIdUpTo, resultSet.getLong(5));
                    } while (resultSet.next());

                    try {
                        TimeUnit.MILLISECONDS.sleep(totalSizeChunkDelay);
                    } catch (InterruptedException ignored) {
                        // We don't expect this thread to be interrupted but if it happens, it's good practice
                        // to restore interrupted state.
                        Thread.currentThread().interrupt();
                        // and to go on with the query execution.
                    }
                }
            }
        }
        return Sets.newHashSet(results.values());
    }

    public long findLastNodeId() throws SQLException {
        ResultSet resultSet = null;
        long lastNodeId = 0;
        try {
            resultSet = jdbcHelper
                    .executeSelect("SELECT MAX(node_id) as node_id FROM nodes");
            if (resultSet.next()) {
                lastNodeId = resultSet.getLong(NODE_ID_COLUMN);
            }
            return lastNodeId;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    Node nodeFromResultSet(ResultSet resultSet) throws SQLException {
        long nodeId = resultSet.getLong(NODE_ID_COLUMN);
        boolean isFile = resultSet.getBoolean("node_type");
        String repoName = resultSet.getString("repo");
        String path = emptyIfNullOrDot(resultSet.getString(NODE_PATH_COLUMN));
        String fileName = emptyIfNullOrDot(resultSet.getString("node_name"));
        short depth = resultSet.getShort("depth");
        long created = resultSet.getLong("created");
        String createdBy = resultSet.getString("created_by");
        long modified = resultSet.getLong("modified");
        String modifiedBy = resultSet.getString("modified_by");
        long updated = resultSet.getLong("updated");
        long length = resultSet.getLong("bin_length");
        String sha1Actual = resultSet.getString("sha1_actual");
        String sha1Original = resultSet.getString("sha1_original");
        String md5Actual = resultSet.getString("md5_actual");
        String md5Original = resultSet.getString("md5_original");
        String sha2 = resultSet.getString("sha256");
        return new Node(nodeId, isFile, repoName, path, fileName, depth, created, createdBy, modified, modifiedBy,
                updated, length, sha1Actual, sha1Original, md5Actual, md5Original, sha2);
    }

    private RepoStorageSummary repoSummaryPairFromResultSet(ResultSet rs) throws SQLException {
        // don't count the repo folder itself -> folderCount - 1
        return new RepoStorageSummary(rs.getString(1), rs.getLong(2) - 1, rs.getLong(3), rs.getLong(4));
    }

    private String calcUniqueKey(Node node) {
        return calcUniqueKey(node.getRepo(), node.getPath(), node.getName());
    }

    private String calcUniqueKey(String repo, String path, String name) {
        String key = repo + "/" + path + "/" + name;
        return DigestUtils.sha1Hex(key.getBytes(StandardCharsets.UTF_8));
    }

    public boolean existsBySha1(String sha1) throws SQLException {
        ResultSet resultSet = null;
        QueryWriter query = new QueryWriter(dbProperties.getDbType());
        query.select("1 as count")
                .from(NODES_TABLE)
                .where("sha1_actual = ?")
                .limit(1L);
        try {
            resultSet = jdbcHelper.executeSelect(query.build(), sha1);
            return resultSet.next();
        } finally {
            DbUtils.close(resultSet);
        }
    }

    private Object[] nodeToInsertParams(@NonNull Node node) {
        return new Object[] {
                node.getNodeId(),
                booleanAsByte(node.isFile()),
                node.getRepo(),
                dotIfNullOrEmpty(node.getPath()),
                dotIfNullOrEmpty(node.getName()),
                node.getDepth(),
                node.getCreated(),
                node.getCreatedBy(),
                node.getModified(),
                node.getModifiedBy(),
                node.getUpdated(),
                node.getLength(),
                node.getSha1Actual(),
                node.getSha1Original(),
                node.getMd5Actual(),
                node.getMd5Original(),
                node.getSha2(),
                calcUniqueKey(node)
        };
    }

    @NonNull
    public Map<RepoPath, Boolean> areFiles(@NonNull Collection<RepoPath> repoPaths) throws SQLException {
        Map<RepoPath, Boolean> map = new HashMap<>();
        Map<NodePath, RepoPath> nodePathToRepoMap = new HashMap<>();

        List<NodePath> nodePaths = repoPaths.stream()
                .map(repoPath -> {
                    NodePath nodePath = NodePath.fromRepoPath(repoPath);
                    map.put(repoPath, null);
                    nodePathToRepoMap.put(nodePath, repoPath);
                    return nodePath;
                })
                .collect(Collectors.toList());

        Map<NodePath, Node> nodesFromDb = get(nodePaths);

        nodesFromDb.forEach((key, value) -> {
            RepoPath requestedPath = nodePathToRepoMap.get(key);
            map.put(requestedPath, value.isFile());
        });

        return map;
    }

    @NonNull
    public Set<String> findExistsRepoRootPaths(Set<String> repoKeys) throws SQLException {
        SqlDaoHelper<String> daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                NODES_TABLE, resultSet -> resultSet.getString(REPO_COLUMN_NAME),
                ConstantValues.existsRepoChunkSize.getInt());

        List<String> existedKeys = daoHelper.findByMultiColumnAndInClauseWithPagination(REPO_COLUMN_NAME, NODES_TABLE,
                List.of(NODE_PATH_COLUMN, "node_name"), List.of(".", "."), REPO_COLUMN_NAME, repoKeys);
        return new HashSet<>(existedKeys);
    }

    @NonNull
    public List<Node> getNodesByPathAndName(NodePath path, long limit) throws SQLException {
        ResultSet resultSet = null;
        String query = new QueryWriter(dbProperties.getDbType())
                .select()
                .from(NODES_TABLE)
                .where("node_path = ? AND node_name = ?")
                .limit(limit)
                .build();
        List<Node> nodes = new ArrayList<>();
        try {
            resultSet = jdbcHelper.executeSelectForSmallResults(query,
                    dotIfNullOrEmpty(path.getPath()), dotIfNullOrEmpty(path.getName()));
            while (resultSet.next()) {
                nodes.add(nodeFromResultSet(resultSet));
            }
            return nodes;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    /**
     * Checks if a folder at the given repository path contains more than the specified limit of items.
     * If the folder is the root (repository), it checks the total number of nodes in the repository.
     * Otherwise, it counts the items in the folder and its subfolders.
     * this method is used to check if a folder has more than the allowed number of items.
     *
     * @param folderRepoPath the path of the folder in the repository
     * @param limit          the maximum number of items allowed
     * @return the number of items for the given path
     * @throws SQLException if a database error occurs
     */
    public long getNumberOfItemsForPathWithLimit(NodePath folderRepoPath, long limit) throws SQLException {
        String repoKey = folderRepoPath.getRepo();
        String rootPath = folderRepoPath.getPathName();
        log.debug("Checking if folder {} has more than {} items", folderRepoPath.getPath(), limit);

        if (folderRepoPath.isRoot()) {
            return handleRepo(limit, repoKey);
        } else {
            String query = buildFinalQuery(rootPath, limit);
            if (isPathContainsSpecialChars(rootPath)) {
                rootPath = replaceSpecialChars(rootPath);
            }
            List<Object> params = List.of(repoKey, rootPath, rootPath + "/%");
            return jdbcHelper.executeSelectLong(query, params.toArray());
        }
    }

    private long handleRepo(long limit, String repoKey) throws SQLException {
        String innerQuery = new QueryWriter(dbProperties.getDbType())
                .select(buildInnerQuerySelectPart(limit))
                .from(NODES_TABLE)
                .where("repo = ?")
                .limit(limit)
                .build();
        String aliasPart = dbProperties.getDbType().equals(DbType.DERBY) ? "AS x" : "x";
        String finalQuery = """
                SELECT COUNT(*)
                FROM (
                    %s
                ) %s
                """.formatted(innerQuery, aliasPart);
        List<Object> params = List.of(repoKey);
        return jdbcHelper.executeSelectLong(finalQuery, params.toArray());
    }

    private String buildFinalQuery(String rootPath, long limit) {
        String aliasPart = dbProperties.getDbType().equals(DbType.DERBY) ? "AS x" : "x";
        String innerQuery = buildInnerQuery(rootPath, limit);
        return """
                SELECT COUNT(*)
                FROM (
                    %s
                ) %s
                """.formatted(innerQuery, aliasPart);
    }

    private String buildInnerQuery(String rootPath, long limit) {
        QueryWriter innerQuery = new QueryWriter(dbProperties.getDbType())
                .select(buildInnerQuerySelectPart(limit))
                .from(NODES_TABLE)
                .where(buildWherePart(rootPath));
        if (!dbProperties.getDbType().equals(DbType.MSSQL)) {
            innerQuery.limit(limit);
        }
        return innerQuery.build();
    }

    private String buildInnerQuerySelectPart(long limit) {
        return dbProperties.getDbType().equals(DbType.MSSQL) ? "TOP %s 1 AS a".formatted(limit) : "1";
    }

    private String buildWherePart(String repoPath) {
        return "repo = ? AND (node_path = ? OR node_path %s)".formatted(buildLikePart(repoPath));
    }

    private String buildLikePart(String rootPath) {
        String likePart = "LIKE ?";
        if (isPathContainsSpecialChars(rootPath)) {
            likePart = "LIKE ? ESCAPE '^'";
        }
        return likePart;
    }

    /**
     * Checks whether a repository has any content (files or folders) beyond its root folder.
     * Delegates to {@link #getNodesByRepo(String, int)} with a limit of 1.
     */
    public boolean isEmptyRepo(String repoKey) throws SQLException {
        return getNodesByRepo(repoKey, 1).isEmpty();
    }

    public long getFolderArtifactsCount(RepoPath folderRepoPath, List<String> params) throws SQLException {
        String repoKey = folderRepoPath.getRepoKey();
        String path = folderRepoPath.getPath();
        QueryWriter writer = new QueryWriter(dbProperties.getDbType());
        String subQueries = buildQueryWherePart(params.size());
        List<String> queryParameters = prepareNodesNames(params);
        writer.select("COUNT(*)").from(NODES_TABLE).where(subQueries + ") AND repo = ? AND node_path = ?");
        queryParameters.add(repoKey);
        queryParameters.add(path);
        return jdbcHelper.executeSelectLong(writer.build(), queryParameters.toArray());
    }

    public ResultSet lockNodeForWrite(long nodeId) throws SQLException {
        if (!DbType.MSSQL.equals(dbProperties.getDbType())) {
            return jdbcHelper.executeSelect(SELECT_NODE_FOR_UPDATE, nodeId);
        } else {
            return jdbcHelper.executeSelect(SELECT_NODE_FOR_UPDATE_MSSQL, nodeId);
        }
    }

    /**
     * Calculate the distinct binary size for the given repositories.
     * Only counts file nodes (node_type = 1), folders are excluded.
     *
     * @param repos   List of repository keys
     * @return sum of binary sizes (deduplicated by SHA1) in bytes
     * @throws StorageException if the calculation fails
     */
    public long getReposBinarySize(@NonNull Set<String> repos) throws StorageException {
        if (repos.isEmpty()) {
            return 0L;
        }
        String sql = dbProperties.getDbType().equals(DbType.DERBY) ?
                SELECT_NODES_SIZE_DERBY :
                SELECT_NODES_SIZE;
        try {
            return jdbcHelper.executeSelectLong(sql, repos);
        } catch (SQLException e) {
            throw new StorageException("Failed to calculate projects binary size", e);
        }
    }

    private List<String> prepareNodesNames(List<String> params) {
        return params.stream().map(nodeName -> "%" + nodeName).collect(Collectors.toList());
    }

    private String buildQueryWherePart(int size) {
        StringBuilder wherePart = new StringBuilder("(");
        for (int i = 0; i < size; i++) {
            if (i < size - 1) {
                wherePart.append("node_name LIKE ? OR ");
            } else {
                wherePart.append("node_name LIKE ?");
            }
        }
        return wherePart.toString();
    }

    /**
     * Paged full-sync entries ordered by {@code created}, then {@code node_id}.
     *
     * @param repo                 repository key
     * @param afterCreatedMillis   {@code null} on the first page; otherwise inclusive lower bound on
     *                             {@code nodes.created} for continuation (callers may deduplicate overlaps when many
     *                             rows share the same {@code created} millis)
     * @param limit                SQL limit (callers often pass {@code pageSize + 1} to detect a next page)
     */
    public List<FileListEntry> getFullSyncEntriesPaged(String repo, Long afterCreatedMillis, int limit)
            throws SQLException {
        String query = createFullSyncQueryWriter()
                .where("nodes.repo = ? AND nodes.created >= ?")
                .orderBy(DbType.MSSQL.equals(dbProperties.getDbType()) ?
                        "created ASC, node_id ASC" :
                        "nodes.created ASC, nodes.node_id ASC")
                .limit((long) limit)
                .build();

        Long created = afterCreatedMillis != null ? afterCreatedMillis : 0L;
        ArrayList<FileListEntry> entries = new ArrayList<>(limit);

        try (ResultSet rs = jdbcHelper.executeSelect(query, repo, created)) {
            while (rs.next()) {
                entries.add(fileListEntryFromResultSet(rs));
            }
        }

        return entries;
    }

    public List<FileListEntry> getFullSyncEntriesAll(String repo, int expectedEntryCount) throws SQLException {
        int listSizeMargin = 100;  // Arbitrary margin to prevent resizing the array when a few nodes are added.
        String query = createFullSyncQueryWriter().where("nodes.repo = ?").build();

        ArrayList<FileListEntry> entries = new ArrayList<>(expectedEntryCount + listSizeMargin);
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, repo)) {
            while (resultSet.next()) {
                entries.add(fileListEntryFromResultSet(resultSet));
            }
            return entries;
        }
    }

    private QueryWriter createFullSyncQueryWriter() {
        return new ArtifactoryQueryWriter()
                .select("""
                        nodes.node_id, nodes.node_type, nodes.repo, nodes.node_path, nodes.node_name, nodes.depth,
                        nodes.modified, nodes.created, nodes.sha1_actual, nodes.md5_actual, nodes.bin_length,
                        nodes.sha256, node_meta_infos.props_modified, node_meta_infos.props_md5
                        """)
                .from("nodes ")  // Added a space to work around a bug in QueryBuilder.
                .leftJoin("node_meta_infos", "nodes.node_id = node_meta_infos.node_id");
    }

    private FileListEntry fileListEntryFromResultSet(ResultSet resultSet) throws SQLException {
        FileListEntry entry = new FileListEntry();
        entry.type = (resultSet.getBoolean("node_type") ? AqlItemTypeEnum.file : AqlItemTypeEnum.folder).signature;
        entry.repo = resultSet.getString("repo");
        entry.path = emptyIfNullOrDot(resultSet.getString(NODE_PATH_COLUMN));
        entry.name = emptyIfNullOrDot(resultSet.getString("node_name"));
        entry.depth = resultSet.getInt("depth");
        entry.modified = new DateTime(resultSet.getLong("modified"), DateTimeZone.UTC).toString();
        entry.created = new DateTime(resultSet.getLong("created"), DateTimeZone.UTC).toString();
        entry.sha256 = resultSet.getString("sha256");
        entry.actualSha1 = resultSet.getString("sha1_actual");
        entry.actualMd5 = resultSet.getString("md5_actual");
        entry.size = resultSet.getObject("bin_length") != null ? resultSet.getLong("bin_length") : null;
        entry.itemPropsMd5 = resultSet.getString("props_md5");
        entry.itemPropsModified = new DateTime(resultSet.getLong("props_modified"), DateTimeZone.UTC).toString();
        return entry;
    }

    /**
     * Check if a folder contains specified files and fetch their properties in a single query.
     * This optimizes file checking by combining the existence check with property fetching,
     * reducing from multiple SQL queries to just 1 query.
     *
     * @param repoPath  The path to check
     * @param fileNames The file names to look for (e.g., "manifest.json", "list.manifest.json")
     * @return Map of file name to list of its node properties, empty if no files found
     * @throws SQLException if database access fails
     */
    @Nonnull
    public Map<String, List<NodeProperty>> getPropertiesForFiles(@Nonnull RepoPath repoPath,
            @Nonnull List<String> fileNames) throws SQLException {
        if (fileNames.isEmpty()) {
            return new HashMap<>();
        }

        List<Object> parameters = new ArrayList<>();
        parameters.add(repoPath.getRepoKey());
        parameters.add(repoPath.getPath());
        parameters.addAll(fileNames);

        String fileNamesIn = String.join(",", Collections.nCopies(fileNames.size(), "?"));
        String query = """
                SELECT
                  n.node_name,
                  np.prop_id,
                  np.node_id,
                  np.prop_key,
                  np.prop_value
                FROM nodes n
                LEFT JOIN node_props np ON np.node_id = n.node_id
                WHERE n.repo = ?
                  AND n.node_path = ?
                  AND n.node_name IN (%s)
                  AND n.node_type = 1
                """.formatted(fileNamesIn);

        Map<String, List<NodeProperty>> results = new HashMap<>();
        try (ResultSet rs = jdbcHelper.executeSelect(query, parameters.toArray())) {
            while (rs.next()) {
                String fileName = rs.getString("node_name");
                long propertyId = rs.getLong("prop_id");
                results.computeIfAbsent(fileName, k -> new ArrayList<>());
                // propertyId will be 0 if no properties, due to LEFT JOIN
                if (propertyId > 0) {
                    String propertyKey = rs.getString("prop_key");
                    String propertyValue = rs.getString("prop_value");
                    long nodeId = rs.getLong(NODE_ID_COLUMN);
                    NodeProperty nodeProperty = new NodeProperty(propertyId, nodeId, propertyKey, propertyValue);
                    results.get(fileName).add(nodeProperty);
                }
            }
        }
        return results;
    }

    private static final Pattern SHA1_PATTERN = Pattern.compile("^[a-fA-F0-9]{40}$");

    /**
     * Finds SHA1s still referenced in nodes (excluding auto-trashcan).
     * Uses per-DB optimized queries. Failed batches are logged and skipped.
     *
     * @param sha1List list of SHA1 checksums to check (must be valid 40-char hex strings)
     * @return set of SHA1s that are still referenced in nodes
     */
    // TODO RTDEV-83177: Add retry and reconciliation for failed batches
    @Nonnull
    public Set<String> findReferencedSha1s(@Nonnull List<String> sha1List) {
        if (sha1List.isEmpty()) {
            return Collections.emptySet();
        }

        List<String> validatedSha1s = validateSha1List(sha1List);
        if (validatedSha1s.isEmpty()) {
            return Collections.emptySet();
        }

        int batchSize = ConstantValues.physicalSizeQueryBatchSize.getInt();
        DbType dbType = dbProperties.getDbType();

        Set<String> referenced = new HashSet<>();
        List<List<String>> chunks = Lists.partition(validatedSha1s, batchSize);

        for (int i = 0; i < chunks.size(); i++) {
            try {
                referenced.addAll(executeFindReferencedQuery(chunks.get(i), dbType));
            } catch (SQLException e) {
                // TODO RTDEV-83177: Failed batches cause physical size to be underestimated.
                //  Add retry logic and reconciliation mechanism to track failed SHA1s for later reprocessing.
                log.error("Failed to query referenced SHA1s for batch {}/{} ({} SHA1s), skipping batch", i + 1,
                        chunks.size(), chunks.get(i).size(), e);
            }
        }
        return referenced;
    }

    /**
     * Validates SHA1 list to ensure all values are valid 40-character hex strings.
     * Invalid SHA1s are logged and filtered out to prevent SQL injection.
     */
    private List<String> validateSha1List(List<String> sha1List) {
        List<String> valid = new ArrayList<>(sha1List.size());
        for (String sha1 : sha1List) {
            if (sha1 != null && SHA1_PATTERN.matcher(sha1).matches()) {
                valid.add(sha1);
            } else {
                log.warn("Invalid SHA1 format rejected: {}", sha1);
            }
        }
        return valid;
    }

    private Set<String> executeFindReferencedQuery(List<String> sha1Chunk, DbType dbType) throws SQLException {
        String query = buildFindReferencedQuery(sha1Chunk.size(), dbType);
        Object[] params = buildQueryParams(sha1Chunk, dbType);

        Set<String> results = new HashSet<>();
        try (ResultSet rs = jdbcHelper.executeSelect(query, params)) {
            while (rs.next()) {
                String sha1 = rs.getString(1);
                if (sha1 != null) {
                    results.add(sha1.trim());
                }
            }
        }
        return results;
    }

    private String buildFindReferencedQuery(int sha1Count, DbType dbType) {
        return switch (dbType) {
            case POSTGRESQL -> buildPostgresQuery();
            case MYSQL, MARIADB -> buildUnionAllQuery(sha1Count);
            case MSSQL -> buildMssqlQuery();
            case ORACLE -> buildOracleQuery();
            default -> buildDerbyQuery(sha1Count);
        };
    }

    /**
     * Builds query parameters for the findReferencedSha1s query.
     * All queries now include TRASH_REPO_KEY as a parameterized value to prevent SQL injection.
     * SHA1 values are pre-validated by {@link #validateSha1List(List)} before reaching this method.
     */
    private Object[] buildQueryParams(List<String> sha1Chunk, DbType dbType) {
        return switch (dbType) {
            case POSTGRESQL -> {
                String pgArray = "{\"" + String.join("\",\"", sha1Chunk) + "\"}";
                yield new Object[] {pgArray, TRASH_REPO_KEY};
            }
            case MSSQL, ORACLE -> {
                String jsonArray =
                        "[" + sha1Chunk.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",")) + "]";
                yield new Object[] {jsonArray, TRASH_REPO_KEY};
            }
            default -> {
                Object[] params = new Object[sha1Chunk.size() + 1];
                for (int i = 0; i < sha1Chunk.size(); i++) {
                    params[i] = sha1Chunk.get(i);
                }
                params[sha1Chunk.size()] = TRASH_REPO_KEY;
                yield params;
            }
        };
    }

    private String buildPostgresQuery() {
        return "SELECT v.sha1 FROM unnest(?::char(40)[]) AS v(sha1) WHERE EXISTS (" +
                "SELECT 1 FROM nodes n WHERE n.sha1_actual = v.sha1 " + "AND n.repo <> ?)";
    }

    /**
     * Builds query using UNION ALL for MySQL/MariaDB.
     * UNION ALL is used instead of JSON_TABLE for portability with MySQL < 8.0 and MariaDB versions
     * that have limited or no JSON_TABLE support.
     */
    private String buildUnionAllQuery(int sha1Count) {
        List<String> selects = new ArrayList<>(sha1Count);
        for (int i = 0; i < sha1Count; i++) {
            selects.add("SELECT CAST(? AS CHAR(40)) AS sha1");
        }
        return "SELECT v.sha1 FROM (" + String.join(" UNION ALL ", selects) + ") AS v WHERE EXISTS (" +
                "SELECT 1 FROM nodes n " + "WHERE n.sha1_actual = v.sha1 " + "AND n.repo <> ?)";
    }

    private String buildMssqlQuery() {
        return "SELECT j.sha1 FROM OPENJSON(?) " + "WITH (sha1 CHAR(40) '$') AS j " + "WHERE EXISTS (" +
                "SELECT 1 FROM nodes n " + "WHERE n.sha1_actual = " + "CAST(j.sha1 AS CHAR(40)) " + "AND n.repo <> ?)";
    }

    private String buildOracleQuery() {
        return "SELECT j.sha1 FROM JSON_TABLE(" + "?, '$[*]' COLUMNS " + "(sha1 VARCHAR2(40) PATH '$')" +
                ") j WHERE EXISTS (" + "SELECT 1 FROM nodes n " + "WHERE n.sha1_actual = " +
                "CAST(j.sha1 AS CHAR(40)) " + "AND n.repo <> ?)";
    }

    private String buildDerbyQuery(int sha1Count) {
        List<String> valueRows = new ArrayList<>(sha1Count);
        for (int i = 0; i < sha1Count; i++) {
            valueRows.add("(CAST(? AS CHAR(40)))");
        }
        return "SELECT v.sha1 FROM " + "(VALUES " + String.join(",", valueRows) + ") AS v(sha1) WHERE EXISTS (" +
                "SELECT 1 FROM nodes n " + "WHERE n.sha1_actual = v.sha1 " + "AND n.repo <> ?)";
    }
}
