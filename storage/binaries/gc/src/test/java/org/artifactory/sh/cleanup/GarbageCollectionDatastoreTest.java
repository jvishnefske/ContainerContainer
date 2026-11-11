package org.artifactory.sh.cleanup;

import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class GarbageCollectionDatastoreTest {

    @Test
    public void testStore() {
        GarbageCollectionDatastoreService garbageCollectionDatastoreService = new GarbageCollectionDatastoreService();
        assertNull(garbageCollectionDatastoreService.getLastGCRunInfo());
        GarbageCollectorInfo garbageCollectorInfo =
                new GarbageCollectorInfo(GarbageCollectorStrategy.FULL.name(), 10);
        garbageCollectorInfo.setTotalSizeCleaned(new AtomicLong(10));
        garbageCollectorInfo.setBinariesCleaned(new AtomicInteger(5));
        garbageCollectorInfo.calcStatus();
        garbageCollectionDatastoreService.saveLastGCRunInfo(garbageCollectorInfo);
        assertNotNull(garbageCollectionDatastoreService.getLastGCRunInfo());
        assertEquals(GarbageCollectorStrategy.FULL.name(),
                garbageCollectionDatastoreService.getLastGCRunInfo().getStrategy());
    }

}