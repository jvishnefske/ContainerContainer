package org.artifactory.lock.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.NonStandardLockCategory;
import org.artifactory.lock.util.ReleaseLockStrategyUtils;
import org.artifactory.storage.db.locks.service.ReleaseLocksDbLockService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.haHeartbeatStaleIntervalSecs;

/**
 * @author Mor Merhav
 */
@Component
public class StandardReleaseLockStrategy implements ReleaseLockStrategy {

    private final Set<ReleaseLocksDbLockService> releaseLockServices;
    private final ArtifactoryServersCommonService serversCommonService;

    @Autowired
    public StandardReleaseLockStrategy(Set<ReleaseLocksDbLockService> releaseLockServices,
                                       ArtifactoryServersCommonService serversCommonService) {
        this.releaseLockServices = releaseLockServices;
        this.serversCommonService = serversCommonService;
    }

    public static int getStdOrphanLockGraceTimeInSeconds() {
        return haHeartbeatStaleIntervalSecs.getInt() * 3;
    }

    @Override
    public void releaseOrphaned() {
        Set<String> activeServers = ReleaseLockStrategyUtils.findActiveServers(serversCommonService,
                getStdOrphanLockGraceTimeInSeconds());
        Set<String> nonStandardCategories = Arrays.stream(NonStandardLockCategory.values())
                .map(NonStandardLockCategory::getName)
                .collect(Collectors.toSet());
        for (ReleaseLocksDbLockService releaseLockService : releaseLockServices) {
            releaseLockService.releaseOrphanLocksExcludingServersAndCategories(activeServers, nonStandardCategories);
        }
    }

    @Override
    public void releaseExpired() {
        Set<String> nonStandardCategories = Arrays.stream(NonStandardLockCategory.values())
                .map(NonStandardLockCategory::getName)
                .collect(Collectors.toSet());
        for (ReleaseLocksDbLockService releaseLockService : releaseLockServices) {
            releaseLockService.releaseExpiredLocksExcludingCategories(nonStandardCategories,
                    ConstantValues.lockLeaseTime.getLong());
        }
    }
}
