package org.artifactory.storage.db.version;

import org.jfrog.converter.DbConverterRuntimeException;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiPredicate;

import static org.artifactory.storage.db.version.OracleDbVersionPredicates.oracleIndexNotExistsByName;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the Oracle function-based index existence guard: it must skip non-Oracle databases, detect the index by
 * name via {@code user_indexes}, and fail (rather than assume the index is absent) when the catalog lookup breaks.
 *
 * @author Arko Jyoti De
 */
class OracleDbVersionPredicatesTest {

    private static final String INDEX_NAME = "node_prop_nuget_lower_idx";

    @Test
    void shouldRunConverterWithoutQueryingCatalogOnNonOracleDb() throws SQLException {
        Connection connection = mock(Connection.class);

        assertTrue(oracleIndexNotExistsByName(INDEX_NAME).test(connection, DbType.POSTGRESQL));
        verifyNoInteractions(connection);
    }

    @Test
    void shouldRunConverterWhenIndexIsAbsentOnOracle() throws SQLException {
        Connection connection = connectionReturning(false);

        assertTrue(oracleIndexNotExistsByName(INDEX_NAME).test(connection, DbType.ORACLE));
    }

    @Test
    void shouldSkipConverterWhenIndexAlreadyExistsOnOracle() throws SQLException {
        Connection connection = connectionReturning(true);

        assertFalse(oracleIndexNotExistsByName(INDEX_NAME).test(connection, DbType.ORACLE));
    }

    @Test
    void shouldLookUpIndexNameInUpperCase() throws SQLException {
        PreparedStatement statement = statementReturning(false);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        oracleIndexNotExistsByName(INDEX_NAME).test(connection, DbType.ORACLE);

        verify(statement).setString(1, INDEX_NAME.toUpperCase());
    }

    @Test
    void shouldFailWhenCatalogLookupFails() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("no access to user_indexes"));

        BiPredicate<Connection, DbType> predicate = oracleIndexNotExistsByName(INDEX_NAME);

        assertThrows(DbConverterRuntimeException.class, () -> predicate.test(connection, DbType.ORACLE));
    }

    private static Connection connectionReturning(boolean indexFound) throws SQLException {
        PreparedStatement statement = statementReturning(indexFound);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        return connection;
    }

    private static PreparedStatement statementReturning(boolean indexFound) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(indexFound);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeQuery()).thenReturn(resultSet);
        return statement;
    }
}
