package org.artifactory.queue.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.model.RepoConfig;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.queue.services.PersistentQueue.TriggerResult;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.spring.ConditionalOnProperty;
import org.artifactory.storage.db.event.service.EventsMetrics;
import org.jfrog.common.ClockUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.artifactory.util.ExceptionUtils.getErrorCause;

/**
 * Report micrometer metrics about the persistent queue
 */
@Component
@ConditionalOnProperty(name = "artifactory.metrics.enabled", havingValue = "true")
@ConditionalOnProperty(name = "artifactory.metrics.persistent.queue.enabled", havingValue = "true", matchIfMissing =
        true)
@Slf4j
public class PersistentQueueMetricsMonitor implements PersistentQueueMonitor, EventsMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, PersistentQueueMonitorData> persistentQueueMonitorDataMap;
    @Setter
    private LongSupplier currentTimeSupplier = System::currentTimeMillis;
    @Setter
    private LongSupplier currentEpochTimeSupplier = ClockUtils::epochMillis;

    private RepositoryService repositoryService;

    public PersistentQueueMetricsMonitor(MeterRegistry meterRegistry, RepositoryService repositoryService) {
        this.meterRegistry = meterRegistry;
        this.repositoryService = repositoryService;
        this.persistentQueueMonitorDataMap = new ConcurrentHashMap<>();
    }

    @Override
    public void reportEvent(EventInfo event) {
        Counter.Builder builder = Counter.builder("pq.events.queued")
                .description(
                    "The total number of events that were published to the temporary buffer of the persistent queue")
                .tag("type", event.getType().name());
        try {
            String repoName = event.getPath().substring(0, event.getPath().indexOf("/"));
            RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoName);
            builder.tag("pkgType", repoConfig.getPackageType().getType());
            builder.tag("repoType", repoConfig.getRepoType().typeNameLowercase());
        } catch (Exception e) {
            // skip reporting package and repo type
            log.debug("Couldn't get repo from id: {}, path {}", event.getEventId(), event.getPath());
        }
        builder.register(meterRegistry).increment();
    }

    /**
     * Create new data for the given queue
     */
    @Override
    public void start(PersistentQueue persistentQueue) {
        log.trace("Start monitoring queue {}", persistentQueue.getName());
        PersistentQueueMonitorData currentData = persistentQueueMonitorDataMap.putIfAbsent(persistentQueue.getName(),
                new PersistentQueueMonitorData(currentTimeSupplier.getAsLong(), persistentQueue.getName(),
                        persistentQueue.getQueueConfiguration().getName().toLowerCase()));
        if (currentData != null) {
            log.warn("Data already found for queue {}.", persistentQueue.getName());
        }
    }

    /**
     * Report the trigger duration using the result as label.
     * Remove the queue data
     */
    @Override
    public void end(String queueName, TriggerResult triggerResult) {
        log.trace("Finish monitoring queue {}", queueName);
        PersistentQueueMonitorData data = persistentQueueMonitorDataMap.remove(queueName);
        if (data == null) {
            log.warn("No data found for queue {}. Skipping 'end' reporting", queueName);
            return;
        }
        long durationTime = currentTimeSupplier.getAsLong() - data.getStartTriggerTime();
        Timer.builder("pq.events.bulk.consume.duration")
                .description("description: The total time spent on fetching event bulks from the persistent queue, " +
                        "filtering and processing them.")
                .tags(
                        "queue", data.getConfigurationName(),
                        "result", triggerResult == null ? TriggerResult.NOT_AVAILABLE.name(): triggerResult.name())
                .register(meterRegistry)
                .record(durationTime, TimeUnit.MILLISECONDS);
    }

    /**
     * Report to consumed metrics about the events, using the filter type as label
     * Report the executed events count
     *
     * @param eventsAfterConfigurationFilter the events before reduction behavior filter, but after the configuration
     *                                       filter
     * @param eventsAfterReductionFilter     the events after reduction behavior filter
     */
    @Override
    public void addFilterEvents(String queueName, List<? extends QueueEvent> eventsAfterConfigurationFilter,
            List<? extends QueueEvent> eventsAfterReductionFilter) {
        PersistentQueueMonitorData data = persistentQueueMonitorDataMap.get(queueName);
        if (data == null) {
            log.warn("No data found for queue {}. Skipping 'addFilterEvents' reporting", queueName);
            return;
        }
        reportFilter(data.getConfigurationName(), ConsumedFilterType.FILTERED_BY_CONFIGURATION,
                data.getLoadedEventsSize() - eventsAfterConfigurationFilter.size());
        reportFilter(data.getConfigurationName(), ConsumedFilterType.FILTERED_BY_REDUCTION_BEHAVIOR,
                eventsAfterConfigurationFilter.size() - eventsAfterReductionFilter.size());
        Counter.builder("pq.events.processed")
                .description(
                        "The total number of events fetched from the persistent queue that passed filter and were " +
                                "processed by handlers")
                .tag("queue", data.getConfigurationName())
                .register(meterRegistry)
                .increment(eventsAfterReductionFilter.size());
    }

    /**
     * Report to consumed metrics about all the events.
     * Measure how long a single event waited to be fetched from the queue
     */
    @Override
    public void loadedEvents(String queueName, List<? extends QueueEvent> events) {
        PersistentQueueMonitorData data = persistentQueueMonitorDataMap.get(queueName);
        if (data == null) {
            log.warn("No data found for queue {}. Skipping 'loadedEvents' reporting", queueName);
            return;
        }
        data.setLoadedEventsSize(events.size());
        reportConsumed(data.getConfigurationName(), events.size());
        recordPendingEventsTime(queueName, data.getConfigurationName(), events);
    }

    /**
     * Save the start time of the implementor execution
     *
     * @param key        - the group key for the execution
     * @param eventsSize - the number of events in the group
     */
    @Override
    public void beforeImplementor(String queueName, String key, int eventsSize) {
        PersistentQueueMonitorData data = persistentQueueMonitorDataMap.get(queueName);
        if (data == null) {
            log.warn("No data found for queue {}. Skipping 'beforeImplementor' reporting", queueName);
            return;
        }
        data.getExecutionsMap()
                .put(key, new PersistentQueueMonitorData.PersistentQueueExecutionDate(eventsSize,
                        currentTimeSupplier.getAsLong()));
    }

    /**
     * Measure the execution time of the implementor
     *
     * @param key       - the group key for the execution
     * @param exception - the exception that occurred during the execution (if occurred)
     */
    @Override
    public void afterImplementor(String queueName, String key, @Nullable Exception exception) {
        long endTime = currentTimeSupplier.getAsLong();
        PersistentQueueMonitorData data = persistentQueueMonitorDataMap.get(queueName);
        if (data == null) {
            log.warn("No data found for queue {}. Skipping 'afterImplementor' reporting", queueName);
            return;
        }
        PersistentQueueMonitorData.PersistentQueueExecutionDate executionDate = data.getExecutionsMap().get(key);
        if (executionDate == null) {
            log.warn("No executionDate found for queue {}. Skipping 'afterImplementor' reporting", queueName);
            return;
        }
        long duration = endTime - executionDate.startTime();
        recordExecutionTime(data.getConfigurationName(), duration, executionDate.eventsSize(), exception);
    }

    private void recordExecutionTime(String configurationName, long durationTime, int numberOfEvents,
            @Nullable Exception e) {
        String batchBucketSize = "1";
        if (numberOfEvents >= 2 && numberOfEvents <= 9) {
            batchBucketSize = "2-9";
        } else if (numberOfEvents > 9) {
            batchBucketSize = "10+";
        }
        Timer.builder("pq.events.bulk.process.duration")
                .description(
                        "The total time spent on processing event bulks, it pulled from this persistent queue (after " +
                                "filtering).")
                .tags(
                        "queue", configurationName,
                        "size", batchBucketSize,
                        "ex", getErrorCause(e))
                .register(meterRegistry)
                .record(durationTime, TimeUnit.MILLISECONDS);
    }


    private void recordPendingEventsTime(String queueName, String configurationName,
            List<? extends QueueEvent> events) {
        long currentTime = currentEpochTimeSupplier.getAsLong();
        long pendingThreshold = ConstantValues.persistentQueueLagThresholdMilis.getLong();
        String configurationsNameLagList = ConstantValues.persistentQueueLagConfigurationNameFilterList.getString();
        for (QueueEvent event : events) {
            long durationTime = currentTime - event.getTimestamp();
            Timer.builder("pq.events.pending.duration")
                    .description(
                            "The total time events spent waiting in the temporary buffer and persistent queue, until " +
                                    "consumed.")
                    .tags("queue", configurationName)
                    .register(meterRegistry)
                    .record(durationTime, TimeUnit.MILLISECONDS);
            if (log.isDebugEnabled() &&
                    durationTime > pendingThreshold &&
                    pendingThreshold > 0 &&
                    (StringUtils.isBlank(configurationsNameLagList) ||
                            configurationsNameLagList.contains(configurationName))) {
                log.debug(
                        "The event with id:'{}', path:'{}', timestamp:'{}', type:'{}' took '{}' ms to be consumed for" +
                                " " +
                                "queue '{}'",
                        event.getEventId(), event.getPath(), event.getTimestamp(), event.getType(), durationTime,
                        queueName);
            }
        }
    }


    private void reportConsumed(String configurationName, int size) {
        Counter.builder("pq.events.consumed")
                .description(
                        "The total number of events that were consumed from the persistent queue (before filtering).")
                .tag("queue", configurationName)
                .register(meterRegistry)
                .increment(size);
    }

    private void reportFilter(String configurationName, ConsumedFilterType consumedFilterType, int size) {
        Counter.builder("pq.events.filtered")
                .description("The total number of events that were filtered out after consumptions")
                .tags(
                        "queue", configurationName,
                        "type", consumedFilterType.getLabel())
                .register(meterRegistry)
                .increment(size);
    }

    @Override
    public void startMarker(String queueName, long startMarker) {
        //not relevant for metrics
    }

    @Override
    public void endMarker(String queueName, long endMarker) {
        //not relevant for metrics
    }

    @Override
    public void includedErrors(String queueName, Collection<Long> errors) {
        //not relevant for metrics
    }

    @Getter
    private static class PersistentQueueMonitorData {
        private final long startTriggerTime;
        private final Map<String, PersistentQueueExecutionDate> executionsMap = new HashMap<>();
        private final String queueName;
        private final String configurationName;
        @Setter
        private int loadedEventsSize;

        private PersistentQueueMonitorData(long startTriggerTime, String queueName, String configurationName) {
            this.startTriggerTime = startTriggerTime;
            this.queueName = queueName;
            this.configurationName = configurationName;
        }

        private record PersistentQueueExecutionDate(int eventsSize, long startTime) {
        }
    }

    private enum ConsumedFilterType {
        FILTERED_BY_CONFIGURATION("event"), FILTERED_BY_REDUCTION_BEHAVIOR("reduction");
        @Getter
        private final String label;

        ConsumedFilterType(String label) {
            this.label = label;
        }
    }
}
