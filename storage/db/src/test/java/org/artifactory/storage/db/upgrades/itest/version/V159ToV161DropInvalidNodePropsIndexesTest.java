package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v158;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v159;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v160;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v161;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertEquals;

/**
 * @author Hussein Alshqerat
 */
public class V159ToV161DropInvalidNodePropsIndexesTest extends AsyncUpgradeBaseTest {
    private static final String CREATE_NODE_PROPS_PROP_VALUE_KEY_IDX_QUERY =
            "CREATE INDEX IF NOT EXISTS pattern_node_props_prop_value_key_idx ON " +
                    "node_props (prop_value varchar_pattern_ops, prop_key varchar_pattern_ops);";
    private static final String CREATE_NODE_PROPS_PROP_KEY_VALUE_IDX_QUERY =
            "CREATE INDEX IF NOT EXISTS pattern_node_props_prop_key_value_idx ON " +
                    "node_props (prop_key varchar_pattern_ops, prop_value varchar_pattern_ops);";
    private static final String CREATE_NODE_PROPS_NODE_PROP_VALUE_IDX_QUERY =
            "CREATE INDEX IF NOT EXISTS pattern_node_props_node_prop_value_idx ON " +
                    "node_props (node_id, prop_key varchar_pattern_ops, prop_value varchar_pattern_ops);";

    @DataProvider
    private static Object[][] indexesProvider() {
        return new Object[][] {
                {CREATE_NODE_PROPS_NODE_PROP_VALUE_IDX_QUERY, v159},
                {CREATE_NODE_PROPS_PROP_KEY_VALUE_IDX_QUERY, v160},
                {CREATE_NODE_PROPS_PROP_VALUE_KEY_IDX_QUERY, v161}
        };
    }

    @Test(dataProvider = "indexesProvider")
    public void testDropPatternIndexesIfExists(String createIndexQuery, ArtifactoryDBVersion version)
            throws SQLException {
        testOnlyWithPostgresql();
        jdbcHelper.executeUpdate(createIndexQuery);
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL, version)) {
            DbConverterResult result = version.converter().convert(context);
            assertEquals(result.getStatus(), ConverterApplyStatus.COMPLETED);
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v158;
    }
}