package org.artifactory.traffic.summary;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.traffic.summary.dao.TrafficSummaryDao;
import org.artifactory.storage.db.traffic.summary.entity.TrafficSummaryEntity;
import org.artifactory.traffic.TrafficEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author kunalm
 */
@Slf4j
@Component
@Reloadable(beanClass = TrafficSummaryService.class, initAfter = {TaskService.class})
public class TrafficSummaryServiceImpl implements TrafficSummaryService {

    private final MeterRegistry meterRegistry;
    private final TaskService taskService;
    private final TrafficSummaryDao trafficSummaryDao;

    private final ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock();

    private Counter uploadRequestCounter;
    private Counter downloadRequestCounter;
    private DistributionSummary uploadBytesSummary;
    private DistributionSummary downloadBytesSummary;

    private static final long MONTH_IN_SECONDS = 30L * 24 * 60 * 60;

    public TrafficSummaryServiceImpl(MeterRegistry meterRegistry, TaskService taskService,
            TrafficSummaryDao trafficSummaryDao) {
        this.meterRegistry = meterRegistry;
        this.taskService = taskService;
        this.trafficSummaryDao = trafficSummaryDao;
        setupAggregationMeters();
        setupCollectionJob();
    }

    @Override
    public void collectTrafficEntry(TrafficEntry trafficEntry) {
        if (trafficEntry == null) {
            return;
        }
        log.trace("Aggregating Traffic Summary. TrafficEntry = {} bytes of {} action",
                trafficEntry.getTransferredSize(), trafficEntry.getAction());
        switch (trafficEntry.getAction()) {
            case UPLOAD -> {
                uploadRequestCounter.increment();
                uploadBytesSummary.record(trafficEntry.getTransferredSize());
            }
            case DOWNLOAD, REDIRECT -> {
                downloadRequestCounter.increment();
                downloadBytesSummary.record(trafficEntry.getTransferredSize());
            }
        }
    }

    @Override
    public void saveAggregatedTrafficSnapshot() {
        snapshotLock.writeLock().lock();
        try {
            TrafficSnapshot snapshot = extractSnapshot();
            log.debug("Saving Traffic Summary to DB. Snapshot: {}", snapshot);

            if (snapshot.totalRequestCount() == 0) {
                log.debug("No traffic recorded in the last period, skipping to save to DB.");
                return;
            }
            trafficSummaryDao.insert(snapshot.toTrafficSummaryEntity());
            log.info("Traffic Summary snapshot saved successfully.");

            resetMeters();

            long timeOlderThanAMonth = Instant.now().minusSeconds(MONTH_IN_SECONDS).toEpochMilli();
            trafficSummaryDao.deleteByThresholdTime(timeOlderThanAMonth);
            log.debug("Traffic Summary snapshots older than {} deleted.", timeOlderThanAMonth);
        } catch (Exception e) {
            log.warn("Failed to save Traffic Summary snapshot to DB.", e);
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    @Override
    public TrafficSummary getMonthlyTrafficSummary() {
        Instant now = Instant.now();
        long to = now.toEpochMilli();
        long from = now.minusSeconds(MONTH_IN_SECONDS).toEpochMilli(); // 30 days ago

        try {
            List<TrafficSummaryEntity> entities = trafficSummaryDao.getByDateRange(from, to);
            return TrafficSummary.fromTrafficSummaryEntities(entities, from, to);
        } catch (Exception e) {
            log.warn("Failed to fetch monthly Traffic Summary from DB.", e);
        }
        return TrafficSummary.builder().build();
    }

    private void setupAggregationMeters() {
        uploadRequestCounter = Counter.builder("traffic.summary.upload.requests.count")
                .description("Number of upload summary requests")
                .register(meterRegistry);

        downloadRequestCounter = Counter.builder("traffic.summary.download.requests.count")
                .description("Number of download summary requests")
                .register(meterRegistry);

        uploadBytesSummary = DistributionSummary.builder("traffic.summary.upload.bytes")
                .description("Total upload bytes summary")
                .register(meterRegistry);

        downloadBytesSummary = DistributionSummary.builder("traffic.summary.download.bytes")
                .description("Total download bytes summary")
                .register(meterRegistry);
    }

    private void resetMeters() {
        meterRegistry.remove(uploadRequestCounter);
        meterRegistry.remove(downloadRequestCounter);
        meterRegistry.remove(uploadBytesSummary);
        meterRegistry.remove(downloadBytesSummary);
        setupAggregationMeters();
    }

    private void setupCollectionJob() {
        String cronExpression = ConstantValues.trafficSummaryCollectionJobCron.getString();
        log.info("Scheduling traffic summary collection job with cron expression: {}", cronExpression);
        Task task = TaskUtils.createCronTask(TrafficSummaryCollectionJob.class, cronExpression);
        taskService.startTask(task, false);
    }

    private TrafficSnapshot extractSnapshot() {
        long currentTimestamp = Instant.now().toEpochMilli();
        long uploadRequests = (long) uploadRequestCounter.count();
        long downloadRequests = (long) downloadRequestCounter.count();
        long totalRequests = uploadRequests + downloadRequests;
        long uploadBytes = (long) uploadBytesSummary.totalAmount();
        long downloadBytes = (long) downloadBytesSummary.totalAmount();
        long totalBytes = uploadBytes + downloadBytes;

        return TrafficSnapshot.builder()
                .timestamp(currentTimestamp)
                .totalRequestCount(totalRequests)
                .uploadRequestCount(uploadRequests)
                .downloadRequestCount(downloadRequests)
                .totalBytes(totalBytes)
                .uploadBytes(uploadBytes)
                .downloadBytes(downloadBytes)
                .build();
    }
}
