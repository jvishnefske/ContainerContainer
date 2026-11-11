package org.artifactory.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadMarkerTest {

    @Test
    void mark_single() {
        ThreadMarker.mark("marker");
        assertTrue(ThreadMarker.isMarkedBy("marker"));
    }

    @Test
    void unmark_single() {
        String marker = "marker";

        ThreadMarker.mark(marker);
        assertTrue(ThreadMarker.isMarkedBy(marker));

        ThreadMarker.unmark(marker);
        assertFalse(ThreadMarker.isMarkedBy(marker));
    }

    @Test
    void unmark_empty_markers() {
        ThreadMarker.unmark("key");
        assertFalse(ThreadMarker.isMarkedBy("key"));
    }

    @Test
    void unmark_wrong_key() {
        String marker = "marker";

        ThreadMarker.mark(marker);
        assertTrue(ThreadMarker.isMarkedBy(marker));

        ThreadMarker.unmark("diff_key");
        assertTrue(ThreadMarker.isMarkedBy(marker));
    }

    @Test
    void mark_multiple() {
        String firstMarker = "marker";
        String secondMarker = "different_marker";

        ThreadMarker.mark(firstMarker);
        assertTrue(ThreadMarker.isMarkedBy(firstMarker));
        assertFalse(ThreadMarker.isMarkedBy(secondMarker));

        ThreadMarker.mark(secondMarker);
        assertTrue(ThreadMarker.isMarkedBy(firstMarker));
        assertTrue(ThreadMarker.isMarkedBy(secondMarker));

        ThreadMarker.unmark(firstMarker);
        assertFalse(ThreadMarker.isMarkedBy(firstMarker));
        assertTrue(ThreadMarker.isMarkedBy(secondMarker));

        ThreadMarker.unmark(secondMarker);
        assertFalse(ThreadMarker.isMarkedBy(firstMarker));
        assertFalse(ThreadMarker.isMarkedBy(secondMarker));
    }

    @Test
    void mark_multiple_with_data() {
        String firstMarker = "marker";
        String secondMarker = "different_marker";

        ThreadMarker.mark(firstMarker);
        ThreadMarker.updateMarkerData(firstMarker, Map.of("data-one", "data"));

        assertTrue(ThreadMarker.isMarkedBy(firstMarker));
        assertFalse(ThreadMarker.isMarkedBy(secondMarker));
        assertEquals("data", ThreadMarker.getMarkerData(firstMarker).get("data-one"));

        ThreadMarker.mark(secondMarker);
        ThreadMarker.updateMarkerData(secondMarker, Map.of("data-two", "data"));
        assertTrue(ThreadMarker.isMarkedBy(firstMarker));
        assertTrue(ThreadMarker.isMarkedBy(secondMarker));
        assertEquals("data", ThreadMarker.getMarkerData(secondMarker).get("data-two"));

        ThreadMarker.unmark(firstMarker);
        assertFalse(ThreadMarker.isMarkedBy(firstMarker));
        assertTrue(ThreadMarker.isMarkedBy(secondMarker));

        ThreadMarker.unmark(secondMarker);
        assertFalse(ThreadMarker.isMarkedBy(firstMarker));
        assertFalse(ThreadMarker.isMarkedBy(secondMarker));
    }

    @Test
    void unmarkAll() {
        String firstMarker = "marker";
        String secondMarker = "different_marker";

        ThreadMarker.mark(firstMarker);
        ThreadMarker.mark(secondMarker);

        assertTrue(ThreadMarker.isMarkedBy(firstMarker));
        assertTrue(ThreadMarker.isMarkedBy(secondMarker));

        ThreadMarker.unmarkAll();
        assertFalse(ThreadMarker.isMarkedBy(firstMarker));
        assertFalse(ThreadMarker.isMarkedBy(secondMarker));

        // make sure that the unmarkAll did not break ThreadMarker
        ThreadMarker.mark(firstMarker);
        assertTrue(ThreadMarker.isMarkedBy(firstMarker));
    }

    @Test
    void isMarkedBy_wrong_key() {
        ThreadMarker.mark("marker");
        assertFalse(ThreadMarker.isMarkedBy("different_marker"));
    }

    @Test
    void executeWithMarker() {
        String marker = "testMarker";
        Supplier<String> supplier = () -> {
            assertTrue(ThreadMarker.isMarkedBy(marker));
            return "result";
        };

        String result = ThreadMarker.executeWithMarker(marker, supplier);

        assertEquals("result", result);
        assertFalse(ThreadMarker.isMarkedBy(marker));
    }
}