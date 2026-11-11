package org.artifactory.jfbus.outbox;

import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.eventing.jfbus.outbox.bridge.DatabaseType;
import org.jfrog.eventing.jfbus.outbox.bridge.ResultSetExtractor;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DataSourceUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactoryOutboxDatabaseBridgeTest {

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;

    @Mock
    private ResultSet resultSet;

    @Mock
    private JFrogDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    private ArtifactoryOutboxDatabaseBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new ArtifactoryOutboxDatabaseBridge(jdbcHelper, DbType.POSTGRESQL);
    }

    // ===== executeSelect =====

    @Test
    void executeSelect_delegatesToJdbcHelperAndPassesResultSetToExtractor() throws SQLException {
        String sql = "SELECT * FROM jfbus_outbox_events WHERE id = ?";
        Object[] params = {42};
        String expected = "extracted";

        when(jdbcHelper.executeSelectForSmallResults(sql, params)).thenReturn(resultSet);
        ResultSetExtractor<String> extractor = rs -> expected;

        String result = bridge.executeSelect(extractor, sql, params);

        assertEquals(expected, result);
        verify(jdbcHelper).executeSelectForSmallResults(sql, params);
    }

    @Test
    void executeSelect_closesResultSetAfterExtraction() throws SQLException {
        String sql = "SELECT 1";
        when(jdbcHelper.executeSelectForSmallResults(sql)).thenReturn(resultSet);

        bridge.executeSelect(rs -> null, sql);

        verify(resultSet).close();
    }

    @Test
    void executeSelect_closesResultSetEvenWhenExtractorThrows() throws SQLException {
        String sql = "SELECT 1";
        when(jdbcHelper.executeSelectForSmallResults(sql)).thenReturn(resultSet);
        ResultSetExtractor<String> failingExtractor = rs -> { throw new SQLException("extraction failed"); };

        assertThrows(SQLException.class, () -> bridge.executeSelect(failingExtractor, sql));

        verify(resultSet).close();
    }

    // ===== executeUpdate =====

    @Test
    void executeUpdate_delegatesToJdbcHelperAndReturnsRowCount() throws SQLException {
        String sql = "DELETE FROM jfbus_outbox_events WHERE id = ?";
        Object[] params = {1};
        when(jdbcHelper.executeUpdate(sql, params)).thenReturn(3);

        int result = bridge.executeUpdate(sql, params);

        assertEquals(3, result);
        verify(jdbcHelper).executeUpdate(sql, params);
    }

    // ===== executeUpdateWithTimeout =====

    @Test
    void executeUpdateWithTimeout_setsQueryTimeoutAndExecutes() throws SQLException {
        String sql = "DROP TABLE IF EXISTS jfbus_outbox_events_p_old";
        int timeoutSec = 30;

        when(jdbcHelper.getDataSource()).thenReturn(dataSource);
        when(connection.prepareStatement(sql)).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        try (MockedStatic<DataSourceUtils> dsUtils = mockStatic(DataSourceUtils.class)) {
            dsUtils.when(() -> DataSourceUtils.getConnection(dataSource)).thenReturn(connection);

            int result = bridge.executeUpdateWithTimeout(timeoutSec, sql);

            assertEquals(1, result);
            verify(preparedStatement).setQueryTimeout(timeoutSec);
            verify(preparedStatement).executeUpdate();
            dsUtils.verify(() -> DataSourceUtils.releaseConnection(connection, dataSource));
        }
    }

    @Test
    void executeUpdateWithTimeout_bindsAllParams() throws SQLException {
        String sql = "UPDATE t SET col = ? WHERE id = ?";
        int timeoutSec = 10;
        Object[] params = {"value", 99};

        when(jdbcHelper.getDataSource()).thenReturn(dataSource);
        when(connection.prepareStatement(sql)).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        try (MockedStatic<DataSourceUtils> dsUtils = mockStatic(DataSourceUtils.class)) {
            dsUtils.when(() -> DataSourceUtils.getConnection(dataSource)).thenReturn(connection);

            bridge.executeUpdateWithTimeout(timeoutSec, sql, params);

            verify(preparedStatement).setObject(1, "value");
            verify(preparedStatement).setObject(2, 99);
        }
    }

    @Test
    void executeUpdateWithTimeout_releasesConnectionEvenOnSqlException() throws SQLException {
        String sql = "INVALID SQL";

        when(jdbcHelper.getDataSource()).thenReturn(dataSource);
        when(connection.prepareStatement(sql)).thenThrow(new SQLException("bad sql"));

        try (MockedStatic<DataSourceUtils> dsUtils = mockStatic(DataSourceUtils.class)) {
            dsUtils.when(() -> DataSourceUtils.getConnection(dataSource)).thenReturn(connection);

            assertThrows(SQLException.class, () -> bridge.executeUpdateWithTimeout(5, sql));

            dsUtils.verify(() -> DataSourceUtils.releaseConnection(connection, dataSource));
        }
    }

    // ===== executeBatch =====

    @Test
    void executeBatch_delegatesToJdbcHelperExecuteBulkUpdate() throws SQLException {
        String sql = "INSERT INTO jfbus_outbox_events VALUES (?, ?)";
        List<Object[]> batchParams = List.of(new Object[]{1, "a"}, new Object[]{2, "b"});

        bridge.executeBatch(sql, batchParams);

        verify(jdbcHelper).executeBulkUpdate(sql, batchParams);
    }

    // ===== getDatabaseType / DbType mapping =====

    static Stream<Arguments> dbTypeMappings() {
        return Stream.of(
                Arguments.of(DbType.POSTGRESQL, DatabaseType.POSTGRESQL),
                Arguments.of(DbType.MYSQL,      DatabaseType.MYSQL),
                Arguments.of(DbType.MARIADB,    DatabaseType.MARIADB),
                Arguments.of(DbType.ORACLE,     DatabaseType.ORACLE),
                Arguments.of(DbType.MSSQL,      DatabaseType.MSSQL),
                Arguments.of(DbType.DERBY,      DatabaseType.DERBY)
        );
    }

    @ParameterizedTest
    @MethodSource("dbTypeMappings")
    void getDatabaseType_mapsEachDbTypeCorrectly(DbType dbType, DatabaseType expected) {
        ArtifactoryOutboxDatabaseBridge b = new ArtifactoryOutboxDatabaseBridge(jdbcHelper, dbType);
        assertEquals(expected, b.getDatabaseType());
    }

    @Test
    void getDatabaseType_returnsGenericForNull() {
        ArtifactoryOutboxDatabaseBridge b = new ArtifactoryOutboxDatabaseBridge(jdbcHelper, null);
        assertEquals(DatabaseType.GENERIC, b.getDatabaseType());
    }
}
