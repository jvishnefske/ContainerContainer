package org.artifactory.storage.db.repo.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.repo.entity.DbRepoKeyConfigBlob;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.common.ResultSetStream;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.SqlDaoHelper;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.storage.db.util.ArtifactoryDbUtils.isSupportsStreaming;

import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.model.LocalCacheRepoConfig.PATH_SUFFIX;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.storage.db.fs.dao.NodesDao.NODES_TABLE;
import static org.artifactory.storage.db.fs.dao.NodesDao.REPO_COLUMN_NAME;

/**
 * @author Tamar Rosen
 */
@Slf4j
@Repository
public class RepositoryConfigDao {
    public static final String REPO_CONFIG_TABLE = "repository_config";
    public static final String REPO_KEY_COLUMN = "repository_key";
    public static final String TYPE_COLUMN = "type";
    public static final String REPO_TYPE_V2_COLUMN = "repo_type_v2";
    public static final String PACKAGE_TYPE_COLUMN = "package_type";
    public static final String REVISION_COLUMN = "revision";
    public static final String CREATED_COLUMN = "created";
    public static final String MODIFIED_COLUMN = "modified";
    public static final String CONFIG_BLOB_COLUMN = "config_blob";

    private static final String[] COLUMNS = {REPO_KEY_COLUMN, TYPE_COLUMN, REPO_TYPE_V2_COLUMN, PACKAGE_TYPE_COLUMN,
            REVISION_COLUMN, CREATED_COLUMN, MODIFIED_COLUMN, CONFIG_BLOB_COLUMN};
    private static final String TABLE_NAME_AND_FIELDS = REPO_CONFIG_TABLE
            + " (" + String.join(",", COLUMNS) + ")";
    private static final List<String> COLUMNS_AS_UPDATE_SET = Arrays.stream(COLUMNS)
            .filter(column -> !column.equals(CREATED_COLUMN) && !column.equals(REPO_KEY_COLUMN))
            .map(column -> column + " = ?")
            .collect(Collectors.toList());
    private static final String INSERT_REPO_SQL = "INSERT INTO "
            + REPO_CONFIG_TABLE + " (" + String.join(",", COLUMNS) + ") VALUES("
            + String.join(", ", Collections.nCopies(COLUMNS.length, "?")) + ")";
    private static final String UPDATE_REPO_CONFIG_SQL = "UPDATE " + REPO_CONFIG_TABLE + " SET "
            + String.join(", ", COLUMNS_AS_UPDATE_SET) + " WHERE " + REPO_KEY_COLUMN + " = ?";
    private static final String DELETE_REPO_SQL = "DELETE FROM "
            + REPO_CONFIG_TABLE + " WHERE " + REPO_KEY_COLUMN + " = ?";
    private static final String REPO_EXISTS_SQL = "SELECT count(1) FROM "
            + REPO_CONFIG_TABLE + " WHERE " + REPO_KEY_COLUMN + " = ?";
    private static final String REPO_KEYS_EXISTS_SQL = "SELECT " + REPO_KEY_COLUMN + " FROM "
            + REPO_CONFIG_TABLE + " WHERE " + REPO_KEY_COLUMN + " IN (#)";
    private static final String[] BASIC_COLUMNS = {REPO_KEY_COLUMN, TYPE_COLUMN, PACKAGE_TYPE_COLUMN, MODIFIED_COLUMN};
    private static final String SELECT_BASIC_REPO_SQL = "SELECT " +
            String.join(",", BASIC_COLUMNS) + " FROM " + REPO_CONFIG_TABLE;
    private static final String SELECT_KEY_AND_BLOB_SQL = "SELECT " + REPO_KEY_COLUMN + ", "
            + TYPE_COLUMN + ", " + PACKAGE_TYPE_COLUMN + ", " + CONFIG_BLOB_COLUMN + " FROM " + REPO_CONFIG_TABLE;

    private final SqlDaoHelper<DbRepositoryConfig> daoHelper;
    private final ArtifactoryJdbcHelper jdbcHelper;
    private final QueryControls controls;
    private final QueryControls gridStreamingControls;
    private final ArtifactoryDbProperties dbProperties;

