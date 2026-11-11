package org.artifactory.storage.db.event.error.itest.dao;

import com.google.common.collect.Sets;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.entity.EventRecordHints;
import org.artifactory.storage.db.event.error.dao.EventAndErrorInfoDao;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.artifactory.event.EventType.create;
import static org.fest.assertions.Assertions.assertThat;

/**
 * @author avishayh
 */
@Test
public class EventAndErrorInfoDaoTest extends ErrorsDaoBaseDbTest {

    @Autowired
    private EventAndErrorInfoDao errorInfoDao;
    @Autowired
    private EventsDao eventsDao;

    @AfterMethod
    public void clean() throws SQLException{
        eventsDao.deleteAll();
    }

    public void noErrors() throws SQLException {
        assertThat(errorInfoDao.loadErrorEvents("EventCategory1", "opKey", -1)).isEmpty();
        assertThat(errorInfoDao.getErrorCount("EventCategory1", "opKey")).isZero();
        eventsDao.create(new EventRecord(100, create, "Path1"));
        eventsDao.create(new EventRecord(100, create, "Path2"));
        eventsDao.create(new EventRecord(100, create, "Path3"));
        eventsDao.shiftAndDeleteTmpEvents(false);
        assertThat(errorInfoDao.loadErrorEvents("EventCategory1", "opKey", -1)).isEmpty();
        assertThat(errorInfoDao.getErrorCount("EventCategory1", "opKey")).isZero();
        //creating mismatched errors
        executeBatch(
                create(1, "opKey", create, "EventCategory1", "Path1", 0, 0, "error", 4, null),
                create(2, "opKey", create, "EventCategory1", "Path1", 0, 0, "error", 5, null)
        );
        assertThat(errorInfoDao.loadErrorEvents("EventCategory1", "opKey", -1)).isEmpty();
        assertThat(errorInfoDao.getErrorCount("EventCategory1", "opKey")).isZero();
    }

    public void foundErrors() throws SQLException {
        //matching error with event
        String hints = EventRecordHints.toJson(Sets.newHashSet("ts1.hello"));
        eventsDao.create(new EventRecord(-1, 100L, create, "Path1", hints));
        eventsDao.shiftAndDeleteTmpEvents(false);

        EventRecord e1 = eventsDao.loadAll().get(0);
        NodeEventsErrorsDBO dbo1 = create(2618965448L, "opKey", create, "EventCategory1", "Path1", 3, 20, "error",
                e1.getTimestamp(), e1.getEventId());
        executeBatch(dbo1);

        assertThat(errorInfoDao.getErrorCount("EventCategory1", "opKey")).isEqualTo(1);
        List<EventAndErrorInfo> errors = errorInfoDao.loadErrorEvents("EventCategory1", "opKey", -1);
        assertThat(errors).hasSize(1);
        EventAndErrorInfo error = errors.get(0);
        assertThat(error.getErrorId()).isEqualTo(2618965448L);
        assertThat(error.getErrorCount()).isEqualTo(3);
        assertThat(error.getFirstErrorTime()).isEqualTo(1);
        assertThat(error.getEventInfo().getType()).isEqualTo(create);
        assertThat(error.getEventInfo().getPath()).isEqualTo("Path1");
        assertThat(error.getEventInfo().getEventId()).isEqualTo(e1.getEventId());
        assertThat(error.getEventInfo().getTimestamp()).isEqualTo(e1.getTimestamp());
        assertThat(error.getEventInfo().getHints()).isEqualTo(Set.of("ts1.hello"));

        //adding mismatch errors - still found 1 error
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", create, "EventCategory1", "Path1", 0,
                0, "error",3, null);
        NodeEventsErrorsDBO dbo3 = create(3, "opKey", create, "EventCategory2", "Path1", 1,
                0, "error", 1, null);
        NodeEventsErrorsDBO dbo4 = create(4, null, create, "EventCategory2", "Path1", 1,
                0, "error", 1, null);
        executeBatch(dbo2, dbo3, dbo4);
        assertThat(errorInfoDao.getErrorCount("EventCategory1", "opKey")).isEqualTo(1);
        errors = errorInfoDao.loadErrorEvents("EventCategory1", "opKey", -1);
        assertThat(errors).hasSize(1);

        //adding both matching and non matching events
        eventsDao.create(new EventRecord(-1, 200L, create, "Path1")); //matches previous dbo2 error
        eventsDao.create(new EventRecord(-1, 300L, create, "Path1"));
        eventsDao.create(new EventRecord(-1, 400L, create, "Path1"));
        eventsDao.create(new EventRecord(-1, 500L, create, "Path1"));
        eventsDao.shiftAndDeleteTmpEvents(false);

        EventRecord e3 = eventsDao.loadAll().stream()
                .filter(e -> e.getPath().equals("Path1"))
                .filter(e -> e.getTimestamp() == 300L)
                .findFirst().orElseThrow();
        NodeEventsErrorsDBO dbo5 = create(5, "opKey", create, "EventCategory1",
                "Path1", 0,0, "error",e3.getTimestamp(), e3.getEventId());
        executeBatch(dbo5);
        assertThat(errorInfoDao.getErrorCount("EventCategory1", "opKey")).isEqualTo(2);
        errors = errorInfoDao.loadErrorEvents("EventCategory1", "opKey", -1);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getErrorId()).isEqualTo(dbo1.getErrorId()).isEqualTo(2618965448L);
        assertThat(errors.get(1).getErrorId()).isEqualTo(dbo5.getErrorId()).isEqualTo(5);

        //check sorting works - add unsorted
        EventRecord e2 = eventsDao.loadAll().stream()
                .filter(e -> e.getPath().equals("Path1"))
                .filter(e -> e.getTimestamp() == 200L)
                .findFirst().orElseThrow();
        NodeEventsErrorsDBO dbo6 = create(6, "opKey", create, "EventCategory1", "Path1", 0,
                0, "error", e2.getTimestamp(), e2.getEventId());
        executeBatch(dbo6);
        assertThat(errorInfoDao.getErrorCount("EventCategory1", "opKey")).isEqualTo(3);
        errors = errorInfoDao.loadErrorEvents("EventCategory1", "opKey", -1);
        assertThat(errors).hasSize(3);
        assertThat(errors.get(0).getErrorId()).isEqualTo(dbo1.getErrorId()).isEqualTo(2618965448L);
        assertThat(errors.get(1).getErrorId()).isEqualTo(dbo6.getErrorId()).isEqualTo(6);
        assertThat(errors.get(2).getErrorId()).isEqualTo(dbo5.getErrorId()).isEqualTo(5);
        //check limit works
        errors = errorInfoDao.loadErrorEvents("EventCategory1", "opKey", 2);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getErrorId()).isEqualTo(dbo1.getErrorId());
        assertThat(errors.get(1).getErrorId()).isEqualTo(dbo6.getErrorId());
    }

    private void executeBatch(NodeEventsErrorsDBO... dbos) {
        simpleJdbcInsert.executeBatch(SqlParameterSourceUtils.createBatch((Object[])dbos));
    }
}