package org.artifactory.storage.db.event.error.itest.dao;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.error.dao.NodeEventsErrorsDAO;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.artifactory.storage.db.event.error.model.RepoAndCategory;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.fest.assertions.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.artifactory.event.EventType.create;
import static org.artifactory.event.EventType.delete;
import static org.artifactory.event.EventType.props;
import static org.artifactory.event.EventType.update;

/**
 * @author Mor Merhav
 */
@Test
public class NodeEventsErrorsDAOTest extends ErrorsDaoBaseDbTest {

    @Autowired
    private NodeEventsErrorsDAO errorsDao;

    @Autowired
    private EventsDao eventsDao;

    @Autowired
    private DbService dbService;

    @Autowired
    private ArtifactoryJdbcHelper jdbcHelper;

    @AfterMethod
    private void cleanup() throws Exception {
        eventsDao.deleteAll();
    }


    @Test(expectedExceptions = SQLException.class,
            expectedExceptionsMessageRegExp = "(?i).*node_events_errors_event_id_fk.*")
    public void testAddErrorEventIdFkViolation() throws SQLException {
        // Given
        // Create error referenced to un existing event
        NodeEventsErrorsDBO error = create(1, "opKey", create,
                "EventCategory1", "Path1",
                0, 0, "defaultErrMsg", System.currentTimeMillis(), Long.MAX_VALUE);
        // When
        // insert the error no the db
        errorsDao.insert(error);

        // Then
        // SQLException contains 'SQLException' should be thrown
    }


    @Test
    public void testAddErrorEventIdNoFkViolation() throws SQLException {

        // Given
        // Add event and referenced error
        eventsDao.create(new EventRecord(100, create, "Path1"));
        eventsDao.shiftAndDeleteTmpEvents(false);
        // When
        // Insert error referenced to the existing event
        EventRecord event = eventsDao.loadAll().get(0);
        errorsDao.insert(
                create(1, "opKey", create, "EventCategory1", "Path1",
                        0, 0, "defaultErrMsg", event.getTimestamp(),
                        event.getEventId()));


        // Then
        // No Exception should occur
    }


    @Test
    public void testAddErrorNullEventIdNoFkViolation() throws SQLException {
        // Given
        // Create error not referenced to any event
        NodeEventsErrorsDBO error = create(1, "opKey", create, "EventCategory1", "Path1",
                0, 0, "defaultErrMsg", System.currentTimeMillis(), null);
        // When
        // insert the error no the db
        errorsDao.insert(error);

        // Then
        // No Exception should occur
    }


    @Test(expectedExceptions = SQLException.class,
            expectedExceptionsMessageRegExp = "(?i).*node_events_errors_event_id_fk.*")
    public void testDeleteEventErrorEventIdFkViolation() throws Exception {

        // Given
        // Add event and referenced error
        eventsDao.create(new EventRecord(1, 100, create, "Path1"));
        eventsDao.shiftAndDeleteTmpEvents(false);
        errorsDao.insert(
                create(1, "opKey", create, "EventCategory1", "Path1",
                        0, 0, "defaultErrMsg", 1L, System.currentTimeMillis()));


        // When
        // Delete the event
        jdbcHelper.executeUpdate("DELETE FROM node_events");

        // Then
        // SQLException contains 'node_events_errors_event_id_fk' should be thrown

    }

