package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.V177ToV181Commons;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v178;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v180;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V178AndV180DropNodeNameIndexIfValidTest extends V177ToV181Commons {
    @Test
    public void testOldIndexShouldBeDroppedOnlyIfNewIndexExists() throws SQLException {
        assertFalse(indexExist(TABLE_NAME, COLUMN_NAMES.get(0), NEW_INDEX));
        assertFalse(indexExist(TABLE_NAME, COLUMN_NAMES.get(1), NEW_INDEX));
        assertTrue(indexExist(TABLE_NAME, COLUMN_NAMES.get(0), OLD_INDEX));
        runUpgrade(v178, ConverterApplyStatus.SKIPPED);
        runUpgrade(v180, ConverterApplyStatus.SKIPPED);
        assertFalse(indexExist(TABLE_NAME, COLUMN_NAMES.get(0), NEW_INDEX));
        assertFalse(indexExist(TABLE_NAME, COLUMN_NAMES.get(1), NEW_INDEX));
        assertTrue(indexExist(TABLE_NAME, COLUMN_NAMES.get(0), OLD_INDEX));
    }
}
