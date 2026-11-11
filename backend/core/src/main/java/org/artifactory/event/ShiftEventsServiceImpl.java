package org.artifactory.event;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.db.locks.service.UnsupportedReentrantLockException;
import org.artifactory.storage.db.security.service.BasicCacheModel;
import org.artifactory.storage.db.security.service.VersionCache;
import org.artifactory.storage.db.security.service.VersioningCacheImpl;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.event.EventsService;
import org.artifactory.storage.event.ShiftEventsService;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.concurrency.LockingUtilImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.artifactory.concurrent.NonStandardLockCategory.FED_SHIFT_EVENTS;


/**
 * The thread will sleep 30 sec or until will signalled for shifts
 * If 30 sec elapsed or got shift triggering (version promoted), a shift will invoked.
 * Shift may be locked for max 120 sec trying to get distributed lock.
 * If distributed lock was not acquired, a new shift will be invoked right away.
 *
 * @author shaharz
 */

@Service
public class ShiftEventsServiceImpl implements Runnable, ShiftEventsService, ContextCreationListener {

    private static final Logger log = LoggerFactory.getLogger(ShiftEventsServiceImpl.class);
    private static final long TRY_LOCK_TIMEOUT_MS = ConstantValues.shiftEventsTryLockTimeoutMs.getInt();
    private static final long PERIODIC_SHIFT_MS = ConstantValues.shiftEventsPeriodicMs.getInt();
    private static final BasicCacheModel CACHE_MODEL = new BasicCacheModel() {
        @Override
        public long getVersion() {
            return 0;
        }

        @Override
        public void setVersion(long version) {
        }

        @Override
        public void destroy() {
        }
    };
    @VisibleForTesting
    static final String SHIFT_EVENT_LOCK_KEY = "shift-events";

    private final ConflictsGuard<String> conflictsGuard;
    private volatile boolean run = true;

    @VisibleForTesting
    final ThreadPoolExecutor executor;
    @VisibleForTesting
    final EventsService eventsService;
    @VisibleForTesting
    final QueueTriggeringService queueTriggeringService;
    @VisibleForTesting
    final VersionCache<?> versionCache;
    @VisibleForTesting
    final Object lock = new Object();

    private final String isolatedMemberName;
    // Lazily resolved and cached: null until first checked. isolatedMemberName and this node's own server id are
    // both fixed for the life of the JVM (changing either requires a restart), so this identity fact never needs
    // to be re-derived once known - only accessed from the single shift-events-thread, so no synchronization needed.
    private Boolean isIsolatedMember;

    private static final LockingUtilImpl lockingUtil = new LockingUtilImpl();

