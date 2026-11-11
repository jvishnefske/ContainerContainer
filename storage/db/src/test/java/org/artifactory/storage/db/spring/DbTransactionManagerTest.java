package org.artifactory.storage.db.spring;

import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionFactory;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class DbTransactionManagerTest {

    private DataSource dataSource;
    private StorageSessionFactory sessionFactory;
    private DbTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        sessionFactory = mock(StorageSessionFactory.class);
        transactionManager = spy(new DbTransactionManager(dataSource, sessionFactory));
        doNothing().when(transactionManager).superDoCleanupAfterCompletion(any());
    }

    @AfterEach
    void tearDown() {
        StorageSessionHolder.removeSession();
    }

    @Test
    void doCleanupAfterCompletion_noSession_logsWarning() {
        transactionManager.doCleanupAfterCompletion(new Object());
    }

    @Test
    void doCleanupAfterCompletion_withSession_releasesResources() {
        StorageSession session = mock(StorageSession.class);
        StorageSessionHolder.setSession(session);

        transactionManager.doCleanupAfterCompletion(new Object());

        verify(session).releaseResources();
        verify(session).afterConnectionReleased();
    }

    @Test
    void doCleanupAfterCompletion_releaseResourcesThrows_continuesAndCallsAfterConnectionReleased() {
        StorageSession session = mock(StorageSession.class);
        doThrow(new RuntimeException("Test exception")).when(session).releaseResources();
        StorageSessionHolder.setSession(session);

        transactionManager.doCleanupAfterCompletion(new Object());

        verify(session).releaseResources();
        verify(session).afterConnectionReleased();
    }

    @Test
    void doCleanupAfterCompletion_afterConnectionReleasedThrows_continues() {
        StorageSession session = mock(StorageSession.class);
        doThrow(new RuntimeException("Test exception")).when(session).afterConnectionReleased();
        StorageSessionHolder.setSession(session);

        transactionManager.doCleanupAfterCompletion(new Object());

        verify(session).releaseResources();
        verify(session).afterConnectionReleased();
    }
}
