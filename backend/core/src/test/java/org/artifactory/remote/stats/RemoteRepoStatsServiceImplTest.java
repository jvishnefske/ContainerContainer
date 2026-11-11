package org.artifactory.remote.stats;

import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.remote.stats.dao.RemoteRepoStatsDailyDao;
import org.artifactory.storage.db.remote.stats.dao.RemoteRepoStatsHourlyDao;
import org.artifactory.storage.db.remote.stats.dao.RemoteRepoStatsStatusDao;
import org.artifactory.storage.db.remote.stats.entity.RemoteRepoStatsEntity;
import org.artifactory.storage.db.remote.stats.entity.RemoteRepoStatsStatusEntity;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Unit tests for {@link RemoteRepoStatsServiceImpl}.
 * Uses Mockito to mock the DAO layer and TaskService, following the
 * {@code TrafficSummaryServiceImplTest} pattern.
 *
 * @author husseina
 */
class RemoteRepoStatsServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private TaskService taskService;
    @Mock
    private RemoteRepoStatsHourlyDao hourlyDao;
    @Mock
    private RemoteRepoStatsDailyDao dailyDao;
    @Mock
    private RemoteRepoStatsStatusDao statusDao;

    @Captor
    private ArgumentCaptor<RemoteRepoStatsEntity> statsEntityCaptor;
    @Captor
    private ArgumentCaptor<RemoteRepoStatsStatusEntity> statusEntityCaptor;

    private RemoteRepoStatsServiceImpl service;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void beforeEach() {
        autoCloseable = openMocks(this);
        doReturn("task-id-1").when(taskService).startTask(any(), anyBoolean());
        doNothing().when(hourlyDao).additiveMerge(any());
        doNothing().when(dailyDao).additiveMerge(any());
        doNothing().when(statusDao).additiveMerge(any());
        doNothing().when(hourlyDao).deleteByThresholdTime(anyLong());
        doNothing().when(dailyDao).deleteByThresholdTime(anyLong());
        doNothing().when(statusDao).deleteByThresholdTime(anyLong());
        service = new RemoteRepoStatsServiceImpl(taskService, hourlyDao, dailyDao, statusDao);
    }

    @AfterEach
    void afterEach() throws Exception {
        autoCloseable.close();
    }

    // ---- Constructor / setup ----

    @Test
    void constructor_shouldScheduleCollectionJob_whenServiceIsCreated() {
        // Arrange — service already created in @BeforeEach

        // Act — constructor was already called

        // Assert
        verify(taskService).startTask(any(), anyBoolean());
    }

    // ---- recordRequest aggregation ----

    @Test
    void recordRequest_shouldAggregateSingleRequest_whenCalled() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 200, 150L, 50L, 1024L);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        RemoteRepoStatsEntity hourly = statsEntityCaptor.getValue();
        assertEquals("repo-a", hourly.repositoryKey());
        assertEquals("maven", hourly.repoPackageType());
        assertEquals("GET", hourly.httpMethod());
        assertEquals(1L, hourly.totalRequestCount());
        assertEquals(150L, hourly.totalResponseTimeMs());
        assertEquals(150L, hourly.maxResponseTimeMs());
        assertEquals(50L, hourly.ttfbAvg());
        assertEquals(1024L, hourly.totalResponseSize());
        assertEquals(1L, hourly.status2xxCount());
        assertEquals(0L, hourly.status3xxCount());
        assertEquals(0L, hourly.status4xxCount());
        assertEquals(0L, hourly.status5xxCount());
        // transferRate = 1024 * 1000 / 150 = 6826
        assertEquals(6826L, hourly.transferRate());
    }

    @Test
    void recordRequest_shouldAggregateMultipleRequests_whenSameKey() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 40L, 500L);
        service.recordRequest("repo-a", "maven", "GET", 200, 200L, 60L, 700L);
        service.recordRequest("repo-a", "maven", "GET", 500, 300L, 80L, 0L);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        RemoteRepoStatsEntity entity = statsEntityCaptor.getValue();
        assertEquals("GET", entity.httpMethod());
        assertEquals(3L, entity.totalRequestCount());
        assertEquals(600L, entity.totalResponseTimeMs());  // 100 + 200 + 300
        assertEquals(300L, entity.maxResponseTimeMs());     // max of 100, 200, 300
        assertEquals(60L, entity.ttfbAvg());                // (40+60+80)/3
        assertEquals(1200L, entity.totalResponseSize());    // 500 + 700 + 0
        assertEquals(2L, entity.status2xxCount());
        assertEquals(0L, entity.status3xxCount());
        assertEquals(0L, entity.status4xxCount());
        assertEquals(1L, entity.status5xxCount());
        // transferRate = 1200 * 1000 / 600 = 2000
        assertEquals(2000L, entity.transferRate());
    }

    // ---- Status code categorization ----

    @Test
    void recordRequest_shouldCategorize2xxStatusCodes_whenStatusIs2xx() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 200, 10L, 5L, 100L);
        service.recordRequest("repo-a", "maven", "GET", 201, 10L, 5L, 100L);
        service.recordRequest("repo-a", "maven", "GET", 204, 10L, 5L, 100L);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        assertEquals(3L, statsEntityCaptor.getValue().status2xxCount());
        assertEquals(0L, statsEntityCaptor.getValue().status3xxCount());
    }

    @Test
    void recordRequest_shouldCategorize3xxStatusCodes_whenStatusIs3xx() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 301, 10L, 5L, 0L);
        service.recordRequest("repo-a", "maven", "GET", 304, 10L, 5L, 0L);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        assertEquals(0L, statsEntityCaptor.getValue().status2xxCount());
        assertEquals(2L, statsEntityCaptor.getValue().status3xxCount());
    }

    @Test
    void recordRequest_shouldCategorize4xxStatusCodes_whenStatusIs4xx() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 400, 10L, 5L, 0L);
        service.recordRequest("repo-a", "maven", "GET", 404, 10L, 5L, 0L);
        service.recordRequest("repo-a", "maven", "GET", 403, 10L, 5L, 0L);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        assertEquals(3L, statsEntityCaptor.getValue().status4xxCount());
    }

    @Test
    void recordRequest_shouldCategorize5xxStatusCodes_whenStatusIs5xx() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 500, 10L, 5L, 0L);
        service.recordRequest("repo-a", "maven", "GET", 502, 10L, 5L, 0L);
        service.recordRequest("repo-a", "maven", "GET", 503, 10L, 5L, 0L);
        service.recordRequest("repo-a", "maven", "GET", 504, 10L, 5L, 0L);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        assertEquals(4L, statsEntityCaptor.getValue().status5xxCount());
    }

    @Test
    void recordRequest_shouldNotCountStatus_whenStatusCodeOutsideKnownRanges() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 100, 10L, 5L, 0L);
        service.recordRequest("repo-a", "maven", "GET", 199, 10L, 5L, 0L);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        RemoteRepoStatsEntity entity = statsEntityCaptor.getValue();
        assertEquals(2L, entity.totalRequestCount());
        assertEquals(0L, entity.status2xxCount());
        assertEquals(0L, entity.status3xxCount());
        assertEquals(0L, entity.status4xxCount());
        assertEquals(0L, entity.status5xxCount());
    }

    // ---- Flush behavior ----

    @Test
    void flushStats_shouldWriteToHourlyAndDailyDaos_whenAccumulatorNotEmpty() {
        // Arrange
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 50L, 1024L);

        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(any(RemoteRepoStatsEntity.class));
        verify(dailyDao).additiveMerge(any(RemoteRepoStatsEntity.class));
    }

    @Test
    void flushStats_shouldWriteStatusDetails_whenMultipleMethodsRecorded() {
        // Arrange
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 50L, 1024L);
        service.recordRequest("repo-a", "maven", "HEAD", 404, 50L, 20L, 0L);

        // Act
        service.flushStats();

        // Assert — 2 accumulator keys (GET, HEAD) each with 1 status code
        // * 2 aggregation types (hourly + daily) = 4 calls
        verify(statusDao, times(4)).additiveMerge(statusEntityCaptor.capture());
        List<RemoteRepoStatsStatusEntity> captured = statusEntityCaptor.getAllValues();

        long hourlyCount = captured.stream()
                .filter(e -> "hourly".equals(e.aggregationType())).count();
        assertEquals(2L, hourlyCount);

        long dailyCount = captured.stream()
                .filter(e -> "daily".equals(e.aggregationType())).count();
        assertEquals(2L, dailyCount);
    }

    @Test
    void flushStats_shouldClearAccumulator_whenFlushCompletes() {
        // Arrange
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 50L, 1024L);

        // Act
        service.flushStats();
        service.flushStats(); // second flush should have nothing new

        // Assert — additiveMerge should only be called once (from first flush)
        verify(hourlyDao, times(1)).additiveMerge(any(RemoteRepoStatsEntity.class));
    }

    @Test
    void flushStats_shouldNotWriteStats_whenAccumulatorIsEmpty() {
        // Arrange — no requests recorded

        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao, never()).additiveMerge(any());
        verify(dailyDao, never()).additiveMerge(any());
        verify(statusDao, never()).additiveMerge(any());
    }

    @Test
    void flushStats_shouldCallRetentionCleanup_whenAccumulatorNotEmpty() {
        // Arrange
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 50L, 1024L);

        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao).deleteByThresholdTime(anyLong());
        verify(dailyDao).deleteByThresholdTime(anyLong());
        verify(statusDao).deleteByThresholdTime(anyLong());
    }

    @Test
    void flushStats_shouldCallRetentionCleanup_whenAccumulatorIsEmpty() {
        // Arrange — no requests recorded

        // Act
        service.flushStats();

        // Assert — cleanup should still run even when no stats to flush
        verify(hourlyDao).deleteByThresholdTime(anyLong());
        verify(dailyDao).deleteByThresholdTime(anyLong());
        verify(statusDao).deleteByThresholdTime(anyLong());
    }

    // ---- Multiple repos ----

    @Test
    void recordRequest_shouldAggregateSeparately_whenDifferentRepos() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 50L, 500L);
        service.recordRequest("repo-b", "npm", "GET", 200, 200L, 80L, 700L);
        service.flushStats();

        // Assert
        verify(hourlyDao, times(2)).additiveMerge(statsEntityCaptor.capture());
        List<RemoteRepoStatsEntity> captured = statsEntityCaptor.getAllValues();
        assertEquals(2, captured.size());

        RemoteRepoStatsEntity repoA = captured.stream()
                .filter(e -> "repo-a".equals(e.repositoryKey()))
                .findFirst().orElseThrow();
        assertEquals(1L, repoA.totalRequestCount());
        assertEquals(100L, repoA.totalResponseTimeMs());
        assertEquals("maven", repoA.repoPackageType());
        assertEquals("GET", repoA.httpMethod());

        RemoteRepoStatsEntity repoB = captured.stream()
                .filter(e -> "repo-b".equals(e.repositoryKey()))
                .findFirst().orElseThrow();
        assertEquals(1L, repoB.totalRequestCount());
        assertEquals(200L, repoB.totalResponseTimeMs());
        assertEquals("npm", repoB.repoPackageType());
        assertEquals("GET", repoB.httpMethod());
    }

    // ---- Max response time tracking ----

    @Test
    void recordRequest_shouldTrackHighestMaxResponseTime_whenMultipleRequests() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 200, 50L, 10L, 100L);
        service.recordRequest("repo-a", "maven", "GET", 200, 500L, 100L, 200L);
        service.recordRequest("repo-a", "maven", "GET", 200, 150L, 30L, 150L);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        assertEquals(500L, statsEntityCaptor.getValue().maxResponseTimeMs());
    }

    // ---- Hour/day bucket computation ----

    @Test
    void computeHourBucket_shouldTruncateToHour_whenGivenMidHourInstant() {
        // Arrange — 2026-02-10T14:35:22Z
        Instant instant = ZonedDateTime.of(2026, 2, 10, 14, 35, 22, 0, ZoneOffset.UTC)
                .toInstant();

        // Act
        long bucket = RemoteRepoStatsServiceImpl.computeHourBucket(instant);

        // Assert — should truncate to 2026-02-10T14:00:00Z
        long expected = ZonedDateTime.of(2026, 2, 10, 14, 0, 0, 0, ZoneOffset.UTC)
                .toInstant().toEpochMilli();
        assertEquals(expected, bucket);
    }

    @Test
    void computeHourBucket_shouldReturnSameValue_whenAtExactHour() {
        // Arrange
        Instant instant = ZonedDateTime.of(2026, 2, 10, 14, 0, 0, 0, ZoneOffset.UTC)
                .toInstant();

        // Act
        long bucket = RemoteRepoStatsServiceImpl.computeHourBucket(instant);

        // Assert
        assertEquals(instant.toEpochMilli(), bucket);
    }

    @Test
    void computeDayBucket_shouldTruncateToDay_whenGivenMidDayTimestamp() {
        // Arrange — 2026-02-10T14:00:00Z
        long hourBucket = ZonedDateTime.of(2026, 2, 10, 14, 0, 0, 0, ZoneOffset.UTC)
                .toInstant().toEpochMilli();

        // Act
        long dayBucket = RemoteRepoStatsServiceImpl.computeDayBucket(hourBucket);

        // Assert — should truncate to 2026-02-10T00:00:00Z
        long expected = ZonedDateTime.of(2026, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
                .toInstant().toEpochMilli();
        assertEquals(expected, dayBucket);
    }

    @Test
    void computeDayBucket_shouldReturnSameValue_whenAtMidnight() {
        // Arrange
        long midnight = ZonedDateTime.of(2026, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
                .toInstant().toEpochMilli();

        // Act & Assert
        assertEquals(midnight, RemoteRepoStatsServiceImpl.computeDayBucket(midnight));
    }

    @Test
    void flushStats_shouldWriteCorrectBuckets_whenHourlyAndDailyFlushed() {
        // Arrange
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 50L, 1024L);

        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        RemoteRepoStatsEntity hourlyEntity = statsEntityCaptor.getValue();

        verify(dailyDao).additiveMerge(statsEntityCaptor.capture());
        RemoteRepoStatsEntity dailyEntity = statsEntityCaptor.getValue();

        // Hourly should be truncated to the hour
        Instant hourInstant = Instant.ofEpochMilli(hourlyEntity.updateTime());
        ZonedDateTime hourZdt = ZonedDateTime.ofInstant(hourInstant, ZoneOffset.UTC);
        assertEquals(0, hourZdt.getMinute());
        assertEquals(0, hourZdt.getSecond());

        // Daily should be truncated to midnight
        Instant dayInstant = Instant.ofEpochMilli(dailyEntity.updateTime());
        ZonedDateTime dayZdt = ZonedDateTime.ofInstant(dayInstant, ZoneOffset.UTC);
        assertEquals(0, dayZdt.getHour());
        assertEquals(0, dayZdt.getMinute());
        assertEquals(0, dayZdt.getSecond());
    }

    // ---- Thread safety ----

    @Test
    void recordRequest_shouldBeThreadSafe_whenConcurrentRequests() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        service.recordRequest("repo-a", "maven", "GET", 200,
                                10L, 5L, 100L);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        RemoteRepoStatsEntity entity = statsEntityCaptor.getValue();
        long expectedTotal = (long) threadCount * requestsPerThread;
        assertEquals(expectedTotal, entity.totalRequestCount());
        assertEquals(expectedTotal * 10L, entity.totalResponseTimeMs());
        assertEquals(10L, entity.maxResponseTimeMs());
        assertEquals(expectedTotal * 100L, entity.totalResponseSize());
        assertEquals(expectedTotal, entity.status2xxCount());
    }

    // ---- Status details ----

    @Test
    void flushStats_shouldWritePerStatusCodeAndMethodDetails_whenMultipleStatusCodes() {
        // Arrange
        service.recordRequest("repo-a", "maven", "GET", 200, 10L, 5L, 100L);
        service.recordRequest("repo-a", "maven", "GET", 200, 10L, 5L, 100L);
        service.recordRequest("repo-a", "maven", "HEAD", 200, 10L, 5L, 0L);
        service.recordRequest("repo-a", "maven", "GET", 404, 10L, 5L, 0L);

        // Act
        service.flushStats();

        // Assert — GET accumulator: 2 status codes (200, 404) * 2 agg types = 4
        // HEAD accumulator: 1 status code (200) * 2 agg types = 2
        // Total = 6
        verify(statusDao, times(6)).additiveMerge(statusEntityCaptor.capture());
        List<RemoteRepoStatsStatusEntity> all = statusEntityCaptor.getAllValues();

        RemoteRepoStatsStatusEntity hourly200Get = all.stream()
                .filter(e -> "hourly".equals(e.aggregationType()))
                .filter(e -> e.statusCode() == 200
                        && "GET".equals(e.httpMethod()))
                .findFirst().orElseThrow();
        assertEquals(2L, hourly200Get.requestCount());

        RemoteRepoStatsStatusEntity hourly200Head = all.stream()
                .filter(e -> "hourly".equals(e.aggregationType()))
                .filter(e -> e.statusCode() == 200
                        && "HEAD".equals(e.httpMethod()))
                .findFirst().orElseThrow();
        assertEquals(1L, hourly200Head.requestCount());

        RemoteRepoStatsStatusEntity hourly404Get = all.stream()
                .filter(e -> "hourly".equals(e.aggregationType()))
                .filter(e -> e.statusCode() == 404
                        && "GET".equals(e.httpMethod()))
                .findFirst().orElseThrow();
        assertEquals(1L, hourly404Get.requestCount());
    }

    // ---- TTFB average ----

    @Test
    void recordRequest_shouldComputeCorrectTtfbAverage_whenMultipleRequests() {
        // Arrange — service ready

        // Act
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 30L, 100L);
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 60L, 100L);
        service.recordRequest("repo-a", "maven", "GET", 200, 100L, 90L, 100L);
        service.flushStats();

        // Assert — ttfbAvg = (30+60+90)/3 = 60
        verify(hourlyDao).additiveMerge(statsEntityCaptor.capture());
        assertEquals(60L, statsEntityCaptor.getValue().ttfbAvg());
    }

    @Test
    void flushStats_shouldNotCreateEntity_whenNoRequestsRecorded() {
        // Arrange — no requests recorded

        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao, never()).additiveMerge(any());
    }

    // ---- queryStats ----

    private static final String Q_START = "2026-02-01T00:00:00Z";
    private static final String Q_END = "2026-02-22T00:00:00Z";
    private static final long Q_START_MS = Instant.parse(Q_START).toEpochMilli();
    private static final long Q_END_MS = Instant.parse(Q_END).toEpochMilli();

    private static final long DAY1_MS = ZonedDateTime.of(
            2026, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
            .toInstant().toEpochMilli();

    private RemoteRepoStatsEntity dailyEntity(
            String repoKey, String pkgType, String method,
            long requests, long respTimeMs, long ttfbAvg,
            long respSize, long s2xx, long s3xx, long s4xx,
            long s5xx) {
        return RemoteRepoStatsEntity.builder()
                .id(0).repositoryKey(repoKey)
                .repoPackageType(pkgType)
                .updateTime(DAY1_MS)
                .totalRequestCount(requests)
                .totalResponseTimeMs(respTimeMs)
                .maxResponseTimeMs(respTimeMs)
                .ttfbAvg(ttfbAvg)
                .totalResponseSize(respSize)
                .status2xxCount(s2xx).status3xxCount(s3xx)
                .status4xxCount(s4xx).status5xxCount(s5xx)
                .httpMethod(method).transferRate(0)
                .build();
    }

    private RemoteRepoStatsRequest defaultRequest() {
        return new RemoteRepoStatsRequest(
                Q_START, Q_END, List.of(), List.of(),
                null, null, false, false,
                "totalRequests", "desc", 0, 50);
    }

    @Test
    void queryStats_shouldReturnEmptyResult_whenNoData() {
        doReturn(List.of()).when(dailyDao).getByTimeRange(
                Q_START_MS, Q_END_MS, List.of(), List.of());
        doReturn(List.of()).when(statusDao)
                .getByTimeRangeAndAggregationType(
                        Q_START_MS, Q_END_MS, "daily",
                        List.of(), List.of());

        RemoteRepoStatsQueryResult result = service.queryStats(defaultRequest());

        assertEquals(0, result.repos().size());
        assertEquals(0, result.globalStats().totalRequests());
        assertEquals(0, result.totalMatchedRepos());
    }

    @Test
    void queryStats_shouldAggregateRepoStats_whenDataExists() {
        List<RemoteRepoStatsEntity> rows = List.of(
                dailyEntity("repo-a", "maven", "GET",
                        100, 5000, 50, 10000,
                        80, 5, 10, 5));
        doReturn(rows).when(dailyDao).getByTimeRange(
                Q_START_MS, Q_END_MS, List.of(), List.of());
        doReturn(List.of()).when(statusDao)
                .getByTimeRangeAndAggregationType(
                        Q_START_MS, Q_END_MS, "daily",
                        List.of(), List.of());

        RemoteRepoStatsQueryResult result = service.queryStats(defaultRequest());

        assertEquals(1, result.repos().size());
        RemoteRepoStatsQueryResult.RepoResult repo = result.repos().get(0);
        assertEquals("repo-a", repo.repoKey());
        assertEquals("maven", repo.packageType());
        assertEquals(100, repo.totalRequests());
        assertEquals(85, repo.successfulRequests());
        assertEquals(15, repo.failedRequests());
    }

    @Test
    void queryStats_shouldFilterByRepoKeys_whenProvided() {
        List<RemoteRepoStatsEntity> rows = List.of(
                dailyEntity("repo-a", "maven", "GET",
                        100, 5000, 50, 10000,
                        90, 0, 10, 0));
        doReturn(rows).when(dailyDao).getByTimeRange(
                Q_START_MS, Q_END_MS, List.of("repo-a"), List.of());
        doReturn(List.of()).when(statusDao)
                .getByTimeRangeAndAggregationType(
                        Q_START_MS, Q_END_MS, "daily",
                        List.of("repo-a"), List.of());

        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                Q_START, Q_END, List.of("repo-a"), List.of(),
                null, null, false, false,
                "totalRequests", "desc", 0, 50);

        RemoteRepoStatsQueryResult result = service.queryStats(req);
        assertEquals(1, result.repos().size());
        assertEquals("repo-a", result.repos().get(0).repoKey());
    }

    @Test
    void queryStats_shouldFilterByMinErrorRate_whenProvided() {
        List<RemoteRepoStatsEntity> rows = List.of(
                dailyEntity("repo-a", "maven", "GET",
                        100, 5000, 50, 10000,
                        90, 0, 10, 0),
                dailyEntity("repo-b", "npm", "GET",
                        100, 5000, 50, 10000,
                        10, 0, 50, 40));
        doReturn(rows).when(dailyDao).getByTimeRange(
                Q_START_MS, Q_END_MS, List.of(), List.of());
        doReturn(List.of()).when(statusDao)
                .getByTimeRangeAndAggregationType(
                        Q_START_MS, Q_END_MS, "daily",
                        List.of(), List.of());

        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                Q_START, Q_END, List.of(), List.of(),
                0.5f, null, false, false,
                "totalRequests", "desc", 0, 50);

        RemoteRepoStatsQueryResult result = service.queryStats(req);
        assertEquals(1, result.repos().size());
        assertEquals("repo-b", result.repos().get(0).repoKey());
    }

    @Test
    void queryStats_shouldBuildGlobalStats_whenMultipleRepos() {
        List<RemoteRepoStatsEntity> rows = List.of(
                dailyEntity("repo-a", "maven", "GET",
                        100, 5000, 50, 10000,
                        80, 5, 10, 5),
                dailyEntity("repo-b", "npm", "GET",
                        200, 8000, 40, 20000,
                        180, 0, 15, 5));
        doReturn(rows).when(dailyDao).getByTimeRange(
                Q_START_MS, Q_END_MS, List.of(), List.of());
        doReturn(List.of()).when(statusDao)
                .getByTimeRangeAndAggregationType(
                        Q_START_MS, Q_END_MS, "daily",
                        List.of(), List.of());

        RemoteRepoStatsQueryResult result = service.queryStats(defaultRequest());

        RemoteRepoStatsQueryResult.GlobalStats gs = result.globalStats();
        assertEquals(300, gs.totalRequests());
        assertEquals(265, gs.successfulRequests());
        assertEquals(35, gs.failedRequests());
        assertEquals(2, gs.repoCount());
        assertEquals(30000, gs.totalResponseSize());
    }

    @Test
    void queryStats_shouldPaginate_whenOffsetAndLimitProvided() {
        List<RemoteRepoStatsEntity> rows = List.of(
                dailyEntity("repo-a", "maven", "GET",
                        300, 9000, 50, 10000,
                        290, 0, 10, 0),
                dailyEntity("repo-b", "npm", "GET",
                        200, 6000, 50, 10000,
                        190, 0, 10, 0),
                dailyEntity("repo-c", "pypi", "GET",
                        100, 3000, 50, 10000,
                        90, 0, 10, 0));
        doReturn(rows).when(dailyDao).getByTimeRange(
                Q_START_MS, Q_END_MS, List.of(), List.of());
        doReturn(List.of()).when(statusDao)
                .getByTimeRangeAndAggregationType(
                        Q_START_MS, Q_END_MS, "daily",
                        List.of(), List.of());

        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                Q_START, Q_END, List.of(), List.of(),
                null, null, false, false,
                "totalRequests", "desc", 1, 1);

        RemoteRepoStatsQueryResult result = service.queryStats(req);
        assertEquals(3, result.totalMatchedRepos());
        assertEquals(1, result.repos().size());
    }

    @Test
    void queryStats_shouldSortByErrorRateDesc_whenRequested() {
        List<RemoteRepoStatsEntity> rows = List.of(
                dailyEntity("repo-a", "maven", "GET",
                        100, 5000, 50, 10000,
                        90, 0, 10, 0),
                dailyEntity("repo-b", "npm", "GET",
                        100, 5000, 50, 10000,
                        50, 0, 30, 20));
        doReturn(rows).when(dailyDao).getByTimeRange(
                Q_START_MS, Q_END_MS, List.of(), List.of());
        doReturn(List.of()).when(statusDao)
                .getByTimeRangeAndAggregationType(
                        Q_START_MS, Q_END_MS, "daily",
                        List.of(), List.of());

        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                Q_START, Q_END, List.of(), List.of(),
                null, null, false, false,
                "errorRate", "desc", 0, 50);

        RemoteRepoStatsQueryResult result = service.queryStats(req);
        assertEquals("repo-b", result.repos().get(0).repoKey());
        assertEquals("repo-a", result.repos().get(1).repoKey());
    }

    @Test
    void queryStats_shouldTruncateToDayBoundaries_whenNonMidnightDates() {
        String midDayStart = "2026-02-01T15:30:00Z";
        String midDayEnd = "2026-02-22T10:45:00Z";
        long startDayMs = Instant.parse("2026-02-01T00:00:00Z").toEpochMilli();
        long endDayMs = Instant.parse("2026-02-22T00:00:00Z").toEpochMilli();

        List<RemoteRepoStatsEntity> rows = List.of(
                dailyEntity("repo-a", "maven", "GET",
                        100, 5000, 50, 10000,
                        80, 5, 10, 5));
        doReturn(rows).when(dailyDao).getByTimeRange(
                startDayMs, endDayMs, List.of(), List.of());
        doReturn(List.of()).when(statusDao)
                .getByTimeRangeAndAggregationType(
                        startDayMs, endDayMs, "daily",
                        List.of(), List.of());

        RemoteRepoStatsRequest req = new RemoteRepoStatsRequest(
                midDayStart, midDayEnd, List.of(), List.of(),
                null, null, false, false,
                "totalRequests", "desc", 0, 50);

        RemoteRepoStatsQueryResult result = service.queryStats(req);
        assertEquals(1, result.repos().size());
        assertEquals("repo-a", result.repos().get(0).repoKey());
    }
}
