package org.artifactory.federation.jfbus;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.SecurityService;
import org.artifactory.federation.DeferredDanglingRefCleanupDao;
import org.artifactory.federation.DeferredDanglingRefCleanupDao.DeferredDanglingRefEntry;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.schedule.ArtifactoryExecutorService;
import org.artifactory.spring.ConditionalOnProperty;
import org.artifactory.spring.ContextReadinessListener;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

/**
 * Replays dangling-ref cleanup rows persisted to DB (from exhausted JFBus
 * retries or publish failures) when the application context becomes ready.
 * Rows that resolve successfully are deleted; rows still unresolvable remain
 * for the next restart.
 */
@Slf4j
@Service
@AllArgsConstructor
@ConditionalOnProperty(
        value = "artifactory.virtual.repo.federation.enabled",
        havingValue = "true")
public class DeferredDanglingRefReplayService implements ContextReadinessListener {

    private final FederatedRepoService federatedRepoService;
    private final DeferredDanglingRefCleanupDao danglingRefCleanupDao;
    private final SecurityService securityService;
    private final ArtifactoryExecutorService executorService;

    @Override
    public void onContextReady() {
        executorService.execute(this::replayPendingRows);
    }

    private void replayPendingRows() {
        final List<DeferredDanglingRefEntry> pending;
        try {
            pending = danglingRefCleanupDao.findAll();
        } catch (SQLException e) {
            log.error("Failed to load deferred dangling ref rows: {}", e.getMessage(), e);
            return;
        }
        log.info("Replaying {} deferred dangling ref cleanup row(s)",
                pending.size());
        long resolved = pending.stream()
                .filter(this::replayEntry)
                .count();
        log.info("Deferred dangling ref replay complete: resolved={}, kept={}",
                resolved, pending.size() - resolved);
    }

    private boolean replayEntry(DeferredDanglingRefEntry entry) {
        try {
            boolean done = securityService.callAsSystem(
                    () -> federatedRepoService.runDanglingRefCleanupRetry(
                            entry.virtualRepoKey(), entry.childRepoKey()));
            if (!done) {
                return false;
            }
        } catch (Exception e) {
            log.warn("Error replaying deferred dangling ref cleanup for {} in {}: {}", entry.childRepoKey(),
                    entry.virtualRepoKey(), e.getMessage());
            return false;
        }
        try {
            danglingRefCleanupDao.delete(entry.virtualRepoKey(), entry.childRepoKey());
        } catch (Exception e) {
            // Cleanup already succeeded (ref removed from virtual config), so next restart will
            // hit the SKIP path in evaluateAndApplyDanglingRefCleanup (ref no longer present)
            // and retry the delete — no redundant remote-member calls will be made.
            log.warn("Cleanup succeeded but failed to delete deferred dangling ref row for {} in {}: {}",
                    entry.childRepoKey(), entry.virtualRepoKey(), e.getMessage());
        }
        return true;
    }
}