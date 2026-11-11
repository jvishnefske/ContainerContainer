package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.error.dao.NodeEventsErrorsDAO;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class EventsDaoDeleteRangeTest extends DbBaseTest {

    @Autowired
    private EventsDao dao;

    @Autowired
    private NodeEventsErrorsDAO errorsDAO;

    @BeforeMethod
    public void setup() throws SQLException {
        dao.create(new EventRecord(1L, 1L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(2L, 2L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(3L, 3L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(4L, 4L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(5L, 5L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(6L, 6L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(7L, 7L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(8L, 8L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(9L, 9L, EventType.create, "/a/b/c"));
        dao.create(new EventRecord(10L, 10L, EventType.create, "/a/b/c"));
        dao.shiftAndDeleteTmpEvents(false);
        assertEquals(dao.loadAll().size(), 10);
    }

    @AfterMethod
    public void cleanup() throws SQLException {
        dao.deleteAll();
    }

    @Test
    public void testDeleteRangeWithError() throws SQLException{

        List<EventRecord> eventsBeforeDelete = dao.loadAll();
        EventRecord event5 = eventsBeforeDelete.stream().filter(e -> e.getTimestamp() == 5L).findFirst().orElseThrow();
        EventRecord event6 = eventsBeforeDelete.stream().filter(e -> e.getTimestamp() == 6L).findFirst().orElseThrow();
        EventRecord event7 = eventsBeforeDelete.stream().filter(e -> e.getTimestamp() == 7L).findFirst().orElseThrow();

        errorsDAO.insert(NodeEventsErrorsDBO.builder()
                .eventId(event5.getEventId())
                .eventTime(event5.getTimestamp())
                .errorMessage("error")
                .eventCategory("cat")
                .operationKey("op-key")
                .eventType(EventType.create.code())
                .errorId(100)
                .path(event5.getPath())
                .build());

        errorsDAO.insert(NodeEventsErrorsDBO.builder()
                .eventTime(event6.getTimestamp())
                .errorMessage("error")
                .eventCategory("cat")
                .operationKey("op-key")
                .eventType(EventType.create.code())
                .errorId(101)
                .path(event6.getPath())
                .build());

        eventsBeforeDelete.remove(event6);
        eventsBeforeDelete.remove(event7);

        dao.deleteRange(5L, 8L);
        List<EventRecord> eventAfterDelete = dao.loadAll();
        assertEquals(eventAfterDelete.size(), 8);

        assertEquals(
                eventAfterDelete.stream().map(EventRecord::getTimestamp).sorted().collect(Collectors.toList()),
                eventsBeforeDelete.stream().map(EventRecord::getTimestamp).sorted().collect(Collectors.toList())
        );
    }
    @Test
    public void testDeleteRange() throws SQLException {
        dao.deleteRange(5L, 8L);
        List<EventRecord> eventRecordsAfterDelete = dao.loadAll();
        assertEquals(eventRecordsAfterDelete.size(), 7);
        assertTrue(eventRecordsAfterDelete.stream().anyMatch(r -> r.getTimestamp() == 8L));
        assertTrue(eventRecordsAfterDelete.stream().noneMatch(r -> r.getTimestamp() == 5L));
        assertTrue(eventRecordsAfterDelete.stream().noneMatch(r -> r.getTimestamp() == 6L));
        assertTrue(eventRecordsAfterDelete.stream().noneMatch(r -> r.getTimestamp() == 7L));
    }

    @Test
    public void testDeleteOlderThanMaxLongNewerThanZero() throws SQLException {
        dao.deleteRange(0L, Long.MAX_VALUE);
        List<EventRecord> eventRecordsAfterDelete = dao.loadAll();
        assertEquals(eventRecordsAfterDelete.size(), 0);
    }

    @Test
    public void testOutOfRangeRangeIsLarge() throws SQLException {
        dao.deleteRange(Long.MAX_VALUE, Long.MAX_VALUE - 10);
        List<EventRecord> eventRecordsAfterDelete = dao.loadAll();
        assertEquals(eventRecordsAfterDelete.size(), 10);
    }

    @Test
    public void testOutOfRangeRangeIsSmall() throws SQLException {
        dao.deleteRange(Long.MIN_VALUE, Long.MIN_VALUE + 100);
        List<EventRecord> eventRecordsAfterDelete = dao.loadAll();
        assertEquals(eventRecordsAfterDelete.size(), 10);
    }

    @Test
    public void testDeleteOlderThanMaxTimestampInTable() throws SQLException {
        dao.deleteRange(100L, 110L);
        List<EventRecord> eventRecordsAfterDelete = dao.loadAll();
        assertEquals(eventRecordsAfterDelete.size(), 10);
    }

}
