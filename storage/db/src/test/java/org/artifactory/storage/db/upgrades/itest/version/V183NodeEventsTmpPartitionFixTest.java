package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v182;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v183;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Tomer Slutsky
 */
public class V183NodeEventsTmpPartitionFixTest extends UpgradeBaseTest {

    @Test
    public void testExtensionExists() throws SQLException {
        testOnlyWithPostgresql();
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL, v183)) {
            v182.converter().convert(context);
            if (isPostgresVersion10OrAbove()) {
                assertTrue(tableExists("node_events_tmp_p0_5"));
                assertTrue(primaryKeyExists("node_events_tmp_p0_5", "node_events_tmp_partition_0_5_pk"));
            } else {
                assertFalse(tableExists("node_events_tmp_p0_5"));
            }
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v182;
    }

    private boolean isPostgresVersion10OrAbove() throws SQLException {
        try (Connection con = jdbcHelper.getDataSource().getConnection(); PreparedStatement stmt = con.prepareStatement(
                "SHOW server_version_num")) {
            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                long version = resultSet.getLong(1);
                return version >= 100000L;
            }
            return false;
        }
    }
}