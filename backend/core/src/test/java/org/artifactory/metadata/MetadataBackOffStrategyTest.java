package org.artifactory.metadata;

import org.artifactory.event.EventType;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.event.error.factory.NodeEventsErrorInfoFactory;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.HOURS;


/**
 * @author Mor Merhav
 */
public class MetadataBackOffStrategyTest {

    private MetadataBackOffStrategy metadataBackOffStrategy;

    @DataProvider
    private static Object[][] errorInfoProvider() {
        return new Object[][] {
                {false, HOURS.toMillis(12), createErrorInfo(0)},
                {true, HOURS.toMillis(5), createErrorInfo(1)},
                {false, HOURS.toMillis(6), createErrorInfo(1)},
                {true, HOURS.toMillis(11), createErrorInfo(2)},
                {false, HOURS.toMillis(12), createErrorInfo(2)},
                {true, HOURS.toMillis(17), createErrorInfo(3)},
                {false, HOURS.toMillis(18), createErrorInfo(3)},
                {true, HOURS.toMillis(23), createErrorInfo(4)},
                {false, HOURS.toMillis(24), createErrorInfo(4)},
                {true, HOURS.toMillis(29), createErrorInfo(5)},
                {false, HOURS.toMillis(30), createErrorInfo(5)},
        };
    }

    private static NodeEventsErrorInfo createErrorInfo(int retryCount) {
        return NodeEventsErrorInfoFactory.create("ec", EventType.create, RepoPathFactory.create("repo"),
                "opKey", null, 1L, retryCount,
                "errMsg", 0, 1L);
    }

    @BeforeClass
    public void beforeClass() {
        metadataBackOffStrategy = new MetadataBackOffStrategy();
    }

    @Test(dataProvider = "errorInfoProvider")
    public void testIsBackoffDelayRequired(boolean expectedBackOffRequired, long currentTime,
            NodeEventsErrorInfo errorInfo) {
        Assert.assertEquals(metadataBackOffStrategy.isBackoffDelayRequired(errorInfo, currentTime),
                expectedBackOffRequired);
    }
}