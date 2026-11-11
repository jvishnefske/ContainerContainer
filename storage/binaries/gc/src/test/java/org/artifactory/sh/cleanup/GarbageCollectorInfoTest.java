package org.artifactory.sh.cleanup;

import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.binstore.ifc.GarbageCollectListenerResultStatus;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Alexei Vainshtein
 */
public class GarbageCollectorInfoTest extends ArtifactoryHomeBoundTest {

    @Test
    public void setStatusTest() {
        GarbageCollectorInfo garbageCollectorInfo =
                spy(new GarbageCollectorInfo(GarbageCollectorStrategy.FULL.name(), 10));
        Assert.assertEquals(garbageCollectorInfo.getStatus(), GarbageCollectListenerResultStatus.UNCOMPLETED);
        garbageCollectorInfo.calcStatus();
        Assert.assertEquals(garbageCollectorInfo.getStrategy(), GarbageCollectorStrategy.FULL.name());
        Assert.assertEquals(garbageCollectorInfo.getFailures().get(), 0);
        Assert.assertEquals(garbageCollectorInfo.getStatus(), GarbageCollectListenerResultStatus.COMPLETED);
        garbageCollectorInfo.setStatus(GarbageCollectListenerResultStatus.UNCOMPLETED);

        garbageCollectorInfo.setCandidatesForDeletion(5);
        garbageCollectorInfo.calcStatus();
        Assert.assertEquals(garbageCollectorInfo.getStrategy(), GarbageCollectorStrategy.FULL.name());
        Assert.assertEquals(garbageCollectorInfo.getStatus(), GarbageCollectListenerResultStatus.COMPLETED);
        garbageCollectorInfo.setStatus(GarbageCollectListenerResultStatus.UNCOMPLETED);

        garbageCollectorInfo.setFailures(new AtomicInteger(5));
        garbageCollectorInfo.calcStatus();
        Assert.assertEquals(garbageCollectorInfo.getStrategy(), GarbageCollectorStrategy.FULL.name());
        Assert.assertEquals(garbageCollectorInfo.getStatus(), GarbageCollectListenerResultStatus.UNCOMPLETED);

        garbageCollectorInfo.setFailures(new AtomicInteger(6));
        garbageCollectorInfo.calcStatus();
        Assert.assertEquals(garbageCollectorInfo.getStrategy(), GarbageCollectorStrategy.FULL.name());
        Assert.assertEquals(garbageCollectorInfo.getStatus(), GarbageCollectListenerResultStatus.UNCOMPLETED);

        garbageCollectorInfo.setCandidatesForDeletion(25);
        garbageCollectorInfo.calcStatus();
        Assert.assertEquals(garbageCollectorInfo.getStrategy(), GarbageCollectorStrategy.FULL.name());
        Assert.assertEquals(garbageCollectorInfo.getFailures().get(), 6);
        Assert.assertEquals(garbageCollectorInfo.getStatus(), GarbageCollectListenerResultStatus.COMPLETED);
        garbageCollectorInfo.setStatus(GarbageCollectListenerResultStatus.UNCOMPLETED);

        garbageCollectorInfo.setFailures(new AtomicInteger(10));
        garbageCollectorInfo.calcStatus();
        Assert.assertEquals(garbageCollectorInfo.getStrategy(), GarbageCollectorStrategy.FULL.name());
        Assert.assertEquals(garbageCollectorInfo.getStatus(), GarbageCollectListenerResultStatus.UNCOMPLETED);

        garbageCollectorInfo.setFailures(new AtomicInteger(11));
        garbageCollectorInfo.calcStatus();
        Assert.assertEquals(garbageCollectorInfo.getStrategy(), GarbageCollectorStrategy.FULL.name());
        Assert.assertEquals(garbageCollectorInfo.getStatus(), GarbageCollectListenerResultStatus.UNCOMPLETED);
        verify(garbageCollectorInfo, times(0)).generateGcLogReport();
        garbageCollectorInfo.printCollectionInfo();
        verify(garbageCollectorInfo, times(1)).generateGcLogReport();
    }
}
