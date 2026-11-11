package org.artifactory.storage.db.storage.cache.dao;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.storage.cache.entity.StorageDbCacheEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * DAO implementation for storage_cache table operations
 * This table can be used as a persistent key-value cache (key -- entity_type, entity_key), value -- entity_value.
 * The value is stores as a string, so it can represent any data that supports String serialization.
 *
 * @author Evgeny Kurtser
 */
@Slf4j
@Repository
public class StorageCacheDao extends BaseDao {

    public static final String TABLE_NAME = "storage_cache";

    private static final String UPDATE_QUERY = """
            UPDATE %s SET cache_value = ?, calculation_timestamp = ?
            WHERE entity_type = ? AND entity_key = ?""".formatted(TABLE_NAME);

    private static final String INSERT_QUERY = """
            INSERT INTO %s (entity_type, entity_key, cache_value, calculation_timestamp)
            VALUES (?, ?, ?, ?)""".formatted(TABLE_NAME);

    private static final String SELECT_BY_TYPE_AND_KEY_QUERY = """
            SELECT id, entity_type, entity_key, cache_value, calculation_timestamp
            FROM %s
            WHERE entity_type = ? AND entity_key = ?""".formatted(TABLE_NAME);

    private static final String SELECT_BY_TYPE_QUERY = """
            SELECT id, entity_type, entity_key, cache_value, calculation_timestamp
            FROM %s
            WHERE entity_type = ?""".formatted(TABLE_NAME);

    private static final String DELETE_BY_TYPE_AND_KEY_QUERY = """
            DELETE FROM %s
            WHERE entity_type = ? AND entity_key = ?""".formatted(TABLE_NAME);

    private static final String DELETE_BY_TYPE_AND_TIMESTAMP = """
            DELETE FROM %s
            WHERE entity_type = ? AND calculation_timestamp < ?""".formatted(TABLE_NAME);

    private static final String DELETE_BY_TYPE_QUERY = """
            DELETE FROM %s
            WHERE entity_type = ?""".formatted(TABLE_NAME);

    private static final String DELETE_BY_TYPE_AND_KEYS_QUERY = """
            DELETE FROM %s
            WHERE entity_type = ? AND entity_key IN (#)""".formatted(TABLE_NAME);

    private static final String SIZE_QUERY = """
            SELECT count(id)
            FROM %s""".formatted(TABLE_NAME);

    private static final String SIZE_BY_TYPE_QUERY = """
            SELECT count(id)
            FROM %s
            WHERE entity_type = ?""".formatted(TABLE_NAME);


    public StorageCacheDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    /**
     * Updates a cache entry
     *
     * @param entity StorageCacheDto to update
     * @return number of updated entries. Normally should be 1
     */
    public int update(StorageDbCacheEntity entity) {
        try {
            return jdbcHelper.executeUpdate(UPDATE_QUERY,
                    entity.value(),
                    entity.calculationTimestamp(),
                    entity.type(),
                    entity.key());
        } catch (SQLException e) {
            throw new StorageException("Failed to update cache entry", e);
        }
    }

    /**
     * Saves a new cache entry
     *
     * @param entity StorageCacheDto to save
     */
    public void save(StorageDbCacheEntity entity) {
        try {
            jdbcHelper.executeUpdate(INSERT_QUERY,
                    entity.type(),
                    entity.key(),
                    entity.value(),
                    entity.calculationTimestamp());
        } catch (SQLException e) {
            throw new StorageException("Failed to save cache entry", e);
        }
    }

    /**
     * Retrieves a cache entry by entity key and type
     *
     * @param entityType the entity type (e.g. "PROJECT")
     * @param entityKey  the entity key
     * @return StorageCacheDto or null if not found
     */
    @Nullable
    public StorageDbCacheEntity getByTypeAndKey(@NonNull String entityType, @NonNull String entityKey)
            throws StorageException {
        try (ResultSet resultSet = jdbcHelper.executeSelect(SELECT_BY_TYPE_AND_KEY_QUERY, entityType, entityKey)) {
            if (resultSet.next()) {
                return mapToEntity(resultSet);
            }
            return null;
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve cache entry", e);
        }
    }

