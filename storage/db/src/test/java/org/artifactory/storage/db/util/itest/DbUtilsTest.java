package org.artifactory.storage.db.util.itest;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.util.DbUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;

/**
 * @author Alexei Vainshtein
 */
public class DbUtilsTest extends DbBaseTest {

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes.sql");
    }

    @Test
    public void testColumnSize() throws SQLException {
        Assert.assertTrue(DbUtils.columnExists(jdbcHelper, dbProperties.getDbType(), "tasks", "task_context"));
        Assert.assertEquals(DbUtils.getColumnSize(jdbcHelper, dbProperties.getDbType(), "tasks", "task_context"), 2048);
    }

    @Test
    public void testGetConnectionWithDefaultTimeout() throws SQLException {
        QueryControls queryControls = QueryControls.builder().build();
        assertConnectionResultWithTimeout(queryControls, DbUtils.DEFAULT_TIMEOUT_MILLISECONDS);
    }

    @Test
    public void testIndexExists() throws SQLException {
        String table = "bundle_files";
        String column = "node_id";
        String indexName = "bundle_files_node_id_idx";

        Assert.assertTrue(DbUtils.indexExists(jdbcHelper, table, column, indexName,
                dbProperties.getDbType()));
    }

    private void assertConnectionResultWithTimeout(QueryControls queryControls, int timeout) throws SQLException {
        Executor executor = jdbcHelper.getDataSource().getNetworkTimeoutExecutor();
        try (Connection connection = DbUtils.getConnection(queryControls, jdbcHelper.getDataSource(),
                executor)) {
            int networkTimeout = connection.getNetworkTimeout();
            if (dbProperties.getDbType() == DbType.DERBY) {
                Assert.assertEquals(networkTimeout, 0);
                return;
            }
            Assert.assertEquals(networkTimeout, timeout);
        }
    }
}
