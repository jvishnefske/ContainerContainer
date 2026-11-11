package org.artifactory.storage.service;

import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.hqc.exception.HeavyQueryCacheException;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class StorageMetricCollectorImplTest extends ArtifactoryHomeBoundTest {

    @Test
    public void happyFlow() {
        BinaryInfoService binaryService = Mockito.mock(BinaryInfoService.class);
        when(binaryService.getStorageSize()).thenReturn(48L);
        StorageMetricCollectorImpl storageMetricCollector = new StorageMetricCollectorImpl(binaryService);
        storageMetricCollector.update();
        StorageMetric metric = storageMetricCollector.get();
        assertEquals(metric.getStorageSize(), 48L);
    }

    @Test
    public void get_Exception() {
        BinaryInfoService binaryService = Mockito.mock(BinaryInfoService.class);
        when(binaryService.getStorageSize()).thenThrow(HeavyQueryCacheException.class);
        StorageMetricCollectorImpl storageMetricCollector = new StorageMetricCollectorImpl(binaryService);
        storageMetricCollector.update();
        StorageMetric metric = storageMetricCollector.get();
        assertEquals(metric.getStorageSize(), -1L);
    }

}