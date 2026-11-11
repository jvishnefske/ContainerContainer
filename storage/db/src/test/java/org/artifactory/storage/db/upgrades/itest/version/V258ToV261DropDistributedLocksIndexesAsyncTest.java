package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.dao.locks.DbDistributeLocksDao.TABLE_DISTRIBUTED_LOCKS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for ASYNC converters v257–v260 that drop indexes on the distributed_locks table.
 * <ul>
 *   <li>v258 — drops owner_thread index on non-PostgreSQL</li>
 *   <li>v259 — drops owner_thread index on PostgreSQL (concurrently)</li>
 *   <li>v260 — drops lock_key_idx on non-PostgreSQL</li>
 *   <li>v261 — drops distributed_locks_owner on non-PostgreSQL</li>
 * </ul>
 */
@Test
public class V258ToV261DropDistributedLocksIndexesAsyncTest extends UpgradeBaseTest {

    public void testOwnerThreadIndexDroppedOnNonPostgres() throws SQLException {
        boolean isPostgres = dbProperties.getDbType() == DbType.POSTGRESQL;
        if (!isPostgres) {
            assertTrue(indexExist(TABLE_DISTRIBUTED_LOCKS, "owner_thread", "distributed_locks_owner_thread"),
                    "distributed_locks_owner_thread should exist before converter runs");
        }
        runConverter(ArtifactoryDBVersion.v258);
        assertEquals(indexExist(TABLE_DISTRIBUTED_LOCKS, "owner_thread", "distributed_locks_owner_thread"), isPostgres);
    }

    public void testOwnerThreadIndexDroppedOnPostgres() throws SQLException {
        boolean isPostgres = dbProperties.getDbType() == DbType.POSTGRESQL;
        if (isPostgres) {
            assertTrue(indexExist(TABLE_DISTRIBUTED_LOCKS, "owner_thread", "distributed_locks_owner_thread"),
                    "distributed_locks_owner_thread should exist before converter runs on PostgreSQL");
        }
        runConverter(ArtifactoryDBVersion.v259);
        assertFalse(indexExist(TABLE_DISTRIBUTED_LOCKS, "owner_thread", "distributed_locks_owner_thread"));
    }

    public void testLockKeyIndexDroppedOnNonPostgres() throws SQLException {
        boolean isPostgres = dbProperties.getDbType() == DbType.POSTGRESQL;
        if (!isPostgres) {
            assertTrue(indexExist(TABLE_DISTRIBUTED_LOCKS, "lock_key", "lock_key_idx"),
                    "lock_key_idx should exist before converter runs");
        }
        runConverter(ArtifactoryDBVersion.v260);
        assertEquals(indexExist(TABLE_DISTRIBUTED_LOCKS, "lock_key", "lock_key_idx"), isPostgres);
    }

    public void testOwnerIndexDroppedOnNonPostgres() throws SQLException {
        boolean isPostgres = dbProperties.getDbType() == DbType.POSTGRESQL;
        if (!isPostgres) {
            assertTrue(indexExist(TABLE_DISTRIBUTED_LOCKS, "owner", "distributed_locks_owner"),
                    "distributed_locks_owner should exist before converter runs");
        }
        runConverter(ArtifactoryDBVersion.v261);
        assertEquals(indexExist(TABLE_DISTRIBUTED_LOCKS, "owner", "distributed_locks_owner"), isPostgres);
    }

    private void runConverter(ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            version.converter().convert(context);
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v257;
    }
}
