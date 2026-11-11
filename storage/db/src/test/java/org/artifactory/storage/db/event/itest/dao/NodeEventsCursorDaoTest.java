package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.event.dao.NodeEventsCursorDao;
import org.artifactory.storage.db.event.entity.DbNodeEventCursor;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.common.ClockUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class NodeEventsCursorDaoTest extends DbBaseTest {

    @Autowired
    private NodeEventsCursorDao dao;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes.sql");
    }

    @Test
    public void allEvents() throws SQLException {
        assertEquals(dao.getAll().size(), 3);
    }

    public void cursorForOperationId() throws SQLException {
        Optional<DbNodeEventCursor> dbNodeEventCursor = dao.cursorForOperator("metadata-dispatcher");
        assertTrue(dbNodeEventCursor.isPresent());
        assertEquals(dbNodeEventCursor.get().getEventMarker().longValue(), 1515653250000L);
    }

    @Test(dependsOnMethods = "allEvents")
    public void createCursor() throws SQLException {
        String operatorId = "replication-ams-nj4";
        long now = ClockUtils.epochMillis();
        dao.insert(DbNodeEventCursor.builder()
                .operatorId(operatorId)
                .eventMarker(now)
                .type(NodeEventCursorType.METADATA_MIGRATION)
                .build());
        assertEquals(dao.getAll().size(), 4);
        Optional<DbNodeEventCursor> cursor = dao.cursorForOperator(operatorId);
        assertTrue(cursor.isPresent());
        assertEquals(cursor.get().getEventMarker().longValue(), now);
    }


    @Test(dependsOnMethods = {"createCursor"}, expectedExceptions = SQLException.class)
    public void createExistingCursor() throws SQLException {
        String operatorId = "replication-ams-nj4";
        final DbNodeEventCursor cursor = DbNodeEventCursor.builder()
                .operatorId(operatorId)
                .eventMarker(ClockUtils.epochMillis())
                .type(NodeEventCursorType.METADATA_MIGRATION)
                .build();
        // primary key violation
        dao.insert(cursor);
    }

    @Test(dependsOnMethods = {"createCursor"})
    public void oldestMarkerForTypes() throws SQLException {
        List<NodeEventCursorType> types = Arrays.asList(
                NodeEventCursorType.INCREMENTAL_REPLICATION_PROGRESS,
                NodeEventCursorType.METADATA_MIGRATION);
        long marker = dao.oldestMarkerForTypes(types);
        assertEquals(marker, 1515653250000L);
    }

    @Test
    public void testDeleteById() throws SQLException {
        String operatorId = "some-operator-id";
        long now = ClockUtils.epochMillis();
        dao.insert(DbNodeEventCursor.builder()
                .operatorId(operatorId)
                .eventMarker(now)
                .type(NodeEventCursorType.METADATA_MIGRATION)
                .build());
        assertTrue(dao.cursorForOperator(operatorId).isPresent());
        dao.delete(operatorId);
        assertFalse(dao.cursorForOperator(operatorId).isPresent());
    }

    @Test
    public void testUpdateCursorsStatus() throws SQLException {
        dao.insert(DbNodeEventCursor.builder()
                .operatorId("testOpFed")
                .eventMarker(ClockUtils.epochMillis())
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.HEALTHY)
                .lastUpdated(System.currentTimeMillis())
                .build());
        dao.updateCursorsStatus("testOpFed", NodeEventCursorStatus.OUT_OF_SYNC);
        DbNodeEventCursor nodeEventCursor = dao.getAll().stream()
                .filter(it -> it.getOperatorId().equals("testOpFed"))
                .findFirst().get();
        assertEquals(nodeEventCursor.getStatus(), NodeEventCursorStatus.OUT_OF_SYNC);
        dao.delete("testOpFed");
    }

    @Test
    public void testUpdateCursorsIfStatus() throws SQLException {
        dao.insert(DbNodeEventCursor.builder()
                .operatorId("testOpFed")
                .eventMarker(ClockUtils.epochMillis())
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.PENDING_FS)
                .lastUpdated(System.currentTimeMillis())
                .build());
        int updatedRows =
                dao.updateWhereStatus("testOpFed", NodeEventCursorStatus.HEALTHY, NodeEventCursorStatus.FS_RUNNING);
        assertEquals(updatedRows, 0);
        updatedRows =
                dao.updateWhereStatus("testOpFed", NodeEventCursorStatus.PENDING_FS, NodeEventCursorStatus.FS_RUNNING);
        assertEquals(updatedRows, 1);
        DbNodeEventCursor nodeEventCursor = dao.getAll().stream()
                .filter(it -> it.getOperatorId().equals("testOpFed"))
                .findFirst().get();
        assertEquals(nodeEventCursor.getStatus(), NodeEventCursorStatus.FS_RUNNING);
        dao.delete("testOpFed");
    }


    @Test
    public void testGetStatus() throws SQLException {
        List<DbNodeEventCursor> fedEvents = dao.cursorForStatus(NodeEventCursorStatus.HEALTHY,
                NodeEventCursorType.FEDERATED_REPLICATION);
        assertEquals(fedEvents.size(), 1);
        assertEquals(fedEvents.stream().findFirst().get().getStatus(), NodeEventCursorStatus.HEALTHY);
    }

    @Test
    public void testUpdateCursorWithTimeStamp() throws SQLException {
        dao.insert(DbNodeEventCursor.builder()
                .operatorId("testOpFed")
                .eventMarker(1L)
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.HEALTHY)
                .lastUpdated(2L)
                .build());

        dao.updateWithMarkerAndTimestamp("testOpFed", 3L, 4L);

        DbNodeEventCursor eventCursor = dao.cursorForOperator("testOpFed").get();

        assertEquals(eventCursor.getLastUpdated().longValue(), 4L);
        assertEquals(eventCursor.getEventMarker().longValue(), 3L);

        //cleanup
        dao.delete("testOpFed");
    }

    @Test
    public void testOldCursorsByTypeAndStatus() throws SQLException {
        List<DbNodeEventCursor> dbNodeEventCursors = dao.getCursorsBy(
                NodeEventCursorType.getEventIdLogTypes(),
                NodeEventCursorStatus.HEALTHY,
                1639903155);
        assertEquals(dbNodeEventCursors.size(), 1);
        assertEquals(dbNodeEventCursors.get(0).getOperatorId(),
                "repo-federated_replicationhttp://0.0.0.0:8081/artifactory/repo-cursor");
    }

    @Test
    public void testOldCursorsByTypeAndStatusWithNew() throws SQLException {
        List<DbNodeEventCursor> dbNodeEventCursors = dao.getCursorsBy(
                NodeEventCursorType.getEventIdLogTypes(),
                NodeEventCursorStatus.HEALTHY,
                1639903153);
        assertEquals(dbNodeEventCursors.size(), 0);
    }

    @Test
    public void testGetAllByTypes_singleType() throws SQLException {
        List<DbNodeEventCursor> result = dao.getAllByTypes(List.of(NodeEventCursorType.METADATA_MIGRATION));
        assertEquals(result.size(), 1);
        assertTrue(result.stream().anyMatch(c -> c.getOperatorId().equals("metadata-dispatcher")));
    }

    @Test
    public void testGetAllByTypes_multipleTypes() throws SQLException {
        List<DbNodeEventCursor> result = dao.getAllByTypes(
                List.of(NodeEventCursorType.METADATA_MIGRATION, NodeEventCursorType.FEDERATED_REPLICATION));
        assertEquals(result.size(), 2);
    }

    @Test
    public void testGetAllByTypes_noMatchingType() throws SQLException {
        List<DbNodeEventCursor> result = dao.getAllByTypes(List.of(NodeEventCursorType.EVIDENCE));
        assertEquals(result.size(), 0);
    }

    @Test
    public void testUpdateTimestamp() throws SQLException {
        dao.insert(DbNodeEventCursor.builder()
                .operatorId("testOpFed")
                .eventMarker(1L)
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.HEALTHY)
                .lastUpdated(2L)
                .build());

        dao.updateTimestamp("testOpFed", 4L);

        DbNodeEventCursor eventCursor = dao.cursorForOperator("testOpFed").get();

        assertEquals(eventCursor.getLastUpdated().longValue(), 4L);
        assertEquals(eventCursor.getEventMarker().longValue(), 1L);

        //cleanup
        dao.delete("testOpFed");
    }
}
