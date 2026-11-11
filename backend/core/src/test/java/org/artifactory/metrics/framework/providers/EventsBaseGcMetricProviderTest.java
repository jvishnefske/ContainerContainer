package org.artifactory.metrics.framework.providers;

import org.artifactory.common.ConstantValues;
import org.artifactory.sh.cleanup.events.StorageEventsService;
import org.artifactory.sh.cleanup.events.EventGcMetric;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.metrics.model.Metric;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.artifactory.metrics.framework.providers.EventsBaseGcMetricProvider.GcEventsReportedMetrics;
import static org.artifactory.metrics.framework.providers.EventsBaseGcMetricProvider.GcEventsReportedMetrics.valueOf;
import static org.artifactory.metrics.framework.providers.EventsBaseGcMetricProvider.GcEventsReportedMetrics.values;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author TamirH
 */

class EventsBaseGcMetricProviderTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void init() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.gcEventsWaitingEventCacheTimeMinutes, "1");
    }

    @AfterEach
    void after() {
        unbindArtifactoryHome();
    }

    @Test
    void testCollectingEventsGcMetrics() {
        final EventGcMetric eventGcMetric = mock(EventGcMetric.class);
        when(eventGcMetric.getEventsCount()).thenReturn(2L);
        when(eventGcMetric.getCleanedBytes()).thenReturn(3L);
        when(eventGcMetric.getDeleteBinariesCount()).thenReturn(1L);
        when(eventGcMetric.getDeleteFailuresCount()).thenReturn(4L);
        when(eventGcMetric.getRetryExhaustedCount()).thenReturn(6L);
        final StorageEventsService storageEvents = mock(StorageEventsService.class);
        when(storageEvents.getEventsCount()).thenReturn(5L).thenReturn(10L);
        final EventsBaseGcMetricProvider eventsBaseGcMetricProvider =
                new EventsBaseGcMetricProvider(eventGcMetric, storageEvents);
        // Call collect twice to test the caching on the waiting metric(db call)
        eventsBaseGcMetricProvider.collect();
        final List<Metric> metrics = eventsBaseGcMetricProvider.collect();
        assertEquals(values().length, metrics.size());
        for (Metric metric : metrics) {
            final GcEventsReportedMetrics reportedMetrics =
                    valueOf(metric.getName());
            switch (reportedMetrics) {
                case size_cleaned -> assertEquals(3L, metric.getValue().longValue());
                case binaries_deleted -> assertEquals(1L, metric.getValue().longValue());
                case events -> assertEquals(2L, metric.getValue().longValue());
                case waiting -> assertEquals(5L, metric.getValue().longValue());
                case failures -> assertEquals(4L, metric.getValue().longValue());
                case retry_exhausted -> assertEquals(6L, metric.getValue().longValue());
                case deduped_events -> assertEquals(0L, metric.getValue().longValue());
                case squashed_events -> assertEquals(0L, metric.getValue().longValue());
                default -> fail();
            }
        }
    }
}