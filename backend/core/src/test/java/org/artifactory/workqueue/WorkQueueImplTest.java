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

package org.artifactory.workqueue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.Lists;
import com.jfrog.test.LogTestUtils;
import org.artifactory.async.AsyncExecutor;
import org.artifactory.schedule.FullExecutorQueueException;
import org.springframework.core.task.TaskRejectedException;
import org.fest.assertions.Assertions;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.concurrency.JvmConflictsGuard;
import org.jfrog.config.utils.IndependentCondition;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Unit tests for the {@link WorkQueueImpl}.
 *
 * @author Yossi Shaul
 */
@Test
public class WorkQueueImplTest {

    private static final String QUEUE_NAME = "Test WQ";

    @BeforeClass
    public void setup() {
        LogTestUtils.setLoggingLevel(WorkQueueImpl.class, Level.INFO);
    }

    /**
     * Verify that adding workers affect the available permits
     *
     * @throws Exception
     */
    public void addWorkers() throws Exception {

        WorkQueueImpl<IntegerWorkItem> wq =
                new WorkQueueImpl<>(QUEUE_NAME, 5, mock(AsyncExecutor.class),
                        mock(ConflictsGuard.class), 60, 60);

        Assertions.assertThat(wq.availablePermits()).isEqualTo(5);

        wq.updateWorkers(10);

        Assertions.assertThat(wq.availablePermits()).isEqualTo(10);

    }

    /**
     * Verify that reducing workers affects the available permits
     *
     * @throws Exception
     */
    public void freeWorkers() throws Exception {

        WorkQueueImpl<IntegerWorkItem> wq =
                new WorkQueueImpl<>(QUEUE_NAME, 5, mock(AsyncExecutor.class),
                        mock(ConflictsGuard.class), 60, 60);

        Assertions.assertThat(wq.availablePermits()).isEqualTo(5);

        wq.updateWorkers(3);

        Assertions.assertThat(wq.availablePermits()).isEqualTo(3);

    }

    /**
     * Verify that reduce used workers affect the available permits after a used worker is being free
     *
     * @throws Exception
     */
    @Test(expectedExceptions = InterruptedException.class)
    public void freeUsedWorkers() throws Exception {

        WorkQueueImpl<IntegerWorkItem> wq =
                new WorkQueueImpl<>(QUEUE_NAME, 5, mock(AsyncExecutor.class),
                        mock(ConflictsGuard.class), 60, 60);

        Assertions.assertThat(wq.availablePermits()).isEqualTo(5);

        wq.tryAcquire();
        wq.tryAcquire();

        Assertions.assertThat(wq.availablePermits()).isEqualTo(3);

        new IndependentCondition().waitForConditionOnCall(() -> false, TimeUnit.SECONDS, 5L);

        wq.updateWorkers(1);

        fail();

    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void executeNullWorkItem() {
        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>(QUEUE_NAME, 1, mock(AsyncExecutor.class),
                        mock(ConflictsGuard.class), 60, 60));

        wq.execute(null, () -> {});
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void executeNullRunnable() {
        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>(QUEUE_NAME, 1, mock(AsyncExecutor.class),
                        mock(ConflictsGuard.class), 60, 60));

        wq.execute(new IntegerWorkItem(1), null);
    }

    /**
     * verify that execute a work queue acquire permit and executed in the async executor
     *
     * @throws Exception
     */
    public void executeAcquired() throws Exception {

        AsyncExecutor executor = mock(AsyncExecutor.class);

        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>(QUEUE_NAME, 1, executor,
                        mock(ConflictsGuard.class), 60, 60));

        wq.execute();

        verify(wq, times(1)).tryAcquire();
        verify(wq, never()).release();

