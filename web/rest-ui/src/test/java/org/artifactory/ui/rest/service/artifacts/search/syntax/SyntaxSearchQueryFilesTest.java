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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
@ExtendWith(MockitoExtension.class)
class SyntaxSearchQueryFilesTest {

    private static final String INJECTION_PAYLOAD = "'; DROP TABLE nodes_view; --";
    private static final Deadline INFINITE_DEADLINE = Deadline.startingNow(Integer.MAX_VALUE);

    @Mock
    private JdbcHelper mockJdbcHelper;

    private List<Node> testNodes;
    private TestRequestExecutor testExecutor;

    @BeforeEach
    void setUp() {
        testNodes = new ArrayList<>();
        testNodes.add(new Node(1L, "maven-local", "com/example", "artifact.jar", 1024L,
                1640995200000L, 0L));
        testNodes.add(new Node(2L, "maven-local", "com/example/utils", "utils.jar", 2048L,
                1640995100000L, 0L));
        testNodes.add(new Node(3L, "npm-local", "packages", "package.tgz", 512L,
                1640995000000L, 0L));
        testNodes.add(new Node(4L, "docker-local", "images", "image.tar", 4096L,
                1640994900000L, 0L));

        testExecutor = new TestRequestExecutor(testNodes);
    }

    @Test
    void withRepoField_ShouldGenerateCorrectCondition() throws SQLException {
        Query query = new Query("search text", "maven", null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search & text:*'))
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (node_path = 'com/example' or node_path like 'com/example/%')
                    and (node_ts_vector @@ to_tsquery('simple', 'search & text:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPathFieldWithoutText_ShouldUsePathsConditionForFile() throws SQLException {
        Query query = new Query(null, null, "com/example", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and node_ts_vector @@ to_tsquery('simple', 'com & example:*')
                    and repo || '/' || node_path || '/' || node_name ilike '%com/example%'
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (node_ts_vector @@ to_tsquery('simple', 'artifact:* & artifact:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withShaField_ShouldGenerateCorrectCondition() throws SQLException {
        String validSha1 = "356a192b7913b04c54574d18c28d46e6395428ab";
        Query query = new Query("search text", null, null, validSha1);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and sha1_actual='356a192b7913b04c54574d18c28d46e6395428ab'
                    and (node_ts_vector @@ to_tsquery('simple', 'search & text:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withInvalidSha1InText_ShouldFilterOutSha1Condition() throws SQLException {
        Query query = new Query("invalidshavalue", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (node_ts_vector @@ to_tsquery('simple', 'invalidshavalue:* & invalidshavalue:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withValidSha1InShaField_ShouldGenerateExactMatch() throws SQLException {
        String validSha1 = "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12";
        Query query = new Query(null, null, null, validSha1);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and sha1_actual='2fd4e1c67a2d28fced849ee1bb76e7391b93eb12'
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withInvalidSha1InShaField_ShouldReturnEmptyWithoutQuery() throws SQLException {
        String invalidSha1 = "invalid";
        Query query = new Query(null, null, null, invalidSha1);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertTrue(result.isEmpty(), "Invalid SHA should return empty results");
        assertNull(testExecutor.getLastExecutedQuery(), "No query should be executed for invalid SHA");
    }

    @Test
    void withTooShortSha1InShaField_ShouldReturnEmptyWithoutQuery() throws SQLException {
        String tooShortSha = "abc123";
        Query query = new Query("search text", null, null, tooShortSha);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertTrue(result.isEmpty(), "Invalid SHA should return empty results");
        assertNull(testExecutor.getLastExecutedQuery(), "No query should be executed for invalid SHA");
    }

    @Test
    void withInvalidShaAndFilter_ShouldReturnEmptyWithoutQuery() throws SQLException {
        final String invalidSha = "invalid";
        final Query query = new Query("search", "maven", null, invalidSha);
        final Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null, null, null);

        final List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertTrue(result.isEmpty(), "Invalid SHA should return empty results regardless of filters");
        assertNull(testExecutor.getLastExecutedQuery(), "No query should be executed for invalid SHA");
    }

    @Test
    void withEmptySha1InShaField_ShouldNotAddShaCondition() throws SQLException {
        Query query = new Query("search", "maven", null, "");
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, filters, limit, state, false, mockJdbcHelper,
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
                    and n.node_type = 1
                    and n.modified >= 1640995000000
                    and (n.node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and type IN ('local')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withEmptyRepoAndPathAndTextAndSha_ShouldNotAddConditions() throws SQLException {
        Query query = new Query("", "", "", "");
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertNull(testExecutor.getLastExecutedQuery());
    }

    @Test
    void withNullRepoAndPathAndTextAndSha_ShouldNotAddConditions() throws SQLException {
        Query query = new Query(null, null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertNull(testExecutor.getLastExecutedQuery());
    }

    @Test
    void withWhitespaceInTerms_ShouldTrimAndSplit() throws SQLException {
        Query query = new Query("  maven   npm  ", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, failingExecutor));

        assertEquals(expectedException, exception);
    }

    @Test
    void withZeroLimit_ShouldPassZeroToQuery() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 0;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (repo='maven')
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 2147483647;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withAllFields_ShouldGenerateCompleteCondition() throws SQLException {
        String validSha1 = "356a192b7913b04c54574d18c28d46e6395428ab";
        Query query = new Query("artifact", "maven", "com/example", validSha1);
        int limit = 5;
        State state = new State(Type.FOLDER, "123", 1640995200000L);

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (repo='maven')
                    and sha1_actual='356a192b7913b04c54574d18c28d46e6395428ab'
                    and (node_path = 'com/example' or node_path like 'com/example/%')
                    and (node_ts_vector @@ to_tsquery('simple', 'artifact:* & artifact:*'))
                    and (modified, node_id) < (1640995200000, 123)
                order by modified desc, node_id desc
                limit 5;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPathAndText_ShouldUseChildrenPathsCondition() throws SQLException {
        Query query = new Query("artifact", null, "com/example", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (node_path = 'com/example' or node_path like 'com/example/%')
                    and (node_ts_vector @@ to_tsquery('simple', 'artifact:* & artifact:*'))
                order by modified desc, node_id desc
                limit 10;
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
                SyntaxSearchQueryUtils.queryFiles(query, filters, limit, state, false, mockJdbcHelper,
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
                    and n.node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, filters, limit, state, false, mockJdbcHelper,
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
                    and n.node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, filters, limit, state, false, mockJdbcHelper,
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
                    and n.node_type = 1
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
                SyntaxSearchQueryUtils.queryFiles(query, filters, limit, state, false, mockJdbcHelper,
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
                    and n.node_type = 1
                    and (n.node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                    and type IN ('remote')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPathPattern_ShouldAddIlikeForSpecialCharacters() throws SQLException {
        Query query = new Query("jfrog/artifactory-messi", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (node_ts_vector @@ to_tsquery('simple', 'jfrog & artifactory & messi:*')
                    and repo || '/' || node_path || '/' || node_name ilike '%jfrog/artifactory-messi%') 
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withParenthesesAndHyphen_ShouldAddIlikeForSpecialWord() throws SQLException {
        Query query = new Query("jfrog(artifactory)-messi ronaldo", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%jfrog(artifactory)-messi%'"),
                "Should have ilike for word with special characters");
        assertFalse(lastQuery.contains("ilike '%ronaldo%'"),
                "Should NOT have ilike for plain word without special characters");
    }

    @Test
    void withParenthesesInFilename_ShouldAddIlike() throws SQLException {
        Query query = new Query("image (1).png", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (node_ts_vector @@ to_tsquery('simple', 'image & 1 & .png:*')
                    and repo || '/' || node_path || '/' || node_name ilike '%(1).png%')
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPlainWords_ShouldNotAddIlike() throws SQLException {
        Query query = new Query("pom snapshot", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertFalse(lastQuery.contains("ilike"),
                "Should NOT have ilike for plain words without special characters");
    }

    @Test
    void withAsterisk_ShouldAddIlike() throws SQLException {
        Query query = new Query("pom*snapshot", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%pom%snapshot%'"),
                "Should have ilike for word with asterisk");
    }

    @Test
    void withPathAndFilename_ShouldAddIlikeForPathOnly() throws SQLException {
        Query query = new Query("jfrog/artifactory manifest.json", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%jfrog/artifactory%'"),
                "Should have ilike for path with slash");
        assertTrue(lastQuery.contains("to_tsquery"),
                "Should use FTS for filename (dots don't trigger ilike)");
    }

    @Test
    void withHyphenatedWord_ShouldAddIlike() throws SQLException {
        Query query = new Query("docker-release", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%docker-release%'"),
                "Should have ilike for hyphenated word");
    }

    @Test
    void withVersionPattern_ShouldAddIlike() throws SQLException {
        Query query = new Query("jfrog-artifactory-api 2.3.0", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%jfrog-artifactory-api%'"),
                "Should have ilike for hyphenated API name");
        assertTrue(lastQuery.contains("to_tsquery"),
                "Should have FTS for version numbers (dots are FTS separators, not special chars)");
    }

    @Test
    void withDotsOnly_ShouldUseFts() throws SQLException {
        Query query = new Query("1.0.0", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("to_tsquery"),
                "Should use FTS for version string");
        assertTrue(lastQuery.contains("1.0.0:*"),
                "Should keep dots in FTS query (not in SEPARATOR_CHARACTERS)");
        assertTrue(lastQuery.contains("ilike"),
                "Should have ilike (dots trigger special char logic)");
    }

    @Test
    void withFileExtension_ShouldUseFts() throws SQLException {
        Query query = new Query(".jar", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("to_tsquery"),
                "Should use FTS for file extension");
        assertTrue(lastQuery.contains(".jar:*"),
                "Should keep dot in FTS query (not in SEPARATOR_CHARACTERS)");
        assertTrue(lastQuery.contains("ilike"),
                "Should have ilike (dots trigger special char logic)");
    }

    @Test
    void withUnderscoreInText_ShouldEscapeForIlike() throws SQLException {
        Query query = new Query("test_file", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%test^_file%' ESCAPE '^'"),
                "Should escape underscore with ^ and add ESCAPE clause");
    }

    @Test
    void withPercentInText_ShouldEscapeForIlike() throws SQLException {
        Query query = new Query("50%", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%50^%%' ESCAPE '^'"),
                "Should escape percent with ^ and add ESCAPE clause");
    }

    @Test
    void withMultipleWildcardsInText_ShouldEscapeAll() throws SQLException {
        Query query = new Query("test_50%_file", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%test^_50^%^_file%' ESCAPE '^'"),
                "Should escape all wildcards with ^ and add ESCAPE clause");
    }

    @Test
    void withSpecialCharsButNoWildcards_ShouldNotAddEscape() throws SQLException {
        Query query = new Query("my/path", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
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
    void withMixedWords_ShouldAddEscapeOnlyWhenNeeded() throws SQLException {
        Query query = new Query("my/path test_file normal", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        assertTrue(lastQuery.contains("ilike '%my/path%'"),
                "Should have ilike for my/path");
        assertFalse(lastQuery.contains("ilike '%my/path%' ESCAPE"),
                "Should not add ESCAPE for my/path (no wildcards)");
        assertTrue(lastQuery.contains("ilike '%test^_file%' ESCAPE '^'"),
                "Should add ESCAPE for test_file (has underscore)");
        assertFalse(lastQuery.contains("ilike '%normal%'"),
                "Should not have ilike for normal word (no special chars)");
    }

    @Test
    void withSpecialCharsInText_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("jfrog/artifactory-pro", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify FTS condition exists
        assertTrue(normalized.contains("node_ts_vector @@ to_tsquery"),
                "Should have FTS condition");

        // Verify ilike condition exists
        assertTrue(normalized.contains("ilike '%jfrog/artifactory-pro%'"),
                "Should have ilike condition");

        // Verify they are combined with AND (not OR)
        assertTrue(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+and\\s+.*ilike.*"),
                "Should use AND operator between FTS and ilike, not OR");

        assertFalse(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+or\\s+.*ilike.*"),
                "Should NOT use OR operator between FTS and ilike");
    }

    @Test
    void withUnderscoreInText_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("test_file", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify both conditions exist
        assertTrue(normalized.contains("to_tsquery('simple', 'test & file:*')"),
                "Should have FTS condition");
        assertTrue(normalized.contains("ilike '%test^_file%' ESCAPE '^'"),
                "Should have ilike condition with escaped underscore");

        // Verify AND operator
        assertTrue(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+and\\s+.*ilike.*ESCAPE.*"),
                "Should use AND operator between FTS and escaped ilike");
    }

    @Test
    void withMultipleSpecialChars_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("docker/release-2.0", null, null, null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify FTS searches for the tokens
        assertTrue(normalized.contains("to_tsquery"),
                "Should have FTS condition");

        // Verify ilike conditions for special chars
        assertTrue(normalized.contains("ilike '%docker/release-2.0%'"),
                "Should have ilike condition for the full path");

        // Verify AND between FTS and ilike
        assertTrue(normalized.matches(".*to_tsquery\\([^)]+\\)\\s+and\\s+.*ilike.*"),
                "Must use AND to ensure both FTS and literal match succeed");
    }

    /**
     * Normalizes SQL queries by removing extra spaces to make comparison not related to spaces
     */
    private String normalizeSql(String sql) {
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
    void sqlInjectionViaText() throws SQLException {
        Query query = new Query(INJECTION_PAYLOAD, null, null, null);
        int limit = 10;
        State state = null;

        SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state,
                false, mockJdbcHelper,
                INFINITE_DEADLINE, testExecutor);

        PreparedQuery lastQuery = testExecutor.getLastPreparedQuery();
        assertFalse(lastQuery.getTemplate().contains(INJECTION_PAYLOAD),
                "SQL template must not contain user input (injection risk)");
    }

    @Test
    void sqlInjectionViaSha() throws SQLException {
        Query query = new Query(null, null, null, INJECTION_PAYLOAD);
        int limit = 10;
        State state = null;

        SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state,
                false, mockJdbcHelper,
                INFINITE_DEADLINE, testExecutor);

        PreparedQuery lastQuery = testExecutor.getLastPreparedQuery();
        assertNull(lastQuery,
                "No request due to invalid SHA");
    }

    @Test
    void withZeroFromDateShouldNotIncludeDateFilter() throws SQLException {
        Query query = new Query("test", "repo", "path", null);
        Filters filters = new Filters(null, null, null, new DateRange(0L, 1735732800000L), null);

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertFalse(executedSql.contains("modified >="));
        assertFalse(executedSql.contains("modified <="));
    }

    @Test
    void withFolderPathForFiles_ShouldIncludeFtsAndIlike() throws SQLException {
        Query query = new Query(null, null, "docker/prod", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and node_ts_vector @@ to_tsquery('simple', 'docker & prod:*')
                    and repo || '/' || node_path || '/' || node_name ilike '%docker/prod%'
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withFolderPathWithTrailingSlashForFiles_ShouldPreserveInIlike() throws SQLException {
        Query query = new Query(null, null, "docker/prod/", null);
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String lastQuery = testExecutor.getLastExecutedQuery();

        assertTrue(lastQuery.contains("to_tsquery('simple', 'docker & prod:*')"),
                "Should use stripped path for FTS");
        assertTrue(lastQuery.contains("ilike '%docker/prod/%'"),
                "Should preserve trailing slash in ilike for files");
    }

    @Test
    void withPermittedRepos_shouldAddInCondition_whenPermittedReposHasTwoItems() throws SQLException {
        Query query = new Query("search", null, null, null, false, List.of("maven-local", "npm-local"));
        int limit = 10;
        State state = null;

        List<Node> result =
                SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testNodes, result);

        String expectedSql = """
                select node_id, repo, node_path, node_name, bin_length, modified, created from nodes_view
                where
                    repo <> 'auto-trashcan'
                    and node_type = 1
                    and (repo IN ('maven-local', 'npm-local'))
                    and (node_ts_vector @@ to_tsquery('simple', 'search:* & search:*'))
                order by modified desc, node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    public static String bind(String template, List<Object> params) {
        StringBuilder result = new StringBuilder();
        int paramIndex = 0;

        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);

            if (c == '?') {
                if (paramIndex >= params.size()) {
                    throw new IllegalArgumentException("Not enough parameters provided for the template.");
                }

                Object value = params.get(paramIndex++);

                if (value instanceof String s) {
                    result.append("'").append(s).append("'");
                } else {
                    result.append(value);
                }
            } else {
                result.append(c);
            }
        }

        if (paramIndex < params.size()) {
            throw new IllegalArgumentException("Too many parameters provided for the template.");
        }

        return result.append(";").toString();
    }

    @Test
    void fuzzyMatchShouldEscapeUnderscore() throws SQLException {
        Query query = new Query("test_file.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ilike '%test^_file.txt%' ESCAPE '^'"),
                "Underscore should be escaped as ^_ in fuzzy match");
    }

    @Test
    void fuzzyMatchShouldConvertAsteriskToWildcard() throws SQLException {
        Query query = new Query("test*.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ilike '%test%.txt%' ESCAPE '^'"),
                "Asterisk should be converted to % (wildcard) in fuzzy match");
    }

    @Test
    void fuzzyMatchShouldEscapeCaret() throws SQLException {
        Query query = new Query("test^file.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ilike '%test^^file.txt%' ESCAPE '^'"),
                "Caret should be escaped as ^^ in fuzzy match");
    }

    @Test
    void fuzzyMatchShouldEscapeMultipleSpecialCharsAndConvertAsterisk() throws SQLException {
        Query query = new Query("test_file%with*special^chars.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ilike '%test^_file^%with%special^^chars.txt%' ESCAPE '^'"),
                "Special chars should be escaped and * converted to % in fuzzy match");
    }

    @ParameterizedTest
    @MethodSource("escapeForIlikeTestCases")
    void escapeForIlikeShouldHandleAllCases(String input, boolean isExactMatch, String expectedText,
            String expectedEscapeClause) {
        SyntaxSearchQueryUtils.EscapedText result = SyntaxSearchQueryUtils.escapeForIlike(input, isExactMatch);

        assertNotNull(result, "Result should never be null");
        assertEquals(expectedText, result.text(),
                "Input: '" + input + "', isExactMatch=" + isExactMatch);
        assertEquals(expectedEscapeClause, result.escapeClause(),
                "Escape clause mismatch for: '" + input + "', isExactMatch=" + isExactMatch);
    }

    @Test
    void queryFiles_shouldUseFullPathIlike_whenExactMatchTextOnly() throws SQLException {
        Query query = new Query("my-app", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("full_path ILIKE 'my-app' OR full_path ILIKE '%/my-app'"),
                "Text-only exact match should use ILIKE with OR pattern for path segment matching");
        assertFalse(sql.contains("full_path ILIKE '%my-app%'"),
                "Should NOT have suffix wildcard in exact match");
        assertTrue(sql.contains("node_ts_vector @@ to_tsquery('simple', 'my & app')"),
                "Should have FTS without :* wildcard");
        assertFalse(sql.contains("my & app:*"),
                "FTS should not have :* in exact match mode");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "null, example-repo-local, org/jfrog",
            "'', example-repo-local, org/jfrog",
            "null, docker-local, a/b/c",
            "'', docker-local, a/b/c"
    }, nullValues = "null")
    void queryFiles_shouldSkipFilesQuery_whenExactMatchFolderOnly(String text, String repo, String folder)
            throws SQLException {
        Query query = new Query(text, repo, folder, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql == null || sql.isEmpty(),
                "Folder-only exact match should skip files query (setNoNeedToQuery)");
    }

    @Test
    void queryFiles_shouldQueryFiles_whenExactMatchFolderOnlyButFuzzyMode() throws SQLException {
        Query query = new Query(null, "example-repo-local", "org/jfrog", null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertNotNull(sql, "Fuzzy mode should query files even with folder-only");
        assertFalse(sql.isEmpty(), "Fuzzy mode should generate SQL");
        assertTrue(sql.contains("ilike '%org/jfrog%'"),
                "Fuzzy folder-only should use ILIKE for paths with special chars like slashes");
    }

    private static Stream<Arguments> escapeForIlikeTestCases() {
        return Stream.of(
                // Null and empty inputs
                Arguments.of(null, false, "", ""),
                Arguments.of(null, true, "", ""),
                Arguments.of("", false, "", ""),
                Arguments.of("", true, "", ""),

                // Text without special chars - no escaping needed
                Arguments.of("simple", false, "simple", ""),
                Arguments.of("simple", true, "simple", ""),
                Arguments.of("simple.txt", false, "simple.txt", ""),
                Arguments.of("simple.txt", true, "simple.txt", ""),
                Arguments.of("myfile123", false, "myfile123", ""),
                Arguments.of("myfile123", true, "myfile123", ""),

                // Underscore _ (ILIKE wildcard) - must escape in both modes
                Arguments.of("test_file.txt", false, "test^_file.txt", " ESCAPE '^'"),
                Arguments.of("test_file.txt", true, "test^_file.txt", " ESCAPE '^'"),
                Arguments.of("my_app_v1", false, "my^_app^_v1", " ESCAPE '^'"),
                Arguments.of("my_app_v1", true, "my^_app^_v1", " ESCAPE '^'"),

                // Percent % (ILIKE wildcard) - must escape in both modes
                Arguments.of("file%20name.txt", false, "file^%20name.txt", " ESCAPE '^'"),
                Arguments.of("file%20name.txt", true, "file^%20name.txt", " ESCAPE '^'"),
                Arguments.of("100%", false, "100^%", " ESCAPE '^'"),
                Arguments.of("100%", true, "100^%", " ESCAPE '^'"),

                // Caret ^ (escape char itself) - must escape in both modes
                Arguments.of("test^file.txt", false, "test^^file.txt", " ESCAPE '^'"),
                Arguments.of("test^file.txt", true, "test^^file.txt", " ESCAPE '^'"),
                Arguments.of("a^b^c", false, "a^^b^^c", " ESCAPE '^'"),
                Arguments.of("a^b^c", true, "a^^b^^c", " ESCAPE '^'"),

                // Asterisk * - DIFFERENT behavior: fuzzy converts to %, exact escapes as literal
                Arguments.of("test*.txt", false, "test%.txt", " ESCAPE '^'"),
                Arguments.of("test*.txt", true, "test^*.txt", " ESCAPE '^'"),
                Arguments.of("*.jar", false, "%.jar", " ESCAPE '^'"),
                Arguments.of("*.jar", true, "^*.jar", " ESCAPE '^'"),
                Arguments.of("file*v*1", false, "file%v%1", " ESCAPE '^'"),
                Arguments.of("file*v*1", true, "file^*v^*1", " ESCAPE '^'"),

                // Multiple special chars - complex escaping
                Arguments.of("test_file%with*special^chars.txt", false, "test^_file^%with%special^^chars.txt",
                        " ESCAPE '^'"),
                Arguments.of("test_file%with*special^chars.txt", true, "test^_file^%with^*special^^chars.txt",
                        " ESCAPE '^'"),
                Arguments.of("my_app-v1.0*.jar", false, "my^_app-v1.0%.jar", " ESCAPE '^'"),
                Arguments.of("my_app-v1.0*.jar", true, "my^_app-v1.0^*.jar", " ESCAPE '^'"),
                Arguments.of("100%_done^now*", false, "100^%^_done^^now%", " ESCAPE '^'"),
                Arguments.of("100%_done^now*", true, "100^%^_done^^now^*", " ESCAPE '^'")
        );
    }
}
