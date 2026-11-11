package org.artifactory.storage.db.event.itest.dao;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.dao.StorageEventsDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.event.StorageEventInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author TamirH
 */
public class StorageEventsDaoTest extends DbBaseTest {

    private static final int SHA1_LENGTH = 40;
    private static final String SHA1_A = StringUtils.rightPad("A", SHA1_LENGTH, 'A');
    private static final String SHA1_B = StringUtils.rightPad("B", SHA1_LENGTH, 'B');
    private static final String SHA1_C = StringUtils.rightPad("C", SHA1_LENGTH, 'C');
    private static final String SHA1_D = StringUtils.rightPad("D", SHA1_LENGTH, 'D');
    private static final long EVENTS_COUNT = 6;
    @Autowired
    private StorageEventsDao dao;

    @BeforeClass
    public void setup() throws Exception {
        importSql("/sql/storage-events.sql");
    }

    @AfterClass
    public void clean() throws SQLException {
        dao.deleteAll();
    }

    @Test
    public void countEvents() throws SQLException {
        assertEquals(dao.getEventsCount(), EVENTS_COUNT);
    }

    @Test
    public void getEventsWithLimit() throws SQLException {
        final List<StorageEventInfo> gcEventInfos = dao.loadEventsByParams(100L, 2L);
        assertEquals(gcEventInfos.size(), 2);
        final StorageEventInfo eventInfo1 = gcEventInfos.get(0);
        final StorageEventInfo eventInfo2 = gcEventInfos.get(1);
        assertEquals(Math.min(eventInfo1.eventId(), eventInfo2.eventId()), 101);
        assertEquals(Math.max(eventInfo1.eventId(), eventInfo2.eventId()), 102);
        //This data comes from events 101 and 102 defined in file src/test/resources/sql/storage-events.sql
        assertNotNull(eventInfo1.data());
        assertEquals(eventInfo1.data().getRetryCount(), 1);
        assertNotNull(eventInfo2.data());
        assertEquals(eventInfo2.data().getRetryCount(), 42);
    }

    @Test
    public void createBulkOfEvents() throws SQLException {
        final String firstSha1 = "xcab88fc2a043c2479a6de676a2f8179e9ea2167";
        final String secondsSha1 = "xcab88fc2a043c2479a6de676a2f8179e9ea2169";
        dao.createBulk(List.of(new StorageEventInfo(EventType.delete, firstSha1,
                        "xbbb23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60bbbb", 3),
                new StorageEventInfo(EventType.delete, secondsSha1,
                        "xbbb23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60bbbX", 3)));
        assertEquals(dao.getEventsCount(), EVENTS_COUNT + 2);
        dao.deleteEventsBySha1(List.of(firstSha1, secondsSha1));
        assertEquals(dao.getEventsCount(), EVENTS_COUNT);
    }

    @Test
    public void createEvent() throws SQLException {
        final String firstSha1 = "xcab88fc2a043c2479a6de676a2f8179e9ea2167";
        final StorageEventInfo eventInfo = new StorageEventInfo(8, -1, EventType.delete, firstSha1,
                "xbbb23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60bbbb", 3, null);
        dao.create(eventInfo);
        assertEquals(dao.getEventsCount(), EVENTS_COUNT + 1);
        assertTrue(dao.loadEventsByParams(0, 100).stream().anyMatch(event -> event.sha1().equals(firstSha1)));
        dao.deleteEventsBySha1(List.of(firstSha1));
        assertEquals(dao.getEventsCount(), EVENTS_COUNT);
    }

