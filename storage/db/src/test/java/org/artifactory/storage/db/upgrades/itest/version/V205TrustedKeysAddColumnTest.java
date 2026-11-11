package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v204;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v205;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V205TrustedKeysAddColumnTest extends UpgradeBaseTest {

    @Test
    public void columnAdded() throws SQLException {
        assertFalse(columnExists("trusted_keys", "key_type"));
        Connection connection = jdbcHelper.getDataSource().getConnection();
        try (ChangeLogContext context = new ChangeLogContext(connection, dbProperties.getDbType(), v205)) {
            DbConverterResult result = v205.converter().convert(context);
            assertEquals(result.getStatus(), ConverterApplyStatus.COMPLETED);
        }
        assertTrue(columnExists("trusted_keys", "key_type"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v204;
    }
}
