package org.artifactory.storage.db.locks.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HaInitLockFactoryTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void setUp() {
        DbType dbType = getRandomNonDerbyDbType();
        getBound().setHaEnabled(true);
        setDbTypeInDbProperties(dbType);
    }

    @Test
    void testCreateLock_defaultType() {
        HaInitLockType haInitLockDefaultType =
                HaInitLockType.fromString(ConstantValues.haInitLockDefaultType.getDefValue());
        String lockKey = "testLockKey";
        HaInitLock haInitLock = assertDoesNotThrow(() -> HaInitLockFactory.createLock(lockKey, () -> true));
        assertNotNull(haInitLock);
        DbLock dbLock = (DbLock) ReflectionTestUtils.getField(haInitLock, "dbLock");
        assertNotNull(dbLock);

        switch (haInitLockDefaultType) {
            case BLOCKING -> assertInstanceOf(DbBlockingLock.class, dbLock);
            case POLLING -> assertInstanceOf(DbPollingLock.class, dbLock);
        }
    }

    @Test
    void testCreateLock_specificType() {
        String lockKey = "testLockKey";

        HaInitLock haInitLockBlocking = assertDoesNotThrow(
                () -> HaInitLockFactory.createLock(lockKey, HaInitLockType.BLOCKING, () -> true));
        assertNotNull(haInitLockBlocking);
        DbLock dbLockBlocking = (DbLock) ReflectionTestUtils.getField(haInitLockBlocking, "dbLock");
        assertNotNull(dbLockBlocking);
        assertInstanceOf(DbBlockingLock.class, dbLockBlocking);

        HaInitLock haInitLockPolling = assertDoesNotThrow(
                () -> HaInitLockFactory.createLock(lockKey, HaInitLockType.POLLING, () -> true));
        assertNotNull(haInitLockPolling);
        DbLock dbLockPolling = (DbLock) ReflectionTestUtils.getField(haInitLockPolling, "dbLock");
        assertNotNull(dbLockPolling);
        assertInstanceOf(DbPollingLock.class, dbLockPolling);
    }

    @Test
    void testCreateLock_Derby_AlwaysPolling() {
        String lockKey = "testLockKey";
        setDbTypeInDbProperties(DbType.DERBY);

        HaInitLock haInitLock = assertDoesNotThrow(
                () -> HaInitLockFactory.createLock(lockKey, HaInitLockType.BLOCKING, () -> true));
        assertNotNull(haInitLock);
        DbLock dbLock = (DbLock) ReflectionTestUtils.getField(haInitLock, "dbLock");
        assertNotNull(dbLock);
        assertInstanceOf(DbPollingLock.class, dbLock);
    }

    @Test
    void testCreateLock_NonHA_DummyLock() {
        getBound().setHaEnabled(false);
        String lockKey = "testLockKey";
        HaInitLock haInitLock = assertDoesNotThrow(
                () -> HaInitLockFactory.createLock(lockKey, HaInitLockType.BLOCKING, () -> true));
        assertNotNull(haInitLock);
        assertInstanceOf(DummyHaInitLock.class, haInitLock);
    }

    private void setDbTypeInDbProperties(DbType dbType) {
        ReflectionTestUtils.setField(getBound().getDBProperties(), "dbType", dbType);
    }

    private DbType getRandomNonDerbyDbType() {
        Random random = new Random();
        DbType[] dbTypes = DbType.values();
        DbType dbType;
        do {
            dbType = dbTypes[random.nextInt(dbTypes.length)];
        } while (dbType == DbType.DERBY);
        return dbType;
    }
}