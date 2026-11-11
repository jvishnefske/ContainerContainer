package org.artifactory.storage.db.locks.service;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;

import java.util.function.BooleanSupplier;

import static org.jfrog.storage.DbType.DERBY;

@Slf4j
@UtilityClass
public class HaInitLockFactory {

    public HaInitLock createLock(String lockKey, BooleanSupplier shouldAcquireInitLockSupplier) {
        HaInitLockType defaultHaInitLockType =
                HaInitLockType.fromString(ConstantValues.haInitLockDefaultType.getString());
        return createLock(lockKey, defaultHaInitLockType, shouldAcquireInitLockSupplier);
    }

    public HaInitLock createLock(String lockKey, HaInitLockType lockType,
            BooleanSupplier shouldAcquireInitLockSupplier) {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        if (!artifactoryHome.isHaConfigured()) {
            return new DummyHaInitLock();
        }
        ArtifactoryDbProperties dbProperties = artifactoryHome.getDBProperties();
        if (dbProperties.getDbType() == DERBY) {
            log.info("Derby database detected, using POLLING lock type for HA init lock.");
            lockType = HaInitLockType.POLLING;
        }
        DbLock dbLock = switch (lockType) {
            case BLOCKING -> {
                log.info("Creating HA init lock of type: BLOCKING for lock key: {}", lockKey);
                yield new DbBlockingLock(lockKey, dbProperties);
            }
            case POLLING -> {
                log.info("Creating HA init lock of type: POLLING for lock key: {}", lockKey);
                yield new DbPollingLock(lockKey, new DbLocksServiceImpl(dbProperties), artifactoryHome);
            }
        };
        return new HaInitLock(dbLock, artifactoryHome.getServerId(), shouldAcquireInitLockSupplier);
    }
}