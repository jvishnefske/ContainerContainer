/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.converter;


import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryCommonDbPropertiesService;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.config.db.DbVersionDao;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.ArtifactoryVersionReader;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.InstallationType;
import org.artifactory.version.VersionProvider;
import org.jfrog.storage.JdbcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

import java.io.File;

/**
 * @author Gidi Shabat
 */
public class VersionProviderImpl implements VersionProvider {
    // Test use only
    public static final String RUNNING_VERSION_OVERRIDE_SYSTEM_PROP = "artifactory.debug.runningVersion";
    public static final DbVersionInfo INITIAL_DB_VERSION_INFO =
            new DbVersionInfo(1, "7.0.0", 70000900, 1, "7.0.0");


    private static final Logger log = LoggerFactory.getLogger(VersionProviderImpl.class);

    // Test use only
    private static final String FROM_VERSION_OVERRIDE_SYSTEM_PROP = "artifactory.debug.fromVersion";

    /**
     * The current running service version, discovered during runtime.
     */
    private CompoundVersionDetails runningServiceVersion;
    /**
     * The current running product version, discovered during runtime.
     */
    private final CompoundVersionDetails runningProductVersion;

    /**
     * Denotes the original version this instance is coming from, if there was one, null if no previous version data
     * was available.
     * -- GETTER --
     * The originalServiceVersion value is null until access to db is allowed
     */
    @Getter
    private CompoundVersionDetails originalDbVersion;

    private final ArtifactoryHome artifactoryHome;
    private CompoundVersionDetails originalHomeVersion;
    private JdbcHelper jdbcHelper;

    public VersionProviderImpl(ArtifactoryHome artifactoryHome) {
        this.artifactoryHome = artifactoryHome;
        this.runningServiceVersion = artifactoryHome.getRunningArtifactoryServiceVersion();
        this.runningProductVersion = artifactoryHome.getRunningArtifactoryProductVersion();
    }

