package org.artifactory.storage.db.fs.dao;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.rtfs.dto.sync.FileListEntry;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.querybuilder.DerbyQueryBuilder;
import org.jfrog.storage.util.querybuilder.IQueryBuilder;
import org.jfrog.storage.util.querybuilder.MysqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.OracleQueryBuilder;
import org.jfrog.storage.util.querybuilder.PostgresqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.SqlServerQueryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodesDaoUnitTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;

    private NodesDao nodesDao;
    private ArtifactoryJdbcHelper jdbcHelper;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(IQueryBuilder.class)).thenReturn(queryBuilderFor(DbType.POSTGRESQL));
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        nodesDao = new NodesDao(jdbcHelper, dbProperties);
    }

    @AfterEach
    void tearDownContext() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void getNodesByRepo_shouldReturnEmptyList_whenNoRowsExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("empty-repo"))).thenReturn(rs);

        List<Node> result = nodesDao.getNodesByRepo("empty-repo", 100);

        assertTrue(result.isEmpty());
    }

    @Test
    void getNodesByRepo_shouldReturnNodes_whenRowsExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        stubResultSetRow(rs, 1L, true, "test-repo", "folder", "file1.jar");
        when(jdbcHelper.executeSelect(anyString(), eq("test-repo"))).thenReturn(rs);

        List<Node> result = nodesDao.getNodesByRepo("test-repo", 100);

        assertEquals(2, result.size());
        assertEquals("test-repo", result.get(0).getRepo());
    }

    @Test
    void getNodesByRepo_shouldPassRepoKeyToQuery() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("my-repo"))).thenReturn(rs);

        nodesDao.getNodesByRepo("my-repo", 50);

        verify(jdbcHelper).executeSelect(anyString(), eq("my-repo"));
    }

    @Test
    void getNodesByRepo_shouldPropagateException_whenSqlFails() throws SQLException {
        when(jdbcHelper.executeSelect(anyString(), eq("bad-repo")))
                .thenThrow(new SQLException("Connection failed"));

        assertThrows(SQLException.class, () -> nodesDao.getNodesByRepo("bad-repo", 100));
    }

    @Test
    void getNodesByRepo_shouldFilterOutRootFolder() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("test-repo"))).thenReturn(rs);

        nodesDao.getNodesByRepo("test-repo", 10);

        verify(jdbcHelper).executeSelect(
                org.mockito.ArgumentMatchers.contains("depth > 0"),
                eq("test-repo"));
    }

    @Test
    void isEmptyRepo_shouldReturnTrue_whenNoNonRootNodesExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("empty-repo"))).thenReturn(rs);

        assertTrue(nodesDao.isEmptyRepo("empty-repo"));
    }

    @Test
    void isEmptyRepo_shouldReturnFalse_whenNonRootNodesExist() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        stubResultSetRow(rs, 1L, true, "full-repo", "folder", "file.jar");
        when(jdbcHelper.executeSelect(anyString(), eq("full-repo"))).thenReturn(rs);

        assertFalse(nodesDao.isEmptyRepo("full-repo"));
    }

    @Test
    void isEmptyRepo_shouldPropagateException_whenSqlFails() throws SQLException {
        when(jdbcHelper.executeSelect(anyString(), eq("bad-repo")))
                .thenThrow(new SQLException("DB error"));

        assertThrows(SQLException.class, () -> nodesDao.isEmptyRepo("bad-repo"));
    }

    // Valid 40-char hex SHA1s for tests
    private static final String SHA1_A =
            "aaaa000000000000000000000000000000000001";
    private static final String SHA1_B =
            "bbbb000000000000000000000000000000000002";
    private static final String SHA1_C =
            "cccc000000000000000000000000000000000003";

    @Test
    void findReferencedSha1s_shouldReturnEmptySet_whenInputEmpty()
            throws SQLException {
        Set<String> result =
                nodesDao.findReferencedSha1s(Collections.emptyList());

        assertTrue(result.isEmpty());
        verify(jdbcHelper, never()).executeSelect(
                anyString(), any(Object[].class));
    }

    @Test
    void findReferencedSha1s_shouldReturnReferencedSha1s()
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn(SHA1_A.toLowerCase(), SHA1_C.toLowerCase());
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        Set<String> result = nodesDao.findReferencedSha1s(
                List.of(SHA1_A, SHA1_B, SHA1_C));

        assertEquals(2, result.size());
        assertTrue(result.contains(SHA1_A.toLowerCase()));
        assertTrue(result.contains(SHA1_C.toLowerCase()));
        assertFalse(result.contains(SHA1_B.toLowerCase()));
    }

    @Test
    void findReferencedSha1s_postgresqlUsesUnnest()
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        nodesDao.findReferencedSha1s(List.of(SHA1_A));

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(
                queryCaptor.capture(), any(Object[].class));
        String query = queryCaptor.getValue();
        assertTrue(query.contains("unnest"));
        assertTrue(query.contains("char(40)"));
        assertTrue(query.contains("EXISTS"));
        assertTrue(query.contains("repo <> ?"),
                "TRASH_REPO_KEY should be parameterized, not inline");
    }

    @Test
    void findReferencedSha1s_postgresqlPassesArrayParamAndTrashRepoKey()
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        nodesDao.findReferencedSha1s(List.of(SHA1_A, SHA1_B));

        ArgumentCaptor<Object[]> paramsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcHelper).executeSelect(
                anyString(), paramsCaptor.capture());
        Object[] params = paramsCaptor.getValue();
        assertEquals(2, params.length, "Should have array param + TRASH_REPO_KEY");
        String arrayParam = (String) params[0];
        assertTrue(arrayParam.contains(SHA1_A.toLowerCase()));
        assertTrue(arrayParam.contains(SHA1_B.toLowerCase()));
        assertEquals("auto-trashcan", params[1], "Second param should be TRASH_REPO_KEY");
    }

    @Test
    void findReferencedSha1s_mysqlUsesUnionAll()
            throws SQLException {
        ArtifactoryDbProperties mysqlProps =
                mock(ArtifactoryDbProperties.class);
        when(mysqlProps.getDbType()).thenReturn(DbType.MYSQL);
        NodesDao mysqlDao = new NodesDao(jdbcHelper, mysqlProps);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        mysqlDao.findReferencedSha1s(List.of(SHA1_A));

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(
                queryCaptor.capture(), any(Object[].class));
        String query = queryCaptor.getValue();
        assertTrue(query.contains("UNION ALL")
                || query.contains("CAST"));
        assertTrue(query.contains("CHAR(40)"));
    }

    @Test
    void findReferencedSha1s_mssqlUsesOpenjson()
            throws SQLException {
        ArtifactoryDbProperties mssqlProps =
                mock(ArtifactoryDbProperties.class);
        when(mssqlProps.getDbType()).thenReturn(DbType.MSSQL);
        NodesDao mssqlDao = new NodesDao(jdbcHelper, mssqlProps);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        mssqlDao.findReferencedSha1s(List.of(SHA1_A));

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(
                queryCaptor.capture(), any(Object[].class));
        assertTrue(queryCaptor.getValue().contains("OPENJSON"));
    }

    @Test
    void findReferencedSha1s_oracleUsesJsonTableWithVarchar2()
            throws SQLException {
        ArtifactoryDbProperties oracleProps =
                mock(ArtifactoryDbProperties.class);
        when(oracleProps.getDbType()).thenReturn(DbType.ORACLE);
        NodesDao oracleDao = new NodesDao(jdbcHelper, oracleProps);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        oracleDao.findReferencedSha1s(List.of(SHA1_A));

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(
                queryCaptor.capture(), any(Object[].class));
        String query = queryCaptor.getValue();
        assertTrue(query.contains("JSON_TABLE"));
        assertTrue(query.contains("VARCHAR2(40)"));
    }

    @Test
    void findReferencedSha1s_derbyUsesValues()
            throws SQLException {
        ArtifactoryDbProperties derbyProps =
                mock(ArtifactoryDbProperties.class);
        when(derbyProps.getDbType()).thenReturn(DbType.DERBY);
        NodesDao derbyDao = new NodesDao(jdbcHelper, derbyProps);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        derbyDao.findReferencedSha1s(List.of(SHA1_A, SHA1_B));

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(
                queryCaptor.capture(), any(Object[].class));
        String query = queryCaptor.getValue();
        assertTrue(query.contains("VALUES"));
        assertTrue(query.contains("CAST"));
    }

    @Test
    void findReferencedSha1s_jsonDbsPassJsonParamAndTrashRepoKey()
            throws SQLException {
        ArtifactoryDbProperties mssqlProps =
                mock(ArtifactoryDbProperties.class);
        when(mssqlProps.getDbType()).thenReturn(DbType.MSSQL);
        NodesDao mssqlDao = new NodesDao(jdbcHelper, mssqlProps);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        mssqlDao.findReferencedSha1s(List.of(SHA1_A, SHA1_B));

        ArgumentCaptor<Object[]> paramsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcHelper).executeSelect(
                anyString(), paramsCaptor.capture());
        Object[] params = paramsCaptor.getValue();
        assertEquals(2, params.length, "Should have JSON param + TRASH_REPO_KEY");
        String jsonParam = (String) params[0];
        assertTrue(jsonParam.startsWith("["));
        assertTrue(jsonParam.contains(SHA1_A.toLowerCase()));
        assertTrue(jsonParam.contains(SHA1_B.toLowerCase()));
        assertEquals("auto-trashcan", params[1], "Second param should be TRASH_REPO_KEY");
    }

    @Test
    void findReferencedSha1s_shouldReturnEmptyOnBatchFailure()
            throws SQLException {
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class)))
                .thenThrow(new SQLException("Connection failed"));

        Set<String> result =
                nodesDao.findReferencedSha1s(List.of(SHA1_A));

        assertTrue(result.isEmpty());
    }

    @Test
    void findReferencedSha1s_shouldRejectInvalidSha1Format()
            throws SQLException {
        Set<String> result = nodesDao.findReferencedSha1s(
                List.of("invalid", "not-a-sha1", "'; DROP TABLE nodes; --"));

        assertTrue(result.isEmpty());
        verify(jdbcHelper, never()).executeSelect(
                anyString(), any(Object[].class));
    }

    @Test
    void findReferencedSha1s_shouldFilterOutInvalidAndKeepValid()
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(1)).thenReturn(SHA1_A.toLowerCase());
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        Set<String> result = nodesDao.findReferencedSha1s(
                List.of(SHA1_A, "invalid-sha1", "'; DROP TABLE nodes;--"));

        assertEquals(1, result.size());
        assertTrue(result.contains(SHA1_A.toLowerCase()));
    }

    @Test
    void findReferencedSha1s_shouldRejectNullSha1()
            throws SQLException {
        List<String> listWithNull = new ArrayList<>();
        listWithNull.add(SHA1_A);
        listWithNull.add(null);
        listWithNull.add(SHA1_B);

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        nodesDao.findReferencedSha1s(listWithNull);

        ArgumentCaptor<Object[]> paramsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcHelper).executeSelect(
                anyString(), paramsCaptor.capture());
        Object[] params = paramsCaptor.getValue();
        String arrayParam = (String) params[0];
        assertTrue(arrayParam.contains(SHA1_A.toLowerCase()));
        assertTrue(arrayParam.contains(SHA1_B.toLowerCase()));
        assertFalse(arrayParam.contains("null"));
    }

    @Test
    void findReferencedSha1s_filtersNullFromResultSet()
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn(SHA1_A.toLowerCase(), null);
        when(jdbcHelper.executeSelect(anyString(),
                any(Object[].class))).thenReturn(rs);

        Set<String> result =
                nodesDao.findReferencedSha1s(List.of(SHA1_A, SHA1_B));

        assertEquals(1, result.size());
        assertTrue(result.contains(SHA1_A.toLowerCase()));
    }

    @Test
    void getFullSyncEntriesPaged_firstPage_usesRepoPredicateOnly() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("repo-a"), eq(0L))).thenReturn(rs);

        List<FileListEntry> entries = nodesDao.getFullSyncEntriesPaged("repo-a", null, 25);

        assertTrue(entries.isEmpty());
        verify(jdbcHelper).executeSelect(
                ArgumentMatchers.<String>argThat(sql -> sql.contains("nodes.repo = ? AND nodes.created >= ?")),
                eq("repo-a"), eq(0L));
    }

    @Test
    void getFullSyncEntriesPaged_postgresContinuation_usesInclusiveCreatedPredicate() throws SQLException {
        nodesDao = daoForDb(DbType.POSTGRESQL);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("r"), eq(11L))).thenReturn(rs);

        assertTrue(nodesDao.getFullSyncEntriesPaged("r", (Long)11L, 50).isEmpty());

        verify(jdbcHelper).executeSelect(
                ArgumentMatchers.<String>argThat(sql -> sql.contains("nodes.repo = ?")
                        && sql.contains("nodes.created >= ?")
                        && sql.contains("nodes.created ASC, nodes.node_id ASC")),
                eq("r"), eq(11L));
    }

    @Test
    void getFullSyncEntriesPaged_mssqlContinuation_usesInclusiveCreatedPredicate() throws SQLException {
        nodesDao = daoForDb(DbType.MSSQL);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(jdbcHelper.executeSelect(anyString(), eq("r"), eq(11L))).thenReturn(rs);

        assertTrue(nodesDao.getFullSyncEntriesPaged("r", (Long)11L, 50).isEmpty());

        verify(jdbcHelper).executeSelect(
                ArgumentMatchers.<String>argThat(sql -> sql.contains("nodes.repo = ?")
                        && sql.contains("nodes.created >= ?")
                        && sql.contains("created ASC, node_id ASC")),
                eq("r"), eq(11L));
    }

    private NodesDao daoForDb(DbType dbType) {
        when(context.beanForType(IQueryBuilder.class)).thenReturn(queryBuilderFor(dbType));
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        return new NodesDao(jdbcHelper, dbProperties);
    }

    private static IQueryBuilder queryBuilderFor(DbType type) {
        return switch (type) {
            case DERBY -> new DerbyQueryBuilder();
            case MARIADB, MYSQL -> new MysqlQueryBuilder();
            case ORACLE -> new OracleQueryBuilder();
            case MSSQL -> new SqlServerQueryBuilder();
            case POSTGRESQL -> new PostgresqlQueryBuilder();
        };
    }

    private void stubResultSetRow(ResultSet rs, long nodeId, boolean isFile,
            String repo, String path, String name) throws SQLException {
        when(rs.getLong("node_id")).thenReturn(nodeId);
        when(rs.getBoolean("node_type")).thenReturn(isFile);
        when(rs.getString("repo")).thenReturn(repo);
        when(rs.getString("node_path")).thenReturn(path);
        when(rs.getString("node_name")).thenReturn(name);
        when(rs.getShort("depth")).thenReturn((short) 2);
        when(rs.getLong("created")).thenReturn(0L);
        when(rs.getString("created_by")).thenReturn("test");
        when(rs.getLong("modified")).thenReturn(0L);
        when(rs.getString("modified_by")).thenReturn("test");
        when(rs.getLong("updated")).thenReturn(0L);
        when(rs.getLong("bin_length")).thenReturn(1024L);
        when(rs.getString("sha1_actual")).thenReturn("abc123");
        when(rs.getString("sha1_original")).thenReturn("abc123");
        when(rs.getString("md5_actual")).thenReturn("def456");
        when(rs.getString("md5_original")).thenReturn("def456");
        when(rs.getString("sha256")).thenReturn("sha256hash");
    }
}
