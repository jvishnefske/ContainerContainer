package org.artifactory.storage.db.devopsagent.errorcatalog.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.AgenticSearchCriteria;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.TemplateUpsertRequest;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the per-DB SQL string emitted by pagination, UPSERT and FTS code paths in
 * {@link ErrorCatalogTemplateDao}. The behavioural itest runs only on the active CI DB lane,
 * so a syntax bug in any non-matching dialect rides through unless captured here.
 */
class ErrorCatalogTemplateDaoSqlShapeUnitTest {

    @Test
    void searchTemplatesEmitsLimitBeforeOffsetOnMysql() throws SQLException {
        String sql = captureSearchSql(DbType.MYSQL, 10, 20);
        assertOrderedTokensPresent(sql, " LIMIT 10", " OFFSET 20");
        assertFalse(sql.matches("(?s).*OFFSET 20.*LIMIT 10.*"),
                "MySQL must NOT emit OFFSET before LIMIT — would be a parse error: " + sql);
    }

    @Test
    void searchTemplatesEmitsLimitBeforeOffsetOnMariadb() throws SQLException {
        String sql = captureSearchSql(DbType.MARIADB, 10, 20);
        assertOrderedTokensPresent(sql, " LIMIT 10", " OFFSET 20");
        assertFalse(sql.matches("(?s).*OFFSET 20.*LIMIT 10.*"),
                "MariaDB must NOT emit OFFSET before LIMIT — would be a parse error: " + sql);
    }

    @Test
    void searchTemplatesEmitsLimitBeforeOffsetOnPostgres() throws SQLException {
        String sql = captureSearchSql(DbType.POSTGRESQL, 10, 20);
        assertOrderedTokensPresent(sql, " LIMIT 10", " OFFSET 20");
    }

    @Test
    void searchTemplatesEmitsOffsetBeforeFetchFirstOnOracle() throws SQLException {
        String sql = captureSearchSql(DbType.ORACLE, 10, 20);
        assertOrderedTokensPresent(sql, " OFFSET 20 ROWS", " FETCH FIRST 10 ROWS ONLY");
    }

    @Test
    void searchTemplatesEmitsOffsetBeforeFetchNextOnMssql() throws SQLException {
        String sql = captureSearchSql(DbType.MSSQL, 10, 20);
        assertOrderedTokensPresent(sql, " OFFSET 20 ROWS", " FETCH NEXT 10 ROWS ONLY");
    }

    @Test
    void searchTemplatesEmitsOffsetBeforeFetchFirstOnDerby() throws SQLException {
        String sql = captureSearchSql(DbType.DERBY, 10, 20);
        assertOrderedTokensPresent(sql, " OFFSET 20 ROWS", " FETCH FIRST 10 ROWS ONLY");
    }

    @Test
    void searchTemplatesOmitsOffsetClauseWhenOffsetIsZero() throws SQLException {
        String mysqlSql = captureSearchSql(DbType.MYSQL, 10, 0);
        assertTrue(mysqlSql.contains(" LIMIT 10"), mysqlSql);
        assertFalse(mysqlSql.contains(" OFFSET "),
                "MySQL must not emit an OFFSET clause when offset == 0: " + mysqlSql);

        String oracleSql = captureSearchSql(DbType.ORACLE, 10, 0);
        assertTrue(oracleSql.contains(" FETCH FIRST 10 ROWS ONLY"), oracleSql);
        assertFalse(oracleSql.contains(" OFFSET "),
                "Oracle must not emit an OFFSET clause when offset == 0: " + oracleSql);
    }

    @Test
    void searchTemplatesEmitsOffsetZeroOnMssqlEvenWhenOffsetIsZero() throws SQLException {
        String sql = captureSearchSql(DbType.MSSQL, 10, 0);
        assertOrderedTokensPresent(sql, " OFFSET 0 ROWS", " FETCH NEXT 10 ROWS ONLY");
    }

    @Test
    void upsertEmitsOnConflictDoUpdateOnPostgres() throws SQLException {
        String sql = captureUpsertSql(DbType.POSTGRESQL);
        assertTrue(sql.contains("ON CONFLICT (signature_hash) DO UPDATE SET"),
                "Postgres UPSERT must use ON CONFLICT … DO UPDATE: " + sql);
        assertTrue(sql.contains("EXCLUDED.last_occurrence_at"),
                "Postgres UPSERT must reference EXCLUDED.* in DO UPDATE: " + sql);
    }

