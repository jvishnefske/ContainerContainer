package org.artifactory.storage.db.event.entity;

import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Unit tests for {@link EventRecordHints}
 *
 * @author Avishay Halpren
 */
@Test
public class EventRecordHintsTest {

    public void parseFull() {
        Set<String> before = new HashSet<>();
        before.add("hint1.value1");
        before.add("hint2.value2");
        before.add("hint3.true");
        before.add("hint4.false");
        String hintsJson = EventRecordHints.toJson(before);
        Set<String> after = EventRecordHints.fromJson(hintsJson);
        assertEquals(before, after);
    }

    public void parseNull() {
        assertNull(EventRecordHints.toJson(null));
        assertNull(EventRecordHints.fromJson(null));
        assertNull(EventRecordHints.fromJson(""));
    }

    public void parseEmptyAsNull() {
        Set<String> before = new HashSet<>();
        String hintsJson = EventRecordHints.toJson(before);
        Set<String> after = EventRecordHints.fromJson(hintsJson);
        assertNull(after);
    }
}