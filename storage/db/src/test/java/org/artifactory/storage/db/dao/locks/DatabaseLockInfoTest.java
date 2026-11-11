package org.artifactory.storage.db.dao.locks;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.itest.DbTestUtils;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author Alexei Vainshtein
 */
public class DatabaseLockInfoTest extends DbBaseTest {

    @Test
    public void testDistributedLocksCategoryColumnSize() throws SQLException {
        Integer size = withConnection(jdbcHelper,
                conn -> DbTestUtils.getColumnSize(conn, dbProperties.getDbType(), "distributed_locks", "category"));
        assertNotNull(size);
        assertEquals(size, Integer.valueOf(128));
    }
}
