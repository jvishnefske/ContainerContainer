package org.artifactory.queue.monitor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.event.queue.QueueEventException;
import org.artifactory.queue.services.PersistentQueue;
import org.jfrog.common.ClockUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * a Human readable report for a running queue
 * enable report by changing PersistentQueueRunnerReport's log level to DEBUG
 *
 * @author avishayh
 */
@Slf4j
@Component
public class PersistentQueueRunnerReport implements PersistentQueueMonitor {

    private static final ThreadLocal<PersistentQueueRunnerReport> tlReport = new ThreadLocal<>();
    private final Set<Long> filtered = new HashSet<>();
    private final List<QueueEvent> loaded = new ArrayList<>();
    private final Map<String, Exception> exceptions = new LinkedHashMap<>();
    private final Set<Long> includedErrors = new HashSet<>();
    private String name;
    private long startTime;
    private long endTime;
    private long startMarker = -1;
    private long endMarker = -1;
    private PersistentQueue.TriggerResult triggerResult;

    @Override
    public void start(PersistentQueue persistentQueue) {
        if (isEnabled()) {
            PersistentQueueRunnerReport report = new PersistentQueueRunnerReport();
            tlReport.set(report);
            report.startTime = ClockUtils.epochMillis();
            report.name = persistentQueue.getName();
        }
    }

    @Override
    public void end(String queueName, PersistentQueue.TriggerResult triggerResult) {
        if (isStarted()) {
            final PersistentQueueRunnerReport report = report();
            report.triggerResult = triggerResult;
            report.endTime = ClockUtils.epochMillis();
            log.debug(report.toString());
            tlReport.remove();
        }
    }

    @Override
    public void addFilterEvents(String queueName, List<? extends QueueEvent> eventsAfterConfigurationFilter,
            List<? extends QueueEvent> eventsAfterReductionFilter) {
        if (isStarted()) {
            //noinspection unchecked
            final PersistentQueueRunnerReport currentReport = report();
            List<QueueEvent> removed = ListUtils.subtract(currentReport.loaded, eventsAfterReductionFilter);
            currentReport.filtered.addAll(removed.stream().map(QueueEvent::getEventId).collect(Collectors.toSet()));
        }
    }

    @Override
    public void loadedEvents(String queueName, List<? extends QueueEvent> events) {
        if (isStarted()) {
            report().loaded.addAll(events);
        }
    }

    @Override
    public void startMarker(String queueName, long startMarker) {
        if (isStarted()) {
            report().startMarker = startMarker;
        }
    }

    @Override
    public void endMarker(String queueName, long endMarker) {
        if (isStarted()) {
            report().endMarker = endMarker;
        }
    }

    @Override
    public void beforeImplementor(String queueName, String key, int eventsSize) {
        // not relevant for the report
    }

    @Override
    public void afterImplementor(String queueName, String key, @Nullable Exception exception) {
        if (isStarted() && (exception != null)) {
            report().exceptions.put(key, exception);

        }
    }

    @Override
    public void includedErrors(String queueName, Collection<Long> errors) {
        if (isStarted()) {
            report().includedErrors.addAll(errors);
        }
    }

    private static boolean isEnabled() {
        return log.isDebugEnabled();
    }

    private static boolean isStarted() {
        return report() != null;
    }

    private static PersistentQueueRunnerReport report() {
        return tlReport.get();
    }

    @Override
    public String toString() {
        long lapTime = endTime - startTime;
        int lapSecs = (int) (lapTime / 1000);
        //create human readable report
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%nname: %s%n", name));
        sb.append(String.format("lapTime: %d ms (%d secs)%n", lapTime, lapSecs));
        sb.append(String.format("status: %s (%d rows loaded, %d rows filtered)%n", triggerResult, loaded.size(),
                filtered.size()));
        sb.append(String.format("marker:   %s -> %s%n",
                startMarker == -1 ? "n/a" : startMarker,
                endMarker == -1 ? "n/a" : endMarker));
        if (!loaded.isEmpty()) {
            sb.append(String.format("loaded:   %s%n",
                    loaded.stream().map(QueueEvent::getEventId).collect(Collectors.toSet())));
        }
        if (!filtered.isEmpty()) {
            sb.append(String.format("filtered: %s%n", filtered));
        }
        if (!exceptions.isEmpty()) {
            sb.append("failed: [\n");
            exceptions.forEach((key, exception) ->
                    sb.append(String.format("%s -> %s%n", key, exception))
            );
            sb.append("]\n");
        }
        if (!loaded.isEmpty()) {
            sb.append("events: [\n");
            Set<Long> failures =
                    exceptions.values().stream()
                            .filter(QueueEventException.class::isInstance)
                            .map(exception -> ((QueueEventException) exception).getFailedEvent().getEventId())
                            .collect(Collectors.toSet());
            loaded.forEach(e -> {
                String error = includedErrors.contains(e.getEventId()) ? "|error" : "";
                String filter = filtered.contains(e.getEventId()) ? " -> filtered" : "";
                String failure = failures.contains(e.getEventId()) ? " -> failed" : "";
                sb.append(String.format("%d -> %s%s%s%s%n", e.getEventId(), e, error, filter, failure));
            });
            sb.append("]\n");
        }
        return sb.toString();
    }
}
