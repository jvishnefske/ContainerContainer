/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.lifecycle.webapp.servlet;

import com.jfrog.base.JFrogService;
import lombok.Getter;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterManager;
import org.artifactory.converter.VersionProviderImpl;
import org.artifactory.lifecycle.storage.db.init.PostDbSchemaInitializationManager;
import org.artifactory.lifecycle.storage.db.servers.service.ArtifactoryServersCommonServiceImpl;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.storage.db.converter.markers.ConverterManagerImpl;
import org.artifactory.storage.db.fs.service.VersionHistoryServiceImpl;
import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.init.DbInitializationManager;
import org.artifactory.storage.db.init.MasterKeyBootstrapUtil;
import org.artifactory.storage.db.locks.service.HaInitLock;
import org.artifactory.storage.db.locks.service.HaInitLockFactory;
import org.artifactory.storage.fs.service.VersionHistoryService;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.InstallationType;
import org.artifactory.version.VersionProvider;
import org.jfrog.config.ifc.ConfigurationManager;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;

import static org.jfrog.config.ConfigurationManagerImpl.create;

/**
 * Entry-point for basic installation related initialization phases.
 *
 * @author Fred Simon on 9/16/16.
 */
public class BasicConfigurationManager {
    private static final Logger log = LoggerFactory.getLogger(BasicConfigurationManager.class);

    public final ArtifactoryHome artifactoryHome;
    public final ConfigurationManager configurationManager;
    public final VersionProvider versionProvider;
    public final ConverterManager convertersManager;
    private final MasterKeyBootstrapUtil masterKeyBootstrapUtil;
    private final HaInitLock haInitLock;
    private ArtifactoryTracer artifactoryTracer;
    private JdbcHelper jdbcHelper;
    private final VersionHistoryService versionHistoryService;
    @Getter
    private boolean schemaCreatedByMe;

