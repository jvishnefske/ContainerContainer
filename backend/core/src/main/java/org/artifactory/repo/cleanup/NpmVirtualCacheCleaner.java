package org.artifactory.repo.cleanup;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.sapi.search.VfsComparatorType;
import org.artifactory.sapi.search.VfsQuery;
import org.artifactory.sapi.search.VfsQueryResult;
import org.artifactory.sapi.search.VfsQueryResultType;
import org.artifactory.sapi.search.VfsQueryRow;
import org.artifactory.sapi.search.VfsQueryService;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.artifactory.descriptor.repo.RepoType.Npm;

/**
 * Cleans the cache of npm virtual repositories in two disjoint phases, each fetching only files it can
 * actually delete so the batch-delete loop always makes forward progress.
 *
 * Phase 1 (non-audit files): everything outside {@value #NPM_AUDIT_CACHE_FOLDER} (for example merged
 * metadata) is evicted after the long
 * {@link ConstantValues#npmVirtualCacheNonAuditItemTtlSeconds} TTL (ten days by default).
 *
 * Phase 2 (audit reports): files under {@value #NPM_AUDIT_CACHE_FOLDER} (see NpmAuditVirtualCacheImpl)
 * keep the short {@link ConstantValues#npmVirtualCacheItemTtlSeconds} TTL (ten minutes by default),
 * exactly as in the previous behavior.
 *
 * The two phases target mutually exclusive file sets, so they never fetch or delete the same row, and
 * their execution order is irrelevant.
 *
 * @author Yoaz Menda
 */
@Component
@Slf4j
public class NpmVirtualCacheCleaner implements VirtualCacheCleaner {

    /**
     * Relative folder under which npm audit reports are cached (see NpmAuditVirtualCacheImpl).
     */
    static final String NPM_AUDIT_CACHE_FOLDER = ".security";

    private final RepositoryService repoService;

    private final VfsQueryService vfsQueryService;

    @Autowired
    public NpmVirtualCacheCleaner(RepositoryService repoService, VfsQueryService vfsQueryService) {
        this.vfsQueryService = vfsQueryService;
        this.repoService = repoService;
    }

    @Override
    public long cleanCache(VirtualRepoConfig repoConfig) {
        long noAuditdeleted = cleanupNonAuditCache(repoConfig);
        log.info("cleaned {} non-audit cached files from repo {}", noAuditdeleted, repoConfig.getKey());
        long auditDeleted = cleanupAuditCache(repoConfig);
        log.info("cleaned {} audit cached files from repo {}", auditDeleted, repoConfig.getKey());
        return noAuditdeleted + auditDeleted;
    }

    @Override
    public boolean shouldRun(VirtualRepoConfig repoConfig) {
        return Npm.equals(repoConfig.getPackageType());
    }

    /**
     * Phase 1: evicts every non-audit cached file (anything not under {@value #NPM_AUDIT_CACHE_FOLDER}) once it
     * exceeds the long (ten-day by default) TTL.
     */
    @VisibleForTesting
    long cleanupNonAuditCache(VirtualRepoConfig repoConfig) {
        return cleanup(repoConfig, CleanupScope.NON_AUDIT,
                ConstantValues.npmVirtualCacheNonAuditItemTtlSeconds.getInt());
    }

    /**
     * Phase 2: evicts cached audit reports older than the short audit TTL - unchanged legacy behavior.
     */
    @VisibleForTesting
    long cleanupAuditCache(VirtualRepoConfig repoConfig) {
        return cleanup(repoConfig, CleanupScope.AUDIT,
                ConstantValues.npmVirtualCacheItemTtlSeconds.getInt());
    }

    // do in batches to not overload memory
    private long cleanup(VirtualRepoConfig repoConfig, CleanupScope scope, int expiryTimeInSeconds) {
        log.info("cleaning up npm virtual {} cache from repo {} ", scope.label, repoConfig.getKey());
        long batchSize = ConstantValues.npmVirtualCacheCleanupBatchSize.getLong();
        CleanupStatus cleanupStatus = new CleanupStatus();
        while (!cleanupStatus.isDone()) {
            cleanupBatch(cleanupStatus, batchSize, repoConfig, scope, expiryTimeInSeconds);
            if (cleanupStatus.hasErrors()) { //stop!
                log.error("Failed to cleanup npm {} cache of {} {} with {} errors. Aborting...",
                          scope.label,
                          repoConfig.getKey(),
                          cleanupStatus.getLastError().getMessage(),
                          cleanupStatus.getErrors().size());
                break;
            }
        }
        return cleanupStatus.deletedCount;
    }

    private void cleanupBatch(CleanupStatus status, long batchSize, RepoConfig repoConfig,
            CleanupScope scope, int expiryTimeInSeconds) {
        long expiryTime = new DateTime().minusSeconds(expiryTimeInSeconds).getMillis();
        VfsQuery query = vfsQueryService.createQuery()
                                 .expectedResult(VfsQueryResultType.FILE)
                                 .setSingleRepoKey(repoConfig.getKey());
        query = applyScope(query, scope);
        query = query.prop("modified")
                     .comp(VfsComparatorType.LOWER_THAN_EQUAL)
                     .val(expiryTime);
        VfsQueryResult queryResult = query.execute(batchSize);
        if (queryResult.getCount() <= 0) {
            status.setDone(true);
            return;
        }
        for (VfsQueryRow result : queryResult.getAllRows()) {
            BasicStatusHolder undeployStatus = repoService.undeploy(result.getItem().getRepoPath());
            if (undeployStatus.hasErrors()) { //stop batch
                log.error("Failed to undeploy virtual cache item {}", result.getItem().getRepoPath());
                status.merge(undeployStatus);
                status.setDone(true);
                return;
            } else {
                status.increaseDeleted();
            }
        }

    }

    /**
     * Restricts the query to the files owned by the given phase. Audit files are matched positively via a path
     * filter on {@value #NPM_AUDIT_CACHE_FOLDER}; non-audit files are matched by excluding that folder with a
     * {@code node_path NOT LIKE} predicate ({@link VfsQuery#addPathFilter} can only match positively, so it
     * cannot express the "not audit" case).
     */
    private VfsQuery applyScope(VfsQuery query, CleanupScope scope) {
        return switch (scope) {
            case AUDIT -> query.addPathFilter(NPM_AUDIT_CACHE_FOLDER + "/**");
            case NON_AUDIT -> query.prop("node_path")
                    .comp(VfsComparatorType.NOT_CONTAINS)
                    .val("*" + NPM_AUDIT_CACHE_FOLDER + "*");
        };
    }

    private enum CleanupScope {
        AUDIT("audit"),
        NON_AUDIT("non-audit");

        private final String label;

        CleanupScope(String label) {
            this.label = label;
        }
    }

    static class CleanupStatus extends BasicStatusHolder {

        CleanupStatus() {
            this.isDone = false;
            this.deletedCount = 0L;
        }

        @Setter
        @Getter
        private boolean isDone;

        private long deletedCount;

        void increaseDeleted() {
            this.deletedCount++;
        }

    }
}
