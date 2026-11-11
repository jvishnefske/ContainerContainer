package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

public class V61OperationKeyAlterTest extends UpgradeBaseTest {

    @Test
    public void testV61ReplicationErrorsNullable() throws SQLException {
        assertTrue(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "replication_errors"));
        assertTrue(isColumnNullable("replication_errors", "operation_key"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v61;
    }
}
