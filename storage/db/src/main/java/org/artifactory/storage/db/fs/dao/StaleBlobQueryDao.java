package org.artifactory.storage.db.fs.dao;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.common.ResultSetStream;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.nodesStreamingDbFetchPageSize;
import static org.artifactory.storage.db.util.ArtifactoryDbUtils.isSupportsStreaming;

/**
 * DAO for finding "stale" blob nodes whose parent directory contains no manifest file.
 * Uses a {@code NOT EXISTS} subquery that leverages the {@code nodes_repo_path_name_idx}
 * unique index for efficient per-row manifest lookup, combined with the
 * {@code nodes_repo_created_idx} composite index for time-interval range scans.
 * <p>
 * Results are streamed via {@link ArtifactoryJdbcHelper#executeSelectStreamed} to keep
 * memory bounded regardless of result-set size.
 *
 * @author Tomer Nir
 */
@Slf4j
@Repository
public class StaleBlobQueryDao extends BaseDao {

    private static final String BLOB_COLUMNS = """
            b.node_id, b.node_type, b.repo, b.node_path, b.node_name,
            b.depth, b.created, b.created_by, b.modified, b.modified_by,
            b.updated, b.bin_length, b.sha1_actual, b.sha1_original,
            b.md5_actual, b.md5_original, b.sha256""";

    private static final String BLOB_NAME_FILTER = "b.node_name LIKE 'sha256^_^_%' ESCAPE '^'";
    private static final String UPLOADS_EXCLUSION = "b.node_path NOT LIKE '%/^_uploads' ESCAPE '^'";
    private static final String NOT_EXISTS_MANIFEST =
            "NOT EXISTS (SELECT 1 FROM nodes m WHERE m.repo = b.repo"
                    + " AND m.node_path = b.node_path AND (m.node_name = ? OR m.node_name = ?))";
    private static final String PATH_FILTER = "b.node_path LIKE ? ESCAPE '^'";

    private final DbType dbType;
    private final NodesDao nodesDao;

    @Autowired
    public StaleBlobQueryDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties,
            NodesDao nodesDao) {
        super(jdbcHelper);
        this.dbType = dbProperties.getDbType();
        this.nodesDao = nodesDao;
    }

    /**
     * Streams blob nodes whose parent directory contains no manifest file.
     * The caller is responsible for closing the returned stream (use try-with-resources).
     *
     * @param repoKey         repository key
     * @param createdFrom     inclusive lower bound on {@code created} (epoch ms)
     * @param createdTo       exclusive upper bound on {@code created} (epoch ms)
     * @param pathPrefix      optional path prefix filter (e.g. {@code "library/nginx"});
     *                        when non-null, only blobs under this path prefix are returned
     * @param manifestName    primary manifest file name (e.g. {@code "manifest.json"})
     * @param listManifestName list manifest file name (e.g. {@code "list.manifest.json"})
     * @return a lazily-streamed {@code Stream<Node>} of stale blob nodes
     */
    @Nonnull
    public Stream<Node> streamStaleBlobs(String repoKey, long createdFrom, long createdTo,
            @Nullable String pathPrefix, String manifestName, String listManifestName) throws SQLException {
        QueryWriter query = new QueryWriter(dbType);
        query.select(BLOB_COLUMNS)
                .from("nodes b")
                .where("b.repo = ? AND b.created >= ? AND b.created < ? AND "
                        + BLOB_NAME_FILTER + " AND " + UPLOADS_EXCLUSION + " AND " + NOT_EXISTS_MANIFEST
                        + (StringUtils.isNotBlank(pathPrefix) ? " AND " + PATH_FILTER : ""));

        Object[] params;
        if (StringUtils.isNotBlank(pathPrefix)) {
            params = new Object[]{repoKey, createdFrom, createdTo, manifestName, listManifestName,
                    toPathLikePattern(pathPrefix)};
        } else {
            params = new Object[]{repoKey, createdFrom, createdTo, manifestName, listManifestName};
        }

        log.debug("Streaming stale blobs for repo '{}' (created [{}, {}), pathPrefix={})",
                repoKey, createdFrom, createdTo, pathPrefix);

        ResultSet rs = jdbcHelper.executeSelectStreamed(getQueryControls(), query.build(), params);
        CompletableFuture<SQLException> onException = futureBounceSqlExceptionToStorageException();
        return ResultSetStream.asStream(rs, nodesDao::nodeFromResultSet, onException);
    }

    /**
     * Finds the {@code created} timestamp of the oldest sha256__ blob in the repository,
     * optionally scoped to a path prefix.
     *
     * @param repoKey    repository key
     * @param pathPrefix optional path prefix filter; when non-null, only blobs under this
     *                   path prefix are considered
     * @return the {@code created} timestamp of the oldest matching blob, or empty if none exist
     */
    public OptionalLong getOldestBlobCreated(String repoKey,
            @Nullable String pathPrefix) throws SQLException {
        QueryWriter query = new QueryWriter(dbType);
        query.select("MIN(b.created) AS created")
                .from("nodes b")
                .where("b.repo = ? AND " + BLOB_NAME_FILTER + " AND " + UPLOADS_EXCLUSION
                        + (StringUtils.isNotBlank(pathPrefix) ? " AND " + PATH_FILTER : ""));

        Object[] params;
        if (StringUtils.isNotBlank(pathPrefix)) {
            params = new Object[]{repoKey, toPathLikePattern(pathPrefix)};
        } else {
            params = new Object[]{repoKey};
        }

        try (ResultSet rs = jdbcHelper.executeSelect(query.build(), params)) {
            if (rs.next()) {
                long created = rs.getLong("created");
                if (rs.wasNull()) {
                    return OptionalLong.empty();
                }
                return OptionalLong.of(created);
            }
            return OptionalLong.empty();
        }
    }

    private QueryControls getQueryControls() {
        QueryControls.QueryControlsBuilder builder = QueryControls.builder();
        if (isSupportsStreaming(dbType)) {
            builder.fetchSize(nodesStreamingDbFetchPageSize.getInt());
        }
        return builder.build();
    }

    /**
     * Normalizes a path prefix into a SQL LIKE pattern, stripping any trailing slashes and
     * escaping SQL LIKE wildcards ({@code %}, {@code _}) and the escape character ({@code ^}).
     */
    private static String toPathLikePattern(String pathPrefix) {
        String normalized = pathPrefix.endsWith("/")
                ? pathPrefix.substring(0, pathPrefix.length() - 1) : pathPrefix;
        String escaped = normalized.replace("^", "^^")
                .replace("%", "^%")
                .replace("_", "^_");
        return escaped + "/%";
    }

    private CompletableFuture<SQLException> futureBounceSqlExceptionToStorageException() {
        CompletableFuture<SQLException> onException = new CompletableFuture<>();
        onException.thenAccept(e -> {
            throw new StorageException("Failed to stream stale blob nodes", e);
        });
        return onException;
    }
}
