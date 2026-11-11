package org.artifactory.storage.fs.lock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.concurrent.LockingException;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.concurrency.JVMLockWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.locksDebugTimeouts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionLockEntryTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private JVMLockWrapper lock;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;
    private AutoCloseable mocks;
    private SessionLockEntry sessionLockEntry;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        LockEntryId lockEntryId = new LockEntryId(lock, RepoPathFactory.create("repo", "path"));
        sessionLockEntry = new SessionLockEntry(lockEntryId);
        when(lock.lockOwner()).thenReturn(Thread.currentThread());
        logger = (Logger) LoggerFactory.getLogger(SessionLockEntry.class);
        listAppender = new ListAppender<>();
        logger.addAppender(listAppender);
        listAppender.start();
    }

    @AfterEach
    public void teardown() throws Exception {
        listAppender.stop();
        mocks.close();
    }

    @Test
    void testAcquireWriteLockSuccessfully() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        sessionLockEntry.acquireWriteLock();

        verify(lock).tryLock(anyLong(), any(TimeUnit.class));
    }

    @Test
    void testAcquireWriteLockTimeoutWarnLog() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        LockingException exception = assertThrows(LockingException.class, () -> sessionLockEntry.acquireWriteLock());

        assertTrue(exception.getMessage().contains("Lock on"));
        verify(lock).tryLock(anyLong(), any(TimeUnit.class));
        assertLogMessage();
        assertStackTraceLogged();

        int warnLogCount = 0;
        for (int i = 1; i <= 1000; i++) {
            listAppender.list.clear();
            assertThrows(LockingException.class, () -> sessionLockEntry.acquireWriteLock());
            warnLogCount += (int) countLogMessages();
        }
        assertEquals(1, warnLogCount, "WARN log should be logged only once in 1000 times");
    }

    private void assertLogMessage() {
        assertTrue(listAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.WARN && event.getMessage().contains("The lockEntryId")));
    }

    private void assertStackTraceLogged() {
        Arrays.stream(lock.lockOwner().getStackTrace()).findFirst().ifPresent(stackTraceElement -> {
            assertTrue(listAppender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN && event.getMessage().contains("at")));
        });
    }

    private long countLogMessages() {
        return listAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN && event.getMessage().contains("The lockEntryId"))
                .count();
    }

    @Test
    void testAcquireWriteLockTimeoutDebugLog() throws InterruptedException {
        logger.setLevel(Level.DEBUG);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        LockingException exception = assertThrows(LockingException.class, () -> sessionLockEntry.acquireWriteLock());

        assertTrue(exception.getMessage().contains("Lock on"));
        verify(lock).tryLock(anyLong(), any(TimeUnit.class));
        assertTrue(listAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.DEBUG && event.getMessage().contains("The lockEntryId")));
        listAppender.list.clear();
        assertThrows(LockingException.class, () -> sessionLockEntry.acquireWriteLock());
        assertTrue(listAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.DEBUG && event.getMessage().contains("The lockEntryId")));
        Arrays.stream(lock.lockOwner().getStackTrace()).findFirst().ifPresent(stackTraceElement -> {
            assertTrue(listAppender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.DEBUG && event.getMessage().contains("at")));
        });
    }

    @Test
    void testAcquireWriteLockInterruptedException() throws InterruptedException {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

        LockingException exception = assertThrows(LockingException.class, () -> sessionLockEntry.acquireWriteLock());
        assertTrue(exception.getMessage().contains("Lock on"));
        verify(lock).tryLock(anyLong(), any(TimeUnit.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testLocksDebugTimeouts(boolean locksDebug) throws InterruptedException {
        getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .setProperty(locksDebugTimeouts.getPropertyName(), Boolean.toString(locksDebug));
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
        try (MockedStatic<LockingDebugUtils> debugUtils = mockStatic(LockingDebugUtils.class)) {
            assertThrows(LockingException.class, () -> sessionLockEntry.acquireWriteLock());
            debugUtils.verify(() -> LockingDebugUtils.debugLocking(any(), any()), times(locksDebug ? 1 : 0));
        }
    }
}
