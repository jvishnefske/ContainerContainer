package org.artifactory.storage.db.event.service;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.dao.NodeEventsCursorDao;
import org.artifactory.storage.db.event.entity.DbNodeEventCursor;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.jfrog.common.ClockUtils;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorType.getEventIdLogTypes;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorType.getTimestampLogTypes;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class NodeEventCursorServiceImplTest {

    private static final String METADATA_OPERATOR_ID = "metadata-dispatcher";
    private static final String FEDERATED_OPERATOR_ID =
            "repo-federated_replication_url_to_repo-cursor";

    private NodeEventCursorService service;
    private NodeEventsCursorDao nodeEventsCursorDao;
    private EventsDao eventsDao;
    private RepositoryConfigService repositoryConfigService;
    @BeforeMethod
    public void setup() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);

        eventsDao = mock(EventsDao.class);
        nodeEventsCursorDao = mock(NodeEventsCursorDao.class);
        repositoryConfigService = mock(RepositoryConfigService.class);
        when(context.getRepositoryConfigService()).thenReturn(repositoryConfigService);

        NodeEventCursorServiceImpl nodeEventCursorService = new NodeEventCursorServiceImpl();
        nodeEventCursorService.setNodeEventsCursorDao(nodeEventsCursorDao);
        nodeEventCursorService.setEventsDao(eventsDao);
        service = nodeEventCursorService;
    }

    @AfterMethod
    public void teardown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    public void updateCursor() throws SQLException {
        long eventTimestamp = ClockUtils.epochMillis();
        service.updateOrInsertCursor(
                new NodeEventCursor(METADATA_OPERATOR_ID, eventTimestamp, NodeEventCursorType.METADATA_MIGRATION));
        DbNodeEventCursor eventCursor = DbNodeEventCursor.builder()
                .eventMarker(eventTimestamp)
                .operatorId(METADATA_OPERATOR_ID)
                .type(NodeEventCursorType.METADATA_MIGRATION)
                .build();
        verify(nodeEventsCursorDao).update(eventCursor);
    }

    public void cursorForOperatorId() throws SQLException {
        long eventTimestamp = ClockUtils.epochMillis();
        DbNodeEventCursor dbNodeEventCursor = DbNodeEventCursor.builder()
                .eventMarker(eventTimestamp)
                .operatorId(METADATA_OPERATOR_ID)
                .type(NodeEventCursorType.METADATA_MIGRATION)
                .build();
        when(nodeEventsCursorDao.cursorForOperator(METADATA_OPERATOR_ID)).thenReturn(Optional.of(dbNodeEventCursor));
        assertEquals(service.cursorForOperator(METADATA_OPERATOR_ID),
                new NodeEventCursor(METADATA_OPERATOR_ID, eventTimestamp, NodeEventCursorType.METADATA_MIGRATION));
    }

    @Test(dataProvider = "provideTimestamps")
    public void oldestTimestampForEventLog(long oldestTimestamp, long oldestEventId, long eventIdTimestamp,
            long expected) throws SQLException {
        when(nodeEventsCursorDao.oldestMarkerForTypes(getTimestampLogTypes())).thenReturn(oldestTimestamp);
        when(nodeEventsCursorDao.oldestMarkerForTypes(getEventIdLogTypes())).thenReturn(oldestEventId);
        when(eventsDao.getOldestTimestampByEventIdInclusive(oldestEventId)).thenReturn(eventIdTimestamp);
        assertThat(service.oldestTimestampForEventLogOperator()).isEqualTo(expected);
    }

    @Test
    public void testGetAllCursorsByStatus() throws SQLException {
        when(nodeEventsCursorDao.cursorForStatus(NodeEventCursorStatus.HEALTHY,
                NodeEventCursorType.FEDERATED_REPLICATION))
                .thenReturn(getDbNodeEvents());
        assertEquals(service.getAllCursorsByStatus(NodeEventCursorStatus.HEALTHY,
                NodeEventCursorType.FEDERATED_REPLICATION)
                        .size(),
                2);
    }

    @Test
    public void testFailOfGetAllCursorByStatus() throws SQLException {
        try {
            when(nodeEventsCursorDao.cursorForStatus(NodeEventCursorStatus.OUT_OF_SYNC,
                    NodeEventCursorType.FEDERATED_REPLICATION))
                    .thenThrow(new SQLException("not good"));
            service.getAllCursorsByStatus(NodeEventCursorStatus.OUT_OF_SYNC,
                    NodeEventCursorType.FEDERATED_REPLICATION);
        } catch (StorageException se) {
            assertTrue(se.getMessage().contains("OUT_OF_SYNC"));
        }
    }

    @Test
    public void testGetAllOldCursorsByTypeAndStatus() throws SQLException {

        when(nodeEventsCursorDao.getCursorsBy(NodeEventCursorType.getEventIdLogTypes(),
                NodeEventCursorStatus.HEALTHY, 1639935650L)).thenReturn(getDbNodeEvents());
        assertEquals(service.getEventIdCursorsBy(
                NodeEventCursorStatus.HEALTHY, 1639935650L)
                        .size(),
                2);
    }

    @Test
    public void testDeleteOrphanCursors_WithOrphans() throws SQLException {
        // Arrange
        var opId1 = "repo1-federated_replicationhttp://url/artifactory/remote1-cursor";
        var opId2 = "orphan1-federated_replicationhttp://url/artifactory/remote2-cursor";

        var dbNodeEventCursorOrphan1 = DbNodeEventCursor.builder()
                .operatorId(opId1)
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.OUT_OF_SYNC)
                .eventMarker(1L)
                .build();
        var dbNodeEventCursorOrphan2 = DbNodeEventCursor.builder()
                .operatorId(opId2)
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.OUT_OF_SYNC)
                .eventMarker(1L)
                .build();
        List<DbNodeEventCursor> dbNodeEventCursors = List.of(
                dbNodeEventCursorOrphan1,
                dbNodeEventCursorOrphan2
        );
        when(nodeEventsCursorDao.cursorForStatus(any(), any())).thenReturn(dbNodeEventCursors);
        when(nodeEventsCursorDao.delete(opId2)).thenReturn(1);
        when(repositoryConfigService.existsByRepoKey("repo1")).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey("orphan1")).thenReturn(false);

        // Act
        service.deleteOrphanCursors();

        // Assert
        verify(nodeEventsCursorDao, times(0)).delete(opId1);
        verify(nodeEventsCursorDao, times(1)).delete(opId2);

    }

    @Test
    public void testDeleteOrphanCursors_WithSqlException() throws SQLException {
        // Arrange
        var opId1 = "repo1-federated_replicationhttp://url/artifactory/remote1-cursor";
        var opId2 = "orphan1-federated_replicationhttp://url/artifactory/remote2-cursor";
        var opId3 = "orphan2-federated_replicationhttp://url/artifactory/remote2-cursor";

        var dbNodeEventCursorOrphan1 = DbNodeEventCursor.builder()
                .operatorId(opId1)
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.OUT_OF_SYNC)
                .eventMarker(1L)
                .build();
        var dbNodeEventCursorOrphan2 = DbNodeEventCursor.builder()
                .operatorId(opId2)
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.OUT_OF_SYNC)
                .eventMarker(1L)
                .build();
        var dbNodeEventCursorOrphan3 = DbNodeEventCursor.builder()
                .operatorId(opId3)
                .type(NodeEventCursorType.FEDERATED_REPLICATION)
                .status(NodeEventCursorStatus.OUT_OF_SYNC)
                .eventMarker(1L)
                .build();
        List<DbNodeEventCursor> dbNodeEventCursors = List.of(
                dbNodeEventCursorOrphan1,
                dbNodeEventCursorOrphan2,
                dbNodeEventCursorOrphan3
        );
        when(nodeEventsCursorDao.cursorForStatus(any(), any())).thenReturn(dbNodeEventCursors);
        when(nodeEventsCursorDao.delete(opId2)).thenThrow(SQLException.class);
        when(repositoryConfigService.existsByRepoKey("repo1")).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey("orphan1")).thenReturn(false);
        when(repositoryConfigService.existsByRepoKey("orphan2")).thenReturn(false);

        // Act
        try {
            service.deleteOrphanCursors();
        } catch (StorageException se) {
            assertTrue(se.getMessage().contains("Error when trying to get delete node event cursor"));
        }
    }

    @Test
    public void testUpdateCursorWithStatus() throws SQLException {
        service.updateCursorWithStatus("opId", NodeEventCursorStatus.OUT_OF_SYNC);
        verify(nodeEventsCursorDao, times(1)).updateCursorsStatus("opId",
                NodeEventCursorStatus.OUT_OF_SYNC);
    }

    @Test
    public void testUpdateCursorIfStatus() throws SQLException {
        int expectedUpdatedRows = 3;
        when(nodeEventsCursorDao.updateWhereStatus("opId", NodeEventCursorStatus.PENDING_FS,
                NodeEventCursorStatus.FS_RUNNING)).thenReturn(expectedUpdatedRows);
        int updatedRows = service.updateCursorFromStatusToStatus("opId", NodeEventCursorStatus.PENDING_FS,
                NodeEventCursorStatus.FS_RUNNING);
        assertEquals(updatedRows, expectedUpdatedRows);
    }

    @Test
    public void testUpdateCursorIfStatusNotUpdated() throws SQLException {
        int expectedUpdatedRows = 0;
        when(nodeEventsCursorDao.updateWhereStatus("opId", NodeEventCursorStatus.PENDING_FS,
                NodeEventCursorStatus.FS_RUNNING)).thenReturn(expectedUpdatedRows);
        int updatedRows = service.updateCursorFromStatusToStatus("opId", NodeEventCursorStatus.PENDING_FS,
                NodeEventCursorStatus.FS_RUNNING);
        assertEquals(updatedRows, expectedUpdatedRows);
    }

    @Test
    public void testUpdateCursorIfStatusStorageException() throws SQLException {
        when(nodeEventsCursorDao.updateWhereStatus("opId", NodeEventCursorStatus.FS_RUNNING,
                NodeEventCursorStatus.HEALTHY)).thenThrow(SQLException.class);
        assertThrows(StorageException.class,
                () -> service.updateCursorFromStatusToStatus("opId", NodeEventCursorStatus.FS_RUNNING,
                        NodeEventCursorStatus.HEALTHY));
    }

    @Test
    public void testUpdateWithTimestamp() throws SQLException {
        long lasUpdate = ClockUtils.epochMillis() - 1;
        ArgumentCaptor<Long> argument = ArgumentCaptor.forClass(Long.class);
        service.updateCursorMarker("op", 1L);
        verify(nodeEventsCursorDao, times(1))
                .updateWithMarkerAndTimestamp(anyString(), anyLong(), argument.capture());
        assertTrue(argument.getValue() > lasUpdate);
    }

    @Test
    public void testUpdateCursorLastUpdatedException() throws SQLException {
        doThrow(SQLException.class).when(nodeEventsCursorDao).updateTimestamp(any(), anyLong());
        assertThrows(StorageException.class, () -> service.updateCursorLastUpdated("op"));
    }

    @Test
    public void testUpdateCursorLastUpdated() throws SQLException {
        doNothing().when(nodeEventsCursorDao).updateTimestamp(any(), anyLong());
        service.updateCursorLastUpdated("op");
        verify(nodeEventsCursorDao).updateTimestamp(any(), anyLong());
    }

    @Test
    public void deleteFederatedCursorsForRepo_shouldDeleteCursors_whenCursorsExist() throws SQLException {
        // Arrange
        String repoKey = "my-federated-repo";
        List<DbNodeEventCursor> cursorsForRepo = List.of(
                DbNodeEventCursor.builder()
                        .operatorId(repoKey + "-federated_replicationhttp://member1.com/repo-cursor")
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .eventMarker(1L)
                        .build(),
                DbNodeEventCursor.builder()
                        .operatorId(repoKey + "-federated_replicationhttp://member2.com/repo-cursor")
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .eventMarker(2L)
                        .build()
        );
        when(nodeEventsCursorDao.cursorForRepoAndType(repoKey, NodeEventCursorType.FEDERATED_REPLICATION))
                .thenReturn(cursorsForRepo);
        when(nodeEventsCursorDao.delete(anyString())).thenReturn(1);

        // Act
        int deleted = service.deleteFederatedCursorsForRepo(repoKey);

        // Assert
        assertEquals(deleted, 2);
        verify(nodeEventsCursorDao).delete(repoKey + "-federated_replicationhttp://member1.com/repo-cursor");
        verify(nodeEventsCursorDao).delete(repoKey + "-federated_replicationhttp://member2.com/repo-cursor");
    }

    @Test
    public void deleteFederatedCursorsForRepo_shouldReturnZero_whenNoCursorsExist() throws SQLException {
        // Arrange
        String repoKey = "non-existent-repo";
        when(nodeEventsCursorDao.cursorForRepoAndType(repoKey, NodeEventCursorType.FEDERATED_REPLICATION))
                .thenReturn(List.of());

        // Act
        int deleted = service.deleteFederatedCursorsForRepo(repoKey);

        // Assert
        assertEquals(deleted, 0);
    }

    @Test
    public void deleteFederatedCursorsForRepo_shouldReturnZeroAndLog_whenQueryFails() throws SQLException {
        // Arrange
        String repoKey = "my-federated-repo";
        when(nodeEventsCursorDao.cursorForRepoAndType(repoKey, NodeEventCursorType.FEDERATED_REPLICATION))
                .thenThrow(new SQLException("DB error"));

        // Act
        int deleted = service.deleteFederatedCursorsForRepo(repoKey);

        // Assert - should return 0 and not throw (logs warning internally)
        assertEquals(deleted, 0);
    }

    @Test
    public void deleteFederatedCursorsForRepo_shouldContinue_whenDeleteFails() throws SQLException {
        // Arrange
        String repoKey = "my-federated-repo";
        String cursor1 = repoKey + "-federated_replicationhttp://member1.com/repo-cursor";
        String cursor2 = repoKey + "-federated_replicationhttp://member2.com/repo-cursor";
        List<DbNodeEventCursor> cursorsForRepo = List.of(
                DbNodeEventCursor.builder()
                        .operatorId(cursor1)
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .eventMarker(1L)
                        .build(),
                DbNodeEventCursor.builder()
                        .operatorId(cursor2)
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .eventMarker(2L)
                        .build()
        );
        when(nodeEventsCursorDao.cursorForRepoAndType(repoKey, NodeEventCursorType.FEDERATED_REPLICATION))
                .thenReturn(cursorsForRepo);
        // First delete succeeds, second fails
        when(nodeEventsCursorDao.delete(cursor1)).thenReturn(1);
        when(nodeEventsCursorDao.delete(cursor2)).thenThrow(new SQLException("Delete failed"));

        // Act
        int deleted = service.deleteFederatedCursorsForRepo(repoKey);

        // Assert - should return 1 (partial success) and not throw
        assertEquals(deleted, 1);
        verify(nodeEventsCursorDao).delete(cursor1);
        verify(nodeEventsCursorDao).delete(cursor2);
    }

    @Test
    public void deleteFederatedCursorsForRepo_shouldReturnZero_whenAllDeletesFail() throws SQLException {
        // Arrange
        String repoKey = "my-federated-repo";
        String cursor1 = repoKey + "-federated_replicationhttp://member1.com/repo-cursor";
        String cursor2 = repoKey + "-federated_replicationhttp://member2.com/repo-cursor";
        List<DbNodeEventCursor> cursorsForRepo = List.of(
                DbNodeEventCursor.builder()
                        .operatorId(cursor1)
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .eventMarker(1L)
                        .build(),
                DbNodeEventCursor.builder()
                        .operatorId(cursor2)
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .eventMarker(2L)
                        .build()
        );
        when(nodeEventsCursorDao.cursorForRepoAndType(repoKey, NodeEventCursorType.FEDERATED_REPLICATION))
                .thenReturn(cursorsForRepo);
        // Both deletes fail
        when(nodeEventsCursorDao.delete(cursor1)).thenThrow(new SQLException("Delete failed"));
        when(nodeEventsCursorDao.delete(cursor2)).thenThrow(new SQLException("Delete failed"));

        // Act
        int deleted = service.deleteFederatedCursorsForRepo(repoKey);

        // Assert - should return 0 (cursors found but all deletes failed)
        assertEquals(deleted, 0);
        verify(nodeEventsCursorDao).delete(cursor1);
        verify(nodeEventsCursorDao).delete(cursor2);
    }

    @Test
    public void getAllByTypes_shouldReturnCursorsForMatchingTypes() throws SQLException {
        List<DbNodeEventCursor> dbCursors = List.of(
                DbNodeEventCursor.builder()
                        .operatorId("fed-cursor")
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .status(NodeEventCursorStatus.HEALTHY)
                        .eventMarker(1L)
                        .build(),
                DbNodeEventCursor.builder()
                        .operatorId("fed-service-cursor")
                        .type(NodeEventCursorType.FEDERATION_SERVICE)
                        .status(NodeEventCursorStatus.HEALTHY)
                        .eventMarker(2L)
                        .build()
        );
        List<NodeEventCursorType> types = List.of(
                NodeEventCursorType.FEDERATED_REPLICATION, NodeEventCursorType.FEDERATION_SERVICE);
        when(nodeEventsCursorDao.getAllByTypes(types)).thenReturn(dbCursors);

        List<NodeEventCursor> result = service.getAllByTypes(types);

        assertEquals(result.size(), 2);
        assertEquals(result.get(0).getOperatorId(), "fed-cursor");
        assertEquals(result.get(1).getOperatorId(), "fed-service-cursor");
    }

    @Test
    public void getAllByTypes_shouldReturnEmpty_whenNoMatch() throws SQLException {
        List<NodeEventCursorType> types = List.of(NodeEventCursorType.FEDERATED_REPLICATION);
        when(nodeEventsCursorDao.getAllByTypes(types)).thenReturn(List.of());

        List<NodeEventCursor> result = service.getAllByTypes(types);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getAllByTypes_shouldThrowStorageException_whenSQLExceptionOccurs() throws SQLException {
        List<NodeEventCursorType> types = List.of(NodeEventCursorType.FEDERATED_REPLICATION);
        when(nodeEventsCursorDao.getAllByTypes(types)).thenThrow(new SQLException("DB error"));

        assertThrows(StorageException.class, () -> service.getAllByTypes(types));
    }

    @DataProvider
    private Object[][] provideTimestamps() {
        return new Object[][] {
                {100000L, 100, 50000L, 50000L},
                {100000L, 100, 200000L, 100000L},
                {0, 100, 200000L, 200000L},
                {100000L, 0, 0, 100000L},
                {0, 0, 0, 0},
        };
    }

    private List<DbNodeEventCursor> getDbNodeEvents() {
        return List.of(
                DbNodeEventCursor.builder()
                        .eventMarker(1L)
                        .operatorId(FEDERATED_OPERATOR_ID)
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .status(NodeEventCursorStatus.HEALTHY)
                        .lastUpdated(1639903154L)
                        .build(),
                DbNodeEventCursor.builder()
                        .eventMarker(2L)
                        .operatorId(FEDERATED_OPERATOR_ID + "2")
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .status(NodeEventCursorStatus.HEALTHY)
                        .lastUpdated(1639903155L)
                        .build()
        );
    }
}