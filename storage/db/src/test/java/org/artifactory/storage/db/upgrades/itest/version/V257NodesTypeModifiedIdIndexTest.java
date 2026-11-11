package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v256;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v257;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertTrue;

/**
 * @author Igor Usenko
 */
public class V257NodesTypeModifiedIdIndexTest extends AsyncUpgradeBaseTest {

    private static final String TABLE_NAME = "nodes";
    private static final String INDEX_NAME = "nodes_type_modified_id_idx";

    @Test
    public void indexCreatedWhenSaaSMode() throws SQLException {
        testOnlyWithPostgresql();
        setSaaSMode(true);
        try (ChangeLogContext changeLogContext = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL,
                v257)) {
            v257.converter().convert(changeLogContext);
        }
        assertTrue(indexExist(TABLE_NAME, "node_type", INDEX_NAME));
        assertTrue(indexExist(TABLE_NAME, "modified", INDEX_NAME));
        assertTrue(indexExist(TABLE_NAME, "node_id", INDEX_NAME));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v256;
    }
}

