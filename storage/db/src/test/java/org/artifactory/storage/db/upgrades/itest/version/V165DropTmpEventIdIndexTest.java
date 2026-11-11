package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.event.dao.EventsDao.EVENTS_TEMP_TABLE;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v164;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v165;
import static org.jfrog.storage.DbType.ORACLE;
import static org.testng.Assert.assertFalse;

/**
 * @author Hussein Alshqerat
 */
public class V165DropTmpEventIdIndexTest extends AsyncUpgradeBaseTest {
    @Test
    public void testIndexNotExists() throws SQLException {
        if (ORACLE.equals(dbProperties.getDbType())) {
            String message = "This test is not for Oracle, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v165)) {
            v165.converter().convert(context);
        }
        assertFalse(indexExist(EVENTS_TEMP_TABLE, "tmp_event_id", "node_events_tmp_event_id_idx"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v164;
    }
}
