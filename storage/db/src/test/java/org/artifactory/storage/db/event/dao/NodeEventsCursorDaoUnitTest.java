package org.artifactory.storage.db.event.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.event.entity.DbNodeEventCursor;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.SqlDaoHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeEventsCursorDaoUnitTest {

    private NodeEventsCursorDao dao;

    @Mock
    private JdbcHelper jdbcHelper;

    @Mock
    private ArtifactoryDbProperties dbProperties;

    @Mock
    private SqlDaoHelper<DbNodeEventCursor> daoHelper;

    @BeforeEach
    void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        dao = new NodeEventsCursorDao(jdbcHelper, dbProperties);

        // Inject mocked daoHelper using reflection
        Field daoHelperField = NodeEventsCursorDao.class.getDeclaredField("daoHelper");
        daoHelperField.setAccessible(true);
        daoHelperField.set(dao, daoHelper);
    }

    @Test
    void cursorForRepoAndType_shouldQueryWithCorrectParameters() throws SQLException {
        // Arrange
        String repoKey = "my-fed-repo";
        List<DbNodeEventCursor> expectedCursors = List.of(
                DbNodeEventCursor.builder()
                        .operatorId(repoKey + "-federated_replicationhttp://member1.com/repo-cursor")
                        .type(NodeEventCursorType.FEDERATED_REPLICATION)
                        .eventMarker(1L)
                        .build()
        );
        when(daoHelper.findAllByQuery(anyString(), eq("FEDERATED_REPLICATION"), eq(repoKey + "-%")))
                .thenReturn(expectedCursors);

        // Act
        List<DbNodeEventCursor> result = dao.cursorForRepoAndType(repoKey, NodeEventCursorType.FEDERATED_REPLICATION);

        // Assert
        assertEquals(1, result.size());
        verify(daoHelper).findAllByQuery(
                "SELECT * FROM node_event_cursor WHERE type = ? AND operator_id LIKE ?",
                "FEDERATED_REPLICATION",
                repoKey + "-%");
    }

    @Test
    void cursorForRepoAndType_shouldReturnEmptyList_whenNoCursorsFound() throws SQLException {
        // Arrange
        String repoKey = "non-existent-repo";
        when(daoHelper.findAllByQuery(anyString(), eq("FEDERATED_REPLICATION"), eq(repoKey + "-%")))
                .thenReturn(List.of());

        // Act
        List<DbNodeEventCursor> result = dao.cursorForRepoAndType(repoKey, NodeEventCursorType.FEDERATED_REPLICATION);

        // Assert
        assertEquals(0, result.size());
    }
}



