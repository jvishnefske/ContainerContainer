package org.artifactory.storage.db.version.converter;

import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.mockito.Mock;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
public class IncreaseColumnStatisticsConvertorTest {

    @Mock
    private ChangeLogContext context;
    @Mock
    private Connection connection;
    @Mock
    private DbVersion dbVersion;
    private IncreaseColumnStatisticsConvertor increaseColumnStatisticsConvertor;
    private AutoCloseable autoCloseable;

    @BeforeClass
    void setup() {
        autoCloseable = openMocks(this);
        increaseColumnStatisticsConvertor = spy(new IncreaseColumnStatisticsConvertor(
                "valid_content", "prop_key", executeUnconditional()));
        when(context.getConnection()).thenReturn(connection);
        when(context.getDbType()).thenReturn(POSTGRESQL);
        when(context.getDbVersion()).thenReturn(dbVersion);
    }

    @AfterClass
    void clear() throws Exception {
        autoCloseable.close();
        context.close();
        connection.close();
    }

    @Test
    void shouldReturnTrueForStaticSet() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(2)).thenReturn(1000);
        assertTrue(increaseColumnStatisticsConvertor.areStatisticsSet(connection));
    }

    @Test
    void shouldReturnFalseForNonStaticSet() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(2)).thenReturn(1);
        assertFalse(increaseColumnStatisticsConvertor.areStatisticsSet(connection));
    }

    @Test
    void testIsStaticSetNoValues() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);
        assertFalse(increaseColumnStatisticsConvertor.areStatisticsSet(connection));
    }

    @Test
    void testConverterCompleted() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(stmt);
        when(connection.getMetaData()).thenReturn(mock(DatabaseMetaData.class));
        when(connection.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(2)).thenReturn(1);
        DbConverterResult result = increaseColumnStatisticsConvertor.convert(context);
        assertEquals(result.getStatus(), ConverterApplyStatus.COMPLETED);
    }

    @Test
    void testConverterSkipped() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(stmt);
        when(connection.getMetaData()).thenReturn(mock(DatabaseMetaData.class));
        when(connection.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(2)).thenReturn(1000);
        DbConverterResult result = increaseColumnStatisticsConvertor.convert(context);
        assertEquals(result.getStatus(), ConverterApplyStatus.SKIPPED);
    }

    @Test
    void testConverterFailed() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(any())).thenReturn(stmt);
        when(connection.getMetaData()).thenReturn(mock(DatabaseMetaData.class));
        when(connection.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery()).thenThrow(SQLException.class);
        DbConverterResult result = increaseColumnStatisticsConvertor.convert(context);
        assertEquals(result.getStatus(), ConverterApplyStatus.FAILED);
    }
}