        Assertions.assertThat(wq.availablePermits()).isEqualTo(0);
        verify(executor, times(1)).execute(any(Runnable.class));
    }

    /**
     * Verify that execute a work queue with no permit, is not consuming permit and not executed in the async executor
     */
    public void executeNotAcquired() {
        AsyncExecutor executor = mock(AsyncExecutor.class);

        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>(QUEUE_NAME, 0, executor,
                        mock(ConflictsGuard.class), 60, 60));

        wq.execute();

        verify(wq, times(1)).tryAcquire();
        verify(wq, never()).release();

        Assertions.assertThat(wq.availablePermits()).isEqualTo(0);
        verify(executor, never()).execute(any(Runnable.class));
    }

    /**
     * Verify that a rejected work item (by the thread pool queue) the available permit is finally not affected
     * (Acquire and released)
     */
    public void executeRejected() {
        AsyncExecutor executor = mock(AsyncExecutor.class);
        doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));
        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>(QUEUE_NAME, 1, executor,
                        mock(ConflictsGuard.class), 60, 60));

        wq.execute();

        verify(wq, times(1)).tryAcquire();
        verify(wq, times(1)).release();

        Assertions.assertThat(wq.availablePermits()).isEqualTo(1);
        verify(executor, times(1)).execute(any(Runnable.class));
    }


    //execute rejects the work item if the queue is full
    public void executeRejectedWhenQueueIsFull() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger log = (Logger) LoggerFactory.getLogger(WorkQueueImpl.class); // Use the class under test
        log.addAppender(listAppender);
        log.setLevel(Level.DEBUG);
        listAppender.start();

        AsyncExecutor executor = mock(AsyncExecutor.class);
        // Spring's TaskExecutorAdapter wraps FullExecutorQueueException in TaskRejectedException
        // Simulate this behavior in the test
        FullExecutorQueueException fullQueueException = new FullExecutorQueueException("Queue size limit reached");
        doThrow(new TaskRejectedException("Task rejected", fullQueueException)).when(executor)
                .execute(any(Runnable.class));
        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>(QUEUE_NAME, 1, executor,
                        mock(ConflictsGuard.class), 60, 60));

        //offer work item
        wq.offerWork(new IntegerWorkItem(1), () -> {});

        //execute the work item
        wq.execute();

        verify(wq, times(1)).tryAcquire();
        verify(wq, times(1)).release();


        Assertions.assertThat(wq.availablePermits()).isEqualTo(1);
        verify(executor, times(1)).execute(any(Runnable.class));
        // verify it gets to the part that supress the exception (and not the log.error part)
        boolean foundLog = listAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.DEBUG &&
                        event.getFormattedMessage().contains("is full, cannot execute task"));
        assertTrue(foundLog);
        listAppender.stop();
    }


    /**
     * Verify that item which was not promoted due to long run is re-executed
     *
     * @throws Exception
     */
    public void testShouldNotPromote() throws Exception {

        AsyncExecutor executor = mock(AsyncExecutor.class);
        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>(QUEUE_NAME, 1, executor,
                        mock(ConflictsGuard.class), 60, 60));

        doAnswer(invocation -> false).when(wq).shouldPromote(any(Instant.class));
        when(wq.getQueueSize()).thenReturn(1);

        wq.doJobs();

        verify(wq, times(1)).execute();
    }

    /**
     * Verify that item which was not promoted due to long run is re-executed
     *
     * submit 2 items, first item with sleep inorder to prevent the direct execution of the next item
     *
     * The next item, should be re-executed through the executor
     *
     * @throws Exception
     */
    public void testShouldNotPromoteExecTimeElapsed() throws Exception {
        try (TestAsyncExecutor executor = spy(new TestAsyncExecutor(1, 5))) {
            WorkQueueImpl<IntegerWorkItem> wq =
                    spy(new WorkQueueImpl<>(QUEUE_NAME, 1, executor,
                            new JvmConflictsGuard<>(1), 1, 60));

            //A countdown to wait until 0
            CountDownLatch count = new CountDownLatch(2);

            //ofer item 1
            wq.offerWork(new IntegerWorkItem(1), () -> {
                try {
                    //Sleep 2 sec in order to not promote the next item
                    Thread.sleep(2000);
                    count.countDown();
                    when(executor.getPendingTasks()).thenReturn(1);
                } catch (InterruptedException e) {
                }
            });

            // ofer item 2
            wq.offerWork(new IntegerWorkItem(2), () -> count.countDown());

            //execute both items
            wq.execute();

            if (!count.await(10, TimeUnit.SECONDS)) {
                fail(wq.getQueueSize() + " item(s) were not executed");
            }

            verify(wq, times(2)).execute();
            verify(wq, times(2)).doJobs();
            assertEquals(wq.getQueueSize(), 0);
        }
    }

    /**
     * Verify should promote when item exec time is short and there are pending thread pool tasks
     */
    public void testShouldPromoteTimeNotElapsedAndPendingThreadPoolTasks() {
        AsyncExecutor executor = mock(AsyncExecutor.class);
        when(executor.getPendingTasks()).thenReturn(1);

        WorkQueueImpl<IntegerWorkItem> wq =
                new WorkQueueImpl<>(QUEUE_NAME, 1, executor,
                        mock(ConflictsGuard.class), 60, 60);

        Assertions.assertThat(wq.shouldPromote(Instant.now())).isTrue();
    }

    /**
     * Verify should promote when item exec time is short and time there are no pending thread pool tasks
     */
    public void testShouldPromoteTimeElapsedAndNoPendingThreadPoolTasks() {
        AsyncExecutor executor = mock(AsyncExecutor.class);
        when(executor.getPendingTasks()).thenReturn(0);
        WorkQueueImpl<IntegerWorkItem> wq =
                new WorkQueueImpl<>(QUEUE_NAME, 1, executor,
                        mock(ConflictsGuard.class), 60, 60);


        Assertions.assertThat(wq.shouldPromote(Instant.now().minusSeconds(61))).isTrue();
    }

    /**
     * Verify should not promote when item exec time is long and there are thread pool pending tasks
     */
    public void testShouldNotPromoteTimeElapsedAndPendingThreadPoolTasks() {
        AsyncExecutor executor = mock(AsyncExecutor.class);
        when(executor.getPendingTasks()).thenReturn(1);
        WorkQueueImpl<IntegerWorkItem> wq =
                new WorkQueueImpl<>(QUEUE_NAME, 1, executor,
                        mock(ConflictsGuard.class), 60, 60);


        Assertions.assertThat(wq.shouldPromote(Instant.now().minusSeconds(61))).isFalse();
    }


    public void offerTheSameWorkConcurrently() throws Exception {
        List<AtomicInteger> counts = Collections.nCopies(10, new AtomicInteger(0));
        Counter counter = new Counter(counts);
        ConflictsGuard guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(Object.class), anyLong(), any(TimeUnit.class))).thenReturn(true);

        try (TestAsyncExecutor executor = spy(new TestAsyncExecutor(20, 1500))) {
            WorkQueueImpl<IntegerWorkItem> wq =
                    new WorkQueueImpl<>("Test Queue", 1, executor, guard, 60, 60);

            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10000; j++) {
                    IntegerWorkItem workItem = new IntegerWorkItem(i);
                    wq.execute(workItem, () -> counter.count(workItem));
                }
            }
        }

        // We expect each job to be executed at least once
        counts.forEach(count -> Assertions.assertThat(count.get()).isGreaterThanOrEqualTo(1));
    }


    public void offerWorkWithNWorkers() throws Exception {
        List<AtomicInteger> counts = Collections.nCopies(1000, new AtomicInteger(0));
        Counter counter = new Counter(counts);
        ConflictsGuard guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(Object.class), anyLong(), any(TimeUnit.class))).thenReturn(true);
        try (TestAsyncExecutor executor = spy(new TestAsyncExecutor(20, 1500))) {

            WorkQueueImpl<IntegerWorkItem> wq =
                    new WorkQueueImpl<>("Test Queue", 4, executor, guard, 60, 60);

            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    IntegerWorkItem workItem = new IntegerWorkItem(i);
                    wq.execute(workItem, () -> counter.count(workItem));
                }
            }
        }

        // We expect each job to be executed at least once
        counts.forEach(count -> Assertions.assertThat(count.get()).isGreaterThanOrEqualTo(1));
    }

    // When waiting for item (blockUntilFinished = true), the item we are waiting for can either be pending, or
    //promoted state.
    // This tests a bug that when the item is promoted, we never actually waited for it to complete
    public void waitUntilWhenItemIsPromotedTest() throws Exception {
        SleepingDummyService dummyService = new SleepingDummyService();
        ConflictsGuard guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(Object.class), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(guard.isLocked(any(Object.class))).thenReturn(true);

        try (TestAsyncExecutor executor = spy(new TestAsyncExecutor(10, 1500))) {
            WorkQueueImpl<DummyWorkItem> queue =
                    new WorkQueueImpl<>("Test Queue", 4, executor, guard, 60, 60);
            for (int i = 0; i < 20; i++) {
                DummyWorkItem workItem = new DummyWorkItem("x-" + i);
                queue.offerWork(workItem, () -> dummyService.serve(workItem));
                queue.execute();
                queue.waitForItemDone(workItem);
                Assert.assertTrue(workItem.isProcessed(), workItem.getUniqueKey() + " Failed");
            }
        }
    }

    public void waitUntilTest() throws Exception {
        List<AtomicInteger> counts = Collections.nCopies(10, new AtomicInteger(0));
        Counter counter = new Counter(counts);
        ConflictsGuard guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(Object.class), anyLong(), any(TimeUnit.class))).thenReturn(true);
        try (TestAsyncExecutor executor = spy(new TestAsyncExecutor(10, 1500))) {
            WorkQueueImpl<IntegerWorkItem> wq =
                    new WorkQueueImpl<>("Test Queue", 4, executor, guard, 60, 60);

            ExecutorService testExecutor = Executors.newFixedThreadPool(10);
            try {
                ArrayList<Future> futures = Lists.newArrayList();
                for (int i = 0; i < 5; i++) {
                    futures.add(testExecutor.submit(() -> {
                        for (int j = 0; j < 10000; j++) {
                            IntegerWorkItem workItem = new IntegerWorkItem((int) (Math.random() * 10d));
                            wq.execute(workItem, () -> counter.count(workItem));
                            wq.waitForItemDone(workItem);
                        }
                        return null;
                    }));
                }
                for (Future future : futures) {
                    future.get();
                }
            } finally {
                testExecutor.shutdown();
                testExecutor.awaitTermination(1500, TimeUnit.SECONDS);
            }
        }
        // We expect each job to be executed at least once
        counts.forEach(count -> Assertions.assertThat(count.get()).isGreaterThanOrEqualTo(1));
    }

    /**
     * Test that specifically validates the FullExecutorQueueException handling in WorkQueueImpl
     * by using a custom executor that throws TaskRejectedException with FullExecutorQueueException as cause
     * (simulating Spring's TaskExecutorAdapter wrapping behavior)
     */
    @Test
    public void testWorkQueueHandlesFullExecutorQueueException() throws Exception {
        // Set up logging capture for WorkQueueImpl to verify the exception handling logs
        LogTestUtils.setLoggingLevel(WorkQueueImpl.class, Level.DEBUG);

        ConflictsGuard guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(Object.class), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(guard.isLocked(any(Object.class))).thenReturn(true);

        // Spring's TaskExecutorAdapter wraps FullExecutorQueueException in TaskRejectedException
        // Simulate this behavior in the test
        FullExecutorQueueException fullQueueException =
                new FullExecutorQueueException("Task test is being rejected due to queue size limit reached");
        AsyncExecutor mockExecutor = mock(AsyncExecutor.class);
        doThrow(new TaskRejectedException("Task rejected", fullQueueException))
                .when(mockExecutor).execute(any(Runnable.class));

        WorkQueueImpl<IntegerWorkItem> wq =
                new WorkQueueImpl<>("Full Executor Exception Test", 1, mockExecutor,
                        guard, 60, 60);

        // Try to execute a task - this should trigger the FullExecutorQueueException
        // which WorkQueueImpl should catch and handle internally
        IntegerWorkItem testItem = new IntegerWorkItem(999);
        try {
            wq.execute(testItem, () -> {
                // This should never execute due to the exception
            });
        } catch (Exception e) {
            Assert.fail("WorkQueueImpl should handle TaskRejectedException with FullExecutorQueueException internally,"
                    + " but got: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Test that covers line 145 - the while loop body that traverses nested cause chain
     * to find FullExecutorQueueException when it's nested deeper in the exception chain
     */
    @Test
    public void testWorkQueueHandlesNestedFullExecutorQueueException() throws Exception {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger log = (Logger) LoggerFactory.getLogger(WorkQueueImpl.class);
        log.addAppender(listAppender);
        log.setLevel(Level.DEBUG);
        listAppender.start();

        ConflictsGuard guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(Object.class), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(guard.isLocked(any(Object.class))).thenReturn(true);

        // Create a nested cause chain: TaskRejectedException -> RuntimeException -> FullExecutorQueueException
        // This tests the while loop that traverses the cause chain (line 145)
        FullExecutorQueueException fullQueueException =
                new FullExecutorQueueException("Task test is being rejected due to queue size limit reached");
        RuntimeException intermediateException = new RuntimeException("Intermediate exception", fullQueueException);
        AsyncExecutor mockExecutor = mock(AsyncExecutor.class);
        doThrow(new TaskRejectedException("Task rejected", intermediateException))
                .when(mockExecutor).execute(any(Runnable.class));

        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>("Nested Full Executor Exception Test", 1, mockExecutor,
                        guard, 60, 60));

        wq.offerWork(new IntegerWorkItem(1), () -> {});
        wq.execute();

        verify(wq, times(1)).tryAcquire();
        verify(wq, times(1)).release();
        Assertions.assertThat(wq.availablePermits()).isEqualTo(1);
        verify(mockExecutor, times(1)).execute(any(Runnable.class));

        // Verify it logs debug message (not error) when FullExecutorQueueException is found in nested cause
        boolean foundLog = listAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.DEBUG &&
                        event.getFormattedMessage().contains("is full, cannot execute task"));
        assertTrue(foundLog);
        listAppender.stop();
    }

    /**
     * Test that covers line 150 - the else branch when TaskRejectedException does NOT
     * have FullExecutorQueueException in its cause chain
     */
    @Test
    public void testWorkQueueHandlesTaskRejectedExceptionWithoutFullQueueException() throws Exception {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger log = (Logger) LoggerFactory.getLogger(WorkQueueImpl.class);
        log.addAppender(listAppender);
        log.setLevel(Level.ERROR);
        listAppender.start();

        ConflictsGuard guard = mock(ConflictsGuard.class);
        when(guard.tryToLock(any(Object.class), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(guard.isLocked(any(Object.class))).thenReturn(true);

        // TaskRejectedException with a different cause (not FullExecutorQueueException)
        // This should trigger the else branch (line 150) and log an error
        RuntimeException differentException = new RuntimeException("Different exception");
        AsyncExecutor mockExecutor = mock(AsyncExecutor.class);
        doThrow(new TaskRejectedException("Task rejected", differentException))
                .when(mockExecutor).execute(any(Runnable.class));

        WorkQueueImpl<IntegerWorkItem> wq =
                spy(new WorkQueueImpl<>("Task Rejected Exception Test", 1, mockExecutor,
                        guard, 60, 60));

        wq.offerWork(new IntegerWorkItem(1), () -> {});
        wq.execute();

        verify(wq, times(1)).tryAcquire();
        verify(wq, times(1)).release();
        Assertions.assertThat(wq.availablePermits()).isEqualTo(1);
        verify(mockExecutor, times(1)).execute(any(Runnable.class));

        // Verify it logs error message when FullExecutorQueueException is NOT found in cause chain
        boolean foundErrorLog = listAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.ERROR &&
                        event.getFormattedMessage().contains("Failed to execute work queue"));
        assertTrue(foundErrorLog);
        listAppender.stop();
    }

    @SuppressWarnings("unchecked")
    private static ConflictsGuard<String> createThrowOnSecondUnlockGuard() {
        ConflictsGuard<String> delegate = new JvmConflictsGuard<>(60);
        Set<String> unlockedKeys = ConcurrentHashMap.newKeySet();
        InvocationHandler handler = (proxy, method, args) -> {
            if ("unlock".equals(method.getName())) {
                String key = (String) args[0];
                if (!unlockedKeys.add(key)) {
                    throw new IllegalStateException("Lock already released for: " + key);
                }
                delegate.unlock(key);
                return null;
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                    throw new UndeclaredThrowableException(cause);
                }
                throw e;
            }
        };
        return (ConflictsGuard<String>) Proxy.newProxyInstance(
                ConflictsGuard.class.getClassLoader(),
                new Class<?>[] {ConflictsGuard.class},
                handler);
    }

    @Test
    public void doubleRemoveScenarioDoesNotLogUnexpectedState() throws Exception {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger log = (Logger) LoggerFactory.getLogger(WorkQueueImpl.class);
        log.addAppender(listAppender);
        log.setLevel(Level.ERROR);
        listAppender.start();

        CountDownLatch workerInsideRunnable = new CountDownLatch(1);
        CountDownLatch releaseWorkerFromRunnable = new CountDownLatch(1);
        try (TestAsyncExecutor executor = new TestAsyncExecutor(1, 5)) {
            WorkQueueImpl<DummyWorkItem> wq = new WorkQueueImpl<>("Helm Virtual Metadata", 1, executor,
                    createThrowOnSecondUnlockGuard(), 60, 60);
            wq.offerWork(new DummyWorkItem("test-key"), () -> {
                workerInsideRunnable.countDown();
                try {
                    assertTrue(releaseWorkerFromRunnable.await(5, TimeUnit.SECONDS),
                            "Main thread must release worker after stopQueue()");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
            wq.execute();
            assertTrue(workerInsideRunnable.await(5, TimeUnit.SECONDS),
                    "Worker must reach runnable body before stopQueue()");
            wq.stopQueue();
            releaseWorkerFromRunnable.countDown();
        }

        boolean foundUnexpectedStateLog = listAppender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("unexpected state")
                        && event.getFormattedMessage().contains("failed to remove"));
        assertTrue(!foundUnexpectedStateLog,
                "With the fix, the exact ticket log 'unexpected state: failed to remove ...' must NOT appear.");
        listAppender.stop();
    }

    class TestAsyncExecutor implements AsyncExecutor, AutoCloseable {

        private final ThreadPoolExecutor executor;
        private int awaitSec;

        public TestAsyncExecutor(int threadsCount, int awaitSec) {
            this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
            this.awaitSec = awaitSec;
        }

        @Override
        public void close() throws Exception {
            executor.shutdown();
            executor.awaitTermination(awaitSec, TimeUnit.SECONDS);
        }

        @Override
        public <T> Future<T> execute(Callable<T> task) {
            return executor.submit(task);
        }

        @Override
        public void execute(Runnable runnable) {
            executor.execute(runnable);
        }

        @Override
        public int getPendingTasks() {
            return executor.getQueue().size();
        }

        @Override
        public int getActiveTasks() {
            return executor.getActiveCount();
        }
    }
}
