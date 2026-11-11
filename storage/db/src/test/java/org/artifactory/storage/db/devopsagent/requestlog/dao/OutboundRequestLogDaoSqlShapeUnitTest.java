package org.artifactory.storage.db.devopsagent.requestlog.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.devopsagent.requestlog.model.Cursor;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collections;
import java.util.List;

import static org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDaoTestUtils.assertOrderedTokensPresent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins per-DB SQL emitted by {@link OutboundRequestLogDao} search paths.
 */
class OutboundRequestLogDaoSqlShapeUnitTest {

    private OutboundRequestLogDao daoFor(DbType dbType) {
        ArtifactoryDbProperties props = mock(ArtifactoryDbProperties.class);
        when(props.getDbType()).thenReturn(dbType);
        return new OutboundRequestLogDao(mock(ArtifactoryJdbcHelper.class), props);
    }

    private static SearchCriteria noFilter(int limit, int offset) {
        return SearchCriteria.builder()
                .repoNames(Collections.emptyList())
                .limit(limit)
                .offset(offset)
                .build();
    }

    private static SearchCriteria withText(String tsquery, String raw) {
        return SearchCriteria.builder()
                .repoNames(Collections.emptyList())
                .searchQuery(tsquery)
                .rawSearchText(raw)
                .limit(10)
                .offset(0)
                .build();
    }

    @Test
    void noFilterQueriesOutboundTable() {
        String sql = daoFor(DbType.POSTGRESQL).buildSearchQuery(noFilter(10, 0)).sql();
        assertTrue(sql.contains("FROM outbound_request_logs"), "Must query outbound table: " + sql);
        assertFalse(sql.contains("request_logs ") && !sql.contains("outbound_request_logs"),
                "Must NOT query inbound table: " + sql);
    }

    @Test
    void noFilterOmitsFtsClause() {
        for (DbType db : DbType.values()) {
            String sql = daoFor(db).buildSearchQuery(noFilter(10, 0)).sql();
            assertFalse(sql.contains("search_vector"), "No FTS on no-filter for " + db + ": " + sql);
            assertFalse(sql.contains("MATCH("), "No MATCH on no-filter for " + db + ": " + sql);
            assertFalse(sql.contains("LIKE ?"), "No LIKE on no-filter for " + db + ": " + sql);
        }
    }

    @Test
    void postgresUsesTsquery() {
        String sql = daoFor(DbType.POSTGRESQL).buildSearchQuery(withText("axios", "axios")).sql();
        assertTrue(sql.contains("search_vector @@ to_tsquery('simple', ?)"),
                "Postgres FTS must use to_tsquery: " + sql);
        assertFalse(sql.contains("repo_name"), "Must not reference old stub column repo_name: " + sql);
    }

    @Test
    void mysqlUsesMatchAgainstWithRepoKey() {
        String sql = daoFor(DbType.MYSQL).buildSearchQuery(withText(null, "axios")).sql();
        assertTrue(sql.contains("MATCH(request_url, repo_key, http_method, trace_id)"),
                "MySQL FTS must use MATCH with repo_key: " + sql);
        assertFalse(sql.contains("repo_name"), "Must not reference old stub column repo_name: " + sql);
    }

    @Test
    void mariadbUsesMatchAgainstWithRepoKey() {
        String sql = daoFor(DbType.MARIADB).buildSearchQuery(withText(null, "axios")).sql();
        assertTrue(sql.contains("MATCH(request_url, repo_key, http_method, trace_id)"),
                "MariaDB FTS must use MATCH with repo_key: " + sql);
    }

    @ParameterizedTest
    @EnumSource(value = DbType.class, names = {"ORACLE", "MSSQL", "DERBY"})
    void nonFtsDbsUseLikeWithEscapeAndRepoKey(DbType dbType) {
        String sql = daoFor(dbType).buildSearchQuery(withText(null, "axios")).sql();
        assertTrue(sql.contains("ESCAPE"), "Must have ESCAPE clause for " + dbType + ": " + sql);
        assertTrue(sql.contains("repo_key"), "Must reference repo_key for " + dbType + ": " + sql);
        assertFalse(sql.contains("repo_name"), "Must not reference repo_name for " + dbType + ": " + sql);
    }

    @Test
    void repoNamesFilterWiredToRepoKey() {
        SearchCriteria c = SearchCriteria.builder()
                .repoNames(List.of("npm-remote", "pypi-remote"))
                .limit(100).offset(0)
                .build();
        RequestLogDaoSupport.QueryParts q = daoFor(DbType.POSTGRESQL).buildSearchQuery(c);
        assertTrue(q.sql().contains("repo_key IN ("), "Must filter by repo_key: " + q.sql());
        assertTrue(q.params().contains("npm-remote"), "Must include npm-remote in params");
        assertTrue(q.params().contains("pypi-remote"), "Must include pypi-remote in params");
    }

    @Test
    void paginationMysqlLimitBeforeOffset() {
        String sql = daoFor(DbType.MYSQL).buildSearchQuery(noFilter(10, 20)).sql();
        assertOrderedTokensPresent(sql, " LIMIT 11", " OFFSET 20");
        assertFalse(sql.matches("(?s).*OFFSET 20.*LIMIT 11.*"),
                "MySQL must NOT emit OFFSET before LIMIT: " + sql);
    }

    @Test
    void paginationMssqlOffsetFetchNext() {
        String sql = daoFor(DbType.MSSQL).buildSearchQuery(noFilter(10, 20)).sql();
        assertOrderedTokensPresent(sql, " OFFSET 20 ROWS", " FETCH NEXT 11 ROWS ONLY");
    }

    @Test
    void paginationOracleOffsetFetchFirst() {
        String sql = daoFor(DbType.ORACLE).buildSearchQuery(noFilter(10, 20)).sql();
        assertOrderedTokensPresent(sql, " OFFSET 20 ROWS", " FETCH FIRST 11 ROWS ONLY");
    }

    @Test
    void cursorFilterApplied() {
        SearchCriteria c = SearchCriteria.builder()
                .repoNames(Collections.emptyList())
                .limit(10).offset(0)
                .cursor(new Cursor(1000L, 42L))
                .build();
        String sql = daoFor(DbType.POSTGRESQL).buildSearchQuery(c).sql();
        assertTrue(sql.contains("request_time < ?"), "Cursor must apply time filter: " + sql);
    }

}
