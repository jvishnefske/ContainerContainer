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
import com.jfrog.test.LogTestUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.concurrency.JvmConflictsGuard;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author gidis
 */
@Test
public class NonBlockingOnWriteQueueTest {

    @BeforeClass
    public void setup() {
        LogTestUtils.setLoggingLevel("org.artifactory.workqueue", Level.WARN);
    }

    @Test
    public void addConcurrentAddRemoveTest() throws InterruptedException {
        double maxWorkItemNumber = 100d;
        int numberOfProducerThreads = 28;
        int numberOfInputs = 10000;
        int numberOfConsumerThreads = 4;
        ExecutorService producerExecutor = Executors.newFixedThreadPool(numberOfProducerThreads);

        ConflictsGuard<String> guard = new JvmConflictsGuard<>(2);

        NonBlockingOnWriteQueue<IntegerWorkItem> queue = new NonBlockingOnWriteQueue<>(guard);
        // Create producer threads that will fill the queue
        for (int i = 0; i < numberOfProducerThreads; i++) {
            producerExecutor.submit(() -> {
                for (int j = 0; j < numberOfInputs; j++) {
                    IntegerWorkItem workItem = new IntegerWorkItem((int) (Math.random() * maxWorkItemNumber));
                    queue.addToPending(workItem, null);
                }
            });
        }
        // Create Consumer threads that will execute the jobs
        AtomicInteger counter = new AtomicInteger();
        ExecutorService consumerExecutor = Executors.newFixedThreadPool(numberOfConsumerThreads);
        for (int i = 0; i < numberOfConsumerThreads; i++) {
            consumerExecutor.submit(() -> {
                while (!producerExecutor.isTerminated() || queue.getQueueSize() > 0 || queue.getRunningSize() > 0) {
                    WorkQueuePromotedItem<IntegerWorkItem> promotion = queue.promote();
                    if (promotion != null) {
                        queue.remove(promotion);
                        counter.getAndIncrement();
                    }
                }
            });
        }

        producerExecutor.shutdown();
        producerExecutor.awaitTermination(2, MINUTES);
        consumerExecutor.shutdown();
        consumerExecutor.awaitTermination(2, MINUTES);
        Assert.assertEquals(queue.getRunningSize(), 0);
        Assert.assertEquals(queue.getQueueSize(), 0);
        Assert.assertTrue(counter.get() >= 100);
        Assert.assertTrue(counter.get() <= 1000);
        System.out.println("Number of successful promotions (removals): " + counter.get());
    }

    public void testIdenticalWorkItemsIsEmpty() {
        Assert.assertTrue(new DummyWorkItem("Im dummy").getIdenticalWorkItems().isEmpty());
    }

    public void testIdenticalWorkItems() {
        ConflictsGuard<String> guard = new JvmConflictsGuard<>(2);

        NonBlockingOnWriteQueue<DummyWorkItem> queueSpy = spy(new NonBlockingOnWriteQueue<>(guard));

        Runnable runnable = () -> {};
        queueSpy.addToPending(new DummyWorkItem("Im dummy"), runnable);

        WorkItemWrapper workItemWrapperMock = mock(WorkItemWrapper.class);
        when(queueSpy.acquireLockOnKey()).thenReturn(workItemWrapperMock);
        when(workItemWrapperMock.getWorkItem()).thenReturn(new DummyWorkItem("Im dummy"));
        when(workItemWrapperMock.getRunnable()).thenReturn(runnable);

        WorkQueuePromotedItem<DummyWorkItem> promotedItem = queueSpy.promote();
        List<WorkItem> identicalWorkItems = promotedItem.getWorkItem().getIdenticalWorkItems();
        Assert.assertEquals(identicalWorkItems.size(), 1);
        Assert.assertEquals(identicalWorkItems.get(0).getUniqueKey(), promotedItem.getWorkItem().getUniqueKey());
    }

    public void testNotIdenticalWorkItems() {
        ConflictsGuard<String> guard = new JvmConflictsGuard<>(2);

        NonBlockingOnWriteQueue<DummyWorkItem> queueSpy = spy(new NonBlockingOnWriteQueue<>(guard));

        Runnable runnable = () -> {};
        queueSpy.addToPending(new DummyWorkItem("Im not dummy"), runnable);

        WorkItemWrapper workItemWrapperMock = mock(WorkItemWrapper.class);
        when(queueSpy.acquireLockOnKey()).thenReturn(workItemWrapperMock);
        when(workItemWrapperMock.getWorkItem()).thenReturn(new DummyWorkItem("Im dummy"));
        when(workItemWrapperMock.getRunnable()).thenReturn(runnable);

        WorkQueuePromotedItem<DummyWorkItem> promotedItem = queueSpy.promote();
        List<WorkItem> identicalWorkItems = promotedItem.getWorkItem().getIdenticalWorkItems();
        Assert.assertNotNull(identicalWorkItems);
        Assert.assertEquals(identicalWorkItems.size(), 0);
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
    public void doubleRemoveSamePromotedItemDoesNotDoubleUnlock() {
        NonBlockingOnWriteQueue<DummyWorkItem> queue = new NonBlockingOnWriteQueue<>(createThrowOnSecondUnlockGuard());
        queue.addToPending(new DummyWorkItem("double-remove-key"), () -> {});

        WorkQueuePromotedItem<DummyWorkItem> promotedItem = queue.promote();
        Assert.assertNotNull(promotedItem);

        Assert.assertTrue(queue.remove(promotedItem), "First remove must succeed");
        Assert.assertTrue(queue.remove(promotedItem),
                "Second remove must return true without double-unlock (item already removed by first call)");
    }
}

