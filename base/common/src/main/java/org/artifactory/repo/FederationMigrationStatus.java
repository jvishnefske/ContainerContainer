package org.artifactory.repo;

/**
 * @author Alexei Vainshtein
 */
public enum FederationMigrationStatus {

    MIGRATION_STARTED(0),
    MIGRATING_EVENTS_TO_RTFS(1),
    MIGRATING_EVENTS_TO_RT(1),
    MIGRATION_COMPLETED_WITH_ERRORS(2),
    MIGRATION_COMPLETED(3);

    private final int value;
    FederationMigrationStatus(int i) {
        this.value = i;
    }

    public boolean minimumReached(FederationMigrationStatus providedStatus) {
        if (providedStatus == null) {
            return false;
        }
        return this.value <= providedStatus.value;
    }
}
