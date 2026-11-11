package org.artifactory.storage.db.event.itest.service;

import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.service.InternalEventsService;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.ClockUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class EventsServiceImplCleanupEventsITest extends DbBaseTest {

    @Autowired
    private InternalEventsService internalEventsService;

    @Autowired
    private EventsService eventsService;

    @AfterMethod
    public void cleanup() throws Exception {
        internalEventsService.deleteAll();
    }

    @Test
    public void testDeleteRange() throws Exception{
        eventsService.appendEvents(createEvents(5));
        eventsService.shiftEvents();
        Thread.sleep(100);

        long start = ClockUtils.epochMillis();
        eventsService.appendEvents(createEvents(5));
        eventsService.shiftEvents();
        long end = ClockUtils.epochMillis();

        Thread.sleep(100);
        eventsService.appendEvents(createEvents(5));
        eventsService.shiftEvents();

        assertEquals(internalEventsService.getAllEvents().size(), 15);
        eventsService.deleteRange(start, end+1);
        assertEquals(internalEventsService.getAllEvents().size(), 10);
    }

    private List<EventInfo> createEvents(int count){
        List<EventInfo> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(new EventInfo(EventType.create, "/a/b"));
        }
        return events;
    }
}
