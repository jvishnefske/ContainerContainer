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

package org.artifactory.storage.db.ds.itest;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.jfrog.sysconf.SysLayout;
import jakarta.annotation.Nonnull;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.init.ArtifactoryHikariDataSource;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.test.TestUtils;
import org.artifactory.util.Files;
import org.jfrog.common.ResourceUtils;
import org.jfrog.storage.DataSourceHealthException;
import org.jfrog.storage.HikariJDBCDataSource;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.metric.MetricsDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.jfrog.config.home.JFrogHome.SYSTEM_YAML_FILENAME;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the connection pooling done with {@link ArtifactoryHikariDataSource}.
 *
 * @author Yossi Shaul
 */
@Test
public class ArtifactoryHikariDataSourceTest extends DbBaseTest {

    @Autowired
    private JFrogDataSource dataSource;

    private boolean isHikariDS;

    @BeforeClass
    public void setup() throws SQLException {
        isHikariDS = dataSource.isWrapperFor(ArtifactoryHikariDataSource.class);
    }

    public void testHealthy() {
        if (isHikariDS) {
            dataSource.health();
        }
    }

    public void testUnHealthy() throws Exception {
        if (isHikariDS) {
            //Spy the unwrapped instance, since mockito doesn't support Proxy spy.
            HikariJDBCDataSource ds;
            if (dataSource.isWrapperFor(MetricsDataSource.class)) {
                ds = spy(dataSource.unwrap(MetricsDataSource.class).unwrap(HikariJDBCDataSource.class));
            } else {
                ds = spy(dataSource.unwrap(HikariJDBCDataSource.class));
            }
            //spy the healthCheckRegistry
            HealthCheckRegistry healthCheckRegistry = spy(ds.getHealthCheckRegistry());
            when(ds.getHealthCheckRegistry()).thenReturn(healthCheckRegistry);
            doReturn(new TreeMap<>(Collections.singletonMap("HikariCP Main.pool.ConnectivityCheck",
                    HealthCheck.Result.unhealthy("no connection")))).when(healthCheckRegistry).runHealthChecks();
            try {
                ds.health();
                fail();
            } catch (DataSourceHealthException e) {
            }
        }
    }

    public void testConnectivityCheckTimeoutMsPropertyDefaultValue() throws IOException {
        if (isHikariDS) {
            HikariJDBCDataSource hds = createTestDataSource();
            assertEquals(hds.getHealthCheckProperties().getProperty("connectivityCheckTimeoutMs"),
                    String.valueOf(hds.getConnectionTimeout()));
        }
    }


    public void testConnectivityCheckTimeoutMsPropertyCustomValue() throws IOException {
        if (isHikariDS) {
            HikariJDBCDataSource hds =
                    createTestDataSource(Optional.of(Collections.singletonMap("connectivityCheckTimeoutMs", "30000")));
            assertEquals(hds.getHealthCheckProperties().getProperty("connectivityCheckTimeoutMs"), "30000");
        }
    }

    public void instanceType() throws SQLException {
        assertTrue(dataSource.isWrapperFor(ArtifactoryDataSource.class),
                "Unexpected datasource type: " + dataSource.getClass());
    }

    public void verifyDefaultParams() throws SQLException {
        if (isHikariDS) {
            ArtifactoryHikariDataSource ds;
            if (dataSource.isWrapperFor(MetricsDataSource.class)) {
                ds = dataSource.unwrap(MetricsDataSource.class).unwrap(ArtifactoryHikariDataSource.class);
            } else {
                ds = dataSource.unwrap(ArtifactoryHikariDataSource.class);
            }
            assertEquals(ds.getTransactionIsolation(), "TRANSACTION_READ_COMMITTED");
            assertTrue(ds.isAutoCommit());
        }
    }

    public void verifyActiveConnections() throws SQLException {
        assertEquals(getDs().getActiveConnectionsCount(), 0);
        //assertEquals(getDs().getIdleConnectionsCount(), dbProps.get);
        try (Connection conn = getDs().getConnection()) {
            assertNotNull(conn);
            assertEquals(getDs().getActiveConnectionsCount(), 1);
        }
        assertEquals(getDs().getActiveConnectionsCount(), 0);
    }

    public void lockingDbDataSource() throws IOException {
        HikariJDBCDataSource hds = createTestDataSource();
        // check non locking db parameters
        assertEquals(hds.getMaximumPoolSize(), 15);

        // check locking db overrides
        assertEquals(hds.getDriverClassName(), "org.postgresql.Driver");
        assertEquals(hds.getJdbcUrl(), "jdbc:postgresql://localhost:5432/lockdb");
        assertEquals(hds.getUsername(), "lockdbuser");
        assertEquals(hds.getPassword(), "lockdbpass");
        assertEquals(hds.getCatalog(), "lockingcatalog");
    }

    public void nonStandardPoolParams() throws IOException {
        HikariJDBCDataSource hds = createTestDataSource();

        assertEquals(hds.getConnectionTimeout(), 888);
        assertEquals(hds.getLeakDetectionThreshold(), 9999);
        assertTrue(hds.isIsolateInternalQueries());
    }

    @Nonnull
    private HikariJDBCDataSource createTestDataSource() throws IOException {
        return createTestDataSource(Optional.empty());
    }

    @Nonnull
    private HikariJDBCDataSource createTestDataSource(Optional<Map<String, String>> customProperties)
            throws IOException {
        File home = TestUtils.createTempDir(getClass());
        SysLayout sysLayout = new SysLayout(home, "test");
        assertTrue(sysLayout.getProductEtc().mkdirs());
        Files.writeToFileAndClose(ResourceUtils.getResource("/db/lockingdb.yaml"),
                new File(sysLayout.getProductEtc() + "/" + SYSTEM_YAML_FILENAME));

        var dbProps = new ArtifactoryDbProperties(new ArtifactoryHome(home.getAbsolutePath()));

        if (customProperties.isPresent()) {
            dbProps = spy(dbProps);
            for (String customPropertyKey : customProperties.get().keySet()) {
                when(dbProps.getProperty(customPropertyKey)).thenReturn(customProperties.get().get(customPropertyKey));
            }
        }

        DataSource ds = ArtifactoryHikariDataSource.createDatasourceForLockingOnExternalDb(dbProps, false);
        assertEquals(ds.getClass(), HikariJDBCDataSource.class);
        return (HikariJDBCDataSource) ds;
    }

    private ArtifactoryDataSource getDs() throws SQLException {
        if (dataSource.isWrapperFor(MetricsDataSource.class)) {
            return dataSource.unwrap(MetricsDataSource.class).unwrap(ArtifactoryDataSource.class);
        }
        return dataSource.unwrap(ArtifactoryDataSource.class);
    }
}
