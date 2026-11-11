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
import org.testng.annotations.Test;

import java.util.List;

import static org.artifactory.event.EventType.create;
import static org.artifactory.event.EventType.delete;
import static org.artifactory.event.EventType.props;
import static org.artifactory.event.EventType.update;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

@Test
public class QueueReductionBehaviourTest extends AbstractEventReductionBehaviorTest {

    @Mock
    private ArtifactoryHome home;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        setProperty(ConstantValues.federatedSkipCreateEliminateDeleteOnDeduplication, "false");
        ArtifactoryHome.bind(home);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        autoCloseable.close();
    }

    public void chainBasicBehaviours() {
        QueueReductionBehaviours behaviors = QueueReductionBehaviours.Basic;

        List<QueueEventImpl> input;
        List<QueueEventImpl> output;

        input = Lists.newArrayList();
        long timestamp = System.currentTimeMillis();
        add(input, create, "a", timestamp); //because of DeleteEliminateUpdateOrCreateBehaviour
        add(input, delete, "a", timestamp); //because of DeleteEliminateUpdateOrCreateBehaviour
        output = QueueReductionBehavioursImpl.get(behaviors).applyChain(input);
        assertEquals(output.size(), 0);

        input = Lists.newArrayList();
        add(input, delete, "a", timestamp); //because of CreateEliminateDeleteBehaviour
        add(input, create, "a", timestamp); //because of DeleteEliminateUpdateOrCreateBehaviour
        add(input, delete, "a", timestamp); //because of DeleteEliminateUpdateOrCreateBehaviour
        output = QueueReductionBehavioursImpl.get(behaviors).applyChain(input);
        assertEquals(output.size(), 0);

        reset();
        input = Lists.newArrayList();
        add(input, create, "a", timestamp); //removed 2nd because of BasicEventDedupBehaviour
        add(input, delete, "a", timestamp); //removed 1st because of CreateEliminateDeleteBehaviour
        add(input, create, "a", timestamp);
        output = QueueReductionBehavioursImpl.get(behaviors).applyChain(input);
        assertEquals(output.size(), 1);
        assertEquals(output.get(0).toString(), "%s|3|create|a".formatted(timestamp));
    }


    public void chainIncrementalIndexBehaviours() {
        QueueReductionBehaviours behaviors = QueueReductionBehaviours.IncrementalIndex;

        List<QueueEventImpl> input;
        List<QueueEventImpl> output;
        long timestamp = System.currentTimeMillis();
        input = Lists.newArrayList();
        add(input, create, "a", timestamp); //because of DeleteEliminateUpdateOrCreateBehaviour
        add(input, delete, "a", timestamp); //because of DeleteEliminateUpdateOrCreateBehaviour
        output = QueueReductionBehavioursImpl.get(behaviors).applyChain(input);
        assertEquals(output.size(), 0);

        input = Lists.newArrayList();
        add(input, delete, "a", timestamp); //because of DeleteEliminateUpdateOrCreateBehaviour
        add(input, create, "a", timestamp); //because of DeleteEliminateUpdateOrCreateBehaviour
        add(input, delete, "a", timestamp);
        output = QueueReductionBehavioursImpl.get(behaviors).applyChain(input);
        assertEquals(output.size(), 1);
        assertEquals(output.get(0).toString(), "%s|3|delete|a".formatted(timestamp));

        reset();
        input = Lists.newArrayList();
        add(input, create, "a", timestamp);
        add(input, delete, "a", timestamp); //removed 1st because of DeleteEliminateUpdateOrCreateBehavior
        add(input, create, "a", timestamp); //removed 2nd because of FederatedDedupBehavior
        output = QueueReductionBehavioursImpl.get(behaviors).applyChain(input);
        assertEquals(output.size(), 1);
        assertEquals(output.get(0).toString(), "%s|3|create|a".formatted(timestamp));

        reset();
        input = Lists.newArrayList();
        add(input, update, "b", timestamp);
        add(input, create, "a", timestamp);
        add(input, props, "a", timestamp);
        add(input, props, "a", timestamp);
        add(input, props, "b", timestamp);
        add(input, update, "a", timestamp);
        add(input, update, "a", timestamp);
        add(input, props, "a", timestamp);
        output = QueueReductionBehavioursImpl.get(behaviors).applyChain(input);
        assertEquals(output.size(), 2);
        assertEquals(output.get(0).toString(), "%s|5|update|b".formatted(timestamp),
                "regular prioritize dedup");
        assertEquals(output.get(1).toString(), "%s|8|create|a".formatted(timestamp),
                "merged prioritize dedup");

        assertEquals(output.get(1).getOriginalEvent().getEventId(), 2);
    }

    private void setProperty(ConstantValues property, String value) {
        ArtifactorySystemProperties systemProperties = new ArtifactorySystemProperties();
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        systemProperties.setProperty(property.getPropertyName(), value);
    }
}
