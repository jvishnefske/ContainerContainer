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

import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.async.AsyncCustomExecutorInfo;
import org.artifactory.async.AsyncExecutor;
import org.artifactory.async.CustomExecutor;
import org.artifactory.mbean.MBeanRegistrationService;
import org.artifactory.workqueue.WorkQueueSessionService.WorkQueueSessionCompletionCallback;
import org.artifactory.workqueue.mbean.WorkQueueWrapperMBean;
import org.jfrog.common.concurrency.JvmConflictsGuard;
import org.jfrog.config.utils.IndependentCondition;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author Shay Bagants
 */
@Test
public class AsyncWorkQueueServiceImplTest {

    @Mock
    private RemoveWorkQueuePredicate removeWorkQueuePredicate;
    @Mock
    private WorkQueueSessionService sessionService;
    private AutoCloseable mocks;

    @DataProvider
    public static Object[][] testGetCreateWorkQueueProvider() {
        return new Object[][] {
                {null,60},
                {500 ,500}
        };
    }

    @BeforeMethod
    private void setup() {
        mocks = openMocks(this);

        when(removeWorkQueuePredicate.test(anyString())).thenReturn(false);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(dataProvider = "testGetCreateWorkQueueProvider")
    public void testGetCreateWorkQueue(Integer maxWaitTimeInfo, int expectedValue) throws IllegalAccessException {
        MBeanRegistrationService mbean = mock(MBeanRegistrationService.class);
        WorkQueueInfo workQueueInfo =
                new WorkQueueInfo(1, "WQ1", mock(WorkQueueConflictsGuardProvider.class), maxWaitTimeInfo);
        AsyncExecutor defaultExecutor = mock(AsyncExecutor.class);
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(workQueueInfo, defaultExecutor, mbean,
                        0);

        WorkQueue<WorkItem> wq1 = workQueueService.getWorkQueue("WQ1");

        assertEquals(wq1.getMaxNumberOfWorkers(), 1);
        assertEquals(wq1.getKey(), "WQ1");
        assertEquals(FieldUtils.readField(wq1, "executor", true), defaultExecutor);
        assertEquals(FieldUtils.readField(wq1, "maxWaitForWorkItemSec", true), expectedValue);
        verify(workQueueService, times(1)).createWorkQueue(eq(workQueueInfo), eq(wq1.getKey()), eq(defaultExecutor));
        verify(mbean, times(1)).register(any(WorkQueueWrapperMBean.class), eq("Work Queue"), eq(wq1.getKey()));
    }

    @Test
    public void testGetCreateWorkQueueWithCustomExecutor() throws IllegalAccessException {
        MBeanRegistrationService mbean = mock(MBeanRegistrationService.class);
        AsyncExecutor defaultExecutor = mock(AsyncExecutor.class);
        WorkQueueInfo workQueueInfo = new WorkQueueInfo(1, "WQ1", mock(WorkQueueConflictsGuardProvider.class));
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(workQueueInfo, defaultExecutor, mbean,
                        0);

        AsyncExecutor customExecutor = mock(AsyncExecutor.class);
        try {
            CustomExecutor.set(AsyncCustomExecutorInfo.builder()
                    .customKey(name -> name + " test repo1")
                    .executor(customExecutor)
                    .workQueueWorkers(() -> 4)
                    .build());
            WorkQueue<WorkItem> wq = workQueueService.getWorkQueue("WQ1");
            assertEquals(wq.getMaxNumberOfWorkers(), 4);
            assertEquals(wq.getKey(), "WQ1 test repo1");
            assertEquals(FieldUtils.readField(wq, "executor", true), customExecutor);
            verify(workQueueService, times(1)).createWorkQueue(eq(workQueueInfo), eq(wq.getKey()), eq(customExecutor));
            verify(mbean, times(1)).register(any(WorkQueueWrapperMBean.class), eq("Work Queue"), eq(wq.getKey()));
        } finally {
            CustomExecutor.clear();
        }
    }

    @Test
    public void testGetExistingWorkQueue() {
        MBeanRegistrationService mbean = mock(MBeanRegistrationService.class);
        WorkQueueInfo workQueueInfo = new WorkQueueInfo(1, "WQ1", mock(WorkQueueConflictsGuardProvider.class));
        AsyncExecutor defaultExecutor = mock(AsyncExecutor.class);
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(workQueueInfo, defaultExecutor, mbean,
                        0);

        WorkQueue<WorkItem> wq1 = workQueueService.getWorkQueue("WQ1");
        WorkQueue<WorkItem> wq2 = workQueueService.getWorkQueue("WQ1");
        assertEquals(wq1, wq2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGetUnExistingWorkQueueName() {
        MBeanRegistrationService mbean = mock(MBeanRegistrationService.class);
        WorkQueueInfo workQueueInfo = new WorkQueueInfo(1, "WQ1", mock(WorkQueueConflictsGuardProvider.class));
        AsyncExecutor defaultExecutor = mock(AsyncExecutor.class);
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(workQueueInfo, defaultExecutor, mbean,
                        0);
        workQueueService.getWorkQueue("WQ2");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testExecuteNullWorkQueueName() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);
        workQueueService.execute(null, new IntegerWorkItem(1), () -> {});
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testExecuteNullWorkItem() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);
        workQueueService.execute("WQ1", null, () -> {});
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testExecuteNullRunnable() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);

        workQueueService.execute("WQ1", new IntegerWorkItem(1), null);
    }