    @Test
    public void testDeleteEventsErrorsWithEventIdBefore() throws Exception {
        // Given
        // Add event and referenced error

        for (int i = 0; i < 5; i++) {
            eventsDao.create(new EventRecord(dbService.nextId(), i, create, "Path1"));
        }
        eventsDao.shiftAndDeleteTmpEvents(false);

        List<EventRecord> events = eventsDao.loadAll();
        for (EventRecord event : events) {
            errorsDao.insert(
                    create(dbService.nextId(), "opKey", create, "EventCategory1",
                            event.getPath(), 0, 0, "defaultErrMsg", event.getTimestamp(), event.getEventId()));
        }

        long errorWithNoEventId = dbService.nextId();
        errorsDao.insert(
                create(errorWithNoEventId, "opKey", create, "EventCategory1",
                        "/a/b/c", 0, 0, "defaultErrMsg",
                        events.get(events.size() - 1).getTimestamp() + 1, null));

        // When
        // Delete range of errors
        int deleted = errorsDao.deleteBeforeWithEventId(3);

        // Then
        // Verify deleted count
        Assert.assertEquals(deleted, 3);

        // Get all errors
        List<NodeEventsErrorsDBO> errors = errorsDao.selectAll("EventCategory1");

        //Verify not all Deleted
        Assert.assertFalse(errors.isEmpty());
        //Verify 2 error left
        Assert.assertEquals(errors.size(), 3);
        //Verify left errors time is greater or equal than delete boundary(4)
        errors.forEach(error -> Assertions.assertThat(error.getEventTime()).isGreaterThanOrEqualTo(3));
        Assert.assertTrue(errors.stream().anyMatch(error -> error.getErrorId() == errorWithNoEventId));
    }


    @Test
    public void testGetFirstErrorEventTimestampWithEventId() throws Exception {
        // Given

        // Add error with no event_id timestamp 0
        // Should not be considered in the getFirstEventErrorTimestampWithEventId
        errorsDao.insert(
                create(dbService.nextId(), "opKey", create, "EventCategory1",
                        "/a/b/c", 0, 0, "defaultErrMsg", 0, null));

        // Add events
        for (int i = 1; i <= 3; i++) {
            eventsDao.create(new EventRecord(dbService.nextId(), i, create, "Path1"));
        }
        eventsDao.shiftAndDeleteTmpEvents(false);

        List<EventRecord> events = eventsDao.loadAll();
        // Add errors with event_id
        for (EventRecord event : events) {
            errorsDao.insert(
                    create(dbService.nextId(), "opKey", create, "EventCategory1", event.getPath(),
                            0, 0, "defaultErrMsg", event.getTimestamp(), event.getEventId()));
        }

        // When
        // Delete range of errors
        long timestamp = errorsDao.getFirstEventErrorTimestampWithEventId();

        // Then
        // Verify deleted count
        Assert.assertEquals(timestamp, 1);

    }


