package org.artifactory.ph;

import com.jfrog.ph.acl.model.PackageHeavyQueryCacheConfig;
import com.jfrog.ph.acl.service.PackageHeavyQueryCacheService;
import lombok.NonNull;
import org.jfrog.storage.hqc.model.HeavyQueryCacheConfig;
import org.jfrog.storage.hqc.service.HeavyQueryCacheService;

import java.util.concurrent.Callable;

/**
 * @author Volodymyr Shulga
 */
public class PackageHeavyQueryCacheServiceImpl implements PackageHeavyQueryCacheService {
    private final HeavyQueryCacheService heavyQueryCacheService;

    public PackageHeavyQueryCacheServiceImpl(HeavyQueryCacheService heavyQueryCacheService) {
        this.heavyQueryCacheService = heavyQueryCacheService;
    }

    @Override
    public <T> T get(@NonNull Callable<T> callable, @NonNull Class<T> clazz,
            @NonNull PackageHeavyQueryCacheConfig hqcConfig) {
        HeavyQueryCacheConfig config = HeavyQueryCacheConfig.builder()
                .minCallableDurationMillis(hqcConfig.getMinCallableDurationMillis())
                .expiryLowerBound(hqcConfig.getExpiryLowerBound())
                .expiryUpperBound(hqcConfig.getExpiryUpperBound())
                .expiryFactor(hqcConfig.getExpiryFactor())
                .maxBlobSizeKb(hqcConfig.getMaxBlobSizeKb())
                .threadLockPeriod(hqcConfig.getThreadLockPeriod())
                .build();
        return heavyQueryCacheService.get(callable, clazz, hqcConfig.getEntryKey(), hqcConfig.getHqcType(), config);
    }

    @Override
    public void evict(@NonNull String hqcType, @NonNull Object... args) {
        heavyQueryCacheService.evict(hqcType, args);
    }
}