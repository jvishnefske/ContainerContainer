package org.artifactory.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.hqc.exception.HeavyQueryCacheException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class StorageMetricCollectorImpl implements StorageMetricCollector {

    private final AtomicReference<StorageMetric> storageMetric =
            new AtomicReference<>(new StorageMetric(-1));

    private final BinaryInfoService binaryService;

    @Autowired
    public StorageMetricCollectorImpl(BinaryInfoService binaryService) {
        this.binaryService = binaryService;
    }

    @Override
    public void update() {
        log.debug("Updating storage metric with storage size");
        try {
            long storageSize = binaryService.getStorageSize();
            storageMetric.set(new StorageMetric(storageSize));
        } catch (HeavyQueryCacheException e) {
            log.info("Exception happened while getting storage size from HQC, leaving previous value: {}",
                    e.getMessage());
            log.debug("HQC exception: ", e);
        }
        log.debug("Finished to update storage metric, binaries size = {}", storageMetric.get());
    }

    @Override
    public StorageMetric get() {
        return storageMetric.get();
    }

}
