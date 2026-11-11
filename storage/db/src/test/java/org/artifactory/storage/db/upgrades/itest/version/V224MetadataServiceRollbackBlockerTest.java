package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v223;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v224;
import static org.testng.Assert.assertEquals;

public class V224MetadataServiceRollbackBlockerTest extends UpgradeBaseTest {

    @Test
    public void columnAdded() throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v224)) {
            DbConverterResult res = v224.converter().convert(context);
            assertEquals(res.getStatus(), ConverterApplyStatus.COMPLETED);
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v223;
    }
}
