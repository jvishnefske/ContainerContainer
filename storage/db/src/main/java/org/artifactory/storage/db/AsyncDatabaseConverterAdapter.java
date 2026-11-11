package org.artifactory.storage.db;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ArtifactoryConverterAdapter;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.storage.db.locks.service.HaInitLock;
import org.artifactory.storage.db.locks.service.HaInitLockFactory;
import org.artifactory.storage.db.locks.service.HaInitLockType;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.storage.jobs.async.database.DatabaseConverterRunnable;
import org.artifactory.util.AolUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterRuntimeException;
import org.jfrog.converter.DbDetails;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.converter.util.DbConverterUtil;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;

import jakarta.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.artifactory.storage.jobs.async.database.DatabaseConverterRunnable.ARTIFACTORY_ASYNC_CONVERTER_LOCK_KEY;

/**
 * Run any necessary conversions to bring the system to the target version.
 * This converter is running as async after all the other converters were finished.
 * Need to be used only when its an optional conversion that can take time (for example adding index's).
 *
 * @author Alexei Vainshtein
 */
@Slf4j
public class AsyncDatabaseConverterAdapter implements ArtifactoryConverterAdapter {
    private final ArtifactoryHome artifactoryHome;
    public AsyncDatabaseConverterAdapter(ArtifactoryHome artifactoryHome) {
        this.artifactoryHome = artifactoryHome;
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        final JdbcHelper jdbcHelper = ContextHelper.get().beanForType(JdbcHelper.class);
        ScheduledExecutorService executorService = getScheduledExecutorService();
        try {
            executorService.schedule(
                    new DatabaseConverterRunnable(artifactoryHome, target, jdbcHelper, getAsyncConversionLock()),
                    getMinutesDelay(), TimeUnit.MINUTES);
            executorService.shutdown();
        } catch (Exception e) {
            log.error("An error occurred: {}.", e.getMessage());
            log.debug("An error occurred", e);
        }
    }

    @VisibleForTesting
    List<DbVersion> getDbVersions(DbType dbType, JdbcHelper jdbcHelper) {
        List<DbVersion> dbVersions;
        try {
            dbVersions = DbConverterUtil.calculateDbVersionsForExecution(
                    new DbDetails(dbType, jdbcHelper), artifactoryHome.getServerId(), new DbVersion[] {},
                    ArtifactoryDBVersion.get(ConverterExecutionType.SEQ_ASYNC)
            );
        } catch (DbConverterExecutionException | SQLException e) {
            throw new DbConverterRuntimeException("Error during dbVersions calculations", e);
        }
        return dbVersions;
    }

    int getMinutesDelay() {
        int minForDelay = 0;
        if (AolUtils.isAol() && ConstantValues.delayDbAsyncConvertorsExecution.getBoolean()) {
            minForDelay = RandomUtils.nextInt(1, 100);
            log.info("Async converter will run in {} minutes", minForDelay);
        }
        return minForDelay;
    }

    @NonNull
    protected ScheduledExecutorService getScheduledExecutorService() {
        return Executors.newScheduledThreadPool(1);
    }

    @Override
    public boolean isInterested(@Nullable CompoundVersionDetails source,
            CompoundVersionDetails target) {
        return true;
    }

    @Override
    public void revert() {

    }

    @Override
    public void backup() {

    }

    @Override
    public void clean() {

    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home,
            CompoundVersionDetails fromVersion, CompoundVersionDetails toVersion)
            throws ConverterPreconditionException {
        if (artifactoryHome == null) {
            throw new ConverterPreconditionException("Artifactory home is missing");
        }

        if (artifactoryHome.getDBProperties() == null) {
            throw new ConverterPreconditionException("DbProperties is missing");
        }
    }

    private HaInitLock getAsyncConversionLock() {
        return HaInitLockFactory.createLock(ARTIFACTORY_ASYNC_CONVERTER_LOCK_KEY, HaInitLockType.POLLING,
                () -> true);
    }
}