    @Autowired
    public RepositoryConfigDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.jdbcHelper = jdbcHelper;
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                REPO_CONFIG_TABLE, this::getFullDbRepoConfigFromResultSet,
                ConstantValues.repoConfigDbBatchSize.getInt());
        var controlsBuilder = QueryControls.builder()
                .networkTimeout(ConstantValues.repoConfigCacheSyncKeysDbQueryTimeout.getInt());
        if (ConstantValues.repositoryDbFetchFetchSize.getInt() > 0) {
            controlsBuilder.fetchSize(ConstantValues.repositoryDbFetchFetchSize.getInt());
        }
        this.controls = controlsBuilder.build();
        this.dbProperties = dbProperties;
        var gridControlsBuilder = QueryControls.builder();
        if (isSupportsStreaming(dbProperties.getDbType())) {
            gridControlsBuilder.fetchSize(ConstantValues.gridRepoInventoryStreamFetchSize.getInt());
        }
        this.gridStreamingControls = gridControlsBuilder.build();
    }

    public void insert(DbRepositoryConfig dbRepositoryConfig) throws SQLException {
        jdbcHelper.executeUpdate(INSERT_REPO_SQL,
                dbRepositoryConfig.getKey(),
                dbRepositoryConfig.getType(),
                dbRepositoryConfig.getRepoTypeV2(),
                dbRepositoryConfig.getPackageType(),
                dbRepositoryConfig.getRevision(),
                dbRepositoryConfig.getCreated(),
                dbRepositoryConfig.getModified(),
                dbRepositoryConfig.getRepositoryInfoBlob());
    }

    public void bulkInsert(List<DbRepositoryConfig> dbRepositoryConfigs) throws SQLException {
        daoHelper.paginationCreate(TABLE_NAME_AND_FIELDS, COLUMNS.length,
                dbRepositoryConfig -> Stream.of(
                        dbRepositoryConfig.getKey(),
                        dbRepositoryConfig.getType(),
                        dbRepositoryConfig.getRepoTypeV2(),
                        dbRepositoryConfig.getPackageType(),
                        dbRepositoryConfig.getRevision(),
                        dbRepositoryConfig.getCreated(),
                        dbRepositoryConfig.getModified(),
                        dbRepositoryConfig.getRepositoryInfoBlob()),
                dbRepositoryConfigs.toArray(new DbRepositoryConfig[0]));
    }

    public void bulkUpdate(List<DbRepositoryConfig> dbRepositoryConfigs) throws SQLException {
        List<Object[]> listOfParams = new ArrayList<>();
        for (DbRepositoryConfig dbRepositoryConfig : dbRepositoryConfigs) {
            Object[] params = dbRepositoryConfigToUpdateParams(dbRepositoryConfig);
            listOfParams.add(params);
        }
        jdbcHelper.executeBulkUpdate(UPDATE_REPO_CONFIG_SQL, listOfParams);
    }

    public Optional<DbRepositoryConfig> findRepositoryByKey(String repoKey) throws SQLException {
        return daoHelper.findFirstByProperty(REPO_KEY_COLUMN, repoKey);
    }

    public void update(DbRepositoryConfig dbRepositoryConfig) throws SQLException {
        jdbcHelper.executeUpdate(UPDATE_REPO_CONFIG_SQL, dbRepositoryConfigToUpdateParams(dbRepositoryConfig));
    }

    public int delete(String repoKey) throws SQLException {
        return daoHelper.deleteByProperty(REPO_KEY_COLUMN, repoKey);
    }

    public void bulkDelete(List<String> repoKeys) throws SQLException {
        List<Object[]> listOfParams = new ArrayList<>();
        for (String repoKey : repoKeys) {
            Object[] params = new Object[] {repoKey};
            listOfParams.add(params);
        }
        jdbcHelper.executeBulkUpdate(DELETE_REPO_SQL, listOfParams);
    }

    public boolean exists(String repoKey) throws SQLException {
        int count = jdbcHelper.executeSelectCount(REPO_EXISTS_SQL, repoKey);
        if (count > 1) {
            log.warn("Unexpected repositories count for repository key: '{}' - {}", repoKey, count);
        }
        return count > 0;
    }

    public List<DbRepositoryConfig> getAll() throws SQLException {
        return daoHelper.getAll();
    }

    public Set<DbRepositoryConfig> findByKeysWithPagination(Set<String> repoKeys) throws SQLException {
        return Set.copyOf(daoHelper.findByMultiColumnAndInClauseWithPagination("*", REPO_CONFIG_TABLE,
                List.of(), List.of(), REPO_KEY_COLUMN, repoKeys));
    }

    public int deleteAll() throws SQLException {
        return daoHelper.deleteAll();
    }

    public int countAll() throws SQLException {
        return daoHelper.executeCount("SELECT COUNT(*) FROM " + REPO_CONFIG_TABLE);
    }

    private DbRepositoryConfig getFullDbRepoConfigFromResultSet(ResultSet resultSet) throws SQLException {
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey(resultSet.getString(REPO_KEY_COLUMN));
        dbRepositoryConfig.setType(resultSet.getString(TYPE_COLUMN));
        dbRepositoryConfig.setPackageType(resultSet.getString(PACKAGE_TYPE_COLUMN));
        dbRepositoryConfig.setRevision(resultSet.getLong(REVISION_COLUMN));
        dbRepositoryConfig.setCreated(resultSet.getLong(CREATED_COLUMN));
        dbRepositoryConfig.setModified(resultSet.getLong(MODIFIED_COLUMN));
        dbRepositoryConfig.setRepositoryInfoBlob(resultSet.getBytes(CONFIG_BLOB_COLUMN));
        dbRepositoryConfig.setRepoTypeV2(resultSet.getString(REPO_TYPE_V2_COLUMN));
        return dbRepositoryConfig;
    }

    private DbRepositoryConfig getBasicDbRepoConfigFromResultSet(ResultSet resultSet) throws SQLException {
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey(resultSet.getString(REPO_KEY_COLUMN));
        dbRepositoryConfig.setType(resultSet.getString(TYPE_COLUMN));
        dbRepositoryConfig.setPackageType(resultSet.getString(PACKAGE_TYPE_COLUMN));
        dbRepositoryConfig.setModified(resultSet.getLong(MODIFIED_COLUMN));
        return dbRepositoryConfig;
    }

    private Object[] dbRepositoryConfigToUpdateParams(DbRepositoryConfig dbRepositoryConfig) {
        return new Object[] {
                dbRepositoryConfig.getType(),
                dbRepositoryConfig.getRepoTypeV2(),
                dbRepositoryConfig.getPackageType(),
                dbRepositoryConfig.getRevision(),
                dbRepositoryConfig.getModified(),
                dbRepositoryConfig.getRepositoryInfoBlob(),
                dbRepositoryConfig.getKey()
        };
    }

    public Set<DbRepositoryConfig> getAllBasicRepoConfigs() throws SQLException {
        Set<DbRepositoryConfig> data = new HashSet<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(controls, SELECT_BASIC_REPO_SQL)) {
            while (resultSet.next()) {
                data.add(getBasicDbRepoConfigFromResultSet(resultSet));
            }
        }
        return data;
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
                resultSet.getString(REPO_KEY_COLUMN),
                resultSet.getString(TYPE_COLUMN),
                resultSet.getString(PACKAGE_TYPE_COLUMN),
                resultSet.getBytes(CONFIG_BLOB_COLUMN));
    }

    private CompletableFuture<SQLException> futureBounceSqlExceptionToStorageException() {
        CompletableFuture<SQLException> onException = new CompletableFuture<>();
        onException.thenAccept(e -> {
            throw new StorageException("Failed to stream repository config inventory rows", e);
        });
        return onException;
    }

    public Collection<DbRepositoryConfig> findRepositoriesByDetailsType(String type) throws SQLException {
        return daoHelper.findAllByProperty(TYPE_COLUMN, type);
    }

    public Set<String> findKeysFromSet(Set<String> repoKeys) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(controls, REPO_KEYS_EXISTS_SQL, repoKeys)) {
            while (resultSet.next()) {
                keys.add(resultSet.getString(1));
            }
        }
        return keys;
    }

    public Set<String> getAllRepoKeys() throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(controls,
                "SELECT " + REPO_KEY_COLUMN + " FROM " + REPO_CONFIG_TABLE)) {
            while (resultSet.next()) {
                keys.add(resultSet.getString(1));
            }
        }
        return keys;
    }


    public Set<String> getAllReleaseBundlesV2RepoKeys() throws SQLException {
        DbType dbType = dbProperties.getDbType();
        QueryWriter query = new QueryWriter(dbType);
        query.select(REPO_KEY_COLUMN)
                .from(REPO_CONFIG_TABLE)
                .where(TYPE_COLUMN + " != ? AND " + PACKAGE_TYPE_COLUMN + " = ?");
        Set<String> keys = new HashSet<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query.build(), RELEASE_BUNDLE.typeNameLowercase(),
                ReleaseBundles.getType())) {
            while (resultSet.next()) {
                keys.add(resultSet.getString(1));
            }
        }
        return keys;
    }

    public Set<String> findAllReposThatDoNotExistInNodes(long modifiedInRange) throws SQLException {
        DbType dbType = dbProperties.getDbType();

        QueryWriter query = new QueryWriter(dbType);

        query.select(REPO_KEY_COLUMN)
                .from(" ( " + getSelectAllReposQuery(dbType, modifiedInRange) +
                        " UNION " + getSelectCacheReposQuery(dbType, modifiedInRange) +
                        " ) inner_query")
                .where("NOT EXISTS ( " + new QueryWriter(dbType).select("1").from(NODES_TABLE)
                        .where("inner_query." + REPO_KEY_COLUMN + " = " + NODES_TABLE + "." + REPO_COLUMN_NAME
                                + " AND node_path = '.' AND node_name = '.'").build() + " )");

        Set<String> keys = new HashSet<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(controls, query.build())) {
            while (resultSet.next()) {
                keys.add(resultSet.getString(1));
            }
        }
        return keys;
    }

    private String getSelectCacheReposQuery(DbType dbType, long modifiedInRange) {
        return new QueryWriter(dbType).select(REPO_KEY_COLUMN).concat(PATH_SUFFIX, REPO_KEY_COLUMN)
                .from(REPO_CONFIG_TABLE).where(TYPE_COLUMN + " = '" + REMOTE.typeNameLowercase() +
                        "' AND " + MODIFIED_COLUMN + " > " + modifiedInRange).build();
    }

    private String getSelectAllReposQuery(DbType dbType, long modifiedInRange) {
        return new QueryWriter(dbType).select(REPO_KEY_COLUMN)
                .from(REPO_CONFIG_TABLE).where(TYPE_COLUMN + " <> '" +
                        REMOTE.typeNameLowercase() +
                        "' AND " + MODIFIED_COLUMN + " > " + modifiedInRange).build();
    }
}
