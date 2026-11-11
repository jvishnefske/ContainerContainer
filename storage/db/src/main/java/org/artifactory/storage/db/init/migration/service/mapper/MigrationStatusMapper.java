package org.artifactory.storage.db.init.migration.service.mapper;

import org.artifactory.storage.db.init.migration.entity.DbMigrationStatus;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author Uriah Levy
 */
@Mapper(uses = MigrationInfoBlobTranslator.class)
public interface MigrationStatusMapper {

    @Mapping(target = "migrationInfoBlob", qualifiedByName = {"MigrationInfoBlobTranslator",
            "InfoBlobModelToByteArray"})
    DbMigrationStatus migrationStatusToDbMigrationStatus(MigrationStatus migrationStatus);

    @Mapping(target = "migrationInfoBlob", ignore = true)
    MigrationStatus dbMigrationStatusToMigrationStatus(DbMigrationStatus dbMigrationStatus);
}
