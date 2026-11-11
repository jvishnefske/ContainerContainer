/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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
package org.artifactory.metrics.framework.providers;

import com.google.common.base.Suppliers;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.sh.cleanup.events.StorageEventsService;
import org.artifactory.sh.cleanup.events.EventGcMetric;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.artifactory.metrics.MetricsUtil.addMetricWithLabelsToList;
import static org.artifactory.sh.cleanup.GarbageCollectorStrategy.EVENTS_GC;

/**
 * @author Tamirh
 */
@Component
@Slf4j
public class EventsBaseGcMetricProvider implements MetricProvider {

    private final EventGcMetric eventGcMetric;
    private final Supplier<Long> waitingEventsCountSupplier;

    @Autowired
    public EventsBaseGcMetricProvider(EventGcMetric eventGcMetric, StorageEventsService storageEvents) {
        this.eventGcMetric = eventGcMetric;
        waitingEventsCountSupplier = Suppliers.memoizeWithExpiration(
                storageEvents::getEventsCount, ConstantValues.gcEventsWaitingEventCacheTimeMinutes.getInt(),
                TimeUnit.MINUTES);
    }

    @Override
    public String getName() {
        return "jfrt_events_gc";
    }

    @Override
    public String getDescription() {
        return "Artifactory Garbage Collection Metrics Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return TimeUnit.SECONDS.toMillis(60);
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        Map<String, Object> labels = new HashMap<>();
        labels.put("type", EVENTS_GC);

        addMetricWithLabelsToList(
                metrics,
                labels, GcEventsReportedMetrics.size_cleaned.name(), Metric.MetricType.BYTES,
                "Total Bytes recovered by Garbage Collection", eventGcMetric.getCleanedBytes()
        );

        addMetricWithLabelsToList(
                metrics,
                labels, GcEventsReportedMetrics.binaries_deleted.name(), Metric.MetricType.COUNTER,
                "Total number of binaries removed by Garbage Collection", eventGcMetric.getDeleteBinariesCount());

        addMetricWithLabelsToList(
                metrics,
                labels, GcEventsReportedMetrics.failures.name(), Metric.MetricType.COUNTER,
                "Total number of binaries that Garbage Collection failed to remove",
                eventGcMetric.getDeleteFailuresCount());

        addMetricWithLabelsToList(
                metrics,
                labels, GcEventsReportedMetrics.retry_exhausted.name(), Metric.MetricType.COUNTER,
                "Total number of binaries that Garbage Collection gave up to remove",
                eventGcMetric.getRetryExhaustedCount());

        addMetricWithLabelsToList(
                metrics,
                labels, GcEventsReportedMetrics.deduped_events.name(), Metric.MetricType.COUNTER,
                "Total number of deduped events",
                eventGcMetric.getDedupCount());

        addMetricWithLabelsToList(
                metrics,
                labels, GcEventsReportedMetrics.squashed_events.name(), Metric.MetricType.COUNTER,
                "Total number of events skipped due to squashing",
                eventGcMetric.getSquashedCount());

        long waitingEvents = 0;
        try {
            waitingEvents = waitingEventsCountSupplier.get();
        } catch (Exception e) {
            log.error("Failed to get events count {}", e.getMessage(), e);
        }
        addMetricWithLabelsToList(
                metrics,
                labels, GcEventsReportedMetrics.waiting.name(), Metric.MetricType.GAUGE,
                "Total number of waiting event", waitingEvents);

        addMetricWithLabelsToList(
                metrics,
                labels, GcEventsReportedMetrics.events.name(), Metric.MetricType.COUNTER,
                "Total number of event", eventGcMetric.getEventsCount());
        return metrics;
    }

    @Override

    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        return 0;
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

    enum GcEventsReportedMetrics {
        size_cleaned,
        binaries_deleted,
        waiting,
        events,
        failures,
        retry_exhausted,
        deduped_events,
        squashed_events,
    }

}
