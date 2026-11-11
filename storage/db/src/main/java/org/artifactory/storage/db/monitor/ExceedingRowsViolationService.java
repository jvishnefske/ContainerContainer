package org.artifactory.storage.db.monitor;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.spring.ContextReadinessListener;
import org.artifactory.storage.db.util.UniversalLimitHandlerUtility;
import org.jfrog.common.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Persists exceeding-rows violation records (queries whose result sets exceeded the configured
 * row-count threshold) into the {@code system_states} table. One row per route key is kept;
 * a new violation for the same route overwrites the previous one via upsert.
 * Writes are submitted to a single-thread executor to avoid blocking the query thread.
 * Old records are cleaned up on each write, rate-limited to at most once per hour.
 *
 * <p>On startup, a dirty-shutdown marker (see {@link DirtyShutdownMarker}) is consulted to
 * determine whether the previous run ended unexpectedly (OOM kill, {@code kill -9}, or any
 * termination that bypassed {@code @PreDestroy} hooks). If it did, recent exceeding-rows
 * violations are loaded and installed as a blacklist on {@link UniversalLimitHandlerUtility}.
 * The marker itself is created on each startup and deleted on graceful shutdown, so only
 * unexpected terminations leave it in place for the next startup to find.
 *
 * @author Eli Fishman
 */
@Slf4j
@Service
public class ExceedingRowsViolationService implements ContextReadinessListener {

