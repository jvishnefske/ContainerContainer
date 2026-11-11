package org.artifactory.storage.db.hqc.dao;

import org.artifactory.hqc.enums.HeavyQueryCacheType;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.hqc.dao.HeavyQueryCacheDao;
import org.jfrog.storage.hqc.model.HeavyQueryCacheEntry;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertNull;

/**
 * @author Tomer Slutsky
 * @author Andrey Tashlitsky
 */
public class HeavyQueryCacheDaoTest extends DbBaseTest {

    private final String ENTRY_NAME = HeavyQueryCacheType.TOTAL_ARTIFACT_COUNT + "repo";
    private final long START = System.currentTimeMillis();

    private HeavyQueryCacheDao heavyQueryCacheDao;

    @Autowired
    private JdbcHelper jdbcHelper;
    @Autowired
    private DbService dbService;

    @BeforeAll
    public void beforeAll() {
        heavyQueryCacheDao = new HeavyQueryCacheDao(jdbcHelper, dbService.getDatabaseType());
    }

    @Test
    public void testCreate() throws SQLException {
        HeavyQueryCacheEntry cacheEntry = HeavyQueryCacheEntry.builder()
                .id(1)
                .cacheKey(ENTRY_NAME)
                .cacheTime(START)
                .expiryTime(START + 120_000)
                .data("info".getBytes())
                .build();
        int i = heavyQueryCacheDao.create(cacheEntry);
        assertEquals(i, 1);
    }

    @Test(dependsOnMethods = "testCreate")
    public void testGetCachedResponse() throws SQLException {
        HeavyQueryCacheEntry cacheEntry = heavyQueryCacheDao.getCachedResponse(ENTRY_NAME, START);
        assertTrue(Arrays.equals(cacheEntry.getData(), "info".getBytes()));
        assertEquals(cacheEntry.getCacheTime(), START);
        assertEquals(cacheEntry.getExpiryTime(), START + 120_000);
    }

    @Test(dependsOnMethods = "testGetCachedResponse")
    public void testGetExpired() throws SQLException {
        long expired = START - 10;
        HeavyQueryCacheEntry heavyQueryCacheEntry = HeavyQueryCacheEntry.builder()
                .id(2)
                .cacheKey(ENTRY_NAME + "1")
                .cacheTime(expired)
                .expiryTime(expired + 1)
                .data("info2".getBytes())
                .build();
        assertEquals(heavyQueryCacheDao.create(heavyQueryCacheEntry), 1);
        HeavyQueryCacheEntry entry = heavyQueryCacheDao.getCachedResponse(ENTRY_NAME + "1", START);
        assertNull(entry);
    }

    @Test(dependsOnMethods = "testGetExpired")
    public void testGetAllExpired() throws SQLException {
        long time = System.currentTimeMillis();
        List<HeavyQueryCacheEntry> allExpiredEntries = heavyQueryCacheDao.getExpiredEntries(time);
        assertEquals(allExpiredEntries.get(0).getId(), 2);
    }

    @Test(dependsOnMethods = "testGetAllExpired")
    public void testDelete() throws SQLException {
        List<HeavyQueryCacheEntry> entries = List.of(HeavyQueryCacheEntry.builder().id(2).build());
        heavyQueryCacheDao.delete(entries);
        long time = System.currentTimeMillis();
        entries = heavyQueryCacheDao.getExpiredEntries(time);
        assertTrue(entries.isEmpty());
    }

    @Test(dependsOnMethods = "testDelete")
    public void testDeleteByName() throws SQLException {
        HeavyQueryCacheEntry cacheEntry = HeavyQueryCacheEntry.builder()
                .id(3)
                .cacheKey(ENTRY_NAME)
                .cacheTime(START)
                .expiryTime(START + 120_000)
                .data("info".getBytes())
                .build();
        int i = heavyQueryCacheDao.create(cacheEntry);
        assertEquals(i, 1);
        heavyQueryCacheDao.delete(cacheEntry);
        assertNull(heavyQueryCacheDao.getCachedResponse(ENTRY_NAME, 0));
    }
}
