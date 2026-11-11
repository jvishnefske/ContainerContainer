package org.artifactory.schedule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.jfrog.test.JAssert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactoryConcurrentExecutorTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryStorageContext storageContext;

    private ArtifactoryConcurrentExecutor executor;

    @Mock
    private ArtifactoryTracer tracer;


    @BeforeEach
    void setUp() {

        when(storageContext.beanForType(ArtifactoryTracer.class)).thenReturn(tracer);

        ThreadPoolConfig config = ThreadPoolConfig.builder()
                .corePoolSize(2)
                .maxThreads(5)
                .keepAliveTimeout(60)
                .keepAliveTimeoutTimeUnit(TimeUnit.SECONDS)
                .queue(new LinkedBlockingQueue<>(10))
                .threadNameFormat("test-")
                .build();

        try (MockedStatic<StorageContextHelper> storageHelper = mockStatic(StorageContextHelper.class)) {
            storageHelper.when(StorageContextHelper::get).thenReturn(storageContext);
            executor = new ArtifactoryConcurrentExecutor(config);
        }

    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Executor is created per test; shut it down so the pool's worker threads (including tasks
        // that Thread.sleep in testExecute_ThrowsFullExecutorQueueException_WhenQueueFull) do not
        // leak across tests and bleed into subsequent WorkQueueImpl state in the same JVM (e.g.
        // under Pitest where tests share a minion JVM). Graceful shutdown first, then escalate to
        // shutdownNow() if the pool has not drained in time; assert the pool actually terminated
        // so a future leak surfaces as a clear failure rather than silent pollution.
        if (executor != null) {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS),
                        "ArtifactoryConcurrentExecutor did not terminate after shutdownNow()");
            }
        }
    }

    @Test
    void testExecute_SuccessfulExecution() {
        Runnable task = () -> System.out.println("Test task");

        assertDoesNotThrow(() -> executor.execute(task));
    }

    @Test
    void testExecute_WithAuthentication() {
        Authentication auth = new TestingAuthenticationToken("user", "password");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Runnable task = () -> System.out.println("Authenticated task");

        assertDoesNotThrow(() -> executor.execute(task));

        SecurityContextHolder.clearContext();
    }

    @Test
    void testExecute_ThrowsFullExecutorQueueException_WhenQueueFull() {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger log = (Logger) LoggerFactory.getLogger(ArtifactoryConcurrentExecutor.class); // Use the class under test
        log.addAppender(listAppender);
        log.setLevel(Level.DEBUG);
        listAppender.start();

        try (MockedStatic<StorageContextHelper> storageHelper = mockStatic(StorageContextHelper.class)) {
            storageHelper.when(StorageContextHelper::get).thenReturn(storageContext);

            // Fill up the executor to capacity
            for (int i = 0; i < 15; i++) { // More than pool + queue size
                try {
                    executor.execute(() -> {
                        try {
                            Thread.sleep(1000); // Keep threads busy
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } catch (FullExecutorQueueException e) {
                    // Expected when queue is full
                    break;
                }
            }
            Runnable task = () -> System.out.println("Should be rejected");

            assertThrows(FullExecutorQueueException.class, () -> executor.execute(task));
            // we do not want the exception to be caught as RejectedExecutionException,
            // it will be caught in class WorkQueueImpl.
            boolean foundLog = listAppender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN &&
                            event.getFormattedMessage().contains("was rejected by scheduler"));
            assertFalse(foundLog);
            listAppender.stop();
        }
    }

    @Test
    void testExecute_ThrowsRejectedExecutionException_WhenExecutorShutdown() {
        executor.shutdown();

        Runnable task = () -> System.out.println("Should be rejected");

        assertThrows(RejectedExecutionException.class, () -> executor.execute(task));
    }

    @Test
    void testExecute_WrapsTaskInArtifactoryRunnable() {
        Runnable originalTask = Mockito.mock(Runnable.class);

        executor.execute(originalTask);

        // Verify that the task was wrapped (indirectly by checking it was executed)
        verify(originalTask, timeout(1000)).run();
    }

    @Test
    void testExecute_DoesNotBlockWhenBlockOnInsufficientThreadsDisabled() {
        Runnable task = () -> System.out.println("Should execute normally");
        assertDoesNotThrow(() -> executor.execute(task));
    }
}