    @Test
    public void testSelectAll() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, "EventCategory");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", "EventCategory");
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbo1, dbo2);
        simpleJdbcInsert.executeBatch(batch);

        List<NodeEventsErrorsDBO> nodeEventsErrorsDBOS = errorsDao.selectAll("EventCategory");
        List<NodeEventsErrorsDBO> expectedNodeEventErrorsDBOs = Lists.newArrayList(dbo2, dbo1);
        expectedNodeEventErrorsDBOs.sort(Comparator.comparing(NodeEventsErrorsDBO::getEventTime)
                .thenComparing(NodeEventsErrorsDBO::getErrorId));
        Assert.assertEquals(expectedNodeEventErrorsDBOs, nodeEventsErrorsDBOS);
    }

    @Test
    public void testSelect() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, create, "EventCategory1", "Path1");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", create, "EventCategory2", "Path3");
        NodeEventsErrorsDBO dbo3 = create(3, "opKey", delete, "EventCategory2", "Path3");
        NodeEventsErrorsDBO dbo4 = create(4, "opKey2", props, "EventCategory3", "Path4");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2, dbo3, dbo4);
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbos);
        simpleJdbcInsert.executeBatch(batch);

        NodeEventsErrorsDBO returnedDbo1 = errorsDao.select("EventCategory1", create, "Path1");
        NodeEventsErrorsDBO returnedDbo3 = errorsDao.select("EventCategory2", delete, "Path3", "opKey");
        Assert.assertNotNull(returnedDbo1);
        Assert.assertEquals(dbo1, returnedDbo1);
        Assert.assertNotNull(returnedDbo3);
        Assert.assertEquals(dbo3, returnedDbo3);
    }

    @Test
    public void testUpdate() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, create, "EventCategory1", "Path1", 0, 2, "defaultErrMsg");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", create, "EventCategory2", "Path3", 0, 2, "defaultErrMsg");
        NodeEventsErrorsDBO dbo3 = create(3, "opKey", delete, "EventCategory2", "Path3", 0, 2, "defaultErrMsg");
        NodeEventsErrorsDBO dbo4 = create(4, "opKey2", props, "EventCategory3", "Path4", 0, 2, "defaultErrMsg");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2, dbo3, dbo4);
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbos);
        simpleJdbcInsert.executeBatch(batch);

        NodeEventsErrorsDBO updatedDbo1 = createUpdated(dbo1, 1, 3, "newErrMsg");
        NodeEventsErrorsDBO updatedDbo2 = createUpdated(dbo2, 1, 3, "newErrMsg");
        int numRowsAffected1 = errorsDao.update(updatedDbo1);
        Assert.assertEquals(numRowsAffected1, 1);
        int numRowsAffected2 = errorsDao.update(updatedDbo2);
        Assert.assertEquals(numRowsAffected2, 1);
        NodeEventsErrorsDBO returnedDbo1 = jdbcTemplate
                .queryForObject("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS + " WHERE error_id = " + dbo1.getErrorId(),
                        getRowMapper());
        NodeEventsErrorsDBO returnedDbo2 = jdbcTemplate
                .queryForObject("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS + " WHERE error_id = " + dbo2.getErrorId(),
                        getRowMapper());
        Assert.assertEquals(updatedDbo1, returnedDbo1);
        Assert.assertEquals(updatedDbo2, returnedDbo2);
    }

    @Test
    public void testUpdateById() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, create, "EventCategory1", "Path1", 0, 2, "defaultErrMsg");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", create, "EventCategory2", "Path3", 0, 2, "defaultErrMsg");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2);
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbos);
        simpleJdbcInsert.executeBatch(batch);

        NodeEventsErrorsDBO updatedDbo1 = createUpdated(dbo1, 1, 3, "newErrMsg");
        NodeEventsErrorsDBO updatedDbo2 = createUpdated(dbo2, 1, 3, "newErrMsg");
        int numRowsAffected1 = errorsDao.updateById(updatedDbo1);
        Assert.assertEquals(numRowsAffected1, 1);
        int numRowsAffected2 = errorsDao.updateById(updatedDbo2);
        Assert.assertEquals(numRowsAffected2, 1);
        NodeEventsErrorsDBO returnedDbo1 = jdbcTemplate
                .queryForObject("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS + " WHERE error_id = " + dbo1.getErrorId(),
                        getRowMapper());
        NodeEventsErrorsDBO returnedDbo2 = jdbcTemplate
                .queryForObject("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS + " WHERE error_id = " + dbo2.getErrorId(),
                        getRowMapper());
        Assert.assertEquals(updatedDbo1, returnedDbo1);
        Assert.assertEquals(updatedDbo2, returnedDbo2);

        NodeEventsErrorsDBO dbo3 = create(3, "opKey", create, "EventCategory2", "Path3", 0, 2, "defaultErrMsg");
        Assert.assertEquals(errorsDao.updateById(dbo3), 0, "trying to update non existing error");

    }

    @Test
    public void testInsert() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, "EventCategory");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", "EventCategory");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2);

        errorsDao.insert(dbos);

        List<NodeEventsErrorsDBO> returnedDBOs =
                jdbcTemplate.query("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS, getRowMapper());
        dbos.sort(Comparator.comparing(NodeEventsErrorsDBO::getErrorId));
        returnedDBOs.sort(Comparator.comparing(NodeEventsErrorsDBO::getErrorId));
        Assert.assertEquals(dbos, returnedDBOs);
    }

    @Test
    public void testSelectErrorsByOperationKey() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, "EventCategory");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", "EventCategory");
        NodeEventsErrorsDBO dbo3 = create(3, "opKey", "EventCategory");
        NodeEventsErrorsDBO dbo4 = create(4, "opKey2", "EventCategory");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2, dbo3, dbo4);
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbos);
        simpleJdbcInsert.executeBatch(batch);

        List<NodeEventsErrorsDBO> returnedDbos = Lists.newArrayList(errorsDao.select("EventCategory", "opKey", 1000));
        Assert.assertNotNull(returnedDbos);
        Assert.assertEquals(2, returnedDbos.size());
        returnedDbos.sort(Comparator.comparing(NodeEventsErrorsDBO::getErrorId));
        Assert.assertEquals(dbo2, returnedDbos.get(0));
        Assert.assertEquals(dbo3, returnedDbos.get(1));
    }

    @Test
    public void testDelete() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, create, "EventCategory1", "Path1", 0, 2, "defaultErrMsg");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", create, "EventCategory2", "Path2", 0, 2, "defaultErrMsg");
        NodeEventsErrorsDBO dbo3 = create(3, "opKey", delete, "EventCategory2", "Path3", 0, 2, "defaultErrMsg");
        NodeEventsErrorsDBO dbo4 = create(4, "opKey2", props, "EventCategory3", "Path4", 0, 2, "defaultErrMsg");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2, dbo3, dbo4);
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbos);
        simpleJdbcInsert.executeBatch(batch);

        errorsDao.delete("EventCategory1", create, "Path1");
        errorsDao.delete("EventCategory2", create, "Path2", "opKey");

        List<NodeEventsErrorsDBO> returnedDbos =
                jdbcTemplate.query("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS, getRowMapper());
        Assert.assertNotNull(returnedDbos);
        Assert.assertEquals(returnedDbos.size(), 2);
        returnedDbos.sort(Comparator.comparing(NodeEventsErrorsDBO::getErrorId));
        Assert.assertEquals(dbo3, returnedDbos.get(0));
        Assert.assertEquals(dbo4, returnedDbos.get(1));
    }

    @Test
    public void testDeleteByOperationKey() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, "EventCategory");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", "EventCategory");
        NodeEventsErrorsDBO dbo3 = create(3, "opKey", "EventCategory");
        NodeEventsErrorsDBO dbo4 = create(4, "opKey2", "EventCategory");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2, dbo3, dbo4);
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbos);
        simpleJdbcInsert.executeBatch(batch);

        errorsDao.delete("EventCategory", "opKey");

        List<NodeEventsErrorsDBO> returnedDbos =
                jdbcTemplate.query("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS, getRowMapper());
        Assert.assertNotNull(returnedDbos);
        Assert.assertEquals(2, returnedDbos.size());
        returnedDbos.sort(Comparator.comparing(NodeEventsErrorsDBO::getErrorId));
        Assert.assertEquals(dbo1, returnedDbos.get(0));
        Assert.assertEquals(dbo4, returnedDbos.get(1));
    }

    @Test
    public void testDeleteByOperationKeyAndEventType() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, update, "EventCategory");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", create, "EventCategory");
        NodeEventsErrorsDBO dbo3 = create(3, "opKey", create, "EventCategory");
        NodeEventsErrorsDBO dbo4 = create(4, "opKey2", delete, "EventCategory");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2, dbo3, dbo4);
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbos);
        simpleJdbcInsert.executeBatch(batch);

        errorsDao.delete("EventCategory", "opKey", create);

        List<NodeEventsErrorsDBO> returnedDbos =
                jdbcTemplate.query("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS, getRowMapper());
        Assert.assertNotNull(returnedDbos);
        Assert.assertEquals(2, returnedDbos.size());
        returnedDbos.sort(Comparator.comparing(NodeEventsErrorsDBO::getErrorId));
        Assert.assertEquals(dbo1, returnedDbos.get(0));
        Assert.assertEquals(dbo4, returnedDbos.get(1));
    }

    @Test
    public void testDeleteByErrorId() throws SQLException {
        NodeEventsErrorsDBO dbo1 = create(1, null, update, "EventCategory");
        NodeEventsErrorsDBO dbo2 = create(2, "opKey", create, "EventCategory");
        NodeEventsErrorsDBO dbo3 = create(3, "opKey", create, "EventCategory");
        NodeEventsErrorsDBO dbo4 = create(4, "opKey2", delete, "EventCategory");
        List<NodeEventsErrorsDBO> dbos = Lists.newArrayList(dbo1, dbo2, dbo3, dbo4);
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(dbos);
        simpleJdbcInsert.executeBatch(batch);
        //delete by id
        Assert.assertTrue(errorsDao.delete(2));
        List<NodeEventsErrorsDBO> returnedDbos =
                jdbcTemplate.query("SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS, getRowMapper());
        returnedDbos.sort(Comparator.comparing(NodeEventsErrorsDBO::getErrorId));
        Assert.assertNotNull(returnedDbos);
        Assert.assertEquals(3, returnedDbos.size());
        Assert.assertEquals(dbo1, returnedDbos.get(0));
        Assert.assertEquals(dbo3, returnedDbos.get(1));
        Assert.assertEquals(dbo4, returnedDbos.get(2));

        //trying to delete non existing id's
        Assert.assertFalse(errorsDao.delete(2));
        Assert.assertFalse(errorsDao.delete(5));
    }

    @Test
    public void testGetErroneousRepos() throws SQLException {
        // Given
        // Add event and referenced error
        eventsDao.create(new EventRecord(100, create, "repo1/Path1"));
        eventsDao.create(new EventRecord(101, create, "repo1/Path2"));
        eventsDao.create(new EventRecord(102, create, "repo1/Path3"));
        eventsDao.create(new EventRecord(103, create, "repo2/Path1"));
        eventsDao.shiftAndDeleteTmpEvents(false);
        // Insert errors referenced to the existing events
        List<EventRecord> events = eventsDao.loadAll();
        errorsDao.insert(
                create(1, "opKey1", create, "EventCategory1", events.get(0).getPath(),
                        0, 0, "defaultErrMsg", events.get(0).getTimestamp(),
                        events.get(0).getEventId()));
        errorsDao.insert(
                create(2, "opKey2", create, "EventCategory1", events.get(1).getPath(),
                        0, 0, "defaultErrMsg", events.get(1).getTimestamp(),
                        events.get(1).getEventId()));
        errorsDao.insert(
                create(3, "opKey2", create, "EventCategory2", events.get(2).getPath(),
                        0, 0, "defaultErrMsg", events.get(2).getTimestamp(),
                        events.get(2).getEventId()));
        // Insert errors with no reference event
        errorsDao.insert(
                create(4, "opKey3", create, "EventCategory2", events.get(3).getPath(),
                        0, 0, "defaultErrMsg", events.get(3).getTimestamp(),
                        null));

        // When
        Set<RepoAndCategory> repos = errorsDao.getErroneousRepos();

        // Then
        Assert.assertEquals(repos.size(), 2);
        repos.forEach(repo -> {
            Assert.assertEquals("repo1", repo.getRepoKey());
            Assert.assertTrue(Sets.newHashSet("EventCategory1", "EventCategory2").contains(repo.getEventCategory()));
        });
    }

    private NodeEventsErrorsDBO createUpdated(NodeEventsErrorsDBO dboToUpdate, int newErrorCount, int newLastErrorTime,
            String newErrMsg) {
        return NodeEventsErrorsDBO.builder()
                .errorCount(newErrorCount)
                .lastErrorTime(newLastErrorTime)
                .errorMessage(newErrMsg)
                .errorId(dboToUpdate.getErrorId())
                .eventCategory(dboToUpdate.getEventCategory())
                .operationKey(dboToUpdate.getOperationKey())
                .eventType(dboToUpdate.getEventType())
                .path(dboToUpdate.getPath())
                .firstErrorTime(dboToUpdate.getFirstErrorTime())
                .eventId(dboToUpdate.getEventId())
                .eventTime(dboToUpdate.getEventTime())
                .build();
    }

    private RowMapper<NodeEventsErrorsDBO> getRowMapper() {
        return (ResultSet rs, int i) -> NodeEventsErrorsDBO.builder()
                .errorId(rs.getLong("error_id"))
                .eventCategory(rs.getString("event_category"))
                .firstErrorTime(rs.getLong("first_error_time"))
                .lastErrorTime(rs.getLong("last_error_time"))
                .errorCount(rs.getInt("error_count"))
                .errorMessage(rs.getString("error_message"))
                .operationKey(rs.getString("operation_key"))
                .eventId(rs.getObject("event_id", Long.class))
                .eventTime(rs.getLong("event_time"))
                .eventType(rs.getInt("event_type"))
                .path(rs.getString("path"))
                .build();
    }
}
