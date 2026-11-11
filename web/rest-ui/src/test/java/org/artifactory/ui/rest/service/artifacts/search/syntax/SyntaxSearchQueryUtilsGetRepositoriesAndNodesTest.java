package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
@ExtendWith(MockitoExtension.class)
class SyntaxSearchQueryUtilsGetRepositoriesAndNodesTest {

    @Mock
    private JdbcHelper mockJdbcHelper;

    @Mock
    private ResultSet mockResultSet;

    @BeforeEach
    void setUp() throws SQLException {
        when(mockJdbcHelper.executeSelect(any(), anyString(), any(Object[].class))).thenReturn(mockResultSet);
    }

    @Test
    void getRepositories_WithSingleRepository_ShouldReturnRepository() throws SQLException {

        when(mockResultSet.next()).thenReturn(true, false); // Has one row then no more rows

        when(mockResultSet.getString("repository_key")).thenReturn("maven-local");
        when(mockResultSet.getString("type")).thenReturn("local");
        when(mockResultSet.getString("package_type")).thenReturn("maven");
        when(mockResultSet.getLong("modified")).thenReturn(1640995200000L);
        when(mockResultSet.getLong("created")).thenReturn(0L);

        List<Repository> result =
                SyntaxSearchQueryUtils.getRepositories(mockJdbcHelper, new PreparedQuery(),
                        Deadline.startingNow(Integer.MAX_VALUE));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("maven-local", result.get(0).repo());
        assertEquals("local", result.get(0).type());
        assertEquals("maven", result.get(0).packageType());
        assertEquals(1640995200000L, result.get(0).modified());
    }

    @Test
    void getNodes_WithSingleNode_ShouldReturnNode() throws SQLException {

        when(mockResultSet.next()).thenReturn(true, false);

        when(mockResultSet.getLong("node_id")).thenReturn(123L);
        when(mockResultSet.getString("repo")).thenReturn("maven-local");
        when(mockResultSet.getString("node_path")).thenReturn("com/example");
        when(mockResultSet.getString("node_name")).thenReturn("artifact.jar");
        when(mockResultSet.getLong("bin_length")).thenReturn(1024L);
        when(mockResultSet.getLong("modified")).thenReturn(1640995200000L);
        when(mockResultSet.getLong("created")).thenReturn(1640995000000L);

        List<Node> result = SyntaxSearchQueryUtils.getNodes(mockJdbcHelper, new PreparedQuery(),
                Deadline.startingNow(Integer.MAX_VALUE));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(123L, result.get(0).nodeId());
        assertEquals("maven-local", result.get(0).repo());
        assertEquals("com/example", result.get(0).path());
        assertEquals("artifact.jar", result.get(0).name());
        assertEquals(1024L, result.get(0).binLength());
        assertEquals(1640995200000L, result.get(0).modified());
        assertEquals(1640995000000L, result.get(0).created());
    }
}

