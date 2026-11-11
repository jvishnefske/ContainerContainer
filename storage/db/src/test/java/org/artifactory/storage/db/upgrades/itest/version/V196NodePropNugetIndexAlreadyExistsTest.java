package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v196;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Volodymyr Shulga
 */
public class V196NodePropNugetIndexAlreadyExistsTest extends AsyncUpgradeBaseTest {
    @Test
    public void testNugetPartialIndexSkipped() throws SQLException {
        testOnlyWithPostgresql();
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL, v196)) {
            // apply the converter twice to make sure it's skipped
            assertEquals(v196.converter().convert(context).getStatus(), ConverterApplyStatus.COMPLETED);
            assertEquals(v196.converter().convert(context).getStatus(), ConverterApplyStatus.SKIPPED);
            assertTrue(indexExist("node_props", "prop_key", "node_prop_nuget_partial_idx"));
            assertTrue(indexExist("node_props", "prop_value", "node_prop_nuget_partial_idx"));
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v196;
    }
}