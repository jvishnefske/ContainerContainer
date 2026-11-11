package org.artifactory.storage.db.fs.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleBlobQueryDaoTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String MANIFEST = "manifest.json";
    private static final String LIST_MANIFEST = "list.manifest.json";

    private StaleBlobQueryDao dao;
    private ArtifactoryJdbcHelper jdbcHelper;

    @BeforeEach
    public void setUp() {
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        NodesDao nodesDao = mock(NodesDao.class);
        dao = new StaleBlobQueryDao(jdbcHelper, dbProperties, nodesDao);
    }

    @Test
    void shouldContainNotExistsClause() throws SQLException {
        stubStreaming();
        dao.streamStaleBlobs("docker-local", 0L, 1000L, null, MANIFEST, LIST_MANIFEST);

        String query = captureQuery();
        assertTrue(query.contains("NOT EXISTS"));
        assertTrue(query.contains("sha256^_^_"));
        assertTrue(query.contains("ESCAPE '^'"));
        assertTrue(query.contains("^_uploads"));
        assertFalse(query.contains("node_path LIKE ?"));
    }

    @Test
    void shouldAppendPathFilterWhenPathPrefixProvided() throws SQLException {
        stubStreaming();
        dao.streamStaleBlobs("docker-local", 0L, 1000L, "library/nginx", MANIFEST, LIST_MANIFEST);

        String query = captureQuery();
        assertTrue(query.contains("node_path LIKE ? ESCAPE '^'"));
        Object[] params = captureParams();
        assertEquals(6, params.length);
        assertEquals("docker-local", params[0]);
        assertEquals(0L, params[1]);
        assertEquals(1000L, params[2]);
        assertEquals(MANIFEST, params[3]);
        assertEquals(LIST_MANIFEST, params[4]);
        assertEquals("library/nginx/%", params[5]);
    }

    @Test
    void shouldPassCorrectParamsWhenNoPathPrefix() throws SQLException {
        stubStreaming();
        dao.streamStaleBlobs("my-repo", 100L, 200L, null, MANIFEST, LIST_MANIFEST);

        Object[] params = captureParams();
        assertEquals(5, params.length);
        assertEquals("my-repo", params[0]);
        assertEquals(100L, params[1]);
        assertEquals(200L, params[2]);
        assertEquals(MANIFEST, params[3]);
        assertEquals(LIST_MANIFEST, params[4]);
    }

    @Test
    void shouldReturnEmptyStreamWhenNoResults() throws SQLException {
        stubStreaming();
        try (Stream<?> stream = dao.streamStaleBlobs("docker-local", 0L, 1000L, null, MANIFEST, LIST_MANIFEST)) {
            assertEquals(0, stream.count());
        }
    }

    @Test
    void shouldPropagateSqlException() throws SQLException {
        when(jdbcHelper.executeSelectStreamed(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenThrow(new SQLException("Connection failed"));

        assertThrows(SQLException.class,
                () -> dao.streamStaleBlobs("docker-local", 0L, 1000L, null, MANIFEST, LIST_MANIFEST));
    }

    @Test
    void shouldUseManifestNamesInNotExistsClause() throws SQLException {
        stubStreaming();
        dao.streamStaleBlobs("docker-local", 0L, 1000L, null, MANIFEST, LIST_MANIFEST);

        assertTrue(captureQuery().contains("m.node_name = ? OR m.node_name = ?"));
    }

    @Test
    void shouldUseTimeRangeInQuery() throws SQLException {
        stubStreaming();
        dao.streamStaleBlobs("docker-local", 0L, 1000L, null, MANIFEST, LIST_MANIFEST);

        String query = captureQuery();
        assertTrue(query.contains("b.created >= ?"));
        assertTrue(query.contains("b.created < ?"));
    }

    @Test
    void shouldReturnOldestBlobTimestamp() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("created")).thenReturn(1500L);
        when(rs.wasNull()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);

        OptionalLong result = dao.getOldestBlobCreated("docker-local", null);
        assertTrue(result.isPresent());
        assertEquals(1500L, result.getAsLong());
    }

    @Test
    void shouldReturnEmptyWhenNoBlobsExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("created")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);

        OptionalLong result = dao.getOldestBlobCreated("docker-local", null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUseMinCreatedQueryWithoutPathFilter() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.wasNull()).thenReturn(true);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);

        dao.getOldestBlobCreated("docker-local", null);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcHelper).executeSelect(queryCaptor.capture(), paramsCaptor.capture());
        String query = queryCaptor.getValue();
        assertTrue(query.contains("MIN(b.created)"));
        assertTrue(query.contains("sha256^_^_"));
        assertFalse(query.contains("NOT EXISTS"));
        assertFalse(query.contains("node_path LIKE ?"));
        assertEquals(1, paramsCaptor.getValue().length);
    }

    @Test
    void shouldAppendPathFilterToOldestBlobQuery() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("created")).thenReturn(500L);
        when(rs.wasNull()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(rs);

        OptionalLong result = dao.getOldestBlobCreated("docker-local", "library/nginx");
        assertTrue(result.isPresent());
        assertEquals(500L, result.getAsLong());

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcHelper).executeSelect(queryCaptor.capture(), paramsCaptor.capture());
        assertTrue(queryCaptor.getValue().contains("node_path LIKE ? ESCAPE '^'"));
        Object[] params = paramsCaptor.getValue();
        assertEquals(2, params.length);
        assertEquals("docker-local", params[0]);
        assertEquals("library/nginx/%", params[1]);
    }

    @Test
    void shouldNormalizeTrailingSlashInPathPrefix() throws SQLException {
        stubStreaming();
        dao.streamStaleBlobs("docker-local", 0L, 1000L, "library/nginx/", MANIFEST, LIST_MANIFEST);

        Object[] params = captureParams();
        assertEquals("library/nginx/%", params[5]);
    }

    @Test
    void shouldEscapeSqlWildcardsInPathPrefix() throws SQLException {
        stubStreaming();
        dao.streamStaleBlobs("docker-local", 0L, 1000L, "lib_rary/100%done", MANIFEST, LIST_MANIFEST);

        Object[] params = captureParams();
        assertEquals("lib^_rary/100^%done/%", params[5]);
    }

    @Test
    void shouldEscapeCaretInPathPrefix() throws SQLException {
        stubStreaming();
        dao.streamStaleBlobs("docker-local", 0L, 1000L, "lib^rary/image", MANIFEST, LIST_MANIFEST);

        Object[] params = captureParams();
        assertEquals("lib^^rary/image/%", params[5]);
    }

    private void stubStreaming() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelectStreamed(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenReturn(rs);
    }

    private String captureQuery() throws SQLException {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelectStreamed(any(QueryControls.class), captor.capture(), any(Object[].class));
        return captor.getValue();
    }

    private Object[] captureParams() throws SQLException {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcHelper).executeSelectStreamed(any(QueryControls.class), anyString(), captor.capture());
        return captor.getValue();
    }
}
