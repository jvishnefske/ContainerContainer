package org.artifactory.storage.db.init.migration.service;

import org.artifactory.storage.db.init.migration.model.MigrationInfoBlob;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;

import java.util.Collection;
import java.util.List;

/**
 * @author Uriah Levy
 * A bussiness service for managing long running migrations.
 */
public interface MigrationStatusStorageService {

    /**
     * Find a stored migration status by its ID
     *
     * @param migrationId the ID of the migration
     * @return the Migration Status
     */
    MigrationStatus findMigrationById(String migrationId);

    /**
     * Find a stored migration status by its ID, including the migration information blob
     *
     * @param migrationId   the ID of the migration
     * @param infoBlobModel the model class of the migration information blob.
     * @return the Migration Status
     */
    MigrationStatus findMigrationByIdWithInfoBlob(String migrationId, Class<? extends MigrationInfoBlob> infoBlobModel);

    /**
     * Find migrations by prefix.
     *
     * @param migrationIdPrefix - migration id prefix
     * @param separator         - separator of migration identifier. In case if migration id has special characters
     *                          (for example '_') they should be escaped, so it should be provided as a parameter.
     *                          If no special characters - keep it null.
     * @param infoBlobModel     - target blob model
     */
    List<MigrationStatus> findMigrationByIdPrefixWithInfoBlob(String migrationIdPrefix, String separator,
            Class<? extends MigrationInfoBlob> infoBlobModel);

    List<MigrationStatus> findMigrationByIdPrefixWithInfoBlobBetweenDates(String migrationIdPrefix, Long from,
            Long to, Class<? extends MigrationInfoBlob> infoBlobModel);

    /**
     * Insert a new migration status
     *
     * @param migrationStatus a migration status to insert
     */
    void insertMigration(MigrationStatus migrationStatus);

    void updateMigration(MigrationStatus migrationStatus);

    /**
     * Insert a new migration status, with a migration information blob
     *
     * @param migrationStatus a migration status to insert
     */
    void insertMigrationWithInfoBlob(MigrationStatus migrationStatus);

    /**
     * Mark an existing migration status as done
     *
     * @param migrationId the identifier of the migration status to update
     */
    void migrationDone(String migrationId);

    List<MigrationStatus> findAllUnfinishedByPrefixWithInfoBlob(String prefix,
            Class<? extends MigrationInfoBlob> infoBlobModel);

    List<MigrationStatus> findLastByTimeAndPrefix(String prefix, long afterEpochTime,
            Class<? extends MigrationInfoBlob> infoBlobModel);

    List<MigrationStatus> findAllUnfinishedIn(List<String> migrationList);

    int countAllUnfinishedByPrefix(String prefix);

    void delete(String identifierName);

    Collection<String> findStartedSinceContainsPrefix(String retentionRestoreCommonPrefix, long startTime);
}
