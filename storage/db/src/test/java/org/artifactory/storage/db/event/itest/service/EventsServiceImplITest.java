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

package org.artifactory.storage.db.event.itest.service;

import com.google.common.collect.Lists;
import org.artifactory.event.EventType;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.db.event.service.InternalEventsService;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.event.EventsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Comparator;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Low level integration tests for the events service.
 *
 * @author Yossi Shaul
 */
@Test
public class EventsServiceImplITest extends DbBaseTest {

    @Autowired
    private EventsService service;

    @Autowired
    private InternalEventsService internal;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes-for-service.sql");
    }

    @Test
    public void countEvents() {
        assertEquals(service.getEventsCount(), 18);
    }

    @Test
    public void allEvents() {
        assertThat(internal.getAllEvents()).hasSize(18);
    }

    @Test
    public void createSingleEvent() {
        long count = service.getEventsCount();
        EventInfo e = new EventInfo(EventType.create, "repo5/land/");
        service.appendEvents(Lists.newArrayList(e));
        service.shiftEvents();
        assertEquals(service.getEventsCount(), count + 1);
    }

    @Test
    public void createMultipleEvents() {
        long count = service.getEventsCount();
        EventInfo e1 = new EventInfo(EventType.create, "repo5/land/");
        EventInfo e2 = new EventInfo(EventType.delete, "repo5/land/");
        service.appendEvents(Lists.newArrayList(e1, e2));
        eventsService.shiftEvents();
        assertEquals(service.getEventsCount(), count + 2);
    }

    public void firstEventTimestamp() {
        assertThat(service.getFirstEventTimestamp()).isEqualTo(1515653241184L);
    }

    public void firstEventId() {
        EventInfo eventInfo = service.getEventsAfter(0, null).stream()
                .min(Comparator.comparingLong(EventInfo::getEventId)).get();

        assertThat(service.getFirstEventLogId("repo1")).isEqualTo(eventInfo.getEventId());
    }

    @Test
    public void getEventsAfterAll() {
        List<EventInfo> result = service.getEventsAfter(0L, "repo1");
        assertThat(result).hasSize(11);
        result.forEach(e -> {
            assertTrue(e.getPath().startsWith("repo1/"));
            assertTrue(e.getTimestamp() > 0);
            assertTrue(e.getEventId() > 0);
        });
    }

    @Test
    public void getEventsAfterOrder() {
        List<EventInfo> result = service.getEventsAfter(0, "repo1");
        EventInfo[] ordered = result.stream().sorted(Comparator.comparingLong(EventInfo::getEventId))
                .toArray(EventInfo[]::new);
        assertThat(result).hasSize(11).containsExactly((Object[]) ordered);
    }

    @Test
    public void getEventsAfterIsExclusive() {

        EventInfo event = service.getEventsAfter(0, "repo2").stream()
                .filter(e -> e.getTimestamp() == 1515653250003L)
                .findFirst().orElseThrow();

        List<EventInfo> result = service.getEventsAfter(event.getEventId(), "repo2");
        assertThat(result).hasSize(2);
        result.forEach(e -> assertTrue(e.getTimestamp() > event.getTimestamp()));
        result.forEach(e -> assertTrue(e.getEventId() > event.getEventId()));
    }

    @Test
    public void getEventsAfterNoEvents() {
        assertThat(service.getEventsAfter(Long.MAX_VALUE, "repo2")).hasSize(0);
    }

    @Test
    public void countRepoEventsAfter() {

        EventInfo firstEvent = eventsService.getEventsAfter(0, "repo1").stream()
                .min(Comparator.comparingLong(EventInfo::getEventId)).orElseThrow();

        int count = service.countEventsAfter(firstEvent.getEventId(), "repo1", "");
        assertThat(count).isEqualTo(10);
    }

    @Test
    public void testCountEventsRange() {
        int count = service.countEventsRange(1515653250001L, 1515653250004L, "repo2", "");
        assertThat(count).isEqualTo(3);
    }

    @Test
    public void testCountEventsRangeByRepoKeyWithRepoNull() {
        int count = service.countEventsRange(1515653241186L, 1515653250004L, null, "");
        assertThat(count).isEqualTo(12);
    }
}