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

package org.artifactory.storage.db.fs.itest.dao;

import lombok.Getter;
import org.artifactory.storage.db.fs.dao.BlockingLockDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.DbType;
import org.springframework.util.StopWatch;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link BlockingLockDao} class which implements a DB transaction-based blocking lock mechanism
 *
 * @author Mor Merhav
 */
public class BlockingLockDaoDbTest extends DbBaseTest {

    private static final String LOCK_KEY = "testLock";

    private BlockingLockDao blockingLockDao1;
    private BlockingLockDao blockingLockDao2;

    @BeforeMethod
    public void setUp() {
        blockingLockDao1 = new BlockingLockDao(LOCK_KEY, dbProperties);
        blockingLockDao2 = new BlockingLockDao(LOCK_KEY, dbProperties);
    }

    /**
     * Tests the HA blocking lock mechanism
     * Thread1 acquires the lock and simulates work for a specified time.
     * Thread2 attempts to acquire the same lock while Thread1 is still holding it.
     * Thread2 should block until Thread1 releases the lock.
     * The latch is used to ensure Thread1 takes the lock before Thread2 attempts to acquire it.
     * The Assert checks that Thread2's total blocked time is at least as long as the simulated work time of Thread1.
     */
    @Test
    public void testLockBlocking() throws InterruptedException {
        if (isDerbyDB()) {
            System.err.println("Skipping testLockBlocking for Derby DB as it does not support blocking locks.");
            return;
        }

        int simulatedWorkTimeMillis = 1000;

        CountDownLatch thread1ReadyLatch = new CountDownLatch(1);
        CountDownLatch thread2ReadyLatch = new CountDownLatch(1);
        TestThread1 thread1 =
                new TestThread1(thread1ReadyLatch, thread2ReadyLatch, blockingLockDao1, simulatedWorkTimeMillis);
        TestThread2 thread2 = new TestThread2(thread1ReadyLatch, thread2ReadyLatch, blockingLockDao2);

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        long totalBlockedTimeMillis = thread2.getTotalBlockedTimeMillis();
        long gracePeriodMillis = 100; // Allow some grace period for timing inaccuracies
        long expectedBlockedTimeMillis = simulatedWorkTimeMillis - gracePeriodMillis;
        assertTrue(totalBlockedTimeMillis >= expectedBlockedTimeMillis,
                "Thread 2 should wait for Thread 1 to finish before acquiring the lock. Total blocked time: " +
                        totalBlockedTimeMillis + " ms, expected at least: " + expectedBlockedTimeMillis + " ms");
    }

    private static class TestThread1 extends Thread {

        private final BlockingLockDao blockingLockDao;
        private final int simulatedWorkTimeMillis;
        private final CountDownLatch thread1ReadyLatch;
        private final CountDownLatch thread2ReadyLatch;

        public TestThread1(CountDownLatch thread1ReadyLatch, CountDownLatch thread2ReadyLatch,
                BlockingLockDao blockingLockDao, int simulatedWorkTimeMillis) {
            this.blockingLockDao = blockingLockDao;
            this.thread1ReadyLatch = thread1ReadyLatch;
            this.thread2ReadyLatch = thread2ReadyLatch;
            this.simulatedWorkTimeMillis = simulatedWorkTimeMillis;
        }

        @Override
        public void run() {
            try {
                System.out.println(
                        "Thread1 acquiring lock and simulating work for " + simulatedWorkTimeMillis + " milliseconds.");
                blockingLockDao.lockBlocking();
                System.out.println("Thread1 acquired lock.");
                thread1ReadyLatch.countDown();
                System.out.println("Thread1 signaled readiness, waiting for Thread2 to be ready.");
                thread2ReadyLatch.await();
                System.out.println("Thread2 is ready, Thread1 simulating work.");
                simulateWork(simulatedWorkTimeMillis);
                System.out.println("Thread1 finished work, releasing lock.");
                blockingLockDao.releaseLock();
                System.out.println("Thread1 released lock.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class TestThread2 extends Thread {

        private final BlockingLockDao blockingLockDao;

        @Getter
        private long totalBlockedTimeMillis;
        private final CountDownLatch thread1ReadyLatch;
        private final CountDownLatch thread2ReadyLatch;

        public TestThread2(CountDownLatch thread1ReadyLatch, CountDownLatch thread2ReadyLatch,
                BlockingLockDao blockingLockDao) {
            this.blockingLockDao = blockingLockDao;
            this.thread1ReadyLatch = thread1ReadyLatch;
            this.thread2ReadyLatch = thread2ReadyLatch;
        }

        @Override
        public void run() {
            try {
                StopWatch stopWatch = new StopWatch();
                System.out.println("Thread2 waiting for Thread1 to be ready.");
                thread1ReadyLatch.await();
                System.out.println("Thread1 is ready, Thread2 attempting to acquire lock.");
                thread2ReadyLatch.countDown();
                stopWatch.start("Locking testLock");
                blockingLockDao.lockBlocking();
                System.out.println("Thread2 acquired lock.");
                stopWatch.stop();
                System.out.println("Thread2 finished timing, releasing lock.");
                blockingLockDao.releaseLock();
                totalBlockedTimeMillis = stopWatch.getTotalTimeMillis();
                System.out.println(
                        "Thread2 acquired lock after waiting for " + totalBlockedTimeMillis + " milliseconds.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testReleaseLockWithoutAcquiring() {
        assertDoesNotThrow(() -> blockingLockDao1.releaseLock());
    }

    @Test
    public void testReleaseTwice() throws SQLException {
        blockingLockDao1.lockBlocking();
        blockingLockDao1.releaseLock();
        assertDoesNotThrow(() -> blockingLockDao1.releaseLock());
    }

    private static void simulateWork(int simulatedWorkTimeMillis) throws InterruptedException {
        Thread.sleep(simulatedWorkTimeMillis);
    }

    private boolean isDerbyDB() {
        return dbProperties.getDbType() == DbType.DERBY;
    }
}