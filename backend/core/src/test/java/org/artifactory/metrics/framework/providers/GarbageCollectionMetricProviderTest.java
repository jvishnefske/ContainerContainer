package org.artifactory.metrics.framework.providers;

import org.artifactory.sh.cleanup.GarbageCollectionDatastoreService;
import org.artifactory.sh.cleanup.GarbageCollectorStrategy;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class GarbageCollectionMetricProviderTest {

    GarbageCollectionDatastoreService garbageCollectionDatastoreService;

    @Test
    public void testGetName() {
        GarbageCollectionMetricProvider gcProvider =
                new GarbageCollectionMetricProvider(mock(GarbageCollectionDatastoreService.class));
        assertEquals("jfrt_artifacts_gc", gcProvider.getName());
    }

    @Test
    public void testGetDescription() {
        GarbageCollectionMetricProvider gcProvider =
                new GarbageCollectionMetricProvider(mock(GarbageCollectionDatastoreService.class));
        assertEquals("Artifactory Garbage Collection Metrics Provider", gcProvider.getDescription());
    }

    @Test
    public void testGetRefreshIntervalMillis() {
        GarbageCollectionMetricProvider gcProvider =
                new GarbageCollectionMetricProvider(mock(GarbageCollectionDatastoreService.class));
        assertEquals(60000, gcProvider.getRefreshIntervalMillis());
    }

    @Test
    public void testGetReportType() {
        GarbageCollectionMetricProvider gcProvider =
                new GarbageCollectionMetricProvider(mock(GarbageCollectionDatastoreService.class));
        assertEquals(MetricProvider.ReportType.DEDUPED, gcProvider.getReportType());
    }

    @Test
    public void testCollectTrashGCType() {
        garbageCollectionDatastoreService = mock(GarbageCollectionDatastoreService.class);
        GarbageCollectionMetricProvider gcProvider =
                new GarbageCollectionMetricProvider(garbageCollectionDatastoreService);
        GarbageCollectorInfo garbageCollectorInfo =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), 10);
        garbageCollectorInfo.setTotalSizeCleaned(new AtomicLong(10));
        garbageCollectorInfo.setBinariesCleaned(new AtomicInteger(5));
        garbageCollectorInfo.calcStatus();
        when(garbageCollectionDatastoreService.getLastGCRunInfo()).thenReturn(garbageCollectorInfo);
        List<Metric> metrics = gcProvider.collect();
        System.out.println(metrics);
        assertEquals(3, metrics.size());
        assertEquals(4, metrics.get(0).getLabels().size());
        String[] keys = new String[] {"start", "end", "type", "status"};
        System.out.println("Metrics : " + metrics);
        assertTrue(Arrays.stream(keys).allMatch(metrics.get(0).getLabels()::containsKey));
    }

    @Test
    public void testCollectFullGCType() {
        garbageCollectionDatastoreService = mock(GarbageCollectionDatastoreService.class);
        GarbageCollectionMetricProvider gcProvider =
                new GarbageCollectionMetricProvider(garbageCollectionDatastoreService);
        GarbageCollectorInfo garbageCollectorInfo =
                new GarbageCollectorInfo(GarbageCollectorStrategy.FULL.name(), 10);
        garbageCollectorInfo.setTotalSizeCleaned(new AtomicLong(10));
        garbageCollectorInfo.setBinariesCleaned(new AtomicInteger(5));
        garbageCollectorInfo.calcStatus();
        when(garbageCollectionDatastoreService.getLastGCRunInfo()).thenReturn(garbageCollectorInfo);
        List<Metric> metrics = gcProvider.collect();
        System.out.println(metrics);
        assertEquals(3, metrics.size());
        assertEquals(4, metrics.get(0).getLabels().size());
        String[] keys = new String[] {"start", "end", "type", "status"};
        System.out.println("Metrics : " + metrics);
        assertTrue(Arrays.stream(keys).allMatch(metrics.get(0).getLabels()::containsKey));
    }
}