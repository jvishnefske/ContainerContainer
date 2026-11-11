package org.artifactory.storage.jobs.async.database;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.locks.service.HaInitLock;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterRuntimeException;
import org.jfrog.converter.DbDetails;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.util.DbConverterUtil;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatabaseConverterRunnableTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ArtifactoryHome artifactoryHome;
    @Mock
    private JdbcHelper jdbcHelper;
    @Mock
    private HaInitLock haInitLock;
    private MockedStatic<DbConverterUtil> dbConverterUtilMockedStatic;

    private DatabaseConverterRunnable databaseConverterRunnable;

    @BeforeEach
    public void setUp() {
        databaseConverterRunnable =
                new DatabaseConverterRunnable(artifactoryHome, mock(CompoundVersionDetails.class), jdbcHelper,
                        haInitLock);
        dbConverterUtilMockedStatic = mockStatic(DbConverterUtil.class);
    }

    @AfterEach
    void tearDown() {
        dbConverterUtilMockedStatic.close();
    }

    @Test
    void testRun_happyFlow() {
        when(haInitLock.acquire()).thenReturn(true);

        dbConverterUtilMockedStatic.when(
                        () -> DbConverterUtil.calculateDbVersionsForExecution(any(DbDetails.class), any(),
                                any(DbVersion[].class), any()))
                .thenReturn(List.of(mock(DbVersion.class)));

        databaseConverterRunnable.run();

        dbConverterUtilMockedStatic.verify(
                () -> DbConverterUtil.executeConverters(any(DbDetails.class), anyList(), any(), any()));
    }

    @Test
    void testRun_ExecuteConvertersThrowsException() {
        when(haInitLock.acquire()).thenReturn(true);
        when(artifactoryHome.isHaConfigured()).thenReturn(true);

        dbConverterUtilMockedStatic.when(
                        () -> DbConverterUtil.calculateDbVersionsForExecution(any(DbDetails.class), any(),
                                any(DbVersion[].class), any()))
                .thenReturn(List.of(mock(DbVersion.class)));

        dbConverterUtilMockedStatic.when(
                () -> DbConverterUtil.executeConverters(any(DbDetails.class), anyList(), any(), any())
                ).thenThrow(new RuntimeException("Thrown by test"));

        DbConverterRuntimeException exception = assertThrows(DbConverterRuntimeException.class,
                databaseConverterRunnable::run);

        assertEquals("Thrown by test", exception.getMessage());
    }

    @Test
    void testRun_noConverters() {
        when(haInitLock.acquire()).thenReturn(true);

        dbConverterUtilMockedStatic.when(
                () -> DbConverterUtil.calculateDbVersionsForExecution(any(DbDetails.class), anyString(),
                        any(DbVersion[].class), any(DbVersion[].class))).thenReturn(List.of());

        databaseConverterRunnable.run();

        dbConverterUtilMockedStatic.verify(() ->
                DbConverterUtil.calculateDbVersionsForExecution(any(DbDetails.class), any(),
                        any(DbVersion[].class), any(DbVersion[].class)));
        dbConverterUtilMockedStatic.verifyNoMoreInteractions();
    }

    @Test
    void testRun_lockNotAcquired() {
        when(haInitLock.acquire()).thenReturn(false);

        databaseConverterRunnable.run();

        verify(haInitLock).acquire();
        dbConverterUtilMockedStatic.verifyNoMoreInteractions();
    }

    @Test
    void testGetDbVersions_databaseException() {
        dbConverterUtilMockedStatic.when(
                        () -> DbConverterUtil.calculateDbVersionsForExecution(any(DbDetails.class), any(),
                                any(DbVersion[].class), any()))
                .thenThrow(new DbConverterExecutionException("Execution exception", new SQLException("SQL error")));

        DbConverterRuntimeException exception = assertThrows(DbConverterRuntimeException.class,
                () -> databaseConverterRunnable.getDbVersions(DbType.POSTGRESQL, jdbcHelper));

        assertEquals("Error during dbVersions calculations", exception.getMessage());
    }
}