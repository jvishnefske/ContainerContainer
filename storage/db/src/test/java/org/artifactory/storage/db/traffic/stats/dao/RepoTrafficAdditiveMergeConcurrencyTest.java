package org.artifactory.storage.db.traffic.stats.dao;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.traffic.stats.entity.RepoTrafficStatsEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;

/**
 * Concurrent integration test for
 * {@link AbstractRepoTrafficStatsDao#additiveMerge}.
 * <p>
 * Simulates the HA scenario where multiple Artifactory nodes flush
 * traffic stats concurrently, causing simultaneous additiveMerge calls
 * with the same composite key. Verifies that all counts are correctly
 * accumulated with no data loss.
 * <p>
 * On PostgreSQL this exercises the INSERT ... ON CONFLICT path;
 * on Derby (test DB) it exercises the try-INSERT/catch/UPDATE
 * fallback under real contention.
 *
 * @author georgyr
 */
@Test
public class RepoTrafficAdditiveMergeConcurrencyTest extends DbBaseTest {

    private static final int THREAD_COUNT = 4;
    private static final long FUTURE_TIMEOUT_SECONDS = 30L;
    private static final long REQUESTS_PER_FLUSH = 10L;
    private static final long BYTES_PER_FLUSH = 2048L;

    @Autowired
    private RepoTrafficHourlyDao hourlyDao;

    @Autowired
    private RepoTrafficDailyDao dailyDao;

    @AfterMethod
    public void cleanup() throws SQLException {
        jdbcHelper.executeUpdate(
                "DELETE FROM repo_traffic_hourly");
        jdbcHelper.executeUpdate(
                "DELETE FROM repo_traffic_daily");
    }

    @DataProvider(name = "daos")
    public Object[][] daos() {
        return new Object[][]{
                {hourlyDao, "repo_traffic_hourly"},
                {dailyDao, "repo_traffic_daily"}
        };
    }

    @Test(dataProvider = "daos", timeOut = 60_000)
    public void additiveMerge_shouldAccumulateAllCounts_whenConcurrentFlushesHitSameKey(
            AbstractRepoTrafficStatsDao dao, String tableName)
            throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool =
                Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                futures.add(pool.submit(() -> {
                    awaitQuietly(startGate);
                    dao.additiveMerge(createFlushEntity(
                            1000L, "virtual-repo",
                            "local-repo"));
                    return null;
                }));
            }

            startGate.countDown();

            for (Future<?> f : futures) {
                f.get(FUTURE_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<RepoTrafficStatsEntity> results =
                dao.getByDateRange(500L, 1500L);
        assertEquals(results.size(), 1,
                "Expected exactly one merged row in "
                        + tableName);
        RepoTrafficStatsEntity merged = results.getFirst();
        assertEquals(merged.requestCount(),
                THREAD_COUNT * REQUESTS_PER_FLUSH,
                "request_count should be sum of all flushes");
        assertEquals(merged.totalTransferredSize(),
                THREAD_COUNT * BYTES_PER_FLUSH,
                "total_transferred_size should be sum of"
                        + " all flushes");
    }

    @Test(dataProvider = "daos", timeOut = 60_000)
    public void additiveMerge_shouldKeepRowsSeparate_whenDifferentCompositeKeys(
            AbstractRepoTrafficStatsDao dao, String tableName)
            throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool =
                Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                final String repoKey = "local-repo-" + i;
                futures.add(pool.submit(() -> {
                    awaitQuietly(startGate);
                    dao.additiveMerge(createFlushEntity(
                            1000L, "virtual-repo", repoKey));
                    return null;
                }));
            }

            startGate.countDown();

            for (Future<?> f : futures) {
                f.get(FUTURE_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<RepoTrafficStatsEntity> results =
                dao.getByDateRange(500L, 1500L);
        assertEquals(results.size(), THREAD_COUNT,
                "Each distinct composite key should produce"
                        + " its own row in " + tableName);
        for (RepoTrafficStatsEntity row : results) {
            assertEquals(row.requestCount(), REQUESTS_PER_FLUSH);
            assertEquals(row.totalTransferredSize(),
                    BYTES_PER_FLUSH);
        }
    }

    @Test(dataProvider = "daos")
    public void additiveMerge_shouldAccumulateCorrectly_whenMultipleRoundsOfFlushes(
            AbstractRepoTrafficStatsDao dao, String tableName) {
        int rounds = 5;
        for (int r = 0; r < rounds; r++) {
            dao.additiveMerge(createFlushEntity(
                    1000L, "virtual-repo", "local-repo"));
        }

        List<RepoTrafficStatsEntity> results =
                dao.getByDateRange(500L, 1500L);
        assertEquals(results.size(), 1);
        RepoTrafficStatsEntity merged = results.getFirst();
        assertEquals(merged.requestCount(),
                rounds * REQUESTS_PER_FLUSH);
        assertEquals(merged.totalTransferredSize(),
                rounds * BYTES_PER_FLUSH);
    }

    private static RepoTrafficStatsEntity createFlushEntity(
            long updateTime, String originRepoKey,
            String repoKey) {
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
                .requestCount(REQUESTS_PER_FLUSH)
                .totalTransferredSize(BYTES_PER_FLUSH)
                .build();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
