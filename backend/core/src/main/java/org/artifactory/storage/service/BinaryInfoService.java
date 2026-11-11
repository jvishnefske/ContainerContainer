package org.artifactory.storage.service;

import org.artifactory.hqc.enums.HeavyQueryCacheType;
import org.jfrog.storage.hqc.model.HeavyQueryCache;

/**
 * @author annakv
 */
public interface BinaryInfoService {

    @HeavyQueryCache(
            minCallableDurationMillis = 200,
            hqcType = HeavyQueryCacheType.STORAGE_SIZE,
            threadLockPeriod = 300, // 5 min
            expiryUpperBound = 2_760_000, // 46 minutes
            expiryLowerBound = 2_760_000 // 46 minutes
    )
    long getStorageSize();

}