    static final String USE_CASE = "exceeding.rows.violation";
    private static final long CLEANUP_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);
    private static final long DIRTY_LOOKBACK_MS = TimeUnit.HOURS.toMillis(1);
    private static final int QUEUE_CAPACITY = 100;

    private final SystemStatesDao systemStatesDao;
    private final ExecutorService executor;
    private final AtomicLong lastCleanupTimeMs = new AtomicLong(0);
    private final AtomicLong droppedViolationCount = new AtomicLong(0);
    /**
     * Holds the OOM blacklist detected during {@link #onStartup()} so it can be propagated to
     * other HA nodes in {@link #onContextReady()}, after the Access service has fully initialized
     * and is able to issue communication tokens.
     */
    private final AtomicReference<Set<String>> pendingHaPropagation = new AtomicReference<>(Set.of());

    /** Resolved dirty-shutdown marker path for this node; set in {@link #onStartup()}. */
    private volatile File markerFile;

    @Autowired
    public ExceedingRowsViolationService(SystemStatesDao systemStatesDao) {
        this(systemStatesDao, createBoundedExecutor());
    }

    private static ExecutorService createBoundedExecutor() {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                r -> new Thread(r, "exceeding-rows-violation-service"));
    }

    ExceedingRowsViolationService(SystemStatesDao systemStatesDao, ExecutorService executor) {
        this.systemStatesDao = systemStatesDao;
        this.executor = executor;
    }

    @PreDestroy
    void shutdown() {
        DirtyShutdownMarker.deleteIfExists(markerFile);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * On startup, checks whether the previous run ended unexpectedly by consulting the
     * per-node dirty-shutdown marker (see {@link DirtyShutdownMarker}). If the marker is
     * present, the previous termination was unexpected and recent exceeding-rows violations
     * are loaded to build a blacklist on {@link UniversalLimitHandlerUtility}. The marker
     * is then created (or kept) for the current run; it is deleted by {@link #shutdown()}
     * on graceful shutdown.
     */
    @PostConstruct
    void onStartup() {
        if (!ConstantValues.queryMonitorExceedingRowsViolationEnableRemedy.getBoolean()) {
            return;
        }
        try {
            markerFile = resolveMarkerFile();
            boolean previousRunWasDirty = markerFile != null && markerFile.exists();
            DirtyShutdownMarker.createIfAbsent(markerFile);

            if (!previousRunWasDirty) {
                log.debug("Dirty-shutdown marker did not exists, hence startup is clean.");
                return;
            }
            log.warn("Dirty-shutdown marker detected at {}. "
                    + "Previous run ended unexpectedly. "
                    + "Loading recent exceeding-rows violations to build blacklist.",
                    markerFile.getAbsolutePath());
            Set<String> blacklist = loadBlacklistFromViolations();
            if (!blacklist.isEmpty()) {
                UniversalLimitHandlerUtility.setOomBlacklist(blacklist);
                log.warn("Dirty-shutdown recovery blacklist activated with {} pattern(s): {}",
                        blacklist.size(), blacklist);
                pendingHaPropagation.set(blacklist);
            } else {
                log.warn("Dirty-shutdown marker detected but no matching violations found "
                        + "within the last {} minute(s). No blacklist applied.",
                        TimeUnit.MILLISECONDS.toMinutes(DIRTY_LOOKBACK_MS));
            }
        } catch (Exception e) {
            log.warn("Dirty-shutdown recovery check failed,", e);
        }
    }

    /**
     * Delegates to {@link DirtyShutdownMarker#getMarkerForCurrentNode()} (null-safe).
     * Kept as a package-private wrapper so unit tests can override the
     * resolved path via a Mockito spy.
     */
    File resolveMarkerFile() {
        return DirtyShutdownMarker.getMarkerForCurrentNode();
    }

    /**
     * Propagates the OOM blocklist detected during {@link #onStartup()} to the other HA nodes.
     * Deferred to context-ready time so that {@code AccessServiceImpl} has finished initializing
     * and can issue the communication token required by {@code HaPropagationServiceImpl}.
     */
    @Override
    public void onContextReady() {
        Set<String> toPropagate = pendingHaPropagation.getAndSet(Set.of());
        if (!toPropagate.isEmpty()) {
            notifyOtherNodes(toPropagate);
        }
    }

    /**
     * Best-effort: notify the other HA nodes to add the same blocklist patterns to their effective
     * blocklist, so the whole cluster enforces the limit identically instead of waiting for each node
     * to independently OOM and recover. No-op in OSS / single-node deployments, where the addon
     * either resolves to a no-op or has no other running members to propagate to.
     * <p>
     * Runs inside a system security context: HA propagation submits the request on a worker thread
     * that copies the submitting thread's {@code Authentication}, and {@code HaPropagationServiceImpl}
     * dereferences {@code UserGroupService.currentUser()} when adding the mandatory HA auth headers.
     * Without a bound principal (as during startup) that call NPEs, so we bind the system user here.
     */
    void notifyOtherNodes(Set<String> blacklist) {
        try {
            Optional<HaCommonAddon> haCommonAddon = resolveHaCommonAddon();
            haCommonAddon.ifPresent(addon ->
                    resolveSecurityService().doAsSystem(() ->
                            addon.propagateOomQueryBlockListChange(blacklist)));
        } catch (Exception e) {
            log.warn("Failed to notify other nodes of OOM query blocklist activation", e);
        }
    }

    /**
     * Resolves the HA addon used to propagate the blocklist to peers. Kept as a package-private
     * wrapper so unit tests can supply a mock via a Mockito spy. Returns an empty {@link Optional}
     * when the addon is not available ({@code addonByType} is not {@code @Nonnull}).
     */
    @VisibleForTesting
    Optional<HaCommonAddon> resolveHaCommonAddon() {
        return Optional.ofNullable(
                ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaCommonAddon.class));
    }

    /**
     * Resolves the security service used to bind a system principal around HA propagation. Kept as a
     * package-private wrapper so unit tests can supply a mock via a Mockito spy.
     */
    @VisibleForTesting
    SecurityService resolveSecurityService() {
        return ContextHelper.get().beanForType(SecurityService.class);
    }

    private Set<String> loadBlacklistFromViolations() throws Exception {
        long cutoffMs = System.currentTimeMillis() - DIRTY_LOOKBACK_MS;
        List<SystemStateRecord> records = systemStatesDao.findByUseCaseSince(USE_CASE, cutoffMs);
        return records.stream()
                .map(SystemStateRecord::id)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * Asynchronously persists an exceeding-rows violation record into the {@code system_states}
     * table. If the bounded queue is full the record is silently dropped and the drop count
     * is logged at WARN level (rate-limited to every 100th drop).
     */
    public void persistViolationAsync(DbQueryContext context, long rowsCount) {
        try {
            executor.execute(() -> persistViolation(context, rowsCount));
        } catch (RejectedExecutionException e) {
            long dropped = droppedViolationCount.incrementAndGet();
            if (dropped == 1 || dropped % 100 == 0) {
                log.warn("Dropped exceeding-rows violation record "
                        + "(queue full, total dropped: {})", dropped);
            }
        }
    }

    void persistViolation(DbQueryContext context, long rowsCount) {
        try {
            long now = System.currentTimeMillis();
            String nodeId = resolveNodeId();
            String routeKey = context.getRouteKey();

            ExceedingRowsViolationRecord exceedingRowsViolationRecord = ExceedingRowsViolationRecord.builder()
                    .query(context.getQuery())
                    .exceedingRowsCount(rowsCount)
                    .threshold(ConstantValues.queryMonitorExceedingRowsThreshold.getLong())
                    .actionName(context.getActionName())
                    .actionKind(context.getActionKind())
                    .actionGroup(context.getActionGroup())
                    .actionPackage(context.getActionPackage())
                    .actionActor(context.getActionActor())
                    .hint(context.getHint())
                    .streaming(context.isStreaming())
                    .routeKey(routeKey)
                    .build();

            String recordAsJson = JsonUtils.getInstance().valueToString(exceedingRowsViolationRecord);
            systemStatesDao.upsert(USE_CASE, routeKey, now, nodeId, context.getActionActor(), recordAsJson);
            log.debug("Persisted exceeding-rows violation: routeKey={}", routeKey);

            cleanupIfDue(now);
        } catch (Exception e) {
            log.warn("Failed to persist exceeding-rows violation record: {}",
                    e.getMessage());
            log.debug("Persistence failure details", e);
        }
    }

    /**
     * Deletes violation records older than the configured retention period.
     */
    public int cleanupOldViolations() {
        try {
            long retentionDays =
                    ConstantValues.queryMonitorExceedingRowsViolationRetentionDays.getLong();
            long cutoffMs = System.currentTimeMillis()
                    - TimeUnit.DAYS.toMillis(retentionDays);
            int deleted = systemStatesDao.deleteByUseCaseOlderThan(USE_CASE, cutoffMs);
            if (deleted > 0) {
                log.info(
                        "Cleaned up {} exceeding-rows violation records older than {} days",
                        deleted, retentionDays);
            }
            return deleted;
        } catch (Exception e) {
            log.warn("Failed to clean up exceeding-rows violation records: {}",
                    e.getMessage());
            return 0;
        }
    }

    private void cleanupIfDue(long now) {
        long last = lastCleanupTimeMs.get();
        if (now - last > CLEANUP_INTERVAL_MS
                && lastCleanupTimeMs.compareAndSet(last, now)) {
            cleanupOldViolations();
        }
    }

    private String resolveNodeId() {
        try {
            return ArtifactoryHome.get().getHaAwareHostId();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
