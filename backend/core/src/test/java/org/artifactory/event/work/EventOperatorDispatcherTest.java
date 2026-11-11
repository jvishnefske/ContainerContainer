package org.artifactory.event.work;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.event.EventType;
import org.artifactory.event.priority.service.NodeEventPriorityService;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ServerTracingScope;
import org.artifactory.storage.db.locks.service.UnsupportedReentrantLockException;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * @author Uriah Levy
 */
@Test
public class EventOperatorDispatcherTest {

    @Mock
    NodeEventPriorityService priorityService;

    @Mock
    NodeEventOperator operator;

    @Mock
    HaCommonAddon haCommonAddon;

    private final ArtifactoryTracer artifactoryTracer = new ArtifactoryTracer(
            ArtifactoryTracerConfiguration.testTracer());
    private ServerTracingScope scope;

    @Mock
    ArtifactoryContext artifactoryContext;

    @Mock
    AddonsManager addonsManager;

    @Mock
    HaAddon haAddon;
    private AutoCloseable mocks;

    @BeforeMethod
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class)).thenReturn(artifactoryTracer);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(priorityService.isDistributed()).thenReturn(true);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        scope = artifactoryTracer.startSpanOnCurrentThread("Dispatcher Test");
    }

    @AfterMethod
    public void unbind() throws Exception {
        if (scope != null) {
            scope.close();
        }
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    public void testRun() {
        when(operator.getOperatorId()).thenReturn("metadata-event-operator");
        when(operator.getNumberOfEventThreads()).thenReturn(1);
        when(operator.isOperatorEnabled()).thenReturn(true);

        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123L, "a/b/c", EventType.props,
                "metadata-event-operator", 0, ClockUtils.epochMillis(), 1, PrioritizedNodeEvent.EventStatus.PENDING);
        final List<PrioritizedNodeEvent> nodeEvents = Collections.singletonList(prioritizedNodeEvent);
        when(priorityService.getCurrentPriorityBatch(operator.getOperatorId())).thenReturn(nodeEvents);
        EventOperatorDispatcher eventOperatorDispatcher =
                spy(new EventOperatorDispatcher(priorityService, null, operator, addonsManager,
                        operator.getNumberOfEventThreads(), ContextHelper.get()));
        when(priorityService.isDistributed()).thenReturn(true);
        doNothing().when(eventOperatorDispatcher).submitSweeper(any());
        ExecutorService executorService = Executors.newFixedThreadPool(1);

        executorService.submit(eventOperatorDispatcher);
        verify(eventOperatorDispatcher, timeout(TimeUnit.SECONDS.toMillis(10)).times(1)).submitSweeper(
                any());
    }

    public void testTraceIdAttachedByExecutor() throws ExecutionException, InterruptedException {
        when(operator.getOperatorId()).thenReturn("metadata-event-operator");
        when(operator.getNumberOfEventThreads()).thenReturn(1);
        when(operator.isOperatorEnabled()).thenReturn(true);

        EventOperatorDispatcher eventOperatorDispatcher =
                spy(new EventOperatorDispatcher(priorityService, null, operator, addonsManager,
                        operator.getNumberOfEventThreads(), ContextHelper.get()));

        ExecutorService executor = eventOperatorDispatcher.getExecutor();

        // The executor has access to the Tracer - the thread should create a new tracing span
        Future<?> invocation = executor.submit(() -> assertNotNull(artifactoryTracer.getCurrentThreadActiveSpan()));
        invocation.get();
    }

    public void testDifferentTraceIds() throws ExecutionException, InterruptedException {
        when(operator.getOperatorId()).thenReturn("metadata-event-operator");
        when(operator.getNumberOfEventThreads()).thenReturn(1);
        when(operator.isOperatorEnabled()).thenReturn(true);

        EventOperatorDispatcher eventOperatorDispatcher =
                spy(new EventOperatorDispatcher(priorityService, null, operator, addonsManager,
                        operator.getNumberOfEventThreads(), ContextHelper.get()));

        ExecutorService executor = eventOperatorDispatcher.getExecutor();

        Callable<String> c1 = () -> artifactoryTracer.getCurrentSpanTraceId();
        Callable<String> c2 = () -> artifactoryTracer.getCurrentSpanTraceId();
        List<Future<String>> futures = executor.invokeAll(List.of(c1, c2));

        assertNotEquals(futures.get(0).get(), futures.get(1).get());
    }

    @Test
    public void testRunExclusiveRecoversFromUnsupportedReentrantLock() throws InterruptedException {
        when(operator.getOperatorId()).thenReturn("metadata-operator-events");
        when(operator.getNumberOfEventThreads()).thenReturn(1);

        EventOperatorDispatcher eventOperatorDispatcher =
                new EventOperatorDispatcher(priorityService, null, operator, addonsManager,
                        operator.getNumberOfEventThreads(), ContextHelper.get());

        ConflictsGuard<String> conflictsGuard = mock(ConflictsGuard.class);
        ConflictGuard mockLock = mock(ConflictGuard.class);
        when(conflictsGuard.getLock(anyString())).thenReturn(mockLock);

        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        when(mockLock.tryToLock(anyLong(), any())).thenAnswer(invocation -> {
            if (firstAttempt.getAndSet(false)) {
                throw new IllegalStateException("Failed to acquire lock",
                        new UnsupportedReentrantLockException());
            }
            return true;
        });

        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        String lockKey = "docker-elastic-cache/elasticsearch/elasticsearch/7.9.0/manifest.json";
        eventOperatorDispatcher.runExclusive(conflictsGuard, lockKey, () -> taskExecuted.set(true));

        verify(conflictsGuard).forceUnlock(lockKey);
        verify(mockLock).unlock();
        assertTrue(taskExecuted.get(), "Task should run after stale lock recovery");
    }

    @Test
    public void testRunExclusiveRunsTaskWhenLockAcquired() throws InterruptedException {
        EventOperatorDispatcher dispatcher = newDispatcher();

        ConflictsGuard<String> conflictsGuard = mock(ConflictsGuard.class);
        ConflictGuard mockLock = mock(ConflictGuard.class);
        when(conflictsGuard.getLock(anyString())).thenReturn(mockLock);
        when(mockLock.tryToLock(anyLong(), any())).thenReturn(true);

        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        String lockKey = "npm-remote-cache/lodash/-/lodash-4.17.21.tgz";
        dispatcher.runExclusive(conflictsGuard, lockKey, () -> taskExecuted.set(true));

        assertTrue(taskExecuted.get(), "Task should run when the lock is acquired on the first attempt");
        verify(mockLock).unlock();
        verify(conflictsGuard, never()).forceUnlock(anyString());
    }

    @Test
    public void testRunExclusiveRecoversFromDirectUnsupportedReentrantLock() throws InterruptedException {
        EventOperatorDispatcher dispatcher = newDispatcher();

        ConflictsGuard<String> conflictsGuard = mock(ConflictsGuard.class);
        ConflictGuard mockLock = mock(ConflictGuard.class);
        when(conflictsGuard.getLock(anyString())).thenReturn(mockLock);

        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        when(mockLock.tryToLock(anyLong(), any())).thenAnswer(invocation -> {
            if (firstAttempt.getAndSet(false)) {
                // Unwrapped reentrant failure (detected at the top of the cause chain)
                throw new UnsupportedReentrantLockException();
            }
            return true;
        });

        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        String lockKey = "npm-remote-cache/express/-/express-4.18.2.tgz";
        dispatcher.runExclusive(conflictsGuard, lockKey, () -> taskExecuted.set(true));

        verify(conflictsGuard).forceUnlock(lockKey);
        verify(mockLock).unlock();
        assertTrue(taskExecuted.get(), "Task should run after recovering from a direct reentrant lock error");
    }

    @Test
    public void testRunExclusiveRethrowsNonReentrantException() throws InterruptedException {
        EventOperatorDispatcher dispatcher = newDispatcher();

        ConflictsGuard<String> conflictsGuard = mock(ConflictsGuard.class);
        ConflictGuard mockLock = mock(ConflictGuard.class);
        when(conflictsGuard.getLock(anyString())).thenReturn(mockLock);
        when(mockLock.tryToLock(anyLong(), any()))
                .thenThrow(new IllegalStateException("Failed to acquire lock", new RuntimeException("db is down")));

        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        String lockKey = "npm-remote-cache/react/-/react-18.2.0.tgz";

        expectThrows(RuntimeException.class,
                () -> dispatcher.runExclusive(conflictsGuard, lockKey, () -> taskExecuted.set(true)));

        assertFalse(taskExecuted.get(), "Task must not run when the lock is never acquired");
        verify(conflictsGuard, never()).forceUnlock(anyString());
    }

    @Test
    public void testRunExclusivePropagatesWhenRetryAlsoFails() throws InterruptedException {
        EventOperatorDispatcher dispatcher = newDispatcher();

        ConflictsGuard<String> conflictsGuard = mock(ConflictsGuard.class);
        ConflictGuard mockLock = mock(ConflictGuard.class);
        when(conflictsGuard.getLock(anyString())).thenReturn(mockLock);
        // Both the initial attempt and the single retry fail with a reentrant lock error
        when(mockLock.tryToLock(anyLong(), any())).thenAnswer(invocation -> {
            throw new IllegalStateException("Failed to acquire lock", new UnsupportedReentrantLockException());
        });

        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        String lockKey = "npm-remote-cache/webpack/-/webpack-5.90.0.tgz";

        expectThrows(RuntimeException.class,
                () -> dispatcher.runExclusive(conflictsGuard, lockKey, () -> taskExecuted.set(true)));

        // Force-unlock is attempted exactly once; a second failure propagates to the caller
        verify(conflictsGuard).forceUnlock(lockKey);
        assertFalse(taskExecuted.get(), "Task must not run when the retry also fails to acquire the lock");
    }

    private EventOperatorDispatcher newDispatcher() {
        when(operator.getOperatorId()).thenReturn("metadata-operator-events");
        when(operator.getNumberOfEventThreads()).thenReturn(1);
        return new EventOperatorDispatcher(priorityService, null, operator, addonsManager,
                operator.getNumberOfEventThreads(), ContextHelper.get());
    }
}