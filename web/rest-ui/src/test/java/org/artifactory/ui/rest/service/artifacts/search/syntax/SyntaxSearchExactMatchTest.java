package org.artifactory.ui.rest.service.artifacts.search.syntax;

import lombok.Getter;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryFilesTest.bind;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxSearchExactMatchTest {

    private static final Deadline INFINITE_DEADLINE = Deadline.startingNow(Integer.MAX_VALUE);

    @Mock
    private JdbcHelper mockJdbcHelper;

    private List<Node> testNodes;
    private TestRequestExecutor testExecutor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testNodes = new ArrayList<>();
        testNodes.add(new Node(1L, "example-repo-local", "a/b", "my-app.jar", 1024L,
                1640995200000L, 0L));
        testNodes.add(new Node(2L, "example-repo-local", ".", "test.txt", 512L,
                1640995100000L, 0L));
        testNodes.add(new Node(3L, "pypi-local", "packages", "my_app-1.0.tar.gz", 2048L,
                1640995000000L, 0L));

        testExecutor = new TestRequestExecutor(testNodes);
    }

    @Test
    void exactMatchSimpleTextShouldUsePrefixWildcardOnly() throws SQLException {
        Query query = new Query("my-app", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("node_ts_vector @@ to_tsquery('simple', 'my & app')"),
                "FTS should not have :* prefix wildcard in exact match");
        assertTrue(sql.contains("full_path ILIKE 'my-app' OR full_path ILIKE '%/my-app'"),
                "ILIKE should match exact full path OR path segment after /");
        assertFalse(sql.contains("full_path ILIKE '%my-app%'"),
                "ILIKE should NOT have suffix wildcard in exact match");
    }

    @Test
    void exactMatchTextWithSlashShouldPreserveSlash() throws SQLException {
        Query query = new Query("a/b", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("node_ts_vector @@ to_tsquery('simple', 'a & b')"),
                "FTS should split on slash but no :* wildcard");
        assertTrue(sql.contains("full_path ILIKE 'a/b' OR full_path ILIKE '%/a/b'"),
                "ILIKE should match exact full path OR path segment after /");
    }

    @Test
    void exactMatchTextWithSpaceShouldTreatSpaceAsSticky() throws SQLException {
        Query query = new Query("my app", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("node_ts_vector @@ to_tsquery('simple', 'my & app')"),
                "FTS should split tokens but no :* wildcard");
        assertTrue(sql.contains("full_path ILIKE 'my app' OR full_path ILIKE '%/my app'"),
                "ILIKE should match exact full path OR path segment after /");
    }

    @Test
    void exactMatchTextWithParenthesesShouldEscapeSpecialChars() throws SQLException {
        Query query = new Query("Image (4).png", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("full_path ILIKE 'Image (4).png' OR full_path ILIKE '%/Image (4).png'"),
                "ILIKE should match exact full path OR path segment after /");
    }

    @Test
    void exactMatchTextWithWildcardShouldEscapeWildcard() throws SQLException {
        Query query = new Query("test*", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ESCAPE '^'") && sql.contains("ILIKE 'test^*'") &&
                        sql.contains("ILIKE '%/test^*'"),
                "Should escape wildcard characters with ESCAPE clause and use OR pattern");
    }

    @Test
    void exactMatchTextWithPercentShouldEscapePercent() throws SQLException {
        Query query = new Query("test%file", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ESCAPE '^'") && sql.contains("ILIKE 'test^%file'") &&
                        sql.contains("ILIKE '%/test^%file'"),
                "Should escape SQL wildcard % with ESCAPE clause and use OR pattern");
    }

    @Test
    void exactMatchRepositoryWithTextFieldShouldUseExactEquality() throws SQLException {
        Query query = new Query("docker-local", null, null, null);

        List<Repository> testRepos = new ArrayList<>();
        TestRepositoryExecutor repoExecutor = new TestRepositoryExecutor(testRepos);

        List<Repository> result = SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null),
                100, null, true, mockJdbcHelper, INFINITE_DEADLINE, repoExecutor);

        assertNotNull(result);
        String sql = repoExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("repository_key = 'docker-local'"),
                "Should use simple repository_key equality");
        assertFalse(sql.contains("repo_ts_vector"),
                "Should NOT use FTS in exact match mode");
        assertFalse(sql.contains("ilike") || sql.contains("ILIKE"),
                "Should NOT use ILIKE in exact match mode");
    }

    @Test
    void exactMatchRepositoryWithRepoFieldShouldUseExactEquality() throws SQLException {
        Query query = new Query(null, "npm-local", null, null);

        List<Repository> testRepos = new ArrayList<>();
        TestRepositoryExecutor repoExecutor = new TestRepositoryExecutor(testRepos);

        List<Repository> result = SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null),
                100, null, true, mockJdbcHelper, INFINITE_DEADLINE, repoExecutor);

        assertNotNull(result);
        String sql = repoExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("repository_key = 'npm-local'"),
                "Should use simple repository_key equality");
        assertFalse(sql.contains("repo_ts_vector"),
                "Should NOT use FTS in exact match mode");
        assertFalse(sql.contains("ilike") || sql.contains("ILIKE"),
                "Should NOT use ILIKE in exact match mode");
    }

    @Test
    void exactMatchRepositoryWithCacheSuffixShouldStripCache() throws SQLException {
        Query query = new Query(null, "npm-cache", null, null);

        List<Repository> testRepos = new ArrayList<>();
        TestRepositoryExecutor repoExecutor = new TestRepositoryExecutor(testRepos);

        List<Repository> result = SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null),
                100, null, true, mockJdbcHelper, INFINITE_DEADLINE, repoExecutor);

        assertNotNull(result);
        String sql = repoExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("repository_key = 'npm'"),
                "Should strip -cache suffix and search for base repository");
        assertFalse(sql.contains("npm-cache"),
                "Should NOT search for npm-cache when -cache is stripped");
    }

    @Test
    void exactMatchRepositoryTextFieldWithCacheSuffixShouldStripCache() throws SQLException {
        Query query = new Query("docker-cache", null, null, null);

        List<Repository> testRepos = new ArrayList<>();
        TestRepositoryExecutor repoExecutor = new TestRepositoryExecutor(testRepos);

        List<Repository> result = SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null),
                100, null, true, mockJdbcHelper, INFINITE_DEADLINE, repoExecutor);

        assertNotNull(result);
        String sql = repoExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("repository_key = 'docker'"),
                "Should strip -cache suffix and search for base repository");
        assertFalse(sql.contains("docker-cache"),
                "Should NOT search for docker-cache when -cache is stripped");
    }

    @Test
    void exactMatchFolderOnlyShouldReturnFolderNotChildren() throws SQLException {
        Query query = new Query("", "example-repo-local", "a/b", null);

        List<Node> result = SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("full_path ILIKE 'a/b' OR full_path ILIKE '%/a/b'"),
                "ILIKE should match exact full path OR path segment after /");
        assertFalse(sql.contains("node_path like 'a/b%'"),
                "Should NOT search children in exact match mode");
    }

    @Test
    void exactMatchFolderPathShouldUsePrefixWildcard() throws SQLException {
        Query query = new Query("org/jfrog", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("full_path ILIKE 'org/jfrog' OR full_path ILIKE '%/org/jfrog"),
                "ILIKE should match exact full path OR path segment after /");
        assertFalse(sql.contains("full_path ILIKE '%/org/jfrog%'"),
                "Should NOT have suffix wildcard - no recursion");
    }

    @Test
    void exactMatchRepoAndPathShouldUseAndLogic() throws SQLException {
        Query query = new Query("a", "repoA", null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("repo='repoA'") || sql.contains("repo like 'repoA'"),
                "Should filter by repo");
        assertTrue(sql.contains("to_tsquery('simple', 'a & a')"),
                "Should have FTS for text without :* wildcard");
        assertTrue(sql.contains("full_path ILIKE 'a' OR full_path ILIKE '%/a'"),
                "Should have ILIKE with OR pattern for single character");
    }

    @Test
    void exactMatchRepoAndPathAndTextShouldUseExactNodeConditions() throws SQLException {
        Query query = new Query("amir.txt", "repoA", "a/b", null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("repo='repoA'") || sql.contains("repo like 'repoA'"),
                "Should use exact repo equality");
        assertTrue(sql.contains("node_path = 'a/b'"),
                "Should use exact node_path equality");
        assertTrue(sql.contains("node_name ILIKE 'amir.txt'"),
                "Should use ILIKE for node_name (case insensitive matching)");
    }

    @Test
    void exactMatchRepoAndPathWithSlashShouldPreserveSlash() throws SQLException {
        Query query = new Query("/org/jfrog", "example-repo-local", null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("repo") && sql.contains("example-repo-local"),
                "Should filter by repo");
        assertTrue(sql.contains("to_tsquery('simple', 'org & jfrog')"),
                "FTS should split on slash without wildcards");
        assertTrue(sql.contains("full_path ILIKE '%/org/jfrog'"),
                "ILIKE should use %/ prefix since text contains slash");
    }

    @Test
    void exactMatchSingleCharacterShouldWork() throws SQLException {
        Query query = new Query("a", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("to_tsquery('simple', 'a & a')"),
                "Single token should be duplicated in exact match FTS");
        assertTrue(sql.contains("full_path ILIKE 'a' OR full_path ILIKE '%/a'"),
                "Should have ILIKE with OR pattern for single character");
    }

    @Test
    void exactMatchUnderscoreShouldTreatAsLiteral() throws SQLException {
        Query query = new Query("my_app", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ILIKE 'my^_app'") && sql.contains("ILIKE '%/my^_app'") && sql.contains("ESCAPE '^'"),
                "Underscore should be escaped and use OR pattern");
    }

    @Test
    void exactMatchMultipleHyphensShouldPreserveAll() throws SQLException {
        Query query = new Query("my-app-v1", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("to_tsquery('simple', 'my & app & v1')"),
                "FTS should split on hyphens");
        assertTrue(sql.contains("full_path ILIKE 'my-app-v1'") && sql.contains("full_path ILIKE '%/my-app-v1'"),
                "ILIKE should preserve all hyphens and use OR pattern");
    }

    @Test
    void exactMatchEmptyTextWithPathShouldOnlySearchFolder() throws SQLException {
        Query query = new Query("", "example-repo-local", "a/b", null);

        List<Node> result = SyntaxSearchQueryUtils.queryPaths(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("full_path ILIKE '%/a/b'"),
                "Empty text with path should search folder with %/ prefix");
    }

    @Test
    void exactMatchShouldEscapeUnderscore() throws SQLException {
        Query query = new Query("test_file.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ILIKE 'test^_file.txt' ESCAPE '^'") &&
                        sql.contains("ILIKE '%/test^_file.txt' ESCAPE '^'"),
                "Underscore should be escaped as ^_ in exact match with OR pattern");
    }

    @Test
    void exactMatchShouldEscapePercent() throws SQLException {
        Query query = new Query("test%file.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ILIKE 'test^%file.txt' ESCAPE '^'") &&
                        sql.contains("ILIKE '%/test^%file.txt' ESCAPE '^'"),
                "Percent should be escaped as ^% in exact match with OR pattern");
    }

    @Test
    void exactMatchShouldEscapeAsteriskAsLiteral() throws SQLException {
        Query query = new Query("test*.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ILIKE 'test^*.txt' ESCAPE '^'") &&
                        sql.contains("ILIKE '%/test^*.txt' ESCAPE '^'"),
                "Asterisk should be escaped as ^* (literal) in exact match with OR pattern");
    }

    @Test
    void exactMatchShouldEscapeCaret() throws SQLException {
        Query query = new Query("test^file.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ILIKE 'test^^file.txt' ESCAPE '^'") &&
                        sql.contains("ILIKE '%/test^^file.txt' ESCAPE '^'"),
                "Caret should be escaped as ^^ in exact match with OR pattern");
    }

    @Test
    void exactMatchShouldEscapeMultipleSpecialChars() throws SQLException {
        Query query = new Query("test_file%with*special^chars.txt", null, null, null);

        List<Node> result = SyntaxSearchQueryUtils.queryFiles(query, Filters.orEmpty(null), 100, null,
                true, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();

        assertTrue(sql.contains("ILIKE 'test^_file^%with^*special^^chars.txt' ESCAPE '^'") &&
                        sql.contains("ILIKE '%/test^_file^%with^*special^^chars.txt' ESCAPE '^'"),
                "All special chars should be properly escaped in exact match with OR pattern");
    }

    @Getter
    private static class TestRequestExecutor implements RequestExecutor<Node> {
        private final List<Node> nodes;
        private String lastExecutedQuery;

        public TestRequestExecutor(List<Node> nodes) {
            this.nodes = nodes;
        }

        @Override
        public List<Node> execute(JdbcHelper jdbcHelper, PreparedQuery query, Deadline deadline) throws SQLException {
            String template = query.getTemplate();
            List<Object> params = query.getParams();
            this.lastExecutedQuery = bind(template, params);
            return nodes;
        }
    }

    @Getter
    private static class TestRepositoryExecutor implements RequestExecutor<Repository> {
        private final List<Repository> repositories;
        private String lastExecutedQuery;

        public TestRepositoryExecutor(List<Repository> repositories) {
            this.repositories = repositories;
        }

        @Override
        public List<Repository> execute(JdbcHelper jdbcHelper, PreparedQuery query, Deadline deadline)
                throws SQLException {
            String template = query.getTemplate();
            List<Object> params = query.getParams();
            this.lastExecutedQuery = bind(template, params);
            return repositories;
        }
    }
}
