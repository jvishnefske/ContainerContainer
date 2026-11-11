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

package org.artifactory.storage.db.locks.service;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.dao.locks.DbDistributeLocksDao;
import org.artifactory.storage.db.dao.locks.LockInfo;
import org.artifactory.storage.db.init.DbUnlockSupplier;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.ThreadDumpUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.artifactory.storage.db.init.CommonDataSourceFactory.createDifferentDatasourceForDbLock;
import static org.jfrog.storage.util.DbUtils.tableExists;

/**
 * @author gidis
 * @author Mor Merhav
 */
@Service
@Reloadable(beanClass = DbLocksService.class, initAfter = AccessService.class)
public class DbLocksServiceImpl
        implements JFrogTableBasedDbLocksService, MarkerDbLocksService, ReleaseLocksDbLockService {
    private static final Logger log = LoggerFactory.getLogger(DbLocksServiceImpl.class);

    private final DbDistributeLocksDao dbDistributeLocksDao;
    private final Map<String, LockInfo> localLocks = new ConcurrentHashMap<>();
    private final Set<Pair<String, String>> stuckLocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ArtifactoryDbProperties dbProperties;
    private final int maxWaitTimeMs;
    private final int initialWaitMs;
    private final int maxRetryForDeletions;
    private final int initialUnlockWaitMs;

    @Autowired
    public DbLocksServiceImpl(DbDistributeLocksDao dbDistributeLocksDao, ArtifactoryDbProperties dbProperties) {
        this.dbDistributeLocksDao = dbDistributeLocksDao;
        this.dbProperties = dbProperties;
        this.maxWaitTimeMs = ConstantValues.dbLockMaxWaitMs.getInt();
        this.initialWaitMs = ConstantValues.dbLockInitialWaitMs.getInt();
        this.maxRetryForDeletions = ConstantValues.dbLockMaxRetryForDeletions.getInt();
        this.initialUnlockWaitMs = ConstantValues.initialUnlockWaitMs.getInt();
    }

    public DbLocksServiceImpl(ArtifactoryDbProperties dbProperties) {
        dbDistributeLocksDao = new DbDistributeLocksDao(
                createDifferentDatasourceForDbLock(dbProperties, true), dbProperties, null);
        this.dbProperties = dbProperties;
        this.maxWaitTimeMs = ConstantValues.dbLockMaxWaitMs.getInt();
        this.initialWaitMs = ConstantValues.dbLockInitialWaitMs.getInt();
        this.maxRetryForDeletions = ConstantValues.dbLockMaxRetryForDeletions.getInt();
        this.initialUnlockWaitMs = ConstantValues.initialUnlockWaitMs.getInt();
    }

    private static void waitLimitedTime(long timeout, long sleepTime, long waitingTime, LockInfo dbLockInfo)
            throws TimeoutException {
        if ((timeout > 0) && (waitingTime < timeout)) {
            long maxTimeTOWait = timeout - waitingTime;
            long timeToSleep = Math.min(sleepTime, maxTimeTOWait);
            sleep(timeToSleep);
        } else {
            throw new TimeoutException(
                    "Couldn't acquire lock for: " + timeout + " milliseconds and locked by: " + dbLockInfo);
        }
    }

    private static void sleep(long timeToSleep) {
        try {
            log.trace("Going to sleep for: {}", timeToSleep);
            Thread.sleep(timeToSleep);
        } catch (InterruptedException e) {
            log.warn("Interrupted exception occur while waiting to acquire lock.", e);
            Thread.currentThread().interrupt();
        }
    }

    private static String getLockInfoMsg(String category) {
        return getLockInfoMsg(category, null, null);
    }

    private static String getLockInfoMsg(String category, String key) {
        return getLockInfoMsg(category, key, null);
    }

    private static String getLockInfoMsg(String category, String key, String owner) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotBlank(category)) {
            builder.append("category: '").append(category).append("' ");
        }
        if (StringUtils.isNotBlank(key)) {
            builder.append("key: '").append(key).append("' ");
        }
        if (StringUtils.isNotBlank(owner)) {
            builder.append("owner: '").append(owner).append("' ");
        }
        return builder
                .append("thread id: '")
                .append(Thread.currentThread().getId())
                .append("' thread name: '")
                .append(Thread.currentThread().getName())
                .append("'")
                .toString();
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        // noop
    }

    @Override
    public void acquireLock(String category, String key, String owner, long timeout, TimeUnit timeUnit)
            throws TimeoutException {
        timeout = timeUnit.toMillis(timeout);
        long start = System.currentTimeMillis();
        String lockInfoMsg = getLockInfoMsg(category, key, owner);
        log.debug("Acquiring lock for: {}", lockInfoMsg);
        final String localId = toLocalId(category, key);
        long startTime = System.currentTimeMillis();
        LockInfo lockInfo = new LockInfo(category, key, owner, Thread.currentThread().threadId(),
                Thread.currentThread().getName(), start);
        boolean successfullyAcquiredDbLock = false;
        try {
            successfullyAcquiredDbLock = acquireLock(lockInfo, lockInfoMsg, timeout, start, startTime, localId);
        } finally {
            if (!successfullyAcquiredDbLock && lockInfo.equals(localLocks.get(localId))) {
                localLocks.remove(localId);
            }
        }
    }

    private boolean acquireLock(LockInfo lockInfo, String lockInfoMsg,
            long timeout, long start, long startTime, String localKey) throws TimeoutException {
        long sleepTime = initialWaitMs;
        long waitingTime;
        final String category = lockInfo.getCategory();
        final String owner = lockInfo.getOwner();
        final String key = lockInfo.getKey();
        while (true) {
            LockInfo dbLockInfo = null;
            try {
                //Assume key is not locked
                log.trace("Attempting to acquire lock for: {}", lockInfoMsg);
                // we update the time on the model before inserting into the db, because in case we wait for x time
                // until we were able to acquire the lock, we don't want its expiry to be shorter.
                lockInfo.setStartedTime(System.currentTimeMillis());
                //put lock to localLocks(jvm) and after it validate in db
                LockInfo lockInfoInJvm = localLocks.putIfAbsent(localKey, lockInfo);
                if (lockInfoInJvm != null && lockInfoInJvm != lockInfo) {
                    validateLockIsNotMine(lockInfoInJvm, owner);
                } else if (dbDistributeLocksDao.tryToAcquireLock(lockInfo)) {
                    log.trace("Successfully acquired lock: '{}'.", lockInfo);
                    long timeTookToAcquireLock = System.currentTimeMillis() - start;
                    log.debug("Lock acquired in '{}' milliseconds.", timeTookToAcquireLock);
                    return true;
                } else {
                    dbLockInfo = dbDistributeLocksDao.getLockInfo(category, key);
                    if (dbLockInfo != null) {
                        validateLockIsNotMine(dbLockInfo, owner);
                    }
                }
            } catch (SQLException e) {
                if (!locksTableExists(category, key, owner)) {
                    log.debug("distributed_locks table doesn't exist yet. Retrying");
                } else {
                    log.error("Exception occurred while trying to acquire lock", e);
                    throw new IllegalStateException("Exception occurred while trying to acquire lock: " +
                            lockInfoMsg, e);
                }
            } catch (Exception e) {
                log.error("Failed to acquire lock for: {}", lockInfo, e);
                throw new IllegalStateException("Failed to acquire lock for: " + lockInfoMsg, e);
            }
            // If we got here the lock exist but we don't own it
            long currentTime = System.currentTimeMillis();
            waitingTime = currentTime - startTime;
            waitLimitedTime(timeout, sleepTime, waitingTime, dbLockInfo);
            sleepTime = Math.min(sleepTime * 4, maxWaitTimeMs);
            log.trace("Waiting while trying to acquiring lock for: {}", lockInfoMsg);
        }
    }

    private void validateLockIsNotMine(@Nonnull LockInfo lockInfo, String owner) {
        if (isLockMine(lockInfo, owner)) {
            throw new UnsupportedReentrantLockException();
        }
    }

    private boolean isLockMine(@Nonnull LockInfo lockInfo, String owner) {
        return (lockInfo.getThreadId() == Thread.currentThread().getId() &&
                lockInfo.getOwner().equals(owner));
    }

    @Override
    public void forceRemoveLocks(@Nonnull Set<LockInfo> locks, boolean removeCachedOnly) {
        for (LockInfo lock : locks) {
            log.info("Destroying lock for category: {}, key: {} ", lock.getCategory(), lock.getKey());
            localLocks.remove(toLocalId(lock.getCategory(), lock.getKey()));
            if (!removeCachedOnly) {
                try {
                    DbUnlockSupplier unlockSupplier = getForceUnlockSupplier(lock.getCategory(), lock.getKey());
                    unlockInternal(unlockSupplier, lock.getCategory(), lock.getKey(), null, 1);
                } catch (Exception e) {
                    // the lock might have already deleted, so we ignore.
                    log.warn("Failed to destroy lock for category: {}, key: {} . {}", lock.getCategory(),
                            lock.getKey(), e.getMessage());
                    log.debug("Failed to destroy lock.", e);
                }
            }
        }
    }

    @Override
    public void releaseAllLocksByOwnerExcludingKeys(String owner, Set<String> excludedLockKeys) {
        try {
            Set<LockInfo> allLocksByOwnerWithExclusions = dbDistributeLocksDao.getAllLocksByOwner(owner).stream()
                    .filter(lockInfo -> !excludedLockKeys.contains(lockInfo.getKey())).collect(Collectors.toSet());
            forceRemoveLocks(allLocksByOwnerWithExclusions, false);
        } catch (SQLException e) {
            log.error("Failed to release all locks for owner: " + owner, e);
        }
    }

    @Override
    public void releaseOrphanLocksByCategoryExcludingServers(Set<String> excludedServerIds, String category) {
        if (CollectionUtils.isNullOrEmpty(excludedServerIds)) {
            log.warn("Skip releasing orphan locks for category: {}, no active servers found.", category);
            return;
        }
        if (StringUtils.isEmpty(category)) {
            throw new IllegalArgumentException("Lock category cannot be empty");
        }
        Set<LockInfo> lockInfoSet = dbDistributeLocksDao
                .getAllLocksByCategoryNotOwnedBy(excludedServerIds, category);
        log.debug("Releasing all orphan locks of category:{} not owned by servers:{}", category, excludedServerIds);
        forceRemoveLocks(lockInfoSet, false);
    }

    @Override
    public void releaseOrphanLocksExcludingServersAndCategories(Set<String> excludedServerIds,
            Set<String> excludedCategories) {
        if (CollectionUtils.isNullOrEmpty(excludedServerIds)) {
            log.warn("Skip releasing orphan locks, no active servers found.");
            return;
        }
        if (CollectionUtils.isNullOrEmpty(excludedCategories)) {
            throw new IllegalArgumentException("Categories excluded from lock release must not be empty");
        }
        Set<LockInfo> lockInfoSet = dbDistributeLocksDao
                .getAllLocksNotOwnedByAndNotContainedInCategories(excludedServerIds, excludedCategories);
        forceRemoveLocks(lockInfoSet, false);
    }

    @Override
    public void releaseExpiredLocksExcludingCategories(Set<String> excludedCategories, long maxLockLeaseTimeMinutes) {
        long current = System.currentTimeMillis();
        log.trace("Current time is: {}", current);
        long minAllowedTimeMillis = getMinAllowedTimeMillis(current, maxLockLeaseTimeMinutes);
        String owner = ArtifactoryHome.get().getServerId();
        Set<LockInfo> expiredLocks = dbDistributeLocksDao
                .getExpiredLocksNotContainedInCategories(excludedCategories, minAllowedTimeMillis, owner);
        forceRemoveLocks(expiredLocks, false);
    }

    @Override
    public void releaseExpiredLocksByCategory(String category, int maxLockLeaseTimeMinutes) {
        long current = System.currentTimeMillis();
        log.trace("Current time is: {}", current);
        long minAllowedTime = getMinAllowedTimeMillis(current, maxLockLeaseTimeMinutes);
        String owner = ArtifactoryHome.get().getServerId();
        Set<LockInfo> expiredLocks = dbDistributeLocksDao.getExpiredLocksByCategory(category, minAllowedTime, owner);
        forceRemoveLocks(expiredLocks, false);
    }

    @Override
    public Optional<LockInfo> getLockInfoFromDbByCategoryAndLockKey(String category, String lockKey) {
        try {
            return Optional.ofNullable(dbDistributeLocksDao.getLockInfo(category, lockKey));
        } catch (SQLException e) {
            throw new StorageException("Unable to query for lock-info by category " + category + " and key "
                    + lockKey, e);
        }
    }

    @Override
    public boolean isLocked(String category, String key) {
        String lockInfoMsg = getLockInfoMsg(category, key);
        log.debug("Checking if lock exist for: {}", lockInfoMsg);
        try {
            if (localLocks.containsKey(toLocalId(category, key))) {
                log.trace("Lock exists locally for: {}", lockInfoMsg);
                return true;
            }
            if (dbDistributeLocksDao.isLocked(category, key)) {
                log.trace("Lock exist for: {}", lockInfoMsg);
                return true;
            }
            log.trace("Lock doesn't exist for: {}", lockInfoMsg);
            return false;
        } catch (SQLException e) {
            log.debug("Failed to check if lock exist for:" + lockInfoMsg, e);
            throw new IllegalStateException("Failed to check if lock exist for:" + lockInfoMsg, e);
        }
    }

    @Override
    public boolean isLockedByPrefixExcluding(String category, String lockPrefix, String excluded,
            boolean fuzzyExclusion) {
        log.trace("Fuzzy exclusion: {}", fuzzyExclusion);
        Predicate<LockInfo> exclusionPredicate = getExclusionPredicate(excluded, fuzzyExclusion);
        Predicate<LockInfo> lockPrefixPredicate = getLockPrefixPredicate(lockPrefix);
        boolean isLockedByPrefix = localLocks.values().stream()
                .filter(exclusionPredicate)
                .anyMatch(lockPrefixPredicate);

        if (isLockedByPrefix) {
            log.trace("Found local lock which matches lock prefix: {} excluding: {}", lockPrefix, excluded);
            return true;
        }
        List<LockInfo> locks =
                dbDistributeLocksDao.getLocksByPrefix(category, lockPrefix, 2).stream()
                        .filter(exclusionPredicate).toList();
        if (!locks.isEmpty()) {
            log.trace("Found lock which matches lock prefix: {} excluding: {}", lockPrefix, excluded);
            return true;
        }
        log.trace("Matches for lock prefix: {}  excluding: {} not found", lockPrefix, excluded);
        return false;
    }

    @Override
    public boolean isLockedByMe(String category, String key) {
        LockInfo lockInfo = localLocks.get(toLocalId(category, key));
        return lockInfo != null && Thread.currentThread().getId() == lockInfo.getThreadId();
    }

    public boolean unlock(String category, String key, String owner) {
        String lockInfoMsg = getLockInfoMsg(category, key, owner);
        log.debug("Attempting to release lock for: {}", lockInfoMsg);
        try {
            // Assume lock acquired by this thread and try to update lock
            log.trace("Attempting to release lock from cache for: {}", lockInfoMsg);
            LockInfo lockInfo = localLocks.get(toLocalId(category, key));
            if (lockInfo != null) {
                if (lockInfo.getThreadId() == Thread.currentThread().getId()) {
                    localLocks.remove(toLocalId(category, key));
                    log.trace("Successfully delete lock from cache for: {}", lockInfo);
                } else {
                    String msg = "Failed to release lock (inconsistent state) for: {}. Current thread is not the " +
                            "owner of the lock." + lockInfo;
                    StringBuilder msgBuilder = new StringBuilder().append(msg);
                    ThreadDumpUtils.builder()
                            .count(1)
                            .build()
                            .dumpThreads(msgBuilder);
                    throw new IllegalStateException(msgBuilder.toString());
                }
            }
            DbUnlockSupplier unlockSupplier = buildUnlockSupplier(category, key, owner);
            boolean dbLockRemoval = unlockInternal(unlockSupplier, category, key, owner, maxRetryForDeletions);
            if (dbLockRemoval) {
                log.trace("Successfully delete lock from cache for: {}", lockInfoMsg);
                return true;
            } else {
                log.warn("Could not remove {} lock.", lockInfoMsg);
            }
        } catch (Exception e) {
            log.error("Failed to release lock for: {}. Exception message: {}", lockInfoMsg, e.getMessage());
            log.debug("Failed to release lock for: {}", lockInfoMsg, e);
            throw new IllegalStateException("Failed to release lock for: " + lockInfoMsg, e);
        }
        log.debug("Lock is either not exist or not owned by current owner for: {}", lockInfoMsg);
        return false;
    }

    @Override
    public boolean forceUnlock(String category, String key) {
        String lockInfoMsg = getLockInfoMsg(category, key);
        log.debug("Releasing lock for: {}", lockInfoMsg);
        try {
            LockInfo removed = localLocks.remove(toLocalId(category, key));
            DbUnlockSupplier forceUnlockSupplier = getForceUnlockSupplier(category, key);
            boolean dbLockRemoved = unlockInternal(forceUnlockSupplier, category, key, null, maxRetryForDeletions);
            return removed != null || dbLockRemoved;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to force lock release for: " + lockInfoMsg, e);
        }
    }

    @Override
    public void forceUnlockAllStuck() {
        log.debug("Releasing stuck locks.");
        stuckLocks.stream()
                .filter(stuckLock -> {
                    try {
                        return forceUnlock(stuckLock.getLeft(), stuckLock.getRight());
                    } catch (IllegalStateException e) {
                        log.warn("Unable to force unlock : {}, {}.", stuckLock.getLeft(), stuckLock.getRight(), e);
                        return false;
                    }
                })
                .forEach(stuckLocks::remove);
    }

    @Override
    public int lockingMapSize(String category) {
        String lockInfoMsg = getLockInfoMsg(category);
        log.debug("Querying for category map size for: {}", lockInfoMsg);
        try {
            int numberOfLocks = dbDistributeLocksDao.lockingMapSize(category);
            log.trace("Found {} locks for: {}", numberOfLocks, lockInfoMsg);
            return numberOfLocks;
        } catch (SQLException e) {
            String message = StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : "";
            throw new IllegalStateException(
                    "Failed to query out number of locks for: " + lockInfoMsg + ". " + message, e);
        }
    }

    @Override
    public Set<String> lockingMapKeySet(String category) {
        String lockInfoMsg = getLockInfoMsg(category);
        log.debug("Querying for category map size for: {}", lockInfoMsg);
        try {
            Set<String> lockingMapKeySet = dbDistributeLocksDao.lockingMapKeySet(category);
            log.trace("Found the following locks {} for: {}", lockingMapKeySet, lockInfoMsg);
            return lockingMapKeySet;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query out locks for:" + lockInfoMsg, e);
        }
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        // noop
    }

    @Nonnull
    private Predicate<LockInfo> getLockPrefixPredicate(String lockPrefix) {
        return lock -> lock.getKey().startsWith(lockPrefix);
    }

    @Nonnull
    private Predicate<LockInfo> getExclusionPredicate(String excluded, boolean fuzzyExclusion) {
        Predicate<LockInfo> predicate;
        if (fuzzyExclusion) {
            predicate = lock -> !lock.getKey().contains(excluded);
        } else {
            predicate = lock -> !lock.getKey().equals(excluded);
        }
        return predicate;
    }

    /**
     * The first startup may happen from multiple nodes in parallel. This node may have yielded for another node in the
     * schema creation phase, so we give the node a chance to create the locks table.
     */
    private boolean locksTableExists(String category, String key, String owner) {
        try (Connection con = dbDistributeLocksDao.getLocksConnection()) {
            return tableExists(con.getMetaData(), dbProperties.getDbType(), "distributed_locks");
        } catch (SQLException se) {
            throw new IllegalStateException(
                    "Failed to check if distributed_locks table exists while trying to lock: " +
                            getLockInfoMsg(category, key, owner), se);
        }
    }

    private long getMinAllowedTimeMillis(long current, long maxLockLeaseTimeMinutes) {
        return current - TimeUnit.MINUTES.toMillis(maxLockLeaseTimeMinutes);
    }

    /**
     * Unlock a combination of 'category', 'key' and optionally 'owner' from the DB with retries and short sleep between
     * each retry
     *
     * @param numOfRetries Num of times to retry if the first attempt failed
     * @throws SQLException if the lock can't be removed
     */
    private boolean unlockInternal(DbUnlockSupplier unlockSupplier, String category, String key, String owner,
            int numOfRetries) throws SQLException {
        // basically, we need retry when catching an exception only, however, although it does not make sense
        //without DB cluster, we saw once that a lock was exists in Oracle DB, and the unlock operation returned false.
        boolean success = false;
        int sleepTime = initialUnlockWaitMs; // sleep time in ms
        while (numOfRetries >= 0) {
            try {
                success = unlockSupplier.unlock();
            } catch (SQLException e) {
                log.trace("SQL Error occurred while trying to delete lock.", e);
                if (numOfRetries == 0) {
                    stuckLocks.add(Pair.of(category, key));
                    throw e;
                }
            }
            if (success) {
                return true;
            }
            log.debug("Failed removing lock for {}", getLockInfoMsg(category, key, owner));
            if (numOfRetries > 0) {
                // sleep, no more than half a second
                sleepTime = Math.min(sleepTime, 256);
                sleep(sleepTime);
                sleepTime = sleepTime * 4;
            }
            numOfRetries--;
        }
        return false;
    }

    private DbUnlockSupplier buildUnlockSupplier(String category, String key, String owner) {
        return () -> dbDistributeLocksDao.unlock(category, key, owner);
    }

    private DbUnlockSupplier getForceUnlockSupplier(String category, String key) {
        return () -> dbDistributeLocksDao.deleteLockByCategoryAndKey(category, key);
    }

    private String toLocalId(String category, String key) {
        return "category:" + category + ",key:" + key;
    }
}
