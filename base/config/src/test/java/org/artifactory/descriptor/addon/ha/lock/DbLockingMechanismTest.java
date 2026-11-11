package org.artifactory.descriptor.addon.ha.lock;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism.AUTO;
import static org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism.JFROG_TABLE_BASED;

class DbLockingMechanismTest extends ArtifactoryHomeBoundJUnitTest {

    @ParameterizedTest
    @EnumSource(value = DbLockingMechanism.class)
    void testGet(DbLockingMechanism expectedDbLockingMechanism) {
        setStringSystemProperty(ConstantValues.dbLockingMechanism, expectedDbLockingMechanism.getName());
        DbLockingMechanism actualDbLockingMechanism = DbLockingMechanism.get();
        Assertions.assertEquals(expectedDbLockingMechanism, actualDbLockingMechanism);
    }

    @Test
    void testIllegalNameReturnsTableBased() {
        setStringSystemProperty(ConstantValues.dbLockingMechanism, "illegal");
        DbLockingMechanism dbLockingMechanism = DbLockingMechanism.get();
        Assertions.assertEquals(JFROG_TABLE_BASED, dbLockingMechanism);
    }

    @Test
    void testDefaultValue() {
        DbLockingMechanism dbLockingMechanism = DbLockingMechanism.get();
        Assertions.assertEquals(AUTO, dbLockingMechanism);
    }
}