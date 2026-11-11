package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v127;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v128;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v129;

/**
 * @author Alexei Vainshtein
 */
public class V128AndV129AlterColumnCategoryInDistributionLocksTableTest extends UpgradeBaseTest {

    @Test
    public void testUpgrade() throws SQLException {
        Assert.assertEquals(64, columnSize("distributed_locks", "category"));

        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v128)) {
            v128.converter().convert(context);
        }

        if (DbType.MSSQL.equals(dbProperties.getDbType())) {
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                    dbProperties.getDbType(), v129)) {
                v129.converter().convert(context);
            }
        }
        Assert.assertEquals(128, columnSize("distributed_locks", "category"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v127;
    }
}
