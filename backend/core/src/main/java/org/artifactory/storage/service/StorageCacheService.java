package org.artifactory.storage.service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.storage.db.storage.cache.entity.StorageDbCacheEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Service for persistent caching of key-value data in the database.
 * The service allows storing and retrieving cached entries of any type T,
 * given appropriate serialization and deserialization functions are provided by T.
 *
 * @author Evgeny Kurtser
 */
public interface StorageCacheService<T> {

    /**
     * Get cached value by type and key
     *
     * @param type         the entity type
     * @param key          the entity key
     * @param expectedType the expected class type of the cached value
     * @return Optional containing StorageCacheEntry if found, otherwise empty
     */
    @Nonnull
    Optional<StorageCacheEntry<T>> get(@NonNull String type, @NonNull String key, @NonNull Class<T> expectedType);


    /**
     * Get all cached entries for a given type
     *
     * @param type         the entity type
     * @param expectedType the expected class type of the cached values
     * @return Optional containing list of StorageCacheEntry if found, otherwise empty
     */
    @Nonnull
    Optional<List<StorageCacheEntry<T>>> get(@NonNull String type, @NonNull Class<T> expectedType);

    /**
     * Put a value into the cache. Overwrites existing entry if present.
     *
     * @param type  the entity type
     * @param key   the entity key
     * @param value the value to cache
     */
    default void put(@NonNull String type, @NonNull String key, @NonNull T value) {
        putConditionally(type, key, value, null);
    }

    /**
     * Put a new value into the cache.
     * If an entry with the same type and key already exists, it is overwritten only if certain condition is met
     *
     * @param type              the entity type
     * @param key               the entity key
     * @param value             the value to cache
     * @param overwriteExisting predicate to determine whether to overwrite existing entry; if the predicate is null
     *                          -- always overwrite. The predicate is invoked only if an existing entry is found.
     */
    void putConditionally(@NonNull String type, @NonNull String key, @NonNull T value,
            @Nullable Predicate<@NonNull StorageDbCacheEntity> overwriteExisting);

    /**
     * Remove a cached entry by type and key
     *
     * @param type the entity type
     * @param key  the entity key
     */
    void remove(@NonNull String type, @NonNull String key);

    /**
     * Remove cached entries by type and a set of keys
     *
     * @param type the entity type
     * @param keys the set of entity keys to remove
     * @return the number of entries removed
     */
    int remove(@NonNull String type, @NonNull Set<String> keys);

    /**
     * Remove cached entries older than the specified threshold
     *
     * @param type  the entity type
     * @param since entries older than this timestamp will be removed
     * @return the number of entries removed
     */
    int removeOlderThan(@NonNull String type, long since);

    /**
     * Get the size of cached entries for a given type and optional key
     *
     * @param type the entity type. If null, counts all types.
     * @return the number of cached entries
     */
    int size(@Nullable String type);

    record StorageCacheEntry<T>(
            String key,
            T value,
            long calculationTimestamp
    ) {
    }
}

