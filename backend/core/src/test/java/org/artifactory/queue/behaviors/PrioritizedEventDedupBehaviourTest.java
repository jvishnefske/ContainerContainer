package org.artifactory.queue.behaviors;

import com.google.common.collect.Lists;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.event.queue.QueueReductionBehaviours;
import org.artifactory.queue.QueueEventImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.List;

import static org.artifactory.event.EventType.create;
import static org.artifactory.event.EventType.props;
import static org.artifactory.event.EventType.update;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * @author avishayh
 */
public class PrioritizedEventDedupBehaviourTest extends AbstractEventReductionBehaviorTest {

    @Mock
    private ArtifactoryHome home;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(home);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        autoCloseable.close();
    }

    public void testReductionBehaviourFederated() {
        setProperty(ConstantValues.federatedSkipCreateEliminateDeleteOnDeduplication, "false");

        var reductionBehaviours = QueueReductionBehavioursImpl.get(QueueReductionBehaviours.FederatedReplication);
        List<QueueEventImpl> before;
        List<QueueEventImpl> after;
        long timestamp = System.currentTimeMillis();
        before = Lists.newArrayList();
        add(before, create, "a/b", timestamp); //removed
        add(before, props, "a/b", timestamp); //merged with create
        after = reductionBehaviours.applyChain(before);
        assertEquals(after.size(), 1);
        assertEquals(after.get(0).toString(), "%s|2|create|a/b".formatted(timestamp),
                "event merged: last id with prioritized type");
    }

    public void prioritizeCreateAndThenProps() {
        var behavior = new FederatedDedupBehavior();
        List<QueueEventImpl> before;
        List<QueueEventImpl> after;
        long timestamp = System.currentTimeMillis();
        before = Lists.newArrayList();
        add(before, props, "a", timestamp); //merged with create
        add(before, create, "a", timestamp); //removed
        add(before, props, "a", timestamp + 1000); //merged with create
        after = behavior.apply(before);

        assertEquals(after.size(), 1);
        assertEquals(after.get(0).toString(), "%s|3|create|a".formatted(timestamp + 1000),
                "event merged: last id with prioritized type");
    }

    public void prioritizeWith2Types() {
        PrioritizedEventDedupBehavior behavior = new PrioritizedEventDedupBehavior(create, update);
        List<QueueEventImpl> before;
        List<QueueEventImpl> after;

        long timestamp = System.currentTimeMillis();
        before = Lists.newArrayList();
        add(before, create, "a", timestamp); //removed
        add(before, update, "a", timestamp); //merged with create
        after = behavior.apply(before);
        assertEquals(after.size(), 1);
        assertEquals(after.get(0).toString(), "%s|2|create|a".formatted(timestamp),
                "event merged: last id with prioritized type");
        assertEquals(((QueueEventImpl) after.get(0)).getOriginalEvent().getEventId(), 1,
                "the original eventId");
        reset();

        before = Lists.newArrayList();
        add(before, update, "a", timestamp); //removed
        add(before, create, "a", timestamp); //regular dedup - create is higher priority
        after = behavior.apply(before);
        assertEquals(after.size(), 1);
        assertEquals(after.get(0).toString(), "%s|2|create|a".formatted(timestamp), "no merge needed");
        reset();
        before = Lists.newArrayList();
        add(before, create, "a", timestamp); //removed
        add(before, create, "b", timestamp);
        add(before, update, "a", timestamp); //merged with create
        after = behavior.apply(before);
        assertEquals(after.size(), 2);
        assertEquals(after.get(0).toString(), "%s|2|create|b".formatted(timestamp));
        assertEquals(after.get(1).toString(), "%s|3|create|a".formatted(timestamp),
                "event merged: last id with prioritized type");
        assertEquals(after.get(1).getOriginalEvent().getEventId(), 1, "the original eventId");

        reset();
        before = Lists.newArrayList();
        add(before, create, "a", timestamp); //removed
        add(before, update, "a", timestamp); //removed
        add(before, update, "a", timestamp); //merged with create
        after = behavior.apply(before);
        assertEquals(after.size(), 1);
        assertEquals(after.get(0).toString(), "%s|3|create|a".formatted(timestamp),
                "event merged: last id with prioritized type");
        assertEquals(after.get(0).getOriginalEvent().getEventId(), 1, "the original eventId");

        reset();
        before = Lists.newArrayList();
        add(before, create, "a", timestamp);
        add(before, props, "a", timestamp); ///props interfere in the middle of create and update
        add(before, update, "a", timestamp);
        after = behavior.apply(before);
        assertEquals(after.size(), 3);
        assertEquals(after, before, "props interfere in the middle of create and update");
        reset();
        before = Lists.newArrayList();
        add(before, create, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, update, "a", timestamp);
        add(before, update, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, update, "a", timestamp); //last event is update, so props still in the middle
        after = behavior.apply(before);
        assertEquals(after.size(), 3);
        assertEquals(after.get(0).toString(), "%s|1|create|a".formatted(timestamp));
        assertEquals(after.get(1).toString(), "%s|6|props|a".formatted(timestamp));
        assertEquals(after.get(2).toString(), "%s|7|update|a".formatted(timestamp));
        reset();
        before = Lists.newArrayList();
        add(before, create, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, create, "b", timestamp);
        add(before, update, "a", timestamp);
        add(before, update, "a", timestamp); //create will be merged to here
        add(before, props, "a", timestamp);  //props will be after crete
        after = behavior.apply(before);
        assertEquals(after.size(), 3);
        assertEquals(after.get(0).toString(), "%s|4|create|b".formatted(timestamp));
        assertEquals(after.get(1).toString(), "%s|6|create|a".formatted(timestamp));
        assertEquals(after.get(2).toString(), "%s|7|props|a".formatted(timestamp));
    }

    public void prioritizeWith3Types() {
        EventReductionBehavior behavior = new FederatedDedupBehavior();
        List<QueueEventImpl> before;
        List<QueueEventImpl> after;
        long timestamp = System.currentTimeMillis();
        before = Lists.newArrayList();
        add(before, update, "b", timestamp);
        add(before, create, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "a", timestamp);
        add(before, props, "b", timestamp);
        add(before, update, "a", timestamp);
        add(before, update, "a", timestamp);
        add(before, props, "a", timestamp);
        after = behavior.apply(before);
        assertEquals(after.size(), 2);
        assertEquals(after.get(0).toString(), "%s|5|update|b".formatted(timestamp), "regular prioritize dedup");
        assertEquals(after.get(1).toString(), "%s|8|create|a".formatted(timestamp), "merged prioritize dedup");
        assertEquals(after.get(1).getOriginalEvent().getEventId(), 2, "the original eventId");
    }

    public void illegalDedupBehaviour() {
        //must crete with at least 2 types (0 or 1 are not illegal)
        assertThrows(IllegalArgumentException.class, PrioritizedEventDedupBehavior::new);
        assertThrows(IllegalArgumentException.class, () -> new PrioritizedEventDedupBehavior(update));
    }

    private void setProperty(ConstantValues property, String value) {
        ArtifactorySystemProperties systemProperties = new ArtifactorySystemProperties();
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        systemProperties.setProperty(property.getPropertyName(), value);
    }
}
