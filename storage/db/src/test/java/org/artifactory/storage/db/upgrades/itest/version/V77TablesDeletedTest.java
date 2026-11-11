package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertFalse;

/**
 * @author Barakh
 */
@Test
public class V77TablesDeletedTest extends UpgradeBaseTest {

    public void testV224DropOldUsersManagementTables() throws SQLException {
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "aces"));
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "acls"));
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "permission_target_repos"));
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "permission_targets"));
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "users_groups"));
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "groups"));
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "user_props"));
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "users"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v77;
    }
}
