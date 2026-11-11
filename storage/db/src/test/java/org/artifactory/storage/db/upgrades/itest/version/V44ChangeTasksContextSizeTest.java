package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v44;
import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Shay Bagants
 */
@Test
public class V44ChangeTasksContextSizeTest extends UpgradeBaseTest {

    @Test
    public void testConvertColumn() throws SQLException {
        String columnName = "task_context";
        String tableName = "tasks";
        assertTrue(columnExists(tableName, columnName));
        int columnSize = withConnection(jdbcHelper, conn -> columnSize(tableName, columnName));
        assertEquals(columnSize, 1024);
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v44)) {
            v44.converter().convert(context);
        }
        columnSize = withConnection(jdbcHelper, conn -> columnSize(tableName, columnName));
        assertEquals(columnSize, 2048);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v43;
    }
}
