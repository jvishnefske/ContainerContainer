package org.artifactory.lock.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.concurrent.NonStandardLockCategory;
import org.artifactory.lock.util.ReleaseLockStrategyUtils;
import org.artifactory.storage.db.locks.service.ReleaseLocksDbLockService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Mor Merhav
 */
@Component
@Slf4j
public class NonStandardReleaseLockStrategy implements ReleaseLockStrategy {

    private final Set<ReleaseLocksDbLockService> releaseLockServices;
    private final ArtifactoryServersCommonService serversCommonService;

    @Autowired
    public NonStandardReleaseLockStrategy(Set<ReleaseLocksDbLockService> releaseLockServices,
                                          ArtifactoryServersCommonService serversCommonService) {
        this.releaseLockServices = releaseLockServices;
        this.serversCommonService = serversCommonService;
    }

    @Override
    public void releaseOrphaned() {
        NonStandardLockCategory[] nonStandardLockCategories = NonStandardLockCategory.values();
        for (NonStandardLockCategory nonStandardLockCategory : nonStandardLockCategories) {
            log.debug("Category OrphanLockGraceTimeMinutes {}, LockLeaseTimeMinutes {}",
                    nonStandardLockCategory.getOrphanLockGraceTimeMinutes(),
                    nonStandardLockCategory.getLockLeaseTimeMinutes());
            long orphanLockGraceTimeSeconds = TimeUnit.MINUTES
                    .toSeconds(nonStandardLockCategory.getOrphanLockGraceTimeMinutes());
            Set<String> activeServers = ReleaseLockStrategyUtils
                    .findActiveServers(serversCommonService, orphanLockGraceTimeSeconds);
            for (ReleaseLocksDbLockService releaseLockService : releaseLockServices) {
                releaseLockService.releaseOrphanLocksByCategoryExcludingServers(activeServers,
                        nonStandardLockCategory.getName());
            }
        }
    }

    @Override
    public void releaseExpired() {
        NonStandardLockCategory[] nonStandardLockCategories = NonStandardLockCategory.values();
        for (NonStandardLockCategory nonStandardLockCategory : nonStandardLockCategories) {
            if (nonStandardLockCategory.isExpirable()) {
                for (ReleaseLocksDbLockService releaseLockService : releaseLockServices) {
                    releaseLockService.releaseExpiredLocksByCategory(nonStandardLockCategory.getName(),
                            nonStandardLockCategory.getLockLeaseTimeMinutes());
                }
            }
        }
    }
}
