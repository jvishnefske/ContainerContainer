package org.artifactory.storage.db.locks.service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum HaInitLockType {
    BLOCKING,
    POLLING;

    public static HaInitLockType fromString(String type) {
        try {
            return HaInitLockType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown HA init lock type: '{}', defaulting to 'BLOCKING'", type);
            return BLOCKING;
        }
    }
}