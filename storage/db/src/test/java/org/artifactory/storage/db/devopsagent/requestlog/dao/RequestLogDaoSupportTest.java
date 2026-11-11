package org.artifactory.storage.db.devopsagent.requestlog.dao;

import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequestLogDaoSupport} covering all ~25 decision points.
 */
class RequestLogDaoSupportTest {

    // -------------------------------------------------------------------------
    // Group A: hasSearch
    // -------------------------------------------------------------------------

    @Test
    void hasSearch_returnsFalse_whenBothNull() {
        SearchCriteria criteria = criteriaWithSearch(null, null);
        assertFalse(RequestLogDaoSupport.hasSearch(criteria));
    }

    @Test
    void hasSearch_returnsFalse_whenSearchQueryBlankAndRawTextNull() {
        SearchCriteria criteria = criteriaWithSearch("  ", null);
        assertFalse(RequestLogDaoSupport.hasSearch(criteria));
    }

    @Test
    void hasSearch_returnsTrue_whenSearchQueryNonBlank() {
        SearchCriteria criteria = criteriaWithSearch("error~", null);
        assertTrue(RequestLogDaoSupport.hasSearch(criteria));
    }

    @Test
    void hasSearch_returnsTrue_whenRawSearchTextNonBlank() {
        SearchCriteria criteria = criteriaWithSearch(null, "needle");
        assertTrue(RequestLogDaoSupport.hasSearch(criteria));
    }

    // -------------------------------------------------------------------------
    // Group B: appendPlaceholders
    // -------------------------------------------------------------------------

    @Test
    void appendPlaceholders_zeroProducesEmptyString() {
        StringBuilder sb = new StringBuilder();
        RequestLogDaoSupport.appendPlaceholders(sb, 0);
        assertEquals("", sb.toString());
    }

    @Test
    void appendPlaceholders_oneProducesSinglePlaceholder() {
        StringBuilder sb = new StringBuilder();
        RequestLogDaoSupport.appendPlaceholders(sb, 1);
        assertEquals("?", sb.toString());
    }

    @Test
    void appendPlaceholders_threeProducesCommaSeparatedPlaceholders() {
        StringBuilder sb = new StringBuilder();
        RequestLogDaoSupport.appendPlaceholders(sb, 3);
        assertEquals("?,?,?", sb.toString());
    }

    // -------------------------------------------------------------------------
    // Group C: appendPagination — MSSQL
    // -------------------------------------------------------------------------