    private Pair<WorkQueue, WorkItem> testExecuteWithDelayAfterCommit(
            BiConsumer<Pair<AsyncWorkQueueService, String>, Pair<WorkItem, Runnable>> impl) {
        AsyncExecutor executor = mock(AsyncExecutor.class);
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , executor, mock(MBeanRegistrationService.class),
                        0);

        AtomicReference<WorkQueueImpl<WorkItem>> workQueue = new AtomicReference<>();
        doAnswer(invocation -> {
            workQueue.set(Mockito.spy((WorkQueueImpl<WorkItem>) invocation.callRealMethod()));
            doAnswer(invocation2 -> true).when(workQueue.get()).offerWork(any(WorkItem.class), any(Runnable.class));
            return workQueue.get();
        }).when(workQueueService)
                .createWorkQueue(any(WorkQueueInfo.class), eq("WQ1"), eq(executor));

        Runnable runnable = () -> {};
        IntegerWorkItem workItem = new IntegerWorkItem(1);

        impl.accept(Pair.of(workQueueService, "WQ1"), Pair.of(workItem, runnable));

        verify(workQueueService, times(1)).getWorkQueue(eq("WQ1"));
        verify(workQueue.get(), never()).waitForItemDone(eq(workItem));
        return Pair.of(workQueue.get(), workItem);
    }

    @Test
    public void testExecuteWithDelayAfterCommit() {
        AtomicReference<WorkQueueSessionCompletionCallback> callback = new AtomicReference<>();
        doAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return true;
        }).when(sessionService).register(any(WorkQueueSessionCompletionCallback.class));

        Pair<WorkQueue, WorkItem> work = testExecuteWithDelayAfterCommit(
                (p1, p2) -> p1.getKey().execute(p1.getValue(), p2.getKey(), p2.getValue(), true, false));

        WorkQueue workQueue = work.getLeft();
        WorkItem workItem = work.getRight();

        assertNotNull(callback.get());

        verify(workQueue, times(0)).offerWork(eq(workItem), any(Runnable.class));
        verify(workQueue, times(0)).execute(eq(workItem), any(Runnable.class));

        callback.get().afterCompletion();

        verify(sessionService, times(1)).register(any(WorkQueueSessionCompletionCallback.class));
        verify(workQueue, times(1)).offerWork(eq(workItem), any(Runnable.class));
        verify(workQueue, times(1)).execute(eq(workItem), any(Runnable.class));
    }

    @Test
    public void testExecuteWithDelayAfterCommitAndNoFailIfNoSessionAndNoSession() {
        when(sessionService.register(any(WorkQueueSessionCompletionCallback.class))).thenReturn(false);
        Pair<WorkQueue, WorkItem> work = testExecuteWithDelayAfterCommit(
                (p1, p2) -> p1.getKey().execute(p1.getValue(), p2.getKey(), p2.getValue(), true, false));

        WorkQueue workQueue = work.getLeft();
        WorkItem workItem = work.getRight();

        verify(sessionService, times(1)).register(any(WorkQueueSessionCompletionCallback.class));
        verify(workQueue, times(1)).offerWork(eq(workItem), any(Runnable.class));
        verify(workQueue, times(1)).execute(eq(workItem), any(Runnable.class));
    }

    @Test(expectedExceptions = WorkQueueException.class)
    public void testExecuteWithDelayAfterCommitAndFailIfNoSessionAndNoSession() {
        when(sessionService.register(any(WorkQueueSessionCompletionCallback.class))).thenReturn(false);

        testExecuteWithDelayAfterCommit(
                (p1, p2) -> p1.getKey().execute(p1.getValue(), p2.getKey(), p2.getValue(), true, true));

        fail("should get ");
    }

    @Test
    public void testExecuteAndWait() {
        AsyncExecutor executor = mock(AsyncExecutor.class);
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , executor, mock(MBeanRegistrationService.class),
                        0);
        AtomicReference<WorkQueueImpl<WorkItem>> workQueue = new AtomicReference<>();
        doAnswer(invocation -> {
            workQueue.set(Mockito.spy((WorkQueueImpl<WorkItem>) invocation.callRealMethod()));
            doAnswer(invocation2 -> true).when(workQueue.get()).offerWork(any(WorkItem.class), any(Runnable.class));
            return workQueue.get();
        }).when(workQueueService)
                .createWorkQueue(any(WorkQueueInfo.class), eq("WQ1"), eq(executor));

        Runnable runnable = () -> {};
        IntegerWorkItem workItem = new IntegerWorkItem(1);

        workQueueService.execute("WQ1", workItem, runnable, true);
        verifyImmediateExecutionWithWait(workQueueService, workQueue, runnable, workItem);
    }

    @Test
    public void testExecuteNoWait1() {
        testExecuteNoWait(
                (p1, p2) -> p1.getKey().execute(p1.getValue(), p2.getKey(), p2.getValue(), false));
    }

    @Test
    public void testExecuteNoWait2() {
        testExecuteNoWait((p1, p2) -> p1.getKey().execute(p1.getValue(), p2.getKey(), p2.getValue()));
    }

    @DataProvider
    public Object[][] workQueueConfigProvider() {
        return new Object[][] {
                // inSession, delayAfterCommit
                {true, true},
                // this use case is reasonable when we want to use the queue lock/event reduction without a transaction
                {true, false}
        };
    }

    @Test(dataProvider = "workQueueConfigProvider")
    public void testExecuteDelayAfterCommitAndWait(boolean inSession, boolean delayAfterCommit) {
        ArgumentCaptor<WorkQueueSessionCompletionCallback> captor =
                ArgumentCaptor.forClass(WorkQueueSessionCompletionCallback.class);
        when(sessionService.register(any())).thenReturn(true);

        AsyncExecutor executor = mock(AsyncExecutor.class);
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , executor, mock(MBeanRegistrationService.class),
                        0);
        AtomicReference<WorkQueueImpl<WorkItem>> workQueue = new AtomicReference<>();
        doAnswer(invocation -> {
            workQueue.set(Mockito.spy((WorkQueueImpl<WorkItem>) invocation.callRealMethod()));
            doAnswer(invocation2 -> true).when(workQueue.get()).offerWork(any(WorkItem.class), any(Runnable.class));
            return workQueue.get();
        }).when(workQueueService).createWorkQueue(any(WorkQueueInfo.class), eq("WQ1"), eq(executor));

        Runnable runnable = () -> {};
        IntegerWorkItem workItem = new IntegerWorkItem(1);

        workQueueService.execute("WQ1", workItem, runnable, true, delayAfterCommit, false);

        if (inSession && delayAfterCommit) {
            verifyDelayAfterCommitAndWait(captor, workQueueService, workQueue, runnable, workItem);
        } else {
            verifyImmediateExecutionWithWait(workQueueService, workQueue, runnable, workItem);
        }
    }

    private void verifyImmediateExecutionWithWait(AsyncWorkQueueServiceImpl workQueueService,
            AtomicReference<WorkQueueImpl<WorkItem>> workQueue, Runnable runnable, IntegerWorkItem workItem) {
        verify(workQueueService, times(1)).getWorkQueue(eq("WQ1"));
        verify(workQueue.get(), times(1)).offerWork(eq(workItem), eq(runnable));
        verify(workQueue.get(), times(1)).execute(eq(workItem), eq(runnable));
        verify(workQueue.get(), times(1)).waitForItemDone(eq(workItem));
    }

    private void verifyDelayAfterCommitAndWait(ArgumentCaptor<WorkQueueSessionCompletionCallback> captor,
            AsyncWorkQueueServiceImpl workQueueService, AtomicReference<WorkQueueImpl<WorkItem>> workQueue,
            Runnable runnable, IntegerWorkItem workItem) {
        verify(workQueueService, times(1)).getWorkQueue(eq("WQ1"));
        verify(workQueue.get(), times(0)).offerWork(eq(workItem), eq(runnable));
        verify(workQueue.get(), times(0)).execute(eq(workItem), eq(runnable));
        verify(workQueue.get(), times(0)).waitForItemDone(eq(workItem));

        verify(sessionService).register(captor.capture());
        WorkQueueSessionCompletionCallback callback = captor.getValue();
        callback.afterCompletion();
        verify(workQueue.get(), times(1)).offerWork(eq(workItem), eq(runnable));
        verify(workQueue.get(), times(1)).execute(eq(workItem), eq(runnable));
        verify(workQueue.get(), times(1)).waitForItemDone(eq(workItem));
    }

    private void testExecuteNoWait(BiConsumer<Pair<AsyncWorkQueueService, String>, Pair<WorkItem, Runnable>> impl) {
        AsyncExecutor executor = mock(AsyncExecutor.class);
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , executor, mock(MBeanRegistrationService.class),
                        0);

        AtomicReference<WorkQueueImpl<WorkItem>> workQueue = new AtomicReference<>();
        doAnswer(invocation -> {
            workQueue.set(Mockito.spy((WorkQueueImpl<WorkItem>) invocation.callRealMethod()));
            doAnswer(invocation2 -> true).when(workQueue.get()).offerWork(any(WorkItem.class), any(Runnable.class));
            return workQueue.get();
        }).when(workQueueService)
                .createWorkQueue(any(WorkQueueInfo.class), eq("WQ1"), eq(executor));

        Runnable runnable = () -> {};
        IntegerWorkItem workItem = new IntegerWorkItem(1);

        impl.accept(Pair.of(workQueueService, "WQ1"), Pair.of(workItem, runnable));

        verify(workQueueService, times(1)).getWorkQueue(eq("WQ1"));
        verify(workQueue.get(), times(1)).offerWork(eq(workItem), eq(runnable));
        verify(workQueue.get(), times(1)).execute(eq(workItem), eq(runnable));
        verify(workQueue.get(), never()).waitForItemDone(eq(workItem));
    }

    @Test
    public void testExecuteExistingItem() {
        AsyncExecutor executor = mock(AsyncExecutor.class);
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , executor, mock(MBeanRegistrationService.class),
                        0);
        AtomicReference<WorkQueueImpl<WorkItem>> workQueue = new AtomicReference<>();
        Runnable runnable = () -> {};
        IntegerWorkItem workItem = new IntegerWorkItem(1);
        AtomicBoolean wqExecResult = new AtomicBoolean();

        doAnswer(invocation -> {
            workQueue.set(Mockito.spy((WorkQueueImpl<WorkItem>) invocation.callRealMethod()));
            doAnswer(invocation2 -> false).when(workQueue.get()).offerWork(any(WorkItem.class), any(Runnable.class));

            doAnswer(invocation2 -> {
                wqExecResult.set((boolean) invocation2.callRealMethod());
                return wqExecResult.get();
            }).when(workQueue.get()).execute(eq(workItem), eq(runnable));

            return workQueue.get();
        }).when(workQueueService)
                .createWorkQueue(any(WorkQueueInfo.class), eq("WQ1"), eq(executor));

        workQueueService.execute("WQ1", workItem, runnable, true);

        verify(workQueueService, times(1)).getWorkQueue(eq("WQ1"));
        verify(workQueue.get(), times(1)).offerWork(eq(workItem), eq(runnable));
        verify(workQueue.get(), times(1)).execute(eq(workItem), eq(runnable));
        assertFalse(wqExecResult.get());
        verify(workQueue.get(), never()).waitForItemDone(eq(workItem));
    }

    @Test
    public void testCloseQueues() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);

        WorkQueue<WorkItem> wq = workQueueService.getWorkQueue("WQ1");

        workQueueService.closeAllQueues();

        assertFalse(wq.offerWork(new IntegerWorkItem(1), () -> {}));
    }

    @Test
    public void testGetExistingWorkQueues() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);

        WorkQueue<WorkItem> wq = workQueueService.getWorkQueue("WQ1");

        Map<String, WorkQueue<WorkItem>> workQueues = workQueueService.getExistingWorkQueues();

        assertTrue(workQueues.containsKey("WQ1"));
        assertEquals(workQueues.get("WQ1"), wq);
    }

    @Test
    public void testGetEstimatedPendingTasksSize() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);

        WorkQueue<WorkItem> wq = workQueueService.getWorkQueue("WQ1");
        wq.offerWork(new IntegerWorkItem(1), () -> {});
        wq.offerWork(new IntegerWorkItem(2), () -> {});

        assertEquals(workQueueService.getEstimatedPendingTasksSize("WQ1"), 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testGetEstimatedPendingTasksSizeInvalidWorkQueueName() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);

        workQueueService.getEstimatedPendingTasksSize("WQ2");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testScheduleWorkQueueJobNotConfigured() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);

        workQueueService.scheduleWorkQueueCleanupJob(delegate -> delegate);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testScheduleWorkQueueJobAlreadyRunning() {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        0);
        workQueueService.scheduleWorkQueueCleanupJob(delegate -> delegate);
        workQueueService.scheduleWorkQueueCleanupJob(delegate -> delegate);
    }

    public void testScheduleWorkQueueJobDecoratorUsed() throws InterruptedException {
        AsyncWorkQueueServiceImpl workQueueService =
                createWorkQueueService(
                        new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
                        , mock(AsyncExecutor.class), mock(MBeanRegistrationService.class),
                        1);

        AtomicBoolean decoratorUsed = new AtomicBoolean();
        workQueueService.scheduleWorkQueueCleanupJob(delegate -> {
            decoratorUsed.set(true);
            return delegate;
        });

        new IndependentCondition().waitForConditionOnCall(decoratorUsed::get, TimeUnit.SECONDS, 5);
    }

    @Test
    public void testScheduleWorkQueueJob() throws InterruptedException {
        AtomicInteger cleanInvocations = new AtomicInteger();
        AtomicInteger removeWorkQueuesInvocations = new AtomicInteger();
        AtomicInteger executeStuckItemsInvocations = new AtomicInteger();

        AsyncWorkQueueServiceImpl workQueueService = spy(new AsyncWorkQueueServiceImpl(sessionService, List.of(
                new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
        ), mock(AsyncExecutor.class), List.of(removeWorkQueuePredicate), mock(MBeanRegistrationService.class), 60, 60,
                1, List.of(), true) {
            @Override
            public void clean() {
                super.clean();
                cleanInvocations.incrementAndGet();
            }

            @Override
            void removeWorkQueues() {
                super.removeWorkQueues();
                removeWorkQueuesInvocations.incrementAndGet();

            }

            @Override
            void executeStuckItems() {
                super.executeStuckItems();
                executeStuckItemsInvocations.incrementAndGet();
            }
        });

        workQueueService.scheduleWorkQueueCleanupJob(delegate -> delegate);

        try {
            new IndependentCondition().waitForConditionOnCall(
                    () -> cleanInvocations.get() >= 1 &&
                            removeWorkQueuesInvocations.get() >= 1 &&
                            executeStuckItemsInvocations.get() >= 1, TimeUnit.SECONDS, 5);
        } finally {
            workQueueService.destroy();
        }
    }

    @Test
    public void testExecuteStuckItems() {
        MBeanRegistrationService mBeanRegistrationService = mock(MBeanRegistrationService.class);
        AsyncWorkQueueServiceImpl workQueueService = spy(new AsyncWorkQueueServiceImpl(sessionService, List.of(
                new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1)),
                new WorkQueueInfo(8, "WQ2", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
        ), mock(AsyncExecutor.class), List.of(removeWorkQueuePredicate), mBeanRegistrationService, 60, 60, 0,
                List.of(), true) {
            @NotNull
            @Override
            protected WorkQueueImpl<WorkItem> createWorkQueue(WorkQueueInfo info, String key, AsyncExecutor executor) {
                return spy(super.createWorkQueue(info, key, executor));
            }
        });

        WorkQueue<WorkItem> wq1 = workQueueService.getWorkQueue("WQ1");
        wq1.offerWork(new IntegerWorkItem(1), () -> {});
        WorkQueue<WorkItem> wq2 = workQueueService.getWorkQueue("WQ2");

        workQueueService.executeStuckItems();

        verify(wq1, times(1)).execute();
        verify(wq2, never()).execute();
    }

    @Test
    public void testRemoveWorkQueues() {

        when(removeWorkQueuePredicate.test(endsWith("repo2"))).thenReturn(true);
        when(removeWorkQueuePredicate.test(endsWith("repo3"))).thenReturn(false);

        MBeanRegistrationService mBeanRegistrationService = mock(MBeanRegistrationService.class);
        AsyncWorkQueueServiceImpl workQueueService = Mockito.spy(new AsyncWorkQueueServiceImpl(sessionService, List.of(
                new WorkQueueInfo(8, "WQ1", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1)),
                new WorkQueueInfo(8, "WQ2", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1)),
                new WorkQueueInfo(8, "WQ3", (key, workQueueInfo) -> new JvmConflictsGuard<String>(1))
        ), mock(AsyncExecutor.class), List.of(removeWorkQueuePredicate), mBeanRegistrationService, 60, 60, 0,
                List.of(), true));

        WorkQueue<WorkItem> wq1 = workQueueService.getWorkQueue("WQ1");
        WorkQueue<WorkItem> wq2;
        try {
            CustomExecutor.set(AsyncCustomExecutorInfo.builder()
                    .customKey(name -> name + " test repo2")
                    .executor(mock(AsyncExecutor.class))
                    .workQueueWorkers(() -> 100)
                    .build());
            wq2 = workQueueService.getWorkQueue("WQ2");
        } finally {
            CustomExecutor.clear();
        }

        WorkQueue<WorkItem> wq3;
        try {
            CustomExecutor.set(AsyncCustomExecutorInfo.builder()
                    .customKey(name -> name + " test repo3")
                    .executor(mock(AsyncExecutor.class))
                    .workQueueWorkers(() -> 100)
                    .build());
            wq3 = workQueueService.getWorkQueue("WQ3");
        } finally {
            CustomExecutor.clear();
        }

        workQueueService.removeWorkQueues();

        assertEquals(workQueueService.getExistingWorkQueues().size(), 2);
        verify(removeWorkQueuePredicate, times(3)).test(anyString());

        assertTrue(workQueueService.getExistingWorkQueues().containsKey(wq1.getKey()));
        assertFalse(workQueueService.getExistingWorkQueues().containsKey(wq2.getKey()));
        assertTrue(workQueueService.getExistingWorkQueues().containsKey(wq3.getKey()));

        verify(mBeanRegistrationService, never()).unregister(anyString(), eq(wq1.getKey()));
        verify(mBeanRegistrationService, times(1)).unregister(anyString(), eq(wq2.getKey()));
        verify(mBeanRegistrationService, never()).unregister(anyString(), eq(wq3.getKey()));

    }

    private AsyncWorkQueueServiceImpl createWorkQueueService(WorkQueueInfo workQueueInfo, AsyncExecutor defaultExecutor,
            MBeanRegistrationService mbean,
            int workQueueJobIntervalSec) {
        return Mockito.spy(new AsyncWorkQueueServiceImpl(sessionService, List.of(workQueueInfo), defaultExecutor,
                List.of(removeWorkQueuePredicate), mbean,
                60, 60,
                workQueueJobIntervalSec, List.of(new WorkQueueCustomizer[] {workQueue -> workQueue}), true));
    }
}
