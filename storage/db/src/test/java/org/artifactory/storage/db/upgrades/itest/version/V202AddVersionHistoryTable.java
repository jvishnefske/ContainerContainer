package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v201;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v202;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V202AddVersionHistoryTable extends UpgradeBaseTest {

    @Test
    public void tableAdded() throws SQLException {
        assertFalse(tableExists("version_history"));
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v202)) {
            DbConverterResult res = v202.converter().convert(context);
            assertEquals(res.getStatus(), ConverterApplyStatus.COMPLETED);
        }
        assertTrue(tableExists("version_history"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v201;
    }
}