    public ShiftEventsServiceImpl(AddonsManager addonsManager, EventsService eventsService,
            QueueTriggeringService queueTriggeringService) {
        this.conflictsGuard = addonsManager.addonByType(HaCommonAddon.class)
                .getConflictsGuard(FED_SHIFT_EVENTS.getName());

        this.eventsService = eventsService;
        this.queueTriggeringService = queueTriggeringService;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new BasicThreadFactory.Builder().namingPattern("shift-events-thread").build());
        this.versionCache = new VersioningCacheImpl<>(TRY_LOCK_TIMEOUT_MS, () -> {
            shiftEvents();
            return CACHE_MODEL;
        }, "shiftEventsCache");
        this.isolatedMemberName = ConstantValues.shiftEventsIsolatedMember.getString();
    }

    @Override
    public void onContextCreated() {
        start();
    }

    @Override
    public void run() {
        log.debug("Thread started...");
        while (run) {
            try {
                versionCache.get();
                if (run) {
                    synchronized (lock) {
                        // if there is no new version:
                        // it means that the shift was not executed due to try lock timeout
                        // in that case we don't want to sleep for 30 sec, we want to try shift again
                        if (!versionCache.hasNewVersion()) {
                            await();
                            // promote the version after sleep, so it will try to shift
                            versionCache.promoteVersion();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (UnsupportedReentrantLockException e) {
                log.error("Unexpected error", e);
                forceReleaseReentrantLock();
            } catch (Exception e) {
                log.error("Unexpected error", e);
                try {
                    synchronized (lock) {
                        await();
                    }
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.debug("Thread ended.");
    }

    @Override
    public void triggerShift() {
        synchronized (lock) {
            int version = versionCache.promoteVersion();
            log.debug("Shift events triggered, version: {}", version);
            lock.notifyAll();
        }
    }

    @PreDestroy
    void stop() throws InterruptedException {
        synchronized (lock) {
            run = false;
            lock.notifyAll();
        }
        executor.shutdown();
        log.info("Shutdown terminated: {}", executor.awaitTermination(5, TimeUnit.SECONDS));
        log.debug("Stopped.");
    }

    @VisibleForTesting
    void shiftEvents() {
        log.debug("Shifting events...");
        if (!StringUtils.isBlank(isolatedMemberName)) {
            HaAddon haAddon = ContextHelper.get().beanForType(HaAddon.class);
            Optional<String> propagationTarget = resolveIsolatedPropagationTarget(haAddon);
            if (propagationTarget.isPresent()) {
                // Not this node's job this cycle: notify the isolated member directly. No distributed lock is
                // taken for this - it's a lightweight REST trigger, not real work, and taking the lock here was
                // the root cause of RTFS-4802 (a non-isolated node could hold the shared lock for the whole
                // propagate call, starving the isolated member from ever winning it to do the actual shift).
                SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
                securityService.doAsSystem(() -> haAddon.propagateShiftEventsOperation(propagationTarget.get()));
                return;
            }
        }
        lockingUtil.clusterExclusive(conflictsGuard, SHIFT_EVENT_LOCK_KEY, () -> {
            Instant start = Instant.now();
            runShiftEvents();
            Duration duration = Duration.between(start, Instant.now());
            log.debug("shift events was locked for: {} ms", duration.toMillis());
        });
    }

    @VisibleForTesting
    Optional<String> resolveIsolatedPropagationTarget(HaAddon haAddon) {
        if (!haAddon.isHaEnabled()) {
            log.trace("Although configured isolated member, ha is not enabled. " +
                    " Running on this server shift events");
            return Optional.empty();
        }
        if (isCurrentNodeIsolatedMember(haAddon)) {
            log.debug("Request for shift events reached the isolated member {}. Triggering shift events",
                    isolatedMemberName);
            return Optional.empty();
        }
        // RTFS-4802: check both the stored state AND heartbeat freshness. The state column alone is not
        // reliable for detecting a crashed peer - nothing updates it away from RUNNING for a node that dies
        // without a graceful shutdown, so a hard-killed isolated member would otherwise look "RUNNING" until
        // the (3-hour-default) stale-server cleanup job eventually deletes its row. isRunning/hasHeartbeat are
        // the same predicates ArtifactoryServersCommonService's own liveness-sensitive methods
        // (getOtherRunningHaMembers, getActiveMembers, etc.) already use, checked against
        // ha.heartbeat.staleSecs (30s default), computed live rather than persisted.
        Optional<ArtifactoryServer> isolatedHaMember = haAddon.getAllArtifactoryServers().stream()
                .filter(artifactoryServer -> artifactoryServer.getServerId().equals(isolatedMemberName))
                .filter(ArtifactoryServersCommonService.isRunning)
                .filter(ArtifactoryServersCommonService.hasHeartbeat)
                .findFirst();
        if (isolatedHaMember.isEmpty()) {
            log.warn("Isolated member {} not found or not running. Starting shift event on this server",
                    isolatedMemberName);
            return Optional.empty();
        }
        log.debug("Propagating shift event operation to the isolated member {}", isolatedHaMember.get());
        return Optional.of(isolatedHaMember.get().getServerId());
    }

    @VisibleForTesting
    boolean isCurrentNodeIsolatedMember(HaAddon haAddon) {
        if (isIsolatedMember == null) {
            isIsolatedMember = isolatedMemberName.equals(haAddon.getCurrentMemberServerId());
        }
        return isIsolatedMember;
    }

    private void runShiftEvents() {
        Set<String> repoKeys = Set.of();
        Instant loopStart = now();
        long leaseMs = TimeUnit.MINUTES.toMillis(ConstantValues.haShiftEventsLockLeasePeriodMinutes.getLong());
        double breakThresholdRatio = ConstantValues.shiftEventsLockLeaseBreakThresholdPercent.getInt() / 100.0;
        long maxLoopDurationMs = (long) (leaseMs * breakThresholdRatio);
        try {
            // Copy the events from node_events_tmp to node_events and clear the node_events_tmp table in one TX.
            do {
                repoKeys = eventsService.shiftEvents();
                queueTriggeringService.triggerConstantQueues();
                if (!repoKeys.isEmpty()) {
                    // trigger the queues for the effected repos key
                    queueTriggeringService.triggerQueues(repoKeys);
                    log.debug("Queues triggered: {}", repoKeys);
                }
                if (!repoKeys.isEmpty() && Duration.between(loopStart, now()).toMillis() > maxLoopDurationMs) {
                    // Backlog isn't drained yet but we're close to the lock lease expiring: release the lock now
                    // (rather than risk the orphan-lock reaper force-releasing it mid-copy, which would let
                    // another cluster member pick up the same lock and reprocess the same rows) and immediately
                    // retrigger ourselves so the remaining backlog is resumed in a fresh lock cycle right away.
                    log.warn("Shift events ran for more than {}% of the {}-minute lock lease. Breaking out early "
                                    + "to release the lock before it expires; remaining backlog will be picked up "
                                    + "immediately in a new cycle.",
                            ConstantValues.shiftEventsLockLeaseBreakThresholdPercent.getInt(),
                            ConstantValues.haShiftEventsLockLeasePeriodMinutes.getLong());
                    triggerShift();
                    break;
                }
            } while (!repoKeys.isEmpty());
        } catch (RuntimeException e) {
            log.error("Failed to perform shift events: {} ",
                    repoKeys.isEmpty() ? e.getMessage() : " for repos: " + repoKeys + ". Error: " + e.getMessage(), e);
        }
    }

    @VisibleForTesting
    Instant now() {
        return Instant.now();
    }

    @VisibleForTesting
    void await() throws InterruptedException {
        log.debug("About to wait {} ms.", PERIODIC_SHIFT_MS);
        lock.wait(PERIODIC_SHIFT_MS);
    }

    /**
     * Force unlock on reentrant lock with infinite retry
     */
    private void forceReleaseReentrantLock() {
        try {
            conflictsGuard.forceUnlock(SHIFT_EVENT_LOCK_KEY);
        } catch (Exception e) {
            log.error("Failed to force unlock", e);
            try {
                synchronized (lock) {
                    await();
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void start() {
        synchronized (lock) {
            if (executor.getActiveCount() == 0) {
                executor.submit(this);
                log.debug("Started.");
            }
        }
    }
}
