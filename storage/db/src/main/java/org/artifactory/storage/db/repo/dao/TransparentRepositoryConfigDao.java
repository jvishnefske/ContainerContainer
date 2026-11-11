package org.artifactory.storage.db.repo.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.entity.DbRepoKeyConfigBlob;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.common.ResultSetStream;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.SqlDaoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.artifactory.storage.db.util.ArtifactoryDbUtils.isSupportsStreaming;

/**
 * DAO for the transparent_repo_config table. Transparent repos reserve a repository key without
 * participating in active repository flows (caching, indexing, sync, etc.).
 */
@Slf4j
@Repository
public class TransparentRepositoryConfigDao {

    private static final String INSERT_SQL =
            "INSERT INTO transparent_repo_config "
                    + "(repository_key, type, package_type, revision, created, modified, config_blob) "
                    + "VALUES(?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_KEY_SQL =
            "SELECT repository_key, type, package_type, revision, created, modified, config_blob "
                    + "FROM transparent_repo_config WHERE repository_key = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT repository_key, type, package_type, revision, created, modified, config_blob "
                    + "FROM transparent_repo_config";
    private static final String SELECT_KEY_AND_BLOB_SQL =
            "SELECT " + RepositoryConfigDao.REPO_KEY_COLUMN + ", "
                    + RepositoryConfigDao.TYPE_COLUMN + ", "
                    + RepositoryConfigDao.PACKAGE_TYPE_COLUMN + ", "
                    + RepositoryConfigDao.CONFIG_BLOB_COLUMN
                    + " FROM transparent_repo_config";
    private static final String SELECT_ALL_KEYS_LOWER_SQL =
            "SELECT LOWER(repository_key) FROM transparent_repo_config";
    private static final String EXISTS_SQL =
            "SELECT count(1) FROM transparent_repo_config WHERE repository_key = ?";
    private static final String EXISTS_IGNORE_CASE_SQL =
            "SELECT count(1) FROM transparent_repo_config WHERE LOWER(repository_key) = LOWER(?)";
    private static final String UPDATE_SQL =
            "UPDATE transparent_repo_config "
                    + "SET type = ?, package_type = ?, revision = ?, modified = ?, config_blob = ? "
                    + "WHERE repository_key = ?";
    private static final String DELETE_SQL =
            "DELETE FROM transparent_repo_config WHERE repository_key = ?";

    private static final String TRANSPARENT_REPO_CONFIG_TABLE = "transparent_repo_config";
    private static final String REPO_KEY_COLUMN = "repository_key";

    private final ArtifactoryJdbcHelper jdbcHelper;
    private final SqlDaoHelper<DbRepositoryConfig> daoHelper;
    private final QueryControls gridStreamingControls;

