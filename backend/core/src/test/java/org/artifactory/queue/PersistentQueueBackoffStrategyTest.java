package org.artifactory.queue;

import org.artifactory.queue.error.PersistentQueueBackoffStrategy;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.artifactory.repo.event.EventInfo;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.artifactory.event.EventType.create;

/**
 * @author avishayh
 */
@Test
public class PersistentQueueBackoffStrategyTest {

    final PersistentQueueBackoffStrategy strategy = new PersistentQueueBackoffStrategy(4, 30);

    @DataProvider
    private static Object[][] errorInfoProvider() {
        return new Object[][] {
                {0, HOURS.toMillis(12), false, 1},
                {1, MINUTES.toMillis(3), true, 4},
                {1, MINUTES.toMillis(6), false, 4},
                {2, MINUTES.toMillis(11), true, 16},
                {2, MINUTES.toMillis(20), false, 16},
                {3, HOURS.toMillis(1), true, 64},
                {3, HOURS.toMillis(2), false, 64},
                {4, HOURS.toMillis(4), true, 256},
                {4, HOURS.toMillis(5), false, 256},
                {5, HOURS.toMillis(16), true, 1024},
                {5, HOURS.toMillis(18), false, 1024},
                {6, HOURS.toMillis(29), true, 1800},
                {6, HOURS.toMillis(30), false, 1800},
        };
    }

    @Test(dataProvider = "errorInfoProvider")
    public void testStrategy(int errorCount, long currentTime, boolean expectedAnswer, int expectedMinutes) {
        EventAndErrorInfo errorInfo = createErrorInfo(errorCount);
        Assert.assertEquals(MILLISECONDS.toMinutes(strategy.backoff(errorInfo.getErrorCount())), expectedMinutes);
        Assert.assertEquals(strategy.isBackoffDelayRequired(errorInfo, currentTime), expectedAnswer);
    }

    private EventAndErrorInfo createErrorInfo(int errorCount) {
        return EventAndErrorInfo.builder().eventInfo(new EventInfo(0, create, ""))
                .errorCount(errorCount).firstErrorTime(0).build();
    }
}
