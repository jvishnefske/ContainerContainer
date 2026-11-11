package org.artifactory.storage.db.hqc.enums;

/**
 * @author Tomer Slutsky
 * @author Andrey Tashlitsky
 */
public enum HeavyQueryCacheFields {

    ID("id"),
    CACHE_KEY("cache_key"),
    CACHE_TIME("cache_time"),
    EXPIRY_TIME("expiry_time"),
    DATA("data");

    private final String value;

    HeavyQueryCacheFields(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
