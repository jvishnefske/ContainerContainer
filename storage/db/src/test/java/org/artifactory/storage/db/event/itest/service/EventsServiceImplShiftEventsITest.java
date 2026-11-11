package org.artifactory.storage.db.event.itest.service;

import org.artifactory.event.EventType;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.event.EventsService;
import org.artifactory.test.TestUtils;
import org.jfrog.common.ClockUtils;
import org.mockito.internal.util.collections.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorType.FEDERATED_REPLICATION;
import static org.testng.Assert.assertEquals;

public class EventsServiceImplShiftEventsITest extends DbBaseTest {

    @Autowired
    private EventsService eventsService;
    @Autowired
    private EventsDao eventsDao;

    @BeforeMethod
    private void setup() throws Exception{
        importSql("/sql/events.sql", false);
        for(int i = 0; i < eventsDao.getTempEventsCount(); i++) {
            TestUtils.invokeMethodNoArgs(dbService, "nextId");
        }
        assertCount("node_events_tmp", 18);
        assertCount("node_events", 0);
    }

    @AfterMethod
    private void clean() throws Exception{
        eventsDao.deleteAll();
        jdbcHelper.executeUpdate("DELETE FROM node_event_cursor");
    }

    @Test
    public void testShiftEventsTx() throws Exception {
        // Verify the shift copied and delete the tmp events in one TX
        Set<String> repos = eventsService.shiftEvents();
        Assert.assertNotNull(repos);
        assertEquals(repos.size(), 3);
        assertEquals(repos, Sets.newSet("repo1", "repo2", "repo-no-create"));
        assertCount("node_events_tmp", 0);
        assertCount("node_events", 18);
    }

    @Test
    public void testGetTempEventsCount() {
        long tempEventsCount = eventsService.getTempEventsCount();
        assertEquals(18, tempEventsCount);
    }

    @Test
    public void testAppendEvents() throws Exception{
        List<EventInfo> tmpEvents = Arrays.asList (
                new EventInfo(123, EventType.create, "/repo1/a", Sets.newSet("hints1")),
                new EventInfo(123, EventType.delete, "/repo1/b", Sets.newSet("hints2"))
        );
        Thread.sleep(10);
        long timestampBeforeAppend = ClockUtils.epochMillis();
        Thread.sleep(10);
        eventsService.appendEvents(tmpEvents);

        assertCount("node_events_tmp", 20);

        Thread.sleep(10);
        long timestampBeforeShift = ClockUtils.epochMillis();
        Thread.sleep(10);
        eventsService.shiftEvents();

        //Verify that new timestamp was added to each event while inserted to node_events_tmp
        List<EventInfo> events = eventsService.getEventsAfter(timestampBeforeAppend, "repo1");
        events.forEach(event -> Assert.assertTrue(event.getTimestamp() > timestampBeforeAppend));
        events.forEach(event -> Assert.assertTrue(event.getTimestamp() < timestampBeforeShift));

    }

    @Test
    public void testEventsLeftForCursorToReach() throws Exception {
        importSql("/sql/node_events.sql", false);
        assertCount("node_events", 19);

        List<EventInfo> eventInfos = eventsService.getRemainingEventsInQueue(
                "http://0.0.0.0:8081/artifactory/repo1", FEDERATED_REPLICATION.getName(), "repo1",
                8);
        assertEquals(6, eventInfos.size());
        eventInfos = eventsService.getRemainingEventsInQueue(
                "http://0.0.0.0:8081/artifactory/repo1-another", FEDERATED_REPLICATION.getName(),
                "repo1-another",
                80);
        assertEquals(2, eventInfos.size());
        eventInfos = eventsService.getRemainingEventsInQueue(
                "http://0.0.0.0:8081/artifactory/repo1", FEDERATED_REPLICATION.getName(), "repo1",
                80);
        assertEquals(7, eventInfos.size());
        eventInfos = eventsService.getRemainingEventsInQueue(
                "http://0.0.0.0:8081/artifactory/repo2", FEDERATED_REPLICATION.getName(), "repo2",
                80);
        assertEquals(10, eventInfos.size());
    }

    @Test
    void testGtEventIdByTimestamp() throws Exception {
        importSql("/sql/node_events.sql", false);
        assertCount("node_events", 19);
        long eventIdByTimestamp = eventsService.getFirstEventIdAfterProvidedTimestamp(1515653250004L, "repo1",
                FEDERATED_REPLICATION.getName());
        assertEquals(4, eventIdByTimestamp);
    }

    @Test
    void testGtEventIdByTimestampNoRepo() throws Exception {
        importSql("/sql/node_events.sql", false);
        assertCount("node_events", 19);
        long eventIdByTimestamp = eventsService.getFirstEventIdAfterProvidedTimestamp(1515653250004L, null,
                FEDERATED_REPLICATION.getName());
        assertEquals(4, eventIdByTimestamp);
        eventIdByTimestamp = eventsService.getFirstEventIdAfterProvidedTimestamp(1515653250060L, null,
                FEDERATED_REPLICATION.getName());
        assertEquals(15, eventIdByTimestamp);
    }

    @Test
    void testGtEventIdByTimestampNoRepoPresented() {
        importSql("/sql/node_events.sql", false);
        long eventIdByTimestamp = eventsService.getFirstEventIdAfterProvidedTimestamp(1515653250004L, "No_repo1",
                FEDERATED_REPLICATION.getName());
        assertEquals(-1, eventIdByTimestamp);
    }

    private void assertCount(String table, int expected) throws Exception {
        assertEquals(jdbcHelper.executeSelectCount("SELECT COUNT(1) FROM " + table), expected);
    }
}
