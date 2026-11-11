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

package org.artifactory.storage.db.ha.itest.dao;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.common.ConstantValues;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.init.ArtifactoryTomcatDataSource;
import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.servers.dao.ArtifactoryServersDao;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.fest.assertions.Assertions;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.metric.MetricsDataSource;
import org.jfrog.storage.util.DbUtils;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.artifactory.storage.db.util.BaseDao.enumToString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the {@link org.artifactory.storage.db.servers.dao.ArtifactoryServersDao}.
 *
 * @author Yossi Shaul
 */
@Ignore
@Test
public class ArtifactoryServersDaoTest extends DbBaseTest {

    private static final String LICENSE_HASH = DigestUtils.shaHex("server") + "0";
    private static final String SERVER_ID = DigestUtils.shaHex("id") + "0";
    private static final String SERVER_ID_UPDATE = DigestUtils.shaHex("toupdate");
    private JFrogDataSource dataSource;
    private ArtifactoryServersDao dao;

    private final ArtifactoryServer sampleServer = new ArtifactoryServer(SERVER_ID, 1000000L,
            "127.0.0.1", 5700, ArtifactoryServerState.RUNNING, ArtifactoryServerRole.STANDALONE,
            System.currentTimeMillis(),
            "3.0.1-test", 2, 3L, ArtifactoryRunningMode.OSS, LICENSE_HASH);

