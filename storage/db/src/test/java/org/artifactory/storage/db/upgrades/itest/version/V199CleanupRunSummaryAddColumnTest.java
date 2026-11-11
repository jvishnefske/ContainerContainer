package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v198;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v199;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Oleksandr Legenkiy
 */
public class V199CleanupRunSummaryAddColumnTest extends UpgradeBaseTest {

    @Test
    public void columnAdded() throws SQLException {
        assertFalse(columnExists("cleanup_run_summary", "total_binary_size"));
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v199)) {
            v199.converter().convert(context);
        }
        assertTrue(columnExists("cleanup_run_summary", "total_binary_size"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v198;
    }
}
