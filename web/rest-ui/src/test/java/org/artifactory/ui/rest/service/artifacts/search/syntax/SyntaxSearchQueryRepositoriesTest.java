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

import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryFilesTest.bind;
import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryPathsTest.normalizeSql;
import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryUtilsCreatePathsConditionTest.getString;
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
class SyntaxSearchQueryRepositoriesTest {

    private static final String INJECTION_PAYLOAD = "'; DROP TABLE nodes_view; --";
    private static final Deadline INFINITE_DEADLINE = Deadline.startingNow(Integer.MAX_VALUE);

    @Mock
    private JdbcHelper mockJdbcHelper;

    private List<Repository> testRepositories;
    private TestRequestExecutor testExecutor;

    @BeforeEach
    void setUp() {
        testRepositories = new ArrayList<>();
        testRepositories.add(new Repository("maven-local", "local", "maven",
                1640995200000L, 0L));
        testRepositories.add(new Repository("maven-remote", "remote", "maven",
                1640995100000L, 0L));
        testRepositories.add(new Repository("npm-local", "local", "npm",
                1640995000000L, 0L));
        testRepositories.add(new Repository("docker-federated", "federated", "docker",
                1640994900000L, 0L));

        testExecutor = new TestRequestExecutor(testRepositories);
    }

