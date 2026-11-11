package org.artifactory.schedule;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.storage.db.locks.service.DBConflictsGuard;
import org.artifactory.storage.db.locks.service.MarkerDbLocksService;

import static org.artifactory.concurrent.NonStandardLockCategory.CLUSTER_SINGLETON;

class QuartzConflictsGuard extends DBConflictsGuard {

    private final MarkerDbLocksService dbLockService;

    QuartzConflictsGuard(MarkerDbLocksService dbLockService) {
        super(dbLockService, CLUSTER_SINGLETON.getName(), ContextHelper.get().getServerId());
        this.dbLockService = dbLockService;
    }

    boolean isLockedByPrefixExcluding(String lockPrefix, String excluded,
            boolean fuzzyExclusion) {
        return dbLockService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), lockPrefix, excluded,
                fuzzyExclusion);
    }
}
