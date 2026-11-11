package org.artifactory.storage.db.init.migration;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.jfrog.common.ClockUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Omri Ziv
 */
public class HaMarkerMigrationHelper extends MarkerMigrationHelper {

    private static final Logger log = LoggerFactory.getLogger(MarkerMigrationHelper.class);

    HaMarkerMigrationHelper(MigrationStatusStorageService migrationStatusStorageService,
            ArtifactoryHome artifactoryHome) {
        super(migrationStatusStorageService, artifactoryHome);
    }

    @Override
    public void insertMigrationOfIdentifier(String identifierName) {
        log.debug("Insert Migration of identifier '{}'", identifierName);
        String serverId = artifactoryHome.getHaNodeProperties() != null ?
                artifactoryHome.getHaNodeProperties().getServerId() : StringUtils.EMPTY;
        log.debug("Insert Migration of identifier '{}' with server id '{}'", identifierName, serverId);
        MigrationStatus migrationStatus = new MigrationStatus(identifierName, ClockUtils.epochMillis(), 0,
                new MarkerMigrationInfoBlob(serverId));
        migrationStatusStorageService.insertMigrationWithInfoBlob(migrationStatus);
    }

    @Override
    public MigrationStatus findByUniqueIdentifier(String identifierName) {
        MigrationStatus migrationStatus;
        log.debug("getting Migration of identifier '{}'", identifierName);
        migrationStatus = migrationStatusStorageService.findMigrationByIdWithInfoBlob(
                identifierName, MarkerMigrationInfoBlob.class);
        return migrationStatus;
    }

    @Override
    public boolean shouldConvert(String identifierName) {
        MigrationStatus migrationStatus = findByUniqueIdentifier(identifierName);
        if (migrationStatus == null || isFinished(migrationStatus)) {
            log.debug("");
            return false;
        }
        String serverId = artifactoryHome.getHaNodeProperties() != null ?
                artifactoryHome.getHaNodeProperties().getServerId() : StringUtils.EMPTY;
        MarkerMigrationInfoBlob markerMigrationInfoBlob = (MarkerMigrationInfoBlob) migrationStatus
                .getMigrationInfoBlob();
        if (markerMigrationInfoBlob == null) {
            throw new IllegalStateException("Missing info blog of marker migration with identifier " + identifierName);
        }
        return StringUtils.equals(markerMigrationInfoBlob.getServerId(), serverId);
    }
}
