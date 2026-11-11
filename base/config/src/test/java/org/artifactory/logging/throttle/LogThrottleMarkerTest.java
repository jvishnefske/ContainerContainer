package org.artifactory.logging.throttle;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.testng.annotations.Test;

import java.util.Iterator;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class LogThrottleMarkerTest {

    @Test
    public void testAddMarker() {
        LogThrottleMarker logThrottleMarker = new LogThrottleMarker("log-throttle");
        Marker otherMarker = MarkerFactory.getMarker("random-marker");
        Marker newMarker = MarkerFactory.getMarker("new-marker");

        Iterator<Marker> iterator = logThrottleMarker.iterator();

        assertFalse(iterator.hasNext());

        logThrottleMarker.add(otherMarker);

        assertTrue(logThrottleMarker.hasChildren());

        logThrottleMarker.add(otherMarker);
        logThrottleMarker.add(logThrottleMarker);
        logThrottleMarker.add(MarkerFactory.getMarker("new-marker"));

        iterator = logThrottleMarker.iterator();

        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), otherMarker);
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), newMarker);
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testEqualsMarker() {
        LogThrottleMarker logThrottleMarker1 = new LogThrottleMarker("log-throttle");
        LogThrottleMarker logThrottleMarker2 = new LogThrottleMarker("log-throttle");

        assertEquals(logThrottleMarker1, logThrottleMarker2);
        assertFalse(logThrottleMarker1.equals(null));
    }

    @Test
    public void testRemoveMarker() {
        LogThrottleMarker logThrottleMarker = new LogThrottleMarker("log-throttle");
        Marker otherMarker = MarkerFactory.getMarker("random-marker");

        assertFalse(logThrottleMarker.remove(otherMarker));

        logThrottleMarker.add(MarkerFactory.getMarker("random-marker"));

        assertTrue(logThrottleMarker.hasChildren());

        logThrottleMarker.remove(MarkerFactory.getMarker("random-marker"));

        assertFalse(logThrottleMarker.hasChildren());
    }

    @Test
    public void testContainsMarker() {
        LogThrottleMarker logThrottleMarker = new LogThrottleMarker("log-throttle");
        Marker otherMarker = MarkerFactory.getMarker("random-marker");
        Marker notIncludedMarker = MarkerFactory.getMarker("not-included-marker");

        logThrottleMarker.add(otherMarker);

        assertTrue(logThrottleMarker.contains(otherMarker));
        assertTrue(logThrottleMarker.contains("random-marker"));

        assertFalse(logThrottleMarker.contains(notIncludedMarker));
        assertFalse(logThrottleMarker.contains("not-included-marker"));

        logThrottleMarker.remove(otherMarker);

        assertFalse(logThrottleMarker.contains(otherMarker));
        assertFalse(logThrottleMarker.contains("random-marker"));

        assertTrue(logThrottleMarker.contains(logThrottleMarker));
        assertTrue(logThrottleMarker.contains("log-throttle"));
    }
}
