package org.artifactory.throttling.lh.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * @author Amir Avidan
 */
class ActivityConfigTest {
    @Test
    void testActivityConfigCreation() {
        ActivityConfig config = new ActivityConfig("testActivity", 100, 200, 5000, ReactiveActivityState.IDLE);
        assertNotNull(config);
        assertEquals("testActivity", config.name());
        assertEquals(100, config.minThreshold());
        assertEquals(200, config.maxThreshold());
        assertEquals(5000, config.healthyTimeout());
        assertEquals(ReactiveActivityState.IDLE, config.reactiveState());
    }

    @Test
    void testSetReactiveState() {
        ActivityConfig config = new ActivityConfig("testActivity", 100, 200, 5000, ReactiveActivityState.IDLE);
        config.setReactiveState(ReactiveActivityState.REACTIVE);
        assertEquals(ReactiveActivityState.REACTIVE, config.reactiveState());
    }

    @Test
    void testEqualsAndHashCode() {
        ActivityConfig config1 = new ActivityConfig("testActivity", 100, 200, 5000, ReactiveActivityState.IDLE);
        ActivityConfig config2 = new ActivityConfig("testActivity", 100, 200, 5000, ReactiveActivityState.IDLE);
        ActivityConfig config3 = new ActivityConfig("anotherActivity", 150, 250, 6000, ReactiveActivityState.REACTIVE);

        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1.hashCode(), config3.hashCode());
    }

    @Test
    void testToString() {
        ActivityConfig config = new ActivityConfig("testActivity", 100, 200,
                5000, ReactiveActivityState.IDLE);
        String expectedString = "ActivityConfig(name=testActivity, minThreshold=100, maxThreshold=200," +
                " healthyTimeout=5000, reactiveState=IDLE)";
        assertEquals(expectedString, config.toString());
    }

    @Test
    void testEqualsWithNullAndDifferentClass() {
        ActivityConfig config = new ActivityConfig("testActivity", 100, 200, 5000, ReactiveActivityState.IDLE);
        assertNotEquals(null, config);
        assertNotEquals("not an ActivityConfig", config);
    }
}
