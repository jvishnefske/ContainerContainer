package org.artifactory.queue.behaviors;

import org.artifactory.queue.QueueEventImpl;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.artifactory.event.EventType.create;
import static org.artifactory.event.EventType.delete;
import static org.artifactory.event.EventType.props;
import static org.artifactory.event.EventType.update;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

/**
 * @author avishayh
 */
public class EliminateEventBehaviorTest extends AbstractEventReductionBehaviorTest {

    public void deleteEliminateUpdateOrCreateBehaviour() {
        EliminateEventBehavior behavior = new DeleteEliminateUpdateOrCreateBehavior();
        List<QueueEventImpl> input;
        List<QueueEventImpl> output;

        long timestamp = System.currentTimeMillis();
        //null doesnt fail
        output = behavior.apply(null);
        assertNull(output, "null does not fail");
        //input equals output
        input = newArrayList();
        add(input, create, "a", timestamp);
        output = behavior.apply(input);
        assertEquals(output, input, "no eliminations here");
        //input equals output
        input = newArrayList();
        add(input, create, "a", timestamp);
        add(input, update, "a", timestamp);
        output = behavior.apply(input);
        assertEquals(output, input, "no eliminations here");
        //input equals output
        input = newArrayList();
        add(input, delete, "a", timestamp);
        add(input, delete, "a", timestamp);
        output = behavior.apply(input);
        assertEquals(output, input, "no eliminations here");
        //eliminating all events
        input = newArrayList();
        add(input, create, "a", timestamp); //eliminated
        add(input, delete, "a", timestamp); //eliminated
        output = behavior.apply(input);
        assertEquals(output.size(), 0, "eliminating all events");
        input = newArrayList();
        add(input, update, "a", timestamp); //eliminated
        add(input, delete, "a", timestamp); //eliminated
        output = behavior.apply(input);
        assertEquals(output.size(), 0, "eliminating all events");
        input = newArrayList();
        add(input, create, "a", timestamp); //eliminated
        add(input, update, "a", timestamp); //eliminated
        add(input, delete, "a", timestamp); //eliminated
        output = behavior.apply(input);
        assertEquals(output.size(), 0, "eliminating all events");

        //new create event survives
        input = newArrayList();
        add(input, create, "a", timestamp); //eliminated
        add(input, update, "a", timestamp); //eliminated
        add(input, delete, "a", timestamp); //eliminated
        add(input, create, "a", timestamp);
        int currentIndex = index;
        output = behavior.apply(input);
        assertEquals(output.size(), 1, "same as before with extra event survived after");
        assertEquals(output.get(0).getEventId(), currentIndex, "same as before with extra event survived after");

        //now with multiple path + survivors
        input = newArrayList();
        add(input, create, "a", timestamp); //eliminated
        add(input, create, "b", timestamp); //eliminated
        add(input, update, "b", timestamp); //eliminated
        add(input, create, "c", timestamp);
        int cIndex = index;
        add(input, update, "a", timestamp); //eliminated
        add(input, delete, "b", timestamp); //eliminates (create,update b) + self
        add(input, delete, "a", timestamp); //eliminates (create,update a) + self
        add(input, create, "a", timestamp);
        int aIndex = index;
        output = behavior.apply(input);
        assertEquals(output.size(), 2, "multiple path same as before");
        assertEquals(output.get(0).getEventId(), cIndex, "multiple path same as before");
        assertEquals(output.get(1).getEventId(), aIndex, "multiple path same as before");
    }

    public void eliminateDedupBehaviorWithoutSelfElimination() {
        CreateEliminateDeleteBehavior behavior = new CreateEliminateDeleteBehavior();
        List<QueueEventImpl> input;
        List<QueueEventImpl> output;
        long timestamp = System.currentTimeMillis();
        input = newArrayList();
        add(input, delete, "a", timestamp); //eliminated
        add(input, create, "a", timestamp);
        output = behavior.apply(input);
        assertEquals(output.size(), 1, "create eliminates delete");
        assertEquals(output.get(0).getType(), create, "create eliminates delete");

        reset();
        input = newArrayList();
        add(input, props, "a", timestamp);
        add(input, delete, "a", timestamp); //eliminated
        add(input, create, "b", timestamp);
        add(input, create, "a", timestamp);
        add(input, props, "a", timestamp);
        output = behavior.apply(input);
        assertEquals(output.size(), 4);
        assertEquals(output.get(0).toString(), "%s|1|props|a".formatted(timestamp));
        assertEquals(output.get(1).toString(), "%s|3|create|b".formatted(timestamp));
        assertEquals(output.get(2).toString(), "%s|4|create|a".formatted(timestamp));
        assertEquals(output.get(3).toString(), "%s|5|props|a".formatted(timestamp));
    }

    public void illegalEliminatorBehaviour() {
        assertThrows(IllegalArgumentException.class, () -> new EliminateEventBehavior().apply(newArrayList()));
        assertThrows(IllegalArgumentException.class,
                () -> new EliminateEventBehavior().eliminator(create).apply(newArrayList()));
        assertThrows(IllegalArgumentException.class,
                () -> new EliminateEventBehavior().eliminator(create).eliminates(create, update));
        assertThrows(IllegalArgumentException.class, () -> new EliminateEventBehavior().eliminates(create, update));
    }
}
