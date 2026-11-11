package org.artifactory.storage.db.devopsagent.errorcatalog.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.KeysetCursor;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the per-DB SQL string emitted by SELECT-pagination and chunked-DELETE paths in
 * {@link ErrorCatalogDao}.
 */
class ErrorCatalogDaoSqlShapeUnitTest {


    @Test
    void findByTemplateEmitsOffsetZeroFetchNextOnMssql() throws SQLException {
        String sql = captureFindByTemplateSql(DbType.MSSQL, 25);
        assertTrue(sql.contains(" OFFSET 0 ROWS FETCH NEXT 25 ROWS ONLY"),
                "MSSQL findByTemplate must emit OFFSET 0 ROWS before FETCH NEXT: " + sql);
        assertFalse(sql.contains(" FETCH FIRST "),
                "MSSQL must not emit FETCH FIRST (T-SQL parse error without OFFSET): " + sql);
    }

    @Test
    void findByTemplateEmitsFetchFirstOnOracle() throws SQLException {
        String sql = captureFindByTemplateSql(DbType.ORACLE, 25);
        assertTrue(sql.contains(" FETCH FIRST 25 ROWS ONLY"), sql);
    }

    @Test
    void findByTemplateEmitsFetchFirstOnDerby() throws SQLException {
        String sql = captureFindByTemplateSql(DbType.DERBY, 25);
        assertTrue(sql.contains(" FETCH FIRST 25 ROWS ONLY"), sql);
    }

    @Test
    void findByTemplateEmitsLimitOnMysql() throws SQLException {
        String sql = captureFindByTemplateSql(DbType.MYSQL, 25);
        assertTrue(sql.endsWith(" LIMIT 25"), sql);
    }

    @Test
    void findByTemplateEmitsLimitOnPostgres() throws SQLException {
        String sql = captureFindByTemplateSql(DbType.POSTGRESQL, 25);
        assertTrue(sql.endsWith(" LIMIT 25"), sql);
    }

    @Test
    void findByOccurredBetweenEmitsOffsetZeroFetchNextOnMssql() throws SQLException {
        String sql = captureFindByOccurredBetweenSql(DbType.MSSQL, 25);
        assertTrue(sql.contains(" OFFSET 0 ROWS FETCH NEXT 25 ROWS ONLY"),
                "MSSQL findByOccurredBetween must emit OFFSET 0 ROWS before FETCH NEXT: " + sql);
    }

