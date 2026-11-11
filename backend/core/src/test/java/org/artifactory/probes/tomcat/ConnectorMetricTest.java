package org.artifactory.probes.tomcat;

import org.junit.jupiter.api.Test;

import static org.artifactory.system.load.event.ConnectorLoadState.HEALTHY;
import static org.artifactory.system.load.event.ConnectorLoadState.LOADED;
import static org.artifactory.system.load.event.ConnectorLoadState.OUTAGE;
import static org.artifactory.system.load.event.ConnectorLoadState.OVERLOADED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectorMetricTest {

    @Test
    void testGetConnectorLoadState_ThreadsBelow80Percent_Healthy() {
        ConnectorMetric metric = ConnectorMetric.builder()
                .name("test-connector")
                .maxThreads(100)
                .currentThreadsBusy(70)
                .processedCount(1000)
                .maxConnections(200)
                .currentConnections(150)
                .build();

        assertEquals(HEALTHY, metric.getConnectorLoadState());
    }

    @Test
    void testGetConnectorLoadState_ThreadsAbove80PercentButNotMaxed_Loaded() {
        ConnectorMetric metric = ConnectorMetric.builder()
                .name("test-connector")
                .maxThreads(100)
                .currentThreadsBusy(85)
                .processedCount(1000)
                .maxConnections(200)
                .currentConnections(150)
                .build();

        assertEquals(LOADED, metric.getConnectorLoadState());
    }

    @Test
    void testGetConnectorLoadState_ThreadsMaxedOutAndConnectionsBelowMax_Overloaded() {
        ConnectorMetric metric = ConnectorMetric.builder()
                .name("test-connector")
                .maxThreads(100)
                .currentThreadsBusy(100)
                .processedCount(1000)
                .maxConnections(200)
                .currentConnections(150)
                .build();

        assertEquals(OVERLOADED, metric.getConnectorLoadState());
    }

    @Test
    void testGetConnectorLoadState_ThreadsAndConnectionsMaxedOut_Outage() {
        ConnectorMetric metric = ConnectorMetric.builder()
                .name("test-connector")
                .maxThreads(100)
                .currentThreadsBusy(100)
                .processedCount(1000)
                .maxConnections(200)
                .currentConnections(200)
                .build();

        assertEquals(OUTAGE, metric.getConnectorLoadState());
    }
}