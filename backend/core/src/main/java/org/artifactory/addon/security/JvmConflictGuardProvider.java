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

package org.artifactory.addon.security;

import com.google.common.collect.Maps;
import org.artifactory.addon.LockingProvider;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.fs.lock.FsItemsVault;
import org.artifactory.storage.fs.lock.FsItemsVaultCacheImpl;
import org.artifactory.storage.fs.lock.provider.JVMLockProvider;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.concurrency.JvmConflictsGuard;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author gidis
 */
public class JvmConflictGuardProvider implements LockingProvider {

    private static final String GENERAL_GUARD = "general";
    private final FsItemsVault vault;
    private Map<String, ConflictsGuard<Object>> lockCategoryToConflictsGuardMap;
    private ReentrantLock lock;

    public JvmConflictGuardProvider() {
        lockCategoryToConflictsGuardMap = Maps.newHashMap();
        lock = new ReentrantLock();
        JVMLockProvider jvmLockProvider = new JVMLockProvider();
        vault = new FsItemsVaultCacheImpl(jvmLockProvider);
    }

    @Override
    public FsItemsVault getFolderLockingMap() {
        return vault;
    }

    @Override
    public FsItemsVault getFileLockingMap() {
        return vault;
    }

    @Override
    public ConflictGuard getConflictGuard(String key) {
        ConflictsGuard<Object> conflictsGuard = lockCategoryToConflictsGuardMap.get(GENERAL_GUARD);
        if (conflictsGuard == null) {
            lock.lock();
            try {
                conflictsGuard = lockCategoryToConflictsGuardMap.get(GENERAL_GUARD);
                if (conflictsGuard == null) {
                    conflictsGuard = new JvmConflictsGuard(ConstantValues.lockLeaseTime.getLong());
                    lockCategoryToConflictsGuardMap.put(GENERAL_GUARD, conflictsGuard);
                }
            } finally {
                lock.unlock();
            }
        }
        return conflictsGuard.getLock(key);
    }

    @Override
    public <T> ConflictsGuard<T> getConflictsGuard(String category) {
        if (GENERAL_GUARD.equals(category)) {
            throw new RuntimeException("The 'general' map is reserved for internal usage");
        }
        ConflictsGuard<Object> conflictsGuard = lockCategoryToConflictsGuardMap.get(category);
        if (conflictsGuard == null) {
            lock.lock();
            try {
                conflictsGuard = lockCategoryToConflictsGuardMap.get(category);
                if (conflictsGuard == null) {
                    conflictsGuard = new JvmConflictsGuard<>(ConstantValues.lockLeaseTime.getLong());
                    lockCategoryToConflictsGuardMap.put(category, conflictsGuard);
                }
            } finally {
                lock.unlock();
            }
        }
        return (ConflictsGuard<T>) conflictsGuard;
    }
}
