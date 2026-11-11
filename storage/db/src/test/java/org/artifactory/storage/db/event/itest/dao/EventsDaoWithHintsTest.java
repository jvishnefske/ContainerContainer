package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.entity.EventRecordHints;
import org.artifactory.storage.db.event.entity.EventsDaoParams;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Units tests for {@link EventsDao}.
 * @author avishayh
 */
@Test
public class EventsDaoWithHintsTest extends DbBaseTest {

    @Autowired
    private EventsDao dao;

    @BeforeClass
    public void setup() {
        importSql("/sql/events-with-hints.sql");
    }

    @AfterClass
    public void clean() throws SQLException {
        dao.deleteAll();
    }

    public void readEvents() throws SQLException {
        List<EventRecord> records = dao.loadAll();
        assertThat(records).hasSize(10);
        assertThat(EventRecordHints.fromJson(records.get(8).getHints())).containsOnly("hint1.name.bla");
        assertThat(EventRecordHints.fromJson(records.get(7).getHints())).hasSize(2);
    }

    @Test(dependsOnMethods = "readEvents")
    public void insertAndQueryHints() throws SQLException {
        String hints = EventRecordHints.toJson(Set.of("deleted.local"));
        //eventId is not
        EventRecord event = new EventRecord(-1, System.currentTimeMillis(), EventType.delete, "repo1/ant/", hints);
        dao.create(event);
        dao.shiftAndDeleteTmpEvents(false);

        List<EventRecord> eventRecords = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(0)
                .inclusive(true)
                .repoKey("repo1")
                .afterRepoColumnMigration(false)
                .build());
        assertThat(eventRecords.stream()
                .filter(e -> e.getTimestamp() == event.getTimestamp() && e.getPath().equals(event.getPath()))
                .findFirst().get().getHints()).isEqualTo(hints);
    }

    @Test(dependsOnMethods = "readEvents")
    public void insertAndQueryHintsWithRepo() throws SQLException {
        String hints = EventRecordHints.toJson(Set.of("deleted.local"));
        //eventId is not
        EventRecord event = new EventRecord(-1, System.currentTimeMillis(), EventType.delete, "repo1/ant/", hints);
        dao.create(event);
        dao.shiftAndDeleteTmpEvents(true);

        List<EventRecord> eventRecords = dao.loadEventsByParams(EventsDaoParams.builder()
                .eventId(0)
                .inclusive(true)
                .repoKey("repo1")
                .afterRepoColumnMigration(true)
                .build());
        assertThat(eventRecords.stream()
                .filter(e -> e.getTimestamp() == event.getTimestamp() && e.getPath().equals(event.getPath()))
                .findFirst().get().getHints()).isEqualTo(hints);
    }
}
