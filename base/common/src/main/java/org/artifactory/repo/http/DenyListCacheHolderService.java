package org.artifactory.repo.http;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import org.artifactory.common.ConstantValues;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class DenyListCacheHolderService {

    @Getter
    private final Cache<String, Boolean> denyListCache = CacheBuilder.newBuilder()
            .initialCapacity(1000)
            .maximumSize(10000)
            .expireAfterWrite(ConstantValues.httpRepoDenyListCacheEvictionTime.getLong(), TimeUnit.MINUTES)
            .build();

    /**
     * Cache specifically designed for tracking denied/blocked URLs in remote repository redirect operations.
     *
     * Key differences from global cache:
     * - Scope: Limited to remote repository URL validation only during redirect operations
     * - Isolation: Separate from global caching to avoid interference with other operations
     * - Redirect-specific: Dedicated cache for redirect flows, not shared with other repository operations
     *
     * The cache expires entries after the configured time period to ensure that temporarily
     * blocked redirect URLs can be re-evaluated, while still providing protection against rapid repeated
     * access attempts to malicious endpoints.
     */
    @Getter
    private final Cache<String, Boolean> denyListRedirectCache = CacheBuilder.newBuilder()
            .initialCapacity(500)
            .maximumSize(10000)
            .expireAfterWrite(ConstantValues.httpRepoDenyListCacheEvictionTime.getLong(), TimeUnit.MINUTES)
            .build();
}