    @Test
    void withRepoField_ShouldGenerateCorrectCondition() throws SQLException {
        Query query = new Query("search text", "maven", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false,
                        mockJdbcHelper,
                        INFINITE_DEADLINE,
                        testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void splitRepoNameBySeparators() throws SQLException {
        Query query = new Query(null, "dev-master-helm-local", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false,
                        mockJdbcHelper,
                        INFINITE_DEADLINE,
                        testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual') 
                and (repo_ts_vector @@ to_tsquery('simple', 'dev & master & helm & local:*')
                and repository_key ilike '%dev-master-helm-local%') 
                order by modified desc, repository_key desc limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withTextField_ShouldFallbackToText() throws SQLException {
        Query query = new Query("npm", null, null, null);
        int limit = 5;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'npm:* & npm:*'))
                
                order by modified desc, repository_key desc
                limit 5;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withRepoState_ShouldAddStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = new State(Type.REPO, "maven-local", 1640995200000L);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                and (modified, repository_key) < (1640995200000, 'maven-local')
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPathState_ShouldAddNodeStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = new State(Type.FOLDER, "123", 1640995200000L);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                and (modified, node_id) < (1640995200000, 123)
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withFileState_ShouldAddNodeStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = new State(Type.FILE, "456", 1640995200000L);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                and (modified, node_id) < (1640995200000, 456)
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withStopState_ShouldNotAddStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = new State(Type.STOP, "", 0L);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withNullState_ShouldNotAddStateCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withEmptyRepoAndText_ShouldNotAddRepoCondition() throws SQLException {
        Query query = new Query("", "", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                
                
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withNullRepoAndText_ShouldNotAddRepoCondition() throws SQLException {
        Query query = new Query(null, null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                
                
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withMultipleTermsInText_ShouldCreateOrCondition() throws SQLException {
        Query query = new Query("maven npm", null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven & npm:*'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withWhitespaceInTerms_ShouldTrimAndSplit() throws SQLException {
        Query query = new Query("  maven   npm  ", null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven & npm:*'))
                
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void executorThrowsSQLException() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 10;
        State state = null;

        RequestExecutor<Repository> failingExecutor = mock(RequestExecutor.class);
        SQLException expectedException = new SQLException("Database connection failed");
        when(failingExecutor.execute(any(JdbcHelper.class), any(), any(Deadline.class))).thenThrow(expectedException);

        SQLException exception = assertThrows(SQLException.class, () ->
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, failingExecutor));

        assertEquals(expectedException, exception);
    }

    @Test
    void withZeroLimit_ShouldPassZeroToQuery() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = 0;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                order by modified desc, repository_key desc
                limit 0;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withLargeLimit_ShouldPassLargeValueToQuery() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        int limit = Integer.MAX_VALUE;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                order by modified desc, repository_key desc
                limit 2147483647;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withRepositoryTypeFilter_ShouldAddTypeCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                type IN ('local')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withPackageTypeFilter_ShouldAddPackageTypeCondition() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        Filters filters = new Filters(null, null, Set.of(PackageType.MAVEN), null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                and package_type IN ('maven')
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withBothFilters_ShouldAddBothConditions() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), Set.of(PackageType.MAVEN), null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                type IN ('local')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                and package_type IN ('maven')
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withMultipleRepositoryTypes_ShouldAddAllTypes() throws SQLException {
        Query query = new Query("search", "maven", null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL, RepositoryType.REMOTE), null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();
        assertTrue(sql.contains("type IN"), "Should have type IN clause");
        assertTrue(sql.contains("'local'"), "Should contain 'local'");
        assertTrue(sql.contains("'remote'"), "Should contain 'remote'");
        assertFalse(sql.contains("(type = 'local' or type = 'remote')"),
                "Should not have redundant default condition when filter is provided");
        assertTrue(sql.contains("select repository_key, type, package_type, modified, created from repos_view"));
        assertTrue(sql.contains("where"));
        assertTrue(sql.contains("order by modified desc, repository_key desc"));
        assertTrue(sql.contains("limit 10"));
    }

    @Test
    void withMultiplePackageTypes_ShouldAddAllTypes() throws SQLException {
        Query query = new Query("search", null, null, null);
        Filters filters = new Filters(null, null, Set.of(PackageType.MAVEN, PackageType.NPM), null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String sql = testExecutor.getLastExecutedQuery();
        assertTrue(sql.contains("package_type IN"), "Should have package_type IN clause");
        assertTrue(sql.contains("'maven'"), "Should contain 'maven'");
        assertTrue(sql.contains("'npm'"), "Should contain 'npm'");
        assertTrue(sql.contains("and package_type IN"), "Should have 'and' prefix before package_type");
        assertTrue(sql.contains("select repository_key, type, package_type, modified, created from repos_view"));
        assertTrue(sql.contains("where"));
        assertTrue(sql.contains("(type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')"),
                "Should have default type condition with federated");
        assertTrue(sql.contains("order by modified desc, repository_key desc"));
        assertTrue(sql.contains("limit 10"));
    }

    @Test
    void withFederatedRepositoryType_ShouldIncludeFederatedInQuery() throws SQLException {
        // Arrange
        Query query = new Query("docker", null, null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.FEDERATED), null, null, null);
        int limit = 10;
        State state = null;

        // Act
        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        // Assert
        assertNotNull(result);
        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                type IN ('federated')
                and (repo_ts_vector @@ to_tsquery('simple', 'docker:* & docker:*'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void createRepositoryTypeFilter_WithCachedType_ShouldMapToRemote() {
        String result = getString(SyntaxSearchQueryUtils.createRepositoryTypeFilter(Set.of(RepositoryType.CACHED)));

        assertEquals("type IN ('remote')", result);
    }

    @Test
    void createRepositoryTypeFilter_WithMultipleTypesIncludingCached_ShouldMapCachedToRemote() {
        String result = getString(SyntaxSearchQueryUtils.createRepositoryTypeFilter(
                Set.of(RepositoryType.LOCAL, RepositoryType.CACHED, RepositoryType.FEDERATED)
        ));

        assertTrue(result.startsWith("type IN ("));
        assertTrue(result.contains("'local'"));
        assertTrue(result.contains("'remote'"));
        assertTrue(result.contains("'federated'"));
        assertFalse(result.contains("'cached'"));
    }

    @Test
    void createRepositoryTypeFilter_WithRemoteAndCached_ShouldGenerateRemoteOnce() {
        String result = getString(SyntaxSearchQueryUtils.createRepositoryTypeFilter(
                Set.of(RepositoryType.REMOTE, RepositoryType.CACHED)
        ));

        assertEquals("type IN ('remote')", result);
    }

    @Test
    void createRepositoryTypeFilter_WithAllTypes_ShouldIncludeAllUniquely() {
        String result = getString(SyntaxSearchQueryUtils.createRepositoryTypeFilter(
                Set.of(RepositoryType.LOCAL, RepositoryType.REMOTE, RepositoryType.FEDERATED,
                        RepositoryType.CACHED, RepositoryType.VIRTUAL)
        ));

        assertTrue(result.startsWith("type IN ("));
        assertTrue(result.contains("'local'"));
        assertTrue(result.contains("'remote'"));
        assertTrue(result.contains("'federated'"));
        assertTrue(result.contains("'virtual'"));
        assertFalse(result.contains("'cached'"));
    }

    @Test
    void withCachedRepositoryType_ShouldQueryForRemoteType() throws SQLException {
        // Arrange
        Query query = new Query("maven", null, null, null);
        Filters filters = new Filters(null, Set.of(RepositoryType.CACHED), null, null, null);
        int limit = 10;
        State state = null;

        // Act
        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        // Assert
        assertNotNull(result);
        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                type IN ('remote')
                and (repo_ts_vector @@ to_tsquery('simple', 'maven:* & maven:*'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withNullDateRangeShouldNotIncludeDateFilter() throws SQLException {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, null);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertFalse(executedSql.contains("modified >="));
        assertFalse(executedSql.contains("modified <="));
    }

    @Test
    void withEmptyDateRangeShouldNotIncludeDateFilter() throws SQLException {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(null, null), null);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertFalse(executedSql.contains("modified >="));
        assertFalse(executedSql.contains("modified <="));
    }

    @Test
    void withBothFromAndToShouldIncludeBothFilters() throws SQLException {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(1640995200000L, 1735732800000L), null);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertTrue(executedSql.contains("modified >= 1640995200000"));
        assertTrue(executedSql.contains("modified <= 1735732800000"));
    }

    @Test
    void withOnlyFromShouldIncludeOnlyFromFilter() throws SQLException {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(1640995200000L, null), null);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertTrue(executedSql.contains("modified >= 1640995200000"));
        assertFalse(executedSql.contains("modified <="));
    }

    @Test
    void withOnlyToDateShouldNotIncludeDateFilter() throws SQLException {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(null, 1735732800000L), null);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertFalse(executedSql.contains("modified >="));
        assertFalse(executedSql.contains("modified <="));
    }

    @Test
    void withZeroToDateShouldIncludeOnlyFromFilter() throws SQLException {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(1735732800000L, 0L), null);

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertTrue(executedSql.contains("modified >= 1735732800000"));
        assertFalse(executedSql.contains("modified <="));
    }

    @Test
    void withCreatedBothFromAndToShouldIncludeBothFilters() throws SQLException {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, new DateRange(1640995200000L, 1735732800000L));

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertTrue(executedSql.contains("created >= 1640995200000"));
        assertTrue(executedSql.contains("created <= 1735732800000"));
    }

    @Test
    void withCreatedOnlyFromShouldIncludeOnlyFromFilter() throws SQLException {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, new DateRange(1640995200000L, null));

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, filters, 10, null, false, mockJdbcHelper,
                        INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        String executedSql = testExecutor.getLastExecutedQuery();
        assertTrue(executedSql.contains("created >= 1640995200000"));
        assertFalse(executedSql.contains("created <="));
    }

    @Test
    void withCachePrefixInRepo_ShouldAddExactRepoMatch() throws SQLException {
        Query query = new Query(null, "npm-ca", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false,
                        mockJdbcHelper,
                        INFINITE_DEADLINE,
                        testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and ((repo_ts_vector @@ to_tsquery('simple', 'npm & ca:*')
                and repository_key ilike '%npm-ca%') or (repository_key = 'npm'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withCachePrefixInText_ShouldAddExactRepoMatch() throws SQLException {
        Query query = new Query("npm-ca", null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false,
                        mockJdbcHelper,
                        INFINITE_DEADLINE,
                        testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and ((repo_ts_vector @@ to_tsquery('simple', 'npm & ca:*')
                and repository_key ilike '%npm-ca%')
                or (repository_key = 'npm'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withMultipleCachePrefixTerms_ShouldAddMultipleExactMatches() throws SQLException {
        Query query = new Query(null, "npm-ca docker-c", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false,
                        mockJdbcHelper,
                        INFINITE_DEADLINE,
                        testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and ((repo_ts_vector @@ to_tsquery('simple', 'npm & ca & docker & c:*')
                and repository_key ilike '%npm-ca%' and repository_key ilike '%docker-c%') 
                or (repository_key = 'npm' or repository_key = 'docker'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withFullCacheWord_ShouldAddExactRepoMatch() throws SQLException {
        Query query = new Query(null, "npm-cache", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false,
                        mockJdbcHelper,
                        INFINITE_DEADLINE,
                        testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and ((repo_ts_vector @@ to_tsquery('simple', 'npm & cache:*')
                and repository_key ilike '%npm-cache%') or (repository_key = 'npm'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
        assertTrue(testExecutor.getLastExecutedQuery().contains("repository_key = 'npm'"));
    }

    @Test
    void withCaseInsensitiveCachePrefix_ShouldAddExactRepoMatch() throws SQLException {
        Query query = new Query(null, "NPM-CA", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String expectedSql = """
                select repository_key, type, package_type, modified, created from repos_view
                where
                (type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual')
                and ((repo_ts_vector @@ to_tsquery('simple', 'NPM & CA:*')
                and repository_key ilike '%NPM-CA%') or (repository_key = 'NPM'))
                order by modified desc, repository_key desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(testExecutor.getLastExecutedQuery()));
    }

    @Test
    void withSpecialCharsInText_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("art-", null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify FTS condition exists
        assertTrue(normalized.contains("repo_ts_vector @@ to_tsquery"),
                "Should have FTS condition");

        // Verify ilike condition exists
        assertTrue(normalized.contains("repository_key ilike '%art-%'"),
                "Should have ilike condition");

        // Verify they are combined with AND (not OR)
        assertTrue(normalized.matches(".*to_tsquery.*\\)\\s+and\\s+.*repository_key\\s+ilike.*"),
                "Should use AND operator between FTS and ilike, not OR");

        assertFalse(normalized.matches(".*to_tsquery.*\\)\\s+or\\s+.*repository_key\\s+ilike.*"),
                "Should NOT use OR operator between FTS and ilike");
    }

    @Test
    void withSlashInText_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("jfrog/npm", null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify both conditions exist
        assertTrue(normalized.contains("to_tsquery('simple', 'jfrog & npm:*')"),
                "Should have FTS condition");
        assertTrue(normalized.contains("repository_key ilike '%jfrog/npm%'"),
                "Should have ilike condition for slash");

        // Verify AND operator
        assertTrue(normalized.matches(".*to_tsquery.*\\)\\s+and\\s+.*repository_key\\s+ilike.*"),
                "Should use AND operator between FTS and ilike");
    }

    @Test
    void withUnderscoreInText_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query("test_repo", null, null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify FTS and escaped ilike
        assertTrue(normalized.contains("to_tsquery('simple', 'test & repo:*')"),
                "Should have FTS condition");
        assertTrue(normalized.contains("repository_key ilike '%test^_repo%' ESCAPE '^'"),
                "Should have escaped ilike condition");

        // Verify AND operator
        assertTrue(normalized.matches(".*to_tsquery.*\\)\\s+and\\s+.*repository_key\\s+ilike.*ESCAPE.*"),
                "Should use AND operator between FTS and escaped ilike");
    }

    @Test
    void withSpecialCharsInRepoParam_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query(null, "art-", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify FTS condition exists
        assertTrue(normalized.contains("repo_ts_vector @@ to_tsquery"),
                "Should have FTS condition");

        // Verify ilike condition exists
        assertTrue(normalized.contains("repository_key ilike '%art-%'"),
                "Should have ilike condition");

        // Verify they are combined with AND (not OR)
        assertTrue(normalized.matches(".*to_tsquery.*\\)\\s+and\\s+.*repository_key\\s+ilike.*"),
                "Should use AND operator between FTS and ilike for repo param, not OR");

        assertFalse(normalized.matches(".*to_tsquery.*\\)\\s+or\\s+.*repository_key\\s+ilike.*"),
                "Should NOT use OR operator between FTS and ilike for repo param");
    }

    @Test
    void withSlashInRepoParam_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query(null, "jfrog/npm", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify both conditions exist
        assertTrue(normalized.contains("to_tsquery('simple', 'jfrog & npm:*')"),
                "Should have FTS condition");
        assertTrue(normalized.contains("repository_key ilike '%jfrog/npm%'"),
                "Should have ilike condition for slash in repo param");

        // Verify AND operator
        assertTrue(normalized.matches(".*to_tsquery.*\\)\\s+and\\s+.*repository_key\\s+ilike.*"),
                "Should use AND operator between FTS and ilike for repo param");
    }

    @Test
    void withUnderscoreInRepoParam_ShouldCombineFtsAndIlikeWithAnd() throws SQLException {
        Query query = new Query(null, "test_repo", null, null);
        int limit = 10;
        State state = null;

        List<Repository> result =
                SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                        false, mockJdbcHelper, INFINITE_DEADLINE, testExecutor);

        assertNotNull(result);
        assertEquals(testRepositories, result);

        String lastQuery = testExecutor.getLastExecutedQuery();
        String normalized = normalizeSql(lastQuery);

        // Verify FTS and escaped ilike
        assertTrue(normalized.contains("to_tsquery('simple', 'test & repo:*')"),
                "Should have FTS condition");
        assertTrue(normalized.contains("repository_key ilike '%test^_repo%' ESCAPE '^'"),
                "Should have escaped ilike condition for repo param");

        // Verify AND operator
        assertTrue(normalized.matches(".*to_tsquery.*\\)\\s+and\\s+.*repository_key\\s+ilike.*ESCAPE.*"),
                "Should use AND operator between FTS and escaped ilike for repo param");
    }

    @Test
    void sqlInjection() throws SQLException {
        Query query = new Query(null, INJECTION_PAYLOAD, null, null);
        int limit = 10;
        State state = null;

        SyntaxSearchQueryUtils.queryRepositories(query, Filters.orEmpty(null), limit, state,
                false, mockJdbcHelper,
                INFINITE_DEADLINE, testExecutor);

        PreparedQuery lastQuery = testExecutor.getLastPreparedQuery();
        assertFalse(lastQuery.getTemplate().contains(INJECTION_PAYLOAD),
                "SQL template must not contain user input (injection risk)");
    }

    private static class TestRequestExecutor implements RequestExecutor<Repository> {
        private final List<Repository> repositories;
        @Getter
        private String lastExecutedQuery;
        @Getter
        private PreparedQuery lastPreparedQuery;

        public TestRequestExecutor(List<Repository> repositories) {
            this.repositories = new ArrayList<>(repositories);
        }

        @Override
        public List<Repository> execute(JdbcHelper jdbcHelper, PreparedQuery query, Deadline deadline)
                throws SQLException {
            this.lastPreparedQuery = query;
            String template = query.getTemplate();
            List<Object> params = query.getParams();
            this.lastExecutedQuery = bind(template, params);
            return new ArrayList<>(repositories);
        }

    }
}
