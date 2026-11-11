package org.artifactory.remote.stats;

import lombok.extern.slf4j.Slf4j;

import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.storage.db.remote.stats.dao.RemoteRepoStatsDailyDao;
import org.artifactory.storage.db.remote.stats.dao.RemoteRepoStatsHourlyDao;
import org.artifactory.storage.db.remote.stats.dao.RemoteRepoStatsStatusDao;
import org.artifactory.storage.db.remote.stats.entity.RemoteRepoStatsEntity;
import org.artifactory.storage.db.remote.stats.entity.RemoteRepoStatsStatusEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Service implementation for remote repository statistics.
 * <p>
 * Each Artifactory node accumulates request stats in memory using thread-safe data structures.
 * A scheduled cron job periodically flushes the accumulated data to the database using
 * additive merge operations, ensuring HA safety when multiple nodes write concurrently.
 * </p>
 *
 * @author husseina
 */
@Slf4j
@Component
public class RemoteRepoStatsServiceImpl implements RemoteRepoStatsService {

    private static final String AGGREGATION_HOURLY = "hourly";
    private static final String AGGREGATION_DAILY = "daily";

    private final TaskService taskService;
    private final RemoteRepoStatsHourlyDao hourlyDao;
    private final RemoteRepoStatsDailyDao dailyDao;
    private final RemoteRepoStatsStatusDao statusDao;

    /**
     * In-memory accumulator map keyed by (repositoryKey, hourBucket).
     * Each node maintains its own map; data is flushed and cleared periodically.
     */
    private final ConcurrentHashMap<AccumulatorKey, StatsAccumulator> accumulatorMap = new ConcurrentHashMap<>();

    /**
     * Lock to ensure flush and record operations don't conflict.
     * Record uses read-lock (many concurrent writers), flush uses write-lock (exclusive).
     */
    private final ReentrantReadWriteLock flushLock = new ReentrantReadWriteLock();

    public RemoteRepoStatsServiceImpl(TaskService taskService,
                                      RemoteRepoStatsHourlyDao hourlyDao,
                                      RemoteRepoStatsDailyDao dailyDao,
                                      RemoteRepoStatsStatusDao statusDao) {
        this.taskService = taskService;
        this.hourlyDao = hourlyDao;
        this.dailyDao = dailyDao;
        this.statusDao = statusDao;
        setupCollectionJob();
    }

