package org.artifactory.storage.db.search.model;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.search.gavc.GavcSearchControls;
import org.artifactory.sapi.search.VfsBoolType;
import org.artifactory.sapi.search.VfsComparatorType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VfsQueryDbImplTest extends ArtifactoryHomeBoundJUnitTest {
    ArtifactoryStorageContext context;

    @Mock
    DbService dbService;

    @BeforeEach
    void setUp() {
        context = mock(ArtifactoryStorageContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        dbService = mock(DbService.class);
        when(context.beanForType(DbService.class)).thenReturn(dbService);
        when(dbService.getDatabaseType()).thenReturn(DbType.ORACLE);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void count() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        when(context.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
        when(jdbcHelper.executeSelectCount(anyString(), any(Object[].class))).thenReturn(1);
        assertEquals(1, new VfsQueryDbImpl().count());
    }

    @Test
    void countFailed() {
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        vfsQueryDb.startGroup();
        String errMessage = "Cannot execute while group still active!";
        GavcSearchControls controls = mock(GavcSearchControls.class);
        when(controls.isSpecific()).thenReturn(true);
        Exception e = assertThrows(IllegalStateException.class, vfsQueryDb::count);
        assertEquals(e.getMessage(), errMessage);
    }

    @ParameterizedTest
    @MethodSource("provider")
    void testExecuteQuery(DbType dbType, String suffix) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        when(context.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
        when(context.beanForType(DbService.class)).thenReturn(dbService);
        when(dbService.getDatabaseType()).thenReturn(dbType);
        ResultSet resultSet = mock(ResultSet.class);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(resultSet);
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        vfsQueryDb.orderByAscending("created");
        vfsQueryDb.execute(10);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(queryCaptor.capture(), any(Object[].class));
        String expectedQuery =
                "SELECT nodes.node_id, nodes.node_type, nodes.repo, nodes.node_path, nodes.node_name FROM nodes WHERE" +
                        "  nodes.repo  != ? AND  nodes.repo  != ? ORDER BY created ASC " + suffix;
        assertEquals(expectedQuery, queryCaptor.getValue());
    }

    @Test
    void executeUsesExplicitJoinChainWhenPropertyAndStatisticFilters() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        when(context.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
        when(context.beanForType(DbService.class)).thenReturn(dbService);
        when(dbService.getDatabaseType()).thenReturn(DbType.ORACLE);
        ResultSet resultSet = mock(ResultSet.class);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(resultSet);
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        vfsQueryDb.prop("foo")
                .comp(VfsComparatorType.EQUAL)
                .val("bar")
                .nextBool(VfsBoolType.AND);
        vfsQueryDb.prop("last_downloaded")
                .comp(VfsComparatorType.LOWER_THAN)
                .val(1770813891581L);
        vfsQueryDb.orderByAscending("created");
        vfsQueryDb.execute(10);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(queryCaptor.capture(), any(Object[].class));
        String sql = queryCaptor.getValue();
        assertTrue(sql.contains(
                        "FROM nodes INNER JOIN node_props ON node_props.node_id = nodes.node_id "
                                + "LEFT JOIN stats ON stats.node_id = nodes.node_id "
                                + "LEFT JOIN stats_remote ON stats_remote.node_id = nodes.node_id"),
                sql);
        assertTrue(!sql.contains("FROM nodes, node_props"), sql);
    }

    @ParameterizedTest
    @MethodSource("provider")
    void testExecuteWithEscapeSymbolQuery(DbType dbType, String escapeSymbol) throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        when(context.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
        when(context.beanForType(DbService.class)).thenReturn(dbService);
        when(dbService.getDatabaseType()).thenReturn(dbType);
        ResultSet resultSet = mock(ResultSet.class);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(resultSet);
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        vfsQueryDb.prop("node_path")
                .comp(VfsComparatorType.CONTAINS)
                .escapeSymbol(escapeSymbol)
                .val("%test_%");
        vfsQueryDb.execute(1);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(queryCaptor.capture(), any(Object[].class));
        String expectedQuery =
                "SELECT nodes.node_id, nodes.node_type, nodes.repo, nodes.node_path, nodes.node_name FROM nodes WHERE" +
                        "  nodes.repo  != ? AND  nodes.repo  != ? AND nodes.node_path LIKE ? ESCAPE '" + escapeSymbol +
                        "'";
        assertTrue(queryCaptor.getValue().contains(expectedQuery));
    }

    static Stream<Arguments> provider() {
        return Stream.of(
                Arguments.of(DbType.ORACLE, "FETCH FIRST 10 ROWS ONLY", "^"),
                Arguments.of(DbType.MYSQL, "LIMIT 10", "#"),
                Arguments.of(DbType.MARIADB, "LIMIT 10", "@")
        );
    }

    @Test
    void testExecuteQueryThrowsSQLException() throws SQLException {
        VfsQueryDbImpl vfsQueryDb = new VfsQueryDbImpl();
        ResultSet resultSet = mock(ResultSet.class);
        ArtifactoryJdbcHelper jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenReturn(resultSet);
        when(context.beanForType(ArtifactoryJdbcHelper.class)).thenReturn(jdbcHelper);
        when(context.beanForType(DbService.class)).thenReturn(dbService);
        when(dbService.getDatabaseType()).thenReturn(DbType.ORACLE);
        when(jdbcHelper.executeSelect(anyString(), any(Object[].class))).thenThrow(new SQLException(""));
        StorageException exception = assertThrows(StorageException.class, () -> vfsQueryDb.execute(10));
        assertTrue(exception.getMessage().contains("Could not execute query"));
    }
}