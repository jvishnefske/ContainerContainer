package org.artifactory.storage.db.remote.stats.dao;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.remote.stats.entity.RemoteRepoStatsStatusEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for {@link RemoteRepoStatsStatusDao}.
 * Tests CRUD operations against the {@code remote_repo_stats_status} table.
 *
 * @author husseina
 */
@Test
public class RemoteRepoStatsStatusDaoTest extends DbBaseTest {

    @Autowired
    private RemoteRepoStatsStatusDao statusDao;

    @AfterMethod
    public void cleanup() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM remote_repo_stats_status");
    }

    @Test
    public void insert_shouldPersistAllFields_whenSingleEntityInserted() {
        // Arrange
        RemoteRepoStatsStatusEntity entity = createEntity("repo-a", 1000L, 200, "GET", "hourly");

        // Act
        statusDao.insert(entity);

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().repositoryKey(), "repo-a");
        assertEquals(results.getFirst().repoPackageType(), "maven");
        assertEquals(results.getFirst().updateTime(), 1000L);
        assertEquals(results.getFirst().aggregationType(), "hourly");
        assertEquals(results.getFirst().statusCode(), 200);
        assertEquals(results.getFirst().requestCount(), 42L);
        assertEquals(results.getFirst().httpMethod(), "GET");
    }

    @Test
    public void insert_shouldPersistMultipleStatusCodes_whenDifferentCodesInserted() {
        // Arrange
        statusDao.insert(createEntity("repo-a", 1000L, 200, "GET", "hourly"));
        statusDao.insert(createEntity("repo-a", 1000L, 404, "GET", "hourly"));
        statusDao.insert(createEntity("repo-a", 1000L, 500, "HEAD", "hourly"));

        // Act
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 1500L);

        // Assert
        assertEquals(results.size(), 3);
    }

    @Test
    public void insertBatch_shouldPersistAllEntities_whenBatchProvided() {
        // Arrange
        List<RemoteRepoStatsStatusEntity> entities = List.of(
                createEntity("repo-a", 1000L, 200, "GET", "hourly"),
                createEntity("repo-a", 1000L, 404, "GET", "hourly"),
                createEntity("repo-a", 1000L, 503, "HEAD", "hourly")
        );

        // Act
        statusDao.insertBatch(entities);

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 1500L);
        assertEquals(results.size(), 3);
    }

    @Test
    public void insertBatch_shouldDoNothing_whenNullOrEmptyProvided() {
        // Act
        statusDao.insertBatch(null);
        statusDao.insertBatch(List.of());

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 0L, Long.MAX_VALUE);
        assertTrue(results.isEmpty());
    }

    @Test
    public void insert_shouldDoNothing_whenEntityIsNull() {
        // Act
        statusDao.insert(null);

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 0L, Long.MAX_VALUE);
        assertTrue(results.isEmpty());
    }

    @Test
    public void getByAggregationType_shouldFilterByType_whenMultipleTypesExist() {
        // Arrange
        statusDao.insert(createEntity("repo-a", 1000L, 200, "GET", "hourly"));
        statusDao.insert(createEntity("repo-a", 1000L, 200, "GET", "daily"));

        // Act
        List<RemoteRepoStatsStatusEntity> hourlyResults =
                statusDao.getByAggregationType("repo-a", 500L, 1500L, "hourly");
        List<RemoteRepoStatsStatusEntity> dailyResults =
                statusDao.getByAggregationType("repo-a", 500L, 1500L, "daily");

        // Assert
        assertEquals(hourlyResults.size(), 1);
        assertEquals(hourlyResults.getFirst().aggregationType(), "hourly");
        assertEquals(dailyResults.size(), 1);
        assertEquals(dailyResults.getFirst().aggregationType(), "daily");
    }

    @Test
    public void getByRepositoryKeyAndTimeRange_shouldFilterByRepoAndRange_whenMultipleRepos() {
        // Arrange
        statusDao.insert(createEntity("repo-a", 1000L, 200, "GET", "hourly"));
        statusDao.insert(createEntity("repo-b", 1000L, 200, "GET", "hourly"));
        statusDao.insert(createEntity("repo-a", 5000L, 200, "GET", "hourly"));

        // Act
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 1500L);

        // Assert
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().repositoryKey(), "repo-a");
        assertEquals(results.getFirst().updateTime(), 1000L);
    }

    @Test
    public void getByRepositoryKeyAndTimeRange_shouldReturnEmpty_whenNoRecordsInRange() {
        // Arrange
        statusDao.insert(createEntity("repo-a", 1000L, 200, "GET", "hourly"));

        // Act
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 5000L, 6000L);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    public void additiveMerge_shouldInsertNewRecord_whenRecordNotExists() {
        // Arrange
        RemoteRepoStatsStatusEntity entity = createEntity("repo-a", 1000L, 200, "GET", "hourly");

        // Act
        statusDao.additiveMerge(entity);

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().requestCount(), 42L);
    }

    @Test
    public void additiveMerge_shouldIncrementRequestCount_whenRecordExists() {
        // Arrange
        statusDao.insert(createEntity("repo-a", 1000L, 200, "GET", "hourly"));

        RemoteRepoStatsStatusEntity delta = RemoteRepoStatsStatusEntity.builder()
                .repositoryKey("repo-a")
                .repoPackageType("maven")
                .updateTime(1000L)
                .aggregationType("hourly")
                .statusCode(200)
                .requestCount(10L)
                .httpMethod("GET")
                .build();

        // Act
        statusDao.additiveMerge(delta);

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 1500L);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().requestCount(), 52L); // 42 + 10
    }

    @Test
    public void additiveMerge_shouldCreateSeparateRows_whenDifferentStatusCodes() {
        // Arrange
        statusDao.insert(createEntity("repo-a", 1000L, 200, "GET", "hourly"));

        RemoteRepoStatsStatusEntity differentStatus = RemoteRepoStatsStatusEntity.builder()
                .repositoryKey("repo-a")
                .repoPackageType("maven")
                .updateTime(1000L)
                .aggregationType("hourly")
                .statusCode(404)
                .requestCount(5L)
                .httpMethod("GET")
                .build();

        // Act
        statusDao.additiveMerge(differentStatus);

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 500L, 1500L);
        assertEquals(results.size(), 2);
    }

    @Test
    public void additiveMerge_shouldDoNothing_whenEntityIsNull() {
        // Act
        statusDao.additiveMerge(null);

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 0L, Long.MAX_VALUE);
        assertTrue(results.isEmpty());
    }

    @Test
    public void deleteByThresholdTime_shouldRemoveOlderRecords_whenThresholdApplied() {
        // Arrange
        statusDao.insert(createEntity("repo-a", 1000L, 200, "GET", "hourly"));
        statusDao.insert(createEntity("repo-a", 2000L, 200, "GET", "hourly"));
        statusDao.insert(createEntity("repo-a", 3000L, 200, "GET", "hourly"));

        // Act
        statusDao.deleteByThresholdTime(2500L);

        // Assert
        List<RemoteRepoStatsStatusEntity> results =
                statusDao.getByRepositoryKeyAndTimeRange("repo-a", 0L, Long.MAX_VALUE);
        assertEquals(results.size(), 1);
        assertEquals(results.getFirst().updateTime(), 3000L);
    }

    private RemoteRepoStatsStatusEntity createEntity(
            String repoKey, long updateTime, int statusCode, String httpMethod, String aggregationType) {
        return RemoteRepoStatsStatusEntity.builder()
                .repositoryKey(repoKey)
                .repoPackageType("maven")
                .updateTime(updateTime)
                .aggregationType(aggregationType)
                .statusCode(statusCode)
                .requestCount(42L)
                .httpMethod(httpMethod)
                .build();
    }
}
