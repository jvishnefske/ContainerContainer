/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.fs.itest.dao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.Sets;
import org.artifactory.concurrent.NonStandardLockCategory;
import org.artifactory.storage.db.dao.locks.DbDistributeLocksDao;
import org.artifactory.storage.db.dao.locks.LockInfo;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Test the {@link DbDistributeLocksDao} functionality
 *
 * @author Shay Bagants
 * @author Mor Merhav
 */
@Test
public class DbDistributeLocksDaoTest extends DbBaseTest {

    @Autowired
    private DbDistributeLocksDao dbLockDao;

    @BeforeClass
    public void setup() {
        importSql("/sql/db_locks.sql");
    }

    // test pre-defined locks and make sure these exists
    @Test
    public void testIsLocked() throws SQLException {
        // expected to be locked
        assertTrue(dbLockDao.isLocked("lockCategory", "path/to/file.jar"));
        assertTrue(dbLockDao.isLocked("lockCategory", "mykey"));
        assertTrue(dbLockDao.isLocked("bagants", "path/to/file2.exe"));
        assertTrue(dbLockDao.isLocked("arti", "path/to/file3.zip"));
        assertTrue(dbLockDao.isLocked("somemap", "imtheindexerjob"));
        assertTrue(dbLockDao.isLocked("somemap", "nugetAddPkg"));

        // non locked paths
        assertFalse(dbLockDao.isLocked("lockCategory", "/path/to/file.jar"));
        assertFalse(dbLockDao.isLocked("lockCategory", "path/to/file2.exe"));
        assertFalse(dbLockDao.isLocked("generic", "path/to/file2.jar"));
        assertFalse(dbLockDao.isLocked("zyd", "path/to/file2.jar"));
        assertFalse(dbLockDao.isLocked("somemap", "newpath"));
    }

    // test pre-defined locks per category and make sure the lockingMapSize return the correct num of locks per category
    @Test
    public void testLockingMapSize() throws SQLException {
        assertEquals(dbLockDao.lockingMapSize("lockCategory"), 2);
        assertEquals(dbLockDao.lockingMapSize("bagants"), 1);
        assertEquals(dbLockDao.lockingMapSize("arti"), 1);
        assertEquals(dbLockDao.lockingMapSize("somemap"), 2);
        assertEquals(dbLockDao.lockingMapSize("nonexistscategory"), 0);
    }

    // test pre-defined locks per category
    @Test
    public void testLockingMapKeySet() throws SQLException {
        // test 'lockCategory' category
        Set<String> lockKeySet = dbLockDao.lockingMapKeySet("lockCategory");
        assertEquals(lockKeySet.size(), 2);
        assertTrue(lockKeySet.contains("path/to/file.jar"));
        assertTrue(lockKeySet.contains("mykey"));
        assertFalse(lockKeySet.contains("blabla"));

        // test 'bagants' category
        Set<String> bagants = dbLockDao.lockingMapKeySet("bagants");
        assertEquals(bagants.size(), 1);
        assertTrue(bagants.contains("path/to/file2.exe"));
        assertFalse(bagants.contains("path/to/where"));

        // test non existing category
        Set<String> who = dbLockDao.lockingMapKeySet("who");
        assertEquals(who.size(), 0);
    }

    // test the getAllCurrentServerLocks method using pre-defined locks
    @Test
    public void testGetAllCurrentServerLocks() throws SQLException {
        Set<LockInfo> locks = dbLockDao.getAllLocksByOwner("froggy");
        assertOnlyFroggyLocksExist(locks);
    }

    @Test
    public void testGetAllCurrentServerLocksByCategoryAndOwner() throws SQLException {
        Set<LockInfo> lockInfoSet = dbLockDao
                .getAllLocksByCategoryAndOwner(NonStandardLockCategory.REPLICATION.getName(), "froggy");
        assertNotNull(lockInfoSet);
        assertFalse(lockInfoSet.isEmpty());
        lockInfoSet.forEach(lockInfo -> {
            assertEquals(lockInfo.getCategory(), NonStandardLockCategory.REPLICATION.getName());
            assertEquals(lockInfo.getOwner(), "froggy");
        });
    }

    // test deleteLock method - add lock, make sure it exists, delete it, make sure it was deleted and that the
    //pre-defined locks still exists
    @Test
    public void testDeleteLock() throws SQLException {
        LockInfo lockInfo = acquireLockInternal("ownerxyz", "cat1", "nibuvy");
        boolean deleted = dbLockDao.unlock(lockInfo.getCategory(), lockInfo.getKey(), lockInfo.getOwner());
        assertTrue(deleted);
        assertFalse(dbLockDao.isLocked(lockInfo.getCategory(), lockInfo.getKey()));

        // make sure all other pre-defined locks still exists
        testIsLocked();
        testLockingMapSize();
        testLockingMapKeySet();
    }

    @Test
    public void testGetExpiredLocksByCategory() {
        long startTime = System.currentTimeMillis();
        Set<LockInfo> expiredLocks = dbLockDao.getExpiredLocksByCategory("MorLockCategory", startTime, "MorOwner1");
        assertNotNull(expiredLocks);
        assertFalse(expiredLocks.isEmpty());
        expiredLocks.forEach(lockInfo -> {
            assertEquals("MorLockCategory", lockInfo.getCategory());
            assertTrue(lockInfo.getStartedTime() < startTime);
        });
    }

