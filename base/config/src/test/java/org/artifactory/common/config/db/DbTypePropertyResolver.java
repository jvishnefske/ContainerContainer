package org.artifactory.common.config.db;

import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.DbType;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * The purpose of this class is to resolve the DbType from the system properties or environment variables.
 * It serves as a consistent way to resolve the DbType for devenv, itests and DB tests.
 * The DbType can be set by the system property: <b>'-Drt.db.type'</b> or the environment variable: <b>'rt.db.type'</b>.
 * Valid values are the names of the {@link DbType} enum values (case-insensitive), or {@link DbType}-<suffix> from
 * supported suffixes defined in: {@link #DB_CONFIG_FILENAME_SUFFIXES}. This is used in order to load pre-prepared
 * properties (See for example: artifactory-oss/storage/db/src/test/resources/db/postgresql-docker.properties)
 * If the DbType is not set, the default DbType will be returned.
 * Backwards compatibility:
 * The DbType can also be set by the old system property: '-Dartifactory.db.config.name' or the old environment
 * variable: 'override.db' (not recommended).
 *
 * @author Mor Merhav
 */
@Slf4j
@UtilityClass
public class DbTypePropertyResolver {

    @VisibleForTesting
    static final String DB_TYPE_PROPERTY = "rt.db.type";
    @VisibleForTesting
    static final String BACKWARDS_COMPATIBLE_DB_CONFIG_NAME_PROPERTY = "artifactory.db.config.name";
    @VisibleForTesting
    static final String BACKWARDS_COMPATIBLE_OVERRIDE_DB_PROPERTY = "override.db";
    private static final Set<String> DB_CONFIG_FILENAME_SUFFIXES = Set.of("-docker", "-windows-1", "-docker-unitests");


    public static DbType resolveOrDefault(DbType defaultDbType) {
        DbType result;
        String dbTypeStr = Optional.ofNullable(getBySystemProperty()).orElse(getByEnvironmentVariable());
        if (dbTypeStr == null) {
            result = defaultDbType;
        } else {
            try {
                result = DbType.parse(dbTypeStr);
            } catch (IllegalArgumentException e) {
                result = searchForDbTypeByFilename(dbTypeStr);
            }
        }
        log.info("Resolved DB type: {}", result);
        return result;
    }

    public static String resolveDbNameOrDefault(String defaultName) {
        String dbName = Optional.ofNullable(getBySystemProperty()).orElse(getByEnvironmentVariable());
        if (dbName == null) {
            dbName = defaultName.toLowerCase();
        }
        log.info("Resolved DB name: {}", dbName);
        return dbName;
    }

    public static String stripOldFilenameSuffix(String name) {
        for (String fileNameSuffix : DB_CONFIG_FILENAME_SUFFIXES) {
            if (name.endsWith(fileNameSuffix)) {
                name = name.replace(fileNameSuffix, "");
            }
        }
        return name;
    }

    private static String getBySystemProperty() {
        return Optional.ofNullable(System.getProperty(DB_TYPE_PROPERTY)).orElse(getByOldDbConfigName());
    }

    private static DbType searchForDbTypeByFilename(String dbFilename) {
        return Arrays.stream(DbType.values()).toList().stream()
                .filter(dbType -> dbFilename.toLowerCase().contains(dbType.name().toLowerCase()))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("Could not resolve DbType from DB filename: " + dbFilename));
    }

    private static String getByEnvironmentVariable() {
        return Optional.ofNullable(System.getenv(DB_TYPE_PROPERTY)).orElse(getByOldOverrideDb());
    }

    private static String getByOldOverrideDb() {
        return System.getenv(BACKWARDS_COMPATIBLE_OVERRIDE_DB_PROPERTY);
    }

    private static String getByOldDbConfigName() {
        return System.getProperty(BACKWARDS_COMPATIBLE_DB_CONFIG_NAME_PROPERTY);
    }
}