    BasicConfigurationManager(ServletContext servletContext) {
        this.artifactoryHome = (ArtifactoryHome) servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR);
        this.versionProvider = (VersionProvider) servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ);
        this.convertersManager = (ConverterManager) servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_CONVERTER_OBJ);
        this.configurationManager = (ConfigurationManager) servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_CONFIG_MANAGER_OBJ);
        this.schemaCreatedByMe = (boolean) servletContext
                .getAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED);
        this.masterKeyBootstrapUtil = new MasterKeyBootstrapUtil(artifactoryHome, configurationManager);
        this.artifactoryTracer = (ArtifactoryTracer) servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_TRACER_OBJ);
        this.versionHistoryService = (VersionHistoryService) servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_HISTORY_SERVICE_OBJ);
        this.haInitLock = (HaInitLock) servletContext.getAttribute(ArtifactoryHome.ARTIFACTORY_HA_INIT_LOCK_OBJ);
    }

    BasicConfigurationManager(ArtifactoryHome artifactoryHome, ArtifactoryTracer artifactoryTracer) {
        this(artifactoryHome, create(new ArtifactoryConfigurationAdapter(artifactoryHome)));
        this.artifactoryTracer = artifactoryTracer;
    }

    public BasicConfigurationManager(ArtifactoryHome artifactoryHome, ConfigurationManager configurationManager) {
        this.artifactoryHome = artifactoryHome;
        // Create configuration manager that will synchronize the shared files
        this.configurationManager = configurationManager;
        // Create the version provider managing running and previous versions
        this.versionProvider = new VersionProviderImpl(artifactoryHome);
        // Create the converter manager that will convert the needed configurations, files and DB tables
        this.convertersManager = new ConverterManagerImpl(artifactoryHome, versionProvider, configurationManager);
        this.masterKeyBootstrapUtil = new MasterKeyBootstrapUtil(artifactoryHome, configurationManager);
        this.versionHistoryService = new VersionHistoryServiceImpl(getOrCreateJdbcHelper(), artifactoryHome);

        this.haInitLock =
                HaInitLockFactory.createLock(ArtifactoryHome.ARTIFACTORY_HA_INIT_LOCK_KEY, this::shouldAcquireInitLock);
    }

    void addServletAttributes(ServletContext servletContext) {
        servletContext.setAttribute(ArtifactoryHome.SERVLET_CTX_ATTR, artifactoryHome);
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_CONVERTER_OBJ, convertersManager);
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_PROVIDER_OBJ, versionProvider);
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_CONFIG_MANAGER_OBJ, configurationManager);
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_TRACER_OBJ, artifactoryTracer);
        servletContext.setAttribute(ArtifactoryHome.IS_DATABASE_SCHEMA_NEWLY_CREATED, schemaCreatedByMe);
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_VERSION_HISTORY_SERVICE_OBJ, versionHistoryService);
        servletContext.setAttribute(ArtifactoryHome.ARTIFACTORY_HA_INIT_LOCK_OBJ, haInitLock);
    }

    /**
     * Extract specific init-params from the servlet context and set them in their corresponding {@link ConstantValues}
     * (only if a value exists - non <code>null</code>).
     *
     * @param servletContext the servlet context
     * @see ConstantValues#accessClientServerUrlOverride
     */
    void inheritInitParamsAsConstantValues(ServletContext servletContext) {
        String propertyName = ConstantValues.accessClientServerUrlOverride.getPropertyName();
        String value = servletContext.getInitParameter(propertyName);
        if (value != null) {
            artifactoryHome.getArtifactoryProperties().setProperty(propertyName, value);
        }
    }

    public void initialize() {
        initArtifactoryInstallation();
        artifactoryHome.initMimeTypes();
    }

    /**
     * Initialize DBs.
     * Initialize and run DB converters
     */
    private void initArtifactoryInstallation() {
        initVersion();
        printStartupSequenceStarted();
        checkRollbackAllowedIfNeeded();
        // Create Locking DB DS for external Locking DB
        JFrogDataSource lockingDBDataSource =
                CommonDataSourceFactory.createDifferentDatasourceForDbLock(artifactoryHome.getDBProperties(), true);
        DbInitializationManager dbInitializingManager = getDbInitializingManager(lockingDBDataSource);

        convertersManager.setJdbcHelper(getOrCreateJdbcHelper());
        // Create and Set LockingDBConverterAdapter When Has External Locking DB
        convertersManager.setLockingDBConverters(lockingDBDataSource);
        convertersManager.convertPreInit();
        schemaCreatedByMe = dbInitializingManager.init(); // schema initialization phase
        // Wait until the master key exists in the DB
        masterKeyBootstrapUtil.handleMasterKey();
        acquireHaInitLock();
        // Save upgrade data before home converters (logback) run.
        saveUpgradeData();
        getPostDbSchemaInitializationManager().init();
        saveBackwardCompatible();
        restoreRollbackLogbackIfNeeded();
        jdbcHelper.destroy();
    }

    /**
     * If a lock was acquired, it would be released on:
     * {@link org.artifactory.spring.ArtifactoryApplicationContext#contextCreated()}
     */
    private void acquireHaInitLock() {
        haInitLock.acquire();
    }

    boolean shouldAcquireInitLock() {
        CompoundVersionDetails versionDetails = versionProvider.getCompoundVersionDetailsFromDbIfExists();
        return artifactoryHome.isHaConfigured() &&
                (schemaCreatedByMe || versionDetails == null || !versionDetails.isCurrent());
    }

    private void initVersion() {
        versionProvider.initOriginalHomeVersion();
        versionProvider.init(getOrCreateJdbcHelper());
    }

    private DbInitializationManager getDbInitializingManager(JFrogDataSource lockingDBDataSource) {
        ArtifactoryDbProperties dbProperties = artifactoryHome.getDBProperties();
        return new DbInitializationManager(dbProperties, getOrCreateJdbcHelper(), lockingDBDataSource);
    }

    private PostDbSchemaInitializationManager getPostDbSchemaInitializationManager() {
        return new PostDbSchemaInitializationManager(artifactoryHome, configurationManager, versionProvider,
                convertersManager, new ArtifactoryServersCommonServiceImpl(getOrCreateJdbcHelper())
        );
    }

    private JdbcHelper getOrCreateJdbcHelper() {
        if (jdbcHelper == null) {
            ArtifactoryDbProperties dbProperties = artifactoryHome.getDBProperties();
            jdbcHelper = new JdbcHelper(CommonDataSourceFactory.createDataSource(dbProperties, true));
        }
        return jdbcHelper;
    }

    private void printStartupSequenceStarted() {
        log.info("{} ({}) service initialization started. Version: {} Revision: {} PID: {} Home: {}",
                JFrogService.ARTIFACTORY.getServiceName(), JFrogService.ARTIFACTORY.getServiceId(),
                versionProvider.getRunningServiceVersion().getVersion().getVersion(),
                versionProvider.getRunningServiceVersion().getRevision(),
                ProcessHandle.current().pid(), artifactoryHome.getHomeDir().getAbsolutePath());
    }

    public void checkRollbackAllowedIfNeeded() {
        InstallationType installationType = versionProvider.getInstallationType();
        CompoundVersionDetails to = versionProvider.getRunningServiceVersion();
        if (installationType == InstallationType.ROLLBACK &&
                !versionHistoryService.isRollbackAllowed(to.getVersion().getVersion())) {
            throw new ArtifactoryRollbackNotAllowedException("Can't rollback to version " + to);
        }
    }

    void restoreRollbackLogbackIfNeeded() {
        if (versionProvider.getInstallationType() == InstallationType.ROLLBACK) {
            String rollbackVersion = versionProvider.getRunningServiceVersion().getVersion().getVersion();
            log.debug("Getting logback from history for version {}", rollbackVersion);
            try {
                versionHistoryService.restoreLogbackFromConfigHistory(rollbackVersion);
            } catch (Exception e) {
                if (ConstantValues.rollbackLogbackRestoreFailOnError.getBoolean(artifactoryHome)) {
                    throw new RollbackLogbackRestoreException(rollbackVersion, e);
                }
                log.error("Failed to restore logback from config history for version {}: continuing startup",
                        rollbackVersion, e);
            }
        }
    }

    void saveBackwardCompatible() {
        InstallationType installationType = versionProvider.getInstallationType();
        if (installationType == InstallationType.UPGRADE) {
            CompoundVersionDetails from = versionProvider.getOriginalDbVersion();
            CompoundVersionDetails to = versionProvider.getRunningServiceVersion();
            try {
                versionHistoryService.saveBackwardCompatible(from.getVersion().getVersion(),
                        to.getVersion().getVersion());
            } catch (Exception e) {
                log.error("Can't save backward compatibility flag for upgrade from {} to {}", from, to, e);
            }
        }
    }

    public void saveUpgradeData() {
        InstallationType installationType = versionProvider.getInstallationType();
        CompoundVersionDetails from = versionProvider.getOriginalDbVersion();
        CompoundVersionDetails to = versionProvider.getRunningServiceVersion();
        if (installationType == InstallationType.UNKNOWN) {
            log.error("Can't save upgrade data, invalid versions");
            return;
        }
        if (installationType == InstallationType.UPGRADE) {
            try {
                // It's an upgrade, save data necessary for rollback.
                boolean saved = versionHistoryService.saveConfig();
                ArtifactoryServiceVersion fromVersion = from.getVersion();
                if (!saved) {
                    log.error("Can't save config history data for version {}", from.getVersion());
                    return;
                }
                versionHistoryService.saveVersionHistory(fromVersion.getVersion(), to.getVersion().getVersion());
            } catch (Exception e) {
                log.error("Can't save upgrade data for version {}", from, e);
            }
        }
    }
}