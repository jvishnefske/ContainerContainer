package org.artifactory.storage.db.storage.cache.entity;

import lombok.Builder;

/**
 * Entity representing a record in the storage_cache table
 * This table can be used as a persistent key-value cache (key -- entity_type, entity_key), value -- entity_value.
 * The value is stores as a string, so it can represent any data that supports String serialization.
 *
 * @author Evgeny Kurtser
 */
@Builder(toBuilder = true)
public record StorageDbCacheEntity(
        long id,
        String key,
        String type,
        String value,
        long calculationTimestamp
) {
}