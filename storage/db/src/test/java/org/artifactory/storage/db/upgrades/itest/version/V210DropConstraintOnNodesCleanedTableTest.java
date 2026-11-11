package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.DbType;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v209;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v210;
import static org.testng.Assert.assertEquals;

public class V210DropConstraintOnNodesCleanedTableTest extends UpgradeBaseTest {


    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v209;
    }

    @Test
    public void testDropConstraintOnNodesCleanedTable() throws SQLException {
        validateConverterStatus(dbProperties.getDbType());
    }

    private void validateConverterStatus(DbType dbType) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbType, v210)) {
            assertEquals(v210.converter().convert(context).getStatus(),
                    ConverterApplyStatus.COMPLETED);
        }
    }
}
