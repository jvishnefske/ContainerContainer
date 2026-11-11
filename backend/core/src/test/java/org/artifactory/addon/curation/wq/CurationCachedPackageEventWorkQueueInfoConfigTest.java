package org.artifactory.addon.curation.wq;

import org.artifactory.addon.AddonsManager;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.workqueue.WorkQueueInfo;
import org.junit.jupiter.api.Test;

import static org.artifactory.addon.curation.wq.CurationCachedPackageEventWorkQueueInfoConfig.CURATION_EVENTS_WQ_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author Andrey Tashlitsky
 */
class CurationCachedPackageEventWorkQueueInfoConfigTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void testWorkQueueInfo() {
        CurationCachedPackageEventWorkQueueInfoConfig config = new CurationCachedPackageEventWorkQueueInfoConfig();
        WorkQueueInfo workQueueInfo = config.curationEventsWorkQueueInfo(mock(AddonsManager.class));
        assertEquals(CURATION_EVENTS_WQ_NAME, workQueueInfo.getName());
    }
}