    /**
     * Retrieves all cache entries
     *
     * @return list of all StorageCacheDto
     */
    @Nonnull
    public List<StorageDbCacheEntity> getByType(@NonNull String entityType) throws StorageException {
        try (ResultSet resultSet = jdbcHelper.executeSelect(SELECT_BY_TYPE_QUERY, entityType)) {
            return mapToEntityList(resultSet);
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve all cache entries", e);
        }
    }

    /**
     * Deletes a cache entry
     *
     * @param entityType the entity type
     * @param entityKey  the entity key. If null, deletes all entries of the given type
     * @return number of deleted entries
     */
    public int delete(@NonNull String entityType, @Nullable String entityKey) throws StorageException {
        try {
            return entityKey == null ? jdbcHelper.executeUpdate(DELETE_BY_TYPE_QUERY, entityType) :
                    jdbcHelper.executeUpdate(DELETE_BY_TYPE_AND_KEY_QUERY, entityType, entityKey);
        } catch (SQLException e) {
            throw new StorageException("Failed to delete cache entry", e);
        }
    }

    /**
     * Deletes cache entries older than the given timestamp
     *
     * @param entityType the entity type
     * @param since      entries older than this timestamp will be deleted
     * @return number of deleted entries
     */
    public int delete(@NonNull String entityType, long since) throws StorageException {
        try {
            int deleted = jdbcHelper.executeUpdate(DELETE_BY_TYPE_AND_TIMESTAMP, entityType, since);
            log.trace("Deleted {} cache entries of type '{}' older than {}", deleted, entityType, since);
            return deleted;
        } catch (SQLException e) {
            throw new StorageException("Failed to delete cache entry", e);
        }
    }

    /**
     * Deletes cache entries by type and a collection of keys
     *
     * @param entityType the entity type
     * @param keys       the set of entity keys to delete
     * @return number of deleted entries
     */
    public int delete(@NonNull String entityType, @NonNull Set<String> keys) throws StorageException {
        if (keys.isEmpty()) {
            log.trace("No keys provided for deletion, skipping delete operation");
            return 0;
        }
        try {
            int deleted = jdbcHelper.executeUpdate(DELETE_BY_TYPE_AND_KEYS_QUERY, entityType, keys);
            log.trace("Deleted {} cache entries of type '{}' for {} keys", deleted, entityType, keys.size());
            return deleted;
        } catch (SQLException e) {
            throw new StorageException("Failed to delete cache entries by keys", e);
        }
    }

    /**
     * Counts the number of cache entries of a given type
     *
     * @param entityType the entity type. If null, counts all entries
     * @return number of cache entries
     */
    public int size(@Nullable String entityType) throws StorageException {
        try (ResultSet resultSet =
                     entityType == null ? jdbcHelper.executeSelect(SIZE_QUERY) :
                             jdbcHelper.executeSelect(SIZE_BY_TYPE_QUERY, entityType)) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new StorageException("Failed to count cache entries", e);
        }
    }

    private StorageDbCacheEntity mapToEntity(ResultSet resultSet) throws SQLException {
        return StorageDbCacheEntity.builder()
                .id(resultSet.getLong("id"))
                .type(resultSet.getString("entity_type"))
                .key(resultSet.getString("entity_key"))
                .value(resultSet.getString("cache_value"))
                .calculationTimestamp(resultSet.getLong("calculation_timestamp"))
                .build();
    }

    private List<StorageDbCacheEntity> mapToEntityList(ResultSet resultSet) throws SQLException {
        List<StorageDbCacheEntity> entities = new ArrayList<>();
        while (resultSet.next()) {
            entities.add(mapToEntity(resultSet));
        }
        return entities;
    }
}