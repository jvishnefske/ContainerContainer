package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v262;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v263;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertFalse;

/**
 * Tests that nodes_ts_folders_idx is not created when not running in SaaS mode (aolOnly false).
 * v263 is SEQ_ASYNC and runs only when AolUtils.isAol() is true and DB is PostgreSQL.
 *
 * @author Igor Usenko
 */
public class V263NodesTsFoldersIdxNotCreatedWhenNotSaaSTest extends AsyncUpgradeBaseTest {

    private static final String TABLE_NAME = "nodes";
    private static final String INDEX_NAME = "nodes_ts_folders_idx";

    @Test
    public void indexNotCreatedWhenNotSaaSMode() throws SQLException {
        testOnlyWithPostgresql();
        setSaaSMode(false);
        try (ChangeLogContext changeLogContext = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL,
                v263)) {
            v263.converter().convert(changeLogContext);
        }
        assertFalse(indexExist(TABLE_NAME, "node_path", INDEX_NAME));
        assertFalse(indexExist(TABLE_NAME, "node_name", INDEX_NAME));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v262;
    }
}
