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

package org.artifactory.storage.db;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.converter.VersionProviderImpl;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.mbean.ManagedDataSource;
import org.artifactory.storage.db.mbean.NewDbInstallation;
import org.artifactory.storage.db.properties.service.ArtifactoryDbPropertiesService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.IdGenerator;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.storage.DataSourceHealthException;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.priviledges.DBPrivilegesVerifierFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.jfrog.storage.util.DbUtils.doWithConnection;
import static org.jfrog.storage.util.DbUtils.getActiveCatalog;
import static org.jfrog.storage.util.DbUtils.getActiveSchema;
import static org.jfrog.storage.util.DbUtils.normalizedName;
import static org.jfrog.storage.util.DbUtils.withMetadata;

/**
 * @author Yossi Shaul
 */
@Repository
@Reloadable(beanClass = DbService.class)
public class DbServiceImpl implements InternalDbService {
    private static final Logger log = LoggerFactory.getLogger(DbServiceImpl.class);

    //I am the god of legacy code, gaze upon me and despair :(
    private boolean sha256Ready = false;
    private final AtomicLong healthCheckCacheInvalidationLastChecked = new AtomicLong(0);
    private final ReentrantLock lock = new ReentrantLock();
    long waitBetweenInitialConnectionRetry = 2000;

    @Autowired
    private ArtifactoryJdbcHelper jdbcHelper;

    @Autowired
    @Qualifier("dbProperties")
    private ArtifactoryDbProperties dbProperties;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private ArtifactoryDbPropertiesService dbPropertiesService;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private Set<JFrogDataSource> allDataSources;

    @Override
    @PostConstruct
    public void initDatabaseService() throws SQLException {
        if (isNewInstallation()) {
            log.debug("New installation. SHA2 set to ready.");
            sha256Ready = true;
            broadcastNewDbInstallation();
            updateDbPropertiesTable();
            dbPropertiesService.deleteDbProperty(VersionProviderImpl.INITIAL_DB_VERSION_INFO);
        } else if (dbPropertiesService.getDbVersionInfo() == null) {
            broadcastNewDbInstallation();
        }
        initializeIdGenerator();
    }

    @Override
    public void init() {
        registerDataSourceMBean();
        verifySha256State();
    }

    @Override
    public DbType getDatabaseType() {
        return dbProperties.getDbType();
    }

