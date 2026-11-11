package org.artifactory.storage.jobs.async.database;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.locks.service.HaInitLock;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.util.CollectionUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterRuntimeException;
import org.jfrog.converter.DbDetails;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.converter.util.DbConverterUtil;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Alexei Vainshtein
 */
@Slf4j
public class DatabaseConverterRunnable implements Runnable {
    private final ArtifactoryHome artifactoryHome;
    private final CompoundVersionDetails targetVersion;
    private final JdbcHelper jdbcHelper;
    private final HaInitLock haInitLock;
    public static final String ARTIFACTORY_ASYNC_CONVERTER_LOCK_KEY = "artifactory_async_converter";

    public DatabaseConverterRunnable(ArtifactoryHome artifactoryHome, CompoundVersionDetails targetVersion,
            JdbcHelper jdbcHelper, HaInitLock haInitLock) {
        this.artifactoryHome = artifactoryHome;
        this.targetVersion = targetVersion;
        this.jdbcHelper = jdbcHelper;
        this.haInitLock = haInitLock;
    }

    @Override
    public void run() {
        log.info("Starting Async converter thread.");
        if (!haInitLock.acquire()) {
            return;
        }
        // Run the converters
        DbType dbType = artifactoryHome.getDBProperties().getDbType();
        try {
            List<DbVersion> dbVersions = getDbVersions(dbType, jdbcHelper);
            if (CollectionUtils.isNullOrEmpty(dbVersions)) {
                log.info("No async database converters were scheduled to run.");
                return;
            }
            log.info("Found {} database convertors to run.", dbVersions.size());
            DbConverterUtil.executeConverters(new DbDetails(dbType, jdbcHelper),
                    dbVersions, targetVersion.getVersionName(), artifactoryHome.getServerId());
            log.info("Async database conversion completed.");
        } catch (Exception e) {
            throw new DbConverterRuntimeException(e.getMessage(), e);
        } finally {
            if (artifactoryHome.isHaConfigured()) {
                haInitLock.release();
            }
        }
    }

    @VisibleForTesting
    List<DbVersion> getDbVersions(DbType dbType, JdbcHelper jdbcHelper) {
        try {
            return DbConverterUtil.calculateDbVersionsForExecution(
                    new DbDetails(dbType, jdbcHelper), artifactoryHome.getServerId(), new DbVersion[] {},
                    ArtifactoryDBVersion.get(ConverterExecutionType.SEQ_ASYNC)
            );
        } catch (DbConverterExecutionException | SQLException e) {
            throw new DbConverterRuntimeException("Error during dbVersions calculations", e);
        }
    }
}