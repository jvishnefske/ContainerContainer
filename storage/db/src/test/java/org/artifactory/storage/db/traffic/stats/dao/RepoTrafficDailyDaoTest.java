package org.artifactory.storage.db.traffic.stats.dao;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.traffic.stats.entity.RepoTrafficStatsEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for {@link RepoTrafficDailyDao}.
 * Tests all CRUD methods inherited from {@link AbstractRepoTrafficStatsDao}
 * against the {@code repo_traffic_daily} table.
 *
 * @author georgyr
 */
@Test
public class RepoTrafficDailyDaoTest extends DbBaseTest {

    @Autowired
    private RepoTrafficDailyDao dailyDao;

    @AfterMethod
    public void cleanup() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM repo_traffic_daily");
    }

    @Test
    public void insert_shouldReturnAllFields_whenGetByDateRange() {
        // Arrange
        RepoTrafficStatsEntity entity = createEntity(
                "virtual-repo", "local-repo", 1000L);

        // Act
        dailyDao.insert(entity);

        // Assert
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByDateRange(500L, 1500L);
        assertEquals(results.size(), 1);
        RepoTrafficStatsEntity result = results.getFirst();
        assertEquals(result.updateTime(), 1000L);
        assertEquals(result.action(), "DOWNLOAD");
        assertEquals(result.actor(), "USER");
        assertEquals(result.redirectTarget(), "DIRECT");
        assertEquals(result.originRepoKey(), "virtual-repo");
        assertEquals(result.originRepoType(), "virtual");
        assertEquals(result.originPackageType(), "maven");
        assertEquals(result.repoKey(), "local-repo");
        assertEquals(result.repoType(), "local");
        assertEquals(result.packageType(), "maven");
        assertEquals(result.requestCount(), 5L);
        assertEquals(result.totalTransferredSize(), 1024L);
    }

    @Test
    public void insert_shouldDoNothing_whenEntityIsNull() {
        // Act
        dailyDao.insert(null);

        // Assert
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByDateRange(0L, Long.MAX_VALUE);
        assertTrue(results.isEmpty());
    }

    @Test
    public void additiveMerge_shouldInsertNewRecord_whenRecordNotExists() {
        // Arrange
        RepoTrafficStatsEntity entity = createEntity(
                "virtual-repo", "local-repo", 1000L);

        // Act
        dailyDao.additiveMerge(entity);

        // Assert
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByDateRange(500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().requestCount(), 5L);
        assertEquals(results.getFirst().totalTransferredSize(), 1024L);
    }

    @Test
    public void additiveMerge_shouldIncrementCounts_whenRecordExists() {
        // Arrange
        dailyDao.insert(createEntity(
                "virtual-repo", "local-repo", 1000L));

        RepoTrafficStatsEntity delta = RepoTrafficStatsEntity.builder()
                .updateTime(1000L)
                .action("DOWNLOAD")
                .actor("USER")
                .redirectTarget("DIRECT")
                .originRepoKey("virtual-repo")
                .originRepoType("virtual")
                .originPackageType("maven")
                .repoKey("local-repo")
                .repoType("local")
                .packageType("maven")
                .requestCount(3L)
                .totalTransferredSize(512L)
                .build();

        // Act
        dailyDao.additiveMerge(delta);

        // Assert
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByDateRange(500L, 1500L);
        assertEquals(results.size(), 1);
        RepoTrafficStatsEntity merged = results.getFirst();
        assertEquals(merged.requestCount(), 8L);
        assertEquals(merged.totalTransferredSize(), 1536L);
    }

    @Test
    public void additiveMerge_shouldDoNothing_whenEntityIsNull() {
        // Act
        dailyDao.additiveMerge(null);

        // Assert
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByDateRange(0L, Long.MAX_VALUE);
        assertTrue(results.isEmpty());
    }

    @Test
    public void getByOriginRepoKeyAndDateRange_shouldReturnMatchingRecords_whenMultipleInserted() {
        // Arrange
        dailyDao.insert(createEntity(
                "virtual-a", "local-a", 1000L));
        dailyDao.insert(createEntity(
                "virtual-a", "local-b", 2000L));
        dailyDao.insert(createEntity(
                "virtual-b", "local-c", 1500L));

        // Act
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByOriginRepoKeyAndDateRange(
                        "virtual-a", 500L, 2500L);

        // Assert -- ordered by update_time DESC
        assertEquals(results.size(), 2);
        assertEquals(results.get(0).updateTime(), 2000L);
        assertEquals(results.get(1).updateTime(), 1000L);
    }

    @Test
    public void getByOriginRepoKeyAndDateRange_shouldFilterByTimeRange_whenPartialMatch() {
        // Arrange
        dailyDao.insert(createEntity(
                "virtual-a", "local-a", 1000L));
        dailyDao.insert(createEntity(
                "virtual-a", "local-b", 5000L));

        // Act -- only the first record falls in range
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByOriginRepoKeyAndDateRange(
                        "virtual-a", 500L, 1500L);

        // Assert
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().updateTime(), 1000L);
    }

    @Test
    public void getByActorAndDateRange_shouldReturnMatchingRecords_whenFilteredByActor() {
        // Arrange
        dailyDao.insert(createEntity(
                "virtual-a", "local-a", 1000L));
        dailyDao.insert(createEntityWithActor(
                "virtual-a", "local-b", 2000L, "XRAY"));

        // Act
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByActorAndDateRange("USER", 500L, 2500L);

        // Assert
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().actor(), "USER");
        assertEquals(results.getFirst().updateTime(), 1000L);
    }

    @Test
    public void getByResolvedRepoKey_shouldReturnMatchingRecords_whenFilteredByRepoKey() {
        // Arrange
        dailyDao.insert(createEntity(
                "virtual-a", "local-a", 1000L));
        dailyDao.insert(createEntity(
                "virtual-a", "local-b", 2000L));

        // Act
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByResolvedRepoKey(
                        "local-a", 500L, 2500L);

        // Assert
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().repoKey(), "local-a");
    }

    @Test
    public void getByDateRange_shouldReturnEmpty_whenNoRecordsInRange() {
        // Arrange
        dailyDao.insert(createEntity(
                "virtual-a", "local-a", 1000L));

        // Act
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByDateRange(5000L, 6000L);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    public void deleteByThresholdTime_shouldRemoveOlderRecords_whenThresholdApplied() {
        // Arrange
        dailyDao.insert(createEntity(
                "virtual-a", "local-a", 1000L));
        dailyDao.insert(createEntity(
                "virtual-a", "local-b", 2000L));
        dailyDao.insert(createEntity(
                "virtual-a", "local-c", 3000L));

        // Act -- delete records older than 2500
        dailyDao.deleteByThresholdTime(2500L);

        // Assert
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByDateRange(0L, 5000L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().updateTime(), 3000L);
    }

    @Test
    public void insert_directHitScenario_shouldStoreOriginEqualsRepo() {
        // Arrange -- direct hit: origin = resolved
        RepoTrafficStatsEntity entity = RepoTrafficStatsEntity.builder()
                .updateTime(1000L)
                .action("DOWNLOAD")
                .actor("USER")
                .redirectTarget("DIRECT")
                .originRepoKey("local-repo")
                .originRepoType("local")
                .originPackageType("maven")
                .repoKey("local-repo")
                .repoType("local")
                .packageType("maven")
                .requestCount(1L)
                .totalTransferredSize(256L)
                .build();

        // Act
        dailyDao.insert(entity);

        // Assert
        List<RepoTrafficStatsEntity> results =
                dailyDao.getByOriginRepoKeyAndDateRange(
                        "local-repo", 500L, 1500L);
        assertEquals(results.size(), 1);
        RepoTrafficStatsEntity result = results.getFirst();
        assertEquals(result.originRepoKey(), "local-repo");
        assertEquals(result.repoKey(), "local-repo");
        assertEquals(result.originRepoType(), "local");
        assertEquals(result.repoType(), "local");
    }

    private RepoTrafficStatsEntity createEntity(
            String originRepoKey, String repoKey, long updateTime) {
        return RepoTrafficStatsEntity.builder()
                .updateTime(updateTime)
                .action("DOWNLOAD")
                .actor("USER")
                .redirectTarget("DIRECT")
                .originRepoKey(originRepoKey)
                .originRepoType("virtual")
                .originPackageType("maven")
                .repoKey(repoKey)
                .repoType("local")
                .packageType("maven")
                .requestCount(5L)
                .totalTransferredSize(1024L)
                .build();
    }

    private RepoTrafficStatsEntity createEntityWithActor(
            String originRepoKey, String repoKey,
            long updateTime, String actor) {
        return RepoTrafficStatsEntity.builder()
                .updateTime(updateTime)
                .action("DOWNLOAD")
                .actor(actor)
                .redirectTarget("DIRECT")
                .originRepoKey(originRepoKey)
                .originRepoType("virtual")
                .originPackageType("maven")
                .repoKey(repoKey)
                .repoType("local")
                .packageType("maven")
                .requestCount(5L)
                .totalTransferredSize(1024L)
                .build();
    }
}