    @Test
    public void testGetExpiredLocksNotContainedInCategories() {
        long startTime = System.currentTimeMillis();
        Set<String> excludedCategories = Sets.newHashSet("MorLockCategory", "LockCategory");
        Set<LockInfo> expiredLocks = dbLockDao
                .getExpiredLocksNotContainedInCategories(excludedCategories, startTime, "froggy");
        assertNotNull(expiredLocks);
        assertFalse(expiredLocks.isEmpty());
        expiredLocks.forEach(lockInfo -> {
            assertNotEquals("MorLockCategory", lockInfo.getCategory());
            assertNotEquals("LockCategory", lockInfo.getCategory());
            assertTrue(lockInfo.getStartedTime() < startTime);
        });
    }

    @Test
    public void testReleaseLockByCategoryAndKey() throws SQLException {
        LockInfo lockInfo = acquireLockInternal("me", "000", "111");
        assertTrue(dbLockDao.isLocked(lockInfo.getCategory(), lockInfo.getKey()));
        boolean released = dbLockDao.deleteLockByCategoryAndKey(lockInfo.getCategory(), lockInfo.getKey());
        assertTrue(released);
        assertFalse(dbLockDao.isLocked(lockInfo.getCategory(), lockInfo.getKey()));

        // make sure all other pre-defined locks still exists
        testIsLocked();
        testLockingMapSize();
        testLockingMapKeySet();
    }

    @Test
    public void testGetAllLocksByCategoryNotOwnedBy() {
        Set<LockInfo> lockInfoSet = dbLockDao
                .getAllLocksByCategoryNotOwnedBy(Sets.newHashSet("MorOwner1"), "MorLockCategory");
        assertNotNull(lockInfoSet);
        lockInfoSet.forEach(lockInfo -> {
            assertNotEquals(lockInfo.getOwner(), "MorOwner1");
            assertEquals(lockInfo.getCategory(), "MorLockCategory");
        });
    }

    @Test
    public void testGetAllLocksNotOwnedByAndNotContainedInCategories() {
        Set<String> excludedCategories = Sets.newHashSet("MorLockCategory", "replications");
        Set<LockInfo> lockInfoSet = dbLockDao
                .getAllLocksNotOwnedByAndNotContainedInCategories(Sets.newHashSet("MorOwner1"),
                        excludedCategories);
        assertNotNull(lockInfoSet);
        assertFalse(lockInfoSet.isEmpty());
        lockInfoSet.forEach(lockInfo -> {
            assertNotEquals(lockInfo.getOwner(), "MorOwner1");
            assertFalse(excludedCategories.contains(lockInfo.getCategory()));
        });
    }

    /**
     * Test for RTFACT-17320 - avoid polluting DB Logs with errors of
     * duplicated key for locks_pk
     */
    @Test
    public void testTryAcquireLockWhenKeyExistsOnDb() throws SQLException {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(DbDistributeLocksDao.class);
        logger.addAppender(listAppender);
        Level oldLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        LockInfo lockInfo = acquireLockInternal("me", "000", "111");
        assertTrue(dbLockDao.isLocked(lockInfo.getCategory(), lockInfo.getKey()));
        // second call should not end up with exception
        assertFalse(dbLockDao.tryToAcquireLock(lockInfo));

        List<ILoggingEvent> logsList = listAppender.list;
        if (!dbLockDao.isConditionalInsertEnabled()) {
            //psql below 9.5, mssql, derby, mariadb, mysql, global system prop disabled
            assertTrue(logsList.stream()
                    .anyMatch(logEvent -> logEvent.getMessage().contains("Failed to insert lock")));
        } else { // psql 9.5+, oracle
            assertTrue(logsList.stream()
                    .noneMatch(logEvent -> logEvent.getMessage().contains("Failed to insert lock")));
        }
        logger.setLevel(oldLevel);
    }

    @Test
    public void testGetLocksByPrefix() {
        List<LockInfo> allLocksByPrefix = dbLockDao.getLocksByPrefix("MorLockCategory", "MorLock", 2);
        assertEquals(allLocksByPrefix.size(), 2);
    }


    private void assertOnlyFroggyLocksExist(Set<LockInfo> locks) {
        assertEquals(locks.size(), 4);
        assertTrue(locks
                .contains(new LockInfo("somemap", "imtheindexerjob", "froggy", 746, "thread-async-0", 1284292504000L)));
        assertTrue(locks
                .contains(new LockInfo("somemap", "nugetAddPkg", "froggy", 745, "thread-async-1", 1284289204000L)));
        assertTrue(locks
                .contains(new LockInfo(NonStandardLockCategory.REPLICATION.getName(), "my:key", "froggy", 746,
                        "thread-async-0",
                        1284290404000L)));
        assertTrue(locks
                .contains(new LockInfo(NonStandardLockCategory.REPLICATION.getName(), "my:expiredKey", "froggy", 746,
                        "thread-async-5",
                        1284287400000L)));
    }

    private LockInfo acquireLockInternal(String owner, String category, String key) throws SQLException {
        LockInfo lockInfo = new LockInfo(category, key, owner, Thread.currentThread().getId(),
                Thread.currentThread().getName(), System.currentTimeMillis());
        boolean locked = dbLockDao.tryToAcquireLock(lockInfo);
        assertTrue(locked);
        assertTrue(dbLockDao.isLocked(lockInfo.getCategory(), lockInfo.getKey()));
        LockInfo dbLockInfo = dbLockDao.getLockInfo(lockInfo.getCategory(), lockInfo.getKey());
        assertEquals(lockInfo, dbLockInfo);
        return lockInfo;
    }
}
