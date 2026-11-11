package org.artifactory.common.federation;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class FederatedRequestCustomContextTest {

    private FederatedRequestCustomContext context;

    @BeforeMethod
    public void setUp() {
        context = new FederatedRequestCustomContext();
        context.clear();
    }

    @AfterMethod
    public void tearDown() {
        context.clear();
    }


    @Test
    public void testInitialStateIsFalse() {
        boolean result = context.isSkipDisabledCheck();
        assertFalse(result, "Initial skipDisabledCheck should be false");
    }

    @Test
    public void testSetSkipDisabledCheckToTrue() {
        context.setSkipDisabledCheck(true);
        assertTrue(context.isSkipDisabledCheck(), "skipDisabledCheck should be true after setting to true");
    }

    @Test
    public void testSetSkipDisabledCheckToFalse() {
        context.setSkipDisabledCheck(true);
        context.setSkipDisabledCheck(false);
        assertFalse(context.isSkipDisabledCheck(), "skipDisabledCheck should be false after setting to false");
    }

    @Test
    public void testSetClearGet() {
        context.setSkipDisabledCheck(true);
        assertTrue(context.isSkipDisabledCheck(), "Value should be true before clear");

        context.clear();
        assertFalse(context.isSkipDisabledCheck(), "Value should be false after clear");
    }

    @Test
    public void testMultipleSetOperations() {
        context.setSkipDisabledCheck(true);
        assertTrue(context.isSkipDisabledCheck());

        context.setSkipDisabledCheck(false);
        assertFalse(context.isSkipDisabledCheck());

        context.setSkipDisabledCheck(true);
        assertTrue(context.isSkipDisabledCheck());

        assertTrue(context.isSkipDisabledCheck(), "Last set value should be true");
    }

    @Test
    public void testClearResetsToDefaultValue() {
        context.setSkipDisabledCheck(true);

        context.clear();
        assertFalse(context.isSkipDisabledCheck(), "After clear, value should reset to default false");
    }

    @Test
    public void testAlternatingSetClearOperations() {
        context.setSkipDisabledCheck(true);
        assertTrue(context.isSkipDisabledCheck());

        context.clear();
        assertFalse(context.isSkipDisabledCheck());

        context.setSkipDisabledCheck(true);
        assertTrue(context.isSkipDisabledCheck());
    }

    @Test
    public void testThreadLocalIsolation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean thread1Success = new AtomicBoolean(false);
        AtomicBoolean thread2Success = new AtomicBoolean(false);

        Thread thread1 = new Thread(() -> {
            try {
                context.setSkipDisabledCheck(true);
                Thread.sleep(50); // Allow time for potential interference
                thread1Success.set(context.isSkipDisabledCheck() == true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                context.clear();
                latch.countDown();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                context.setSkipDisabledCheck(false);
                Thread.sleep(50); // Allow time for potential interference
                thread2Success.set(context.isSkipDisabledCheck() == false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                context.clear();
                latch.countDown();
            }
        });

        thread1.start();
        thread2.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Threads should complete");

        assertTrue(thread1Success.get(), "Thread 1 should have true value");
        assertTrue(thread2Success.get(), "Thread 2 should have false value");
    }

    @Test
    public void testThreadLocalIndependence() throws InterruptedException {
        context.setSkipDisabledCheck(true);
        assertTrue(context.isSkipDisabledCheck(), "Main thread should have true");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean otherThreadHadDefaultValue = new AtomicBoolean(false);

        Thread otherThread = new Thread(() -> {
            try {
                otherThreadHadDefaultValue.set(context.isSkipDisabledCheck() == false);
            } finally {
                context.clear();
                latch.countDown();
            }
        });

        otherThread.start();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Thread should complete");
        assertTrue(otherThreadHadDefaultValue.get(), "Other thread should have default value (false)");
        assertTrue(context.isSkipDisabledCheck(), "Main thread should still have true");

        context.clear();
    }

    @Test
    public void testConcurrentSetsInDifferentThreads() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final boolean expectedValue = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    context.setSkipDisabledCheck(expectedValue);
                    Thread.sleep(10);
                    boolean actualValue = context.isSkipDisabledCheck();
                    if (actualValue != expectedValue) {
                        error.set(new AssertionError(
                                "Expected " + expectedValue + " but got " + actualValue));
                    }
                } catch (Exception e) {
                    error.set(e);
                } finally {
                    context.clear();
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        assertNull(error.get(), "No errors should occur in concurrent access: " +
                (error.get() != null ? error.get().getMessage() : ""));
    }

    @Test
    public void testClearInOneThreadDoesNotAffectOthers() throws InterruptedException {
        context.setSkipDisabledCheck(true);

        CountDownLatch latch = new CountDownLatch(1);
        Thread otherThread = new Thread(() -> {
            try {
                context.setSkipDisabledCheck(false);
                context.clear();
            } finally {
                latch.countDown();
            }
        });

        otherThread.start();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Thread should complete");
        assertTrue(context.isSkipDisabledCheck(), "Main thread value should be unaffected by other thread's clear");

        context.clear();
    }
}