    @Override
    public void recordRequest(String repositoryKey, String repoPackageType,
                              String httpMethod, int statusCode,
                              long responseTimeMs, long ttfbMs,
                              long responseSize) {
        flushLock.readLock().lock();
        try {
            long hourBucket = computeHourBucket(Instant.now());
            AccumulatorKey key = new AccumulatorKey(
                    repositoryKey, repoPackageType, httpMethod, hourBucket);
            StatsAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    key, k -> new StatsAccumulator());
            accumulator.record(statusCode, responseTimeMs, ttfbMs,
                    responseSize);
        } finally {
            flushLock.readLock().unlock();
        }
    }

    @Override
    public void flushStats() {
        Map<AccumulatorKey, StatsAccumulator> snapshot;

        // Take exclusive lock to swap out the accumulator map atomically
        flushLock.writeLock().lock();
        try {
            snapshot = new ConcurrentHashMap<>(accumulatorMap);
            accumulatorMap.clear();
        } finally {
            flushLock.writeLock().unlock();
        }

        if (snapshot.isEmpty()) {
            log.debug("No remote repo stats to flush");
        } else {
            log.debug("Flushing remote repo stats for {} keys", snapshot.size());

            for (Map.Entry<AccumulatorKey, StatsAccumulator> entry : snapshot.entrySet()) {
                AccumulatorKey key = entry.getKey();
                StatsAccumulator acc = entry.getValue();
                try {
                    flushHourlyStats(key, acc);
                    flushDailyStats(key, acc);
                    flushStatusDetails(key, acc);
                } catch (Exception e) {
                    log.warn("Failed to flush remote repo stats for repo={}, bucket={}",
                            key.repositoryKey, key.hourBucket, e);
                }
            }
        }

        cleanupOldRecords();
        log.debug("Remote repo stats flush completed for {} entries", snapshot.size());
    }

    private void flushHourlyStats(AccumulatorKey key, StatsAccumulator acc) {
        RemoteRepoStatsEntity entity = buildStatsEntity(key, acc, key.hourBucket);
        hourlyDao.additiveMerge(entity);
    }

    private void flushDailyStats(AccumulatorKey key, StatsAccumulator acc) {
        long dayBucket = computeDayBucket(key.hourBucket);
        RemoteRepoStatsEntity entity = buildStatsEntity(key, acc, dayBucket);
        dailyDao.additiveMerge(entity);
    }

    private RemoteRepoStatsEntity buildStatsEntity(AccumulatorKey key,
                                                     StatsAccumulator acc,
                                                     long bucket) {
        long totalRequests = acc.totalRequestCount.get();
        long totalTimeMs = acc.totalResponseTimeMs.get();
        long totalSize = acc.totalResponseSize.get();
        long ttfbAvg = totalRequests > 0
                ? acc.ttfbSum.get() / totalRequests : 0;
        long transferRate = totalTimeMs > 0
                ? (totalSize * 1000) / totalTimeMs : 0;
        return RemoteRepoStatsEntity.builder()
                .repositoryKey(key.repositoryKey)
                .repoPackageType(key.repoPackageType)
                .updateTime(bucket)
                .totalRequestCount(totalRequests)
                .totalResponseTimeMs(totalTimeMs)
                .maxResponseTimeMs(acc.maxResponseTimeMs.get())
                .ttfbAvg(ttfbAvg)
                .totalResponseSize(totalSize)
                .status2xxCount(acc.status2xxCount.get())
                .status3xxCount(acc.status3xxCount.get())
                .status4xxCount(acc.status4xxCount.get())
                .status5xxCount(acc.status5xxCount.get())
                .httpMethod(key.httpMethod)
                .transferRate(transferRate)
                .build();
    }

    private void flushStatusDetails(AccumulatorKey key,
                                     StatsAccumulator acc) {
        List<RemoteRepoStatsStatusEntity> hourlyStatusEntities =
                new ArrayList<>();
        List<RemoteRepoStatsStatusEntity> dailyStatusEntities =
                new ArrayList<>();
        long dayBucket = computeDayBucket(key.hourBucket);

        for (Map.Entry<Integer, AtomicLong> statusEntry :
                acc.statusDetails.entrySet()) {
            int statusCode = statusEntry.getKey();
            long count = statusEntry.getValue().get();
            if (count <= 0) {
                continue;
            }
            hourlyStatusEntities.add(RemoteRepoStatsStatusEntity.builder()
                    .repositoryKey(key.repositoryKey)
                    .repoPackageType(key.repoPackageType)
                    .updateTime(key.hourBucket)
                    .aggregationType(AGGREGATION_HOURLY)
                    .statusCode(statusCode)
                    .requestCount(count)
                    .httpMethod(key.httpMethod)
                    .build());
            dailyStatusEntities.add(RemoteRepoStatsStatusEntity.builder()
                    .repositoryKey(key.repositoryKey)
                    .repoPackageType(key.repoPackageType)
                    .updateTime(dayBucket)
                    .aggregationType(AGGREGATION_DAILY)
                    .statusCode(statusCode)
                    .requestCount(count)
                    .httpMethod(key.httpMethod)
                    .build());
        }

        for (RemoteRepoStatsStatusEntity entity : hourlyStatusEntities) {
            statusDao.additiveMerge(entity);
        }
        for (RemoteRepoStatsStatusEntity entity : dailyStatusEntities) {
            statusDao.additiveMerge(entity);
        }
    }

    private void cleanupOldRecords() {
        try {
            int retentionDays = ConstantValues.remoteRepoStatsRetentionDays.getInt();
            long thresholdTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS).toEpochMilli();
            hourlyDao.deleteByThresholdTime(thresholdTime);
            dailyDao.deleteByThresholdTime(thresholdTime);
            statusDao.deleteByThresholdTime(thresholdTime);
            log.debug("Cleaned up remote repo stats older than {} days", retentionDays);
        } catch (Exception e) {
            log.warn("Failed to cleanup old remote repo stats records", e);
        }
    }

    private void setupCollectionJob() {
        String cronExpression = ConstantValues.remoteRepoStatsCollectionCron.getString();
        log.info("Scheduling remote repo stats collection job with cron expression: {}", cronExpression);
        Task task = TaskUtils.createCronTask(RemoteRepoStatsCollectionJob.class, cronExpression);
        taskService.startTask(task, false);
    }

    /**
     * Computes the hour bucket (start of the hour in UTC) for a given instant.
     */
    static long computeHourBucket(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * Computes the day bucket (start of the day in UTC) for a given epoch-millis timestamp.
     */
    static long computeDayBucket(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()
                .toEpochMilli();
    }

    @Override
    public RemoteRepoStatsQueryResult queryStats(RemoteRepoStatsRequest request) {
        long startMs = computeDayBucket(
                Instant.parse(request.startDate()).toEpochMilli());
        long endMs = computeDayBucket(
                Instant.parse(request.endDate()).toEpochMilli());
        List<String> repoKeys = request.repoKeys() != null
                ? request.repoKeys() : List.of();
        List<String> packageTypes = request.packageTypes() != null
                ? request.packageTypes() : List.of();

        Map<String, RepoAggregation> repoMap = fetchAndAggregateDailyStats(
                startMs, endMs, repoKeys, packageTypes);
        Map<String, Map<Integer, Long>> repoStatusMap = new HashMap<>();
        Map<String, Map<String, Map<Integer, Long>>> repoDateStatusMap =
                new HashMap<>();
        fetchAndAggregateStatusData(startMs, endMs, repoKeys, packageTypes,
                repoStatusMap, repoDateStatusMap);

        final StatusPatternMatcher statusMatcher;
        if (request.statusPattern() != null
                && !request.statusPattern().isBlank()) {
            statusMatcher =
                    StatusPatternMatcher.compile(request.statusPattern());
        } else {
            statusMatcher = null;
        }

        List<Map.Entry<String, RepoAggregation>> filtered =
                applyFilters(repoMap, repoStatusMap, request,
                        statusMatcher);
        sortEntries(filtered, request);

        long total = filtered.size();
        RemoteRepoStatsQueryResult.GlobalStats globalStats =
                buildGlobalStats(filtered, repoStatusMap);
        List<Map.Entry<String, RepoAggregation>> page =
                paginate(filtered, request);

        List<RemoteRepoStatsQueryResult.RepoResult> repos = page.stream()
                .map(e -> buildRepoResult(e.getKey(), e.getValue(), request,
                        repoStatusMap.getOrDefault(e.getKey(), Map.of()),
                        repoDateStatusMap.getOrDefault(e.getKey(), Map.of())))
                .toList();

        return new RemoteRepoStatsQueryResult(
                request.startDate(), request.endDate(),
                request.offset(), request.limit(), total,
                globalStats, repos);
    }

    private Map<String, RepoAggregation> fetchAndAggregateDailyStats(
            long startMs, long endMs,
            List<String> repoKeys, List<String> packageTypes) {
        List<RemoteRepoStatsEntity> dailyRows =
                dailyDao.getByTimeRange(startMs, endMs, repoKeys, packageTypes);
        Map<String, RepoAggregation> repoMap = new LinkedHashMap<>();
        for (RemoteRepoStatsEntity row : dailyRows) {
            RepoAggregation agg = repoMap.computeIfAbsent(row.repositoryKey(),
                    k -> new RepoAggregation(row.repositoryKey(),
                            row.repoPackageType()));
            agg.updatePackageTypeIfEmpty(row.repoPackageType());
            agg.addDaily(row);
        }
        return repoMap;
    }

    private void fetchAndAggregateStatusData(
            long startMs, long endMs,
            List<String> repoKeys, List<String> packageTypes,
            Map<String, Map<Integer, Long>> repoStatusMap,
            Map<String, Map<String, Map<Integer, Long>>> repoDateStatusMap) {
        List<RemoteRepoStatsStatusEntity> statusRows =
                statusDao.getByTimeRangeAndAggregationType(
                        startMs, endMs, AGGREGATION_DAILY,
                        repoKeys, packageTypes);
        for (RemoteRepoStatsStatusEntity row : statusRows) {
            repoStatusMap.computeIfAbsent(row.repositoryKey(),
                            k -> new HashMap<>())
                    .merge(row.statusCode(), row.requestCount(), Long::sum);
            String dateKey = LocalDate.ofInstant(
                    Instant.ofEpochMilli(row.updateTime()),
                    ZoneOffset.UTC).toString();
            repoDateStatusMap
                    .computeIfAbsent(row.repositoryKey(), k -> new HashMap<>())
                    .computeIfAbsent(dateKey, k -> new HashMap<>())
                    .merge(row.statusCode(), row.requestCount(), Long::sum);
        }
    }

    private List<Map.Entry<String, RepoAggregation>> applyFilters(
            Map<String, RepoAggregation> repoMap,
            Map<String, Map<Integer, Long>> repoStatusMap,
            RemoteRepoStatsRequest request,
            StatusPatternMatcher statusMatcher) {
        return repoMap.entrySet().stream()
                .filter(e -> {
                    if (request.minErrorRate() == null) return true;
                    return e.getValue().computeErrorRate()
                            >= request.minErrorRate();
                })
                .filter(e -> {
                    if (statusMatcher == null) return true;
                    Map<Integer, Long> statuses =
                            repoStatusMap.getOrDefault(e.getKey(), Map.of());
                    return statuses.keySet().stream()
                            .anyMatch(statusMatcher::matches);
                })
                .collect(Collectors.toList());
    }

    private void sortEntries(
            List<Map.Entry<String, RepoAggregation>> entries,
            RemoteRepoStatsRequest request) {
        Comparator<Map.Entry<String, RepoAggregation>> comparator =
                getComparator(request.sortBy());
        if ("desc".equalsIgnoreCase(request.order())) {
            comparator = comparator.reversed();
        }
        entries.sort(comparator);
    }

    private List<Map.Entry<String, RepoAggregation>> paginate(
            List<Map.Entry<String, RepoAggregation>> entries,
            RemoteRepoStatsRequest request) {
        int offset = Math.max(request.offset(), 0);
        int limit = Math.max(request.limit(), 0);
        int fromIndex = Math.min(offset, entries.size());
        int toIndex = Math.min(fromIndex + limit, entries.size());
        return entries.subList(fromIndex, toIndex);
    }

    private RemoteRepoStatsQueryResult.GlobalStats buildGlobalStats(
            List<Map.Entry<String, RepoAggregation>> entries,
            Map<String, Map<Integer, Long>> repoStatusMap) {
        long totalReqs = 0, totalRespTime = 0, totalTtfb = 0, totalSize = 0;
        long successful = 0, failed = 0;
        Map<Integer, Long> globalByStatus = new TreeMap<>();
        Map<String, Long> globalByGroup = new TreeMap<>();

        for (var entry : entries) {
            RepoAggregation agg = entry.getValue();
            totalReqs += agg.totalRequests;
            totalRespTime += agg.totalResponseTimeMs;
            totalTtfb += agg.ttfbWeightedSum;
            totalSize += agg.totalResponseSize;
            successful += agg.status2xx + agg.status3xx;
            failed += agg.status4xx + agg.status5xx;

            Map<Integer, Long> statuses =
                    repoStatusMap.getOrDefault(entry.getKey(), Map.of());
            for (var s : statuses.entrySet()) {
                globalByStatus.merge(s.getKey(), s.getValue(), Long::sum);
                String group = (s.getKey() / 100) + "xx";
                globalByGroup.merge(group, s.getValue(), Long::sum);
            }
        }

        double errorRate = totalReqs > 0 ? (double) failed / totalReqs : 0.0;
        long avgRespTime = totalReqs > 0 ? totalRespTime / totalReqs : 0;
        long avgTtfb = totalReqs > 0 ? totalTtfb / totalReqs : 0;

        return new RemoteRepoStatsQueryResult.GlobalStats(
                totalReqs, successful, failed,
                Math.round(errorRate * 10000.0) / 10000.0,
                avgRespTime, avgTtfb, totalSize, entries.size(),
                globalByStatus, globalByGroup);
    }

    private RemoteRepoStatsQueryResult.RepoResult buildRepoResult(
            String repoKey, RepoAggregation agg, RemoteRepoStatsRequest request,
            Map<Integer, Long> statusMap,
            Map<String, Map<Integer, Long>> dateStatusMap) {
        double errorRate = agg.totalRequests > 0
                ? (double) (agg.status4xx + agg.status5xx) / agg.totalRequests
                : 0.0;
        long avgRespTime = agg.totalRequests > 0
                ? agg.totalResponseTimeMs / agg.totalRequests : 0;
        long avgTtfb = agg.totalRequests > 0
                ? agg.ttfbWeightedSum / agg.totalRequests : 0;
        long avgTransferRate = agg.totalResponseTimeMs > 0
                ? (agg.totalResponseSize * 1000) / agg.totalResponseTimeMs : 0;

        Map<Integer, Long> byStatus = null;
        Map<String, Long> byStatusGroup = null;
        if (request.includeStatusBreakdown() && !statusMap.isEmpty()) {
            byStatus = new TreeMap<>(statusMap);
            byStatusGroup = new TreeMap<>();
            for (var s : statusMap.entrySet()) {
                String group = (s.getKey() / 100) + "xx";
                byStatusGroup.merge(group, s.getValue(), Long::sum);
            }
        }

        List<RemoteRepoStatsQueryResult.DayResult> daily = null;
        if (request.includeDailyBreakdown() && !agg.dailyBuckets.isEmpty()) {
            daily = agg.dailyBuckets.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> buildDayResult(e.getKey(), e.getValue(),
                            request.includeStatusBreakdown(),
                            dateStatusMap.getOrDefault(e.getKey(), Map.of())))
                    .collect(Collectors.toList());
        }

        return new RemoteRepoStatsQueryResult.RepoResult(
                repoKey, agg.packageType,
                agg.totalRequests,
                agg.status2xx + agg.status3xx,
                agg.status4xx + agg.status5xx,
                Math.round(errorRate * 10000.0) / 10000.0,
                avgRespTime, avgTtfb, agg.totalResponseSize,
                avgTransferRate, byStatus, byStatusGroup, daily);
    }

    private RemoteRepoStatsQueryResult.DayResult buildDayResult(
            String dateKey, DayBucket db,
            boolean includeStatus, Map<Integer, Long> dayStatuses) {
        long dayReqs = db.totalRequests;
        Map<Integer, Long> dayByStatus = null;
        Map<String, Long> dayByGroup = null;
        if (includeStatus && !dayStatuses.isEmpty()) {
            dayByStatus = new TreeMap<>(dayStatuses);
            dayByGroup = new TreeMap<>();
            for (var s : dayStatuses.entrySet()) {
                dayByGroup.merge(
                        (s.getKey() / 100) + "xx", s.getValue(), Long::sum);
            }
        }
        return new RemoteRepoStatsQueryResult.DayResult(
                dateKey, dayReqs,
                dayReqs > 0 ? db.totalResponseTimeMs / dayReqs : 0,
                dayReqs > 0 ? db.ttfbWeightedSum / dayReqs : 0,
                db.totalResponseSize, dayByStatus, dayByGroup);
    }

    private Comparator<Map.Entry<String, RepoAggregation>> getComparator(String sortBy) {
        return switch (sortBy) {
            case RemoteRepoStatsSortFields.ERROR_RATE ->
                    Comparator.comparingDouble(e -> e.getValue().computeErrorRate());
            case RemoteRepoStatsSortFields.AVG_RESPONSE_TIME ->
                    Comparator.comparingLong(e -> e.getValue().totalRequests > 0
                            ? e.getValue().totalResponseTimeMs / e.getValue().totalRequests : 0);
            case RemoteRepoStatsSortFields.AVG_TTFB ->
                    Comparator.comparingLong(e -> e.getValue().totalRequests > 0
                            ? e.getValue().ttfbWeightedSum / e.getValue().totalRequests : 0);
            case RemoteRepoStatsSortFields.TOTAL_RESPONSE_SIZE ->
                    Comparator.comparingLong(e -> e.getValue().totalResponseSize);
            default -> Comparator.comparingLong(e -> e.getValue().totalRequests);
        };
    }

    /**
     * In-memory aggregation of daily rows for a single repository.
     */
    private static class RepoAggregation {
        final String repoKey;
        String packageType;
        long totalRequests;
        long totalResponseTimeMs;
        long ttfbWeightedSum;
        long totalResponseSize;
        long status2xx;
        long status3xx;
        long status4xx;
        long status5xx;
        final Map<String, DayBucket> dailyBuckets = new TreeMap<>();

        RepoAggregation(String repoKey, String packageType) {
            this.repoKey = repoKey;
            this.packageType = packageType;
        }

        void updatePackageTypeIfEmpty(String candidate) {
            if (isEmpty(packageType) && !isEmpty(candidate)) {
                packageType = candidate;
            }
        }

        void addDaily(RemoteRepoStatsEntity row) {
            totalRequests += row.totalRequestCount();
            totalResponseTimeMs += row.totalResponseTimeMs();
            ttfbWeightedSum += row.ttfbAvg() * row.totalRequestCount();
            totalResponseSize += row.totalResponseSize();
            status2xx += row.status2xxCount();
            status3xx += row.status3xxCount();
            status4xx += row.status4xxCount();
            status5xx += row.status5xxCount();

            String dateKey = LocalDate.ofInstant(
                    Instant.ofEpochMilli(row.updateTime()), ZoneOffset.UTC).toString();
            DayBucket bucket = dailyBuckets.computeIfAbsent(dateKey, k -> new DayBucket());
            bucket.totalRequests += row.totalRequestCount();
            bucket.totalResponseTimeMs += row.totalResponseTimeMs();
            bucket.ttfbWeightedSum += row.ttfbAvg() * row.totalRequestCount();
            bucket.totalResponseSize += row.totalResponseSize();
        }

        double computeErrorRate() {
            return totalRequests > 0 ? (double) (status4xx + status5xx) / totalRequests : 0.0;
        }
    }

    private static class DayBucket {
        long totalRequests;
        long totalResponseTimeMs;
        long ttfbWeightedSum;
        long totalResponseSize;
    }

    // ---- Inner classes ----

    /**
     * Key for the in-memory accumulator map:
     * (repositoryKey, repoPackageType, httpMethod, hourBucket).
     */
    record AccumulatorKey(String repositoryKey, String repoPackageType,
                          String httpMethod, long hourBucket) {
    }

    /**
     * Thread-safe accumulator for a single
     * (repository, httpMethod, hour) bucket.
     * Uses {@link AtomicLong} for lock-free concurrent updates.
     */
    static class StatsAccumulator {
        final AtomicLong totalRequestCount = new AtomicLong();
        final AtomicLong totalResponseTimeMs = new AtomicLong();
        final AtomicLong maxResponseTimeMs = new AtomicLong();
        final AtomicLong ttfbSum = new AtomicLong();
        final AtomicLong totalResponseSize = new AtomicLong();
        final AtomicLong status2xxCount = new AtomicLong();
        final AtomicLong status3xxCount = new AtomicLong();
        final AtomicLong status4xxCount = new AtomicLong();
        final AtomicLong status5xxCount = new AtomicLong();
        final ConcurrentHashMap<Integer, AtomicLong> statusDetails =
                new ConcurrentHashMap<>();

        void record(int statusCode, long responseTimeMs,
                    long ttfbMs, long responseSize) {
            totalRequestCount.incrementAndGet();
            totalResponseTimeMs.addAndGet(responseTimeMs);
            updateMax(maxResponseTimeMs, responseTimeMs);
            ttfbSum.addAndGet(ttfbMs);
            totalResponseSize.addAndGet(responseSize);
            incrementStatusBucket(statusCode);
            statusDetails.computeIfAbsent(statusCode,
                    k -> new AtomicLong()).incrementAndGet();
        }

        private void incrementStatusBucket(int statusCode) {
            if (statusCode >= 200 && statusCode < 300) {
                status2xxCount.incrementAndGet();
            } else if (statusCode >= 300 && statusCode < 400) {
                status3xxCount.incrementAndGet();
            } else if (statusCode >= 400 && statusCode < 500) {
                status4xxCount.incrementAndGet();
            } else if (statusCode >= 500 && statusCode < 600) {
                status5xxCount.incrementAndGet();
            }
        }

        /**
         * Lock-free compare-and-set loop to track the maximum value.
         */
        private static void updateMax(AtomicLong max, long value) {
            long current;
            do {
                current = max.get();
                if (value <= current) {
                    return;
                }
            } while (!max.compareAndSet(current, value));
        }
    }
}
