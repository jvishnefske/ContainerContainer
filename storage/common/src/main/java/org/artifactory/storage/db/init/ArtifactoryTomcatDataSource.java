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

package org.artifactory.storage.db.init;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.jmx.ConnectionPool;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.concurrent.TimeUnit;

/**
 * A pooling data source based on tomcat-jdbc library.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryTomcatDataSource extends DataSource implements ArtifactoryDataSource {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryTomcatDataSource.class);

    private ArtifactoryTomcatDataSource() {
    }

    public ArtifactoryTomcatDataSource(ArtifactoryDbProperties s, boolean temporaryDbChannel) {
        // see org.apache.tomcat.jdbc.pool.CommonDataSourceFactory.parsePoolProperties()
        PoolProperties p = new PoolProperties();
        p.setUrl(s.getConnectionUrl());
        p.setDriverClassName(s.getDriverClass());
        p.setUsername(s.getUsername());
        p.setPassword(s.getPassword());
        // validation query for all kind of tests (connect, borrow etc.)
        p.setValidationQuery(s.getProperty("validationQuery", getDefaultValidationQuery(s)));
        p.setDefaultCatalog(s.getProperty("defaultCatalog", null));

        fillCommonProperties(s, p, temporaryDbChannel);

        setPoolProperties(p);
    }

    static DataSource createUniqueIdDataSource(ArtifactoryDbProperties s) {
        ArtifactoryTomcatDataSource tomcatDataSource = new ArtifactoryTomcatDataSource(s, false); //NOSONAR
        // see org.apache.tomcat.jdbc.pool.CommonDataSourceFactory.parsePoolProperties()
        PoolConfiguration p = tomcatDataSource.getPoolProperties();

        // auto commit is true for the unique id generator
        p.setDefaultAutoCommit(true);

        // only one connection is required for the id generator
        p.setInitialSize(0);
        p.setMinIdle(0);
        p.setMaxIdle(1);
        p.setMaxActive(1);
        // Make sure old idle connections are sweep and tested
        p.setTestWhileIdle(true);
        p.setTestOnBorrow(true);
        p.setRemoveAbandoned(true);
        p.setRemoveAbandonedTimeout((int) ConstantValues.locksTimeoutSecs.getLong() / 2);
        p.setSuspectTimeout((int) ConstantValues.locksTimeoutSecs.getLong() / 2);
        p.setLogAbandoned(true);
        p.setLogValidationErrors(true);

        // Timeout default to make sure new connection is created
        long timeoutInMillis = TimeUnit.SECONDS.toMillis(ConstantValues.locksTimeoutSecs.getLong());
        p.setMaxAge(timeoutInMillis);
        p.setMaxWait((int) timeoutInMillis);

        // Pool sweeper critical here since connection rarely used
        if (!p.isPoolSweeperEnabled()) {
            log.error("ID Generator pool connection should sweep idled connections");
        }

        p.setJmxEnabled(false);

        return tomcatDataSource;
    }

    static ArtifactoryTomcatDataSource createDatasourceForLockingOnExternalDb(ArtifactoryDbProperties props,
            boolean temporaryDbChannel) {
        ArtifactoryTomcatDataSource dataSource = new ArtifactoryTomcatDataSource();
        // see org.apache.tomcat.jdbc.pool.DataSourceFactory.parsePoolProperties()
        PoolProperties p = new PoolProperties();
        p.setUrl(props.getLockingDbConnectionUrl());
        p.setDriverClassName(props.getLockingDbDriverClass());
        p.setUsername(props.getLockingDbUsername());
        p.setPassword(props.getLockingDbPassword());
        // validation query for all kind of tests (connect, borrow etc.)
        p.setValidationQuery(
                props.getProperty("lockingdb.validationQuery", getDefaultValidationQueryForExternalDbLocking(props)));
        p.setDefaultCatalog(props.getProperty("lockingdb.defaultCatalog", null));
        fillCommonProperties(props, p, temporaryDbChannel);
        dataSource.setPoolProperties(p);
        return dataSource;
    }

    private static void fillCommonProperties(ArtifactoryDbProperties artifactoryDbProperties, PoolProperties p,
            boolean temporaryDbChannel) {
        p.setDefaultAutoCommit(artifactoryDbProperties.getBooleanProperty("defaultAutoCommit", true));
        p.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

        p.setInitialSize(artifactoryDbProperties.getIntProperty("initialSize", 1));
        p.setMaxAge(artifactoryDbProperties.getIntProperty("maxAge", 0));
        p.setMaxActive(artifactoryDbProperties.getMaxActiveConnections());
        p.setMaxWait(artifactoryDbProperties.getIntProperty("maxWait", (int) TimeUnit.SECONDS.toMillis(120)));
        p.setMaxIdle(artifactoryDbProperties.getMaxIdleConnections());
        p.setMinIdle(artifactoryDbProperties.getIntProperty("minIdle", 1));
        p.setMinEvictableIdleTimeMillis(
                artifactoryDbProperties.getIntProperty("minEvictableIdleTimeMillis", 300000));
        p.setTimeBetweenEvictionRunsMillis(
                artifactoryDbProperties.getIntProperty("timeBetweenEvictionRunsMillis", 30000));
        p.setInitSQL(artifactoryDbProperties.getProperty("initSQL", null));

        p.setValidationQueryTimeout(artifactoryDbProperties.getIntProperty("validationQueryTimeout", 30));
        p.setValidationInterval(artifactoryDbProperties.getLongProperty("validationInterval", 30000));
        p.setTestOnBorrow(artifactoryDbProperties.getBooleanProperty("testOnBorrow", true));
        p.setTestWhileIdle(artifactoryDbProperties.getBooleanProperty("testWhileIdle", false));
        p.setTestOnReturn(artifactoryDbProperties.getBooleanProperty("testOnReturn", false));
        p.setTestOnConnect(artifactoryDbProperties.getBooleanProperty("testOnConnect", false));

        p.setRemoveAbandoned(artifactoryDbProperties.getBooleanProperty("removeAbandoned", true));
        p.setRemoveAbandonedTimeout(
                artifactoryDbProperties.getIntProperty("removeAbandonedTimeout", DbUtils.DEFAULT_TIMEOUT_SEC + 10));
        p.setSuspectTimeout(artifactoryDbProperties.getIntProperty("suspectTimeout", 600));
        p.setLogAbandoned(artifactoryDbProperties.getBooleanProperty("logAbandoned", false));
        p.setLogValidationErrors(artifactoryDbProperties.getBooleanProperty("logValidationErrors", false));

        p.setJmxEnabled(artifactoryDbProperties.getBooleanProperty("jmxEnabled", true));

        // only applicable if auto commit is false. has high performance penalty and only protects bugs in the code
        p.setRollbackOnReturn(artifactoryDbProperties.getBooleanProperty("rollbackOnReturn", false));
        p.setCommitOnReturn(artifactoryDbProperties.getBooleanProperty("commitOnReturn", false));

        p.setIgnoreExceptionOnPreLoad(artifactoryDbProperties.getBooleanProperty("ignoreExceptionOnPreLoad", false));

        p.setJdbcInterceptors(artifactoryDbProperties.getProperty("jdbcInterceptors", "ConnectionState"));
        // If it's a temporary init-phase datasource, allow up to a single concurrent connection
        if (temporaryDbChannel) {
            p.setMaxActive(1);
            p.setMaxIdle(1);
            p.setMinIdle(0);
            p.setMaxWait(3000);
        }
    }

    private static String getDefaultValidationQuery(ArtifactoryDbProperties s) {
        return getDefaultValidationQuery(s.getDbType());
    }

    private static String getDefaultValidationQueryForExternalDbLocking(ArtifactoryDbProperties s) {
        return s.getLockingDbSpecificType().map(ArtifactoryTomcatDataSource::getDefaultValidationQuery)
                .orElseThrow(() -> new IllegalStateException("Another DB was configured for locking but no db type " +
                        "parameter found"));
    }

    private static String getDefaultValidationQuery(DbType dbType) {
        switch (dbType) {
            case DERBY:
                return "values(1)";
            case MYSQL:
                // special MySQL lightweight ping query (not supported by the MariaDB connector to-date!)
                return "/* ping */";
            case ORACLE:
                return "SELECT 1 FROM DUAL";
            default:
                return "SELECT 1";
        }
    }

    @Override
    public void registerMBeans(MBeanRegistrationServiceImpl mbeansService) {
        if (isJmxEnabled()) {
            ConnectionPool jmxPool = getPool().getJmxPool();
            mbeansService.register(jmxPool, "Storage", "Connection Pool");
        }
    }

    @Override
    public int getActiveConnectionsCount() {
        return super.getActive();
    }

    @Override
    public int getIdleConnectionsCount() {
        return super.getIdle();
    }

    @Override
    public void reset() {
        int currentIdleTime = this.getPoolProperties().getMinEvictableIdleTimeMillis();
        this.getPoolProperties().setMinEvictableIdleTimeMillis(0);
        this.purge();
        this.getPoolProperties().setMinEvictableIdleTimeMillis(currentIdleTime);
    }

    @Override
    public void close() {
        close(true);    // close all connections, including active ones
    }
}