    @Autowired
    public TransparentRepositoryConfigDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.jdbcHelper = jdbcHelper;
        // Reuses the active-config batch size — the schema mirrors repository_config so the same
        // tuning applies.
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                TRANSPARENT_REPO_CONFIG_TABLE, this::fromResultSet,
                ConstantValues.repoConfigDbBatchSize.getInt());
        var gridControlsBuilder = QueryControls.builder();
        if (isSupportsStreaming(dbProperties.getDbType())) {
            gridControlsBuilder.fetchSize(ConstantValues.gridRepoInventoryStreamFetchSize.getInt());
        }
        this.gridStreamingControls = gridControlsBuilder.build();
    }

    public void insert(DbRepositoryConfig config) throws SQLException {
        jdbcHelper.executeUpdate(INSERT_SQL,
                config.getKey(),
                config.getType(),
                config.getPackageType(),
                config.getRevision(),
                config.getCreated(),
                config.getModified(),
                config.getRepositoryInfoBlob());
    }

    public Optional<DbRepositoryConfig> findByKey(String repoKey) throws SQLException {
        try (ResultSet rs = jdbcHelper.executeSelect(SELECT_BY_KEY_SQL, repoKey)) {
            if (rs.next()) {
                return Optional.of(fromResultSet(rs));
            }
        }
        return Optional.empty();
    }

    public List<DbRepositoryConfig> getAll() throws SQLException {
        List<DbRepositoryConfig> result = new ArrayList<>();
        try (ResultSet rs = jdbcHelper.executeSelect(SELECT_ALL_SQL)) {
            while (rs.next()) {
                result.add(fromResultSet(rs));
            }
        }
        return result;
    }

    /**
     * Streams repository keys and config blobs in unspecified order. The caller must close the returned stream.
     */
    public Stream<DbRepoKeyConfigBlob> streamKeyAndConfigBlob() throws SQLException {
        CompletableFuture<SQLException> onException = futureBounceSqlExceptionToStorageException();
        ResultSet resultSet = jdbcHelper.executeSelectStreamed(gridStreamingControls, SELECT_KEY_AND_BLOB_SQL);
        return ResultSetStream.asStream(resultSet, this::keyAndConfigBlobFromResultSet, onException);
    }

    private DbRepoKeyConfigBlob keyAndConfigBlobFromResultSet(ResultSet resultSet) throws SQLException {
        return new DbRepoKeyConfigBlob(
                resultSet.getString(RepositoryConfigDao.REPO_KEY_COLUMN),
                resultSet.getString(RepositoryConfigDao.TYPE_COLUMN),
                resultSet.getString(RepositoryConfigDao.PACKAGE_TYPE_COLUMN),
                resultSet.getBytes(RepositoryConfigDao.CONFIG_BLOB_COLUMN));
    }

    private CompletableFuture<SQLException> futureBounceSqlExceptionToStorageException() {
        CompletableFuture<SQLException> onException = new CompletableFuture<>();
        onException.thenAccept(e -> {
            throw new StorageException("Failed to stream transparent repository config inventory rows", e);
        });
        return onException;
    }

    public Set<String> findAllKeysLowerCase() throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet rs = jdbcHelper.executeSelect(SELECT_ALL_KEYS_LOWER_SQL)) {
            while (rs.next()) {
                keys.add(rs.getString(1));
            }
        }
        return keys;
    }

    public boolean existsByKey(String repoKey) throws SQLException {
        int count = jdbcHelper.executeSelectCount(EXISTS_SQL, repoKey);
        if (count > 1) {
            log.warn("Unexpected transparent repositories count for key: '{}' - {}", repoKey, count);
        }
        return count > 0;
    }

    public boolean existsByKeyIgnoreCase(String repoKey) throws SQLException {
        return jdbcHelper.executeSelectCount(EXISTS_IGNORE_CASE_SQL, repoKey) > 0;
    }

    public int update(DbRepositoryConfig config) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE_SQL,
                config.getType(),
                config.getPackageType(),
                config.getRevision(),
                config.getModified(),
                config.getRepositoryInfoBlob(),
                config.getKey());
    }

    public int delete(String repoKey) throws SQLException {
        return jdbcHelper.executeUpdate(DELETE_SQL, repoKey);
    }

    public void bulkInsert(List<DbRepositoryConfig> configs) throws SQLException {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        List<Object[]> listOfParams = new ArrayList<>(configs.size());
        for (DbRepositoryConfig config : configs) {
            listOfParams.add(new Object[] {
                    config.getKey(),
                    config.getType(),
                    config.getPackageType(),
                    config.getRevision(),
                    config.getCreated(),
                    config.getModified(),
                    config.getRepositoryInfoBlob()
            });
        }
        jdbcHelper.executeBulkUpdate(INSERT_SQL, listOfParams);
    }

    public void bulkDelete(List<String> repoKeys) throws SQLException {
        if (repoKeys == null || repoKeys.isEmpty()) {
            return;
        }
        List<Object[]> listOfParams = new ArrayList<>(repoKeys.size());
        for (String repoKey : repoKeys) {
            listOfParams.add(new Object[] {repoKey});
        }
        jdbcHelper.executeBulkUpdate(DELETE_SQL, listOfParams);
    }

    public List<DbRepositoryConfig> findByKeys(List<String> repoKeys) throws SQLException {
        if (repoKeys == null || repoKeys.isEmpty()) {
            return List.of();
        }
        // Paginates the IN-clause so we stay below DB-specific parameter limits (Oracle ~1000 etc.).
        // Mirrors RepositoryConfigDao#findByKeysWithPagination — same helper, same arg shape.
        return daoHelper.findByMultiColumnAndInClauseWithPagination(
                "*", TRANSPARENT_REPO_CONFIG_TABLE,
                List.of(), List.of(),
                REPO_KEY_COLUMN, new HashSet<>(repoKeys));
    }

    private DbRepositoryConfig fromResultSet(ResultSet rs) throws SQLException {
        DbRepositoryConfig config = new DbRepositoryConfig();
        config.setKey(rs.getString(RepositoryConfigDao.REPO_KEY_COLUMN));
        config.setType(rs.getString(RepositoryConfigDao.TYPE_COLUMN));
        config.setPackageType(rs.getString(RepositoryConfigDao.PACKAGE_TYPE_COLUMN));
        config.setRevision(rs.getLong(RepositoryConfigDao.REVISION_COLUMN));
        config.setCreated(rs.getLong(RepositoryConfigDao.CREATED_COLUMN));
        config.setModified(rs.getLong(RepositoryConfigDao.MODIFIED_COLUMN));
        config.setRepositoryInfoBlob(rs.getBytes(RepositoryConfigDao.CONFIG_BLOB_COLUMN));
        return config;
    }
}
