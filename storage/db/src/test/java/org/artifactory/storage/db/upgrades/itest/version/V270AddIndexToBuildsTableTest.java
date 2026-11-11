package org.artifactory.storage.db.upgrades.itest.version;

import lombok.NonNull;
import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v233;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v234;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v270;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v83;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v84;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v85;
import static org.testng.Assert.assertTrue;

public class V270AddIndexToBuildsTableTest extends AsyncUpgradeBaseTest {

    @Test
    public void testIndexExists() throws SQLException {
        testOnlyWithPostgresql();
        String indexName = "repo_build_name_date_desc_idx";
        runUpgrade(v83);
        runUpgrade(v84);
        runUpgrade(v85);
        runUpgrade(v234);
        runUpgrade(v270);
        assertTrue(indexExist("builds", "", indexName), String.format("Index %s does not exist", indexName));
        assertIndexColumns(indexName);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v233;
    }

    private void assertIndexColumns(String indexName) throws SQLException {
        List<String> columns = List.of("repo", "build_name", "build_date", "build_id", "build_number");
        for (String column : columns) {
            assertTrue(indexExist("builds", column, indexName),
                    String.format("Column %s does not exist on index %s", column, indexName));
        }
    }

    private void runUpgrade(@NonNull ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            version.converter().convert(context);
        }
    }
}
