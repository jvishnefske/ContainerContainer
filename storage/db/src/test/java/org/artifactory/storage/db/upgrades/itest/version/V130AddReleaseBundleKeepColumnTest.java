package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v128;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v130;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Eli Skoran
 */
public class V130AddReleaseBundleKeepColumnTest extends UpgradeBaseTest {

    @Test
    public void testUpgrade() throws SQLException {
        assertFalse(columnExists("artifact_bundles", "keep"));

        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v130)) {
            v130.converter().convert(context);
        }

        assertTrue(columnExists("artifact_bundles", "keep"));
        assertEquals(columnSize("artifact_bundles", "keep"), 5);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v128;
    }
}
