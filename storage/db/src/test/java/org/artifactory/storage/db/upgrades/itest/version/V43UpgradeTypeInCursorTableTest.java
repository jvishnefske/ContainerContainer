package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v42;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v43;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Gal Ben Ami
 */
@Test
public class V43UpgradeTypeInCursorTableTest extends UpgradeBaseTest {

    @BeforeMethod
    public void setup() throws IOException, SQLException {
        importSql("/sql/event_cursor_metadata.sql");
        final List<ArtifactoryDBVersion> dbVersions = List.of(v42, v43);
        for (DbVersion dbVersion : dbVersions) {
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                    dbProperties.getDbType(), dbVersion)) {
                dbVersion.converter().convert(context);
            }
        }
    }

    public void testMigration() throws SQLException {
        assertTrue(DbUtils.columnExists(jdbcHelper, dbProperties.getDbType(), "node_event_cursor", "type"));
        try (ResultSet res = jdbcHelper.executeSelect("SELECT * FROM node_event_cursor")) {
            assertTrue(res.next());
            assertEquals(res.getString("operator_id"), "metadata-operator-events");
            assertEquals(res.getString("type"), "METADATA_EVENTS");
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v41;
    }
}
