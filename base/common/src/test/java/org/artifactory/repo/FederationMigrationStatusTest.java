package org.artifactory.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Alexei Vainshtein
 */
class FederationMigrationStatusTest {

    @Test
    void minimumReached() {
        assertFalse(
                FederationMigrationStatus.MIGRATION_COMPLETED.minimumReached(
                        FederationMigrationStatus.MIGRATING_EVENTS_TO_RTFS));
        assertTrue(
                FederationMigrationStatus.MIGRATION_COMPLETED.minimumReached(
                        FederationMigrationStatus.MIGRATION_COMPLETED));
        assertTrue(
                FederationMigrationStatus.MIGRATING_EVENTS_TO_RTFS.minimumReached(
                        FederationMigrationStatus.MIGRATION_COMPLETED));
        assertTrue(
                FederationMigrationStatus.MIGRATION_STARTED.minimumReached(
                        FederationMigrationStatus.MIGRATING_EVENTS_TO_RT));
        assertFalse(FederationMigrationStatus.MIGRATION_COMPLETED.minimumReached(null));
    }
}