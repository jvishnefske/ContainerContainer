package org.artifactory.ui.rest.service.artifacts.search.syntax;

import lombok.Getter;
import org.artifactory.common.repo.PackageType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.artifacts.search.syntax.DateRange;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.State;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Type;
import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryFilesTest.bind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
@ExtendWith(MockitoExtension.class)
class SyntaxSearchQueryPathsTest {

    private static final String INJECTION_PAYLOAD = "'; DROP TABLE nodes_view; --";
    private static final Deadline INFINITE_DEADLINE = Deadline.startingNow(Integer.MAX_VALUE);

    @Mock
    private JdbcHelper mockJdbcHelper;

    private List<Node> testNodes;
    private TestRequestExecutor testExecutor;

    @BeforeEach
    void setUp() {
        testNodes = new ArrayList<>();
        testNodes.add(new Node(1L, "maven-local", "com/example", "artifact", 1024L,
                1640995200000L, 0L));
        testNodes.add(new Node(2L, "maven-local", "com/example/utils", "utils", 2048L,
                1640995100000L, 0L));
        testNodes.add(new Node(3L, "npm-local", "packages", "package", 512L,
                1640995000000L, 0L));
        testNodes.add(new Node(4L, "docker-local", "images", "image", 4096L,
                1640994900000L, 0L));

        testExecutor = new TestRequestExecutor(testNodes);
    }

