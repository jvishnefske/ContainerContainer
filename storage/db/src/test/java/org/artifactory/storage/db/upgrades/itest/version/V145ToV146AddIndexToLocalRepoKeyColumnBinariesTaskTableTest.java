package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v145;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v146;
import static org.testng.Assert.assertTrue;

public class V145ToV146AddIndexToLocalRepoKeyColumnBinariesTaskTableTest extends UpgradeBaseTest {

    @BeforeClass
    @Override
    protected void springTestContextPrepareTestInstance() throws Exception {
        super.springTestContextPrepareTestInstance();
        dropExistingObjectsWithRetryIfNeeded();
        executeSqlStream(getPreviousDbSchemaSql("v204", dbProperties.getDbType()));
    }

    @Test
    public void testRepoKeyIndexExists() throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v146)) {
            v146.converter().convert(context);
        }
        assertTrue(indexExist("binaries_tasks", "local_repo_key",
                "binaries_tasks_repo_key_idx"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v145;
    }
}
