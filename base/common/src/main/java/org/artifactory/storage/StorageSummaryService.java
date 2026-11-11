package org.artifactory.storage;

import lombok.NonNull;

/**
 * @author annakv
 */
public interface StorageSummaryService {

    void replaceLocalCache(StorageSummaryInfo info);

    StorageSummaryInfo getLocalCache() throws CacheUnAvailableException;

    @NonNull
    StorageSummaryInfo get();

    StorageSummaryInfo updateAndGet();

    StorageSummaryInfo getStorageSummaryInfoHqc();

}
