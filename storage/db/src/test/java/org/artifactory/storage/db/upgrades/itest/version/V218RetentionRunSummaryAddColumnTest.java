package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v217;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v218;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V218RetentionRunSummaryAddColumnTest extends UpgradeBaseTest {

    @Test
    public void columnAdded() throws SQLException {
        assertFalse(columnExists("retention_run_summary", "run_config"));
        Connection connection = jdbcHelper.getDataSource().getConnection();
        try (ChangeLogContext context = new ChangeLogContext(connection, dbProperties.getDbType(), v217)) {
            DbConverterResult result = v218.converter().convert(context);
            assertEquals(result.getStatus(), ConverterApplyStatus.COMPLETED);
        }
        assertTrue(columnExists("trusted_keys", "key_type"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v217;
    }
}
