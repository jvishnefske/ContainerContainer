package org.artifactory.sh.cleanup.events;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueTriggeringService;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Single thread waiting until notified for new Storage events.
 * On event triggering the storage queue
 *
 * @author TamirH
 */

@Slf4j
public class StorageEventsTriggerServiceImpl implements StorageEventsTriggerService, Runnable {

    public static final String JFROG_GC_INTERNAL = "jfrog_gc_internal";
    private final QueueTriggeringService queueTriggeringService;
    private final ScheduledExecutorService executor;
    private boolean shouldRun;
    private final Semaphore scheduledEventsLimit;

    public StorageEventsTriggerServiceImpl(QueueTriggeringService queueTriggeringService) {
        this.queueTriggeringService = queueTriggeringService;
        this.shouldRun = true;
        this.scheduledEventsLimit = new Semaphore(ConstantValues.gcTrashBinariesCleanupMaxQueueLength.getInt());
        this.executor = Executors
                .newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder()
                        .namingPattern(StorageEventsTriggerServiceImpl.class.getSimpleName() + "-%1$s").build());
    }

    @Override
    public void scheduleTrigger() {
        if (!ConstantValues.gcEventsCleanupContinuesTriggerEnabled.getBoolean()) {
            return;
        }
        try {
            if (scheduledEventsLimit.tryAcquire()) {
                this.executor.schedule(this, ConstantValues.gcEventsCleanupDelayMillis.getLong(),
                        TimeUnit.MILLISECONDS);
            } else {
                log.debug("Dropping events due to queue size limit {}",
                        ConstantValues.gcTrashBinariesCleanupMaxQueueLength.getInt());
            }
        } catch (Exception e) {
            log.trace("Failed to schedule task {}", e.getMessage());
        }
    }

    @Override
    public void run() {
        if (shouldRun) {
            scheduledEventsLimit.release();
            this.queueTriggeringService.triggerQueues(JFROG_GC_INTERNAL, QueueCursorType.GC);
        }
    }

    @PreDestroy
    void stop() throws InterruptedException {
        shouldRun = false;
        executor.shutdown();
        log.info("Shutdown terminated: {}", executor.awaitTermination(5, TimeUnit.SECONDS));
        log.debug("Stopped.");
    }
}
