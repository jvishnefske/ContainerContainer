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

package org.artifactory.storage.db.converter.markers;

import com.google.common.collect.Maps;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.config.db.ArtifactoryCommonDbPropertiesService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactoryConverter;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.config.CronConfigurationException;
import org.artifactory.converter.ArtifactoryConverterAdapter;
import org.artifactory.converter.ConverterBlocker;
import org.artifactory.converter.ConverterManager;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.ConverterType;
import org.artifactory.converter.local.PreInitConverter;
import org.artifactory.converter.postinit.PostInitConverterAdapter;
import org.artifactory.environment.converter.local.HomeConverterAdapter;
import org.artifactory.logging.converter.LoggingConverter;
import org.artifactory.storage.db.AsyncDatabaseConverterAdapter;
import org.artifactory.storage.db.DatabaseConverterAdapter;
import org.artifactory.storage.db.LockingDBConverterAdapter;
import org.artifactory.storage.db.environment.converter.shared.version.SharedEnvironmentConverter;
import org.artifactory.storage.db.fs.dao.VersionConfigDao;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.InstallationType;
import org.artifactory.version.VersionProvider;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.config.ifc.ConfigurationManager;
import org.jfrog.converter.DbDetails;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.artifactory.converter.ConverterType.ASYNC;
import static org.artifactory.converter.ConverterType.DATABASE;
import static org.artifactory.converter.ConverterType.HOME_FILES;
import static org.artifactory.converter.ConverterType.HOME_SYNC_FILES;
import static org.artifactory.converter.ConverterType.MARKERS;
import static org.artifactory.converter.ConverterType.POST_INIT;
import static org.artifactory.converter.ConverterType.PRE_INIT;


/**
 * The class manages the conversions process during Artifactory life cycle.
 * It creates a complete separation between the HOME, CLUSTER and database
 * environments, thoughts Artifactory converts each environment independently,
 * each environment has its own original version and each original version might
 * trigger the relevant conversion.
 * For HA environment, only the primary master node can do a conversion of the
 * cluster home and DB. And once primary is up and running,
 * shutdown events are sent to the slaves.
 *
 * @author Gidi Shabat
 */
public class ConverterManagerImpl implements ConverterManager {
    private static final Logger log = LoggerFactory.getLogger(ConverterManagerImpl.class);

    private final ConverterBlocker rollbackBlocker;
    private final ArtifactoryHome artifactoryHome;
    private final VersionProvider vp;
    private Map<ConverterType, ArrayList<ArtifactoryConverterAdapter>> converters;
    private boolean preInitConversionRunning;
    private boolean homeSyncConversionRunning;
    private boolean homeConversionRunning;
    private boolean databaseConversionRunning;
    private boolean markerConversionRunning;
    private boolean postInitConversionRunning;
    private ConfigurationManager configurationManager;
    private DbDetails dbDetails;

    public ConverterManagerImpl(ArtifactoryHome artifactoryHome, VersionProvider vp,
            ConfigurationManager configurationManager) {
        // Initialize
        initConvertersMap();
        this.configurationManager = configurationManager;
        this.artifactoryHome = artifactoryHome;
        this.vp = vp;
        this.dbDetails = new DbDetails();
        this.rollbackBlocker = createRollbackPreventer();

        // This converter runs on all nodes before Artifactory initialization
        converters.get(PRE_INIT).add(new PreInitConverter(artifactoryHome));
        converters.get(DATABASE).add(new DatabaseConverterAdapter(artifactoryHome, this.dbDetails));
        converters.get(ASYNC).add(new AsyncDatabaseConverterAdapter(artifactoryHome));
        converters.get(MARKERS).add(new MarkerConverterAdapter(artifactoryHome, this.dbDetails));
        //This converter run once (on the first node that start after upgrade)
        converters.get(HOME_SYNC_FILES).add(new SharedEnvironmentConverter(artifactoryHome));
        // This converter runs on all nodes after Artifactory initialization
        converters.get(HOME_FILES).add(new LoggingConverter(artifactoryHome.getEtcDir()));
        converters.get(HOME_FILES).add(new HomeConverterAdapter(artifactoryHome));
        //This converter run once after full start
        converters.get(POST_INIT).add(new PostInitConverterAdapter());
    }