    @Test
    void withRepoField_ShouldGenerateCorrectCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPathField_ShouldGenerateCorrectCondition() throws SQLException {
        Query query = new Query("search text", null, "com/example", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (node_path = 'com/example' or node_path like 'com/example/%')
                    and (node_ts_vector @@ to_tsquery('simple', 'search & text:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withTextField_ShouldGenerateCorrectCondition() throws SQLException {
        Query query = new Query("artifact", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (node_ts_vector @@ to_tsquery('simple', 'artifact:* & artifact:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPathAndText_ShouldUseChildrenPathsCondition() throws SQLException {
        Query query = new Query("artifact", null, "com/example", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (node_path = 'com/example' or node_path like 'com/example/%')
                    and (node_ts_vector @@ to_tsquery('simple', 'artifact:* & artifact:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withRepoState_ShouldAddStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = new State(Type.REPO, "maven-local", 1640995200000L);

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and (modified, repository_key) < (1640995200000, 'maven-local')
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPathState_ShouldAddNodeStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = new State(Type.FOLDER, "123", 1640995200000L);

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and (modified, node_id) < (1640995200000, 123)
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withFileState_ShouldAddNodeStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = new State(Type.FILE, "456", 1640995200000L);

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and (modified, node_id) < (1640995200000, 456)
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withStopState_ShouldNotAddStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = new State(Type.STOP, "", 0L);

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withNullState_ShouldNotAddStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withLastModifiedFilter_ShouldAddLastModifiedCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        Filters filters =
                new Filters(null, null, null, new DateRange(1640995000000L, null), null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and modified >= 1640995000000
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withLastModifiedFilterAndRepositoryFilters_ShouldAddLastModifiedConditionWithTablePrefix()
            throws SQLException {
        Query query = new Query("search", null, null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null,
                new DateRange(1640995000000L, null), null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created
                from nodes_view n
                 JOIN repository_config rc ON\s
                     n.repo = CASE\s
                         WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                         ELSE rc.repository_key
                     END
                   \s
                where
                    n.repo <> 'auto-trashcan'
                    and n.node_type = 0
                    and n.depth > 0
                    and n.modified >= 1640995000000
                    and (n.node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and type IN ('local')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withEmptyRepoAndPathAndText_ShouldNotAddConditions() throws SQLException {
        Query query = new Query("", "", "", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withNullRepoAndPathAndText_ShouldNotAddConditions() throws SQLException {
        Query query = new Query(null, null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withWhitespaceInTerms_ShouldTrimAndSplit() throws SQLException {
        Query query = new Query("  maven   npm  ", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (node_ts_vector @@ to_tsquery('simple', 'maven & npm:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withSimplePath_ShouldUseNodeNameCondition() throws SQLException {
        Query query = new Query("search", null, "artifact", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (node_path = 'artifact' or node_path like 'artifact/%')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withComplexPath_ShouldUseFtsCondition() throws SQLException {
        Query query = new Query("search", null, "com/example/utils", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (node_path = 'com/example/utils' or node_path like 'com/example/utils/%')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void executorThrowsSQLException() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = null;

        RequestExecutor<Node> failingExecutor = mock(RequestExecutor.class);
        SQLException expectedException = new SQLException("Database connection failed");
        when(failingExecutor.execute(any(JdbcHelper.class), any(), any(Deadline.class))).thenThrow(expectedException);

        SQLException exception = assertThrows(SQLException.class, () ->
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, failingExecutor));

        assertEquals(expectedException, exception);
    }

    @Test
    void withZeroLimit_ShouldPassZeroToQuery() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 0;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 0;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withLargeLimit_ShouldPassLargeValueToQuery() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = Integer.MAX_VALUE;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 2147483647;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withAllFields_ShouldGenerateCompleteCondition() throws SQLException {
        Query query = new Query("artifact", "maven", "com/example", null);
        int limit = 5;
        State state = new State(Type.FOLDER, "123", 1640995200000L);

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 0
                    and depth > 0
                    and (repo='maven')
                    and (node_path = 'com/example' or node_path like 'com/example/%')
                    and (node_ts_vector @@ to_tsquery('simple', 'artifact:* & artifact:*'))
                    and (modified, node_id) < (1640995200000, 123)
                order by modified desc, node_id desc
                limit 5;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withRepositoryTypeFilter_ShouldUseJoinAndAddTypeCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null, null, null);
        int limit = 10;
        State state = null;
        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);
        assertNotNull(result);

        String expectedSql = """
                select n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created
                from nodes_view n
                 JOIN repository_config rc ON
                     n.repo = CASE
                         WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                         ELSE rc.repository_key
                     END
                where
                    n.repo <> 'auto-trashcan'
                    and n.node_type = 0
                    and n.depth > 0
                    and (n.repo='maven')
                    and (n.node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and type IN ('local')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPackageTypeFilter_ShouldUseJoinAndAddPackageTypeCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        Filters filters = new Filters(null, null, Set.of(PackageType.MAVEN), null, null);
        int limit = 10;
        State state = null;
        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);
        assertNotNull(result);

        String expectedSql = """
                select n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created
                from nodes_view n
                 JOIN repository_config rc ON
                     n.repo = CASE
                         WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                         ELSE rc.repository_key
                     END
                where
                    n.repo <> 'auto-trashcan'
                    and n.node_type = 0
                    and n.depth > 0
                    and (n.repo='maven')
                    and (n.node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and package_type IN ('maven')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withBothFilters_ShouldUseJoinAndAddBothConditions() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), Set.of(PackageType.MAVEN), null, null);
        int limit = 10;
        State state = null;
        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);
        assertNotNull(result);

        String expectedSql = """
                select n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created
                from nodes_view n
                 JOIN repository_config rc ON
                     n.repo = CASE
                         WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                         ELSE rc.repository_key
                     END
                where
                    n.repo <> 'auto-trashcan'
                    and n.node_type = 0
                    and n.depth > 0
                    and (n.repo='maven')
                    and (n.node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and type IN ('local')
                    and package_type IN ('maven')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withFilters_ShouldHandleRemoteCacheRepos() throws SQLException {
        Query query = new Query("search", null, null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.REMOTE), null, null, null);
        int limit = 10;
        State state = null;
        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);
        assertNotNull(result);

        String expectedSql = """
                select n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created
                from nodes_view n
                 JOIN repository_config rc ON
                     n.repo = CASE
                         WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                         ELSE rc.repository_key
                     END
                where
                    n.repo <> 'auto-trashcan'
                    and n.node_type = 0
                    and n.depth > 0
                    and (n.node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and type IN ('remote')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void sqlInjection() throws SQLException {
        Query query = new Query(null, null, INJECTION_PAYLOAD, null);
        int limit = 10;
        State state = null;

        SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state,
                false, mockJdbcHelper,
                INFINITE_DEADLINE, testExecutor);

        PreparedQuery lastQuery = testExecutor.getLastPreparedQuery();
        assertFalse(lastQuery.getTemplate().contains(INJECTION_PAYLOAD),
                "SQL template must not contain user input (injection risk)");
    }


    /**
     * Normalizes SQL queries by removing extra spaces to make comparison not related to spaces
     */
    static String normalizeSql(String sql) {
        if (sql == null) {
            return null;
        }
        // Replace multiple whitespace characters with single space and trim
        return Pattern.compile("\\s+").matcher(sql.trim()).replaceAll(" ");
    }

    private static class TestRequestExecutor implements RequestExecutor<Node> {
        private final List<Node> nodes;
        @Getter
        private String lastExecutedQuery;
        @Getter
        private PreparedQuery lastPreparedQuery;

        public TestRequestExecutor(List<Node> nodes) {
            this.nodes = new ArrayList<>(nodes);
        }

        @Override
        public List<Node> execute(JdbcHelper jdbcHelper, PreparedQuery query, Deadline deadline) throws SQLException {
            this.lastPreparedQuery = query;
            String template = query.getTemplate();
            List<Object> params = query.getParams();
            this.lastExecutedQuery = bind(template, params);
            return new ArrayList<>(nodes);
        }
    }

    @Test
    void withUnderscoreInText_ShouldEscapeForIlike() throws SQLException {
        Query query = new Query("test_folder", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%test^_folder%' ESCAPE '^'"),
                "Should escape underscore with ^ and add ESCAPE clause");
    }

    @Test
    void withPercentInText_ShouldEscapeForIlike() throws SQLException {
        Query query = new Query("50%", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%50^%%' ESCAPE '^'"),
                "Should escape percent with ^ and add ESCAPE clause");
    }

    @Test
    void withSlashInText_ShouldNotAddEscape() throws SQLException {
        Query query = new Query("my/path", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%my/path%'"),
                "Should have ilike for path with slash");
        assertFalse(lastQuery.contains("ESCAPE"),
                "Should not add ESCAPE clause when no wildcards");
    }

    @Test
    void withMixedSpecialChars_ShouldAddEscapeOnlyForWildcards() throws SQLException {
        Query query = new Query("my/path data_file 50%", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%my/path%'"),
                "Should have ilike for my/path");
        assertFalse(lastQuery.contains("ilike '%my/path%' ESCAPE"),
                "Should not add ESCAPE for my/path (no wildcards)");
        assertTrue(lastQuery.contains("ilike '%data^_file%' ESCAPE '^'"),
                "Should add ESCAPE for data_file (has underscore)");
        assertTrue(lastQuery.contains("ilike '%50^%%' ESCAPE '^'"),
                "Should add ESCAPE for 50% (has percent)");
    }

    @Test
    void withSpecialCharsInText_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("jfrog/artifactory", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify FTS condition exists
        assertTrue(normalized.contains("node_ts_vector @@ to_tsquery"),
                "Should have FTS condition");

        // Verify ilike condition exists
        assertTrue(normalized.contains("ilike '%jfrog/artifactory%'"),
                "Should have ilike condition");

        // Verify they are combined with AND (not OR)
        assertTrue(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+and\\s+.*ilike.*"),
                "Should use AND operator between FTS and ilike, not OR");

        assertFalse(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+or\\s+.*ilike.*"),
                "Should NOT use OR operator between FTS and ilike");
    }

    @Test
    void withUnderscoreInText_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("test_folder", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify both conditions exist
        assertTrue(normalized.contains("to_tsquery('simple', 'test & folder:*')"),
                "Should have FTS condition");
        assertTrue(normalized.contains("ilike '%test^_folder%' ESCAPE '^'"),
                "Should have ilike condition with escaped underscore");

        // Verify AND operator
        assertTrue(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+and\\s+.*ilike.*ESCAPE.*"),
                "Should use AND operator between FTS and escaped ilike");
    }

    @Test
    void withMixedSpecialChars_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("docker/data-v2.0", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify FTS condition
        assertTrue(normalized.contains("to_tsquery"),
                "Should have FTS condition");

        // Verify ilike condition
        assertTrue(normalized.contains("ilike '%docker/data-v2.0%'"),
                "Should have ilike condition for the full path");

        // Verify AND between FTS and ilike
        assertTrue(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+and\\s+.*ilike.*"),
                "Must use AND to ensure both FTS and literal match succeed");
    }

    @Test
    void withOnlyToDateShouldNotIncludeDateFilter() throws SQLException {
        Query query = new Query("test", "repo", null, null);
        Filters filters = new Filters(null, null, null, null, new DateRange(null, 1735732800000L));

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertFalse(executedSql.contains("created >="));
        assertFalse(executedSql.contains("created <="));
    }

    @Test
    void withFolderPathWithTrailingSlash_ShouldPreserveInIlike() throws SQLException {
        Query query = new Query(null, null, "artifactory/", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%artifactory/%'"),
                "Should preserve trailing slash in ilike pattern");
    }

    @Test
    void withFolderPathWithoutTrailingSlash_ShouldNotAddSlashToIlike() throws SQLException {
        Query query = new Query(null, null, "artifactory", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%artifactory%'"),
                "Should not add trailing slash when not provided");
        assertFalse(lastQuery.contains("ilike '%artifactory/%'"),
                "Should not have trailing slash in ilike when not in input");
    }

    @Test
    void withFolderPathLeadingAndTrailingSlash_ShouldStripLeadingPreserveTrailing() throws SQLException {
        Query query = new Query(null, null, "/artifactory/", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%artifactory/%'"),
                "Should strip leading slash but preserve trailing slash");
        assertFalse(lastQuery.contains("ilike '%/artifactory/%'"),
                "Should not have leading slash in ilike pattern");
    }

    @Test
    void withMultiLevelPath_ShouldUseFtsAndIlike() throws SQLException {
        Query query = new Query(null, null, "docker/prod", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Should have FTS condition
        assertTrue(normalized.contains("node_ts_vector @@ to_tsquery"),
                "Should have FTS condition for multi-level path");

        // Should have ilike condition
        assertTrue(normalized.contains("ilike '%docker/prod%'"),
                "Should have ilike condition for multi-level path");

        // Should use AND between FTS and ilike
        assertTrue(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+and\\s+.*ilike.*"),
                "Should use AND operator between FTS and ilike");
    }

    @Test
    void withMultiLevelPathWithTrailingSlash_ShouldPreserveInIlike() throws SQLException {
        Query query = new Query(null, null, "/docker/prod/", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();

        assertTrue(lastQuery.contains("to_tsquery('simple', 'docker & prod:*')"),
                "Should use stripped path for FTS");
        assertTrue(lastQuery.contains("ilike '%docker/prod/%'"),
                "Should preserve trailing slash in ilike, strip leading");
    }

    @Test
    void withSingleLevelPath_ShouldOnlyUseFullPathIlike() throws SQLException {
        Query query = new Query(null, null, "artifactory", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Should have path ilike (without repo, since folder searches exclude repo)
        assertTrue(normalized.contains("node_path || '/' || node_name ilike '%artifactory%'"),
                "Should have path ilike for single-level path");

        // Should NOT have FTS (single level without /)
        assertFalse(normalized.contains("to_tsquery"),
                "Should not have FTS for single-level path without /");
    }
}