    @Test
    void appendPagination_mssqlEmitsOffsetRowsFetchNextSyntax() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        RequestLogDaoSupport.appendPagination(DbType.MSSQL, sql, 10, 5);
        String result = sql.toString();
        assertTrue(result.contains(" OFFSET 5 ROWS FETCH NEXT 10 ROWS ONLY"),
                "MSSQL must use OFFSET … ROWS FETCH NEXT … ROWS ONLY: " + result);
        assertFalse(result.contains("LIMIT"), "MSSQL must not contain LIMIT: " + result);
    }

    // -------------------------------------------------------------------------
    // Group D: appendPagination — Oracle/Derby
    // -------------------------------------------------------------------------

    @Test
    void appendPagination_oracleWithOffsetEmitsOffsetBeforeFetchFirst() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        RequestLogDaoSupport.appendPagination(DbType.ORACLE, sql, 10, 5);
        String result = sql.toString();
        int offsetIdx = result.indexOf(" OFFSET 5 ROWS");
        int fetchIdx = result.indexOf(" FETCH FIRST 10 ROWS ONLY");
        assertTrue(offsetIdx >= 0, "ORACLE must contain OFFSET clause: " + result);
        assertTrue(fetchIdx > offsetIdx, "ORACLE FETCH FIRST must follow OFFSET: " + result);
    }

    @Test
    void appendPagination_oracleWithZeroOffsetOmitsOffsetClause() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        RequestLogDaoSupport.appendPagination(DbType.ORACLE, sql, 10, 0);
        String result = sql.toString();
        assertFalse(result.contains("OFFSET"), "ORACLE offset=0 must not contain OFFSET: " + result);
        assertTrue(result.contains(" FETCH FIRST 10 ROWS ONLY"),
                "ORACLE offset=0 must contain FETCH FIRST: " + result);
    }

    @Test
    void appendPagination_derbyWithZeroOffsetOmitsOffsetClause() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        RequestLogDaoSupport.appendPagination(DbType.DERBY, sql, 5, 0);
        String result = sql.toString();
        assertFalse(result.contains("OFFSET"), "DERBY offset=0 must not contain OFFSET: " + result);
        assertTrue(result.contains(" FETCH FIRST 5 ROWS ONLY"),
                "DERBY offset=0 must contain FETCH FIRST: " + result);
    }

    // -------------------------------------------------------------------------
    // Group E: appendPagination — MySQL / PostgreSQL / MariaDB
    // -------------------------------------------------------------------------

    @Test
    void appendPagination_mysqlWithZeroOffsetEmitsOnlyLimit() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        RequestLogDaoSupport.appendPagination(DbType.MYSQL, sql, 10, 0);
        String result = sql.toString();
        assertTrue(result.contains(" LIMIT 10"), "MySQL must contain LIMIT: " + result);
        assertFalse(result.contains("OFFSET"), "MySQL offset=0 must not contain OFFSET: " + result);
    }

    @Test
    void appendPagination_postgresqlWithOffsetEmitsLimitThenOffset() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        RequestLogDaoSupport.appendPagination(DbType.POSTGRESQL, sql, 10, 5);
        String result = sql.toString();
        int limitIdx = result.indexOf(" LIMIT 10");
        int offsetIdx = result.indexOf(" OFFSET 5");
        assertTrue(limitIdx >= 0, "PostgreSQL must contain LIMIT: " + result);
        assertTrue(offsetIdx > limitIdx, "PostgreSQL OFFSET must follow LIMIT: " + result);
    }

    @Test
    void appendPagination_mariadbWithOffsetEmitsBothLimitAndOffset() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        RequestLogDaoSupport.appendPagination(DbType.MARIADB, sql, 10, 20);
        String result = sql.toString();
        assertTrue(result.contains(" LIMIT 10"), "MariaDB must contain LIMIT: " + result);
        assertTrue(result.contains(" OFFSET 20"), "MariaDB must contain OFFSET: " + result);
    }

    // -------------------------------------------------------------------------
    // Group F: appendInboundSearchClause — no search (short-circuit)
    // -------------------------------------------------------------------------

    @Test
    void appendInboundSearchClause_doesNothing_whenNoSearch() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch(null, null);
        RequestLogDaoSupport.appendInboundSearchClause(DbType.POSTGRESQL, where, params, criteria);
        assertEquals("SELECT 1", sql.toString(), "SQL must be unchanged when no search");
        assertTrue(params.isEmpty(), "Params must be unchanged when no search");
    }

    // -------------------------------------------------------------------------
    // Group G: appendInboundSearchClause — each DB type with search
    // -------------------------------------------------------------------------

    @Test
    void appendInboundSearchClause_postgresql_appendsTsquery() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch("error~", "error");
        RequestLogDaoSupport.appendInboundSearchClause(DbType.POSTGRESQL, where, params, criteria);
        String result = sql.toString();
        assertTrue(result.contains("to_tsquery('simple', ?)"),
                "PostgreSQL inbound must use to_tsquery: " + result);
        assertEquals(1, params.size());
        assertEquals("error~", params.get(0));
    }

    @Test
    void appendInboundSearchClause_mysql_appendsMatchAgainstOnRequestPathAndUsername() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch(null, "needle");
        RequestLogDaoSupport.appendInboundSearchClause(DbType.MYSQL, where, params, criteria);
        String result = sql.toString();
        assertTrue(result.contains("MATCH(request_path, username)"),
                "MySQL inbound must match request_path and username: " + result);
        assertEquals(1, params.size());
        assertEquals("needle", params.get(0));
    }

    @Test
    void appendInboundSearchClause_oracle_appendsNvlLike() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch(null, "test");
        RequestLogDaoSupport.appendInboundSearchClause(DbType.ORACLE, where, params, criteria);
        String result = sql.toString();
        assertTrue(result.contains("NVL(request_path"), "ORACLE inbound must use NVL(request_path: " + result);
        assertTrue(result.contains("LIKE ? ESCAPE"), "ORACLE inbound must use LIKE ESCAPE: " + result);
        assertEquals(1, params.size());
        assertTrue(params.get(0).toString().contains("test"),
                "ORACLE inbound param must contain search text: " + params.get(0));
        assertEquals("%test%", params.get(0));
    }

    @Test
    void appendInboundSearchClause_mssql_appendsIsnullLike() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch(null, "test");
        RequestLogDaoSupport.appendInboundSearchClause(DbType.MSSQL, where, params, criteria);
        String result = sql.toString();
        assertTrue(result.contains("ISNULL(request_path"), "MSSQL inbound must use ISNULL(request_path: " + result);
        assertTrue(result.contains("LIKE ? ESCAPE"), "MSSQL inbound must use LIKE ESCAPE: " + result);
        assertEquals(1, params.size());
        assertEquals("%test%", params.get(0));
    }

    @Test
    void appendInboundSearchClause_derby_appendsCoalesceLike() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch(null, "test");
        RequestLogDaoSupport.appendInboundSearchClause(DbType.DERBY, where, params, criteria);
        String result = sql.toString();
        assertTrue(result.contains("COALESCE(request_path"), "DERBY inbound must use COALESCE(request_path: " + result);
        assertTrue(result.contains("LIKE ? ESCAPE"), "DERBY inbound must use LIKE ESCAPE: " + result);
        assertEquals(1, params.size());
        assertEquals("%test%", params.get(0));
    }

    @Test
    void appendInboundSearchClause_mariadb_appendsMatchAgainstOnRequestPathAndUsername() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch(null, "needle");
        RequestLogDaoSupport.appendInboundSearchClause(DbType.MARIADB, where, params, criteria);
        String result = sql.toString();
        assertTrue(result.contains("MATCH(request_path, username)"),
                "MariaDB inbound must match request_path and username: " + result);
        assertEquals(1, params.size());
        assertEquals("needle", params.get(0));
    }

    // -------------------------------------------------------------------------
    // Group H: appendOutboundSearchClause — spot checks
    // -------------------------------------------------------------------------

    @Test
    void appendOutboundSearchClause_mysql_matchesOutboundColumns() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch(null, "needle");
        RequestLogDaoSupport.appendOutboundSearchClause(DbType.MYSQL, where, params, criteria);
        String result = sql.toString();
        assertTrue(result.contains("MATCH(request_url, repo_key, http_method, trace_id)"),
                "MySQL outbound must match outbound columns: " + result);
        assertEquals(1, params.size());
        assertEquals("needle", params.get(0));
    }

    @Test
    void appendOutboundSearchClause_oracle_usesNvlOnRequestUrl() {
        StringBuilder sql = new StringBuilder("SELECT 1");
        AbstractRequestLogDao.WhereClause where = new AbstractRequestLogDao.WhereClause(sql);
        List<Object> params = new ArrayList<>();
        SearchCriteria criteria = criteriaWithSearch(null, "find");
        RequestLogDaoSupport.appendOutboundSearchClause(DbType.ORACLE, where, params, criteria);
        String result = sql.toString();
        assertTrue(result.contains("NVL(request_url"), "ORACLE outbound must use NVL(request_url: " + result);
        assertTrue(result.contains("LIKE ? ESCAPE"), "ORACLE outbound must use LIKE ESCAPE: " + result);
        assertEquals(1, params.size());
        assertEquals("%find%", params.get(0));
    }

    // -------------------------------------------------------------------------
    // Group I: deleteOlderThanChunked
    // -------------------------------------------------------------------------

    @Test
    void deleteOlderThanChunked_singleBatch_returnsDeletedCount() throws SQLException {
        String sql = "DELETE FROM request_logs WHERE id IN "
                + "(SELECT id FROM request_logs WHERE request_time < ? LIMIT 5)";
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        when(helper.executeUpdate(sql, 1000L)).thenReturn(4);
        int result = RequestLogDaoSupport.deleteOlderThanChunked(
                helper, DbType.POSTGRESQL, "request_logs", 1000L, 5, 0L);
        assertEquals(4, result, "Single partial batch must return rows deleted");
        verify(helper, times(1)).executeUpdate(sql, 1000L);
    }

    @Test
    void deleteOlderThanChunked_multiBatchThenDone_returnsTotalCount() throws SQLException {
        String sql = "DELETE FROM request_logs WHERE id IN "
                + "(SELECT id FROM request_logs WHERE request_time < ? LIMIT 5)";
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        when(helper.executeUpdate(sql, 1000L))
                .thenReturn(5)
                .thenReturn(0);
        int result = RequestLogDaoSupport.deleteOlderThanChunked(
                helper, DbType.POSTGRESQL, "request_logs", 1000L, 5, 0L);
        assertEquals(5, result, "Two-batch run must return sum of both batches");
        verify(helper, times(2)).executeUpdate(sql, 1000L);
    }

    @Test
    void deleteOlderThanChunked_sqlContainsCutoffParam() throws SQLException {
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        RequestLogDaoSupport.deleteOlderThanChunked(
                helper, DbType.POSTGRESQL, "request_logs", 9999L, 100, 0L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(helper).executeUpdate(sqlCaptor.capture(), eq(9999L));
        assertTrue(sqlCaptor.getValue().contains("WHERE request_time < ?"),
                "Delete SQL must filter by request_time: " + sqlCaptor.getValue());
    }

    @Test
    void deleteOlderThanChunked_usesMssqlTopSyntax() throws SQLException {
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        RequestLogDaoSupport.deleteOlderThanChunked(
                helper, DbType.MSSQL, "request_logs", 1000L, 100, 0L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(helper).executeUpdate(sqlCaptor.capture(), eq(1000L));
        assertTrue(sqlCaptor.getValue().contains("DELETE TOP (100)"),
                "MSSQL delete must use TOP with inlined batch size: " + sqlCaptor.getValue());
    }

    @Test
    void deleteOlderThanChunked_usesOracleRownumSyntax() throws SQLException {
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        RequestLogDaoSupport.deleteOlderThanChunked(
                helper, DbType.ORACLE, "request_logs", 1000L, 100, 0L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(helper).executeUpdate(sqlCaptor.capture(), eq(1000L));
        assertTrue(sqlCaptor.getValue().contains("ROWNUM <= 100"),
                "ORACLE delete must use inlined ROWNUM: " + sqlCaptor.getValue());
    }

    @Test
    void deleteOlderThanChunked_mysqlUsesInlineLimit() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.MYSQL, "request_logs", 100);
        assertTrue(sql.startsWith("DELETE FROM request_logs WHERE request_time < ?"),
                "MySQL chunked DELETE must use inline DELETE … LIMIT: " + sql);
        assertTrue(sql.endsWith(" LIMIT 100"),
                "MySQL chunked DELETE must end with inlined LIMIT: " + sql);
    }

    @Test
    void deleteOlderThanChunked_mariadbUsesInlineLimit() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.MARIADB, "request_logs", 100);
        assertTrue(sql.startsWith("DELETE FROM request_logs WHERE request_time < ?"),
                "MariaDB chunked DELETE must use inline DELETE … LIMIT: " + sql);
        assertTrue(sql.endsWith(" LIMIT 100"), sql);
    }

    @Test
    void deleteOlderThanChunked_postgresqlUsesSubquerySelectLimit() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.POSTGRESQL, "request_logs", 100);
        assertTrue(sql.startsWith("DELETE FROM request_logs WHERE id IN (SELECT id FROM request_logs"),
                "Postgres chunked DELETE must use subquery-with-IN pattern: " + sql);
        assertTrue(sql.contains("WHERE request_time < ? LIMIT 100"),
                "Postgres subquery must filter by request_time with inlined batch limit: " + sql);
        assertFalse(sql.equals("DELETE FROM request_logs WHERE request_time < ? LIMIT 100"),
                "Postgres must not use top-level DELETE … LIMIT: " + sql);
    }

    @Test
    void deleteOlderThanChunked_postgresqlUsesSubqueryForOutboundTable() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.POSTGRESQL, "outbound_request_logs", 100);
        assertTrue(sql.startsWith(
                        "DELETE FROM outbound_request_logs WHERE id IN (SELECT id FROM outbound_request_logs"),
                "Postgres outbound chunked DELETE must use subquery-with-IN pattern: " + sql);
        assertTrue(sql.contains("WHERE request_time < ? LIMIT 100"), sql);
    }

    @Test
    void deleteOlderThanChunked_derbyUsesFetchFirstSyntax() throws SQLException {
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        RequestLogDaoSupport.deleteOlderThanChunked(
                helper, DbType.DERBY, "request_logs", 1000L, 50, 0L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(helper).executeUpdate(sqlCaptor.capture(), eq(1000L));
        assertTrue(sqlCaptor.getValue().contains("FETCH FIRST 50 ROWS ONLY"),
                "DERBY delete must use inlined FETCH FIRST: " + sqlCaptor.getValue());
    }

    @Test
    void deleteOlderThanChunked_mssqlInlinesBatchSizeBindsCutoff() throws SQLException {
        long cutoffMs = 1_700_000_000_000L;
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        RequestLogDaoSupport.deleteOlderThanChunked(
                helper, DbType.MSSQL, "request_logs", cutoffMs, 500, 0L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(helper).executeUpdate(sqlCaptor.capture(), eq(cutoffMs));
        assertTrue(sqlCaptor.getValue().contains("DELETE TOP (500)"),
                "MSSQL: batchSize must be inlined in TOP clause: " + sqlCaptor.getValue());
    }

    @Test
    void deleteOlderThanChunked_postgresqlInlinesBatchSizeBindsCutoff() throws SQLException {
        long cutoffMs = 1_700_000_000_000L;
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        RequestLogDaoSupport.deleteOlderThanChunked(
                helper, DbType.POSTGRESQL, "request_logs", cutoffMs, 500, 0L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(helper).executeUpdate(sqlCaptor.capture(), eq(cutoffMs));
        assertTrue(sqlCaptor.getValue().contains("LIMIT 500"),
                "PostgreSQL: batchSize must be inlined in LIMIT clause: " + sqlCaptor.getValue());
    }

    @Test
    void deleteOlderThanChunked_stopsOnInterrupt() throws Exception {
        String sql = "DELETE FROM request_logs WHERE id IN "
                + "(SELECT id FROM request_logs WHERE request_time < ? LIMIT 5)";
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        when(helper.executeUpdate(sql, 1000L)).thenReturn(5);

        int[] result = {-1};
        Thread worker = new Thread(() -> {
            try {
                result[0] = RequestLogDaoSupport.deleteOlderThanChunked(
                        helper, DbType.POSTGRESQL, "request_logs", 1000L, 5, 200L);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        worker.start();
        Thread.sleep(50);
        worker.interrupt();
        worker.join(2000);
        assertFalse(worker.isAlive(), "Worker thread must exit after interrupt");
        assertTrue(result[0] >= 5, "Should have deleted at least one full batch before stopping");
    }

    // -------------------------------------------------------------------------
    // Helper factory
    // -------------------------------------------------------------------------

    private static SearchCriteria criteriaWithSearch(String searchQuery, String rawSearchText) {
        return SearchCriteria.builder()
                .repoNames(Collections.emptyList())
                .searchQuery(searchQuery)
                .rawSearchText(rawSearchText)
                .limit(100)
                .offset(0)
                .build();
    }

    private static String captureChunkedDeleteSql(DbType dbType, String tableName, int batchSize)
            throws SQLException {
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        RequestLogDaoSupport.deleteOlderThanChunked(helper, dbType, tableName, 1_700_000_000_000L, batchSize, 0L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(helper).executeUpdate(sqlCaptor.capture(), eq(1_700_000_000_000L));
        return sqlCaptor.getValue();
    }
}
