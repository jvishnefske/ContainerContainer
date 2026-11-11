package org.artifactory.storage.db.version.converter;

import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.version.converter.validconversion.ValidOracleConversionVersion;
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

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v187;
import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Tomer Slutsky
 */
class SuspendOnConditionOracleConvertorTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private ChangeLogContext context;
    @Mock
    private Connection connection;
    private SuspendOnConditionOracleConvertor suspendOnConditionOracleConvertor;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        suspendOnConditionOracleConvertor =
                spy(new SuspendOnConditionOracleConvertor("file", ValidOracleConversionVersion.RAC,
                        executeUnconditional()));
        when(context.getConnection()).thenReturn(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.oracleNodeEventsSequenceIsNoCache, "false");
        autoCloseable.close();
        context.close();
        connection.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCheckIsValidConversionVersion(boolean value) {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.oracleNodeEventsSequenceIsNoCache,
                String.valueOf(value));
        boolean b = suspendOnConditionOracleConvertor.checkIsValidConversionVersion(connection);
        if (!value) {
            assertFalse(b);
        } else {
            assertTrue(b);
        }
    }

    @Test
    void testHandleConvertorExecutionException() {
        DbConverterResult result = new DbConverterResult();
        doReturn(false).when(suspendOnConditionOracleConvertor).checkIsValidConversionVersion(any());
        assertThrows(DbConverterExecutionException.class,
                () -> suspendOnConditionOracleConvertor.handleConvertorExecution(context, result));
    }

    @Test
    void testConvertException() {
        doReturn(false).when(suspendOnConditionOracleConvertor).checkIsValidConversionVersion(any());
        when(context.getDbVersion()).thenReturn(v187);
        DbConverterResult convert = suspendOnConditionOracleConvertor.convert(context);
        assertEquals(ConverterApplyStatus.SUSPENDED, convert.getStatus());
    }
}