package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v188;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v189;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

/**
 * @author Hezi Cohen
 */
public class V189AddServiceIdColumnToArtifactBundlesTest extends UpgradeBaseTest {

    @Test
    public void testServiceIdColumnExists() throws SQLException {
        assertFalse(columnExists("artifact_bundles", "source_service_id"));
        runV189Convertor(ConverterApplyStatus.COMPLETED);
        assertTrue(columnExists("artifact_bundles", "source_service_id"));
        runV189Convertor(ConverterApplyStatus.SKIPPED);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v188;
    }

    private void runV189Convertor(ConverterApplyStatus expectedResult) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v189)) {
            DbConverterResult result = v189.converter().convert(context);
            assertEquals(result.getStatus(), expectedResult);
        }
    }
}
