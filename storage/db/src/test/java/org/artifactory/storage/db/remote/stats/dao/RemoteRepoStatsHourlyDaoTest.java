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
 * Integration tests for {@link RemoteRepoStatsHourlyDao}.
 * Tests all CRUD methods inherited from {@link AbstractRemoteRepoStatsDao}
 * against the {@code remote_repo_stats_hourly} table.
 *
 * @author husseina
 */
@Test
public class RemoteRepoStatsHourlyDaoTest extends DbBaseTest {

    @Autowired
    private RemoteRepoStatsHourlyDao hourlyDao;

    @AfterMethod
    public void cleanup() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM remote_repo_stats_hourly");
    }

    @Test
    public void insert_shouldReturnAllFields_whenGetByTimeRange() {
        // Arrange
        RemoteRepoStatsEntity entity = createEntity("repo-a", 1000L);

        // Act
        hourlyDao.insert(entity);

        // Assert
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().repositoryKey(), "repo-a");
        assertEquals(results.getFirst().repoPackageType(), "maven");
        assertEquals(results.getFirst().updateTime(), 1000L);
        assertEquals(results.getFirst().totalRequestCount(), 100L);
        assertEquals(results.getFirst().totalResponseTimeMs(), 5000L);
        assertEquals(results.getFirst().maxResponseTimeMs(), 200L);
        assertEquals(results.getFirst().ttfbAvg(), 50L);
        assertEquals(results.getFirst().totalResponseSize(), 1024L);
        assertEquals(results.getFirst().status2xxCount(), 80L);
        assertEquals(results.getFirst().status3xxCount(), 5L);
        assertEquals(results.getFirst().status4xxCount(), 10L);
        assertEquals(results.getFirst().status5xxCount(), 5L);
        assertEquals(results.getFirst().httpMethod(), "GET");
        assertEquals(results.getFirst().transferRate(), 204L);
    }

    @Test
    public void getByRepositoryKeyAndTimeRange_shouldReturnMatchingRecords_whenMultipleInserted() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L));
        hourlyDao.insert(createEntity("repo-a", 2000L));
        hourlyDao.insert(createEntity("repo-b", 1500L));

        // Act
        List<RemoteRepoStatsEntity> results =
                hourlyDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 2500L);

        // Assert — ordered by update_time DESC
        assertEquals(results.size(), 2);
        assertEquals(results.get(0).updateTime(), 2000L);
        assertEquals(results.get(1).updateTime(), 1000L);
    }

    @Test
    public void getByRepositoryKeyAndTimeRange_shouldFilterByTimeRange_whenPartialMatch() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L));
        hourlyDao.insert(createEntity("repo-a", 5000L));

        // Act — only the first record falls in range
        List<RemoteRepoStatsEntity> results =
                hourlyDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 1500L);

        // Assert
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().updateTime(), 1000L);
    }

    @Test
    public void getByTimeRange_shouldReturnEmpty_whenNoRecordsInRange() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L));

        // Act
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(5000L, 6000L);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    public void exists_shouldReturnTrue_whenRecordExists() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L));

        // Act & Assert
        assertTrue(hourlyDao.exists("repo-a", 1000L, "GET"));
    }

    @Test
    public void exists_shouldReturnFalse_whenRecordNotExists() {
        // Act & Assert
        assertFalse(hourlyDao.exists("repo-a", 1000L, "GET"));
    }

    @Test
    public void update_shouldModifyExistingRecord_whenRecordExists() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L));

        RemoteRepoStatsEntity updated = RemoteRepoStatsEntity.builder()
                .repositoryKey("repo-a")
                .repoPackageType("maven")
                .updateTime(1000L)
                .totalRequestCount(999L)
                .totalResponseTimeMs(8000L)
                .maxResponseTimeMs(500L)
                .ttfbAvg(100L)
                .totalResponseSize(2048L)
                .status2xxCount(900L)
                .status3xxCount(10L)
                .status4xxCount(50L)
                .status5xxCount(39L)
                .httpMethod("GET")
                .transferRate(256L)
                .build();

        // Act
        hourlyDao.update(updated);

        // Assert
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().totalRequestCount(), 999L);
        assertEquals(results.getFirst().totalResponseTimeMs(), 8000L);
        assertEquals(results.getFirst().maxResponseTimeMs(), 500L);
        assertEquals(results.getFirst().status2xxCount(), 900L);
    }

    @Test
    public void upsert_shouldInsertNewRecord_whenRecordNotExists() {
        // Arrange
        RemoteRepoStatsEntity entity = createEntity("repo-a", 1000L);

        // Act
        hourlyDao.upsert(entity);

        // Assert
        assertTrue(hourlyDao.exists("repo-a", 1000L, "GET"));
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().totalRequestCount(), 100L);
    }

    @Test
    public void upsert_shouldUpdateExistingRecord_whenRecordExists() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L));

        RemoteRepoStatsEntity updated = RemoteRepoStatsEntity.builder()
                .repositoryKey("repo-a")
                .repoPackageType("maven")
                .updateTime(1000L)
                .totalRequestCount(555L)
                .totalResponseTimeMs(5000L)
                .maxResponseTimeMs(200L)
                .ttfbAvg(50L)
                .totalResponseSize(1024L)
                .status2xxCount(500L)
                .status3xxCount(5L)
                .status4xxCount(25L)
                .status5xxCount(25L)
                .httpMethod("GET")
                .transferRate(204L)
                .build();

        // Act
        hourlyDao.upsert(updated);

        // Assert
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().totalRequestCount(), 555L);
    }

    @Test
    public void deleteByThresholdTime_shouldRemoveOlderRecords_whenThresholdApplied() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L));
        hourlyDao.insert(createEntity("repo-a", 2000L));
        hourlyDao.insert(createEntity("repo-a", 3000L));

        // Act — delete records older than 2500
        hourlyDao.deleteByThresholdTime(2500L);

        // Assert
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(0L, 5000L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().updateTime(), 3000L);
    }

    @Test
    public void additiveMerge_shouldInsertNewRecord_whenRecordNotExists() {
        // Arrange
        RemoteRepoStatsEntity entity = createEntity("repo-a", 1000L);

        // Act
        hourlyDao.additiveMerge(entity);

        // Assert
        assertTrue(hourlyDao.exists("repo-a", 1000L, "GET"));
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().totalRequestCount(), 100L);
        assertEquals(results.getFirst().maxResponseTimeMs(), 200L);
    }

    @Test
    public void additiveMerge_shouldIncrementCounts_whenRecordExists() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L));

        RemoteRepoStatsEntity delta = RemoteRepoStatsEntity.builder()
                .repositoryKey("repo-a")
                .repoPackageType("maven")
                .updateTime(1000L)
                .totalRequestCount(50L)
                .totalResponseTimeMs(2000L)
                .maxResponseTimeMs(300L)  // higher than existing 200
                .ttfbAvg(60L)
                .totalResponseSize(512L)
                .status2xxCount(40L)
                .status3xxCount(3L)
                .status4xxCount(5L)
                .status5xxCount(2L)
                .httpMethod("GET")
                .transferRate(256L)
                .build();

        // Act
        hourlyDao.additiveMerge(delta);

        // Assert
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(500L, 1500L);
        assertEquals(results.size(), 1);
        RemoteRepoStatsEntity merged = results.getFirst();
        assertEquals(merged.totalRequestCount(), 150L);   // 100 + 50
        assertEquals(merged.totalResponseTimeMs(), 7000L); // 5000 + 2000
        assertEquals(merged.maxResponseTimeMs(), 300L);    // GREATEST(200, 300)
        assertEquals(merged.totalResponseSize(), 1536L);   // 1024 + 512
        assertEquals(merged.status2xxCount(), 120L);       // 80 + 40
        assertEquals(merged.status3xxCount(), 8L);         // 5 + 3
        assertEquals(merged.status4xxCount(), 15L);        // 10 + 5
        assertEquals(merged.status5xxCount(), 7L);         // 5 + 2
    }

    @Test
    public void additiveMerge_shouldKeepExistingMax_whenNewMaxIsLower() {
        // Arrange
        hourlyDao.insert(createEntity("repo-a", 1000L)); // max = 200

        RemoteRepoStatsEntity delta = RemoteRepoStatsEntity.builder()
                .repositoryKey("repo-a")
                .repoPackageType("maven")
                .updateTime(1000L)
                .totalRequestCount(10L)
                .totalResponseTimeMs(100L)
                .maxResponseTimeMs(50L)  // lower than existing 200
                .ttfbAvg(30L)
                .totalResponseSize(100L)
                .status2xxCount(10L)
                .status3xxCount(0L)
                .status4xxCount(0L)
                .status5xxCount(0L)
                .httpMethod("GET")
                .transferRate(1000L)
                .build();

        // Act
        hourlyDao.additiveMerge(delta);

        // Assert
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(500L, 1500L);
        assertEquals(results.getFirst().maxResponseTimeMs(), 200L); // kept existing
    }

    @Test
    public void additiveMerge_shouldDoNothing_whenEntityIsNull() {
        // Act
        hourlyDao.additiveMerge(null);

        // Assert
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(0L, Long.MAX_VALUE);
        assertTrue(results.isEmpty());
    }

    @Test
    public void insert_shouldDoNothing_whenEntityIsNull() {
        // Act
        hourlyDao.insert(null);

        // Assert
        List<RemoteRepoStatsEntity> results = hourlyDao.getByTimeRange(0L, Long.MAX_VALUE);
        assertTrue(results.isEmpty());
    }

    private RemoteRepoStatsEntity createEntity(String repoKey,
                                               long updateTime) {
        return RemoteRepoStatsEntity.builder()
                .repositoryKey(repoKey)
                .repoPackageType("maven")
                .updateTime(updateTime)
                .totalRequestCount(100L)
                .totalResponseTimeMs(5000L)
                .maxResponseTimeMs(200L)
                .ttfbAvg(50L)
                .totalResponseSize(1024L)
                .status2xxCount(80L)
                .status3xxCount(5L)
                .status4xxCount(10L)
                .status5xxCount(5L)
                .httpMethod("GET")
                .transferRate(204L)
                .build();
    }
}