    @Override
    public void initOriginalHomeVersion() {
        resolveOriginalVersionFromOverridePropertyForTest();
        resolveRunningVersionFromOverridePropertyForTest();
        if (originalHomeVersion != null) {
            //Original version resolved from debug property - good to go.
            return;
        }
        try {
            loadOriginalHomeVersion();
            String locallyResolvedVersion = originalHomeVersion == null ? "New Installation" :
                    originalHomeVersion.getVersion().getVersion();
            log.debug("Last Artifactory version (resolved from local artifactory.properties file) is: {}",
                    locallyResolvedVersion);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public InstallationType getInstallationType() {
        CompoundVersionDetails from = getOriginalDbVersion();
        CompoundVersionDetails to = getRunningServiceVersion();
        if (inValidVersion(from) || inValidVersion(to)) {
            writeCompoundVersionDetailsToLog(from, to);
            return InstallationType.UNKNOWN;
        }
        if (from.getVersion().before(to.getVersion())) {
            writeCompoundVersionDetailsToLog(from, to);
            return InstallationType.UPGRADE;
        }
        if (from.getVersion().after(to.getVersion())) {
            writeCompoundVersionDetailsToLog(from, to);
            return InstallationType.ROLLBACK;
        }
        return InstallationType.RESTART;
    }

    private boolean inValidVersion(CompoundVersionDetails compoundVersionDetails) {
        return compoundVersionDetails == null || compoundVersionDetails.getVersion() == null;
    }

    /**
     * Loads the original version from the database.
     */
    @Override
    public void init(JdbcHelper jdbcHelper) {
        this.jdbcHelper = jdbcHelper;
        // Used for test only!
        resolveOriginalVersionFromOverridePropertyForTest();
        if (originalDbVersion != null) {
            //Original version resolved from debug property - good to go.
            return;
        }
        try {
            loadOriginalDbVersion();
            log.debug("Last Artifactory database version is: {}", originalDbVersion == null ? "New Installation" :
                    originalDbVersion.getVersion().getVersion());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Provides an access interface to the db_properties table, depending on the state of the application's context
     * returns null if no version information is stored in the db or if the table does not exist
     */
    @Override
    public CompoundVersionDetails getCompoundVersionDetailsFromDbIfExists() {
        DbVersionInfo serviceVersionInfo = null;
        // Context is null --> still in conversion phase, need to use temp db channel
        if (ContextHelper.get() == null) {
            serviceVersionInfo = tryToResolveFromDb(jdbcHelper);
        } else {
            try {
                ArtifactoryCommonDbPropertiesService dbPropsService =
                        ContextHelper.get().beanForType(ArtifactoryCommonDbPropertiesService.class);
                if (dbPropsService.isDbPropertiesTableExists()) {
                    serviceVersionInfo = dbPropsService.getDbVersionInfo();
                }
            } catch (Exception e) {
                if (Boolean.parseBoolean(System.getProperty(ConstantValues.test.getPropertyName()))) {
                    log.warn("Failed to retrieve version information from DB using DbPropertiesService");
                    serviceVersionInfo = tryToResolveFromDb(jdbcHelper);
                } else {
                    throw e;
                }
            }
        }
        return serviceVersionInfo == null ? null : getDbCompoundVersionDetails(serviceVersionInfo);
    }

    private void loadOriginalDbVersion() {
        CompoundVersionDetails compoundVersionDetails = getCompoundVersionDetailsFromDbIfExists();
        // Handling the version is based on the existence of the db_properties table
        if (compoundVersionDetails != null) {
            populateExistingVersionFromDb(compoundVersionDetails);
        } else {
            log.debug("Resolved original db version '{}' from existing local artifactory.properties file",
                    originalHomeVersion.getVersion().getVersion());
            originalDbVersion = originalHomeVersion;
        }
    }

    /**
     * Populates version information from the db version information if it exists.
     */
    private void populateExistingVersionFromDb(CompoundVersionDetails versionDetails) {
        if (versionDetails == null) {
            // Special case for test - use latest version
            if (Boolean.parseBoolean(System.getProperty(ConstantValues.test.getPropertyName()))) {
                // If test get the version before the latest one to force most current conversion to run on the tests
                ArtifactoryServiceVersion artifactoryServiceVersion = ArtifactoryServiceVersion.getCurrent();
                originalDbVersion = new CompoundVersionDetails(artifactoryServiceVersion, "TEST", 0L);
                log.debug("Resolved original db version '{}' from db", originalHomeVersion.getVersion().getVersion());
            } else {
                log.warn("db_properties table exists in database but contains no information - version information will"
                        + " be resolved based on the local filesystem if such information exists.");
            }
        } else {
            originalDbVersion = versionDetails;
        }
    }

    private void loadOriginalHomeVersion() {
        File artifactoryPropertiesFile = artifactoryHome.getArtifactoryPropertiesFile();
        if (!artifactoryPropertiesFile.exists()) {
            // Conversion was probably not done yet, try to get the file from the previous location
            artifactoryPropertiesFile = artifactoryHome.getArtifactoryOldPropertiesFile();
            if (!artifactoryPropertiesFile.exists()) {
                // If the properties file doesn't exists, then create it in the new location
                artifactoryPropertiesFile = artifactoryHome.getArtifactoryPropertiesFile();
                artifactoryHome.writeArtifactoryProperties();
            }
        }
        // Load the original home version
        originalHomeVersion = ArtifactoryVersionReader.readFromFileAndFindVersion(artifactoryPropertiesFile);
    }

    private DbVersionInfo tryToResolveFromDb(JdbcHelper jdbcHelper) {
        DbVersionDao versionInfoDao;
        DbVersionInfo serviceVersionInfo = null;
        // This will run after local environment conversion for sure so we should have db.properties by now
        // if this was an upgrade, null means this is a new installation.
        ArtifactoryDbProperties dbConfig = artifactoryHome.getDBProperties();
        if (dbConfig != null) {
            versionInfoDao = new DbVersionDao(jdbcHelper);
            if (versionInfoDao.isDbPropertiesTableExists()) {
                if (versionInfoDao.isDbPropertiesEmpty()) {
                    /**
                     * Inserting initial version for cases where db_properties were deleted by mistake / migration.
                     * In that case all the convertros will run.
                     */
                    log.warn("Inserting initial version into db_properties table");
                    versionInfoDao.createVersion(INITIAL_DB_VERSION_INFO);
                    serviceVersionInfo = INITIAL_DB_VERSION_INFO;
                } else {
                    serviceVersionInfo = versionInfoDao.getDbServiceVersion();
                }
            }
        }
        return serviceVersionInfo;
    }

    @Override
    public CompoundVersionDetails getRunningServiceVersion() {
        return runningServiceVersion;
    }

    @Override
    public CompoundVersionDetails getRunningProductVersion() {
        return runningProductVersion;
    }

    @Nullable
    @Override
    public CompoundVersionDetails getOriginalHomeVersion() {
        return originalHomeVersion;
    }

    /**
     * <b>Used for test purposes</b>, gives you the ability to control the original version (and thus what
     * conversions will run) by using the {@link this#FROM_VERSION_OVERRIDE_SYSTEM_PROP} property that you can pass to
     * the jvm when starting the instance.
     */
    private void resolveOriginalVersionFromOverridePropertyForTest() {
        String versionOverride = System.getProperty(FROM_VERSION_OVERRIDE_SYSTEM_PROP);
        if (StringUtils.isNotBlank(versionOverride)) {
            log.warn("Version override property detected - 'From Version' will be set to: '{}'", versionOverride);
            ArtifactoryServiceVersion resolvedFromVersion;
            try {
                resolvedFromVersion = ArtifactoryServiceVersion.valueOf(versionOverride);
                originalDbVersion = new CompoundVersionDetails(resolvedFromVersion, "UNDEFINED", 0L);
                originalHomeVersion = originalDbVersion;
            } catch (IllegalArgumentException iae) {
                log.debug("", iae);
                log.error("Bad version value {} !", versionOverride);
            }
        }
    }

    /**
     * <b>Used for test purposes</b>, gives you the ability to control the running version by using the
     * {@link this#RUNNING_VERSION_OVERRIDE_SYSTEM_PROP} property that you can pass to the jvm when starting the
     * instance.
     */
    private void resolveRunningVersionFromOverridePropertyForTest() {
        String versionOverride = System.getProperty(RUNNING_VERSION_OVERRIDE_SYSTEM_PROP);
        ArtifactoryServiceVersion resolvedRunningVersion;
        if (StringUtils.isNotBlank(versionOverride)) {
            log.warn("Version override property detected - 'Running Version' will be set to: '{}'", versionOverride);
            try {
                resolvedRunningVersion = ArtifactoryServiceVersion.valueOf(versionOverride);
                runningServiceVersion = new CompoundVersionDetails(resolvedRunningVersion, "UNDEFINED", 0L);
            } catch (IllegalArgumentException iae) {
                log.debug("", iae);
                log.error("Bad version value {} !", versionOverride);
            }
        }
    }

    @VisibleForTesting
    CompoundVersionDetails getDbCompoundVersionDetails(DbVersionInfo dbProperties) {
        return ArtifactoryVersionReader.getCompoundVersionDetails(
                dbProperties.getArtifactoryVersion(),
                getRevisionStringFromInt(dbProperties.getArtifactoryVersion(),
                        dbProperties.getArtifactoryRevision()),
                "" + dbProperties.getArtifactoryRelease());
    }

    private String getRevisionStringFromInt(String artServiceVer, int rev) {
        if (rev <= 0 || rev == Integer.MAX_VALUE) {
            try {
                long revision = ArtifactoryServiceVersionProvider.getRevisionFromVersion(artServiceVer);
                return String.valueOf(revision);
            } catch (Exception e) {
                log.error("Failed to resolve revision from version '{}'", artServiceVer);
                return "" + Integer.MAX_VALUE;
            }
        }
        return "" + rev;
    }

    private static void writeCompoundVersionDetailsToLog(CompoundVersionDetails from, CompoundVersionDetails to) {
        writeVersionsToLog(from, to);
        writeRevisionToLog(from, to);
    }

    private static String retriveVersionName(CompoundVersionDetails compoundVersionDetails) {
        return isVersionExist(compoundVersionDetails) ? compoundVersionDetails.getVersionName() : "N/A";
    }

    private static String retriveRevision(CompoundVersionDetails compoundVersionDetails) {
        return isVersionExist(compoundVersionDetails) ? String.valueOf(compoundVersionDetails.getRevision()) : "N/A";
    }

    private static void writeRevisionToLog(CompoundVersionDetails from, CompoundVersionDetails to) {
        log.info("From Revision: '{}' To Revision: '{}'", retriveRevision(from), retriveRevision(to));
    }

    private static void writeVersionsToLog(CompoundVersionDetails from, CompoundVersionDetails to) {
        log.info("From Version: '{}' To Version: '{}'", retriveVersionName(from), retriveVersionName(to));
    }

    private static boolean isVersionExist(CompoundVersionDetails compoundVersionDetails) {
        return !(compoundVersionDetails == null || compoundVersionDetails.getVersion() == null);
    }
}