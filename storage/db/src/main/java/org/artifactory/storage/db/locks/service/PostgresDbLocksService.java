package org.artifactory.storage.db.locks.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.locks.service.annotation.NativeDbLocksType;
import org.artifactory.storage.db.locks.service.condition.NativeDbLocksBeanCondition;
import org.jfrog.storage.DbType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.artifactory.converter.VersionProviderImpl.RUNNING_VERSION_OVERRIDE_SYSTEM_PROP;

/**
 * Postgres native locks implementation, using Advisory locks.
 *
 * @author Mor Merhav
 */
@Service
@Slf4j
@NativeDbLocksType(DbType.POSTGRESQL)
@Conditional(NativeDbLocksBeanCondition.class)
@Reloadable(beanClass = DbLocksServiceFacade.class, initAfter = AccessService.class)
public class PostgresDbLocksService implements NativeDbLocksService {

    private final ArtifactoryNativeDbLocksProvider nativeDbLocksProvider;

    @Autowired
    public PostgresDbLocksService(ArtifactoryNativeDbLocksProvider nativeDbLocksProvider) {
        this.nativeDbLocksProvider = nativeDbLocksProvider;
    }

    @SneakyThrows
    @Override
    public void acquireLock(String category, String key, String owner, long timeout, TimeUnit timeUnit)
            throws TimeoutException {
        nativeDbLocksProvider.tryToLock(category, key, Thread.currentThread().threadId(), timeout, timeUnit);
        log.trace("Acquired lock with category: '{}' and key: '{}' by thread ID: '{}'", category, key,
                Thread.currentThread().threadId());
    }

    @Override
    public boolean isLocked(String category, String key) {
        return nativeDbLocksProvider.isLocked(category, key, Thread.currentThread().threadId());
    }

    @Override
    public boolean isLockedByMe(String category, String key) {
        return nativeDbLocksProvider.isLockedByMe(category, key, Thread.currentThread().threadId());
    }

    @Override
    public boolean unlock(String category, String key, String owner) {
        try {
            log.trace("Unlocking lock with category: '{}' and key: '{}' by thread ID: '{}'", category, key,
                    Thread.currentThread().threadId());
            return nativeDbLocksProvider.unlock(category, key, Thread.currentThread().threadId());
        } catch (IllegalStateException e) {
            log.warn("Attempted to unlock unowned lock with category: '{}' and key: '{}'", category, key, e);
            return false;
        }
    }

    @Override
    public boolean forceUnlock(String category, String key) {
        log.trace("Force unlocking lock with category: '{}' and key: '{}'", category, key);
        return nativeDbLocksProvider.forceUnlock(category, key);
    }

    /**
     * Returns the advisory locks currently held by this JVM.
     * <p>
     * Uses {@code NativeDbLocksProvider#getAllLocksCopy()} as the authoritative source — always
     * consistent with what PostgreSQL actually holds, including locks acquired before a
     * {@code @Reloadable} reinit and locks expired by the background cleanup job.
     * <p>
     * {@code threadName} and {@code acquiredAtEpochMs} are carried directly inside
     * {@code NativeDbLockInfo}, so no Artifactory-side enrichment cache is required.
     */
    @Override
    public Collection<LockEntry> getHeldLocks() {
        return nativeDbLocksProvider.getAllLocksCopy().stream()
                .map(info -> new LockEntry(
                        info.getCategory(),
                        info.getKey(),
                        info.getAcquiredAtEpochMs() != null ? info.getAcquiredAtEpochMs() : 0L,
                        info.getOwnerId() != null ? info.getOwnerId() : 0L,
                        info.getThreadName()))
                .toList();
    }

    @Override
    public String getSupportedVersion() {
        return System.getProperty(RUNNING_VERSION_OVERRIDE_SYSTEM_PROP, StringUtils.EMPTY);
    }

    @Override
    public void releaseExpiredLocksExcludingCategories(Set<String> excludedCategories, long maxLockLeaseTimeMinutes) {
        nativeDbLocksProvider.releaseExpiredLocksExcludingCategories(excludedCategories, maxLockLeaseTimeMinutes);
    }

    @Override
    public void releaseExpiredLocksByCategory(String category, int maxLockLeaseTimeMinutes) {
        nativeDbLocksProvider.releaseExpiredLocksByCategory(category, maxLockLeaseTimeMinutes);
    }
}
