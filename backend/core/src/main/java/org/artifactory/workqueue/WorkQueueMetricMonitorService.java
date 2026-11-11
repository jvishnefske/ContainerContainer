/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.artifactory.workqueue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.artifactory.async.AsyncExecutor;
import org.artifactory.spring.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static org.artifactory.util.ExceptionUtils.getErrorCause;

@Component
@ConditionalOnProperty(name = "artifactory.metrics.enabled", havingValue = "true")
@ConditionalOnProperty(name = "artifactory.metrics.workqueue.enabled", havingValue = "true", matchIfMissing = true)

public class WorkQueueMetricMonitorService<T extends WorkItem> implements WorkQueueCustomizer<T> {
    private final MeterRegistry meterRegistry;

    public WorkQueueMetricMonitorService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public WorkQueue<T> customizeWorkQueue(WorkQueue<T> workQueue) {
        if (workQueue.getKey().startsWith("Persistent Queue Triggering Federated")) {
            //skip metrics for fed work queues to avoid cardinality issue, as we have queue per repo.
            return workQueue;
        }
        if (workQueue.getMaxNumberOfWorkers() <= 0) {
            //skip metrics for queue that not really executing tasks
            return workQueue;
        }
        return new MonitorWorkQueue<>(workQueue, meterRegistry);
    }


    private static class MonitorWorkQueue<T extends WorkItem> implements WorkQueue<T> {
        private final WorkQueue<T> workQueue;
        private final Counter publishedCounter;
        private final Counter ignoredTaskCounter;

        private final Counter consumedCounter;

        private final MeterRegistry meterRegistry;

        public MonitorWorkQueue(WorkQueue<T> workQueue, MeterRegistry meterRegistry) {
            this.workQueue = workQueue;
            Gauge.builder("workqueue.pending.size", this::getQueueSize)
                    .description("The total count of tasks in the pending queue")
                    .tag("name", workQueue.getKey())
                    .register(meterRegistry);
            Gauge.builder("workqueue.max.workers", this::getMaxNumberOfWorkers)
                    .description("The total number of workers assigned to the queue")
                    .tag("name", workQueue.getKey())
                    .register(meterRegistry);
            Gauge.builder("workqueue.active.workers", this::getNumberOfActiveWorkers)
                    .description("The current count of active workers")
                    .tag("name", workQueue.getKey())
                    .register(meterRegistry);
            publishedCounter = Counter.builder("workqueue.tasks.published")
                    .description("The count of tasks published to the queue")
                    .tag("name", workQueue.getKey())
                    .register(meterRegistry);
            ignoredTaskCounter = Counter.builder("workqueue.tasks.ignored")
                    .description("Count of tasks rejected by the queue")
                    .tag("name", workQueue.getKey())
                    .register(meterRegistry);
            consumedCounter = Counter.builder("workqueue.tasks.consumed")
                    .description("Count of tasks consumed from the queue")
                    .tag("name", workQueue.getKey())
                    .register(meterRegistry);
            this.meterRegistry = meterRegistry;
        }

        /**
         * Only items that were successfully added to the queue should be reported as published.
         */
        @Override
        public boolean offerWork(T workItem, Runnable runnable) {
            return workQueue.offerWork(workItem, runnable);
        }

        @Override
        public int availablePermits() {
            return workQueue.availablePermits();
        }

        @Override
        public void stopQueue() {
            workQueue.stopQueue();
        }

        @Override
        public String getKey() {
            return workQueue.getKey();
        }

        @Override
        public void waitForItemDone(T workItem) {
            workQueue.waitForItemDone(workItem);
        }

        @Override
        public int getQueueSize() {
            return workQueue.getQueueSize();
        }

        @Override
        public int getMaxNumberOfWorkers() {
            return workQueue.getMaxNumberOfWorkers();
        }

        @Override
        public void updateWorkers(int newWorkers) throws InterruptedException {
            workQueue.updateWorkers(newWorkers);
        }

        @Override
        public void execute() {
            workQueue.execute();
        }

        /**
         * Prepare a runnable that will report the following:
         * 1. The execution time of the given runnable
         * 2. How long the work item spent in the queue before executed
         * 3. Report about the identical work items size
         */
        @Override
        public boolean execute(T workItem, @NotNull Runnable runnable) {
            Runnable warrpedRunnable = () -> {
                Exception e = null;
                long removedFromQueue = System.currentTimeMillis();
                try {
                    runnable.run();
                } catch (Exception ex) {
                    e = ex;
                    throw ex;
                } finally {
                    recordExecutionTime(System.currentTimeMillis() - removedFromQueue,
                            workItem.getIdenticalWorkItems().size(), workQueue.getKey(), e);
                    recordPendingTaskTime(workItem, removedFromQueue, workQueue.getKey());
                    consumedCounter.increment(workItem.getIdenticalWorkItems().size());
                }

            };
            publishedCounter.increment();
            boolean executed = workQueue.execute(workItem, warrpedRunnable);
            if (!executed) {
                ignoredTaskCounter.increment();
            }
            return executed;
        }

        @Nonnull
        @Override
        public AsyncExecutor executor() {
            return workQueue.executor();
        }

        private int getNumberOfActiveWorkers() {
            return getMaxNumberOfWorkers() - availablePermits();
        }

        private void recordExecutionTime(long durationTime, int batchSize, String queueName, @Nullable Exception e) {
            String batchBucketSize = "1";
            if (batchSize >= 2 && batchSize <= 5) {
                batchBucketSize = "2-5";
            } else if (batchSize > 5) {
                batchBucketSize = "6+";
            }
            Timer.builder("workqueue.execution.duration")
                    .description("The queue's execution duration")
                    .tags(
                            "name", queueName,
                            "size", batchBucketSize,
                            "ex", getErrorCause(e))
                    .register(meterRegistry)
                    .record(durationTime, TimeUnit.MILLISECONDS);
        }

        private void recordPendingTaskTime(T workItem, long removedFromQueue, String queueName) {
            for (WorkItem identicalWorkItem : workItem.getIdenticalWorkItems()) {
                long durationTime = removedFromQueue - identicalWorkItem.getAddedToQueueTimestamp();
                Timer.builder("workqueue.pending.duration")
                        .description("The duration a task spent waiting in the queue")
                        .tags("name", queueName)
                        .register(meterRegistry)
                        .record(durationTime, TimeUnit.MILLISECONDS);
            }
        }
    }
}
