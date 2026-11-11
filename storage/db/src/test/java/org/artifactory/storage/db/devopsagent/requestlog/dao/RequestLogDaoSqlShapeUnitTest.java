package org.artifactory.storage.db.devopsagent.requestlog.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

import static org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDaoTestUtils.assertOrderedTokensPresent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins per-DB SQL emitted by {@link RequestLogDao} search and pagination paths.
 */
class RequestLogDaoSqlShapeUnitTest {

    @Test
    void searchEmitsLimitBeforeOffsetOnMysql() throws SQLException {
        String sql = captureSearchSqlWithoutTextFilter(DbType.MYSQL, 10, 20);
        assertOrderedTokensPresent(sql, " LIMIT 11", " OFFSET 20");
        assertFalse(sql.matches("(?s).*OFFSET 20.*LIMIT 11.*"),
                "MySQL must NOT emit OFFSET before LIMIT: " + sql);
    }

    @Test
    void searchEmitsLimitBeforeOffsetOnMariadb() throws SQLException {
        String sql = captureSearchSqlWithoutTextFilter(DbType.MARIADB, 10, 20);
        assertOrderedTokensPresent(sql, " LIMIT 11", " OFFSET 20");
    }

    @Test
    void searchEmitsLimitBeforeOffsetOnPostgres() throws SQLException {
        String sql = captureSearchSqlWithoutTextFilter(DbType.POSTGRESQL, 10, 20);
        assertOrderedTokensPresent(sql, " LIMIT 11", " OFFSET 20");
    }

    @Test
    void searchEmitsOffsetBeforeFetchFirstOnOracle() throws SQLException {
        String sql = captureSearchSqlWithoutTextFilter(DbType.ORACLE, 10, 20);
        assertOrderedTokensPresent(sql, " OFFSET 20 ROWS", " FETCH FIRST 11 ROWS ONLY");
    }

    @Test
    void searchEmitsOffsetBeforeFetchNextOnMssql() throws SQLException {
        String sql = captureSearchSqlWithoutTextFilter(DbType.MSSQL, 10, 20);
        assertOrderedTokensPresent(sql, " OFFSET 20 ROWS", " FETCH NEXT 11 ROWS ONLY");
    }

    @Test
    void searchEmitsOffsetBeforeFetchFirstOnDerby() throws SQLException {
        String sql = captureSearchSqlWithoutTextFilter(DbType.DERBY, 10, 20);
        assertOrderedTokensPresent(sql, " OFFSET 20 ROWS", " FETCH FIRST 11 ROWS ONLY");
    }

    @Test
    void searchEmitsOffsetZeroOnMssqlWhenOffsetIsZero() throws SQLException {
        String sql = captureSearchSqlWithoutTextFilter(DbType.MSSQL, 10, 0);
        assertOrderedTokensPresent(sql, " OFFSET 0 ROWS", " FETCH NEXT 11 ROWS ONLY");
    }

    @Test
    void searchUsesTsqueryOnPostgres() throws SQLException {
        String sql = captureSearchSql(DbType.POSTGRESQL, "a|b", "needle", 10, 0);
        assertTrue(sql.contains("search_vector @@ to_tsquery('simple', ?)"),
                "Postgres FTS must use to_tsquery: " + sql);
    }

    @Test
    void searchUsesMatchAgainstOnMysql() throws SQLException {
        String sql = captureSearchSql(DbType.MYSQL, null, "needle", 10, 0);
        assertTrue(sql.contains("MATCH(request_path, username) AGAINST(? IN NATURAL LANGUAGE MODE)"),
                "MySQL FTS must use MATCH … AGAINST: " + sql);
    }

    @Test
    void searchFallsBackToLowerLikeOnDerby() throws SQLException {
        String sql = captureSearchSql(DbType.DERBY, null, "Needle", 10, 0);
        assertTrue(sql.contains("LOWER(") && sql.contains("LIKE ?"),
                "Derby must use case-insensitive LIKE: " + sql);
    }

    @Test
    void searchOmitsTextClauseWhenSearchBlank() throws SQLException {
        String sql = captureSearchSql(DbType.MYSQL, null, null, 10, 0);
        assertFalse(sql.contains("MATCH("), "Blank search must skip FTS: " + sql);
        assertFalse(sql.contains("LIKE ?"), "Blank search must skip LIKE: " + sql);
    }

    private static String captureSearchSqlWithoutTextFilter(DbType dbType, int limit, int offset)
            throws SQLException {
        return captureSearchSql(dbType, null, null, limit, offset);
    }

    private static String captureSearchSql(DbType dbType, String tsquery, String rawSearch, int limit, int offset)
            throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(any(String.class), any(Object[].class))).thenReturn(rs);

        RequestLogDao dao = new RequestLogDao(jdbcHelper, dbProperties);
        SearchCriteria criteria = SearchCriteria.builder()
                .repoNames(Collections.emptyList())
                .searchQuery(tsquery)
                .rawSearchText(rawSearch)
                .limit(limit)
                .offset(offset)
                .build();
        dao.search(criteria);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(sqlCaptor.capture(), any(Object[].class));
        return sqlCaptor.getValue();
    }

}
