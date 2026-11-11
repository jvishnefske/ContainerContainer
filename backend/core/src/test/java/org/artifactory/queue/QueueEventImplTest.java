package org.artifactory.queue;

import org.artifactory.event.EventType;
import org.artifactory.repo.event.EventInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Bilal Issa
 */
class QueueEventImplTest {
    
    private EventInfo event1;
    private QueueEventImpl queueEvent1, queueEvent2;
    
    @BeforeEach
    void setUp() {
        Set<String> hints = new HashSet<>();
        hints.add("hint1");
        hints.add("hint2");

        event1 = new EventInfo(1L, 1000L, EventType.create, "a/b/c", hints);
        EventInfo event2 = new EventInfo(2L, 2000L, EventType.props, "a/b/c", hints);
        
        queueEvent1 = new QueueEventImpl(event1);
        queueEvent2 = new QueueEventImpl(event2);
    }
    
    @Test
    void testGetEventId() {
        assertEquals(1L, queueEvent1.getEventId());
        assertEquals(2L, queueEvent2.getEventId());
    }
    
    @Test
    void testGetHints() {
        assertEquals(2, queueEvent1.getHints().size());
        assertTrue(queueEvent1.getHints().contains("hint1"));
        assertTrue(queueEvent1.getHints().contains("hint2"));
    }
    
    @Test
    void testGetRepoKey() {
        assertEquals("a", queueEvent1.getRepoKey());
        assertEquals("a", queueEvent2.getRepoKey());
    }
    
    @Test
    void testGetArtifactPath() {
        assertEquals("b/c", queueEvent1.getArtifactPath());
        assertEquals("b/c", queueEvent2.getArtifactPath());
    }
    
    @Test
    void testGetType() {
        assertEquals(EventType.create, queueEvent1.getType());
        assertEquals(EventType.props, queueEvent2.getType());
    }
    
    @Test
    void testGetTimestamp() {
        assertEquals(1000L, queueEvent1.getTimestamp());
        assertEquals(2000L, queueEvent2.getTimestamp());
    }
    
    @Test
    void testEqualsAndHashCode() {
        QueueEventImpl sameAsQueueEvent1 = new QueueEventImpl(event1);
        assertEquals(queueEvent1, sameAsQueueEvent1);
        assertEquals(queueEvent1.hashCode(), sameAsQueueEvent1.hashCode());
        assertNotEquals(queueEvent1, queueEvent2);
    }
    
    @Test
    void testToString() {
        assertEquals("1000|1|create|a/b/c", queueEvent1.toString());
        assertEquals("2000|2|props|a/b/c", queueEvent2.toString());
    }
}
