package org.artifactory.storage.db.remote.stats.dao;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.remote.stats.entity.RemoteRepoStatsEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for {@link RemoteRepoStatsDailyDao}.
 * Verifies that the daily DAO correctly targets the {@code remote_repo_stats_daily} table
 * and that all inherited CRUD methods work against it.
 *
 * @author husseina
 */
@Test
public class RemoteRepoStatsDailyDaoTest extends DbBaseTest {

    @Autowired
    private RemoteRepoStatsDailyDao dailyDao;

    @AfterMethod
    public void cleanup() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM remote_repo_stats_daily");
    }

    @Test
    public void insert_shouldReturnRecords_whenTimeRangeMatches() {
        // Arrange
        RemoteRepoStatsEntity entity = createEntity("repo-a", 86400000L);

        // Act
        dailyDao.insert(entity);

        // Assert
        List<RemoteRepoStatsEntity> results = dailyDao.getByTimeRange(0L, 172800000L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().repositoryKey(), "repo-a");
        assertEquals(results.getFirst().updateTime(), 86400000L);
        assertEquals(results.getFirst().totalRequestCount(), 500L);
    }

    @Test
    public void getByTimeRange_shouldReturnOnlyDailyRecords_whenHourlyTableIsEmpty() {
        // Arrange
        dailyDao.insert(createEntity("repo-a", 86400000L));

        // Act
        List<RemoteRepoStatsEntity> dailyResults = dailyDao.getByTimeRange(0L, Long.MAX_VALUE);

        // Assert
        assertEquals(dailyResults.size(), 1);
        assertTrue(dailyDao.exists("repo-a", 86400000L, "GET"));
    }

    @Test
    public void upsert_shouldUpdateExistingRecord_whenRecordExists() {
        // Arrange
        dailyDao.upsert(createEntity("repo-a", 86400000L));
        assertTrue(dailyDao.exists("repo-a", 86400000L, "GET"));

        RemoteRepoStatsEntity updated = RemoteRepoStatsEntity.builder()
                .repositoryKey("repo-a")
                .repoPackageType("npm")
                .updateTime(86400000L)
                .totalRequestCount(999L)
                .totalResponseTimeMs(30000L)
                .maxResponseTimeMs(1000L)
                .ttfbAvg(150L)
                .totalResponseSize(8192L)
                .status2xxCount(800L)
                .status3xxCount(50L)
                .status4xxCount(100L)
                .status5xxCount(49L)
                .httpMethod("GET")
                .transferRate(273L)
                .build();

        // Act
        dailyDao.upsert(updated);

        // Assert
        List<RemoteRepoStatsEntity> results = dailyDao.getByTimeRange(0L, Long.MAX_VALUE);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().totalRequestCount(), 999L);
    }

    @Test
    public void deleteByThresholdTime_shouldRemoveOldRecords_whenThresholdApplied() {
        // Arrange
        dailyDao.insert(createEntity("repo-a", 86400000L));
        dailyDao.insert(createEntity("repo-a", 172800000L));

        // Act
        dailyDao.deleteByThresholdTime(100000000L);

        // Assert
        List<RemoteRepoStatsEntity> results = dailyDao.getByTimeRange(0L, Long.MAX_VALUE);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().updateTime(), 172800000L);
    }

    @Test
    public void additiveMerge_shouldInsertNewRecord_whenRecordNotExists() {
        // Arrange
        RemoteRepoStatsEntity entity = createEntity("repo-a", 86400000L);

        // Act
        dailyDao.additiveMerge(entity);

        // Assert
        assertTrue(dailyDao.exists("repo-a", 86400000L, "GET"));
        List<RemoteRepoStatsEntity> results = dailyDao.getByTimeRange(0L, Long.MAX_VALUE);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().totalRequestCount(), 500L);
    }

    @Test
    public void additiveMerge_shouldIncrementCounts_whenRecordExists() {
        // Arrange
        dailyDao.insert(createEntity("repo-a", 86400000L));

        RemoteRepoStatsEntity delta = RemoteRepoStatsEntity.builder()
                .repositoryKey("repo-a")
                .repoPackageType("npm")
                .updateTime(86400000L)
                .totalRequestCount(100L)
                .totalResponseTimeMs(5000L)
                .maxResponseTimeMs(1200L) // higher than existing 800
                .ttfbAvg(80L)
                .totalResponseSize(2048L)
                .status2xxCount(80L)
                .status3xxCount(5L)
                .status4xxCount(10L)
                .status5xxCount(5L)
                .httpMethod("GET")
                .transferRate(409L)
                .build();

        // Act
        dailyDao.additiveMerge(delta);

        // Assert
        List<RemoteRepoStatsEntity> results = dailyDao.getByTimeRange(0L, Long.MAX_VALUE);
        assertEquals(results.size(), 1);
        RemoteRepoStatsEntity merged = results.getFirst();
        assertEquals(merged.totalRequestCount(), 600L);    // 500 + 100
        assertEquals(merged.totalResponseTimeMs(), 30000L); // 25000 + 5000
        assertEquals(merged.maxResponseTimeMs(), 1200L);   // GREATEST(800, 1200)
        assertEquals(merged.totalResponseSize(), 6144L);   // 4096 + 2048
        assertEquals(merged.status2xxCount(), 480L);       // 400 + 80
    }

    @Test
    public void exists_shouldReturnFalse_whenTableIsEmpty() {
        // Arrange — empty table

        // Act & Assert
        assertFalse(dailyDao.exists("non-existent", 0L, "GET"));
    }

    private RemoteRepoStatsEntity createEntity(String repoKey,
                                               long updateTime) {
        return RemoteRepoStatsEntity.builder()
                .repositoryKey(repoKey)
                .repoPackageType("npm")
                .updateTime(updateTime)
                .totalRequestCount(500L)
                .totalResponseTimeMs(25000L)
                .maxResponseTimeMs(800L)
                .ttfbAvg(120L)
                .totalResponseSize(4096L)
                .status2xxCount(400L)
                .status3xxCount(20L)
                .status4xxCount(50L)
                .status5xxCount(30L)
                .httpMethod("GET")
                .transferRate(163L)
                .build();
    }
}
