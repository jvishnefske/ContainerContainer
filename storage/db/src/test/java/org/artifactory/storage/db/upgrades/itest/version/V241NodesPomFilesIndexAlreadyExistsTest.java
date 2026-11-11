package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v241;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Fawaz Atwa
 */
public class V241NodesPomFilesIndexAlreadyExistsTest extends AsyncUpgradeBaseTest {

    @Test
    public void testNodesPomFilesIndexSkipped() throws SQLException {
        testOnlyWithPostgresql();
        try (ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL,
                v241)) {

            assertEquals(v241.converter().convert(context).getStatus(), ConverterApplyStatus.COMPLETED);
            assertEquals(v241.converter().convert(context).getStatus(), ConverterApplyStatus.SKIPPED);

            assertTrue(indexExist("nodes", "repo", "nodes_pom_files_idx"));
            assertTrue(indexExist("nodes", "depth", "nodes_pom_files_idx"));
            assertTrue(indexExist("nodes", "node_path", "nodes_pom_files_idx"));
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v241;
    }
}
