/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.event.itest.dao;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.entity.EventsDaoParams;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

/**
 * Units tests for {@link EventsDao}.
 *
 * @author Yossi Shaul
 */
@Test
public class EventsDaoTest extends DbBaseTest {

    private static final long FIRST_NODE_EVENTS_TS = 1515653241184L;
    private static final long SECOND_NODE_EVENTS_TS = 1515653241185L;
    private static final int EVENTS_COUNT = 18;

    @Autowired
    private EventsDao dao;

    @BeforeClass
    public void setup() throws Exception {
        importSql("/sql/nodes.sql");
    }

    @AfterClass
    public void clean() throws SQLException {
        dao.deleteAll();
        ArtifactoryHomeStub.unbind();
    }

    @Test
    public void countEvents() throws SQLException {
        assertEquals(dao.getEventsCount(), EVENTS_COUNT);
    }

    @Test
    public void allEvents() throws SQLException {
        assertThat(dao.loadAll()).hasSize(EVENTS_COUNT);
    }

    public void allEventsStream() throws SQLException {
        CompletableFuture<SQLException> onException = new CompletableFuture<>();
        List<EventRecord> lst = dao.loadAllStream(onException).collect(toList());
        if (onException.isDone()) {throw onException.join();}
        assertThat(lst).hasSize(EVENTS_COUNT);
    }

    @Test
    public void allEventsOrdered() throws SQLException {
        List<EventRecord> result = dao.loadAll();
        EventRecord[] ordered = result.stream().sorted().toArray(EventRecord[]::new);
        assertThat(result).hasSize(EVENTS_COUNT).containsExactly((Object[]) ordered);
    }


    @Test(dependsOnMethods = {"countEvents", "allEvents", "allEventsOrdered"})
    public void createDirectoryNode() throws SQLException {
        EventRecord e = new EventRecord(50, System.currentTimeMillis(), EventType.create, "repo5/land/");
        dao.create(e);
        dao.shiftAndDeleteTmpEvents(false);
    }

    public void firstEventTimestamp() throws SQLException {
        assertThat(dao.getFirstEventTimestamp()).isEqualTo(FIRST_NODE_EVENTS_TS);
    }

    public void firstEventId() throws SQLException {
        EventRecord event = dao.loadEventsByParams(EventsDaoParams.builder()
                        .afterRepoColumnMigration(false)
                        .repoKey("repo1")
                        .build())
                .stream()
                .min(Comparator.comparingLong(EventRecord::getEventId)).get();

        assertThat(dao.getFirstEventId("repo1")).isEqualTo(event.getEventId());
    }

    public void lastEventTimestamp() throws SQLException {
        assertThat(dao.getLastEventTimestamp()).isEqualTo(1515653250004L);
    }

    public void lastEventId() throws SQLException {
        EventRecord event = dao.loadAll().stream().max(Comparator.comparingLong(EventRecord::getEventId)).get();
        assertThat(dao.getLastEventId()).isEqualTo(event.getEventId());
    }

    public void getOldestTimestampByEventIdInclusive() throws SQLException {
        long time = System.currentTimeMillis();
        try {
            EventRecord e1 = new EventRecord(dbService.nextId(), time, EventType.create, "repo6/a", null);
            EventRecord e2 = new EventRecord(dbService.nextId(), time + 1000, EventType.create, "repo6/a/b", null);
            EventRecord e3 = new EventRecord(dbService.nextId(), time + 2000, EventType.create, "repo6/a/b/c", null);

            dao.create(e1);
            dao.create(e2);
            dao.create(e3);
            dao.shiftAndDeleteTmpEvents(false);
            EventRecord e4 = new EventRecord(dbService.nextId(), time + 500, EventType.create, "repo6/a/c", null);
            dao.create(e4);
            dao.shiftAndDeleteTmpEvents(false);


            EventRecord event = dao.loadEventsByParams(EventsDaoParams.builder()
                            .repoKey("repo6")
                            .afterRepoColumnMigration(false)
                            .build())
                    .stream().filter(e -> e.getPath().equals("repo6/a/c"))
                    .findFirst().get();

            Assert.assertEquals(dao.getOldestTimestampByEventIdInclusive(event.getEventId()), e4.getTimestamp());
        } finally {
            //Delete the events in order not to break other tests
            dao.deleteRange(time, time + 2001);
        }
    }