    @Test
    void chunkedDeleteUsesSubquerySelectLimitOnPostgres() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.POSTGRESQL, 250);
        assertTrue(sql.startsWith("DELETE FROM error_catalog WHERE id IN (SELECT id FROM "),
                "Postgres chunked DELETE must use the subquery-with-IN pattern: " + sql);
        assertTrue(sql.contains(" LIMIT 250"),
                "Postgres subquery must carry the per-batch LIMIT: " + sql);
    }

    @Test
    void chunkedDeleteUsesInlineLimitOnMysql() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.MYSQL, 250);
        assertTrue(sql.startsWith("DELETE FROM error_catalog WHERE occurred_at < ?"),
                "MySQL chunked DELETE must use an inline DELETE … LIMIT n: " + sql);
        assertTrue(sql.endsWith(" LIMIT 250"),
                "MySQL chunked DELETE must end with LIMIT n: " + sql);
    }

    @Test
    void chunkedDeleteUsesInlineLimitOnMariadb() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.MARIADB, 250);
        assertTrue(sql.startsWith("DELETE FROM error_catalog WHERE occurred_at < ?"),
                "MariaDB chunked DELETE must use an inline DELETE … LIMIT n: " + sql);
        assertTrue(sql.endsWith(" LIMIT 250"), sql);
    }

    @Test
    void chunkedDeleteUsesRownumOnOracle() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.ORACLE, 250);
        assertTrue(sql.contains("AND ROWNUM <= 250"),
                "Oracle chunked DELETE must bound the chunk with ROWNUM: " + sql);
    }

    @Test
    void chunkedDeleteUsesTopOnMssql() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.MSSQL, 250);
        assertTrue(sql.startsWith("DELETE TOP (250) FROM error_catalog"),
                "MSSQL chunked DELETE must use DELETE TOP (n): " + sql);
    }

    @Test
    void chunkedDeleteUsesFetchFirstSubqueryOnDerby() throws SQLException {
        String sql = captureChunkedDeleteSql(DbType.DERBY, 250);
        assertTrue(sql.contains("WHERE id IN (SELECT id FROM error_catalog"),
                "Derby chunked DELETE must use the subquery-with-IN pattern: " + sql);
        assertTrue(sql.contains("FETCH FIRST 250 ROWS ONLY"),
                "Derby subquery must use ANSI FETCH FIRST: " + sql);
    }

    private static String captureFindByTemplateSql(DbType dbType, int limit) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(any(String.class), any(Object[].class))).thenReturn(rs);

        new ErrorCatalogDao(jdbcHelper, dbProperties).findByTemplate(42L, null, limit);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(sqlCaptor.capture(), any(Object[].class));
        return sqlCaptor.getValue();
    }

    private static String captureFindByOccurredBetweenSql(DbType dbType, int limit) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(any(String.class), anyLong(), anyLong())).thenReturn(rs);

        new ErrorCatalogDao(jdbcHelper, dbProperties).findByOccurredBetween(1L, 2L, limit);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(sqlCaptor.capture(), anyLong(), anyLong());
        return sqlCaptor.getValue();
    }

    private static String captureChunkedDeleteSql(DbType dbType, int batchSize) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        when(jdbcHelper.executeUpdate(anyString(), any(Object[].class))).thenReturn(0);

        new ErrorCatalogDao(jdbcHelper, dbProperties)
                .deleteOlderThanChunked(1_700_000_000_000L, batchSize, 0L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeUpdate(sqlCaptor.capture(), anyLong());
        return sqlCaptor.getValue();
    }

    // ============================================================
    // findByTemplatesAndTimeRange — RTDEV-86991 raw events drill-down
    // ============================================================

    @Test
    void findByTemplatesAndTimeRangeEmitsOffsetZeroFetchNextOnMssql() throws SQLException {
        String sql = captureFindByTemplatesAndTimeRangeSql(DbType.MSSQL, List.of(1L, 2L), null, null, null, 25);
        assertTrue(sql.contains(" OFFSET 0 ROWS FETCH NEXT 25 ROWS ONLY"),
                "MSSQL findByTemplatesAndTimeRange must emit OFFSET 0 ROWS before FETCH NEXT: " + sql);
        assertFalse(sql.contains(" FETCH FIRST "),
                "MSSQL must not emit FETCH FIRST (T-SQL parse error without OFFSET): " + sql);
    }

    @Test
    void findByTemplatesAndTimeRangeEmitsFetchFirstOnOracleAndDerby() throws SQLException {
        for (DbType dbType : new DbType[] {DbType.ORACLE, DbType.DERBY}) {
            String sql = captureFindByTemplatesAndTimeRangeSql(dbType, List.of(1L), null, null, null, 25);
            assertTrue(sql.contains(" FETCH FIRST 25 ROWS ONLY"),
                    dbType + " must emit FETCH FIRST 25 ROWS ONLY: " + sql);
        }
    }

    @Test
    void findByTemplatesAndTimeRangeEmitsLimitOnPgMysqlMariadb() throws SQLException {
        for (DbType dbType : new DbType[] {DbType.POSTGRESQL, DbType.MYSQL, DbType.MARIADB}) {
            String sql = captureFindByTemplatesAndTimeRangeSql(dbType, List.of(1L), null, null, null, 25);
            assertTrue(sql.endsWith(" LIMIT 25"),
                    dbType + " must end with LIMIT 25: " + sql);
        }
    }

    @Test
    void findByTemplatesAndTimeRangePlaceholderCountMatchesIdListSize() throws SQLException {
        String oneId = captureFindByTemplatesAndTimeRangeSql(
                DbType.POSTGRESQL, List.of(1L), null, null, null, 10);
        assertTrue(oneId.contains("template_id IN (?)"),
                "Single-id call must emit IN (?): " + oneId);

        String threeIds = captureFindByTemplatesAndTimeRangeSql(
                DbType.POSTGRESQL, List.of(1L, 2L, 3L), null, null, null, 10);
        assertTrue(threeIds.contains("template_id IN (?,?,?)"),
                "Three-id call must emit IN (?,?,?): " + threeIds);
    }

    @Test
    void findByTemplatesAndTimeRangeKeysetPredicatePresentOnlyWhenCursorSupplied() throws SQLException {
        String noCursor = captureFindByTemplatesAndTimeRangeSql(
                DbType.POSTGRESQL, List.of(1L), null, null, null, 10);
        assertFalse(noCursor.contains("occurred_at < ? OR (occurred_at = ?"),
                "Keyset predicate must be absent when cursor is null: " + noCursor);

        String withCursor = captureFindByTemplatesAndTimeRangeSql(
                DbType.POSTGRESQL, List.of(1L), null, null, KeysetCursor.of(100L, 5L), 10);
        assertTrue(withCursor.contains("AND (occurred_at < ? OR (occurred_at = ? AND id < ?))"),
                "Keyset predicate must be present when cursor is non-null: " + withCursor);
    }

    @Test
    void findByTemplatesAndTimeRangeTimeRangePredicateOmittedWhenNull() throws SQLException {
        String noRange = captureFindByTemplatesAndTimeRangeSql(
                DbType.POSTGRESQL, List.of(1L), null, null, null, 10);
        assertFalse(noRange.contains("occurred_at >="),
                "occurred_at >= must be absent when fromMs is null: " + noRange);
        assertFalse(noRange.contains("occurred_at <"),
                "occurred_at < must be absent when no cursor + no toMsExclusive: " + noRange);

        String onlyFrom = captureFindByTemplatesAndTimeRangeSql(
                DbType.POSTGRESQL, List.of(1L), 100L, null, null, 10);
        assertTrue(onlyFrom.contains("occurred_at >= ?"),
                "occurred_at >= must be present when fromMs is non-null: " + onlyFrom);
        assertFalse(onlyFrom.contains("occurred_at <"),
                "occurred_at < must be absent when toMsExclusive is null + no cursor: " + onlyFrom);

        String onlyTo = captureFindByTemplatesAndTimeRangeSql(
                DbType.POSTGRESQL, List.of(1L), null, 200L, null, 10);
        assertFalse(onlyTo.contains("occurred_at >="),
                "occurred_at >= must be absent when fromMs is null: " + onlyTo);
        assertTrue(onlyTo.contains("occurred_at < ?"),
                "occurred_at < ? must be present when toMsExclusive is non-null: " + onlyTo);
    }

    private static String captureFindByTemplatesAndTimeRangeSql(DbType dbType, List<Long> templateIds,
            Long fromMs, Long toMsExclusive, KeysetCursor cursor, int limit) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(any(String.class), any(Object[].class))).thenReturn(rs);

        new ErrorCatalogDao(jdbcHelper, dbProperties)
                .findByTemplatesAndTimeRange(templateIds, fromMs, toMsExclusive, cursor, limit);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(sqlCaptor.capture(), any(Object[].class));
        return sqlCaptor.getValue();
    }
}
