package org.artifactory.storage.db.upgrades.itest.version;


import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;
import org.springframework.test.context.ActiveProfiles;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Test for the unlogged table annotation of table distributed_locks
 * Test only on PostgreSql
 *
 * @author shaharz
 * @see org.artifactory.storage.db.itest.spring.DbTestConfigFactoryWithExternalLockingDB
 */

@Test
@ActiveProfiles({"with-external-locking-db"})
public class V82AsyncUpgradePostgreSqlDistributedLocksUnloggedTest extends UpgradeBaseTest {


    private static final String IS_UNLOGGED_SQL =
            "SELECT COALESCE((CASE WHEN relpersistence = 'u' THEN TRUE END), FALSE) is_unlogged " +
                    "FROM pg_class " +
                    "WHERE relname = 'distributed_locks'";

    private boolean isUnlogged;

    /**
     * Verify that postgresql distribute_locks table was converted to unlogged
     */
    public void testPostgreSQLDistributedLockedUnlogged() {
        if (dbProperties.getDbType().equals(DbType.POSTGRESQL)) {

            // PostgreSQL's system table has its own cache - we should be able, that we don't receive the old data.
            Awaitility.await()
                    .atLeast(Durations.ONE_SECOND)
                    .atMost(Durations.ONE_MINUTE)
                    .with()
                    .pollInterval(Durations.ONE_SECOND)
                    .until(() -> isUnlogged(jdbcHelper));
            Assert.assertTrue(isUnlogged);
        }
    }

    /**
     * Verify that postgresql distribute_locks external table was converted to unlogged
     *
     * @throws Exception
     */
    public void testPostgreSQLExternalDistributedLockedUnlogged() throws Exception {
        if (dbProperties.getDbType().equals(DbType.POSTGRESQL) && dbProperties.getLockingDbSpecificType().isPresent()) {
            JFrogDataSource lockingDBDataSource =
                    CommonDataSourceFactory.createDifferentDatasourceForDbLock(dbProperties, true);
            JdbcHelper lockingDBJdbcHelper = new JdbcHelper(lockingDBDataSource);
            Assert.assertTrue(isUnlogged(lockingDBJdbcHelper));
        }
    }

    private boolean isUnlogged(JdbcHelper jdbcHelper) throws SQLException {
        try (ResultSet resultSet = jdbcHelper.executeSelect(IS_UNLOGGED_SQL)) {
            isUnlogged = resultSet.next() && resultSet.getBoolean(1);
        }

        return isUnlogged;
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v82;
    }
}