    private ConverterBlocker createRollbackPreventer() {
        try {
            return (ConverterBlocker) Class.forName("org.artifactory.addon.ConverterBlockerImpl").newInstance();
        } catch (ClassNotFoundException e) {
            // We're in OSS no need for any action
            return (home, configManager) -> false;
        } catch (Exception e) {
            String msg =
                    "Unable to initialize the ConverterBlocker which verifies if the instance is ready for conversion";
            log.error(msg, e.getMessage());
            log.debug(msg, e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Converters for db-synced files. in normal usecases only the master will run this, nodes will retrieve from db.
     */
    @Override
    public void convertHomeSync() {
        assertNoConversionBlock(ConverterType.HOME_SYNC_FILES);
        homeSyncConversionRunning = convert(HOME_SYNC_FILES);
    }

    /**
     * Converters for stuff like logback which is not synced, every node may run this based on the 'originalVersion'
     * info it gets from the artifactory.properties file.
     */
    @Override
    public void convertHome() {
        homeConversionRunning = convert(HOME_FILES);
    }

    @Override
    public void convertDatabase() {
        assertNoConversionBlock(DATABASE);
        StreamSupportUtils.stream(converters.get(DATABASE)).forEach(this::serviceConvert);
    }

    @Override
    public void convertAsync() {
        assertNoConversionBlock(ASYNC);
        StreamSupportUtils.stream(converters.get(ASYNC))
                .forEach(this::serviceConvertAsync);
    }

    private void serviceConvertAsync(ArtifactoryConverterAdapter artifactoryConverterAdapter) {
        CompoundVersionDetails originalService = vp.getOriginalDbVersion();
        if (originalService != null && !originalService.isCurrent()) {
            // Could be a possibility that this is a clean install without a license. In such case, don't perform
            // check on the block convert
            shouldBlockConvert();
        }
        artifactoryConverterAdapter
                .convert(originalService, vp.getRunningServiceVersion());
    }

    void shouldBlockConvert() {
        if (rollbackBlocker.shouldBlockConvert(artifactoryHome, configurationManager)) {
            throw new IllegalStateException(
                    "Converter can't run since no matching license found, please add new license");
        }
    }

    /**
     * <b> Local Environment converters always run! they should be smart enough to know when and if to run. </b>
     */
    @Override
    public void convertPreInit() {
        assertPreInitNoConversionBlock();
        preInitConversionRunning = convert(PRE_INIT);
    }

    @Override
    public void convertMarkers() {
        assertNoConversionBlock(MARKERS);
        markerConversionRunning = convert(MARKERS);
    }

    private boolean convert(ConverterType type) {
        try {
            CompoundVersionDetails originalVersion = getOriginalVersion(type);
            CompoundVersionDetails running = vp.getRunningServiceVersion();
            if (convertersInterested(converters.get(type), originalVersion)) {
                // Before we perform the actual convert, we check if it is allowed using the ConverterBlocker interface.
                if ((ConverterType.HOME_FILES == type || ConverterType.HOME_SYNC_FILES == type) &&
                        rollbackBlocker.shouldBlockConvert(artifactoryHome, configurationManager)) {
                    throw new IllegalStateException(
                            "Converter can't run since no matching license found, please add new license");
                }
                String fromVersion = getFromVersion(originalVersion);
                String converterTypeName = type.name();
                log.info("Triggering {} conversion, from {} to {}", converterTypeName, fromVersion,
                        running.getVersion());
                runConverters(converters.get(type), originalVersion, running);
                log.info("Finished {} conversion, current version is: {}", converterTypeName, running.getVersion());
                return true;
            }
        } catch (Exception e) {
            handleException(e);
        }
        return false;
    }

    @Override
    public void serviceConvert(ArtifactoryConverter artifactoryConverter) {
        try {
            if (artifactoryConverter.isInterested(vp.getOriginalDbVersion(), vp.getRunningServiceVersion())) {
                shouldBlockConvert();
                CompoundVersionDetails running = vp.getRunningServiceVersion();
                CompoundVersionDetails originalService = vp.getOriginalDbVersion();
                log.debug("Starting ReloadableBean conversion for: {}, from {} to {}",
                        artifactoryConverter.getClass().getName(), originalService, running);
                databaseConversionRunning = true;
                artifactoryConverter.convert(originalService, running);
                log.debug("Finished ReloadableBean conversion for: {}", artifactoryConverter.getClass().getName());
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    @Override
    public void convertPostInit() {
        assertNoConversionBlock(ConverterType.POST_INIT);
        postInitConversionRunning = convert(POST_INIT);
        if (vp.getInstallationType() == InstallationType.ROLLBACK) {
            // Set rolled back version as inactive.
            VersionConfigDao versionConfigDao = ContextHelper.get().beanForType(VersionConfigDao.class);
            try {
                String version = vp.getRunningServiceVersion().getVersion().getVersion();
                versionConfigDao.update(version);
            } catch (Exception e) {
                log.error("Can't update version {}", vp.getRunningServiceVersion());
            }
        }
        if (isConverting()) {
            try {
                if (isDatabaseConversionInterested()) {
                    log.info("Updating database properties to running service version: {}, product version: {}",
                            vp.getRunningServiceVersion(), vp.getRunningProductVersion());
                    ArtifactoryCommonDbPropertiesService dbPropertiesService = ContextHelper.get().beanForType(
                            ArtifactoryCommonDbPropertiesService.class);
                    DbVersionInfo latest = dbPropertiesService.getDbVersionInfo();
                    DbVersionInfo dbVersionInfo = createDbVersionInfoFromVersion(vp.getRunningServiceVersion(),
                            vp.getRunningProductVersion());
                    if (latest != null && !latest.sameVersion(dbVersionInfo)) {
                        dbPropertiesService.updateDbVersionInfo(dbVersionInfo);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Unable to finish post-init conversions, the DB version cannot be updated", e);
            }
        }
    }

    @Override
    public void afterContextReady() {
        if (isConverting()) {
            cleanRunningFlags();
        }
        artifactoryHome.writeArtifactoryProperties();
    }

    private void handleException(Exception e) {
        if (e instanceof CronConfigurationException) {
            // we let saving cron (even if its no longer valid/malformed)
            // rather than fail upgrade, later on user can change it manually
            log.warn(e.getMessage());
            log.debug("", e);
        } else {
            String errMsg = e.getCause() != null ? e.getMessage() + " : " + e.getCause().getMessage() : e.getMessage();
            log.error("Conversion failed. You should analyze the error and retry launching Artifactory. Error is: {}",
                    errMsg);
            cleanRunningFlags();
            throw new IllegalStateException(errMsg, e);
        }
    }

    private void cleanRunningFlags() {
        homeSyncConversionRunning = false;
        homeConversionRunning = false;
        databaseConversionRunning = false;
        markerConversionRunning = false;
        postInitConversionRunning = false;
        preInitConversionRunning = false;
    }

    private DbVersionInfo createDbVersionInfoFromVersion(CompoundVersionDetails versionDetails,
            CompoundVersionDetails runningProductVersion) {
        long installTime = System.currentTimeMillis();
        return new DbVersionInfo(installTime,
                versionDetails.getVersionName(),
                (int) versionDetails.getRevision(),
                versionDetails.getTimestamp(),
                runningProductVersion.getVersionName()
        );
    }

    @Override
    public boolean isConverting() {
        return homeSyncConversionRunning || homeConversionRunning || preInitConversionRunning ||
                databaseConversionRunning || postInitConversionRunning || markerConversionRunning;
    }

    private boolean convertersInterested(List<ArtifactoryConverterAdapter> converters,
            CompoundVersionDetails originalVersion) {
        CompoundVersionDetails toVersion = vp.getRunningServiceVersion();
        for (ArtifactoryConverterAdapter converterAdapter : converters) {
            if (converterAdapter.isInterested(originalVersion, toVersion)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDatabaseConversionInterested() {
        return vp.getOriginalDbVersion() != null && !vp.getOriginalDbVersion().isCurrent();
    }

    private void runConverters(List<ArtifactoryConverterAdapter> converters, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) {
        for (ArtifactoryConverterAdapter converter : converters) {
            if (converter.isInterested(fromVersion, toVersion)) {
                converter.backup();
                converter.convert(fromVersion, toVersion);
            }
        }
    }

    private String getFromVersion(CompoundVersionDetails originalVersion) {
        String fromVersion = "new installation";
        if (originalVersion != null) {
            fromVersion = originalVersion.getVersion().getVersion();
        }
        return fromVersion;
    }

    public Map<ConverterType, ArrayList<ArtifactoryConverterAdapter>> getConverters() {
        return converters;
    }

    /**
     * Inits the converters map
     */
    private void initConvertersMap() {
        converters = Maps.newHashMap();
        converters.put(HOME_FILES, new ArrayList<>());
        converters.put(MARKERS, new ArrayList<>());
        converters.put(HOME_SYNC_FILES, new ArrayList<>());
        converters.put(POST_INIT, new ArrayList<>());
        converters.put(PRE_INIT, new ArrayList<>());
        converters.put(DATABASE, new ArrayList<>());
        converters.put(ASYNC, new ArrayList<>());
    }

    public void addPreInitConverter(ArtifactoryConverterAdapter converterAdapter) {
        converters.get(PRE_INIT).add(converterAdapter);
    }

    public void addHomeConverter(ArtifactoryConverterAdapter converterAdapter) {
        converters.get(HOME_FILES).add(converterAdapter);
    }

    public void setDatabaseConverter(ArtifactoryConverterAdapter converterAdapter) {
        converters.get(DATABASE).clear();
        converters.get(DATABASE).add(converterAdapter);
    }

    public void addSyncFilesConverter(ArtifactoryConverterAdapter converterAdapter) {
        converters.get(HOME_SYNC_FILES).add(converterAdapter);
    }

    @Override
    public void assertPreInitNoConversionBlock() {
        assertNoConversionBlock(ConverterType.PRE_INIT);
        assertNoConversionBlock(ConverterType.HOME_FILES);
    }

    @Override
    public void assertPostInitNoConversionBlock() {
        assertNoConversionBlock(ConverterType.HOME_SYNC_FILES);
        assertNoConversionBlock(ConverterType.POST_INIT);
    }

    @Override
    public void setJdbcHelper(JdbcHelper jdbcHelper) {
        this.dbDetails.setJdbcHelper(jdbcHelper);
        this.dbDetails.setDbType(artifactoryHome.getDBProperties().getDbType());
    }

    /**
     * Create and Set if Needed (When External Locking DB)
     * LockingDBConverterAdapter to convert External Locking DB
     */
    @Override
    public void setLockingDBConverters(JFrogDataSource jFrogDataSource) {
        Optional<DbType> lockingDbSpecificType = artifactoryHome.getDBProperties().getLockingDbSpecificType();
        if (lockingDbSpecificType.isPresent()) {
            this.converters.get(DATABASE)
                    .add(new LockingDBConverterAdapter(this.artifactoryHome,
                            new DbDetails(lockingDbSpecificType.get(), new JdbcHelper(jFrogDataSource))));
        }
    }

    private void assertNoConversionBlock(ConverterType converterType) {
        CompoundVersionDetails fromVersion = getOriginalVersion(converterType);
        CompoundVersionDetails toVersion = vp.getRunningServiceVersion();
        try {
            converters.get(converterType).stream()
                    .filter(converter -> converter.isInterested(fromVersion, toVersion))
                    .forEach(converter -> converter
                            .assertConversionPrecondition(artifactoryHome, fromVersion, toVersion));
        } catch (ConverterPreconditionException e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    private CompoundVersionDetails getOriginalVersion(ConverterType type) {
        switch (type) {
            case MARKERS:
            case PRE_INIT:
            case HOME_FILES:
                return vp.getOriginalHomeVersion();
            case HOME_SYNC_FILES:
            case DATABASE:
            case ASYNC:
            case POST_INIT:
                return vp.getOriginalDbVersion();
            default:
                throw new UnsupportedOperationException(
                        "No original version can be found for type " + type.name() + " conversion");
        }
    }
}
