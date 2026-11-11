package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.itest.UniqueJvmForkTestIdentifier;
import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.PostgresqlUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.sql.SQLException;

/**
 * @author Alexei Vainshtein
 */
public class V79AsyncUpgradePostgreSqlNodesIndexesTest extends AsyncUpgradeBaseTest {

    @Test
    public void testNodesNodePath() throws SQLException {
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        String indexDef = PostgresqlUtils.getPostgresqlIndexDefInfo(jdbcHelper, "nodes_node_path_idx")
                .replace(UniqueJvmForkTestIdentifier.get() + ".", "");
        String query = "CREATE INDEX nodes_node_path_idx ON nodes USING btree (node_path varchar_pattern_ops)";
        assertIndexDef(query, indexDef);
    }

    @Test
    public void testNodesNodeNameIndex() throws SQLException {
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        String indexDef = PostgresqlUtils.getPostgresqlIndexDefInfo(jdbcHelper, "nodes_node_name_idx")
                .replace(UniqueJvmForkTestIdentifier.get() + ".", "");
        String query = "CREATE INDEX nodes_node_name_idx ON nodes USING btree (node_name varchar_pattern_ops)";
        assertIndexDef(query, indexDef);
    }

    @Test
    public void testNodesRepoPathIndex() throws SQLException {
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        String indexDef = PostgresqlUtils.getPostgresqlIndexDefInfo(jdbcHelper, "nodes_repo_path_name_idx")
                .replace(UniqueJvmForkTestIdentifier.get() + ".", "");
        String query = "CREATE UNIQUE INDEX nodes_repo_path_name_idx ON " +
                "nodes USING btree (repo " +
                "varchar_pattern_ops, " +
                "node_path varchar_pattern_ops, node_name varchar_pattern_ops)";
        assertIndexDef(query, indexDef);
    }

    private void assertIndexDef(String index, String indexDef) {
        Assert.assertEquals(indexDef, index);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v80;
    }
}