    public void loadByParams1() throws SQLException {
        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder().build());
        assertThat(result).hasSize(EVENTS_COUNT);
        assertThat(result.get(0).getTimestamp()).isEqualTo(FIRST_NODE_EVENTS_TS);
    }

    public void loadByParams2() throws SQLException {
        long firstEventId =
                dao.loadAll().stream().min(Comparator.comparingLong(EventRecord::getEventId)).get().getEventId();

        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(firstEventId)
                .inclusive(true)
                .build());
        assertThat(result).hasSize(18);
        assertThat(result.get(0).getTimestamp()).isEqualTo(FIRST_NODE_EVENTS_TS);
    }

    public void loadByParams3() throws SQLException {

        long firstEventId =
                dao.loadAll().stream().min(Comparator.comparingLong(EventRecord::getEventId)).get().getEventId();

        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(firstEventId)
                .inclusive(false)
                .build());
        assertThat(result).hasSize(17);
        assertThat(result.get(0).getTimestamp()).isEqualTo(SECOND_NODE_EVENTS_TS);
    }

    public void loadByParams4() throws SQLException {

        long firstEventId =
                dao.loadAll().stream().min(Comparator.comparingLong(EventRecord::getEventId)).get().getEventId();


        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(firstEventId)
                .limit(1)
                .build());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTimestamp()).isEqualTo(SECOND_NODE_EVENTS_TS);
    }


    public void loadByParams5() throws SQLException {
        long firstEventId =
                dao.loadAll().stream().min(Comparator.comparingLong(EventRecord::getEventId)).get().getEventId();

        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(firstEventId)
                .limit(1)
                .inclusive(true)
                .build());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTimestamp()).isEqualTo(FIRST_NODE_EVENTS_TS);
    }

    public void loadByParams6() throws SQLException {
        long firstEventId =
                dao.loadAll().stream().min(Comparator.comparingLong(EventRecord::getEventId)).get().getEventId();

        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(firstEventId)
                .repoKey("repo1")
                .afterRepoColumnMigration(false)
                .build());
        assertThat(result).hasSize(10);
    }

    public void loadByParams6WithEmptySuffix() throws SQLException {
        long firstEventId =
                dao.loadAll().stream().min(Comparator.comparingLong(EventRecord::getEventId)).get().getEventId();

        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(firstEventId)
                .repoKey("repo1")
                .suffix("")
                .afterRepoColumnMigration(false)
                .build());
        assertThat(result).hasSize(10);
    }

    public void loadByParams7() throws SQLException {
        long firstEventId =
                dao.loadAll().stream().min(Comparator.comparingLong(EventRecord::getEventId)).get().getEventId();

        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(firstEventId)
                .repoKey("repo1")
                .suffix(".jar")
                .afterRepoColumnMigration(false)
                .build());
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPath()).startsWith("repo1");
        assertThat(result.get(0).getPath()).endsWith(".jar");
    }

    public void loadByParams8() throws SQLException {
        long firstEventId =
                dao.loadAll().stream().min(Comparator.comparingLong(EventRecord::getEventId)).get().getEventId();

        List<EventRecord> result = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(firstEventId)
                .suffix(".pom")
                .build());
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPath()).endsWith(".pom");
    }

    public void loadEventsBetweenLoadAll() {
        assertThat(dao.loadIntervalStream(0, Long.MAX_VALUE, "repo1", 100, new CompletableFuture<>())
                .count())
                .isEqualTo(11);
    }

    public void loadEventsBetweenTestNonInclusive() {
        assertThat(dao.loadIntervalStream(FIRST_NODE_EVENTS_TS, 1515653241293L, "repo1", 100, new CompletableFuture<>())
                .count())
                .isEqualTo(7);
    }

    public void loadEventsBetweenTestNonInclusiveAndLimit() {
        assertThat(dao.loadIntervalStream(FIRST_NODE_EVENTS_TS, 1515653241293L, "repo1", 2, new CompletableFuture<>())
                .count())
                .isEqualTo(2);
    }

    public void loadEventsBetweenTestSorted() {
        List<EventRecord> events =
                dao.loadIntervalStream(FIRST_NODE_EVENTS_TS, 1515653241293L, "repo1", 100, new CompletableFuture<>())
                        .collect(Collectors.toList());
        List<EventRecord> sortedEvents = new ArrayList<>(events);
        Collections.sort(sortedEvents);
        assertThat(events).isEqualTo(sortedEvents);
    }

    public void loadAllNewerStreamEvents() throws SQLException {
        int maxEvents = 13;
        long timestamp = 1515653241184L;

        CompletableFuture<SQLException> onException = new CompletableFuture<>();
        long total = dao.loadNewerStream(timestamp, Long.MAX_VALUE, onException).count();
        //Verify that there are more events then the max
        assertThat(total).isGreaterThan(maxEvents);
        //load from first event
        List<EventRecord> result = dao.loadNewerStream(timestamp, maxEvents, onException).collect(toList());
        if (onException.isDone()) {throw onException.join();}
        //Verify that result are limited to max although there are more.
        assertThat(result).hasSize(maxEvents);
        //Verify that result contains events from 3 repos
        assertEquals(3, result.stream().map(e -> StringUtils.substringBefore(e.getPath(), "/")).distinct().count());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void countByParamsWithLimit() throws SQLException {
        EventsDaoParams params = EventsDaoParams.builder().eventId(1).limit(1).build();
        dao.countEventsByParams(params);
    }


    @Test(dataProvider = "CountByParamsProvider")
    public void countByParams(EventsDaoParams params, int expectedCount) throws SQLException {
        long count = dao.countEventsByParams(params);
        assertThat(count).isEqualTo(expectedCount);
    }

    @Test
    public void testCountRangeEventsByRepoPath() throws SQLException {
        int count = dao.countRangeEventsByRepoPath(1515653250000L,
                1515653250004L,
                "repo2");
        assertEquals(count, 4);
    }

    @DataProvider(name = "CountByParamsProvider")
    private Object[][] countByParamsProvider() throws Exception {
        // Ensure ArtifactoryContext is bound for the data provider
        artifactoryHomeBoundTest.bindArtifactoryHome();

        EventRecord firstEvent = dao.loadAll().stream()
                .min(Comparator.comparingLong(EventRecord::getEventId)).orElseThrow();

        return new Object[][] {
                {EventsDaoParams.builder().build(), EVENTS_COUNT},
                {EventsDaoParams.builder().eventId(firstEvent.getEventId()).build(), 17},
                {EventsDaoParams.builder().eventId(firstEvent.getEventId()).inclusive(true).build(), 18},
                {EventsDaoParams.builder().eventId(firstEvent.getEventId()).repoKey("repo1").build(), 10},
                {EventsDaoParams.builder().eventId(firstEvent.getEventId()).repoKey("repo1").suffix("").build(), 10},
                {EventsDaoParams.builder().eventId(firstEvent.getEventId()).repoKey("repo1").suffix(".jar").build(), 3},
                {EventsDaoParams.builder().eventId(firstEvent.getEventId()).suffix(".pom").build(), 2}

        };
    }

}