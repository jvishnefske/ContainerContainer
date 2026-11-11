package org.artifactory.jfbus;

import lombok.extern.slf4j.Slf4j;
import org.jfrog.eventing.jfbus.outbox.processor.OutboxOperator;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodically runs {@link OutboxOperator#managePartitions(String)} so PostgreSQL range
 * partitions on {@code artifactory_outbox_events} are created ahead of expiry.
 *
 * <p>The eventing outbox library does not schedule partition lifecycle automatically; the host
 * application must invoke {@code managePartitions} every 15–30 minutes on partitioning databases.
 */
@Slf4j
public class OutboxPartitionLifecycleManager {

    private final OutboxOperator outboxOperator;
    private final long intervalMinutes;
    private final Object lifecycleLock = new Object();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;

    public OutboxPartitionLifecycleManager(OutboxOperator outboxOperator, long intervalMinutes) {
        this.outboxOperator = outboxOperator;
        this.intervalMinutes = intervalMinutes;
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (executor != null) {
                return;
            }
            runMaintenance();
            executor = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().name("artifactory-outbox-partition-", 0).daemon(true).factory());
            scheduledTask = executor.scheduleWithFixedDelay(
                    this::runMaintenance,
                    intervalMinutes,
                    intervalMinutes,
                    TimeUnit.MINUTES);
            log.info("Started outbox partition lifecycle manager (interval={} min)", intervalMinutes);
        }
    }

    public void stop() {
        synchronized (lifecycleLock) {
            if (scheduledTask != null) {
                scheduledTask.cancel(true);
                scheduledTask = null;
            }
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            log.info("Stopped outbox partition lifecycle manager");
        }
    }

    void runMaintenance() {
        try {
            boolean changed = outboxOperator.managePartitions(null);
            log.debug("Outbox partition maintenance completed (changesApplied={})", changed);
        } catch (Exception e) {
            log.warn("Outbox partition maintenance failed: {}", e.getMessage(), e);
        }
    }
}
