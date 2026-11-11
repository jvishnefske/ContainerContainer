package org.artifactory.storage.db.fs.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockingLockDaoTest {

    private static final String LOCK_KEY = "testLock";
    private BlockingLockDao blockingLockDao;
    @Mock
    private ArtifactoryDbProperties dbProperties;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ArtifactoryJdbcHelper jdbcHelper;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ArtifactoryHome artifactoryHome;

    @BeforeEach
    void setUp() throws SQLException {
        blockingLockDao = spy(new BlockingLockDao(LOCK_KEY, dbProperties));
        ReflectionTestUtils.setField(blockingLockDao, "jdbcHelper", jdbcHelper);
        when(jdbcHelper.getDataSource().getConnection()).thenReturn(connection);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    void lockBlocking_acquiresLockSuccessfully() throws SQLException, IOException {
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        when(artifactoryHome.getDBProperties()).thenReturn(dbProperties);
        doReturn(jdbcHelper).when(blockingLockDao).createJdbcHelper();
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(mock(ResultSet.class));

        assertDoesNotThrow(() -> blockingLockDao.lockBlocking());

        verify(jdbcHelper).close();
        verify(jdbcHelper).executeUpdate(anyString(), any());
        verify(connection).setAutoCommit(false);
        verify(preparedStatement).executeQuery();
    }

    @Test
    void releaseLock_releasesPreviouslyAcquiredLock() throws SQLException, IOException {
        lockBlocking_acquiresLockSuccessfully();
        when(connection.isValid(anyInt())).thenReturn(true);

        assertDoesNotThrow(() -> blockingLockDao.releaseLock());

        verify(connection).commit();
        verify(jdbcHelper, times(2)).close();
    }

    @Test
    void createLockEntryFails_throwsSQLException() throws SQLException {
        doReturn(jdbcHelper).when(blockingLockDao).createJdbcHelper();
        when(jdbcHelper.executeUpdate(anyString(), any())).thenThrow(
                new SQLException("Failed to create lock entry"));
        when(jdbcHelper.executeSelect(eq("SELECT lock_key FROM distributed_blocking_locks WHERE lock_key=?"),
                anyString()))
                .thenReturn(mock(ResultSet.class));

        assertThrows(SQLException.class, () -> blockingLockDao.lockBlocking());
    }

    @Test
    void createLockEntryFails_AlreadyExists_doesNotThrow() throws SQLException, IOException {
        when(jdbcHelper.executeUpdate(anyString(), any())).thenThrow(
                new SQLException("Failed to create lock entry"));
        ResultSet resultSet = mock(ResultSet.class);
        when(jdbcHelper.executeSelect("SELECT lock_key FROM distributed_blocking_locks WHERE lock_key=?", LOCK_KEY))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        lockBlocking_acquiresLockSuccessfully();
    }

    @Test
    void releaseLock_withoutAcquiringDoesNotThrow() {
        assertDoesNotThrow(() -> blockingLockDao.releaseLock());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void constructor_withNullOrEmptyKey_throwsException(String lockKey) {
        assertThrows(IllegalArgumentException.class, () -> new BlockingLockDao(lockKey, dbProperties).lockBlocking());
    }

    @Test
    void constructor_withNullDbProperties_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new BlockingLockDao(LOCK_KEY, null).lockBlocking());
    }
}