    /*
    Test plan:
    1. store number of events    2
    2. add x events with sha1=A and y events with sha1=B:  C[0] D[1] A[2] A[3] B[4] B[5] A[6]
    3. get max ID of A and B (6 and 5)
    4. delete all previous events of sha1=B and sha1=B:  C[0] D[1] A[6] B[5]
    5. check that number of events = 2 + 2 = 4
    6. check that A and B are unique
    7. check that A has ID = 6 and B has ID = 5
     */
    @Test(dataProvider = "deleteAllEvents")
    public void deleteAllPreviousBySHA1(List<StorageEventInfo> newEvents) throws SQLException {
        // 1. store existing events
        List<StorageEventInfo> initialEvents = getAll();
        // 2. add x events with sha1=A and y events with sha1=B:  C[0] D[1] A[2] A[3] B[4] B[5] A[6]
        dao.createBulk(newEvents);
        Map<String, Integer> maxIDs = new HashMap<>();
        if (!newEvents.isEmpty()) {
            // 3. get max ID of A and B (6 and 5)
            try (ResultSet rs = jdbcHelper.executeSelect("""
                    SELECT sha1, MAX(event_id) AS event_id
                    FROM storage_events
                    WHERE sha1 IN (#)
                    GROUP BY sha1""", newEvents.stream().map(StorageEventInfo::sha1).collect(Collectors.toList()))) {
                while (rs.next()) {
                    String sha1 = rs.getString("sha1");
                    Integer maxID = rs.getInt("event_id");
                    maxIDs.put(sha1, maxID);
                }
            }
            // test
            // 4. delete all previous events of sha1=B and sha1=B.  C[0] D[1] A[6] B[5]
            newEvents.stream()
                    .distinct()
                    .map(StorageEventInfo::sha1)
                    .forEach(sha1 -> {
                        try {
                            dao.deleteAllPreviousBySHA1(sha1);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
            List<StorageEventInfo> remainedEvents = getAll();
            // restore initial events
            dao.deleteEventsBySha1(newEvents.stream().map(StorageEventInfo::sha1).collect(Collectors.toList()));

            // verify
            // 5. check that number of events = 2 + 2 = 4
            assertEquals(remainedEvents.size(), initialEvents.size() + newEvents.stream().distinct().count());
            // 6. check that A and B are unique
            newEvents.stream()
                    .distinct()
                    .map(StorageEventInfo::sha1)
                    .forEach(sha1 ->
                            assertEquals(remainedEvents.stream()
                                    .filter(e -> e.sha1().equals(sha1))
                                    .count(), 1));
            // 7. check that A has ID = 6 and B has ID = 5
            maxIDs.forEach((sha1, maxID) -> {
                StorageEventInfo event = remainedEvents.stream()
                        .filter(e -> e.sha1().equals(sha1))
                        .findFirst()
                        .orElseThrow();
                assertEquals(event.eventId(), (long) maxID);
            });
        }
    }

    @DataProvider
    private static Object[][] deleteAllEvents() {
        return new Object[][] {
                {List.of()},
                {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1)
                )},
                {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1)
                )},
                {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_C, "sha256", 3),
                        new StorageEventInfo(EventType.delete, SHA1_D, "sha256", 4)
                )},
                {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_C, "sha256", 3),
                        new StorageEventInfo(EventType.delete, SHA1_D, "sha256", 4),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_C, "sha256", 3)
                )}, {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1)
        )}
        };
    }

    private List<StorageEventInfo> getAll() {
        List<StorageEventInfo> res = new ArrayList<>();
        try (ResultSet rs = jdbcHelper.executeSelect("""
                SELECT *
                FROM storage_events
                WHERE 1 = ?""", 1)) {
            while (rs.next()) {
                StorageEventInfo event = ReflectionTestUtils.invokeMethod(dao, "eventFromResultSet", rs);
                res.add(event);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return res;
    }

    @Test(dataProvider = "getLatestEventsBySHA1")
    public void getLatestEventsBySHA1(List<StorageEventInfo> events, List<String> dedupedSHA1s) throws SQLException {
        dao.createBulk(events);
        List<StorageEventInfo> actual = dao.getLatestEventsBySHA1(dedupedSHA1s);
        List<StorageEventInfo> eventsWithMaxID = new ArrayList<>();
        if (!dedupedSHA1s.isEmpty()) {
            try (ResultSet rs = jdbcHelper.executeSelect("""
                    SELECT *
                    FROM storage_events
                    WHERE event_id IN (
                        SELECT MAX(event_id)
                        FROM storage_events
                        WHERE sha1 IN (#)
                    GROUP BY sha1)""", dedupedSHA1s)) {
                while (rs.next()) {
                    StorageEventInfo event = ReflectionTestUtils.invokeMethod(dao, "eventFromResultSet", rs);
                    eventsWithMaxID.add(event);
                }
            }
        }
        dao.deleteEventsBySha1(dedupedSHA1s);
        assertEquals(actual.size(), eventsWithMaxID.size());
        assertEqualsNoOrder(actual, eventsWithMaxID);
    }

    @DataProvider
    private static Object[][] getLatestEventsBySHA1() {
        return new Object[][] {
                {List.of(), List.of()},
                {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1)
                ), List.of(SHA1_A)},
                // >  A
                // 0--1
                {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1)
                ), List.of(SHA1_A)},
                // >  A  B  C  D
                // 0--1--2--3--4-->
                {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_C, "sha256", 3),
                        new StorageEventInfo(EventType.delete, SHA1_D, "sha256", 4)
                ), List.of(SHA1_A, SHA1_B, "x", SHA1_C, SHA1_D)},
                // >  A  B  B  A  C  D  B  <  B  C
                // 0--1--2--3--4--5--6--7--8--9--10-->
                {List.of(
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_A, "sha256", 1),
                        new StorageEventInfo(EventType.delete, SHA1_C, "sha256", 3),
                        new StorageEventInfo(EventType.delete, SHA1_D, "sha256", 4),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_B, "sha256", 2),
                        new StorageEventInfo(EventType.delete, SHA1_C, "sha256", 3)
                ), List.of(SHA1_A, SHA1_B, SHA1_C, SHA1_D)},
        };
    }
}