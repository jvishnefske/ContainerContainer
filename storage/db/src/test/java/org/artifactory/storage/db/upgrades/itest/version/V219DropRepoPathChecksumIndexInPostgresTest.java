package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v215;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v219;
import static org.testng.Assert.assertFalse;

/**
 * @author Hussein Alshqerat
 */
public class V219DropRepoPathChecksumIndexInPostgresTest extends AsyncUpgradeBaseTest {

    @Test
    public void testIndexDropped() throws SQLException {
        invokeConverter(v219);
        validateIndexDropped();
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v215;
    }

    private void invokeConverter(ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            version.converter().convert(context);
        }
    }

    private void validateIndexDropped() throws SQLException {
        assertFalse(indexExist("nodes", "repo_path_checksum", "nodes_repo_path_checksum"));
    }
}
