package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v203;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v204;
import static org.testng.Assert.assertEquals;

public class V204MetadataServiceRollbackBlockerTest extends UpgradeBaseTest {

    @Test
    public void columnAdded() throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v204)) {
            DbConverterResult res = v204.converter().convert(context);
            assertEquals(res.getStatus(), ConverterApplyStatus.COMPLETED);
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v203;
    }
}
