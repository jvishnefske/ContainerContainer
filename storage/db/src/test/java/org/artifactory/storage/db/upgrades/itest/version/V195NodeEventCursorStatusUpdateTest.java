package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v195;
import static org.testng.Assert.assertTrue;

/**
 * @author Tamar Rosen
 */
public class V195NodeEventCursorStatusUpdateTest extends UpgradeBaseTest {

    @Test
    public void testSyncStatusIsUpdatedToHealthy() throws SQLException {
        importSql("/sql/event_cursor_status_data.sql");
        try (final ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), v195)) {
            v195.converter().convert(context);
        }
        assertTrue(checkIfHealthyStatusExists());
    }

    private boolean checkIfHealthyStatusExists() throws SQLException {
        try (ResultSet resultSet = jdbcHelper.executeSelect(
                "SELECT * FROM node_event_cursor WHERE status = '" + "HEALTHY" + "'")) {
            return resultSet.next();
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v195;
    }
}
