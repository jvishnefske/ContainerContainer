package org.artifactory.traffic.stats;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.storage.db.traffic.stats.dao.RepoTrafficDailyDao;
import org.artifactory.storage.db.traffic.stats.dao.RepoTrafficHourlyDao;
import org.artifactory.storage.db.traffic.stats.entity.RepoTrafficStatsEntity;
import org.artifactory.traffic.TrafficEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service implementation for per-repo traffic statistics.
 * <p>
 * Each Artifactory node accumulates traffic stats in memory using thread-safe data structures.
 * A scheduled cron job periodically flushes the accumulated data to both hourly and daily
 * database tables using additive merge operations, ensuring HA safety when multiple nodes
 * write concurrently.
 * </p>
 *
 * @author georgyr
 */
@Slf4j
@Component
public class RepoTrafficStatsServiceImpl implements RepoTrafficStatsService {

    private final TaskService taskService;
    private final RepoTrafficHourlyDao hourlyDao;
    private final RepoTrafficDailyDao dailyDao;

    private final ConcurrentHashMap<AccumulatorKey, TrafficAccumulator> accumulatorMap = new ConcurrentHashMap<>();
    private final AtomicInteger accumulatorCount = new AtomicInteger(0);
    private final ReentrantReadWriteLock flushLock = new ReentrantReadWriteLock();

    public RepoTrafficStatsServiceImpl(TaskService taskService,
                                       RepoTrafficHourlyDao hourlyDao,
                                       RepoTrafficDailyDao dailyDao) {
        this.taskService = taskService;
        this.hourlyDao = hourlyDao;
        this.dailyDao = dailyDao;
        setupCollectionJob();
    }

    @Override
    public void recordTrafficEntry(TrafficEntry entry) {
        if (entry.getRepoType() == null) {
            log.debug("Skipping traffic entry with null repo type for repo={}", entry.getRepoKey());
            return;
        }
        flushLock.readLock().lock();
        try {
            long hourBucket = computeHourBucket(Instant.now());
            String redirectTarget = entry.getRedirectTarget() != null
                    ? entry.getRedirectTarget().name() : TrafficEntry.TrafficTarget.DIRECT.name();
            AccumulatorKey key = new AccumulatorKey(
                    hourBucket,
                    entry.getAction().name(),
                    entry.getActor().name(),
                    redirectTarget,
                    entry.getOriginRepoKey(),
                    entry.getRepoKey()
            );
            TrafficAccumulator accumulator = getOrCreateAccumulator(key);
            if (accumulator == null) {
                return;
            }
            accumulator.record(
                    entry.getOriginRepoType(),
                    entry.getOriginPackageType(),
                    entry.getRepoType(),
                    entry.getPackageType(),
                    entry.getTransferredSize()
            );
        } finally {
            flushLock.readLock().unlock();
        }
    }

    /**
     * Returns an existing accumulator for the key, or atomically creates one if capacity allows.
     * Returns {@code null} when the accumulator map is at capacity and the key is new.
     */
    private TrafficAccumulator getOrCreateAccumulator(AccumulatorKey key) {
        TrafficAccumulator accumulator = accumulatorMap.get(key);
        if (accumulator != null) {
            return accumulator;
        }
        int maxSize = ConstantValues.repoTrafficStatsMaxAccumulatorSize.getInt();
        if (accumulatorCount.get() >= maxSize) {
            log.debug("Accumulator map at capacity ({}), dropping entry for repo={}", maxSize, key.repoKey);
            return null;
        }
        int slot = accumulatorCount.getAndIncrement();
        if (slot >= maxSize) {
            accumulatorCount.decrementAndGet();
            return null;
        }
        TrafficAccumulator created = new TrafficAccumulator();
        TrafficAccumulator existing = accumulatorMap.putIfAbsent(key, created);
        if (existing != null) {
            accumulatorCount.decrementAndGet();
            return existing;
        }
        return created;
    }

    @Override
    public void flushStats() {
        Map<AccumulatorKey, TrafficAccumulator> snapshot;

        flushLock.writeLock().lock();
        try {
            snapshot = new HashMap<>(accumulatorMap);
            accumulatorMap.clear();
            accumulatorCount.set(0);
        } finally {
            flushLock.writeLock().unlock();
        }

        if (snapshot.isEmpty()) {
            log.debug("No repo traffic stats to flush");
        } else {
            int maxRetries = ConstantValues.repoTrafficStatsMaxRetries.getInt();
            log.debug("Flushing repo traffic stats for {} keys", snapshot.size());

            for (Map.Entry<AccumulatorKey, TrafficAccumulator> entry : snapshot.entrySet()) {
                AccumulatorKey key = entry.getKey();
                TrafficAccumulator acc = entry.getValue();

                if (acc.retryCount.get() >= maxRetries) {
                    log.error("Dropping repo traffic stats entry after {} retries for originRepo={},repo={}, bucket={}",
                            acc.retryCount.get(), key.originRepoKey, key.repoKey, key.hourBucket);
                    continue;
                }

                if (!flushToDao(hourlyDao, key, acc, key.hourBucket, "hourly")) {
                    requeueFailedEntry(key, acc);
                    continue;
                }
                long dayBucket = computeDayBucket(key.hourBucket);
                flushToDao(dailyDao, key, acc, dayBucket, "daily");
            }
        }

        cleanupOldRecords();
        log.debug("Repo traffic stats flush completed for {} entries", snapshot.size());
    }

