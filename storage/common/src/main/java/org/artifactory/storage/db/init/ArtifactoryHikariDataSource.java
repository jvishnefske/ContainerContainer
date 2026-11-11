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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.HikariJDBCDataSource;

import javax.sql.DataSource;

/**
 * A pooling data source based on Hikari pool library.
 * See https://github.com/brettwooldridge/HikariCP for configuration details.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryHikariDataSource extends HikariJDBCDataSource implements ArtifactoryDataSource {
    private ArtifactoryDbProperties p;

    ArtifactoryHikariDataSource(ArtifactoryDbProperties p, boolean temporaryDbChannel) {
        this(p);
        if (temporaryDbChannel) {
            setMaximumPoolSize(1);
            setMinimumIdle(0);
            // hikari combines borrow time with the actual connection timeout setting, so we're quiet generous here
            setConnectionTimeout(20_000);
        }
    }

    ArtifactoryHikariDataSource(ArtifactoryDbProperties p) {
        super(p);
        this.p = p;
    }

    public static DataSource createDatasourceForLockingOnExternalDb(ArtifactoryDbProperties props,
            boolean temporaryDbChannel) {
        HikariJDBCDataSource ds = new HikariJDBCDataSource(props);
        ds.setPoolName("HikariCP Locking External");

        ds.setJdbcUrl(props.getLockingDbConnectionUrl());
        ds.setDriverClassName(props.getLockingDbDriverClass());
        ds.setUsername(props.getLockingDbUsername());
        ds.setPassword(props.getLockingDbPassword());

        // validation query for all kind of tests (connect, borrow etc.)
        ds.setCatalog(props.getProperty(DbProperties.Key.LOCKING_DB_CATALOG, null));

        // If it's a temporary init-phase datasource, allow up to a single concurrent connection
        if (temporaryDbChannel) {
            ds.setMaximumPoolSize(1);
            ds.setMinimumIdle(0);
            ds.setConnectionTimeout(3000);
        }
        return ds;
    }

    @Override
    public void registerMBeans(MBeanRegistrationServiceImpl mbeansService) {
        if (p.getBooleanProperty("jmxEnabled", true)) {
            mbeansService.register(getHikariPoolMXBean(), "Storage", "Connection Pool");
            mbeansService.register(getHikariConfigMXBean(), "Storage", "Connection Pool Config");
            if (p.getBooleanProperty("metricsEnabled", false)) {
                setupMetrics(mbeansService);
            }
        }
    }

    private void setupMetrics(MBeanRegistrationServiceImpl mbeansService) {
        MetricRegistry metricRegistry = mbeansService.getMetricRegistry();
        setMetricRegistry(metricRegistry);
        JmxReporter jmxReporter = JmxReporter.forRegistry(metricRegistry)
                .createsObjectNamesWith(
                        (type, domain, name) -> mbeansService.createObjectName("Metrics, name=JDBC", name))
                .filter((name, metric) -> name.contains("HikariCP"))
                .build();
        jmxReporter.start();
    }

    @Override
    public int getActiveConnectionsCount() {
        return getHikariPoolMXBean().getActiveConnections();
    }

    @Override
    public int getIdleConnectionsCount() {
        return getHikariPoolMXBean().getIdleConnections();
    }

    @Override
    public int getMaxActive() {
        return getHikariConfigMXBean().getMaximumPoolSize();
    }

    @Override
    public int getMaxIdle() {
        // unsupported for HikariDataSource
        return -1;
    }

    @Override
    public int getMaxWait() {
        return (int) getConnectionTimeout();
    }

    @Override
    public int getMinIdle() {
        return getHikariConfigMXBean().getMinimumIdle();
    }

    @Override
    public String getUrl() {
        return p.getConnectionUrl();
    }

    @Override
    public void reset() {
        this.getHikariPoolMXBean().softEvictConnections();
    }
}