    @Test
    void upsertEmitsOnDuplicateKeyUpdateOnMysql() throws SQLException {
        String sql = captureUpsertSql(DbType.MYSQL);
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"),
                "MySQL UPSERT must use ON DUPLICATE KEY UPDATE: " + sql);
        assertTrue(sql.contains("VALUES(last_occurrence_at)"),
                "MySQL UPSERT must reference VALUES(col) in the UPDATE clause: " + sql);
    }

    @Test
    void upsertEmitsOnDuplicateKeyUpdateOnMariadb() throws SQLException {
        String sql = captureUpsertSql(DbType.MARIADB);
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"),
                "MariaDB UPSERT must use ON DUPLICATE KEY UPDATE: " + sql);
    }

    @Test
    void upsertEmitsMergeIntoOnOracle() throws SQLException {
        String sql = captureUpsertSql(DbType.ORACLE);
        assertOrderedTokensPresent(sql,
                "MERGE INTO ", " USING (", " FROM dual", " WHEN MATCHED THEN UPDATE",
                " WHEN NOT MATCHED THEN INSERT");
    }

    @Test
    void upsertEmitsMergeIntoOnMssql() throws SQLException {
        String sql = captureUpsertSql(DbType.MSSQL);
        assertOrderedTokensPresent(sql,
                "MERGE INTO ", " USING (", " VALUES (", " WHEN MATCHED THEN UPDATE",
                " WHEN NOT MATCHED THEN INSERT");
    }

    @Test
    void upsertOnDerbyTriesUpdateFirstThenInsert() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(jdbcHelper.executeUpdate(anyString(), any(Object[].class))).thenReturn(0).thenReturn(1);

        new ErrorCatalogTemplateDao(jdbcHelper, dbProperties).upsertBatch(List.of(sampleRequest()));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper, times(2)).executeUpdate(sqlCaptor.capture(), any(Object[].class));
        List<String> statements = sqlCaptor.getAllValues();
        assertTrue(statements.get(0).startsWith("UPDATE error_catalog_templates SET"),
                "Derby UPSERT must issue an UPDATE first, got: " + statements.get(0));
        assertTrue(statements.get(1).startsWith("INSERT INTO error_catalog_templates"),
                "Derby UPSERT must follow with an INSERT when UPDATE matched zero rows, got: "
                        + statements.get(1));
    }

    @Test
    void upsertOnDerbyRetriesUpdateWhenInsertHitsUkRace() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(jdbcHelper.executeUpdate(anyString(), any(Object[].class)))
                .thenReturn(0)
                .thenThrow(new SQLException("dup", "23505"))
                .thenReturn(1);

        new ErrorCatalogTemplateDao(jdbcHelper, dbProperties).upsertBatch(List.of(sampleRequest()));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper, times(3)).executeUpdate(sqlCaptor.capture(), any(Object[].class));
        List<String> statements = sqlCaptor.getAllValues();
        assertTrue(statements.get(0).startsWith("UPDATE error_catalog_templates SET"));
        assertTrue(statements.get(1).startsWith("INSERT INTO error_catalog_templates"));
        assertTrue(statements.get(2).startsWith("UPDATE error_catalog_templates SET"));
    }

    @Test
    void upsertBatchSwallowsNonUkSqlExceptionOnDerby() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);
        // UPDATE finds no row → INSERT → non-UK failure (e.g. connection lost)
        when(jdbcHelper.executeUpdate(anyString(), any(Object[].class)))
                .thenReturn(0)
                .thenThrow(new SQLException("connection lost", "08006"));

        assertDoesNotThrow(() -> new ErrorCatalogTemplateDao(jdbcHelper, dbProperties)
                        .upsertBatch(List.of(sampleRequest())),
                "upsertBatch must catch and log StorageException, not propagate it");
        verify(jdbcHelper, times(2)).executeUpdate(anyString(), any(Object[].class));
    }

    @Test
    void upsertBatchContinuesWhenDeadlockThrownOnDuplicateSignature() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        // s1 appears twice — second upsert for the same signature races with another node and deadlocks
        when(jdbcHelper.executeUpdate(anyString(), any(Object[].class)))
                .thenReturn(1)
                .thenThrow(new SQLException("deadlock detected", "40P01"))
                .thenReturn(1);

        ErrorCatalogTemplateDao dao = new ErrorCatalogTemplateDao(jdbcHelper, dbProperties);
        List<TemplateUpsertRequest> batch = List.of(sampleRequest("s1"), sampleRequest("s1"), sampleRequest("s2"));

        assertDoesNotThrow(() -> dao.upsertBatch(batch), "deadlock on a duplicate signature must not abort the batch");
        verify(jdbcHelper, times(3)).executeUpdate(anyString(), any(Object[].class));
    }

    @Test
    void upsertBatchContinuesAndDoesNotThrowWhenOneRowFails() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        // Second call simulates a transient DB failure (e.g. constraint violation, deadlock)
        when(jdbcHelper.executeUpdate(anyString(), any(Object[].class)))
                .thenReturn(1)
                .thenThrow(new SQLException("deadlock detected", "40P01"))
                .thenReturn(1);

        ErrorCatalogTemplateDao dao = new ErrorCatalogTemplateDao(jdbcHelper, dbProperties);
        List<TemplateUpsertRequest> batch = List.of(sampleRequest("s1"), sampleRequest("s2"), sampleRequest("s3"));

        assertDoesNotThrow(() -> dao.upsertBatch(batch), "upsertBatch must not propagate per-row failures");
        // All three rows must be attempted — a failure on s2 must not abort s3
        verify(jdbcHelper, times(3)).executeUpdate(anyString(), any(Object[].class));
    }

    @Test
    void searchByMessageUsesTsqueryOnPostgres() throws SQLException {
        String sql = captureSearchByMessageSql(DbType.POSTGRESQL, "needle");
        assertTrue(sql.contains("message_tsv @@ websearch_to_tsquery('english', ?)"),
                "Postgres FTS must use websearch_to_tsquery: " + sql);
    }

    @Test
    void searchByMessageUsesMatchAgainstOnMysql() throws SQLException {
        String sql = captureSearchByMessageSql(DbType.MYSQL, "needle");
        assertTrue(sql.contains("MATCH(template_message) AGAINST(? IN NATURAL LANGUAGE MODE)"),
                "MySQL FTS must use MATCH … AGAINST: " + sql);
    }

    @Test
    void searchByMessageFallsBackToLowerLikeOnDerby() throws SQLException {
        String sql = captureSearchByMessageSql(DbType.DERBY, "Needle");
        assertTrue(sql.contains("LOWER(template_message) LIKE ?"),
                "Non-FTS dialect must use case-insensitive LIKE: " + sql);
    }

    @Test
    void searchByMessageOmitsTextClauseWhenSearchTextBlank() throws SQLException {
        String sql = captureSearchByMessageSql(DbType.MYSQL, "");
        assertFalse(sql.contains("MATCH(template_message)"),
                "Blank searchText must skip the FTS clause: " + sql);
        assertFalse(sql.contains("LIKE ?"),
                "Blank searchText must skip the LIKE fallback too: " + sql);
    }

    private static String captureSearchSql(DbType dbType, int limit, int offset) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(any(String.class), any(Object[].class))).thenReturn(rs);

        ErrorCatalogTemplateDao dao = new ErrorCatalogTemplateDao(jdbcHelper, dbProperties);
        AgenticSearchCriteria criteria = new AgenticSearchCriteria(
                List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null);
        dao.searchTemplates(criteria, limit, offset);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(sqlCaptor.capture(), any(Object[].class));
        return sqlCaptor.getValue();
    }

    private static String captureUpsertSql(DbType dbType) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        when(jdbcHelper.executeUpdate(anyString(), any(Object[].class))).thenReturn(1);

        new ErrorCatalogTemplateDao(jdbcHelper, dbProperties).upsertBatch(List.of(sampleRequest()));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeUpdate(sqlCaptor.capture(), any(Object[].class));
        return sqlCaptor.getValue();
    }

    private static String captureSearchByMessageSql(DbType dbType, String searchText) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(any(String.class), any(Object[].class))).thenReturn(rs);

        new ErrorCatalogTemplateDao(jdbcHelper, dbProperties)
                .searchByMessage(new SearchCriteria(searchText, null, null), null, 10);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(sqlCaptor.capture(), any(Object[].class));
        return sqlCaptor.getValue();
    }

    private static TemplateUpsertRequest sampleRequest() {
        return sampleRequest("h");
    }

    private static TemplateUpsertRequest sampleRequest(String signatureHash) {
        long now = 1_700_000_000_000L;
        return new TemplateUpsertRequest(signatureHash, "ERROR", "c.A", null, "m", 1L, now, now);
    }

    private static void assertOrderedTokensPresent(String sql, String... tokens) {
        int previousIdx = -1;
        for (String token : tokens) {
            int idx = sql.indexOf(token);
            assertTrue(idx >= 0, "Expected '" + token + "' in SQL: " + sql);
            assertTrue(idx > previousIdx,
                    "Expected token '" + token + "' to appear after the previous token in SQL: " + sql);
            previousIdx = idx;
        }
    }
}
