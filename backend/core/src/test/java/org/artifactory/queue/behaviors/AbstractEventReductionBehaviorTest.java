package org.artifactory.queue.behaviors;

import org.artifactory.event.EventType;
import org.artifactory.queue.QueueEventImpl;
import org.artifactory.repo.event.EventInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * @author avishayh
 */
@Test
public abstract class AbstractEventReductionBehaviorTest {

    int index;

    @BeforeMethod
    public void reset() {
        index = 0;
    }

    protected void add(List<QueueEventImpl> list, EventType type, String path, long timestamp) {

        list.add(new QueueEventImpl(new EventInfo(++index, timestamp, type, path, null)));
    }
}
