/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.locks.itest;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.dao.locks.DbDistributeLocksDao;
import org.artifactory.storage.db.dao.locks.LockInfo;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.locks.provider.DbMapLockWrapper;
import org.artifactory.storage.db.locks.service.DbLocksServiceImpl;
import org.artifactory.storage.db.locks.service.UnsupportedReentrantLockException;
import org.artifactory.storage.fs.lock.FsItemsVaultCacheImpl;
import org.artifactory.storage.fs.lock.LockEntryId;
import org.artifactory.test.TestUtils;
import org.jfrog.common.concurrency.LockWrapper;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author gidis
 */
public class DbLocksServiceImplDBTest extends DbBaseTest {

    @Autowired
    private DbLocksServiceImpl dbLocksService;

    @BeforeClass
    public void setup() {
        importSql("/sql/aql_test.sql");
        TestUtils.setLoggingLevel(DbMapLockWrapper.class, Level.ERROR);
    }

    // Perform concurrent try lock operations on the DB locking mechanism and make sure
    @Test
    public void concurrentDBLockTest() throws ExecutionException, InterruptedException {
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(40, 100, 100L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
        FsItemsVaultCacheImpl vaultCache =
                new FsItemsVaultCacheImpl(repoPath -> new DbMapLockWrapper(repoPath, dbLocksService, "server"));
        List<Future<?>> futures = Lists.newArrayList();
        for (int i = 0; i < 60; i++) {
            futures.add(executor.submit(() -> {
                for (int j = 0; j < 50; j++) {
                    LockEntryId lock = vaultCache.getLock(RepoPathFactory.create("repo", "test"));
                    LockWrapper lockWrapper = lock.getLock();
                    if (lockWrapper.isHeldByCurrentThread()) {
                        throw new RuntimeException("Current thread should not be the owner of a lock!");
                    }
                    if (lockWrapper.tryLock(1, TimeUnit.MILLISECONDS)) {
                        try {
                            Thread.sleep(100);
                            if (!lockWrapper.isHeldByCurrentThread()) {
                                throw new RuntimeException("Current thread must be owner of this lock!");
                            }
                        } finally {
                            lockWrapper.unlock();
                        }
                        if (lockWrapper.isHeldByCurrentThread()) {
                            throw new RuntimeException("Current thread should not be the owner of a lock!");
                        }
                    }

                }
                return true;
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
    }

    @Test
    @SneakyThrows
    public void checkNoExtraCallsToDao() {
        DbDistributeLocksDao distributeLocksDao = spy(ContextHelper.get().beanForType(DbDistributeLocksDao.class));
        DbLocksServiceImpl dbLocksService = new DbLocksServiceImpl(distributeLocksDao, dbProperties);
        runLockTest(dbLocksService);
        //verifying no extra db calls has been done to those methods
        verify(distributeLocksDao, times(1)).tryToAcquireLock(any());
        verify(distributeLocksDao, never()).getLockInfo(any(), any());
    }

    @SneakyThrows
    private void runLockTest(DbLocksServiceImpl dbLocksService) {
        try {
            assertFalse(dbLocksService.isLocked("category", "key"), "check not locked before start");
            //lock now so all the inner threads will not get it and receive timeout
            dbLocksService.acquireLock("category", "key", "owner", 0, TimeUnit.SECONDS);
            assertTrue(dbLocksService.isLocked("category", "key"), "check lock exist");
            //create threads which calls the same acquireLock multiple times
            ExecutorService executorService = Executors.newFixedThreadPool(4);
            List<Callable<Boolean>> callables = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                callables.add(() -> {
                    try {
                        assertTrue(dbLocksService.isLocked("category", "key"));
                        dbLocksService.acquireLock("category", "key", "owner", 0, TimeUnit.MILLISECONDS);
                        Assert.fail("we expect a timeout exception because lock was acquired by outside thread");
                    } catch (TimeoutException expected) {
                    }
                    return true;
                });
            }
            //wait for all futures to finish
            List<Future<Boolean>> futures = executorService.invokeAll(callables);
            for (Future<Boolean> future : futures) {
                future.get();
            }
        } finally {
            //free lock at the end (in case other test needs it)
            dbLocksService.unlock("category", "key", "owner");
            assertFalse(dbLocksService.isLocked("category", "key"), "should be unlocked now");
        }
    }

    @Test
    @SneakyThrows
    public void checkNoExtraCallsToDaoOnlyOneToAcquireLockForThreadsWithTheSameLockKey() {
        DbDistributeLocksDao distributeLocksDaoMock = Mockito.mock(DbDistributeLocksDao.class);
        DbLocksServiceImpl dbLocksService = new DbLocksServiceImpl(distributeLocksDaoMock, dbProperties);
        runLockTestWithMockedDaoLayer(dbLocksService, distributeLocksDaoMock);
        //verifying one db call for all threads with same key
        verify(distributeLocksDaoMock, times(1)).tryToAcquireLock(any());
        //verifying no extra db calls has been done to those methods
        verify(distributeLocksDaoMock, never()).getLockInfo(any(), any());
    }

    @SneakyThrows
    private void runLockTestWithMockedDaoLayer(DbLocksServiceImpl dbLocksService,
            DbDistributeLocksDao dbDistributeLocksDaoMock) {
        try {
            assertFalse(dbLocksService
                    .isLocked("category", "key"), "check not locked before start");
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            when(dbDistributeLocksDaoMock.tryToAcquireLock(any())).thenReturn(true);
            List<Runnable> runnables = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                runnables.add(() -> {
                    try {
                        dbLocksService.acquireLock("category", "key",
                                "owner", 0, TimeUnit.MILLISECONDS);
                        Assert.fail("we expect a timeout exception because lock was acquired by outside thread");
                    } catch (TimeoutException expected) {
                    }
                });
            }
            runnables.forEach(executorService::submit);
        } finally {
            this.dbLocksService.unlock("category", "key", "owner");
            assertFalse(this.dbLocksService
                    .isLocked("category", "key"), "should be unlocked now");
        }
    }

    @Test
    @SneakyThrows
    public void acquireLockCleanUpLocalLocksOnSQLException() {
        DbDistributeLocksDao distributeLocksDao = spy(ContextHelper.get().beanForType(DbDistributeLocksDao.class));
        DbLocksServiceImpl dbLocksService = new DbLocksServiceImpl(distributeLocksDao, dbProperties);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Mockito.doThrow(new SQLException()).when(distributeLocksDao).tryToAcquireLock(any());
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getSchema()).thenReturn("testSchema");
        try {
            dbLocksService.acquireLock("category", "key",
                    "owner", 0, TimeUnit.MILLISECONDS);
        } catch (IllegalStateException e) {
            assertFalse(this.dbLocksService
                    .isLocked("category", "key"), "should be unlocked now");
        }
    }

    @Test
    @SneakyThrows
    public void acquireLockCleanUpLocalLocksOnException() {
        DbDistributeLocksDao distributeLocksDao = spy(ContextHelper.get().beanForType(DbDistributeLocksDao.class));
        DbLocksServiceImpl dbLocksService = new DbLocksServiceImpl(distributeLocksDao, dbProperties);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Mockito.doThrow(new MockitoException("test")).when(distributeLocksDao).tryToAcquireLock(any());
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getSchema()).thenReturn("testSchema");
        try {
            dbLocksService.acquireLock("category", "key",
                    "owner", 0, TimeUnit.MILLISECONDS);
        } catch (IllegalStateException e) {
            assertFalse(this.dbLocksService
                    .isLocked("category", "key"), "should be unlocked now");
        }
    }

    @Test
    @SneakyThrows
    public void acquireLockDbLockedFinallyRemoved() {
        DbDistributeLocksDao distributeLocksDao = spy(ContextHelper.get().beanForType(DbDistributeLocksDao.class));
        DbLocksServiceImpl dbLocksService = new DbLocksServiceImpl(distributeLocksDao, dbProperties);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Mockito.doReturn(false).when(distributeLocksDao).tryToAcquireLock(any());
        when(connection.getMetaData()).thenReturn(metaData);
        try {
            when(connection.getSchema()).thenReturn("testSchema");
            dbLocksService.acquireLock("category", "key",
                    "owner", 0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            assertFalse(this.dbLocksService
                    .isLocked("category", "key"), "should be unlocked now");
        }
    }

    @Test
    @SneakyThrows
    public void acquireLockDbNotLockedFinallyRemoved() {
        DbDistributeLocksDao distributeLocksDao = spy(ContextHelper.get().beanForType(DbDistributeLocksDao.class));
        DbLocksServiceImpl dbLocksService = new DbLocksServiceImpl(distributeLocksDao, dbProperties);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Mockito.doReturn(true).when(distributeLocksDao).tryToAcquireLock(any());
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getSchema()).thenReturn("testSchema");
        dbLocksService.acquireLock("category", "key",
                "owner", 0, TimeUnit.MILLISECONDS);
        assertFalse(this.dbLocksService
                .isLocked("category", "key"), "should be unlocked now");
    }

    @Test
    @SneakyThrows
    public void testUnsupportedReentrantLockInLocalLocks() {
        testUnsupportedReentrantLock(dbLocksService -> {}, 1, 0);
    }

    @Test
    @SneakyThrows
    public void testUnsupportedReentrantLockInDbLocks() {
        testUnsupportedReentrantLock(dbLocksService -> {
            try {
                ((Map<String, LockInfo>) FieldUtils
                        .readField(dbLocksService, "localLocks", true)).clear();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }, 2, 1);
    }

    private void testUnsupportedReentrantLock(Consumer<DbLocksServiceImpl> consumer, int tryToAcquireLockTimes,
            int getLockInfoTimes)
            throws Exception {
        DbDistributeLocksDao distributeLocksDao = spy(ContextHelper.get().beanForType(DbDistributeLocksDao.class));
        DbLocksServiceImpl dbLocksService = new DbLocksServiceImpl(distributeLocksDao, dbProperties);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        //Mockito.doReturn(true).when(distributeLocksDao).tryToAcquireLock(any());
        AtomicInteger acquireTimes = new AtomicInteger();
        doAnswer(invocation -> {
            acquireTimes.incrementAndGet();
            if (acquireTimes.get() <= 1) {
                return true;
            } else {
                return false;
            }
        }).when(distributeLocksDao).tryToAcquireLock(any());

        doAnswer(invocation -> new LockInfo("category", "key", "owner", Thread.currentThread().getId(),
                Thread.currentThread().getName(), System.currentTimeMillis())).when(distributeLocksDao)
                .getLockInfo(eq("category"), eq("key"));

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getSchema()).thenReturn("testSchema");
        dbLocksService.acquireLock("category", "key",
                "owner", 0, TimeUnit.MILLISECONDS);

        assertTrue(dbLocksService
                .isLocked("category", "key"), "should be locked");
        try {

            consumer.accept(dbLocksService);

            dbLocksService.acquireLock("category", "key",
                    "owner", 0, TimeUnit.MILLISECONDS);
            fail("Should get " + UnsupportedReentrantLockException.class.getSimpleName());
        } catch (Exception e) {
            verify(distributeLocksDao, times(tryToAcquireLockTimes)).tryToAcquireLock(any(LockInfo.class));
            verify(distributeLocksDao, times(getLockInfoTimes)).getLockInfo(eq("category"), eq("key"));
            assertEquals(ExceptionUtils.getRootCause(e).getClass(), UnsupportedReentrantLockException.class);
        }
    }
}
