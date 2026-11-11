package org.artifactory.storage.db.event.itest.dao;

import com.google.common.collect.Sets;
import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.common.ClockUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test the shift events {@link EventsDao}
 *
 * @author shaharz
 */
@Test
public class EventsDaoTmpEventsTest extends DbBaseTest {

    @Autowired
    private EventsDao eventsDao;

    @BeforeMethod
    private void setup() throws Exception {
        importSql("/sql/events.sql", false);
        assertCount("node_events_tmp", 18);
        assertCount("node_events", 0);
    }

    @AfterMethod
    private void clean() throws Exception {
        eventsDao.deleteAll();
    }

    @Test
    public void testShiftAndDeleteTmpEvents() throws SQLException {
        Set<String> repos = eventsDao.shiftAndDeleteTmpEvents(false);
        Assert.assertEquals(repos.size(), 3);
        repos.forEach(repo -> {
            Assert.assertTrue(Sets.newHashSet("repo1", "repo2", "repo-no-create").contains(repo));
        });
        assertCount("node_events_tmp", 0);
        assertCount("node_events", 18);

        List<EventRecord> events = eventsDao.loadAll();
        Assert.assertEquals(events.size(), 18);
        //Verify the shifted events has positive event id
        events.forEach(event -> Assert.assertTrue(event.getEventId() >= 1));

        //Verify that events with the same timestamp will shifted sorted by tmp_event_id
        List<EventRecord> subEvents = events.stream().filter(e -> e.getTimestamp() == 1515653241185L)
                .collect(Collectors.toList());
        Assert.assertEquals(subEvents.get(0).getPath(), "repo1/ant/");
        Assert.assertEquals(subEvents.get(1).getPath(), "repo1/ant/1.2/");
        Assert.assertEquals(subEvents.get(2).getPath(), "repo1/ant/ant/");

        // Get the last one
        EventRecord last = events.stream().max(Comparator.comparingLong(EventRecord::getEventId)).orElseThrow();

        //Sort by id
        List<EventRecord> sortedById = events.stream()
                .sorted(Comparator.comparingLong(EventRecord::getEventId)).collect(Collectors.toList());

        // Sort by time
        List<EventRecord> sortedByTime = events.stream()
                .sorted(Comparator.comparingLong(EventRecord::getEventId)).collect(Collectors.toList());
        // Verify that the shift was sorted by time
        Assert.assertEquals(sortedById, sortedByTime);

        // Create new event and shift it
        EventRecord newEvent = new EventRecord(-1, ClockUtils.epochMillis(), EventType.delete, "repo3/path", "hints");
        eventsDao.create(newEvent);
        assertCount("node_events_tmp", 1);
        Assert.assertEquals(eventsDao.shiftAndDeleteTmpEvents(false), Sets.newHashSet("repo3"));
        assertCount("node_events_tmp", 0);
        // Verify the new events count
        assertCount("node_events", 19);
        events = eventsDao.loadAll();
        Assert.assertEquals(events.size(), 19);
        // Get the last one
        EventRecord newLast = events.stream().max(Comparator.comparingLong(EventRecord::getEventId)).orElseThrow();

        // Verify the last one got id > previous last one
        Assert.assertTrue(newLast.getEventId() > last.getEventId());
        // Compare last event with the inserted event
        Assert.assertEquals(newLast.getTimestamp(), newEvent.getTimestamp());
        Assert.assertEquals(newLast.getPath(), newEvent.getPath());
        Assert.assertEquals(newLast.getType(), newEvent.getType());
        Assert.assertEquals(newLast.getHints(), newEvent.getHints());

        Assert.assertEquals(eventsDao.shiftAndDeleteTmpEvents(false).size(), 0);
    }

    private void assertCount(String table, int expected) throws SQLException {
        Assert.assertEquals(jdbcHelper.executeSelectCount("SELECT COUNT(1) FROM " + table), expected);
    }
}
