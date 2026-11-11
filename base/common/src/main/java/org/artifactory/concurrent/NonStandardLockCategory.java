package org.artifactory.concurrent;

import org.artifactory.common.ConstantValues;

import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import static org.artifactory.common.ConstantValues.federatedRepoFullSyncLockTimeMin;
import static org.artifactory.common.ConstantValues.federatedRepoFullSyncStaleLockTimeMin;
import static org.artifactory.common.ConstantValues.haHeartbeatStaleIntervalSecs;
import static org.artifactory.common.ConstantValues.hqcLockLeaseTime;
import static org.artifactory.common.ConstantValues.replicationLockLeaseTime;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.CLUSTER_SINGLETON_LOCK_NAME;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.FED_REPO_FULL_SYNC_LOCK_NAME;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.FORCE_OPTIMIZATION_LOCK_NAME;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.HEAVY_QUERY_CACHE_LOCK_NAME;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.OBS_REPO_INIT_LOCK_NAME;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.REPLICATIONS_LOCK_NAME;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.REPO_CREATION_LOCK_NAME;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.REPO_DELETION_LOCK_NAME;
import static org.artifactory.concurrent.NonStandardLockCategory.Constants.SHIFT_EVENTS_LOCK_NAME;

/**
 * Supports dynamic lock expiry & orphan lock criteria for non-standard locks
 *
 * @author Uriah Levy
 * @author Mor Merhav
 */
public enum NonStandardLockCategory {

    CLUSTER_SINGLETON(CLUSTER_SINGLETON_LOCK_NAME,
            ConstantValues.haOrphanClusterSingletonLockPeriodMinutes::getInt),
    REPLICATION(REPLICATIONS_LOCK_NAME, replicationLockLeaseTime::getInt,
            () -> (int) TimeUnit.SECONDS.toMinutes(haHeartbeatStaleIntervalSecs.getLong() * 4L)),
    FORCE_BINARY_SHARDING_OPTIMIZATION(FORCE_OPTIMIZATION_LOCK_NAME, () -> (int) TimeUnit.DAYS.toMinutes(1),
            () -> (int) TimeUnit.DAYS.toMinutes(1)),
    HEAVY_QUERY_CACHE(HEAVY_QUERY_CACHE_LOCK_NAME, hqcLockLeaseTime::getInt,
            () -> haHeartbeatStaleIntervalSecs.getInt() * 3),
    FED_REPO_FULL_SYNC(FED_REPO_FULL_SYNC_LOCK_NAME, federatedRepoFullSyncLockTimeMin::getInt,
            federatedRepoFullSyncStaleLockTimeMin::getInt),
    FED_SHIFT_EVENTS(SHIFT_EVENTS_LOCK_NAME, ConstantValues.haShiftEventsLockLeasePeriodMinutes::getInt,
            ConstantValues.haOrphanShiftEventsLockPeriodMinutes::getInt),
    OBS_REPO_INIT(OBS_REPO_INIT_LOCK_NAME, ConstantValues.haRepoCreationLockExpirationPeriodMinutes::getInt,
            ConstantValues.haRepoCreationLockOrphanedMinutes::getInt),
    REPO_CREATION(REPO_CREATION_LOCK_NAME, ConstantValues.haRepoCreationLockExpirationPeriodMinutes::getInt,
            ConstantValues.haRepoCreationLockOrphanedMinutes::getInt),
    REPO_DELETION(REPO_DELETION_LOCK_NAME, ConstantValues.haRepoDeletionLockExpirationPeriodMinutes::getInt,
            ConstantValues.haRepoDeletionLockOrphanedMinutes::getInt);

    final String name;
    final IntSupplier lockLeaseTimeMinutesSupplier;
    final IntSupplier orphanLockGraceTimeMinutesSupplier;

    NonStandardLockCategory(String name, IntSupplier lockLeaseTimeProvider,
            IntSupplier orphanLockGraceTimeMinutesSupplier) {
        this.name = name;
        this.lockLeaseTimeMinutesSupplier = lockLeaseTimeProvider;
        this.orphanLockGraceTimeMinutesSupplier = orphanLockGraceTimeMinutesSupplier;
    }

    NonStandardLockCategory(String name, IntSupplier orphanLockGraceTimeMinutesSupplier) {
        this(name, () -> -1, orphanLockGraceTimeMinutesSupplier);
    }

    public boolean isExpirable() {
        return lockLeaseTimeMinutesSupplier.getAsInt() != -1;
    }

    public int getLockLeaseTimeMinutes() {
        return lockLeaseTimeMinutesSupplier.getAsInt();
    }

    public int getOrphanLockGraceTimeMinutes() {
        return orphanLockGraceTimeMinutesSupplier.getAsInt();
    }

    public String getName() {
        return name;
    }

    public abstract static class Constants {
        private Constants() {
        }

        public static final String FED_REPO_FULL_SYNC_LOCK_NAME = "Full sync triggering";
        public static final String FORCE_OPTIMIZATION_LOCK_NAME = "force_optimization";
        public static final String REPLICATIONS_LOCK_NAME = "replications";
        public static final String CLUSTER_SINGLETON_LOCK_NAME = "cluster_singleton";
        public static final String SHIFT_EVENTS_LOCK_NAME = "shift_events";
        public static final String HEAVY_QUERY_CACHE_LOCK_NAME = "hqc";
        public static final String OBS_REPO_INIT_LOCK_NAME = "obs_repo_init";
        public static final String REPO_CREATION_LOCK_NAME = "repo_creation";
        public static final String REPO_DELETION_LOCK_NAME = "repo_deletion";
    }
}
