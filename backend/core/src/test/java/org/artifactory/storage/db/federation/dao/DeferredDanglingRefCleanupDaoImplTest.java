package org.artifactory.storage.db.federation.dao;

import org.artifactory.federation.DeferredDanglingRefCleanupDao.DeferredDanglingRefEntry;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeferredDanglingRefCleanupDaoImpl} (mocked JDBC).
 */
@ExtendWith(MockitoExtension.class)
class DeferredDanglingRefCleanupDaoImplTest {

    private static final String TABLE = "virtual_repo_dangling_refs";
    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE
                    + " (virtual_repo_key, child_repo_key, created, modified)"
                    + " VALUES (?, ?, ?, ?)";
    private static final String UPDATE_MODIFIED_SQL =
            "UPDATE " + TABLE + " SET modified = ?"
                    + " WHERE virtual_repo_key = ? AND child_repo_key = ?";
    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE
                    + " WHERE virtual_repo_key = ? AND child_repo_key = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT virtual_repo_key, child_repo_key FROM " + TABLE;

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;

    private DeferredDanglingRefCleanupDaoImpl dao;

    @BeforeEach
    void setUp() {
        dao = new DeferredDanglingRefCleanupDaoImpl(jdbcHelper);
    }

    @Test
    void upsert_inserts_whenNoRowUpdated() throws SQLException {
        when(jdbcHelper.executeUpdate(eq(UPDATE_MODIFIED_SQL), anyLong(),
                eq("virt"), eq("child"))).thenReturn(0);

        dao.upsert("virt", "child");

        verify(jdbcHelper).executeUpdate(eq(UPDATE_MODIFIED_SQL), anyLong(),
                eq("virt"), eq("child"));
        verify(jdbcHelper).executeUpdate(eq(INSERT_SQL), eq("virt"),
                eq("child"), anyLong(), anyLong());
    }

    @Test
    void upsert_skipsInsert_whenRowUpdated() throws SQLException {
        when(jdbcHelper.executeUpdate(eq(UPDATE_MODIFIED_SQL), anyLong(),
                eq("virt"), eq("child"))).thenReturn(1);

        dao.upsert("virt", "child");

        verify(jdbcHelper).executeUpdate(eq(UPDATE_MODIFIED_SQL), anyLong(),
                eq("virt"), eq("child"));
        verify(jdbcHelper, never()).executeUpdate(eq(INSERT_SQL), anyString(),
                anyString(), anyLong(), anyLong());
    }

    @Test
    void delete_executesDeleteSql() throws SQLException {
        dao.delete("virt", "child");

        verify(jdbcHelper).executeUpdate(DELETE_SQL, "virt", "child");
    }

    @Test
    void findAll_returnsEntries() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("virtual_repo_key")).thenReturn("v1", "v2");
        when(rs.getString("child_repo_key")).thenReturn("c1", "c2");
        doReturn(rs).when(jdbcHelper).executeSelect(SELECT_ALL_SQL);

        List<DeferredDanglingRefEntry> result = dao.findAll();

        assertEquals(2, result.size());
        assertEquals("v1", result.get(0).virtualRepoKey());
        assertEquals("c1", result.get(0).childRepoKey());
        assertEquals("v2", result.get(1).virtualRepoKey());
        assertEquals("c2", result.get(1).childRepoKey());
        verify(jdbcHelper).executeSelect(SELECT_ALL_SQL);
    }

    @Test
    void findAll_propagatesSQLException() throws SQLException {
        doThrow(new SQLException("db error")).when(jdbcHelper)
                .executeSelect(anyString());

        assertThrows(SQLException.class, () -> dao.findAll());
    }
}
