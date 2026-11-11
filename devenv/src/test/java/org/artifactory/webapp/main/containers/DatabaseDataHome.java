package org.artifactory.webapp.main.containers;


import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.DbType;

import java.io.File;
import java.nio.file.Paths;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;
import static org.jfrog.config.home.JFrogHome.DATA_FOLDER;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;

/**
 * @author William Arustamyan
 */
@Slf4j
@UtilityClass
public final class DatabaseDataHome {

    public static File buildPostgres(final File artHome) {
        return build(artHome, DbType.POSTGRESQL);
    }

    public static File buildDerby(final File artHome) {
        return build(artHome, DbType.DERBY);
    }

    public static File build(final File artHome, final DbType dbType) {
        _validateNotNull(artHome, dbType);
        log.info("Starting database data directory creation");
        final File dbDataDir = Paths.get(dataDir(artHome).getAbsolutePath(), dbType.name().toLowerCase()).toFile();

        if (!dbDataDir.exists()) {
            log.warn("Db data directory for db with type : {} not found. Creating", dbType);
            if (!dbDataDir.mkdirs()) {
                log.error("Unable to create db data directory ...");
                throw new RuntimeException("Unable to create database data home ... ");
            }
        }
        log.info("Database data directory exists or successfully created.");
        return dbDataDir;
    }

    private static File dataDir(final File artHome) {
        return Paths.get(artHome.getAbsolutePath(), VAR_FOLDER, DATA_FOLDER, ARTIFACTORY_SERVICE_NAME).toFile();
    }

    private static void _validateNotNull(final File artHome, final DbType dbType) {
        if (artHome == null || dbType == null) {
            throw new RuntimeException("Artifactory home or db type cannot be null");
        }
    }
}
