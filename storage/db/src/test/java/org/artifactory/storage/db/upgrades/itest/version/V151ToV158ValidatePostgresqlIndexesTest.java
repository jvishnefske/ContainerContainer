package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Hussein Alshqerat
 */
@Ignore
public class V151ToV158ValidatePostgresqlIndexesTest extends AsyncUpgradeBaseTest {
    private static final String NODES_NODE_PATH_IDX = "nodes_node_path_idx";
    private static final String NODES_NODE_NAME_IDX = "nodes_node_name_idx";
    private static final String NODES_REPO_PATH_NAME_IDX = "nodes_repo_path_name_idx";
    private static final String NODE_PROPS_PROP_VALUE_KEY_IDX = "node_props_prop_value_key_idx";
    private static final String NODE_PROPS_PROP_KEY_VALUE_IDX = "node_props_prop_key_value_idx";
    private static final String NODE_PROPS_NODE_PROP_VALUE_IDX = "node_props_node_prop_value_idx";
    private static final String MAKE_INDEX_INVALID_QUERY =
            "UPDATE pg_index SET indisvalid = false FROM pg_class c WHERE c.relname = 'index_name'" +
                    "AND c.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = current_schema()) " +
                    "AND pg_index.indexrelid = c.oid;";
    private static final String IS_INVALID_INDEX_QUERY = "SELECT count(1) FROM pg_class, pg_index WHERE " +
            "pg_index.indisvalid = false AND pg_index.indexrelid = pg_class.oid and relname = " +
            "'index_name'";

    @Test(dataProvider = "indexesProvider")
    public void testShouldSkipIndexCreation(String indexName, ArtifactoryDBVersion version) throws SQLException {
        testOnlyWithPostgresql();
        assertTrue(isIndexValid(indexName));
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL, version)) {
            DbConverterResult result = version.converter().convert(context);
            assertEquals(result.getStatus(), ConverterApplyStatus.SKIPPED);
        }
        assertTrue(isIndexValid(indexName));
    }

    @Test(dataProvider = "indexesProvider")
    public void testShouldTryToCreateTheIndexIfInvalid(String indexName, ArtifactoryDBVersion version)
            throws SQLException {
        testOnlyWithPostgresql();
        makeIndexInvalid(indexName);
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL, version)) {
            version.converter().convert(context);
        }
        assertTrue(isIndexValid(indexName));
    }

    private void makeIndexInvalid(String indexName) throws SQLException {
        String query = MAKE_INDEX_INVALID_QUERY.replaceFirst("index_name", indexName);
        jdbcHelper.executeUpdate(query);
    }

    private boolean isIndexValid(String indexName) throws SQLException {
        String query = IS_INVALID_INDEX_QUERY.replaceFirst("index_name", indexName);
        return jdbcHelper.executeSelectCount(query) == 0;
    }

    @DataProvider
    private static Object[][] indexesProvider() {
        return new Object[][] {
                {NODES_REPO_PATH_NAME_IDX, ArtifactoryDBVersion.v151},
                {NODES_NODE_NAME_IDX, ArtifactoryDBVersion.v152},
                {NODES_NODE_PATH_IDX, ArtifactoryDBVersion.v153},
                {NODE_PROPS_PROP_VALUE_KEY_IDX, ArtifactoryDBVersion.v154},
                {NODE_PROPS_PROP_KEY_VALUE_IDX, ArtifactoryDBVersion.v155},
                {NODE_PROPS_NODE_PROP_VALUE_IDX, ArtifactoryDBVersion.v156}
        };
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v150;
    }
}