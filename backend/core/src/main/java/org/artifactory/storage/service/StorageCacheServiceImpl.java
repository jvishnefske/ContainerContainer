package org.artifactory.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.storage.cache.dao.StorageCacheDao;
import org.artifactory.storage.db.storage.cache.entity.StorageDbCacheEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Service for persistent caching of key-value data in the database.
 * The data is stored in the storage_cache table as strings, so it can represent any data
 * that supports String serialization.
 *
 * @author Evgeny Kurtser
 */
@Slf4j
@Service
public class StorageCacheServiceImpl<T> implements StorageCacheService<T> {

    private final StorageCacheDao storageCacheDao;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    public StorageCacheServiceImpl(StorageCacheDao storageCacheDao) {
        this.storageCacheDao = storageCacheDao;
    }


    @Nonnull
    @Override
    public Optional<StorageCacheEntry<T>> get(@NonNull String type, @NonNull String key,
            @NonNull Class<T> expectedType) throws StorageException {
        StorageDbCacheEntity dto = storageCacheDao.getByTypeAndKey(type, key);
        if (dto == null) {
            return Optional.empty();
        }
        return Optional.of(fromDTO(dto, expectedType));
    }


    @Override
    @Nonnull
    public Optional<List<StorageCacheEntry<T>>> get(@NonNull String type, @NonNull Class<T> expectedType)
            throws StorageException {
        List<StorageDbCacheEntity> cacheDTOs = storageCacheDao.getByType(type);
        List<StorageCacheEntry<T>> list = cacheDTOs.stream().map(dto -> fromDTO(dto, expectedType)).toList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list);
    }

    private StorageCacheEntry<T> fromDTO(StorageDbCacheEntity dto, Class<T> expectedType) throws StorageException {
        try {
            T value = MAPPER.readValue(dto.value(), expectedType);
            return new StorageCacheEntry<>(dto.key(), value, dto.calculationTimestamp());
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to deserialize cache value for type '%s' and key '%s': %s"
                    .formatted(dto.type(), dto.key(), e.getMessage()), e);
        }
    }

    @Override
    public void putConditionally(@NonNull String type, @NonNull String key, @NonNull T value,
            @Nullable Predicate<@NonNull StorageDbCacheEntity> overwriteExisting) throws StorageException {
        try {
            String cacheValue = MAPPER.writeValueAsString(value);
            StorageDbCacheEntity entity = StorageDbCacheEntity.builder()
                    .key(key)
                    .type(type)
                    .value(cacheValue)
                    .calculationTimestamp(System.currentTimeMillis())
                    .build();
            // Check if entry exists
            StorageDbCacheEntity existing = storageCacheDao.getByTypeAndKey(entity.type(), entity.key());
            if (existing != null) {
                if (overwriteExisting == null || overwriteExisting.test(existing)) {
                    // Update existing entry
                    storageCacheDao.update(entity);
                } else {
                    log.trace("Skipping update of existing cache entry for type '{}' and key '{}' as per predicate",
                            entity.type(), entity.key());
                }
            } else {
                // Insert new entry
                storageCacheDao.save(entity);
            }
        } catch (JsonProcessingException e) {
            throw new StorageException(
                    "Failed to serialize cache value for type '%s' and key '%s': %s"
                            .formatted(type, key, e.getMessage()), e);
        }
    }


    @Override
    public void remove(@NonNull String type, @NonNull String key) throws StorageException {
        storageCacheDao.delete(type, key);
    }

    @Override
    public int remove(@NonNull String type, @NonNull Set<String> keys) throws StorageException {
        return storageCacheDao.delete(type, keys);
    }

    @Override
    public int removeOlderThan(@NonNull String type, long since) throws StorageException {
        return storageCacheDao.delete(type, since);
    }


    @Override
    public int size(@Nullable String type) throws StorageException {
        return storageCacheDao.size(type);
    }
}