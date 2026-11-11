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

import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.init.ArtifactoryTomcatDataSource;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.DataSourceHealthException;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the connection pooling done with {@link ArtifactoryTomcatDataSource}.
 *
 * @author Yossi Shaul
 */
@Test
public class ArtifactoryTomcatDataSourceTest extends DbBaseTest {

    @Autowired
    private JFrogDataSource dataSource;

    private boolean isTomcatDS;

    @BeforeClass
    public void setup() throws SQLException {
        isTomcatDS = dataSource.isWrapperFor(ArtifactoryTomcatDataSource.class);
    }

    public void testHealthy() {
        if (isTomcatDS) {
            dataSource.health();
        }
    }

    public void testUnHealthy() throws Exception {
        if (isTomcatDS) {
            //Spy the unwrapped instance, since mockito doesn't support Proxy spy.
            JFrogDataSource ds = spy(dataSource.unwrap(JFrogDataSource.class));
            doThrow(SQLException.class).when(ds).getConnection();
            try {
                ds.health();
                fail();
            } catch (DataSourceHealthException e) {
            }
        }
    }

    public void instanceType() throws SQLException {
        assertTrue(dataSource.isWrapperFor(ArtifactoryDataSource.class),
                "Unexpected datasource type: " + dataSource.getClass());
    }

    public void verifyDefaultParams() throws SQLException {
        if (isTomcatDS) {
            ArtifactoryTomcatDataSource ds = dataSource.unwrap(ArtifactoryTomcatDataSource.class);
            assertEquals(ds.getDefaultTransactionIsolation(), Connection.TRANSACTION_READ_COMMITTED);
            assertTrue(ds.isDefaultAutoCommit());
            assertEquals(ds.getRemoveAbandonedTimeout(), DbUtils.DEFAULT_TIMEOUT_SEC + 10);
            assertTrue(ds.isRemoveAbandoned());
        }
    }

    public void verifyValidationQuery() throws SQLException {
        if (isTomcatDS) {
            ArtifactoryTomcatDataSource ds = dataSource.unwrap(ArtifactoryTomcatDataSource.class);
            String validationQuery = ds.getValidationQuery();
            assertNotNull(validationQuery, "Validation query shouldn't be null");

            try (Connection con = dataSource.getConnection(); Statement stmt = con
                    .createStatement(); ResultSet rs = stmt.executeQuery(validationQuery)) {
                if (!rs.next()) {
                    fail("No result returned from the validation query");
                }
            }
        }
    }
}
