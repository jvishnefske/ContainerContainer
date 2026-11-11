package org.artifactory.threadlocal.cache;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * A static wrapper to ReposItemsThreadLocalCache implementation to ease the code usage
 */
@NoArgsConstructor
@Service
@Primary
public class ReposItemsThreadLocalCacheWrapper implements ReposItemsThreadLocalCache {

    public <T, Y extends Exception> T wrap(String repoKey, @NonNull SupplierWithError<T, Y> supplier) throws Y {
        return ReposItemsThreadLocalCacheService.wrap(repoKey, supplier);
    }

    public void invalidateCache(String repoName) {
        ReposItemsThreadLocalCacheService.invalidateCache(repoName);
    }

    public <T extends RepositoryNameProvider, Y extends Exception> T get(String key, String repoName,
            SupplierWithLimit<T, Y> supplierWithLimit, long limit, FallbackSupplier<T, Y> fallback) throws Y {
        return ReposItemsThreadLocalCacheService.get(key, repoName, supplierWithLimit, limit, fallback);
    }

    @Nullable
    public <T extends RepositoryNameProvider, Y extends Exception> List<T> getAll(String key, String repoName,
            SupplierWithLimit<T, Y> supplierWithLimit, long limit, FallbackSupplier<T, Y> fallback) throws Y {
        return ReposItemsThreadLocalCacheService.getAll(key, repoName, supplierWithLimit, limit, fallback);
    }

    public <T extends RepositoryNameProvider, Y extends Exception> boolean exists(String key, String repoName,
            SupplierWithLimit<T, Y> function, long limit, NodeExistsSupplier<Y> fallback) throws Y {
        return ReposItemsThreadLocalCacheService.exists(key, repoName, function, limit, fallback);
    }
}
