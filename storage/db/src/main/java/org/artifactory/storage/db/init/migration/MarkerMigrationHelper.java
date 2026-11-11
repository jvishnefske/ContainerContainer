package org.artifactory.storage.db.init.migration;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.init.migration.dao.MigrationStatusDao;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageServiceImpl;
import org.jfrog.common.ClockUtils;
import org.jfrog.storage.JdbcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Omri Ziv
 */
public class MarkerMigrationHelper {
    public static final String IDENTIFIER_PREFIX = "marker-migration-";
    private static final Logger log = LoggerFactory.getLogger(MarkerMigrationHelper.class);
    private static MarkerMigrationHelper current;

    MigrationStatusStorageService migrationStatusStorageService;
    ArtifactoryHome artifactoryHome;

    public MarkerMigrationHelper(MigrationStatusStorageService migrationStatusStorageService,
            ArtifactoryHome artifactoryHome) {
        this.artifactoryHome = artifactoryHome;
        this.migrationStatusStorageService = migrationStatusStorageService;
    }

    public List<MigrationStatus> getUnfinished(List<String> migrationNames) {
        return migrationStatusStorageService.findAllUnfinishedIn(migrationNames);
    }

    public void insertMigrationOfIdentifier(String identifierName) {
        log.debug("Insert Migration of identifier '{}'", identifierName);
        MigrationStatus migrationStatus = new MigrationStatus(identifierName, ClockUtils.epochMillis(), 0, null);
        migrationStatusStorageService.insertMigration(migrationStatus);
    }

    public MigrationStatus findByUniqueIdentifier(String identifierName) {
        log.debug("getting Migration of identifier '{}'", identifierName);
        return migrationStatusStorageService.findMigrationById(identifierName);
    }

    public void finish(String identifierName) {
        log.debug("Marking '{}' as done.", identifierName);
        migrationStatusStorageService.migrationDone(identifierName);
    }

    public static MarkerMigrationHelper createFromArtifactoryHome(ArtifactoryHome artifactoryHome,
            JdbcHelper jdbcHelper) {
        if (current != null && current.getArtifactoryHome() == artifactoryHome) {
            return current;
        }
        MigrationStatusDao migrationStatusDao = new MigrationStatusDao(jdbcHelper, artifactoryHome.getDBProperties());
        if (artifactoryHome.isHaConfigured()) {
            return new HaMarkerMigrationHelper(new MigrationStatusStorageServiceImpl(migrationStatusDao),
                    artifactoryHome);
        } else {
            return new MarkerMigrationHelper(new MigrationStatusStorageServiceImpl(migrationStatusDao),
                    artifactoryHome);
        }
    }

    public static void bind(MarkerMigrationHelper markerMigrationHelper) {
        current = markerMigrationHelper;
    }

    public boolean shouldConvert(String identifierName) {
        MigrationStatus migrationStatus = findByUniqueIdentifier(identifierName);
        if (migrationStatus == null || isFinished(migrationStatus)) {
            log.debug("");
            return false;
        }
        return true;
    }

    public void reset(String identifierName) {
        log.debug("Resenting migration '{}'.", identifierName);
        migrationStatusStorageService.delete(identifierName);
        insertMigrationOfIdentifier(identifierName);
    }

    boolean isFinished(MigrationStatus migrationStatus) {
        return migrationStatus.getFinished() > 0;
    }

    public ArtifactoryHome getArtifactoryHome() {
        return artifactoryHome;
    }

}