    private boolean flushToDao(
            org.artifactory.storage.db.traffic.stats.dao.AbstractRepoTrafficStatsDao dao,
            AccumulatorKey key, TrafficAccumulator acc, long bucket, String granularity) {
        try {
            RepoTrafficStatsEntity entity = buildStatsEntity(key, acc, bucket);
            dao.additiveMerge(entity);
            return true;
        } catch (Exception e) {
            log.warn("Failed to flush {} repo traffic stats for originRepo={}, repo={}, bucket={}",
                    granularity, key.originRepoKey, key.repoKey, bucket, e);
            return false;
        }
    }

    private void requeueFailedEntry(AccumulatorKey key, TrafficAccumulator failedAcc) {
        failedAcc.retryCount.incrementAndGet();
        TrafficAccumulator existing = accumulatorMap.putIfAbsent(key, failedAcc);
        if (existing == null) {
            accumulatorCount.incrementAndGet();
            return;
        }
        existing.requestCount.addAndGet(failedAcc.requestCount.get());
        existing.totalTransferredSize.addAndGet(failedAcc.totalTransferredSize.get());
        existing.retryCount.set(Math.max(existing.retryCount.get(), failedAcc.retryCount.get()));
        existing.copyTypesFromWhenMissing(failedAcc);
    }

    private RepoTrafficStatsEntity buildStatsEntity(AccumulatorKey key,
                                                    TrafficAccumulator acc,
                                                    long bucket) {
        return RepoTrafficStatsEntity.builder()
                .updateTime(bucket)
                .action(key.action)
                .actor(key.actor)
                .redirectTarget(key.redirectTarget)
                .originRepoKey(key.originRepoKey)
                .originRepoType(acc.originRepoType)
                .originPackageType(acc.originPackageType)
                .repoKey(key.repoKey)
                .repoType(acc.repoType)
                .packageType(acc.packageType)
                .requestCount(acc.requestCount.get())
                .totalTransferredSize(acc.totalTransferredSize.get())
                .build();
    }

    private void cleanupOldRecords() {
        try {
            int hourlyRetentionDays = ConstantValues.repoTrafficStatsHourlyRetentionDays.getInt();
            long hourlyThreshold = Instant.now().minus(hourlyRetentionDays, ChronoUnit.DAYS).toEpochMilli();
            hourlyDao.deleteByThresholdTime(hourlyThreshold);

            int dailyRetentionDays = ConstantValues.repoTrafficStatsDailyRetentionDays.getInt();
            long dailyThreshold = Instant.now().minus(dailyRetentionDays, ChronoUnit.DAYS).toEpochMilli();
            dailyDao.deleteByThresholdTime(dailyThreshold);

            log.debug("Cleaned up repo traffic stats: hourly older than {} days, daily older than {} days",
                    hourlyRetentionDays, dailyRetentionDays);
        } catch (Exception e) {
            log.warn("Failed to cleanup old repo traffic stats records", e);
        }
    }

    private void setupCollectionJob() {
        String cronExpression = ConstantValues.repoTrafficStatsCollectionCron.getString();
        log.info("Scheduling repo traffic stats collection job with cron expression: {}", cronExpression);
        Task task = TaskUtils.createCronTask(RepoTrafficStatsCollectionJob.class, cronExpression);
        taskService.startTask(task, false);
    }

    static long computeHourBucket(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS)
                .toInstant()
                .toEpochMilli();
    }

    static long computeDayBucket(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()
                .toEpochMilli();
    }

    // ---- Inner classes ----

    record AccumulatorKey(long hourBucket, String action, String actor,
                          String redirectTarget, String originRepoKey,
                          String repoKey) {
    }

    static class TrafficAccumulator {
        volatile String originRepoType;
        volatile String originPackageType;
        volatile String repoType;
        volatile String packageType;
        final AtomicLong requestCount = new AtomicLong();
        final AtomicLong totalTransferredSize = new AtomicLong();
        final AtomicInteger retryCount = new AtomicInteger();

        void record(String originRepoType, String originPackageType,
                    String repoType, String packageType, long transferredSize) {
            if (this.originRepoType == null && originRepoType != null) {
                this.originRepoType = originRepoType;
                this.originPackageType = originPackageType;
                this.repoType = repoType;
                this.packageType = packageType;
            }
            requestCount.incrementAndGet();
            totalTransferredSize.addAndGet(transferredSize);
        }

        /**
         * Copies type metadata from {@code source} into this accumulator when this accumulator's
         * type field is null and source's is non-null. Preserves non-null types when merging
         * requeued failed entries so that DB NOT NULL constraints are satisfied on next flush.
         */
        void copyTypesFromWhenMissing(TrafficAccumulator source) {
            if (this.originRepoType == null && source.originRepoType != null) {
                this.originRepoType = source.originRepoType;
                this.originPackageType = source.originPackageType;
                this.repoType = source.repoType;
                this.packageType = source.packageType;
            }
        }
    }
}