    @BeforeClass
    public void createServer() throws SQLException {
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.haHeartbeatNetworkTimeout, "1");
        dao = new ArtifactoryServersDao(jdbcHelper);
        dao.createArtifactoryServer(sampleServer);
        if (dbProperties.getDbType() != DbType.DERBY) {
            dataSource = CommonDataSourceFactory.createDataSource(dbProperties, false);
            if (dbProperties.getPoolType().equalsIgnoreCase(DbProperties.TOMCAT_POOL_TYPE)) {
                logger.info("Changing the pool configuration for the test");
                // Tomcat removes the connection only if validation fails. Validation occurs every 30 seconds by
                //default.
                // For the tests changing it to 1 seconds interval.
                ArtifactoryTomcatDataSource currentDataSource =
                        jdbcHelper.getDataSource().unwrap(ArtifactoryTomcatDataSource.class);
                currentDataSource.setValidationInterval(1000);
                dao = new ArtifactoryServersDao(jdbcHelper);
            }
        }
    }

    @AfterClass
    public void reset() {
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.haHeartbeatNetworkTimeout, "10");
    }

    @Test
    public void hasServer() throws SQLException {
        assertTrue(dao.hasArtifactoryServer(SERVER_ID));
    }

    public void hasServerNonExistingServer() throws SQLException {
        assertFalse(dao.hasArtifactoryServer(DigestUtils.shaHex("nosuchserver")));
    }

    public void loadServer() throws SQLException {
        ArtifactoryServer insertedServer = new ArtifactoryServer(DigestUtils.shaHex("create'n'load"), 1000000L,
                "152.45.32.56", 5700, ArtifactoryServerState.RUNNING, ArtifactoryServerRole.STANDALONE,
                System.currentTimeMillis(), "6.1", 2, 3L, ArtifactoryRunningMode.OSS, LICENSE_HASH);

        dao.createArtifactoryServer(insertedServer);

        ArtifactoryServer loadedServer = dao.getArtifactoryServer(DigestUtils.shaHex("create'n'load"));

        assertTrue(EqualsBuilder.reflectionEquals(insertedServer, loadedServer), "Orig and copy differ");
    }

    @Test
    public void loadAllServers() throws SQLException {
        Assertions.assertThat(dao.getAllArtifactoryServers()).isNotEmpty().contains(sampleServer);
    }

    @Test(expectedExceptions = SQLException.class)
    public void loadServerWithVeryLongServerId() throws SQLException {
        ArtifactoryServer longNameServer = new ArtifactoryServer(
                "Very Long name - longer then 128 characters........................................................." +
                        "................................",
                1000000L,
                "152.45.32.56", 5700, ArtifactoryServerState.RUNNING, ArtifactoryServerRole.STANDALONE,
                System.currentTimeMillis(), "6.1", 2, 3L, ArtifactoryRunningMode.OSS, LICENSE_HASH);

        dao.createArtifactoryServer(longNameServer);
    }

    @Test
    public void loadServerWithLongServerId() throws SQLException {
        ArtifactoryServer longNameServer = new ArtifactoryServer(
                "Long name - longer then 41 characters , shorter then 128 characters",
                1000000L,
                "152.45.32.56", 5700, ArtifactoryServerState.RUNNING, ArtifactoryServerRole.STANDALONE,
                System.currentTimeMillis(), "6.1", 2, 3L, ArtifactoryRunningMode.OSS, LICENSE_HASH);

        dao.createArtifactoryServer(longNameServer);

        Assertions.assertThat(dao.getAllArtifactoryServers()).contains(longNameServer);

    }

    @Test
    public void updateServer() throws SQLException {
        ArtifactoryServer server = new ArtifactoryServer(SERVER_ID_UPDATE, 1000000L,
                "152.45.32.56", 5700, ArtifactoryServerState.RUNNING, ArtifactoryServerRole.MEMBER,
                System.currentTimeMillis(), "6.1", 2, 3L, ArtifactoryRunningMode.OSS, LICENSE_HASH);
        dao.createArtifactoryServer(server);

        ArtifactoryServer updatedServer = new ArtifactoryServer(SERVER_ID_UPDATE, 11000000L,
                "152.45.32.57", 5700, ArtifactoryServerState.STOPPED, ArtifactoryServerRole.STANDALONE,
                System.currentTimeMillis(), "6.2", 3, 2L, ArtifactoryRunningMode.OSS, LICENSE_HASH);

        int updateCount = dao.updateArtifactoryServer(updatedServer);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(server.getServerId());
        assertTrue(EqualsBuilder.reflectionEquals(updatedServer, serverFromDb));
    }

    @Test(dependsOnMethods = "updateServer")
    public void updateServerWithTimeout() throws SQLException, NoSuchMethodException, IllegalAccessException {
        if (shouldSkipTest()) {
            return;
        }

        ArtifactoryServer server = new ArtifactoryServer(SERVER_ID_UPDATE, 11000000L,
                "152.45.132.57", 5700, ArtifactoryServerState.STOPPED, ArtifactoryServerRole.STANDALONE,
                System.currentTimeMillis(), "6.20", 3, 2L, ArtifactoryRunningMode.OSS, LICENSE_HASH);

        ArtifactoryServer updatedServer = new ArtifactoryServer(SERVER_ID_UPDATE, 11000000L,
                "152.45.132.57", 5700, ArtifactoryServerState.STOPPED, ArtifactoryServerRole.STANDALONE,
                System.currentTimeMillis(), "6.20", 3, 2L, ArtifactoryRunningMode.PRO, LICENSE_HASH);
        String query = String.format(
                "UPDATE artifactory_servers " +
                        "SET start_time = %s, context_url = '%s', " +
                        "server_state = '%s', server_role = '%s', " +
                        "last_heartbeat = %s, artifactory_version = '%s', " +
                        "artifactory_revision = %s, artifactory_release = %s, " +
                        "artifactory_running_mode = '%s', license_hash='%s' WHERE server_id = '%s'",
                server.getStartTime(), server.getContextUrl(),
                enumToString(server.getServerState()), enumToString(server.getServerRole()),
                server.getLastHeartbeat(), server.getArtifactoryVersion(),
                server.getArtifactoryRevision(), server.getArtifactoryRelease(),
                enumToString(server.getArtifactoryRunningMode()), server.getLicenseKeyHash(), server.getServerId());

        executeQueryWithTimeout(query,
                dao.getClass()
                        .getMethod("updateArtifactoryServer", updatedServer.getClass()), updatedServer);

        // update the server
        int updateCount = dao.updateArtifactoryServer(updatedServer);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID_UPDATE);
        assertTrue(EqualsBuilder.reflectionEquals(updatedServer, serverFromDb));
    }

    @Test
    public void updateArtifactoryServerHeartbeatTest() throws SQLException {
        long lastHeartbeat = System.currentTimeMillis();

        int updateCount = dao.updateArtifactoryServerHeartbeat(SERVER_ID, lastHeartbeat + 5, LICENSE_HASH);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID);
        assert serverFromDb != null;
        assertEquals(serverFromDb.getLastHeartbeat(), lastHeartbeat + 5);
    }

    @Test
    public void updateArtifactoryServerHeartbeatWithTimeout()
            throws SQLException, NoSuchMethodException, IllegalAccessException {
        if (shouldSkipTest()) { return; }
        assertTrue(dao.hasArtifactoryServer(SERVER_ID));

        // Check that the heartbeat fails:
        long lastHeartbeat = System.currentTimeMillis();
        String query = String.format(
                "UPDATE artifactory_servers SET last_heartbeat = %s, license_hash = '%s' WHERE server_id = '%s'",
                lastHeartbeat + 10, LICENSE_HASH, SERVER_ID);

        executeQueryWithTimeout(query,
                dao.getClass()
                        .getMethod("updateArtifactoryServerHeartbeat", SERVER_ID.getClass(), long.class,
                                LICENSE_HASH.getClass()),
                SERVER_ID, lastHeartbeat + 15, LICENSE_HASH);

        int updateCount = dao.updateArtifactoryServerHeartbeat(SERVER_ID, lastHeartbeat + 5, LICENSE_HASH);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID);
        assertNotNull(serverFromDb);
        assertEquals(serverFromDb.getLastHeartbeat(), lastHeartbeat + 5);
    }

    @Test
    public void updateArtifactoryServerState() throws SQLException {
        long lastHeartbeat = System.currentTimeMillis();

        int updateCount = dao.updateArtifactoryServerState(SERVER_ID, ArtifactoryServerState.STOPPED, lastHeartbeat);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID);
        assert serverFromDb != null;
        assertEquals(serverFromDb.getLastHeartbeat(), lastHeartbeat);
        assertEquals(serverFromDb.getServerState(), ArtifactoryServerState.STOPPED);
    }

    @Test
    public void updateArtifactoryServerStateWithTimeout()
            throws SQLException, NoSuchMethodException, IllegalAccessException {
        if (shouldSkipTest()) { return; }
        assertTrue(dao.hasArtifactoryServer(SERVER_ID));

        long lastHeartbeat = System.currentTimeMillis();
        String query = String.format(
                "UPDATE artifactory_servers SET server_state = '%s', last_heartbeat = %s WHERE server_id = '%s'",
                ArtifactoryServerState.CONVERTING, lastHeartbeat + 10, SERVER_ID);

        executeQueryWithTimeout(query,
                dao.getClass()
                        .getMethod("updateArtifactoryServerState", SERVER_ID.getClass(), ArtifactoryServerState.class,
                                long.class),
                SERVER_ID, ArtifactoryServerState.RUNNING, lastHeartbeat + 15);

        int updateCount =
                dao.updateArtifactoryServerState(SERVER_ID, ArtifactoryServerState.RUNNING, lastHeartbeat + 15);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID);
        assertNotNull(serverFromDb);
        assertEquals(serverFromDb.getLastHeartbeat(), lastHeartbeat + 15);
        assertEquals(serverFromDb.getServerState(), ArtifactoryServerState.RUNNING);
    }

    @Test
    public void updateArtifactoryServerRole() throws SQLException {
        int updateCount = dao.updateArtifactoryServerRole(SERVER_ID, ArtifactoryServerRole.MEMBER);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID);
        assert serverFromDb != null;
        assertEquals(serverFromDb.getServerRole(), ArtifactoryServerRole.MEMBER);
    }

    @Test
    public void updateArtifactoryServerRoleWithTimeout()
            throws SQLException, NoSuchMethodException, IllegalAccessException {
        if (shouldSkipTest()) { return; }
        assertTrue(dao.hasArtifactoryServer(SERVER_ID));

        String query = String.format(
                "UPDATE artifactory_servers SET server_role = '%s' WHERE server_id = '%s'",
                enumToString(ArtifactoryServerRole.STANDALONE), SERVER_ID);
        executeQueryWithTimeout(query,
                dao.getClass()
                        .getMethod("updateArtifactoryServerRole", SERVER_ID.getClass(), ArtifactoryServerRole.class),
                SERVER_ID, ArtifactoryServerRole.COPY);

        int updateCount = dao.updateArtifactoryServerRole(SERVER_ID, ArtifactoryServerRole.COPY);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID);
        assertNotNull(serverFromDb);
        assertEquals(serverFromDb.getServerState(), ArtifactoryServerState.RUNNING);
    }

    @Test
    public void updateArtifactoryServerMembershipPort() throws SQLException {
        assertTrue(dao.hasArtifactoryServer(SERVER_ID));

        int updateCount = dao.updateArtifactoryMembershipPort(SERVER_ID, 9999);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID);
        assert serverFromDb != null;
        assertEquals(serverFromDb.getMembershipPort(), 9999);
    }

    @Test
    public void updateArtifactoryServerMembershipPortWithTimeout()
            throws SQLException, NoSuchMethodException, IllegalAccessException {
        if (shouldSkipTest()) { return; }
        assertTrue(dao.hasArtifactoryServer(SERVER_ID));

        String query = String.format(
                "UPDATE artifactory_servers SET membership_port = %s WHERE server_id = '%s'",
                6060, SERVER_ID);

        executeQueryWithTimeout(query,
                dao.getClass().getMethod("updateArtifactoryMembershipPort", SERVER_ID.getClass(), int.class),
                SERVER_ID, 7777);
        int updateCount = dao.updateArtifactoryMembershipPort(SERVER_ID, 8888);
        assertEquals(updateCount, 1);

        ArtifactoryServer serverFromDb = dao.getArtifactoryServer(SERVER_ID);
        assertNotNull(serverFromDb);
        assertEquals(serverFromDb.getMembershipPort(), 8888);
    }

    private void keepTransactionOpen(String query, Connection con) throws SQLException {
        con.setAutoCommit(false);
        Statement stmt = con.createStatement();
        stmt.executeUpdate(query);
    }

    private void executeQueryWithTimeout(String query,
            Method method, Object... params) throws IllegalAccessException, SQLException {
        Connection con = DataSourceUtils.doGetConnection(dataSource);
        keepTransactionOpen(query, con);
        try {
            method.invoke(dao, params);
            fail(String.format("Expected method %s to fail", method.getName()));
        } catch (InvocationTargetException e) {
            assertTrue(e.getTargetException() instanceof SQLException);
        } finally {
            con.commit();
            DbUtils.close(con);
        }
    }

    // Derby not supporting the get network timeout and set network timeout
    private boolean shouldSkipTest() throws SQLException {
        if (dbProperties.getDbType() == DbType.DERBY) {
            logger.info("Derby not supporting the network timeout");
            return true;
        }

        if (dbProperties.getDbType() == DbType.ORACLE) {
            // Skip the test if version of Oracle driver is 11 or less since the get and set is not supported for this.
            QueryControls queryControls = QueryControls.builder().build();
            try (Connection connection = DbUtils
                    .getConnection(queryControls, dataSource, dataSource.getNetworkTimeoutExecutor())) {
                if (connection.getMetaData().getDriverMajorVersion() <= 11) {
                    return true;
                }
            }
        }
        return false;
    }

    @AfterClass
    public void tearDownDataSource() throws IOException, SQLException {
        if (dbProperties.getDbType() != DbType.DERBY) {
            ArtifactoryDataSource ds;
            if (dataSource.isWrapperFor(MetricsDataSource.class)) {
                ds = dataSource.unwrap(MetricsDataSource.class).unwrap(ArtifactoryDataSource.class);
            } else {
                ds = dataSource.unwrap(ArtifactoryDataSource.class);
            }
            assertEquals(ds.getActiveConnectionsCount(), 0, "Found " + ds.getActiveConnectionsCount() +
                    " active connections after test ended");
            dataSource.close();
            if (dbProperties.getPoolType().equalsIgnoreCase(DbProperties.TOMCAT_POOL_TYPE)) {
                ArtifactoryTomcatDataSource currentDataSource =
                        jdbcHelper.getDataSource().unwrap(ArtifactoryTomcatDataSource.class);
                currentDataSource.setValidationInterval(30000);
            }
        }
    }
}
