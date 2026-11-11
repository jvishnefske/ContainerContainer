package org.artifactory.storage.db.init;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.HikariJDBCDataSource;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.metric.MetricsDataSource;
import org.jfrog.storage.wrapper.JFrogDataSourceWrapper;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.artifactory.common.config.db.ArtifactoryDbProperties.DEFAULT_POOL_TYPE;
import static org.jfrog.storage.DataSourceFactory.wrapWithMetricsDataSource;
import static org.jfrog.storage.DbProperties.TOMCAT_POOL_TYPE;

/**
 * Creates a DataSource from a given {@link ArtifactoryDbProperties}
 *
 * @author Uriah Levy
 */
@Slf4j
public class CommonDataSourceFactory {

    private CommonDataSourceFactory() {
    }

    @Nonnull
    public static JFrogDataSource createDataSource(ArtifactoryDbProperties dbProps, boolean temporaryDbChannel) {
        String poolType = dbProps.getProperty(DbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE);
        DataSource dataSource = poolType.equalsIgnoreCase(TOMCAT_POOL_TYPE) ?
                new ArtifactoryTomcatDataSource(dbProps, temporaryDbChannel) :
                new ArtifactoryHikariDataSource(dbProps, temporaryDbChannel);
        dataSource = wrapWithMetricsDataSource(dbProps, dataSource);
        return JFrogDataSourceWrapper.newInstance(dataSource, dbProps);
    }

    public static JFrogDataSource createUniqueDataSourceByType(ArtifactoryDbProperties dbProps) {
        String poolType = dbProps.getProperty(DbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE);
        DataSource dataSource = poolType.equalsIgnoreCase(TOMCAT_POOL_TYPE) ?
                ArtifactoryTomcatDataSource.createUniqueIdDataSource(dbProps) :
                HikariJDBCDataSource.createUniqueIdDataSource(dbProps);
        dataSource = wrapWithMetricsDataSource(dbProps, dataSource);
        return JFrogDataSourceWrapper.newInstance(dataSource, dbProps);
    }

    public static JFrogDataSource createDifferentDatasourceForDbLock(ArtifactoryDbProperties dbProps,
            boolean temporaryChannel) {
        if (dbProps.getLockingDbSpecificType().isPresent()) {
            return createDatasourceForLockingOnExternalDb(dbProps, temporaryChannel);
        }
        JFrogDataSource dataSource = createDataSource(dbProps, temporaryChannel);
        if (!temporaryChannel) {
            capUniqueLockDataSourceMaxActiveConnections(
                    dataSource, getUniqueLockDataSourceMaxActiveConnections(dbProps), dbProps);
        }
        return dataSource;
    }

    private static JFrogDataSource createDatasourceForLockingOnExternalDb(ArtifactoryDbProperties dbProps,
            boolean temporaryDbChannel) {
        String poolType = dbProps.getProperty(DbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE);
        DataSource dataSource = poolType.equalsIgnoreCase(TOMCAT_POOL_TYPE) ?
                ArtifactoryTomcatDataSource.createDatasourceForLockingOnExternalDb(dbProps, temporaryDbChannel) :
                ArtifactoryHikariDataSource.createDatasourceForLockingOnExternalDb(dbProps, temporaryDbChannel);
        return JFrogDataSourceWrapper.newInstance(dataSource, dbProps);
    }

    private static void capUniqueLockDataSourceMaxActiveConnections(JFrogDataSource dataSource,
            int maxActiveConnections, ArtifactoryDbProperties dbProps) {
        String poolType = dbProps.getProperty(DbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE);
        if (poolType.equalsIgnoreCase(TOMCAT_POOL_TYPE)) {
            ArtifactoryTomcatDataSource tomcatDataSource = unwrapPool(dataSource, ArtifactoryTomcatDataSource.class);
            var poolProperties = tomcatDataSource.getPoolProperties();
            poolProperties.setMaxActive(maxActiveConnections);
            poolProperties.setMaxIdle(Math.min(poolProperties.getMaxIdle(), maxActiveConnections));
            poolProperties.setMinIdle(Math.min(poolProperties.getMinIdle(), maxActiveConnections));
        } else {
            HikariJDBCDataSource hikariDataSource = unwrapPool(dataSource, HikariJDBCDataSource.class);
            hikariDataSource.setMinimumIdle(Math.min(hikariDataSource.getMinimumIdle(), maxActiveConnections));
            hikariDataSource.setMaximumPoolSize(maxActiveConnections);
        }
        log.info("Capped lock datasource ({}) max connections to {}", poolType, maxActiveConnections);
    }

    private static <T> T unwrapPool(JFrogDataSource dataSource, Class<T> poolType) {
        try {
            if (dataSource.isWrapperFor(poolType)) {
                return dataSource.unwrap(poolType);
            }
            if (dataSource.isWrapperFor(MetricsDataSource.class)) {
                return dataSource.unwrap(MetricsDataSource.class).unwrap(poolType);
            }
        } catch (SQLException directUnwrapFailed) {
            try {
                return dataSource.unwrap(MetricsDataSource.class).unwrap(poolType);
            } catch (SQLException metricsUnwrapFailed) {
                metricsUnwrapFailed.addSuppressed(directUnwrapFailed);
                throw new IllegalStateException(
                        "Failed to unwrap " + poolType.getName() + " from " + dataSource.getClass(),
                        metricsUnwrapFailed);
            }
        }
        try {
            return dataSource.unwrap(poolType);
        } catch (SQLException directUnwrapFailed) {
            throw new IllegalStateException(
                    "Failed to unwrap " + poolType.getName() + " from " + dataSource.getClass(),
                    directUnwrapFailed);
        }
    }

    private static int getUniqueLockDataSourceMaxActiveConnections(ArtifactoryDbProperties dbProps) {
        int maxActiveConnections = dbProps.getMaxActiveConnections();
        if (maxActiveConnections <= 0) {
            throw new IllegalStateException("maxActiveConnections must be > 0, got: " + maxActiveConnections);
        }

        int lockDataSourceMaxActiveConnections = dbProps.getIntProperty(
                ArtifactoryDbProperties.LOCKS_MAX_ACTIVE_CONNECTIONS, maxActiveConnections);
        if (lockDataSourceMaxActiveConnections <= 0) {
            lockDataSourceMaxActiveConnections = maxActiveConnections;
        }
        // Lock datasource should never exceed either global DB max or the lock-specific cap.
        return Math.min(maxActiveConnections, lockDataSourceMaxActiveConnections);
    }
}
