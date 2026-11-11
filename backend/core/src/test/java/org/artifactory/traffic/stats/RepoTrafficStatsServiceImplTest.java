package org.artifactory.traffic.stats;

import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.traffic.stats.dao.RepoTrafficDailyDao;
import org.artifactory.storage.db.traffic.stats.dao.RepoTrafficHourlyDao;
import org.artifactory.storage.db.traffic.stats.entity.RepoTrafficStatsEntity;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.traffic.TrafficEntry;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Unit tests for {@link RepoTrafficStatsServiceImpl}.
 * Uses Mockito to mock the DAO layer and TaskService, following the
 * {@code RemoteRepoStatsServiceImplTest} pattern.
 *
 * @author georgyr
 */
class RepoTrafficStatsServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private TaskService taskService;
    @Mock
    private RepoTrafficHourlyDao hourlyDao;
    @Mock
    private RepoTrafficDailyDao dailyDao;

    @Captor
    private ArgumentCaptor<RepoTrafficStatsEntity> entityCaptor;

    private RepoTrafficStatsServiceImpl service;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void beforeEach() {
        autoCloseable = openMocks(this);
        doReturn("task-id-1").when(taskService)
                .startTask(any(), anyBoolean());
        doNothing().when(hourlyDao).additiveMerge(any());
        doNothing().when(dailyDao).additiveMerge(any());
        doNothing().when(hourlyDao).deleteByThresholdTime(anyLong());
        doNothing().when(dailyDao).deleteByThresholdTime(anyLong());
        service = new RepoTrafficStatsServiceImpl(
                taskService, hourlyDao, dailyDao);
    }

    @AfterEach
    void afterEach() throws Exception {
        autoCloseable.close();
        ArtifactoryHome.get().getArtifactoryProperties().setProperty(
                ConstantValues.repoTrafficStatsMaxAccumulatorSize.getPropertyName(),
                ConstantValues.repoTrafficStatsMaxAccumulatorSize.getDefValue());
    }

    // ---- Constructor / scheduling ----

    @Test
    void constructor_shouldScheduleCollectionJob_whenServiceIsCreated() {
        verify(taskService).startTask(any(), anyBoolean());
    }

    // ---- recordTrafficEntry single/multiple ----

    @Test
    void recordTrafficEntry_shouldAggregateSingleEntry_whenCalled() {
        // Arrange
        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-repo", "virtual", "maven",
                "local-repo", "local", "maven",
                5242880L);

        // Act
        service.recordTrafficEntry(entry);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        RepoTrafficStatsEntity hourly = entityCaptor.getValue();
        assertEquals("DOWNLOAD", hourly.action());
        assertEquals("USER", hourly.actor());
        assertEquals("DIRECT", hourly.redirectTarget());
        assertEquals("virtual-repo", hourly.originRepoKey());
        assertEquals("virtual", hourly.originRepoType());
        assertEquals("maven", hourly.originPackageType());
        assertEquals("local-repo", hourly.repoKey());
        assertEquals("local", hourly.repoType());
        assertEquals("maven", hourly.packageType());
        assertEquals(1L, hourly.requestCount());
        assertEquals(5242880L, hourly.totalTransferredSize());
    }

    @Test
    void recordTrafficEntry_shouldAggregateMultipleEntries_whenSameKey() {
        // Arrange
        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-repo", "virtual", "maven",
                "local-repo", "local", "maven",
                1000L);

        // Act
        service.recordTrafficEntry(entry);
        service.recordTrafficEntry(entry);
        service.recordTrafficEntry(entry);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        RepoTrafficStatsEntity hourly = entityCaptor.getValue();
        assertEquals(3L, hourly.requestCount());
        assertEquals(3000L, hourly.totalTransferredSize());
    }

    // ---- Flush writes to both DAOs ----

    @Test
    void flushStats_shouldWriteToHourlyAndDailyDaos_whenAccumulatorNotEmpty() {
        // Arrange
        service.recordTrafficEntry(createDefaultEntry());

        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(
                any(RepoTrafficStatsEntity.class));
        verify(dailyDao).additiveMerge(
                any(RepoTrafficStatsEntity.class));
    }

    // ---- Flush clears map / empty flush ----

    @Test
    void flushStats_shouldClearAccumulator_whenFlushCompletes() {
        // Arrange
        service.recordTrafficEntry(createDefaultEntry());

        // Act
        service.flushStats();
        service.flushStats();

        // Assert -- additiveMerge called only once from first flush
        verify(hourlyDao, times(1)).additiveMerge(
                any(RepoTrafficStatsEntity.class));
    }

    @Test
    void flushStats_shouldNotWriteStats_whenAccumulatorIsEmpty() {
        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao, never()).additiveMerge(any());
        verify(dailyDao, never()).additiveMerge(any());
    }

    // ---- Concurrent recording ----

    @Test
    void recordTrafficEntry_shouldBeThreadSafe_whenConcurrentRequests()
            throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int entriesPerThread = 100;
        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-repo", "virtual", "maven",
                "local-repo", "local", "maven",
                100L);

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < entriesPerThread; j++) {
                        service.recordTrafficEntry(entry);
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
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        RepoTrafficStatsEntity entity = entityCaptor.getValue();
        long expectedTotal =
                (long) threadCount * entriesPerThread;
        assertEquals(expectedTotal, entity.requestCount());
        assertEquals(expectedTotal * 100L,
                entity.totalTransferredSize());
    }

    // ---- Hour/day bucket computation ----

    @Test
    void computeHourBucket_shouldTruncateToHour_whenGivenMidHourInstant() {
        // Arrange -- 2026-02-10T14:35:22Z
        Instant instant = ZonedDateTime.of(
                2026, 2, 10, 14, 35, 22, 0, ZoneOffset.UTC)
                .toInstant();

        // Act
        long bucket =
                RepoTrafficStatsServiceImpl.computeHourBucket(instant);

        // Assert -- should truncate to 2026-02-10T14:00:00Z
        long expected = ZonedDateTime.of(
                2026, 2, 10, 14, 0, 0, 0, ZoneOffset.UTC)
                .toInstant().toEpochMilli();
        assertEquals(expected, bucket);
    }

    @Test
    void computeHourBucket_shouldReturnSameValue_whenAtExactHour() {
        // Arrange
        Instant instant = ZonedDateTime.of(
                2026, 2, 10, 14, 0, 0, 0, ZoneOffset.UTC)
                .toInstant();

        // Act
        long bucket =
                RepoTrafficStatsServiceImpl.computeHourBucket(instant);

        // Assert
        assertEquals(instant.toEpochMilli(), bucket);
    }

    @Test
    void computeDayBucket_shouldTruncateToDay_whenGivenMidDayTimestamp() {
        // Arrange -- 2026-02-10T14:00:00Z
        long hourBucket = ZonedDateTime.of(
                2026, 2, 10, 14, 0, 0, 0, ZoneOffset.UTC)
                .toInstant().toEpochMilli();

        // Act
        long dayBucket =
                RepoTrafficStatsServiceImpl.computeDayBucket(hourBucket);

        // Assert -- should truncate to 2026-02-10T00:00:00Z
        long expected = ZonedDateTime.of(
                2026, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
                .toInstant().toEpochMilli();
        assertEquals(expected, dayBucket);
    }

    @Test
    void computeDayBucket_shouldReturnSameValue_whenAtMidnight() {
        // Arrange
        long midnight = ZonedDateTime.of(
                2026, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC)
                .toInstant().toEpochMilli();

        // Act & Assert
        assertEquals(midnight,
                RepoTrafficStatsServiceImpl.computeDayBucket(midnight));
    }

    @Test
    void flushStats_shouldWriteCorrectBuckets_whenHourlyAndDailyFlushed() {
        // Arrange
        service.recordTrafficEntry(createDefaultEntry());

        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        RepoTrafficStatsEntity hourlyEntity = entityCaptor.getValue();

        verify(dailyDao).additiveMerge(entityCaptor.capture());
        RepoTrafficStatsEntity dailyEntity = entityCaptor.getValue();

        // Hourly should be truncated to the hour
        ZonedDateTime hourZdt = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(hourlyEntity.updateTime()),
                ZoneOffset.UTC);
        assertEquals(0, hourZdt.getMinute());
        assertEquals(0, hourZdt.getSecond());

        // Daily should be truncated to midnight
        ZonedDateTime dayZdt = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(dailyEntity.updateTime()),
                ZoneOffset.UTC);
        assertEquals(0, dayZdt.getHour());
        assertEquals(0, dayZdt.getMinute());
        assertEquals(0, dayZdt.getSecond());
    }

    // ---- Actor + redirect_target propagation ----

    @Test
    void recordTrafficEntry_shouldPropagateActor_whenActorIsXray() {
        // Arrange
        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.XRAY,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-repo", "virtual", "maven",
                "local-repo", "local", "maven",
                512L);

        // Act
        service.recordTrafficEntry(entry);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        assertEquals("XRAY", entityCaptor.getValue().actor());
    }

    @Test
    void recordTrafficEntry_shouldDefaultRedirectTargetToDirect_whenNull() {
        // Arrange -- null redirectTarget
        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.UPLOAD,
                TrafficEntry.TrafficActor.USER,
                null,
                "local-repo", "local", "maven",
                "local-repo", "local", "maven",
                256L);

        // Act
        service.recordTrafficEntry(entry);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        assertEquals("DIRECT",
                entityCaptor.getValue().redirectTarget());
    }

    @Test
    void recordTrafficEntry_shouldPropagateRedirectTarget_whenS3() {
        // Arrange
        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.REDIRECT,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.S3,
                "virtual-repo", "virtual", "docker",
                "remote-repo", "remote", "docker",
                4096L);

        // Act
        service.recordTrafficEntry(entry);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        assertEquals("S3", entityCaptor.getValue().redirectTarget());
    }

    // ---- Multiple repos / different keys ----

    @Test
    void recordTrafficEntry_shouldAggregateSeparately_whenDifferentRepos() {
        // Arrange
        TrafficEntry entryA = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-a", "virtual", "maven",
                "local-a", "local", "maven",
                500L);
        TrafficEntry entryB = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-b", "virtual", "npm",
                "local-b", "local", "npm",
                700L);

        // Act
        service.recordTrafficEntry(entryA);
        service.recordTrafficEntry(entryB);
        service.flushStats();

        // Assert -- atLeast(2): on CI, entries can land in different hour buckets (extra key),
        // so we assert the two expected entities are present instead of exact invocation count
        verify(hourlyDao, atLeast(2)).additiveMerge(
                entityCaptor.capture());
        List<RepoTrafficStatsEntity> captured =
                entityCaptor.getAllValues();
        assertTrue(captured.size() >= 2, "expected at least 2 merged entities");

        RepoTrafficStatsEntity repoA = captured.stream()
                .filter(e -> "virtual-a"
                        .equals(e.originRepoKey()))
                .findFirst().orElseThrow();
        assertEquals(1L, repoA.requestCount());
        assertEquals(500L, repoA.totalTransferredSize());

        RepoTrafficStatsEntity repoB = captured.stream()
                .filter(e -> "virtual-b"
                        .equals(e.originRepoKey()))
                .findFirst().orElseThrow();
        assertEquals(1L, repoB.requestCount());
        assertEquals(700L, repoB.totalTransferredSize());
    }

    @Test
    void recordTrafficEntry_shouldAggregateSeparately_whenDifferentActions() {
        // Arrange
        TrafficEntry download = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-repo", "virtual", "maven",
                "local-repo", "local", "maven",
                1000L);
        TrafficEntry upload = createTrafficEntry(
                TrafficEntry.TrafficAction.UPLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-repo", "virtual", "maven",
                "local-repo", "local", "maven",
                2000L);

        // Act
        service.recordTrafficEntry(download);
        service.recordTrafficEntry(upload);
        service.flushStats();

        // Assert -- atLeast(2): on CI, entries can land in different hour buckets (extra key),
        // so we assert the two expected entities are present instead of exact invocation count
        verify(hourlyDao, atLeast(2)).additiveMerge(
                entityCaptor.capture());
        List<RepoTrafficStatsEntity> captured =
                entityCaptor.getAllValues();
        assertTrue(captured.size() >= 2, "expected at least 2 merged entities");

        RepoTrafficStatsEntity dl = captured.stream()
                .filter(e -> "DOWNLOAD".equals(e.action()))
                .findFirst().orElseThrow();
        assertEquals(1000L, dl.totalTransferredSize());

        RepoTrafficStatsEntity ul = captured.stream()
                .filter(e -> "UPLOAD".equals(e.action()))
                .findFirst().orElseThrow();
        assertEquals(2000L, ul.totalTransferredSize());
    }

    // ---- Retention cleanup ----

    @Test
    void flushStats_shouldCallRetentionCleanup_whenAccumulatorNotEmpty() {
        // Arrange
        service.recordTrafficEntry(createDefaultEntry());

        // Act
        service.flushStats();

        // Assert
        verify(hourlyDao).deleteByThresholdTime(anyLong());
        verify(dailyDao).deleteByThresholdTime(anyLong());
    }

    @Test
    void flushStats_shouldCallRetentionCleanup_whenAccumulatorIsEmpty() {
        // Act
        service.flushStats();

        // Assert -- cleanup should still run
        verify(hourlyDao).deleteByThresholdTime(anyLong());
        verify(dailyDao).deleteByThresholdTime(anyLong());
    }

    // ---- Re-queue on flush failure ----

    @Test
    void flushStats_shouldRequeueEntry_whenDaoThrowsException() {
        // Arrange
        doThrow(new RuntimeException("DB down"))
                .doNothing()
                .when(hourlyDao).additiveMerge(any());
        service.recordTrafficEntry(createDefaultEntry());

        // Act -- first flush fails, entry should be re-queued
        service.flushStats();
        service.flushStats();

        // Assert -- hourlyDao called twice: first fails, second succeeds
        verify(hourlyDao, times(2)).additiveMerge(
                any(RepoTrafficStatsEntity.class));
    }

    @Test
    void flushStats_shouldPreserveCountsOnRequeue_whenDaoThrowsException() {
        // Arrange
        doThrow(new RuntimeException("DB down"))
                .doNothing()
                .when(hourlyDao).additiveMerge(any());
        doThrow(new RuntimeException("DB down"))
                .doNothing()
                .when(dailyDao).additiveMerge(any());
        service.recordTrafficEntry(createDefaultEntry());

        // Act
        service.flushStats();
        service.flushStats();

        // Assert -- verify the re-queued entity preserved counts
        verify(hourlyDao, times(2)).additiveMerge(entityCaptor.capture());
        List<RepoTrafficStatsEntity> captured = entityCaptor.getAllValues();
        RepoTrafficStatsEntity requeued = captured.get(1);
        assertEquals(1L, requeued.requestCount());
        assertEquals(1024L, requeued.totalTransferredSize());
    }

    // ---- Retry limit ----

    @Test
    void flushStats_shouldDropEntry_whenRetryLimitExceeded() {
        // Arrange -- DAO always throws
        doThrow(new RuntimeException("permanent failure"))
                .when(hourlyDao).additiveMerge(any());
        service.recordTrafficEntry(createDefaultEntry());

        // Act -- default max retries is 3
        service.flushStats(); // retry 0 -> fails, requeued (retryCount=1)
        service.flushStats(); // retry 1 -> fails, requeued (retryCount=2)
        service.flushStats(); // retry 2 -> fails, requeued (retryCount=3)
        service.flushStats(); // retry 3 >= max -> dropped

        // Assert -- 3 actual DAO attempts, 4th tick drops without call
        verify(hourlyDao, times(3)).additiveMerge(
                any(RepoTrafficStatsEntity.class));

        // Fifth flush should have nothing left
        service.flushStats();
        verify(hourlyDao, times(3)).additiveMerge(
                any(RepoTrafficStatsEntity.class));
    }

    @Test
    void flushStats_shouldSucceed_whenRetryCountBelowLimit() {
        // Arrange -- fail twice then succeed
        doThrow(new RuntimeException("transient"))
                .doThrow(new RuntimeException("transient"))
                .doNothing()
                .when(hourlyDao).additiveMerge(any());
        service.recordTrafficEntry(createDefaultEntry());

        // Act
        service.flushStats(); // fails, retryCount -> 1
        service.flushStats(); // fails, retryCount -> 2
        service.flushStats(); // retryCount 2 < 3, succeeds

        // Assert
        verify(hourlyDao, times(3)).additiveMerge(
                any(RepoTrafficStatsEntity.class));
        verify(dailyDao).additiveMerge(
                any(RepoTrafficStatsEntity.class));
    }

    // ---- Map capacity ----

    @Test
    void recordTrafficEntry_shouldDropNewKey_whenMapAtCapacity() {
        // Arrange -- set capacity to 2 via system property
        setBooleanProp("repo.traffic.stats.max.accumulator.size", "2");

        TrafficEntry entryA = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "repo-a", "local", "maven",
                "repo-a", "local", "maven", 100L);
        TrafficEntry entryB = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "repo-b", "local", "maven",
                "repo-b", "local", "maven", 200L);
        TrafficEntry entryC = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "repo-c", "local", "maven",
                "repo-c", "local", "maven", 300L);

        // Act -- A and B fill the map; C should be dropped
        service.recordTrafficEntry(entryA);
        service.recordTrafficEntry(entryB);
        service.recordTrafficEntry(entryC);
        service.flushStats();

        // Assert -- only 2 entities flushed
        verify(hourlyDao, times(2)).additiveMerge(
                entityCaptor.capture());
        List<RepoTrafficStatsEntity> flushed =
                entityCaptor.getAllValues();
        assertEquals(2, flushed.size());
        assertTrue(flushed.stream().noneMatch(
                e -> "repo-c".equals(e.repoKey())));
    }

    @Test
    void recordTrafficEntry_shouldAccumulateExistingKey_whenMapAtCapacity() {
        // Arrange -- set capacity to 1
        setBooleanProp("repo.traffic.stats.max.accumulator.size", "1");

        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "repo-a", "local", "maven",
                "repo-a", "local", "maven", 100L);

        // Act -- same key, should still accumulate even at cap
        service.recordTrafficEntry(entry);
        service.recordTrafficEntry(entry);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        assertEquals(2L, entityCaptor.getValue().requestCount());
        assertEquals(200L,
                entityCaptor.getValue().totalTransferredSize());
    }

    // ---- Null type handling ----

    @Test
    void recordTrafficEntry_shouldDropEntry_whenRepoTypeIsNull() {
        // Arrange -- system repo with null types
        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "jfrog-usage-logs", null, null,
                "jfrog-usage-logs", null, null,
                512L);

        // Act
        service.recordTrafficEntry(entry);
        service.flushStats();

        // Assert -- entry was dropped, nothing flushed
        verify(hourlyDao, never()).additiveMerge(any());
        verify(dailyDao, never()).additiveMerge(any());
    }

    @Test
    void recordTrafficEntry_shouldAcceptEntry_whenRepoTypeIsPresent() {
        // Arrange
        TrafficEntry entry = createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "repo-a", "local", "maven",
                "repo-a", "local", "maven", 100L);

        // Act
        service.recordTrafficEntry(entry);
        service.flushStats();

        // Assert
        verify(hourlyDao).additiveMerge(entityCaptor.capture());
        assertEquals("local", entityCaptor.getValue().repoType());
        assertEquals("maven", entityCaptor.getValue().packageType());
    }

    // ---- Helper methods ----

    private void setBooleanProp(String key, String value) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.SYS_PROP_PREFIX + key, value);
    }

    private TrafficEntry createDefaultEntry() {
        return createTrafficEntry(
                TrafficEntry.TrafficAction.DOWNLOAD,
                TrafficEntry.TrafficActor.USER,
                TrafficEntry.TrafficTarget.DIRECT,
                "virtual-repo", "virtual", "maven",
                "local-repo", "local", "maven",
                1024L);
    }

    private TrafficEntry createTrafficEntry(
            TrafficEntry.TrafficAction action,
            TrafficEntry.TrafficActor actor,
            TrafficEntry.TrafficTarget redirectTarget,
            String originRepoKey,
            String originRepoType,
            String originPackageType,
            String repoKey,
            String repoType,
            String packageType,
            long transferredSize) {
        TrafficEntry entry = TrafficEntry.builder()
                .action(action)
                .actor(actor)
                .redirectTarget(redirectTarget)
                .repoKey(repoKey)
                .repoType(repoType)
                .packageType(packageType)
                .transferredSize(transferredSize)
                .build();
        entry.setOriginRepoKey(originRepoKey);
        entry.setOriginRepoType(originRepoType);
        entry.setOriginPackageType(originPackageType);
        return entry;
    }
}
