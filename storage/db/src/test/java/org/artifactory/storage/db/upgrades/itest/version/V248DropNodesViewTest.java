package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v247;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v248;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class V248DropNodesViewTest extends UpgradeBaseTest {

    @Test
    public void testViewNotExists() throws SQLException {
        testOnlyWithPostgresql();

        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v248)) {
            DbConverterResult res = v248.converter().convert(context);
            assertEquals(res.getStatus(), ConverterApplyStatus.COMPLETED);
        }
        assertFalse(DbUtils.viewExists(jdbcHelper, dbProperties.getDbType(), "nodes_view"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v247;
    }
}
