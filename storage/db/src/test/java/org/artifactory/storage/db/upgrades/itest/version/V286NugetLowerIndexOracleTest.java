package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v286;
import static org.jfrog.storage.DbType.ORACLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the Oracle-only function-based index {@code node_prop_nuget_lower_idx} (v286) is created on upgrade and
 * that re-running the converter is idempotent (skipped when the index already exists - e.g. a customer who created it
 * manually). Idempotency is the critical property: Oracle function-based indexes expose system-generated hidden
 * columns, so a column-name-based existence guard would not detect the index and would fail with ORA-00955 on re-run.
 *
 * @author Arko Jyoti De
 */
public class V286NugetLowerIndexOracleTest extends AsyncUpgradeBaseTest {

    private static final String INDEX_NAME = "node_prop_nuget_lower_idx";

    @Test
    public void nugetLowerIndexShouldBeCreatedAndIdempotentWhenUpgradingOnOracle() throws SQLException {
        testOnlyWithOracle();
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                ORACLE, v286)) {
            // Apply the converter twice: the second run must be skipped by the existence guard, not fail on ORA-00955.
            assertEquals(v286.converter().convert(context).getStatus(), ConverterApplyStatus.COMPLETED);
            assertEquals(v286.converter().convert(context).getStatus(), ConverterApplyStatus.SKIPPED);
        }
        assertTrue(oracleIndexExistsByName(),
                "Expected function-based index " + INDEX_NAME + " to exist on node_props");
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v285;
    }

    /**
     * Checks index existence by name via {@code user_indexes}. This is reliable for Oracle function-based indexes,
     * unlike a column-name lookup which sees the system-generated {@code SYS_NCnnnnn$} hidden columns.
     */
    private boolean oracleIndexExistsByName() throws SQLException {
        return DbUtils.withConnection(jdbcHelper, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM user_indexes WHERE index_name = ?")) {
                ps.setString(1, INDEX_NAME.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }
}
