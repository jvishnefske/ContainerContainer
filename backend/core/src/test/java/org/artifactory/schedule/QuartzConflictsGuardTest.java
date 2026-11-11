package org.artifactory.schedule;


import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.locks.service.MarkerDbLocksService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.concurrency.ConflictGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.artifactory.concurrent.NonStandardLockCategory.CLUSTER_SINGLETON;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuartzConflictsGuardTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private MarkerDbLocksService dbLocksService;
    @Mock
    private ArtifactoryApplicationContext context;
    private QuartzConflictsGuard quartzConflictsGuard;

    private static final String SERVER_ID = "serverID";
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.getServerId()).thenReturn(SERVER_ID);
        ArtifactoryContextThreadBinder.bind(context);
        quartzConflictsGuard = new QuartzConflictsGuard(dbLocksService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void getLock() throws InterruptedException, TimeoutException {
        ConflictGuard conflictGuard = quartzConflictsGuard.getLock("key");
        boolean locked = conflictGuard.tryToLock(1L, TimeUnit.DAYS);
        Assertions.assertTrue(locked);
        verify(dbLocksService).acquireLock(CLUSTER_SINGLETON.getName(), "key", SERVER_ID, 1L, TimeUnit.DAYS);
    }

    @Test
    void unlock() {
        quartzConflictsGuard.unlock("key");
        verify(dbLocksService).unlock(CLUSTER_SINGLETON.getName(), "key", SERVER_ID);
    }

    @Test
    void isLockedByPrefixExcluding() {
        quartzConflictsGuard.isLockedByPrefixExcluding("lockPrefix", SERVER_ID, true);
        verify(dbLocksService).isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), "lockPrefix", SERVER_ID, true);
    }

    @Test
    void isLocked() {
        quartzConflictsGuard.isLocked("key");
        verify(dbLocksService).isLocked(CLUSTER_SINGLETON.getName(), "key");
    }
}