    @Override
    public DbMetaData getDbMetaData() {
        try {
            return withMetadata(jdbcHelper, DbMetaData::new);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public long nextId() {
        return idGenerator.nextId();
    }

    @Override
    public void compressDerbyDb(BasicStatusHolder statusHolder) {
        DerbyUtils.compress(statusHolder, dbProperties.getDbType());
    }

    @Override
    public <T> T invokeInTransaction(String transactionName, Callable<T> execute) {
        if (StringUtils.isNotBlank(transactionName)) {
            TransactionSynchronizationManager.setCurrentTransactionName(transactionName);
        }
        try {
            return execute.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    boolean isNewInstallation() {
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        return artifactoryContext != null && artifactoryContext.isVanillaInstallation();
    }

    void broadcastNewDbInstallation() {
        log.debug("Broadcasting new DB Installation event");
        publisher.publishEvent(new NewDbInstallation(getClass().getSimpleName()));
    }

    void updateDbPropertiesTable() {
        long installTime = System.currentTimeMillis();
        CompoundVersionDetails versionDetails = ArtifactoryHome.get().getRunningArtifactoryServiceVersion();
        String versionStr = versionDetails.getVersion().getVersion();
        long timestamp = versionDetails.getTimestamp();
        int revisionInt = (int) versionDetails.getRevision();
        CompoundVersionDetails productVersionDetails = ArtifactoryHome.get().getRunningArtifactoryProductVersion();
        String productVersionStr = productVersionDetails.getVersion().getVersion();
        dbPropertiesService.updateDbVersionInfo(
                new DbVersionInfo(installTime, versionStr, revisionInt, timestamp, productVersionStr));
    }

    //used via reflection by DbBaseTest
    private void initializeIdGenerator() throws SQLException {
        idGenerator.initializeIdGenerator();
    }

    private void registerDataSourceMBean() {
        try {
            if (jdbcHelper.getDataSource().isWrapperFor(ArtifactoryDataSource.class)) {
                ArtifactoryDataSource artifactoryDS = jdbcHelper.getDataSource().unwrap(ArtifactoryDataSource.class);
                MBeanRegistrationServiceImpl mbeansService =
                        ContextHelper.get().beanForType(MBeanRegistrationServiceImpl.class);
                mbeansService.register(new ManagedDataSource(artifactoryDS, jdbcHelper), "Storage", "Data Source");
                artifactoryDS.registerMBeans(mbeansService);
            }
        } catch (SQLException sqlException) {
            log.debug(sqlException.getMessage());
            log.trace("Got SQLException: ", sqlException);
        }
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        runEnforceDBPrivilegesConversion();
        updateDbPropertiesTable();
        verifySha256State();
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        InternalDbService txMe = ContextHelper.get().beanForType(InternalDbService.class);
        txMe.verifyMigrations();
    }

    @Override
    public void destroy() {
        jdbcHelper.destroy();
    }

    /**
     * TO BE USED ONLY BY THE SHA256 MIGRATION JOB
     * Tests the db metadata for the sha256 column's (in binaries table) nullable state,
     * and sets the state's flag {@link #sha256Ready} accordingly
     *
     * @return {@link #sha256Ready}
     */
    @Override
    public boolean verifySha256State() {
        if (sha256Ready) {
            //Already verified to be ok, no need to do it again
            return true;
        }
        try {
            doWithConnection(jdbcHelper, this::setSha256State);
        } catch (Exception e) {
            log.warn("Can't determine state of sha256 column in binaries table: {}", e.getMessage());
            log.debug("", e);
        }
        log.debug("Determined SHA256 readiness state to be: {}", sha256Ready);
        return sha256Ready;
    }

    private void setSha256State(Connection conn) throws SQLException {
        DbType dbType = dbProperties.getDbType();
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet rs = metadata.getColumns(getActiveCatalog(conn, dbType), getActiveSchema(conn, dbType),
                normalizedName("binaries", metadata), normalizedName("sha256", metadata))) {
            if (rs.next()) {
                sha256Ready = "NO".equalsIgnoreCase(rs.getString(normalizedName("IS_NULLABLE", metadata)));
            } else {
                log.warn("Can't determine state of sha256 column in binaries table, column not found in db metadata.");
            }
        } catch (Exception e) {
            log.warn("Can't determine state of sha256 column in binaries table: {}", e.getMessage());
            log.debug("", e);
        }
    }

    @Override
    public boolean isSha256Ready() {
        return sha256Ready;
    }

    @Override
    public void verifyMigrations() {
        verifySha256State();
    }

    /**
     * Verify that DB is healthy
     */
    @Override
    public void health() throws DataSourceHealthException {
        boolean locked = false;
        try {
            long now = System.currentTimeMillis();
            if (now - healthCheckCacheInvalidationLastChecked.longValue() >
                    ConstantValues.dbDatasourceHealthCheckCacheInvalidationIntervalMillis.getLong()) {
                // This lock is used as a fail-fast mechanism in case HikariCP adder thread gets stuck during health
                // check, for more info see: https://jira.jfrog.org/browse/RTDEV-24918
                locked = lock.tryLock(ConstantValues.dbDataSourceHealthCheckLockRetrievalTimeoutMillis.getLong(),
                        TimeUnit.MILLISECONDS);
                if (locked) {
                    allDataSources.forEach(JFrogDataSource::health);
                    healthCheckCacheInvalidationLastChecked.set(now);
                } else {
                    throw new DataSourceHealthException(String.format(
                            "Datasource is not healthy. Failed to acquire lock for %d seconds",
                            TimeUnit.MILLISECONDS.toSeconds(
                                    ConstantValues.dbDataSourceHealthCheckLockRetrievalTimeoutMillis.getLong())));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataSourceHealthException("Datasource is not healthy: " + e.getMessage(), e);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    private void runEnforceDBPrivilegesConversion() {
        try {
            doWithConnection(jdbcHelper, this::enforceDBPrivileges);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void enforceDBPrivileges(Connection con) {
        File marker = ArtifactoryHome.get().getSkipVerifyPrivilegesMarkerFile();
        if (!marker.exists()) {
            try {
                if (!DBPrivilegesVerifierFactory.createDBPrivilegesVerifier(dbProperties.getDbType())
                        .isSufficientPrivileges(con, "artifactory")) {
                    log.error("Insufficient DB privileges found!. Not starting migration.");
                    throw new IllegalStateException("Insufficient DB privileges found!");
                }
            } catch (Exception e) {
                log.error("Error while verifying DB privileges. Not starting migration.");
                throw new IllegalStateException("Error while verifying DB privileges", e);
            }
        }
        marker.delete();
    }
}