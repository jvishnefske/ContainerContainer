package org.artifactory.storage.db.locks.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.fs.dao.BlockingLockDao;

import java.sql.SQLException;

/**
 * Class for acquiring and releasing a database lock in a blocking manner.
 * It differs from {@link DbPollingLock} by providing a blocking mechanism to acquire the lock.
 *
 * @author Mor Merhav
 */
@Slf4j
public class DbBlockingLock implements DbLock {

    @Getter
    private final String lockKey;
    private final BlockingLockDao blockingLockDao;

    public DbBlockingLock(String lockKey, ArtifactoryDbProperties dbProperties) {
        this(lockKey, new BlockingLockDao(lockKey, dbProperties));
    }

    @VisibleForTesting
    DbBlockingLock(String lockKey, BlockingLockDao blockingLockDao) {
        validate(lockKey);
        validate(blockingLockDao);
        this.blockingLockDao = blockingLockDao;
        this.lockKey = lockKey;
    }

    @Override
    public boolean acquire() {
        try {
            log.debug("Attempting to acquire blocking DB lock: {}", lockKey);
            blockingLockDao.lockBlocking();
            log.debug("Acquired blocking DB lock: {}", lockKey);
            return true;
        } catch (Exception e) {
            String errMsg = "Unexpected error while acquiring HA blocking lock: '%s'".formatted(lockKey);
            log.error(errMsg, e);
            blockingLockDao.close();
            return false;
        }
    }

    @Override
    public void release() {
        try {
            log.debug("Attempting to release blocking DB lock: {}", lockKey);
            blockingLockDao.releaseLock();
            log.debug("Released blocking DB lock: {}", lockKey);
        } catch (Exception e) {
            log.error("Unexpected error while releasing HA blocking lock: '{}'", lockKey, e);
        }
    }

    @Override
    public void releaseStale() {
        // No-op for blocking lock
    }

    @Override
    public boolean isLockedByMe() {
        try {
            return blockingLockDao.isValid();
        } catch (SQLException e) {
            log.error("Unexpected error while checking if HA blocking lock: '{}' is held by me", lockKey, e);
            return false;
        }
    }

    private void validate(String lockKey) {
        if (StringUtils.isBlank(lockKey)) {
            throw new IllegalArgumentException("Lock key must not be null or empty");
        }
    }

    private void validate(BlockingLockDao blockingLockDao) {
        if (blockingLockDao == null) {
            throw new IllegalArgumentException("HaBlockingLockDao must not be null");
        }
    }
}