package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v216;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v217;
import static org.testng.Assert.assertEquals;

public class V217MetadataServiceRollbackBlockerTest extends UpgradeBaseTest {

    @Test
    public void columnAdded() throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v217)) {
            DbConverterResult res = v217.converter().convert(context);
            assertEquals(res.getStatus(), ConverterApplyStatus.COMPLETED);
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v216;
    }
}
