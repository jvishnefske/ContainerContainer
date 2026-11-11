package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v94;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V95UpgradeNodeEventsTmpEventIdIdxTest extends UpgradeBaseTest {

    @BeforeClass
    @Override
    protected void springTestContextPrepareTestInstance() throws Exception {
        super.springTestContextPrepareTestInstance();
        dropExistingObjectsWithRetryIfNeeded();
        executeSqlStream(getPreviousDbSchemaSql("v204", dbProperties.getDbType()));
    }

    @Test
    public void test() throws Exception {
        assertTrue(primaryKeyExists("node_events_tmp", "node_events_tmp_pk"));
        final ArtifactoryDBVersion dbVersion = ArtifactoryDBVersion.v95;
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), dbVersion)) {
            dbVersion.converter().convert(context);
        }
        assertFalse(indexExist("node_events_tmp", "tmp_event_id", "node_events_tmp_event_id_idx"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v94;
    }
}
