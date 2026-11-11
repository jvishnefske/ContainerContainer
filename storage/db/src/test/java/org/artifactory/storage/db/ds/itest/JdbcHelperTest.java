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

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.test.TestUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.metric.MetricsDataSource;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.wrapper.ResultSetProxy;
import org.jfrog.storage.wrapper.ResultSetWrapper;
import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executor;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ArtifactoryJdbcHelper}.
 *
 * @author Yossi Shaul
 */
@Test
public class JdbcHelperTest extends DbBaseTest {

    public void testTxIsolationDefault() throws SQLException {
        ResultSet rs = jdbcHelper.executeSelect("select count(1) from nodes");
        Connection con = getConnection(rs);
        assertEquals(con.getTransactionIsolation(), Connection.TRANSACTION_READ_COMMITTED);
        DbUtils.close(rs);
    }

    public void testTxIsolationResetBackRoReadCommitted() throws SQLException {
        if (dbService.getDatabaseType().equals(DbType.ORACLE)) {
            return; // dirty reads are not supported by oracle driver
        }
        ResultSet rs = null;
        try {
            QueryControls queryControls = QueryControls.builder().allowDirtyReads(true).build();
            rs = jdbcHelper.executeSelect(queryControls, "select count(1) from nodes", new Object[] {});
            Connection con = getConnection(rs);
            assertEquals(con.getTransactionIsolation(), Connection.TRANSACTION_READ_UNCOMMITTED);
            // return it back to the pool and expect to get back the default TX isolation
            DbUtils.close(rs);
            rs = jdbcHelper.executeSelect("select count(1) from nodes");
            assertEquals(getConnection(rs).getTransactionIsolation(), Connection.TRANSACTION_READ_COMMITTED);
        } finally {
            DbUtils.close(rs);
        }
    }

    public void testGetConnection() throws SQLException {
        QueryControls queryControls = QueryControls.builder().networkTimeout(20).build();
        JFrogDataSource dataSource = jdbcHelper.getDataSource();
        Executor executor = dataSource.getNetworkTimeoutExecutor();
        try (Connection connection = DbUtils.getConnection(queryControls, dataSource, executor)) {
            DbType dbType = dbProperties.getDbType();
            if (dbType == DbType.DERBY ||
                    (dbType == DbType.ORACLE && connection.getMetaData().getDriverMajorVersion() <= 11)) {
                assertEquals(connection.getNetworkTimeout(), 0);
            } else {
                assertEquals(connection.getNetworkTimeout(), 20 * 1000);
            }
        }
    }

    public void testGetDataSource() throws SQLException {
        JFrogDataSource dataSource = jdbcHelper.getDataSource();
        assertNotNull(dataSource);
        assertNotNull(dataSource.getNetworkTimeoutExecutor());
        assertTrue(jdbcHelper.getDataSource().isWrapperFor(JFrogDataSource.class));
        if (jdbcHelper.getDataSource().isWrapperFor(MetricsDataSource.class)) {
            assertNotNull(jdbcHelper.getDataSource().unwrap(MetricsDataSource.class).unwrap(JFrogDataSource.class));
        } else {
            assertNotNull(jdbcHelper.getDataSource().unwrap(JFrogDataSource.class));
        }
    }

    private Connection getConnection(ResultSet rs) {
        if (rs instanceof ResultSetProxy proxy) {
            return proxy.getConnection();
        }
        ResultSetWrapper rsw = (ResultSetWrapper) Proxy.getInvocationHandler(rs);
        return TestUtils.getField(rsw, "con", Connection.class);
    }

}
