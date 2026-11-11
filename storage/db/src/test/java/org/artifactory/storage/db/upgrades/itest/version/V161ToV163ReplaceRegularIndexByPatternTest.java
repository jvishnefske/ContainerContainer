package org.artifactory.storage.db.upgrades.itest.version;

import lombok.NonNull;
import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v161;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v162;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v163;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V161ToV163ReplaceRegularIndexByPatternTest extends AsyncUpgradeBaseTest {

    private final static String TABLE_NAME = "bundle_files";
    private final static String COLUMN_NAME = "repo_path";
    private final static String REGULAR_INDEX = "repo_path_bundle_files_idx";
    private final static String PATTERN_INDEX = "pattern_bundle_files_repo_path_idx";

    @Test
    public void testUpgradeFlow() throws SQLException {
        testOnlyWithPostgresql();

        runUpgrade(v162);
        runUpgrade(v163);

        assertTrue(indexExist(TABLE_NAME, COLUMN_NAME, PATTERN_INDEX));
        assertFalse(indexExist(TABLE_NAME, COLUMN_NAME, REGULAR_INDEX));
    }

    private void runUpgrade(@NonNull ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL, version)) {
            version.converter().convert(context);
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v161;
    }
}
