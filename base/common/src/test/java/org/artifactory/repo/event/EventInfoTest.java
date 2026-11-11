package org.artifactory.repo.event;

import org.artifactory.event.EventType;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * @author Alexei Vainshtein
 */
public class EventInfoTest {

    @Test
    public void testEquals() {
        EventInfo info1 = new EventInfo(EventType.create, "temp/path");
        EventInfo info2 = new EventInfo(EventType.create, "temp/path2");

        assertNotEquals(info2, info1);

        EventInfo info3 = new EventInfo(EventType.create, "temp/path");
        assertEquals(info1, info3);

        EventInfo info4 = new EventInfo(30L, EventType.create, "temp/path");
        assertEquals(info1, info4);

        Set<String> hints = new HashSet<>();
        hints.add("test");
        EventInfo info5 = new EventInfo(30L, EventType.create, "temp/path", hints);
        assertNotEquals(info1, info5);

        EventInfo info6 = new EventInfo(30L, EventType.create, "temp/path",null);
        EventInfo info7 = new EventInfo(31L, EventType.create, "temp/path", null);
        assertEquals(info6, info7);

        EventInfo info8 =
                new EventInfo(30, 30L, EventType.create, "temp/path", hints);
        EventInfo info9 =
                new EventInfo(31, 30L, EventType.create, "temp/path1", hints);
        assertNotEquals(info8, info9);

        EventInfo info10 = new EventInfo(EventType.create, "temp/path", hints);
        EventInfo info11 = new EventInfo(EventType.create, "temp/path", hints);
        assertEquals(info10, info11);

        EventInfo info12 = new EventInfo(EventType.create, "temp/path", hints);
        EventInfo info13 = new EventInfo(EventType.create, "temp/path1", hints);
        assertNotEquals(info12, info13);
    }

    @Test
    public void testHash() {
        EventInfo info1 = new EventInfo(EventType.create, "temp/path");
        EventInfo info2 = new EventInfo(EventType.create, "temp/path2");
        assertNotEquals(info2.hashCode(), info1.hashCode());

        EventInfo info4 = new EventInfo(30L, EventType.create, "temp/path");
        assertEquals(info1.hashCode(), info4.hashCode());

        Set<String> hints = new HashSet<>();
        hints.add("test");
        EventInfo info5 = new EventInfo(30L, EventType.create, "temp/path", hints);
        assertNotEquals(info1.hashCode(), info5.hashCode());
    }

}