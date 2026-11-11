package org.artifactory.event.work;

import com.google.common.base.Suppliers;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.security.JvmConflictGuardProvider;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.event.priority.service.NodeEventPriorityService;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.event.provider.EventProvider;
import org.artifactory.storage.db.locks.service.DbLocksService;
import org.artifactory.storage.db.locks.service.UnsupportedReentrantLockException;
import org.artifactory.repo.event.NodeEvent;
import org.artifactory.util.ExceptionUtils;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.concurrency.LockingUtilImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.artifactory.event.priority.service.NodeEventPriorityServiceImpl.MAX_RETRIES;

/**
 * Dispatches event-handling threads and manages work using a {@link NodeEventOperator}.
 * The following aspects are handled:
 * 1. Event exclusivity using cluster-wide locking
 * 2. Concurrency control
 * 3. Basic error tracking - disables events after a certain threshold is reached
 *
 * @author Uriah Levy
 */
class EventOperatorDispatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(EventOperatorDispatcher.class);
    private static final String DISPATCHER_LOCK_KEY_SUFFIX = "dispatcher";
    private static final String LOCK_CATEGORY = "node-event-task-manager";
    private final Semaphore semaphore;
    private final Object workMonitor = new Object();
    private final ReentrantLock errorThresholdLock = new ReentrantLock();
    private final NodeEventPriorityService nodeEventPriorityService;
    private final EventProvider<? extends NodeEvent> eventProvider;
    private final NodeEventOperator nodeEventOperator;
    private final AddonsManager addonsManager;
    private boolean active = true;
    private final Supplier<ConflictsGuard<String>> defaultConflictsGuardSupplier;
    private final Supplier<ConflictsGuard<String>> jvmConflictsGuardSupplier;
    private final int maxPermits;
    private final int maxBatchErrors;
    private AtomicInteger batchErrors = new AtomicInteger();
    private List<PrioritizedNodeEvent> currentEventBatch = new ArrayList<>();
    private final ExecutorService executor;
    private final ExecutorService dispatcherExecutor;
    private final ArtifactoryContext artifactoryContext;
    private final NodeEventsErrorService nodeEventsErrorService;
    private final ExecutionUtils.RetryOptions eventSweeperRetryOptions = getEventSweeperRetryOptions();

    EventOperatorDispatcher(NodeEventPriorityService nodeEventPriorityService,
            EventProvider<? extends NodeEvent> eventProvider, NodeEventOperator operator, AddonsManager addonsManager,
            int numOfThreads, ArtifactoryContext artifactoryContext) {
        this.nodeEventPriorityService = nodeEventPriorityService;
        this.eventProvider = eventProvider;
        this.nodeEventOperator = operator;
        this.addonsManager = addonsManager;
        this.defaultConflictsGuardSupplier = Suppliers.memoize(this::getDefaultConflictsGuard);
        this.jvmConflictsGuardSupplier = Suppliers.memoize(this::getJvmConflictsGuard);
        this.maxPermits = numOfThreads;
        this.maxBatchErrors = numOfThreads * (MAX_RETRIES - 1);
        this.semaphore = new Semaphore(numOfThreads);
        this.artifactoryContext = artifactoryContext;
        this.executor = getExecutor(operator, numOfThreads, artifactoryContext, "sweeper");
        this.dispatcherExecutor = getExecutor(operator, 1, artifactoryContext, "dispatcher");
        this.nodeEventsErrorService = artifactoryContext.beanForType(NodeEventsErrorService.class);
    }

    public void start() {
        dispatcherExecutor.execute(this);
    }

    @Override
    public void run() {
        try {
            dispatchLoop();
        } finally {
            stopSweepers();
        }
    }

    void batchError() {
        if (batchErrors.incrementAndGet() >= maxBatchErrors) {
            locallyExclusive(this::notifyOperatorOnErrorThreshold);
        }
    }

    void submitSweeper(List<PrioritizedNodeEvent> currentEventBatch) {
        log.debug("Submitting event sweeper with {} events", currentEventBatch.size());
        ConflictsGuard<String> conflictsGuard =
                !nodeEventPriorityService.isDistributed() ? jvmConflictsGuardSupplier.get() :
                        defaultConflictsGuardSupplier.get();
        log.debug("Using {} for event sweeper", conflictsGuard.getClass());
        executor.submit(new NodeEventSweeper(nodeEventPriorityService, nodeEventOperator, this, currentEventBatch,
                eventSweeperRetryOptions, nodeEventsErrorService, conflictsGuard));
    }

    /**
     * Called by the sweepers once (and only once), when a sweeper finishes working.
     */
    void sweeperDone() {
        synchronized (workMonitor) {
            workMonitor.notifyAll();
        }
        semaphore.release();
    }

    void stopDispatcher() {
        active = false;
        stopSweepers();
    }

    ExecutorService getExecutor() {
        return executor;
    }

    private void notifyOperatorOnErrorThreshold() {
        log.warn("Event error threshold reached for operator '{}'. Notifying operator.",
                nodeEventOperator.getOperatorId());
        nodeEventOperator.onErrorThresholdReached();
        batchErrors.set(0);
    }

    private void locallyExclusive(Runnable function) {
        try {
            boolean lockAcquired = errorThresholdLock.tryLock();
            if (lockAcquired) {
                log.debug("Local error-threshold exclusive lock acquired");
                function.run();
            }
        } finally {
            if (errorThresholdLock.isHeldByCurrentThread()) {
                errorThresholdLock.unlock();
                log.debug("Local error-threshold exclusive lock released");
            }
        }
    }

    private void dispatchLoop() {
        while (active) {
            try {
                if (nodeEventPriorityService.isDistributed()) {
                    work();
                } else {
                    // If the priority service is not distributed, the dispatcher should operate within a lock
                    runExclusive(defaultConflictsGuardSupplier.get(),
                            getLockKeyForOperation(DISPATCHER_LOCK_KEY_SUFFIX), this::work);
                }
            } finally {
                pause();
            }
        }
    }

    /**
     * Creates the prioritized list of events, and dispatches work sweepers in a semi back-pressured manner.
     */
    private void work() {
        synchronized (workMonitor) {
            while (shouldContinue()) {
                try {
                    if (nodeEventOperator.isOperatorEnabled()) {
                        createPrioritiesIfNeeded();
                        submitSweepers();
                    }
                    // Wait for workers to signal work done
                    workMonitor.wait(getInterval());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("Exception during the '{}' dispatch loop. The dispatcher will continue execution. " +
                            "message : {}", nodeEventOperator.getOperatorId(), e.getMessage());
                    log.debug("", e);
                    try {
                        workMonitor.wait(getInterval());
                    } catch (InterruptedException ie) {
                        log.warn("'{}' dispatcher thread caught interrupted exception. Terminating",
                                nodeEventOperator.getOperatorId());
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private boolean shouldContinue() {
        if (!active) {
            return false;
        }
        AddonsManager addonsManager = artifactoryContext.beanForType(AddonsManager.class);
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        if (nodeEventPriorityService.isDistributed() || !haAddon.isHaEnabled()) {
            return active;
        }
        // If not distributed and HA, stop the work if the dispatcher lock was taken by some other node
        DbLocksService dbLocksService = artifactoryContext.beanForType(DbLocksService.class);
        return dbLocksService.isLockedByMe(LOCK_CATEGORY, getLockKeyForOperation(DISPATCHER_LOCK_KEY_SUFFIX));
    }

    private void pause() {
        try {
            Thread.sleep(getOperatorInterval());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Dispatcher thread for '{}' interrupted", nodeEventOperator.getOperatorId());
        }
    }

    private int getNumberOfPendingEventsFromCurrentBatch() {
        return (int) currentEventBatch.stream()
                .filter(PrioritizedNodeEvent::isPending).count();
    }

    private long getInterval() {
        if (nodeEventOperator.isMigrating()) {
            return 1; // when migration is on, sleep you shall none!
        }
        return getOperatorInterval();
    }

    private long getOperatorInterval() {
        return TimeUnit.SECONDS.toMillis(nodeEventOperator.getDispatcherIntervalSecs());
    }

    private void submitSweepers() {
        int activeWorkers = maxPermits - semaphore.availablePermits();
        if (!hasPendingEvents()) {
            // refill
            currentEventBatch = nodeEventPriorityService
                    .getCurrentPriorityBatch(nodeEventOperator.getOperatorId());
        }
        while (getNumberOfPendingEventsFromCurrentBatch() > activeWorkers && semaphore.tryAcquire()) {
            submitSweeper(currentEventBatch);
            activeWorkers = maxPermits - semaphore.availablePermits();
        }
    }

    private boolean hasPendingEvents() {
        return getNumberOfPendingEventsFromCurrentBatch() > 0;
    }

    private void createPrioritiesIfNeeded() {
        if (semaphore.availablePermits() == maxPermits) {
            try {
                if (nodeEventPriorityService.priorityListIsEmpty(nodeEventOperator.getOperatorId())) {
                    runExclusive(defaultConflictsGuardSupplier.get(), getLockKeyForOperation("priorities"),
                            () -> doCreatePriorities(nodeEventOperator.getOperatorId()));
                }
            } catch (Exception e) {
                log.error("Caught exception while creating the prioritized event list.", e);
            }
        }
    }

    private void doCreatePriorities(String operatorId) {
        if (nodeEventPriorityService.priorityListIsEmpty(operatorId)) {
            nodeEventPriorityService.createAndSaveEventPrioritiesIfNeeded(nodeEventOperator, eventProvider);
            batchErrors = new AtomicInteger();
            currentEventBatch = new ArrayList<>(); // reset current batch
        }
    }

    void runExclusive(ConflictsGuard<String> conflictsGuard, String lockKey, Runnable task) {
        try {
            clusterExclusive(conflictsGuard, lockKey, task);
        } catch (Exception e) {
            if (isUnsupportedReentrantLock(e)) {
                log.warn(
                        "Reentrant or stale DB lock for operator '{}' on key '{}', force-unlocking and retrying once",
                        nodeEventOperator.getOperatorId(), lockKey, e);
                conflictsGuard.forceUnlock(lockKey);
                clusterExclusive(conflictsGuard, lockKey, task);
                return;
            }
            throw e;
        }
    }

    private void clusterExclusive(ConflictsGuard<String> conflictsGuard, String lockKey, Runnable task) {
        new LockingUtilImpl().clusterExclusive(conflictsGuard, lockKey, task);
    }

    private static boolean isUnsupportedReentrantLock(Throwable e) {
        return ExceptionUtils.getCauseOfType(e, UnsupportedReentrantLockException.class) != null;
    }

    private ConflictsGuard<String> getDefaultConflictsGuard() {
        log.debug("Using default conflicts guard");
        return addonsManager.addonByType(HaCommonAddon.class).getConflictsGuard(LOCK_CATEGORY);
    }

    private ConflictsGuard<String> getJvmConflictsGuard() {
        log.debug("Using JVM conflicts guard");
        return new JvmConflictGuardProvider().getConflictsGuard(LOCK_CATEGORY);
    }

    private String getLockKeyForOperation(String lockSuffix) {
        return nodeEventOperator.getOperatorId() + "-" + lockSuffix;
    }

    private void stopSweepers() {
        try {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Unable to stop dispatched sweepers", e);
            Thread.currentThread().interrupt();
        }
    }

    private ArtifactoryExecutor getExecutor(NodeEventOperator operator, int numOfThreads,
            ArtifactoryContext artifactoryContext, String namingSuffix) {
        return ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(numOfThreads)
                .maxThreads(numOfThreads)
                .keepAliveTimeout(5L)
                .keepAliveTimeoutTimeUnit(TimeUnit.MINUTES)
                .daemon(true)
                .queue(new LinkedBlockingQueue<>())
                .threadNameFormat(operator.getOperatorId() + "-event-" + namingSuffix + "-%s")
                .build(), artifactoryContext, false, false);
    }

    private ExecutionUtils.RetryOptions getEventSweeperRetryOptions() {
        return ExecutionUtils.RetryOptions.builder()
                .backoffMaxDelay(6000)
                .exponentialBackoffMultiplier(3)
                .numberOfRetries(MAX_RETRIES)
                .timeout(1500)
                .build();
    }
}