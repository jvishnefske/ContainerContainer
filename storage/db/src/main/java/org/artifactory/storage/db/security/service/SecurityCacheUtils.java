package org.artifactory.storage.db.security.service;

import com.google.common.cache.Cache;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.annotation.Nonnull;

/**
 * @author Tomer Slutsky
 */
@Slf4j
@UtilityClass
public class SecurityCacheUtils {
    public static void putInCache(@Nonnull String loginIdentifier, Cache<String, Long> cache, long value) {
        log.trace("Inserting loginIdentifier: {} as lowercase in cache: {}, with value: {}", loginIdentifier, cache,
                value);
        if (StringUtils.isNotBlank(loginIdentifier)) {
            cache.put(loginIdentifier.toLowerCase(), value);
        }
    }

    public static void invalidateFromCache(@Nonnull String loginIdentifier, Cache<String, Long> cache) {
        log.trace("Invalidating loginIdentifier: {} as lowercase in cache: {}", loginIdentifier, cache);
        if (StringUtils.isNotBlank(loginIdentifier)) {
            cache.invalidate(loginIdentifier.toLowerCase());
        }
    }

    public static Long getFromCache(@Nonnull String loginIdentifier, Cache<String, Long> cache) {
        log.trace("Getting from cache: {} with loginIdentifier: {} in lowercase", cache, loginIdentifier);
        if (StringUtils.isNotBlank(loginIdentifier)) {
            return cache.getIfPresent(loginIdentifier.toLowerCase());
        }

        return null;
    }
}
