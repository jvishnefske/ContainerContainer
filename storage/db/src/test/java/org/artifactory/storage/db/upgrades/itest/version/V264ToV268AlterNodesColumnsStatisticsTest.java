package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v263;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v264;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v265;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v266;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v267;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v268;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests that v264–v268 correctly increase PostgreSQL statistics collection to 1000
 * for high-cardinality / skewed columns on the nodes table.
 */
public class V264ToV268AlterNodesColumnsStatisticsTest extends UpgradeBaseTest {

    private static final List<String> NODES_COLUMNS =
            List.of("node_path", "sha256", "sha1_actual", "repo", "node_name");

    private static final List<ArtifactoryDBVersion> CONVERTERS =
            List.of(v264, v265, v266, v267, v268);

    @Test
    public void testConvertorsCompleted() throws SQLException {
        testOnlyWithPostgresql();
        runConverters(ConverterApplyStatus.COMPLETED);
        assertStatisticsForAllColumns(1000);
    }

    @Test
    public void testConvertorsSkipped() throws SQLException {
        testOnlyWithPostgresql();
        setStatisticsOnAllColumns();
        runConverters(ConverterApplyStatus.SKIPPED);
        assertStatisticsForAllColumns(1000);
    }

    private void setStatisticsOnAllColumns() throws SQLException {
        for (String column : NODES_COLUMNS) {
            jdbcHelper.executeUpdate(
                    "ALTER TABLE nodes ALTER COLUMN " + column + " SET STATISTICS 1000");
        }
    }

    private void assertStatisticsForAllColumns(Integer expectedValue) throws SQLException {
        for (String column : NODES_COLUMNS) {
            ResultSet result = jdbcHelper.executeSelect(
                    """
                            SELECT attname, attstattarget FROM pg_attribute
                            WHERE attrelid = 'nodes'::regclass
                            AND attname = '""" + column + "';");
            assertTrue(result.next(), "Column not found in pg_attribute: " + column);
            assertEquals(result.getObject(2), expectedValue,
                    "Unexpected statistics target for column: " + column);
            result.close();
        }
    }

    private void runConverters(ConverterApplyStatus expectedStatus) throws SQLException {
        for (ArtifactoryDBVersion version : CONVERTERS) {
            try (ChangeLogContext context = new ChangeLogContext(
                    jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), version)) {
                DbConverterResult result = version.converter().convert(context);
                assertEquals(result.getStatus(), expectedStatus,
                        "Unexpected status for converter: " + version.version());
            }
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v263;
    }
}
