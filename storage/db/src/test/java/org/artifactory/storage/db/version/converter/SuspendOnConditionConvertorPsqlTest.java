package org.artifactory.storage.db.version.converter;

import org.artifactory.storage.db.version.converter.validconversion.ValidPsqlConversionVersion;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v182;
import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Tomer Slutsky
 */
class SuspendOnConditionConvertorPsqlTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private ChangeLogContext context;
    @Mock
    private Connection connection;
    private SuspendOnConditionPsqlDbConvertor suspendOnConditionPsqlDbConvertor;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        suspendOnConditionPsqlDbConvertor =
                spy(new SuspendOnConditionPsqlDbConvertor("file", ValidPsqlConversionVersion.VALID_VERSION,
                        executeUnconditional()));
        when(context.getConnection()).thenReturn(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
        context.close();
        connection.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCheckIsValidConversionVersion(boolean hasNext) throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(hasNext);
        when(rs.getLong(any())).thenReturn(100L);
        boolean b = suspendOnConditionPsqlDbConvertor.checkIsValidConversionVersion(connection);
        if (!hasNext) {
            assertFalse(b);
        } else {
            assertTrue(b);
        }
    }

    @Test
    void testCheckIsValidConversionVersionBadVersion() throws SQLException {
        SuspendOnConditionPsqlDbConvertor convertor =
                new SuspendOnConditionPsqlDbConvertor("file", ValidPsqlConversionVersion.PSQL_10,
                        executeUnconditional());
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong(any())).thenReturn(900L);
        boolean b = convertor.checkIsValidConversionVersion(connection);
        assertFalse(b);
    }

    @Test
    void testCheckIsValidConversionVersionException() throws SQLException {
        SuspendOnConditionPsqlDbConvertor convertor =
                new SuspendOnConditionPsqlDbConvertor("file", ValidPsqlConversionVersion.PSQL_10,
                        executeUnconditional());
        when(connection.prepareStatement(any())).thenThrow(SQLException.class);
        assertFalse(convertor.checkIsValidConversionVersion(connection));
    }

    @Test
    void testHandleConvertorExecutionException() {
        DbConverterResult result = new DbConverterResult();
        doReturn(false).when(suspendOnConditionPsqlDbConvertor).checkIsValidConversionVersion(any());
        assertThrows(DbConverterExecutionException.class,
                () -> suspendOnConditionPsqlDbConvertor.handleConvertorExecution(context, result));
    }

    @Test
    void testConvertException() {
        doReturn(false).when(suspendOnConditionPsqlDbConvertor).checkIsValidConversionVersion(any());
        when(context.getDbVersion()).thenReturn(v182);
        DbConverterResult convert = suspendOnConditionPsqlDbConvertor.convert(context);
        assertEquals(ConverterApplyStatus.SUSPENDED, convert.getStatus());
    }

}