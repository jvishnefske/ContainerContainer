package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v117;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v118;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v119;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


@Test
public class V118toV119CursorEventTableUpgradeTest extends UpgradeBaseTest {

    @BeforeMethod
    public void setup() throws IOException, SQLException {
        final List<ArtifactoryDBVersion> dbVersions = List.of(v118, v119);
        for (DbVersion dbVersion : dbVersions) {
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                    dbProperties.getDbType(), dbVersion)) {
                dbVersion.converter().convert(context);
            }
        }
        importSql("/sql/event_cursor_status_and_updated.sql");
    }

    public void testAddStatusColumnToEventCursorTable() throws SQLException {
        String tbName = "node_event_cursor";
        assertTrue(tableExists(tbName));
        assertTrue(columnExists(tbName, "status"));
        assertTrue(columnExists(tbName, "last_updated"));

        long afterTime = System.currentTimeMillis();

        try (ResultSet res = jdbcHelper.executeSelect("SELECT * FROM node_event_cursor")){
            assertTrue(res.next());
            assertEquals(res.getString("status"), "SYNC");
            long columnLastUpdated = res.getLong("last_updated");
            assertTrue(columnLastUpdated <= afterTime);
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v117;
    }
}
