package org.artifactory.storage.db.postgresql.dao;

import org.artifactory.storage.db.postgresql.entity.IndexSizeEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class PgSchemaDaoTest {

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;

    private PgSchemaDao pgSchemaDao;

    @Captor
    private ArgumentCaptor<String> queryCaptor;

    @BeforeEach
    void setup() {
        pgSchemaDao = new PgSchemaDao(jdbcHelper);
    }

    @Test
    void getIndexSizes_shouldReturnCorrectEntities_whenMultipleIndexesQueried() throws SQLException {
        // Arrange
        ResultSet resultSet = mockResultSetWithMultipleRows();
        doReturn(resultSet).when(jdbcHelper).executeSelect(anyString(), any(Object[].class));

        // Act
        Set<IndexSizeEntity> result = pgSchemaDao.getIndexSizes(Set.of("idx_0", "idx_1"));

        // Assert
        assertEquals(2, result.size());
        verify(jdbcHelper).executeSelect(queryCaptor.capture(), any(Object[].class));
        assertTrue(queryCaptor.getValue().contains("IN (?,?)"));
    }

    @Test
    void getIndexSizes_shouldReturnEntityWithCorrectData_whenQueried() throws SQLException {
        // Arrange
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("index_name")).thenReturn("idx_builds_build_id");
        when(resultSet.getLong("index_size")).thenReturn(512000L);
        when(resultSet.getString("table_name")).thenReturn("builds");
        when(resultSet.getLong("table_size")).thenReturn(10000000L);
        when(resultSet.getLong("table_rows")).thenReturn(500L);

        doReturn(resultSet).when(jdbcHelper).executeSelect(anyString(), any(Object[].class));

        // Act
        Set<IndexSizeEntity> result = pgSchemaDao.getIndexSizes(Set.of("idx_builds_build_id"));

        // Assert
        IndexSizeEntity entity = result.iterator().next();
        assertEquals("idx_builds_build_id", entity.indexName());
        assertEquals(512000L, entity.indexSize());
        assertEquals("builds", entity.tableName());
        assertEquals(10000000L, entity.tableSize());
        assertEquals(500L, entity.tableRows());
    }

    @Test
    void getIndexSizes_shouldReturnEmptySet_whenNoIndexesFound() throws SQLException {
        // Arrange
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        doReturn(resultSet).when(jdbcHelper).executeSelect(anyString(), any(Object[].class));

        // Act
        Set<IndexSizeEntity> result = pgSchemaDao.getIndexSizes(Set.of("non_existent_index"));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getIndexSizes_shouldThrowSQLException_whenQueryFails() throws SQLException {
        // Arrange
        doThrow(new SQLException("Connection error")).when(jdbcHelper).executeSelect(anyString(), any(Object[].class));

        // Act & Assert
        assertThrows(SQLException.class, () -> pgSchemaDao.getIndexSizes(Set.of("idx_nodes_sha256")));
    }

    private ResultSet mockResultSetWithMultipleRows() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("index_name")).thenReturn("idx_0", "idx_1");
        when(resultSet.getLong("index_size")).thenReturn(1024000L, 2048000L);
        when(resultSet.getString("table_name")).thenReturn("table_0", "table_1");
        when(resultSet.getLong("table_size")).thenReturn(50000000L, 100000000L);
        when(resultSet.getLong("table_rows")).thenReturn(1000L, 2000L);

        return resultSet;
    }
}
