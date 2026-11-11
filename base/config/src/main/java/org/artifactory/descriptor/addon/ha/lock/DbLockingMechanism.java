package org.artifactory.descriptor.addon.ha.lock;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;

@Slf4j
public enum DbLockingMechanism {

    JFROG_TABLE_BASED("jfrog_table_based"),
    NATIVE("native"),
    AUTO("auto");

    private final String name;

    DbLockingMechanism(String name) {
        this.name = name;
    }

    public static DbLockingMechanism get() {
        String dbLockingMechanism = ConstantValues.dbLockingMechanism.getString();
        DbLockingMechanism[] values = DbLockingMechanism.values();
        for (DbLockingMechanism value : values) {
            if (value.getName().equalsIgnoreCase(dbLockingMechanism)) {
                return value;
            }
        }
        log.error("Illegal locking mechanism name: {}. Returning generic", dbLockingMechanism);
        return JFROG_TABLE_BASED;
    }
    public String getName() {
        return this.name;
    }
}

