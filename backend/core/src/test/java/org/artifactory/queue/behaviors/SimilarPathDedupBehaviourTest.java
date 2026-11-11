package org.artifactory.queue.behaviors;

import org.apache.commons.compress.utils.Lists;
import org.artifactory.queue.QueueEventImpl;

import java.util.List;

import static org.artifactory.event.EventType.create;
import static org.artifactory.event.EventType.delete;
import static org.artifactory.event.EventType.props;
import static org.artifactory.event.EventType.update;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * @author avishayh
 */
public class SimilarPathDedupBehaviourTest extends AbstractEventReductionBehaviorTest {

    public void basicDedupBehaviour() {
        SimilarPathDedupBehavior similarPathDedupBehaviour = new SimilarPathDedupBehavior();
        List<QueueEventImpl> before = Lists.newArrayList();
        long timestamp = System.currentTimeMillis();
        add(before, create, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, create, "b", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, create, "c", timestamp);
        add(before, props, "a", timestamp);
        List<QueueEventImpl> after = similarPathDedupBehaviour.apply(before);
        assertEquals(after.size(), 4);
        assertEquals(after.get(0).toString(), "%s|1|create|a".formatted(timestamp));
        assertEquals(after.get(1).toString(), "%s|4|create|b".formatted(timestamp));
        assertEquals(after.get(2).toString(), "%s|7|create|c".formatted(timestamp));
        assertEquals(after.get(3).toString(), "%s|8|props|a".formatted(timestamp));
    }

    public void treat2EventTypesAsSame() {
        SimilarPathDedupBehavior similarPathDedupBehaviour = new SimilarPathDedupBehavior(create, props);
        List<QueueEventImpl> before = Lists.newArrayList();
        long timestamp = System.currentTimeMillis();
        add(before, create, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, create, "b", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, create, "c", timestamp);
        add(before, update, "c", timestamp);
        add(before, props, "a", timestamp);
        List<QueueEventImpl> after = similarPathDedupBehaviour.apply(before);
        assertEquals(after.size(), 4);
        assertEquals(after.get(0).toString(), "%s|4|create|b".formatted(timestamp));
        assertEquals(after.get(1).toString(), "%s|7|create|c".formatted(timestamp));
        assertEquals(after.get(2).toString(), "%s|8|update|c".formatted(timestamp));
        assertEquals(after.get(3).toString(), "%s|9|props|a".formatted(timestamp));
    }

    public void treat3EventTypesAsSame() {
        SimilarPathDedupBehavior similarPathDedupBehaviour = new SimilarPathDedupBehavior(create, update, props);
        List<QueueEventImpl> before = Lists.newArrayList();
        long timestamp = System.currentTimeMillis();
        add(before, create, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, create, "b", timestamp);
        add(before, update, "b", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, update, "a", timestamp);
        add(before, update, "c", timestamp);
        add(before, props, "c", timestamp);
        add(before, props, "a", timestamp);
        List<QueueEventImpl> after = similarPathDedupBehaviour.apply(before);
        assertEquals(after.size(), 3);
        assertEquals(after.get(0).toString(), "%s|5|update|b".formatted(timestamp));
        assertEquals(after.get(1).toString(), "%s|10|props|c".formatted(timestamp));
        assertEquals(after.get(2).toString(), "%s|11|props|a".formatted(timestamp));
    }

    public void illegalDedupBehaviour() {
        //either 0 or more than 1 types allowed
        assertThrows(IllegalArgumentException.class, () -> new SimilarPathDedupBehavior(create));
        assertThrows(IllegalArgumentException.class, () -> new SimilarPathDedupBehavior(update));
        assertThrows(IllegalArgumentException.class, () -> new SimilarPathDedupBehavior(delete));
        assertThrows(IllegalArgumentException.class, () -> new SimilarPathDedupBehavior(props));
    }
}
